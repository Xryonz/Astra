package app.astra.desktop.voice

import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ServidorDeGeloDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

class CallEmMalha(
    private val scope: CoroutineScope,
    private val sidecar: SidecarDeVoz,
    private val socket: DesktopSocket,
    private val voiceApi: VoiceApi,
    private val meuId: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

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
    }

    private val _mostrandoTela = MutableStateFlow<Set<String>>(emptySet())
    val mostrandoTela = _mostrandoTela.asStateFlow()

    private val _monitores = MutableStateFlow<List<MonitorDaTela>?>(null)
    val monitores = _monitores.asStateFlow()

    private val _janelas = MutableStateFlow<List<JanelaDaTela>?>(null)
    val janelas = _janelas.asStateFlow()

    private val conectados = ConcurrentHashMap.newKeySet<String>()

    private val estadoDoPar = ConcurrentHashMap<String, String>()

    private val falando = ConcurrentHashMap.newKeySet<String>()

    private val tentativas = ConcurrentHashMap<String, Int>()

    private val resgates = ConcurrentHashMap<String, Job>()

    @Volatile private var salaAtual: String? = null
    @Volatile private var pronto = false
    private val tarefas = mutableListOf<Job>()

    fun entrar(channelId: String) {
        if (salaAtual == channelId) return
        sair()
        salaAtual = channelId
        _status.value = VoiceStatus.Connecting

        sidecar.ligar()
        socket.voiceJoin(channelId)

        tarefas += scope.launch { buscarGelo() }
        tarefas += scope.launch { ouvirSinais() }
        tarefas += scope.launch { ouvirSidecar() }
        tarefas += scope.launch { ouvirPresenca(channelId) }
        tarefas += scope.launch { manterPresenca(channelId) }
        tarefas += scope.launch { conferirDeTemposEmTempos(channelId) }
    }

    fun sair() {
        val sala = salaAtual ?: return
        salaAtual = null
        pronto = false

        if (_transmitindo.value) pararDeTransmitir()

        socket.voiceLeave(sala)
        for (id in conectados) sidecar.desconectar(id)
        conectados.clear()
        estadoDoPar.clear()
        falando.clear()
        tentativas.clear()
        resgates.values.forEach { it.cancel() }
        resgates.clear()

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

    private fun aplicarPreferencias() {
        microfoneEscolhido?.let { sidecar.usarAparelho("entrada", it) }
        saidaEscolhida?.let { sidecar.usarAparelho("saida", it) }
        sidecar.tratamento(eco, ruido, ganho)
    }

    fun dispose() = sair()

    @Volatile private var gelo: List<ServidorDeGeloDto>? = null

    private suspend fun buscarGelo() {
        val servidores = runCatching { voiceApi.ice().data?.iceServers }
            .onFailure { VoiceLog.nota("[call] sem lista de ICE do servidor: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return

        gelo = servidores
        if (pronto) mandarGelo()
    }

    private fun mandarGelo() {
        val servidores = gelo ?: return

        val stun = mutableListOf<String>()
        val turn = mutableListOf<ServidorTurn>()
        for (servidor in servidores) {
            for (url in servidor.urls) {
                if (url.startsWith("turn:") || url.startsWith("turns:")) {
                    turn += ServidorTurn(url, servidor.username.orEmpty(), servidor.credential.orEmpty())
                } else {
                    stun += url
                }
            }
        }

        if (sidecar.configurar(stun, turn)) {
            VoiceLog.nota("[call] ICE do servidor: ${stun.size} STUN, ${turn.size} TURN")
        }
    }

    private suspend fun manterPresenca(channelId: String) {
        while (true) {
            delay(20_000)
            if (salaAtual != channelId) return
            socket.voiceKeepalive(channelId)
        }
    }

    private suspend fun conferirDeTemposEmTempos(channelId: String) {
        while (true) {
            delay(15_000)
            if (salaAtual != channelId) return
            conferir(channelId)
        }
    }

    private suspend fun conferir(channelId: String) {
        runCatching { voiceApi.presence(channelId).data.orEmpty()[channelId].orEmpty() }
            .onSuccess { lista -> reconciliar(lista) }
    }

    private suspend fun ouvirPresenca(channelId: String) {
        socket.voicePresence.collect { cru ->
            if (salaAtual != channelId) return@collect
            val o = runCatching { json.parseToJsonElement(cru).jsonObject }.getOrNull() ?: return@collect
            if (o["channelId"]?.jsonPrimitive?.content != channelId) return@collect
            val quem = o["userId"]?.jsonPrimitive?.content ?: return@collect
            val entrou = o["joined"]?.jsonPrimitive?.content == "true"
            if (quem == meuId) return@collect
            if (entrou) abrirCom(quem) else fecharCom(quem)
        }
    }

    private fun reconciliar(naSala: List<String>) {
        val devidos = naSala.filter { it != meuId }.toSet()
        for (id in devidos - conectados) abrirCom(id)
        for (id in conectados - devidos) fecharCom(id)
    }

    private fun abrirCom(outro: String) {
        if (conectados.contains(outro)) return

        if (!sidecar.conectar(meuId, outro)) return
        conectados.add(outro)
        publicar()
    }

    private fun fecharCom(outro: String) {
        if (!conectados.remove(outro)) return
        estadoDoPar.remove(outro)
        falando.remove(outro)
        tentativas.remove(outro)
        resgates.remove(outro)?.cancel()
        _mostrandoTela.value = _mostrandoTela.value - outro
        sidecar.quadros.esquecer(outro)
        sidecar.desconectar(outro)
        publicar()
    }

    private fun aoMudarEstado(quem: String, estado: String) {
        when (estado) {
            "connected" -> {
                resgates.remove(quem)?.cancel()
                tentativas.remove(quem)
            }

            "failed" -> agendarResgate(quem, imediato = true)

            "disconnected" -> agendarResgate(quem, imediato = false)
        }
    }

    private fun agendarResgate(quem: String, imediato: Boolean) {
        if (salaAtual == null || !conectados.contains(quem)) return
        resgates.remove(quem)?.cancel()

        val n = tentativas.merge(quem, 1) { a, b -> a + b } ?: 1
        val espera = if (imediato) (1_000L shl (n - 1).coerceAtMost(3)).coerceAtMost(10_000L)
        else 6_000L

        resgates[quem] = scope.launch {
            delay(espera)
            if (salaAtual == null || !conectados.contains(quem)) return@launch
            if (estadoDoPar[quem] == "connected") return@launch

            VoiceLog.nota("[call] refazendo a conexão com $quem (tentativa $n)")

            conectados.remove(quem)
            estadoDoPar.remove(quem)
            falando.remove(quem)
            sidecar.desconectar(quem)
            publicar()
            abrirCom(quem)
        }
    }

    private suspend fun ouvirSinais() {
        socket.sinalRtc.collect { cru ->
            val o = runCatching { json.parseToJsonElement(cru).jsonObject }.getOrNull() ?: return@collect
            val de = o["de"]?.jsonPrimitive?.content ?: return@collect
            val tipo = o["tipo"]?.jsonPrimitive?.content ?: return@collect
            val dados = o["dados"]?.jsonPrimitive?.content ?: return@collect

            abrirCom(de)
            sidecar.repassarSinal(de, tipo, dados)
        }
    }

    private suspend fun ouvirSidecar() {
        sidecar.eventos.collect { ev ->
            when (ev.ev) {
                "pronto" -> {
                    pronto = true
                    if (_inicio.value == null) _inicio.value = System.currentTimeMillis()
                    mandarGelo()
                    aplicarPreferencias()
                    sidecar.pedirAparelhos()
                    salaAtual?.let { sala -> scope.launch { conferir(sala) } }
                    publicar()
                }
                "sinal" -> {
                    val para = ev.par ?: return@collect
                    socket.mandarSinalRtc(para, ev.tipo.orEmpty(), ev.dados.orEmpty())
                }
                "estado" -> {
                    val quem = ev.par ?: return@collect
                    val valor = ev.valor.orEmpty()
                    estadoDoPar[quem] = valor
                    VoiceLog.nota("[call] $quem: $valor")
                    aoMudarEstado(quem, valor)
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
                        if (ev.tipo != "ritmo") VoiceLog.nota("[tela] recebendo de $quem por ${ev.tipo}")
                        _mostrandoTela.value = _mostrandoTela.value + quem
                    } else {
                        _mostrandoTela.value = _mostrandoTela.value - quem
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
                    conectados.clear()
                    estadoDoPar.clear()
                    falando.clear()
                    tentativas.clear()
                    resgates.values.forEach { it.cancel() }
                    resgates.clear()
                    publicar()
                    VoiceLog.nota("[call] o componente de voz reiniciou; refazendo as conexões")
                }
                "erro" -> {
                    VoiceLog.nota("[call] erro: ${ev.msg}")
                }
            }
        }
    }

    private fun publicar() {
        if (salaAtual == null) return

        val outros = conectados.sorted().map { id ->
            VoiceParticipant(identity = id, label = id, speaking = falando.contains(id))
        }

        val vozPassando = pronto &&
            (conectados.isEmpty() || estadoDoPar.values.any { it == "connected" })

        _status.value = VoiceStatus.Connected(
            others = outros,
            audioLive = vozPassando,
            mySpeaking = falando.contains(""),
        )
    }
}
