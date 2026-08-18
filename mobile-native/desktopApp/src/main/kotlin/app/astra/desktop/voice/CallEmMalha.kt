package app.astra.desktop.voice

import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.VoiceApi
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

// A CALL EM MALHA — quem conversa com quem, e o que a tela vê disso.
//
// Em ponto a ponto não existe "entrar numa sala": existe conectar em CADA pessoa
// que já está lá, uma conexão por companheiro. Esta classe é quem sabe disso, e ela
// é a única que sabe — o sidecar só recebe ordens ("conecte-se a fulano") e o resto
// do app não faz ideia de que há N conexões por baixo.
//
// TRÊS FONTES DIZEM QUEM ESTÁ NA SALA, e as três são necessárias:
//
//  1. A CONSULTA ao entrar. Sem ela, quem chega numa call em andamento não
//     conectaria em ninguém — só veria as pessoas que entrassem DEPOIS dele.
//  2. O AVISO por socket (entrou/saiu). É o caminho rápido, e o que faz alguém
//     aparecer na hora em vez de no próximo giro.
//  3. A CONSULTA periódica. Rede de segurança: aviso perdido por reconexão de
//     socket deixaria um par mudo para sempre, sem nada indicando o porquê.
//
// Uma só das três não basta, e é por isso que as três existem.
class CallEmMalha(
    private val scope: CoroutineScope,
    private val sidecar: SidecarDeVoz,
    private val socket: DesktopSocket,
    private val voiceApi: VoiceApi,
    private val meuId: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ---- o que a tela lê ----

    private val _status = MutableStateFlow<VoiceStatus>(VoiceStatus.Connecting)
    val status = _status.asStateFlow()

    // Quando a call começou, para o cronômetro. Nasce no `pronto` do sidecar e não
    // no `entrar`: antes disso não há voz nenhuma, e um cronômetro que começa a
    // contar antes de existir call mede a espera, não a conversa.
    private val _inicio = MutableStateFlow<Long?>(null)
    val inicio = _inicio.asStateFlow()

    // ---- o que ele guarda para chegar naquilo ----

    // Concorrentes porque mexem neles o laço de presença, o aviso de socket e os
    // eventos do sidecar — três origens, em corrotinas diferentes.
    private val conectados = ConcurrentHashMap.newKeySet<String>()

    // Estado da conexão com cada par, como o Pion o reporta.
    private val estadoDoPar = ConcurrentHashMap<String, String>()

    // Quem está falando agora. String vazia sou eu — é a convenção da ponte, e ela
    // existe porque o processo de voz não sabe (nem precisa saber) o meu id.
    private val falando = ConcurrentHashMap.newKeySet<String>()

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

        socket.voiceLeave(sala)
        for (id in conectados) sidecar.desconectar(id)
        conectados.clear()
        estadoDoPar.clear()
        falando.clear()

        tarefas.forEach { it.cancel() }
        tarefas.clear()
        sidecar.parar()

        _inicio.value = null
        _status.value = VoiceStatus.Closed
    }

    // Os dois nomes que a VoiceSession usa. `podeFalar` é o contrário de mudo — a
    // sessão raciocina em "pode falar" (mudo + apertar-para-falar somados) e o
    // processo de voz raciocina em "está mudo".
    fun setMic(podeFalar: Boolean) {
        sidecar.mudo(!podeFalar)
        // Fechar o microfone apaga o meu indicador na hora. Esperar o sidecar
        // perceber o silêncio levaria a espera inteira do detector, e o círculo
        // ficaria aceso quase meio segundo depois de eu já estar mudo.
        if (!podeFalar && falando.remove("")) publicar()
    }

    fun setEnsurdecido(on: Boolean) = sidecar.surdo(on)

    fun dispose() = sair()

    // ---- as três fontes de "quem está na sala" ----

    // Renova a marca a cada 20 segundos. O servidor derruba quem para de renovar em
    // 60 — três tentativas de folga, para que um engasgo de rede não tire ninguém
    // da call por acidente.
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

    // Acerta o conjunto conectado com a lista de verdade: abre com quem falta e
    // fecha com quem sobrou. É a única função que precisa saber das duas coisas ao
    // mesmo tempo, e por isso a reconciliação vive num lugar só.
    private fun reconciliar(naSala: List<String>) {
        val devidos = naSala.filter { it != meuId }.toSet()
        for (id in devidos - conectados) abrirCom(id)
        for (id in conectados - devidos) fecharCom(id)
    }

    private fun abrirCom(outro: String) {
        if (conectados.contains(outro)) return

        // SÓ CONTA COMO CONECTADO SE A ORDEM SAIU DE VERDADE.
        //
        // O processo de voz demora um instante para subir, e comando mandado antes
        // disso não vai a lugar nenhum — some em silêncio. Se marcássemos a pessoa
        // como conectada mesmo assim, a conferência seguinte veria "já está lá" e
        // NUNCA tentaria de novo: um par mudo para o resto da call, sem erro em
        // lugar nenhum. Registrar só o que saiu faz a próxima volta consertar.
        //
        // QUEM OFERECE É DECIDIDO PELO ID, e a regra é a mesma dos dois lados. Sem
        // regra, os dois ofereceriam ao mesmo tempo assim que se vissem, e as duas
        // ofertas colidiriam — o encontro de ofertas, que produz uma conexão que
        // nunca fecha. Comparar os ids resolve sem nenhuma troca de mensagem.
        if (!sidecar.conectar(meuId, outro)) return
        conectados.add(outro)
        publicar()
    }

    private fun fecharCom(outro: String) {
        if (!conectados.remove(outro)) return
        estadoDoPar.remove(outro)
        falando.remove(outro)
        sidecar.desconectar(outro)
        publicar()
    }

    // ---- a ponte de sinalização, nos dois sentidos ----

    private suspend fun ouvirSinais() {
        socket.sinalRtc.collect { cru ->
            val o = runCatching { json.parseToJsonElement(cru).jsonObject }.getOrNull() ?: return@collect
            val de = o["de"]?.jsonPrimitive?.content ?: return@collect
            val tipo = o["tipo"]?.jsonPrimitive?.content ?: return@collect
            val dados = o["dados"]?.jsonPrimitive?.content ?: return@collect

            // Oferta de quem ainda não conhecemos ABRE a conexão em vez de ser
            // descartada. Acontece de verdade: quem entrou depois pode nos ver antes
            // de nós o vermos, porque as três fontes de presença não chegam na mesma
            // ordem para os dois lados.
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
                    // CONFERIR AGORA, e não daqui a quinze segundos. Este é o único
                    // instante em que sabemos que os comandos passam a chegar — e
                    // tudo que foi tentado antes disso se perdeu. Esperar o giro
                    // normal seria entrar numa call e ficar quinze segundos em
                    // silêncio sem nada explicando por quê.
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
                    publicar()
                }
                "fala" -> {
                    // Par vazio sou eu — a convenção da ponte.
                    val quem = ev.par.orEmpty()
                    val mudou = if (ev.valor == "1") falando.add(quem) else falando.remove(quem)
                    if (mudou) publicar()
                }
                "caiu" -> {
                    // O processo voltou do zero: ele não tem mais conexão nenhuma.
                    // Esquecer quem estava conectado faz a próxima conferência
                    // reabrir tudo — sem isto, o conjunto diria "já conectado" e a
                    // call ficaria muda para sempre depois de um reinício.
                    pronto = false
                    conectados.clear()
                    estadoDoPar.clear()
                    falando.clear()
                    publicar()
                    VoiceLog.nota("[call] o componente de voz reiniciou; refazendo as conexões")
                }
                "erro" -> {
                    VoiceLog.nota("[call] erro: ${ev.msg}")
                    // Erro do sidecar não derruba a call: pode ser um fone que caiu
                    // no meio, e as conexões continuam de pé. Quem decide desistir é
                    // a pessoa, não o log.
                }
            }
        }
    }

    // Monta o retrato que a tela lê. Chamado só quando algo mudou de verdade — a
    // detecção de fala já chega aqui como transição, não como nível.
    private fun publicar() {
        if (salaAtual == null) return

        val outros = conectados.sorted().map { id ->
            VoiceParticipant(identity = id, label = id, speaking = falando.contains(id))
        }

        // "A VOZ ESTÁ PASSANDO" É DIFERENTE DE "ESTOU NA SALA".
        //
        // Sozinho na sala, passando: não há a quem ouvir, e nada está errado. Com
        // gente e nenhum par conectado, a tela precisa dizer isso — é exatamente a
        // falha que a pessoa está sentindo quando ninguém a escuta, e chamar aquilo
        // de "conectado" esconde a única pista que ela tinha.
        val vozPassando = pronto &&
            (conectados.isEmpty() || estadoDoPar.values.any { it == "connected" })

        _status.value = VoiceStatus.Connected(
            others = outros,
            audioLive = vozPassando,
            mySpeaking = falando.contains(""),
        )
    }
}
