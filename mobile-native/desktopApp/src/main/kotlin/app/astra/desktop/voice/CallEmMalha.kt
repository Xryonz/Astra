package app.astra.desktop.voice

import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.VoiceApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

// A CALL EM MALHA — quem conversa com quem.
//
// Em ponto a ponto não existe "entrar numa sala": existe conectar em CADA pessoa
// que já está lá, uma conexão por companheiro. Esta classe é quem sabe disso, e
// ela é a única que sabe — o sidecar só recebe ordens ("conecte-se a fulano") e o
// resto do app não faz ideia de que há N conexões por baixo.
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

    // Com quem já estamos conectados. Concorrente porque mexem nele o laço de
    // presença, o aviso de socket e os eventos do sidecar — três origens.
    private val conectados = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var salaAtual: String? = null
    private var tarefas = mutableListOf<Job>()

    fun entrar(channelId: String) {
        if (salaAtual == channelId) return
        sair()
        salaAtual = channelId

        sidecar.ligar()
        socket.voiceJoin(channelId)

        tarefas += scope.launch { ouvirSinais() }
        tarefas += scope.launch { ouvirSidecar() }
        tarefas += scope.launch { ouvirPresenca(channelId) }
        tarefas += scope.launch { manterPresenca(channelId) }
        tarefas += scope.launch { conferirParticipantes(channelId, primeira = true) }
    }

    fun sair() {
        val sala = salaAtual ?: return
        salaAtual = null

        socket.voiceLeave(sala)
        for (id in conectados) sidecar.desconectar(id)
        conectados.clear()

        tarefas.forEach { it.cancel() }
        tarefas.clear()
        sidecar.parar()
    }

    fun mudo(on: Boolean) = sidecar.mudo(on)

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

    private suspend fun conferirParticipantes(channelId: String, primeira: Boolean) {
        // Na primeira volta não espera: quem entra quer ouvir gente agora.
        if (!primeira) delay(15_000)
        while (true) {
            if (salaAtual != channelId) return
            runCatching { voiceApi.presence(channelId).data.orEmpty()[channelId].orEmpty() }
                .onSuccess { lista -> reconciliar(lista) }
            delay(15_000)
        }
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
    // mesmo tempo, e é por isso que a reconciliação vive num lugar só.
    private fun reconciliar(naSala: List<String>) {
        val devidos = naSala.filter { it != meuId }.toSet()
        for (id in devidos - conectados) abrirCom(id)
        for (id in conectados - devidos) fecharCom(id)
    }

    private fun abrirCom(outro: String) {
        if (!conectados.add(outro)) return
        // QUEM OFERECE É DECIDIDO PELO ID, e a regra é a mesma dos dois lados.
        //
        // Sem regra, os dois ofereceriam ao mesmo tempo assim que se vissem, e as
        // duas ofertas colidiriam — o famoso encontro de ofertas, que produz uma
        // conexão que nunca fecha. Comparar os ids resolve isso sem nenhuma troca
        // de mensagem: os dois chegam à mesma conclusão sozinhos.
        sidecar.conectar(meuId, outro)
    }

    private fun fecharCom(outro: String) {
        if (!conectados.remove(outro)) return
        sidecar.desconectar(outro)
    }

    // ---- a ponte de sinalização, nos dois sentidos ----

    private suspend fun ouvirSinais() {
        socket.sinalRtc.collect { cru ->
            val o = runCatching { json.parseToJsonElement(cru).jsonObject }.getOrNull() ?: return@collect
            val de = o["de"]?.jsonPrimitive?.content ?: return@collect
            val tipo = o["tipo"]?.jsonPrimitive?.content ?: return@collect
            val dados = o["dados"]?.jsonPrimitive?.content ?: return@collect

            // Oferta de quem ainda não conhecemos ABRE a conexão em vez de ser
            // descartada. Acontece de verdade: quem entrou depois pode nos ver
            // antes de nós o vermos, porque as três fontes de presença não chegam
            // na mesma ordem para os dois lados.
            if (conectados.add(de)) sidecar.conectar(meuId, de)
            sidecar.repassarSinal(de, tipo, dados)
        }
    }

    private suspend fun ouvirSidecar() {
        sidecar.eventos.collect { ev ->
            when (ev.ev) {
                "sinal" -> {
                    val para = ev.par ?: return@collect
                    socket.mandarSinalRtc(para, ev.tipo.orEmpty(), ev.dados.orEmpty())
                }
                "estado" -> VoiceLog.nota("[call] ${ev.par}: ${ev.valor}")
                "caiu" -> {
                    // O processo voltou do zero: ele não tem mais conexão nenhuma.
                    // Esquecer quem estava conectado faz a próxima conferência
                    // reabrir tudo — sem isto, o conjunto diria "já conectado" e a
                    // call ficaria muda para sempre depois de um reinício.
                    conectados.clear()
                    VoiceLog.nota("[call] o componente de voz reiniciou; refazendo as conexões")
                }
                "erro" -> VoiceLog.nota("[call] erro: ${ev.msg}")
            }
        }
    }
}
