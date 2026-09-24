package app.astra.mobile.core.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import app.astra.mobile.core.data.PreferencesStore
import app.astra.mobile.core.data.QualidadeAoAssistir
import app.astra.mobile.core.data.QualidadeAoTransmitir
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.VoiceTokenRequest
import app.astra.mobile.core.realtime.SocketManager
import com.twilio.audioswitch.AudioDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.RemoteTrackPublication
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoCaptureParameter
import io.livekit.android.room.track.VideoCodec
import io.livekit.android.room.track.VideoEncoding
import io.livekit.android.room.track.VideoQuality
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import livekit.org.webrtc.RtpParameters
import org.json.JSONObject
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

enum class CallStatus { Idle, Connecting, Connected, Error }

data class SalaDaCall(
    val tipo: String,
    val id: String,
    val nome: String,
    val orbitaId: String?,
    val fotoDoSussurro: String? = null,
)

data class PessoaNaCall(
    val identity: String,
    val souEu: Boolean,
    val falando: Boolean,
    val mudo: Boolean,
    val surdo: Boolean,
    val transmitindo: Boolean,
    val tela: VideoTrack?,
)

enum class TipoDeSaida { ALTO_FALANTE, APARELHO, FONE_COM_FIO, BLUETOOTH }

data class SaidaDeSom(val chave: String, val nome: String, val tipo: TipoDeSaida)

data class VoiceState(
    val status: CallStatus = CallStatus.Idle,
    val sala: SalaDaCall? = null,
    val mudo: Boolean = false,
    val surdo: Boolean = false,
    val transmitindo: Boolean = false,
    val pessoas: List<PessoaNaCall> = emptyList(),
    val inicio: Long? = null,
    val saidas: List<SaidaDeSom> = emptyList(),
    val saidaAtual: SaidaDeSom? = null,
    val erro: String? = null,
) {
    val naCall: Boolean get() = sala != null && (status == CallStatus.Connecting || status == CallStatus.Connected)
}

@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val voiceApi: VoiceApi,
    private val socketManager: SocketManager,
    private val salasDeVoz: SalasDeVoz,
    private val tokenStore: TokenStore,
    private val preferencias: PreferencesStore,
) {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var room: Room? = null
    private var conexao: Job? = null
    private var eventos: Job? = null
    private var presenca: Job? = null
    private var meuId: String? = null

    private val mudosDaSala = mutableSetOf<String>()
    private val surdosDaSala = mutableSetOf<String>()
    private var mudoAntesDeEnsurdecer = false

    private var palco: String? = null
    private var palcoEmTelaCheia = false
    private var aoAssistir = QualidadeAoAssistir.AUTOMATICA

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    val activeRoom: Room? get() = room

    init {
        escopo.launch {
            socketManager.dmCallEnded.collect { conversa ->
                val sala = _state.value.sala
                if (sala?.tipo == TIPO_SUSSURRO && sala.id == conversa) encerrar(avisarServidor = false)
            }
        }
        escopo.launch {
            preferencias.prefs.map { it.aoAssistir }.distinctUntilChanged().collect {
                aoAssistir = it
                ajustarAssinaturas()
            }
        }
    }

    fun entrar(tipo: String, id: String, nome: String, orbitaId: String?, fotoDoSussurro: String? = null) {
        val atual = _state.value
        if (atual.sala?.id == id && atual.naCall) return
        if (atual.sala != null) encerrar(avisarServidor = true, comSom = false)
        _state.value = VoiceState(
            status = CallStatus.Connecting,
            sala = SalaDaCall(tipo, id, nome, orbitaId, fotoDoSussurro),
        )
        conexao = escopo.launch { conectar(tipo, id) }
    }

    fun sair() = encerrar(avisarServidor = true)

    fun alternarMudo() {
        _state.update {
            if (it.surdo) it.copy(surdo = false, mudo = false) else it.copy(mudo = !it.mudo)
        }
        aplicarMudoESurdo()
    }

    fun alternarSurdo() {
        _state.update {
            if (!it.surdo) {
                mudoAntesDeEnsurdecer = it.mudo
                it.copy(surdo = true, mudo = true)
            } else {
                it.copy(surdo = false, mudo = mudoAntesDeEnsurdecer)
            }
        }
        aplicarMudoESurdo()
    }

    fun assistir(quem: String?, emTelaCheia: Boolean) {
        if (palco == quem && palcoEmTelaCheia == emTelaCheia) return
        palco = quem
        palcoEmTelaCheia = emTelaCheia
        ajustarAssinaturas()
    }

    fun escolherSaida(chave: String) {
        val comutador = room?.audioSwitchHandler ?: return
        comutador.availableAudioDevices.firstOrNull { chaveDe(it) == chave }?.let { comutador.selectDevice(it) }
    }

    fun comecarATransmitir(permissao: Intent) {
        val r = room ?: return
        escopo.launch {
            val qualidade = preferencias.prefs.first().aoTransmitir
            val (largura, altura) = tamanhoDaCaptura(qualidade)
            r.screenShareTrackCaptureDefaults = LocalVideoTrackOptions(
                captureParams = VideoCaptureParameter(largura, altura, qualidade.quadros),
            )
            r.screenShareTrackPublishDefaults = VideoTrackPublishDefaults(
                videoEncoding = VideoEncoding(maxBitrate = qualidade.kbps * 1_000, maxFps = qualidade.quadros),
                simulcast = false,
                videoCodec = VideoCodec.H264.codecName,
                backupCodec = null,
                degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE,
            )
            val parametros = ScreenCaptureParams(
                mediaProjectionPermissionResultData = permissao,
                notificationId = CallService.ID_DA_NOTIFICACAO,
                notification = CallService.construirNotificacao(appContext, _state.value.copy(transmitindo = true)),
                onStop = { escopo.launch { pararDeTransmitir() } },
            )
            runCatching { r.localParticipant.setScreenShareEnabled(true, parametros) }
                .onSuccess { subiu ->
                    if (subiu && room === r) {
                        _state.update { it.copy(transmitindo = true, pessoas = retrato(r)) }
                        SonsDaCall.comecarATransmitir(appContext)
                    }
                }
                .onFailure { e ->
                    Log.w(TAG, "transmissão não subiu: ${e.message}")
                    _state.update { it.copy(erro = "Não consegui transmitir a tela.") }
                }
        }
    }

    fun pararDeTransmitir() {
        val r = room ?: return
        if (!_state.value.transmitindo) return
        _state.update { it.copy(transmitindo = false) }
        SonsDaCall.pararDeTransmitir(appContext)
        escopo.launch {
            runCatching { r.localParticipant.setScreenShareEnabled(false) }
            if (room === r) _state.update { it.copy(pessoas = retrato(r)) }
        }
    }

    fun esquecerErro() = _state.update { it.copy(erro = null) }

    private suspend fun conectar(tipo: String, id: String) {
        try {
            val dados = voiceApi.token(VoiceTokenRequest(tipo, id)).data
                ?: error("Resposta vazia do servidor")
            val r = LiveKit.create(appContext, options = RoomOptions())
            room = r
            observar(r)
            withTimeout(CONNECT_TIMEOUT_MS) { r.connect(dados.url, dados.token) }
            runCatching { r.localParticipant.setMicrophoneEnabled(!_state.value.mudo) }
                .onFailure { Log.w(TAG, "microfone não abriu: ${it.message}") }

            meuId = tokenStore.currentUserId()
            socketManager.entrarNaVoz(id)
            if (tipo == TIPO_CANAL) socketManager.joinChannel(id)
            salasDeVoz.entrou(id, meuId)
            presenca = escopo.launch { manterPresenca(id) }

            CallService.start(appContext)
            SonsDaCall.entrar(appContext)
            vigiarSaidas(r)
            _state.update {
                it.copy(status = CallStatus.Connected, inicio = System.currentTimeMillis(), pessoas = retrato(r))
            }
            anunciarEstado()
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "a sala demorou demais para responder")
            falhar("A conexão demorou demais. A rede está instável; tente de novo ou troque de rede.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "entrada na sala falhou: ${e.message}", e)
            falhar(explicar(e))
        }
    }

    private fun falhar(motivo: String) {
        val sala = _state.value.sala
        largarSala(sala, avisarServidor = false)
        _state.value = VoiceState(status = CallStatus.Error, sala = sala, erro = motivo)
    }

    private fun encerrar(avisarServidor: Boolean, comSom: Boolean = true) {
        val estava = _state.value
        largarSala(estava.sala, avisarServidor)
        if (comSom && estava.status == CallStatus.Connected) SonsDaCall.sair(appContext)
        _state.value = VoiceState()
    }

    private fun largarSala(sala: SalaDaCall?, avisarServidor: Boolean) {
        conexao?.cancel()
        conexao = null
        eventos?.cancel()
        eventos = null
        presenca?.cancel()
        presenca = null
        if (sala != null) {
            socketManager.sairDaVoz(sala.id)
            salasDeVoz.saiu(sala.id, meuId)
            if (avisarServidor && sala.tipo == TIPO_SUSSURRO) socketManager.desligar(sala.id)
        }
        room?.let {
            it.audioSwitchHandler?.unregisterAudioDeviceChangeListener(aoMudarSaidas)
            runCatching { it.disconnect() }
            runCatching { it.release() }
        }
        room = null
        mudosDaSala.clear()
        surdosDaSala.clear()
        palco = null
        palcoEmTelaCheia = false
        CallService.stop(appContext)
    }

    private suspend fun manterPresenca(salaId: String) {
        while (true) {
            delay(PRESENCA_MS)
            socketManager.manterNaVoz(salaId)
        }
    }

    private fun observar(r: Room) {
        eventos?.cancel()
        eventos = escopo.launch {
            r.events.collect { evento ->
                if (room !== r) return@collect
                when (evento) {
                    is RoomEvent.Disconnected -> {
                        if (_state.value.sala != null) falhar("A chamada caiu. Entre de novo quando a rede voltar.")
                        return@collect
                    }
                    is RoomEvent.Reconnecting -> _state.update { it.copy(status = CallStatus.Connecting) }
                    is RoomEvent.Reconnected -> {
                        _state.update { it.copy(status = CallStatus.Connected) }
                        anunciarEstado()
                    }
                    is RoomEvent.ParticipantConnected -> anunciarEstado()
                    is RoomEvent.ParticipantDisconnected -> {
                        val quem = evento.participant.identity?.value
                        mudosDaSala.remove(quem)
                        surdosDaSala.remove(quem)
                        if (palco == quem) palco = null
                    }
                    is RoomEvent.DataReceived -> lerRecado(evento.participant?.identity?.value, evento.data)
                    is RoomEvent.TrackPublished, is RoomEvent.TrackSubscribed -> ajustarAssinaturas()
                    else -> Unit
                }
                if (_state.value.surdo) silenciarOsOutros(r, true)
                _state.update { it.copy(pessoas = retrato(r)) }
            }
        }
    }

    private fun lerRecado(quem: String?, dados: ByteArray) {
        if (quem.isNullOrBlank()) return
        val recado = runCatching { JSONObject(String(dados, Charsets.UTF_8)) }.getOrNull() ?: return
        if (recado.optString("astra") != RECADO_DE_ESTADO) return
        if (recado.optBoolean("mudo")) mudosDaSala.add(quem) else mudosDaSala.remove(quem)
        if (recado.optBoolean("surdo")) surdosDaSala.add(quem) else surdosDaSala.remove(quem)
    }

    private fun anunciarEstado() {
        val r = room ?: return
        val estado = _state.value
        val recado = JSONObject()
            .put("astra", RECADO_DE_ESTADO)
            .put("mudo", estado.mudo)
            .put("surdo", estado.surdo)
            .toString()
            .toByteArray(Charsets.UTF_8)
        escopo.launch {
            r.localParticipant.publishData(recado).onFailure { Log.w(TAG, "estado não chegou à sala: ${it.message}") }
        }
    }

    private fun aplicarMudoESurdo() {
        val r = room ?: return
        val estado = _state.value
        silenciarOsOutros(r, estado.surdo)
        escopo.launch {
            runCatching { r.localParticipant.setMicrophoneEnabled(!estado.mudo) }
            if (room === r) _state.update { it.copy(pessoas = retrato(r)) }
        }
        anunciarEstado()
    }

    private fun silenciarOsOutros(r: Room, surdo: Boolean) {
        val volume = if (surdo) 0.0 else 1.0
        r.remoteParticipants.values.forEach { p ->
            p.audioTrackPublications.forEach { (_, faixa) -> (faixa as? RemoteAudioTrack)?.setVolume(volume) }
        }
    }

    private fun ajustarAssinaturas() {
        val r = room ?: return
        r.remoteParticipants.values.forEach { p ->
            val publicacao = p.getTrackPublication(Track.Source.SCREEN_SHARE) as? RemoteTrackPublication
                ?: return@forEach
            val quer = palco != null && palco == p.identity?.value
            if (publicacao.subscribed != quer) publicacao.setSubscribed(quer)
            if (quer) publicacao.setVideoQuality(qualidadeDoPalco())
        }
    }

    private fun qualidadeDoPalco(): VideoQuality = when (aoAssistir) {
        QualidadeAoAssistir.AUTOMATICA -> if (palcoEmTelaCheia) VideoQuality.HIGH else VideoQuality.LOW
        QualidadeAoAssistir.COMPLETA -> VideoQuality.HIGH
        QualidadeAoAssistir.LEVE -> VideoQuality.LOW
    }

    private fun retrato(r: Room): List<PessoaNaCall> {
        val estado = _state.value
        val eu = r.localParticipant
        val lista = ArrayList<PessoaNaCall>(r.remoteParticipants.size + 1)
        lista += PessoaNaCall(
            identity = eu.identity?.value.orEmpty(),
            souEu = true,
            falando = eu.isSpeaking && !estado.mudo,
            mudo = estado.mudo,
            surdo = estado.surdo,
            transmitindo = estado.transmitindo,
            tela = null,
        )
        r.remoteParticipants.values
            .sortedBy { it.identity?.value.orEmpty() }
            .forEach { p ->
                val id = p.identity?.value.orEmpty()
                val publicacao = p.getTrackPublication(Track.Source.SCREEN_SHARE)
                lista += PessoaNaCall(
                    identity = id,
                    souEu = false,
                    falando = p.isSpeaking,
                    mudo = id in mudosDaSala || !p.isMicrophoneEnabled,
                    surdo = id in surdosDaSala,
                    transmitindo = publicacao != null,
                    tela = publicacao?.track as? VideoTrack,
                )
            }
        return lista
    }

    private val aoMudarSaidas: (List<AudioDevice>, AudioDevice?) -> Unit = { lista, atual ->
        escopo.launch { publicarSaidas(lista, atual) }
    }

    private fun vigiarSaidas(r: Room) {
        val comutador = r.audioSwitchHandler ?: return
        comutador.registerAudioDeviceChangeListener(aoMudarSaidas)
        publicarSaidas(comutador.availableAudioDevices, comutador.selectedAudioDevice)
    }

    private fun publicarSaidas(lista: List<AudioDevice>, atual: AudioDevice?) {
        val saidas = lista.map { it.comoSaida() }
        _state.update { it.copy(saidas = saidas, saidaAtual = atual?.comoSaida()) }
    }

    private fun AudioDevice.comoSaida(): SaidaDeSom {
        val tipo = when (this) {
            is AudioDevice.Speakerphone -> TipoDeSaida.ALTO_FALANTE
            is AudioDevice.Earpiece -> TipoDeSaida.APARELHO
            is AudioDevice.WiredHeadset -> TipoDeSaida.FONE_COM_FIO
            is AudioDevice.BluetoothHeadset -> TipoDeSaida.BLUETOOTH
        }
        val nome = when (tipo) {
            TipoDeSaida.ALTO_FALANTE -> "Alto-falante"
            TipoDeSaida.APARELHO -> "No ouvido"
            TipoDeSaida.FONE_COM_FIO -> "Fone com fio"
            TipoDeSaida.BLUETOOTH -> name.ifBlank { "Bluetooth" }
        }
        return SaidaDeSom(chaveDe(this), nome, tipo)
    }

    private fun chaveDe(aparelho: AudioDevice): String = aparelho::class.java.simpleName + ":" + aparelho.name

    private fun tamanhoDaCaptura(qualidade: QualidadeAoTransmitir): Pair<Int, Int> {
        val janelas = appContext.getSystemService(WindowManager::class.java)
        val (largura, altura) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            janelas.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val medidas = DisplayMetrics()
            @Suppress("DEPRECATION")
            janelas.defaultDisplay.getRealMetrics(medidas)
            medidas.widthPixels to medidas.heightPixels
        }
        val escala = min(
            1f,
            min(
                qualidade.ladoMaior / max(largura, altura).toFloat(),
                qualidade.ladoMenor / min(largura, altura).toFloat(),
            ),
        )
        return ((largura * escala).toInt() and 1.inv()) to ((altura * escala).toInt() and 1.inv())
    }

    private fun explicar(e: Exception): String = when {
        e is HttpException && e.code() == 503 -> "As chamadas estão desligadas no servidor."
        e is HttpException && e.code() == 403 -> "Você não tem acesso a esta sala."
        else -> "Não consegui entrar na call. Tente de novo."
    }

    companion object {
        const val TIPO_CANAL = "channel"
        const val TIPO_SUSSURRO = "dm"
        private const val TAG = "VoiceManager"
        private const val CONNECT_TIMEOUT_MS = 20_000L
        private const val PRESENCA_MS = 20_000L
        private const val RECADO_DE_ESTADO = "estado-da-voz"
    }
}
