package app.astra.desktop.voice

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.desktop.AtalhosGlobais
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.ModoDeFala
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ChannelDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.core.Koin
import org.koin.core.qualifier.named

// Sessao de voz VIVA acima da navegacao.
//
// Antes o VoiceEngine nascia dentro do VoiceView (`remember(channel.id) { ... }`)
// e um DisposableEffect o matava quando a tela saia da composicao. Como abrir uma
// órbita de texto limpa o `voiceChannel` do palco, navegar DESCONECTAVA a call —
// era o "kick automatico". Aqui a sessão mora no shell: so desligar (ou entrar em
// outra sala) encerra.
//
// Dois conceitos que antes eram um so:
//   - `voiceChannel` (ShellVm) = que sala esta NO PALCO. Some ao navegar. Certo.
//   - `joined` (aqui)          = em que sala você esta CONECTADO. Sobrevive.
@Stable
class VoiceSession(private val scope: CoroutineScope, private val koin: Koin) {
    var joined by mutableStateOf<ChannelDto?>(null)
        private set
    var engine by mutableStateOf<VoiceEngine?>(null)
        private set

    // Engine so quando a sala do palco E a sala conectada — o lobby (sala aberta
    // mas não entrou) recebe null e desenha o botao de entrar.
    fun engineFor(channel: ChannelDto?): VoiceEngine? =
        if (channel != null && joined?.id == channel.id) engine else null

    fun join(channel: ChannelDto) = entrar("channel", channel)

    // Chamada de sussurro. A sala do LiveKit e `dm:<conversationId>` e o token
    // dela ja existia — o `connect` sempre foi generico, so ninguem chamava com
    // "dm".
    //
    // O ChannelDto aqui e SINTETICO: `id` = conversa, `name` = nome da pessoa.
    // Ele existe porque a tela de call inteira (VoiceView) e desenhada a partir
    // dele, e os participantes de la ja vem do LiveKit, nao da lista de membros
    // da constelacao — entao a mesma tela serve pra sussurro sem mudanca.
    fun joinDm(conversationId: String, titulo: String) =
        entrar("dm", ChannelDto(id = conversationId, name = titulo, type = "VOICE"))

    // `emSussurro` diz de que tipo e a sala conectada. A UI precisa porque num
    // sussurro nao ha soundboard nem lista de membros pra oferecer.
    var emSussurro by mutableStateOf(false)
        private set

    // MUDO E ENSURDECER MORAM AQUI, e nao no motor, por um motivo simples: os
    // botoes ficam no rodape, que esta na tela o tempo todo, e o motor so existe
    // durante a call. Estado na sessao = a escolha atravessa entrar e sair de sala,
    // e chegar mudo na proxima e uma decisao que da pra tomar antes de entrar.
    //
    // O motor nao guarda nada disto: recebe a ordem ao nascer e a cada mudanca.
    var mudo by mutableStateOf(false)
        private set
    var ensurdecido by mutableStateOf(false)
        private set

    // Quem ensurdece fica mudo junto (padrao Discord: nao se conversa com quem nao
    // se ouve). Ao voltar a ouvir, o microfone volta pro que ERA antes — quem ja
    // estava mudo continua mudo, e nao ganha o mic aberto de brinde.
    private var mudoAntesDeEnsurdecer = false

    // APERTAR PARA FALAR. Não é um terceiro estado de mudo: é uma permissão que se
    // soma. O mudo manual continua ganhando de tudo — segurar a tecla não abre o
    // microfone de quem escolheu ficar calado.
    private var falaSegurada by mutableStateOf(false)

    private val prefs = koin.get<DesktopPrefs>()

    init {
        // Re-registra os atalhos quando as teclas (ou o modo) mudam, e aplica na
        // hora: trocar pra apertar-para-falar no meio de uma call tem de fechar o
        // microfone imediatamente, não na próxima sala.
        scope.launch {
            prefs.state
                .map { listOf(it.modoDeFala.ordinal, it.teclaFalar, it.teclaMudo, it.teclaEnsurdecer) }
                .distinctUntilChanged()
                .collect {
                    if (prefs.state.value.modoDeFala != ModoDeFala.APERTAR) falaSegurada = false
                    registrarAtalhos()
                    aplicar()
                }
        }
    }

    private fun registrarAtalhos() {
        val p = prefs.state.value
        // Um mapa e não três registros: a mesma tecla em dois papéis viraria dois
        // avisos pro mesmo apertar. Aqui o último ganha, e é um só.
        val mapa = buildMap<Int, (Boolean) -> Unit> {
            if (p.teclaMudo != 0) put(p.teclaMudo) { desceu -> if (desceu) naUi { alternarMudo() } }
            if (p.teclaEnsurdecer != 0) put(p.teclaEnsurdecer) { desceu -> if (desceu) naUi { alternarEnsurdecer() } }
            if (p.modoDeFala == ModoDeFala.APERTAR && p.teclaFalar != 0) {
                put(p.teclaFalar) { desceu -> naUi { segurarFala(desceu) } }
            }
        }
        AtalhosGlobais.observar(mapa)
    }

    // O aviso chega na thread do gancho. Tudo daqui pra frente mexe em estado de
    // tela e no motor, então volta pro escopo da interface antes de tocar em nada.
    private fun naUi(acao: () -> Unit) {
        scope.launch { acao() }
    }

    // Segurar tecla repete WM_KEYDOWN dezenas de vezes por segundo. Sem esta
    // guarda, cada repetição viraria um `setMic(true)` e um pedido de sinalização
    // pro servidor — que é como se transforma uma tecla segurada em enxurrada.
    private fun segurarFala(apertado: Boolean) {
        if (falaSegurada == apertado) return
        falaSegurada = apertado
        aplicar()
    }

    fun alternarMudo() {
        if (ensurdecido) {
            // Voltar a falar devolve o ouvido junto. "Falo mas nao ouco" nao e uma
            // intencao que alguem tenha — e so um estado de onde nao se sai clicando
            // no lugar obvio.
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
        val apertarParaFalar = prefs.state.value.modoDeFala == ModoDeFala.APERTAR
        val podeFalar = !mudo && (!apertarParaFalar || falaSegurada)
        engine?.let {
            it.setMic(podeFalar)
            it.setEnsurdecido(ensurdecido)
        }
    }

    private fun entrar(tipo: String, sala: ChannelDto) {
        // "JA ESTOU NESTA SALA" TEM QUE INCLUIR TER MOTOR, e nao so o id bater.
        //
        // `joined` e o motor sao dois campos, e nada garantia que andassem juntos:
        // bastava um caminho deixar `joined` apontando pra sala e o motor nulo
        // (connect que falhou, dispose sem limpar) pra esta funcao virar um beco —
        // ela devolvia na hora, achando que ja estavamos dentro, e NUNCA mais se
        // entrava naquela sala. Sem erro, sem log: o botao simplesmente nao fazia
        // nada, que e o formato do "aceitei a chamada e nao entrei em call nenhuma".
        //
        // Com o motor na condicao, o estado inconsistente se conserta sozinho na
        // proxima tentativa em vez de travar pra sempre.
        if (joined?.id == sala.id && engine != null) return
        // Entrar noutra sala sai da anterior: uma call por vez (como o Discord).
        engine?.dispose()
        engine = VoiceEngine(
            scope,
            koin.get<VoiceApi>(),
            koin.get<OkHttpClient>(named("plain")),
            koin.get<DesktopPrefs>(),
        ).also { it.connect(tipo, sala.id) }
        joined = sala
        emSussurro = tipo == "dm"
        // A escolha do rodape vale na sala nova. `connect` e assincrono, mas o motor
        // guarda a ordem e as pecas nascem obedecendo (ver publishMic/onAddTrack).
        aplicar()
    }

    // Fim da sessão inteira (sair da conta, fechar o shell) — não é o mesmo que
    // desligar de uma call. Solta o gancho de teclado junto: gancho global
    // instalado depois que a tela morreu é a sobra que ninguém vê e todo mundo
    // sente, porque o processo segue escutando o teclado sem ninguém pra ouvir.
    fun encerrar() {
        leave()
        AtalhosGlobais.observar(emptyMap())
    }

    fun leave() {
        engine?.dispose()
        engine = null
        joined = null
        emSussurro = false
    }
}
