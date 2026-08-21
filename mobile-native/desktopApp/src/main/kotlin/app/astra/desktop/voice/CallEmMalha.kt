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

    // Os aparelhos que o Windows tem. Chegam do processo de voz, que é quem fala
    // WASAPI — a JVM enxerga uma lista diferente e incompleta, e listar por um
    // caminho e abrir por outro é como se acaba escolhendo um aparelho e ouvindo
    // outro.
    private val _microfones = MutableStateFlow<List<AparelhoDeAudio>>(emptyList())
    val microfones = _microfones.asStateFlow()

    private val _saidas = MutableStateFlow<List<AparelhoDeAudio>>(emptyList())
    val saidas = _saidas.asStateFlow()

    // A TRANSMISSÃO DE TELA, e QUEM MANDA NO ESTADO É O PROCESSO DE VOZ.
    //
    // O botão não acende ao ser apertado: acende quando o outro lado confirma que a
    // captura e o compressor subiram — o que leva quase um segundo e pode falhar (não
    // existe compressor de H.264 em toda máquina). Acender no clique e apagar depois é
    // o padrão que ensina a pessoa a não confiar no próprio botão.
    private val _transmitindo = MutableStateFlow(false)
    val transmitindo = _transmitindo.asStateFlow()

    // O que está subindo agora: "Intel® Quick Sync… · 1280x720 @30 · 58 fps · 3,4 Mbps".
    // Vazio quando não há transmissão. É a única prova visível enquanto não existe
    // imagem para ver — ver a fatia 2.
    private val _relatorioDaTela = MutableStateFlow("")
    val relatorioDaTela = _relatorioDaTela.asStateFlow()
    @Volatile private var comoSubiu = ""

    // ---- o que ele guarda para chegar naquilo ----

    // Concorrentes porque mexem neles o laço de presença, o aviso de socket e os
    // eventos do sidecar — três origens, em corrotinas diferentes.
    private val conectados = ConcurrentHashMap.newKeySet<String>()

    // Estado da conexão com cada par, como o Pion o reporta.
    private val estadoDoPar = ConcurrentHashMap<String, String>()

    // Quem está falando agora. String vazia sou eu — é a convenção da ponte, e ela
    // existe porque o processo de voz não sabe (nem precisa saber) o meu id.
    private val falando = ConcurrentHashMap.newKeySet<String>()

    // Quantas vezes seguidas tentamos refazer a conexão com cada pessoa. Zera assim
    // que ela conecta — é o que faz a espera voltar a ser curta depois de uma queda
    // isolada, em vez de carregar para sempre a punição de um problema já resolvido.
    private val tentativas = ConcurrentHashMap<String, Int>()

    // Uma corrotina por pessoa esperando para agir sobre a queda dela. Guardadas
    // para poder cancelar: a conexão que volta sozinha tem de cancelar o resgate que
    // estava a caminho, senão o resgate derruba justamente o que acabou de sarar.
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

        // A TELA PARA JUNTO. Sair da call sem parar a transmissão deixaria a captura
        // e o compressor rodando — placa ocupada e a luz de "transmitindo" acesa numa
        // sala de que a pessoa já saiu. O processo cai logo depois e resolveria isso
        // sozinho, mas depender disso é depender de um efeito colateral.
        if (_transmitindo.value) pararDeTransmitir()

        socket.voiceLeave(sala)
        for (id in conectados) sidecar.desconectar(id)
        conectados.clear()
        estadoDoPar.clear()
        falando.clear()
        tentativas.clear()
        // Resgate pendente de uma call que acabou reabriria conexão numa sala de que
        // já se saiu. Cancelar aqui é o que impede o fantasma.
        resgates.values.forEach { it.cancel() }
        resgates.clear()

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

    // ---- transmissão de tela ----

    fun transmitir(monitor: Int, largura: Int, altura: Int, fps: Int, kbps: Int) {
        sidecar.transmitir(monitor, largura, altura, fps, kbps)
    }

    fun pararDeTransmitir() {
        // APAGA AQUI, sem esperar a confirmação — ao contrário de acender.
        //
        // A assimetria é de propósito: acender cedo promete o que ainda não existe;
        // apagar cedo cumpre na hora o que a pessoa pediu. Quem aperta "parar" quer
        // parar de mostrar a tela AGORA, e o evento de volta só confirma.
        _transmitindo.value = false
        Transmitindo.marcar(false)
        _relatorioDaTela.value = ""
        sidecar.pararDeTransmitir()
    }

    // ---- aparelhos ----

    // A ESCOLHA FICA GUARDADA AQUI, e não só enviada.
    //
    // O processo de voz pode reiniciar no meio da call (é justamente para isso que
    // ele é um processo à parte). Quando volta, ele volta com o aparelho padrão do
    // Windows — a escolha da pessoa mora do lado de cá. Sem reenviar no `pronto`, a
    // primeira queda do processo desfaria silenciosamente a configuração dela.
    @Volatile private var microfoneEscolhido: String? = null
    @Volatile private var saidaEscolhida: String? = null

    // O tratamento do microfone entra na MESMA regra dos aparelhos, e pelo mesmo
    // motivo: o processo de voz volta do zero quando cai, e volta nos padrões dele.
    // Quem desligou algo de propósito veria a escolha se desfazer sozinha, sem nada
    // na tela mudando — o pior tipo de defeito, o que se desmancha sem aviso.
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

    // Chamado pela sessão ao entrar, com o que estava salvo nas preferências.
    fun lembrarAparelhos(microfone: String?, saida: String?) {
        microfoneEscolhido = microfone
        saidaEscolhida = saida
    }

    fun lembrarTratamento(eco: Boolean, ruido: Boolean, ganho: Boolean) {
        this.eco = eco
        this.ruido = ruido
        this.ganho = ganho
    }

    // Mudança em plena call. Guarda E manda: guardar sozinho não teria efeito agora,
    // mandar sozinho se perderia na próxima queda do processo.
    fun definirTratamento(eco: Boolean, ruido: Boolean, ganho: Boolean) {
        lembrarTratamento(eco, ruido, ganho)
        sidecar.tratamento(eco, ruido, ganho)
    }

    private fun aplicarPreferencias() {
        microfoneEscolhido?.let { sidecar.usarAparelho("entrada", it) }
        saidaEscolhida?.let { sidecar.usarAparelho("saida", it) }
        // SEMPRE, e não só quando difere do padrão do processo. Os padrões dos dois
        // lados não coincidem — o objeto do Windows nasce com o ganho automático
        // DESLIGADO e o Astra mostra ele ligado — e "mandar só o que mudou" exigiria
        // manter aqui uma cópia fiel dos padrões de lá. Cópia de padrão alheio é a
        // coisa que envelhece errado em silêncio.
        sidecar.tratamento(eco, ruido, ganho)
    }

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
        tentativas.remove(outro)
        resgates.remove(outro)?.cancel()
        sidecar.desconectar(outro)
        publicar()
    }

    // ---- a conexão que cai no meio da call ----

    // QUINZE SEGUNDOS É TEMPO DEMAIS PARA FICAR MUDO.
    //
    // A reconferência periódica já reabriria a conexão, mas só no próximo giro — e
    // quinze segundos de alguém sumido é tempo suficiente para a pessoa concluir que
    // a call quebrou e sair dela. O processo de voz já relata o estado de cada par;
    // faltava alguém escutando.
    private fun aoMudarEstado(quem: String, estado: String) {
        when (estado) {
            "connected" -> {
                // Voltou. Cancela o resgate a caminho e perdoa o histórico: uma queda
                // isolada não pode deixar a próxima tentativa lenta para sempre.
                resgates.remove(quem)?.cancel()
                tentativas.remove(quem)
            }

            // O ICE desistiu. Não volta sozinho — só refazendo.
            "failed" -> agendarResgate(quem, imediato = true)

            // ESPERAR ANTES DE AGIR, e essa espera é o ponto.
            //
            // `disconnected` é quase sempre passageiro: uma troca de rede, um pico de
            // perda, o Wi-Fi hesitando. O ICE se recupera sozinho na maior parte das
            // vezes. Derrubar e refazer na primeira suspeita transformaria um engasgo
            // de dois segundos numa reconexão completa — trocar um tropeço por uma
            // queda.
            "disconnected" -> agendarResgate(quem, imediato = false)
        }
    }

    private fun agendarResgate(quem: String, imediato: Boolean) {
        if (salaAtual == null || !conectados.contains(quem)) return
        resgates.remove(quem)?.cancel()

        val n = tentativas.merge(quem, 1) { a, b -> a + b } ?: 1
        // Espera crescente com teto: 1s, 2s, 4s, 8s, e daí em diante 10s. Sem o
        // crescimento, um par que falha por um motivo permanente (rede que não deixa)
        // viraria um laço de refazer conexão sem parar, gastando processador para
        // não resolver nada.
        val espera = if (imediato) (1_000L shl (n - 1).coerceAtMost(3)).coerceAtMost(10_000L)
        else 6_000L

        resgates[quem] = scope.launch {
            delay(espera)
            // Confere de novo DEPOIS de esperar: nesse meio-tempo a pessoa pode ter
            // saído da call, ou a conexão pode ter voltado sozinha.
            if (salaAtual == null || !conectados.contains(quem)) return@launch
            if (estadoDoPar[quem] == "connected") return@launch

            VoiceLog.nota("[call] refazendo a conexão com $quem (tentativa $n)")

            // Fecha e reabre em vez de remendar. Refazer do zero é o caminho que já
            // sabemos que funciona — é o mesmo que acontece quando alguém entra na
            // sala — e não depende de o outro lado cooperar com uma renegociação.
            //
            // Os dois lados detectam a queda e refazem, mas isso não gera colisão: a
            // regra de quem oferece continua sendo o id menor, e quem receber uma
            // oferta de alguém que não está mais no conjunto abre a conexão ao
            // recebê-la (ver `ouvirSinais`).
            conectados.remove(quem)
            estadoDoPar.remove(quem)
            falando.remove(quem)
            sidecar.desconectar(quem)
            publicar()
            abrirCom(quem)
        }
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
                    // O aparelho escolhido e a lista, nesta ordem: aplicar primeiro
                    // evita a call abrir alguns segundos no microfone errado.
                    aplicarPreferencias()
                    sidecar.pedirAparelhos()
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
                    aoMudarEstado(quem, valor)
                    publicar()
                }
                "aparelhos" -> {
                    val lista = ev.aparelhos.orEmpty()
                    if (ev.tipo == "entrada") _microfones.value = lista else _saidas.value = lista
                }
                "transmissao" -> {
                    val noAr = ev.valor == "1"
                    _transmitindo.value = noAr
                    Transmitindo.marcar(noAr)
                    when {
                        !noAr -> { comoSubiu = ""; _relatorioDaTela.value = "" }
                        // O relatório por segundo se soma ao que subiu, e não o
                        // substitui: sem o nome do compressor a pessoa vê "14 fps" e
                        // não tem como saber se caiu para software.
                        ev.tipo == "ritmo" -> _relatorioDaTela.value = listOf(comoSubiu, ev.msg.orEmpty())
                            .filter { it.isNotBlank() }.joinToString(" · ")
                        // O perfil é diagnóstico, não recado: vai pro registro e não
                        // pra tela. Quem precisa dele é quem for escrever o
                        // decodificador, não quem está compartilhando a tela.
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
                    tentativas.clear()
                    // Os resgates em voo miravam conexões de um processo que não
                    // existe mais. Quem refaz tudo agora é o `pronto`, que vem a
                    // seguir e dispara uma conferência inteira.
                    resgates.values.forEach { it.cancel() }
                    resgates.clear()
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
