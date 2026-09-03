package app.astra.desktop.voice

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.desktop.AtalhosGlobais
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ChannelDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.Koin

@Stable
class VoiceSession(private val scope: CoroutineScope, private val koin: Koin) : FonteDeAparelhos {
    var joined by mutableStateOf<ChannelDto?>(null)
        private set

    var call by mutableStateOf<CallNaSala?>(null)
        private set

    fun callFor(channel: ChannelDto?): CallNaSala? =
        if (channel != null && joined?.id == channel.id) call else null

    private val sonda = SondaDeAparelhos(scope)

    override var microfones by mutableStateOf<List<AparelhoDeAudio>>(emptyList())
        private set
    override var saidas by mutableStateOf<List<AparelhoDeAudio>>(emptyList())
        private set

    init {
        scope.launch { sonda.entradas.collect { if (call == null) microfones = it } }
        scope.launch { sonda.saidas.collect { if (call == null) saidas = it } }
    }

    override fun listar() {
        val viva = call
        if (viva != null) {
            viva.atualizarAparelhos()
            return
        }
        sonda.atualizar()
    }

    fun join(channel: ChannelDto) = entrar("channel", channel)

    fun joinDm(conversationId: String, titulo: String) =
        entrar("dm", ChannelDto(id = conversationId, name = titulo, type = "VOICE"))

    var emSussurro by mutableStateOf(false)
        private set

    var mudo by mutableStateOf(false)
        private set
    var ensurdecido by mutableStateOf(false)
        private set

    private var mudoAntesDeEnsurdecer = false

    private val prefs = koin.get<DesktopPrefs>()

    init {
        scope.launch {
            prefs.state
                .map { it.teclaMudo to it.teclaEnsurdecer }
                .distinctUntilChanged()
                .collect {
                    registrarAtalhos()
                    aplicar()
                }
        }
        scope.launch {
            prefs.state
                .map { Triple(it.micEchoCancel, it.micNoiseSuppression, it.micAutoGain) }
                .distinctUntilChanged()
                .collect { (eco, ruido, ganho) -> call?.definirTratamento(eco, ruido, ganho) }
        }
        scope.launch {
            prefs.state
                .map { it.volumeDoMicrofone to it.volumeDaEscuta }
                .distinctUntilChanged()
                .collect { (mic, escuta) -> call?.definirVolumes(mic, escuta) }
        }
        scope.launch {
            prefs.state
                .map { it.duasCamadas }
                .distinctUntilChanged()
                .collect { call?.lembrarDuasCamadas(it) }
        }
    }

    private fun registrarAtalhos() {
        val p = prefs.state.value
        val mapa = buildMap<Int, (Boolean) -> Unit> {
            if (p.teclaMudo != 0) put(p.teclaMudo) { desceu -> if (desceu) naUi { alternarMudo() } }
            if (p.teclaEnsurdecer != 0) put(p.teclaEnsurdecer) { desceu -> if (desceu) naUi { alternarEnsurdecer() } }
        }
        AtalhosGlobais.observar(mapa)
    }

    private fun naUi(acao: () -> Unit) {
        scope.launch { acao() }
    }

    fun alternarMudo() {
        if (ensurdecido) {
            ensurdecido = false
            mudo = false
        } else {
            mudo = !mudo
        }
        aplicar()
    }

    fun alternarEnsurdecer() {
        ensurdecido = !ensurdecido
        if (ensurdecido) {
            mudoAntesDeEnsurdecer = mudo
            mudo = true
        } else {
            mudo = mudoAntesDeEnsurdecer
        }
        aplicar()
    }

    private fun aplicar() {
        call?.let {
            it.setMic(!mudo)
            it.setEnsurdecido(ensurdecido)
        }
    }

    private fun entrar(tipo: String, sala: ChannelDto) {
        if (joined?.id == sala.id && call != null) return

        if (koin.get<SessionStore>().load()?.userId == null) {
            VoiceLog.nota("[call] sem sessão carregada; não dá para entrar na sala")
            return
        }

        call?.dispose()
        call = CallNaSala(
            scope,
            SidecarDeVoz(scope),
            koin.get<DesktopSocket>(),
            koin.get<VoiceApi>(),
        ).also {
            val p = prefs.state.value
            it.lembrarAparelhos(p.audioInput, p.audioOutput)
            it.lembrarTratamento(p.micEchoCancel, p.micNoiseSuppression, p.micAutoGain)
            it.lembrarDuasCamadas(p.duasCamadas)
            it.definirVolumes(p.volumeDoMicrofone, p.volumeDaEscuta)
            it.entrar(tipo, sala.id)
            espelhoDosAparelhos?.cancel()
            espelhoDosAparelhos = scope.launch {
                launch { it.microfones.collect { lista -> microfones = lista } }
                launch { it.saidas.collect { lista -> saidas = lista } }
            }
        }
        joined = sala
        emSussurro = tipo == "dm"
        aplicar()
    }

    fun encerrar() {
        leave()
        AtalhosGlobais.observar(emptyMap())
    }

    private var espelhoDosAparelhos: Job? = null

    fun leave() {
        espelhoDosAparelhos?.cancel()
        espelhoDosAparelhos = null
        call?.dispose()
        call = null
        joined = null
        emSussurro = false
    }
}
