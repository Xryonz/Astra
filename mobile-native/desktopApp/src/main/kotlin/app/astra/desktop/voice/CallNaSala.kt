package app.astra.desktop.voice

import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.VoiceTokenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class CallNaSala(
    private val scope: CoroutineScope,
    private val sidecar: SidecarDeVoz,
    private val socket: DesktopSocket,
    private val voiceApi: VoiceApi,
) {
    private val _status = MutableStateFlow<VoiceStatus>(VoiceStatus.Connecting)
    val status = _status.asStateFlow()

    private val _inicio = MutableStateFlow<Long?>(null)
    val inicio = _inicio.asStateFlow()

    private val _microfones = MutableStateFlow<List<AparelhoDeAudio>>(emptyList())
    val microfones = _microfones.asStateFlow()

    private val _saidas = MutableStateFlow<List<AparelhoDeAudio>>(emptyList())
    val saidas = _saidas.asStateFlow()

    private val _transmitindo = MutableStateFlow(false)
    val transmitindo = _transmitindo.asStateFlow()

    private val _relatorioDaTela = MutableStateFlow("")
    val relatorioDaTela = _relatorioDaTela.asStateFlow()
    @Volatile private var comoSubiu = ""

    @Volatile private var porQueCaiu = ""

    val telasDosOutros = sidecar.quadros.telas

    val quemTemTela = sidecar.quadros.quemTransmite

    companion object {
        const val EU = ""
        const val VOLUME_CHEIO = 100
    }

    private val _mostrandoTela = MutableStateFlow<Set<String>>(emptySet())
    val mostrandoTela = _mostrandoTela.asStateFlow()

    private val _ritmoDeQuemMostra = MutableStateFlow<Map<String, String>>(emptyMap())
    val ritmoDeQuemMostra = _ritmoDeQuemMostra.asStateFlow()

    private val _monitores = MutableStateFlow<List<MonitorDaTela>?>(null)
    val monitores = _monitores.asStateFlow()

    private val _janelas = MutableStateFlow<List<JanelaDaTela>?>(null)
    val janelas = _janelas.asStateFlow()

    private val naSala = ConcurrentHashMap.newKeySet<String>()

    private val falando = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var salaAtual: String? = null
    @Volatile private var pronto = false
    @Volatile private var credencial: VoiceTokenRequest? = null
    private val tarefas = mutableListOf<Job>()

    fun entrar(tipo: String, channelId: String) {
        if (salaAtual == channelId) return
        sair()
        salaAtual = channelId
        credencial = VoiceTokenRequest(roomKind = tipo, roomId = channelId)
        _status.value = VoiceStatus.Connecting

        sidecar.ligar()
        socket.voiceJoin(channelId)

        tarefas += scope.launch { ouvirSidecar() }
        tarefas += scope.launch { manterPresenca(channelId) }
    }

    fun sair() {
        val sala = salaAtual ?: return
        salaAtual = null
        credencial = null
        pronto = false

        if (_transmitindo.value) pararDeTransmitir()

        socket.voiceLeave(sala)
        sidecar.deixarSala()
        naSala.clear()
        falando.clear()

        tarefas.forEach { it.cancel() }
        tarefas.clear()
        sidecar.parar()
        noPalco = null

        _inicio.value = null
        _status.value = VoiceStatus.Closed
    }

    fun setMic(podeFalar: Boolean) {
        sidecar.mudo(!podeFalar)
        if (!podeFalar && falando.remove("")) publicar()
    }

    fun setEnsurdecido(on: Boolean) = sidecar.surdo(on)

    fun transmitir(monitor: Int, largura: Int, altura: Int, fps: Int, kbps: Int) {
        sidecar.transmitir(monitor, largura, altura, fps, kbps)
    }

    fun transmitirJanela(janela: ULong, largura: Int, altura: Int, fps: Int, kbps: Int) {
        sidecar.transmitirJanela(janela, largura, altura, fps, kbps)
    }

    fun pedirMonitores() {
        _monitores.value = null
        sidecar.pedirMonitores()
    }

    fun pedirJanelas() {
        _janelas.value = null
        sidecar.pedirJanelas()
    }

    fun assistir(par: String?) {
        val alvo = par.orEmpty()
        if (alvo == noPalco) return
        noPalco = alvo
        sidecar.assistir(par)
    }

    private val _volumes = MutableStateFlow<Map<String, Int>>(emptyMap())
    val volumes = _volumes.asStateFlow()

    fun volumeDe(par: String): Int = _volumes.value[par] ?: VOLUME_CHEIO

    fun definirVolume(par: String, porcento: Int) {
        val alvo = porcento.coerceIn(0, VOLUME_CHEIO)
        if (volumeDe(par) == alvo) return
        _volumes.value =
            if (alvo == VOLUME_CHEIO) _volumes.value - par else _volumes.value + (par to alvo)
        sidecar.volume(par, alvo)
    }

    @Volatile private var noPalco: String? = null

    fun pararDeTransmitir() {
        _transmitindo.value = false
        Transmitindo.marcar(false)
        _relatorioDaTela.value = ""
        sidecar.pararDeTransmitir()
    }

    @Volatile private var microfoneEscolhido: String? = null
    @Volatile private var saidaEscolhida: String? = null

    @Volatile private var eco = true
    @Volatile private var ruido = true
    @Volatile private var ganho = true

    fun atualizarAparelhos() = sidecar.pedirAparelhos()

    fun escolherMicrofone(id: String?) {
        microfoneEscolhido = id
        sidecar.usarAparelho("entrada", id)
    }

    fun escolherSaida(id: String?) {
        saidaEscolhida = id
        sidecar.usarAparelho("saida", id)
    }

    fun lembrarAparelhos(microfone: String?, saida: String?) {
        microfoneEscolhido = microfone
        saidaEscolhida = saida
    }

    fun lembrarTratamento(eco: Boolean, ruido: Boolean, ganho: Boolean) {
        this.eco = eco
        this.ruido = ruido
        this.ganho = ganho
    }

    fun definirTratamento(eco: Boolean, ruido: Boolean, ganho: Boolean) {
        lembrarTratamento(eco, ruido, ganho)
        sidecar.tratamento(eco, ruido, ganho)
    }

    fun dispose() = sair()

    private fun aplicarPreferencias() {
        microfoneEscolhido?.let { sidecar.usarAparelho("entrada", it) }
        saidaEscolhida?.let { sidecar.usarAparelho("saida", it) }
        sidecar.tratamento(eco, ruido, ganho)
    }

    private suspend fun pedirCredencialEEntrar() {
        val pedido = credencial ?: return
        val dados = runCatching { voiceApi.token(pedido).data }
            .onFailure { VoiceLog.nota("[call] o servidor negou a credencial da sala: ${it.message}") }
            .getOrNull()

        if (dados == null) {
            _status.value = VoiceStatus.Failed("Não consegui entrar na chamada")
            return
        }
        if (credencial != pedido) return

        VoiceLog.nota("[call] entrando em ${dados.roomName ?: pedido.roomId}")
        sidecar.entrarNaSala(dados.url, dados.token)
    }

    private suspend fun manterPresenca(channelId: String) {
        while (true) {
            delay(20_000)
            if (salaAtual != channelId) return
            socket.voiceKeepalive(channelId)
        }
    }

    private suspend fun ouvirSidecar() {
        sidecar.eventos.collect { ev ->
            when (ev.ev) {
                "pronto" -> {
                    pronto = true
                    if (_inicio.value == null) _inicio.value = System.currentTimeMillis()
                    aplicarPreferencias()
                    sidecar.pedirAparelhos()
                    pedirCredencialEEntrar()
                    publicar()
                }
                "estado" -> {
                    val quem = ev.par.orEmpty()
                    val valor = ev.valor.orEmpty()
                    if (quem.isEmpty()) {
                        VoiceLog.nota("[call] sala: $valor")
                        if (valor == "disconnected") {
                            naSala.clear()
                            falando.clear()
                        }
                    } else {
                        VoiceLog.nota("[call] $quem: $valor")
                        if (valor == "connected") naSala.add(quem) else esquecer(quem)
                    }
                    publicar()
                }
                "aparelhos" -> {
                    val lista = ev.aparelhos.orEmpty()
                    if (ev.tipo == "entrada") _microfones.value = lista else _saidas.value = lista
                }
                "monitores" -> _monitores.value = ev.monitores.orEmpty()
                "janelas" -> _janelas.value = ev.janelas.orEmpty()
                "tela" -> {
                    val quem = ev.par ?: return@collect
                    if (ev.valor == "1") {
                        if (ev.tipo == "ritmo") {
                            _ritmoDeQuemMostra.value = _ritmoDeQuemMostra.value + (quem to ev.msg.orEmpty())
                        } else {
                            VoiceLog.nota("[tela] recebendo de $quem por ${ev.tipo}")
                        }
                        _mostrandoTela.value = _mostrandoTela.value + quem
                    } else {
                        _mostrandoTela.value = _mostrandoTela.value - quem
                        _ritmoDeQuemMostra.value = _ritmoDeQuemMostra.value - quem
                        sidecar.quadros.esquecer(quem)
                    }
                }
                "transmissao" -> {
                    val noAr = ev.valor == "1"
                    _transmitindo.value = noAr
                    Transmitindo.marcar(noAr)
                    when {
                        !noAr -> { comoSubiu = ""; porQueCaiu = ""; _relatorioDaTela.value = "" }
                        ev.tipo == "ritmo" -> _relatorioDaTela.value =
                            listOf(comoSubiu, ev.msg.orEmpty(), porQueCaiu)
                                .filter { it.isNotBlank() }.joinToString(" · ")
                        ev.tipo == "taxa" -> {
                            porQueCaiu = ev.msg.orEmpty()
                            VoiceLog.nota("[tela] taxa rebaixada: $porQueCaiu")
                        }
                        ev.tipo == "perfil" -> VoiceLog.nota("[tela] perfil no fluxo: ${ev.msg}")
                        else -> {
                            comoSubiu = listOf(ev.tipo.orEmpty(), ev.msg.orEmpty())
                                .filter { it.isNotBlank() }.joinToString(" · ")
                            _relatorioDaTela.value = comoSubiu
                            VoiceLog.nota("[tela] transmitindo: $comoSubiu")
                        }
                    }
                }
                "fala" -> {
                    val quem = ev.par.orEmpty()
                    val mudou = if (ev.valor == "1") falando.add(quem) else falando.remove(quem)
                    if (mudou) publicar()
                }
                "caiu" -> {
                    pronto = false
                    naSala.clear()
                    falando.clear()
                    publicar()
                    VoiceLog.nota("[call] o componente de voz reiniciou; entrando na sala de novo")
                }
                "erro" -> {
                    VoiceLog.nota("[call] erro: ${ev.msg}")
                }
            }
        }
    }

    private fun esquecer(quem: String) {
        naSala.remove(quem)
        falando.remove(quem)
        _mostrandoTela.value = _mostrandoTela.value - quem
        sidecar.quadros.esquecer(quem)
    }

    private fun publicar() {
        if (salaAtual == null) return

        val outros = naSala.sorted().map { id ->
            VoiceParticipant(identity = id, label = id, speaking = falando.contains(id))
        }

        _status.value = VoiceStatus.Connected(
            others = outros,
            audioLive = pronto,
            mySpeaking = falando.contains(""),
        )
    }
}
