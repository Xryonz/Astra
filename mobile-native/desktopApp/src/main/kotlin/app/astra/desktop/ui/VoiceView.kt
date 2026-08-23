package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.astra.desktop.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.MicOff
import com.composables.icons.lucide.Volume2
import androidx.compose.runtime.rememberCoroutineScope
import app.astra.mobile.core.network.SoundApi
import app.astra.mobile.core.network.dto.ServerSoundDto
import app.astra.mobile.core.network.dto.TocarSomRequest
import kotlinx.coroutines.launch
import com.composables.icons.lucide.PhoneOff
import com.composables.icons.lucide.ScreenShare
import com.composables.icons.lucide.Settings
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.prefs.DesktopPrefs
import kotlin.math.cos
import kotlin.math.sin
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.voice.AparelhoDeAudio
import app.astra.desktop.voice.CallEmMalha
import app.astra.desktop.voice.VoiceStatus
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import org.koin.core.context.GlobalContext

// Sala de voz — V3..V6: audio bidirecional (mute), transmissão de tela a 60fps,
// palco de video remoto e speaking indicators
// (plano: docs/plans/2026-07-10-astra-voz-nativa.md).
@Composable
fun VoiceView(
    channel: ChannelDto,
    members: List<ServerMemberDto>,
    me: ProfileUserDto?,
    // A call vem de fora (VoiceSession, no shell). Não pode nascer aqui: era o
    // DisposableEffect desta tela que desconectava a call ao navegar.
    call: CallEmMalha,
    // Mesmo motivo do CallDock: quem guarda o mudo e a VoiceSession.
    mudo: Boolean,
    aoAlternarMudo: () -> Unit,
    onLeave: () -> Unit,
    // So pra soundboard: ChannelDto nao carrega a constelacao, e a rota de tocar
    // precisa dela pra checar se voce e membro.
    serverId: String? = null,
) {
    val koin = GlobalContext.get()
    val prefs = remember { koin.get<DesktopPrefs>() }

    // Soundboard. A lista e buscada UMA vez por constelacao: sons mudam quando
    // alguem sobe um novo, e isso nao acontece no meio de uma call.
    val soundApi = remember { koin.get<SoundApi>() }
    val escopoSons = rememberCoroutineScope()
    var sons by remember(serverId) { mutableStateOf<List<ServerSoundDto>>(emptyList()) }
    var sonsAbertos by remember { mutableStateOf(false) }
    LaunchedEffect(serverId) {
        val sid = serverId ?: return@LaunchedEffect
        runCatching { sons = soundApi.listar(sid).sounds }
    }
    val prefState by prefs.state.collectAsState()
    val status by call.status.collectAsState()
    val microfones by call.microfones.collectAsState()
    val saidas by call.saidas.collectAsState()
    // O icone mostra a INTENCAO (mudo), e nao o que o motor esta transmitindo neste
    // milissegundo. Com apertar-para-falar o motor liga e desliga a cada tecla, e um
    // icone vermelho piscando dezenas de vezes por minuto nao informa nada.
    val micOn = !mudo

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header enxuto (Discord-like: pouco texto).
        Text(
            text = "◉ ${channel.name}",
            style = TextStyle(color = Obsidian.accent, fontSize = 18.sp, fontFamily = DmSerif),
        )
        Spacer(Modifier.height(4.dp))
        val (label, color) = when (val s = status) {
            VoiceStatus.Connecting -> "conectando…" to Obsidian.text3
            // "Conectado" era MENTIRA quando o canal de áudio não subia: entrar na
            // sala (sinalização) e a voz achar caminho pela rede são duas coisas
            // diferentes, e só a segunda faz alguém ouvir alguém. Dizer "conectado"
            // nas duas escondia justamente a falha que a pessoa está sentindo — ela
            // ficava olhando pro verde sem entender por que ninguém a escuta.
            is VoiceStatus.Connected ->
                if (s.audioLive) "conectado" to Obsidian.success
                else "entrou na sala, mas o áudio ainda não passou" to Obsidian.accent
            is VoiceStatus.Failed -> s.reason to Obsidian.danger
            VoiceStatus.Closed -> "sinal encerrado" to Obsidian.text3
        }
        // Estado da conexao e tempo de sala na mesma linha: sao as duas coisas que se
        // olha de relance. O cronometro em mono pra o numero nao dancar a cada
        // segundo (fonte proporcional muda a largura do "1" pro "8").
        val inicio by call.inicio.collectAsState()
        val tempo by lembrarTempoDeCall(inicio)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(color = color, fontSize = 11.sp))
            if (tempo.isNotEmpty()) {
                Text(
                    "  ·  $tempo",
                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Palco: um tile por pessoa. A transmissão de tela saiu daqui junto com o
        // motor antigo — ver o botão lá embaixo.
        val connected = status as? VoiceStatus.Connected

        // TRADUZIR ID EM GENTE É TRABALHO DESTA TELA, e isso mudou com a malha.
        //
        // Antes o nome vinha nos metadados do token do LiveKit, porque havia um
        // servidor de mídia para carregá-los. Ponto a ponto não tem esse servidor:
        // o que circula é só o id. Quem tem a lista de membros é esta tela, então é
        // aqui que o id vira nome e foto.
        //
        // No sussurro não há lista de membros — são duas pessoas, e o nome da sala
        // JÁ É o nome da outra. Daí o `channel.name` como último recurso.
        val pessoaPorId = remember(members) { members.associateBy { it.userId } }
        // QUANDO ALGUÉM COMPARTILHA, A TELA TOMA O PALCO e as pessoas descem para uma
        // faixa. É a escolha de todo aplicativo de chamada, e a razão é o conteúdo: quem
        // compartilha quase sempre está mostrando TEXTO — código, um documento, uma
        // planilha —, e texto pequeno numa moldura do tamanho de um avatar não se lê.
        // Rosto encolhido continua reconhecível; letra encolhida vira borrão.
        //
        // UMA TELA NO PALCO POR VEZ, e quem escolhe é quem assiste: clicar na pessoa na
        // faixa troca o palco para a tela dela. Não há aba nem seletor à parte porque a
        // faixa JÁ está desenhada ali com as pessoas todas — acrescentar uma segunda
        // fileira de nomes seria repetir a mesma lista roubando altura do palco, que é
        // justamente o que o palco não tem de sobra.
        val telas by call.telasDosOutros.collectAsState()
        val mostrando by call.mostrandoTela.collectAsState()

        // A ESCOLHA SOBREVIVE À TELA ESCOLHIDA, e não ao contrário: se quem eu escolhi
        // parar de transmitir, o palco volta sozinho para quem sobrou em vez de ficar
        // preto esperando uma decisão minha.
        var telaEscolhida by remember { mutableStateOf<String?>(null) }
        val quemMostra = remember(telas, mostrando, telaEscolhida) {
            telaEscolhida?.takeIf { it in mostrando }
                ?: mostrando.firstOrNull { telas.containsKey(it) }
                ?: mostrando.firstOrNull()
        }

        // O PALCO AVISA O PROCESSO DE VOZ, e é o que faz a tela fora dele custar zero.
        //
        // Decodificar 720p custa 1,03 ms por quadro. Numa sala com três pessoas
        // transmitindo, olhar UMA custava as três — e a que mais pesava era a de quem não
        // está olhando nada: sair daqui para uma conversa de texto sem largar a chamada
        // desmonta esta tela inteira, e até agora a máquina seguia decodificando imagem
        // para uma janela que não existe mais. Daí o `onDispose`, que é a metade
        // importante deste efeito.
        //
        // O aviso é de quem ASSISTE porque a malha entrega a todo mundo de qualquer jeito
        // — aqui se economiza processador, não banda. Cortar banda exigiria avisar quem
        // transmite, e isso é outra conversa.
        // SÃO DOIS EFEITOS E NÃO UM, e a razão é o que o `onDispose` de um
        // `DisposableEffect(quemMostra)` significaria: ele dispara TAMBÉM na troca de
        // palco, e mandaria um "ninguém" no meio do caminho entre olhar A e olhar B —
        // fechando e reabrindo à toa. Preso a `Unit`, ele só fala quando a tela sai
        // mesmo de cena, que é o que se quer dizer.
        LaunchedEffect(quemMostra) { call.assistir(quemMostra) }
        DisposableEffect(Unit) {
            onDispose { call.assistir(null) }
        }

        val tiles = remember(connected, me, micOn, pessoaPorId, channel.name, mostrando, quemMostra) {
            buildList {
                if (connected != null) {
                    add(
                        Tile(
                            "me", "você", connected.mySpeaking, me?.avatarUrl,
                            isMe = true, muted = !micOn,
                            transmitindo = false, emCartaz = false,
                        ),
                    )
                    connected.others.forEach { p ->
                        val membro = pessoaPorId[p.identity]
                        val nome = membro?.user?.displayName
                            ?: membro?.user?.username
                            ?: channel.name.ifBlank { "alguém" }
                        add(
                            Tile(
                                p.identity, nome, p.speaking, membro?.user?.avatarUrl,
                                isMe = false, muted = false,
                                transmitindo = p.identity in mostrando,
                                emCartaz = p.identity == quemMostra,
                            ),
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
            if (quemMostra == null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    ParticipantGrid(tiles)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    val nomeDeQuemMostra = pessoaPorId[quemMostra]?.user?.let { it.displayName ?: it.username }
                        ?: channel.name.ifBlank { "alguém" }
                    Box(
                        Modifier.weight(1f).fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Obsidian.void),
                        contentAlignment = Alignment.Center,
                    ) {
                        val quadro = telas[quemMostra]
                        if (quadro == null) {
                            // O VAZIO DURA UM INSTANTE E TEM NOME. Entre o aviso de que
                            // alguém começou a transmitir e o primeiro quadro passam-se
                            // alguns décimos — o descompressor precisa da sequência de
                            // parâmetros e de um quadro-chave. Um retângulo preto mudo
                            // nesse intervalo parece defeito.
                            Text(
                                "abrindo a tela de $nomeDeQuemMostra…",
                                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                            )
                        } else {
                            TelaCompartilhada(quadro, Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // O RECADO MUDA QUANDO HÁ ESCOLHA A FAZER. Com uma pessoa
                    // transmitindo a frase é só legenda; com duas ela precisa contar que
                    // dá para trocar, senão a segunda tela existe e ninguém descobre.
                    val quantasTelas = mostrando.size
                    Text(
                        if (quantasTelas > 1) {
                            "$nomeDeQuemMostra está compartilhando a tela · " +
                                "clique em outra pessoa para ver a dela"
                        } else {
                            "$nomeDeQuemMostra está compartilhando a tela"
                        },
                        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                    )
                    Spacer(Modifier.height(8.dp))
                    ParticipantGrid(tiles) { telaEscolhida = it }
                }
            }
        }

        // Controles minimalistas (Discord): botoes de simbolo com borda, sem texto.
        var settingsOpen by remember { mutableStateOf(false) }
        var transmissaoAvisada by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CallIconButton(
                icon = if (micOn) Lucide.Mic else Lucide.MicOff,
                tone = if (micOn) CallTone.Normal else CallTone.Danger,
                onClick = aoAlternarMudo,
            )
            // SOUNDBOARD. Clicar num som NAO mistura audio no seu microfone: o
            // servidor avisa a sala e cada um toca o arquivo original localmente
            // (ver SoundboardPlayer). Passar pelo mic faria o som atravessar o Opus
            // da voz, que e afinado pra fala e esmaga efeito.
            //
            // Sem freio entre disparos — decisao explicita do dono.
            Box {
                CallIconButton(
                    icon = Lucide.Volume2,
                    tone = if (sonsAbertos) CallTone.Active else CallTone.Normal,
                    onClick = { sonsAbertos = !sonsAbertos },
                )
                if (sonsAbertos) {
                    Popup(
                        onDismissRequest = { sonsAbertos = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier
                                .popupReveal(originX = 0.5f, originY = 1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Obsidian.raised)
                                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                        ) {
                            if (sons.isEmpty()) {
                                Text(
                                    "nenhum som aqui ainda",
                                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                            sons.forEach { som ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            val sid = serverId
                                            if (sid != null) escopoSons.launch {
                                                runCatching {
                                                    soundApi.tocar(sid, som.id, TocarSomRequest(channel.id))
                                                }
                                            }
                                            // O menu NAO fecha: soundboard e feita
                                            // pra disparar varios seguidos, e reabrir
                                            // a cada som mataria a graca.
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    LIcon(Lucide.Volume2, tint = Obsidian.text3, size = 13.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(som.name, style = TextStyle(color = Obsidian.text1, fontSize = 12.sp))
                                }
                            }
                        }
                    }
                }
            }
            // TRANSMITIR A TELA.
            //
            // O botão NÃO ACENDE AO SER APERTADO: acende quando o processo de voz
            // confirma que a captura e o compressor subiram. Isso leva quase um segundo
            // e pode falhar — nem toda máquina tem compressor de H.264 —, e acender no
            // clique para apagar depois é o padrão que ensina a pessoa a desconfiar do
            // próprio botão. Apagar, sim, é imediato: quem manda parar quer parar já.
            //
            // O toque no botão abre a placa; enquanto está no ar, ele mostra o que está
            // subindo de verdade. Ainda não há imagem para ver deste lado (o
            // decodificador é a próxima fatia), e o relatório é o que prova que a coisa
            // está viva no lugar dela.
            val transmitindo by call.transmitindo.collectAsState()
            val relatorio by call.relatorioDaTela.collectAsState()
            val monitores by call.monitores.collectAsState()
            var escolhendoTela by remember { mutableStateOf(false) }
            Box {
                CallIconButton(
                    icon = Lucide.ScreenShare,
                    tone = if (transmitindo) CallTone.Active else CallTone.Normal,
                    onClick = {
                        if (transmitindo) {
                            call.pararDeTransmitir()
                            transmissaoAvisada = false
                            escolhendoTela = false
                        } else {
                            // O CLIQUE ABRE A ESCOLHA em vez de transmitir direto. Antes
                            // ele mandava o monitor principal sem perguntar, o que acerta
                            // por acaso em quem tem uma tela só e erra metade das vezes em
                            // quem tem duas — com o erro acontecendo ao vivo.
                            escolhendoTela = true
                            call.pedirMonitores()
                        }
                    },
                )
                if (escolhendoTela && !transmitindo) {
                    Popup(
                        onDismissRequest = { escolhendoTela = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        SeletorDeTela(monitores) { indice ->
                            escolhendoTela = false
                            val q = prefState.screenQuality
                            call.transmitir(indice, q.width, q.height, q.fps, q.bitrate / 1000)
                            transmissaoAvisada = true
                        }
                    }
                }
                if (transmissaoAvisada && transmitindo) {
                    Popup(
                        onDismissRequest = { transmissaoAvisada = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier
                                .popupReveal(originX = 0.5f, originY = 1f)
                                .width(230.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Obsidian.raised)
                                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                        ) {
                            Text(
                                "Transmitindo",
                                style = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
                            )
                            if (relatorio.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    relatorio,
                                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Quem está na sala recebe a imagem. Para trocar de tela, " +
                                    "pare a transmissão e escolha outra.",
                                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                            )
                        }
                    }
                }
            }
            Box {
                CallIconButton(
                    icon = Lucide.Settings,
                    tone = if (settingsOpen) CallTone.Active else CallTone.Normal,
                    onClick = { settingsOpen = !settingsOpen },
                )
                if (settingsOpen) {
                    // Reconsulta ao ABRIR o painel, e não uma vez só: aparelho vai e
                    // vem no meio de uma call — fone USB plugado, monitor com caixa
                    // ligado. Uma lista buscada na entrada da sala estaria velha
                    // justamente quando a pessoa foi lá procurar o aparelho novo.
                    LaunchedEffect(Unit) { call.atualizarAparelhos() }
                    Popup(
                        onDismissRequest = { settingsOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        PopupReveal(originX = 0.5f, originY = 1f) {
                            CallSettingsPanel(
                                microfones = microfones,
                                saidas = saidas,
                                microfoneAtual = prefState.audioInput,
                                saidaAtual = prefState.audioOutput,
                                aoTrocarMicrofone = {
                                    prefs.setAudioInput(it)
                                    call.escolherMicrofone(it)
                                },
                                aoTrocarSaida = {
                                    prefs.setAudioOutput(it)
                                    call.escolherSaida(it)
                                },
                            )
                        }
                    }
                }
            }
            CallIconButton(icon = Lucide.PhoneOff, tone = CallTone.Danger, onClick = onLeave)
        }
    }
}

// Config da call (gear): escolher microfone e saída.
//
// A LISTA VEM DO PROCESSO DE VOZ, e essa é a parte que importa. Ele é quem fala
// WASAPI e quem vai abrir o aparelho; a JVM enxerga uma lista diferente e menor.
// Listar por um caminho e abrir por outro é como se acaba escolhendo um aparelho e
// ouvindo outro — e ninguém consegue explicar por quê.
//
// A escolha guarda o IDENTIFICADOR do Windows, não o nome: nome muda com atualização
// de driver e se repete entre placas iguais.
@Composable
private fun CallSettingsPanel(
    microfones: List<AparelhoDeAudio>,
    saidas: List<AparelhoDeAudio>,
    microfoneAtual: String?,
    saidaAtual: String?,
    aoTrocarMicrofone: (String?) -> Unit,
    aoTrocarSaida: (String?) -> Unit,
) {
    Column(
        Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        PanelHeader("Microfone")
        SeletorDeAparelho(microfones, microfoneAtual, aoTrocarMicrofone)
        Spacer(Modifier.height(10.dp))
        PanelHeader("Saida")
        SeletorDeAparelho(saidas, saidaAtual, aoTrocarSaida)
        Spacer(Modifier.height(10.dp))
        Text(
            "Trocar vale na hora, sem sair da call.",
            style = TextStyle(color = Obsidian.text3, fontSize = 10.5.sp),
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

// Seletor de aparelho: mostra o atual e abre a lista num popup.
//
// "Padrão do Windows" é uma opção de verdade e vem primeiro, porque é o certo para a
// maioria — é o aparelho que a pessoa já escolheu no sistema para conversar.
@Composable
private fun SeletorDeAparelho(
    opcoes: List<AparelhoDeAudio>,
    atual: String?,
    aoEscolher: (String?) -> Unit,
) {
    var aberto by remember { mutableStateOf(false) }
    val nomeAtual = opcoes.firstOrNull { it.id == atual }?.nome ?: "Padrao do Windows"
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.base)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .clickable { aberto = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                nomeAtual,
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            LIcon(Lucide.ChevronDown, tint = Obsidian.text3, size = 14.dp)
        }
        if (aberto) {
            Popup(
                onDismissRequest = { aberto = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                ) {
                    LinhaDeAparelho("Padrao do Windows", atual == null) {
                        aoEscolher(null); aberto = false
                    }
                    opcoes.forEach { ap ->
                        LinhaDeAparelho(ap.nome, ap.id == atual) {
                            aoEscolher(ap.id); aberto = false
                        }
                    }
                    if (opcoes.isEmpty()) {
                        Text(
                            "procurando aparelhos…",
                            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaDeAparelho(rotulo: String, ativo: Boolean, aoClicar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val sobHover by interacao.collectIsHoveredAsState()
    val fundo by animateColorAsState(if (sobHover) Obsidian.hover else Color.Transparent, tween(100))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(fundo)
            .hoverable(interacao)
            .clickable(interactionSource = interacao, indication = null, onClick = aoClicar)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rotulo,
            style = TextStyle(color = if (ativo) Obsidian.accent else Obsidian.text2, fontSize = 12.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (ativo) {
            Spacer(Modifier.width(6.dp))
            LIcon(Lucide.Check, tint = Obsidian.accent, size = 13.dp)
        }
    }
}

@Composable
private fun PanelHeader(text: String) {
    Text(
        text.uppercase(),
        style = TextStyle(color = Obsidian.text3, fontSize = 9.sp, letterSpacing = 1.sp),
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// Pilulas segmentadas (um eixo). Ativa = accent; muda na hora.
@Composable
private fun <T> CallSegmented(options: List<Pair<String, T>>, selected: T, onPick: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.base)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            val bg by animateColorAsState(
                if (on) Obsidian.accent.copy(alpha = 0.16f) else Obsidian.base.copy(alpha = 0f),
                tween(140),
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .border(1.dp, if (on) Obsidian.accent.copy(alpha = 0.5f) else Obsidian.borderDim.copy(alpha = 0f), RoundedCornerShape(6.dp))
                    .clickable { onPick(value) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = TextStyle(color = if (on) Obsidian.accent else Obsidian.text2, fontSize = 12.sp))
            }
        }
    }
}

private enum class CallTone { Normal, Active, Danger }

// Botao minimalista de call (Discord): so o ícone, circulo com borda que troca
// de cor pelo estado. Sem texto. Icone Lucide monocromatico -> o tint AGORA pega
// no glifo (antes, com emoji colorido, so a borda carregava o estado).
@Composable
private fun CallIconButton(
    icon: ImageVector,
    tone: CallTone,
    onClick: () -> Unit,
    // Desligado ainda RESPONDE ao clique, e isso é de propósito: o clique abre a
    // explicação de por que o controle está fora. Um botão que não faz nada e não
    // diz nada é o pior dos dois mundos.
    habilitado: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(
        when (tone) {
            CallTone.Danger -> Obsidian.danger
            CallTone.Active -> Obsidian.accent
            CallTone.Normal -> Obsidian.borderMid
        },
        tween(140),
    )
    val fg = when {
        // Apagado, e não vermelho nem accent: "fora do ar" tem de ler como ausência,
        // e qualquer cor ali seria confundida com estado.
        !habilitado -> Obsidian.text3.copy(alpha = 0.45f)
        tone == CallTone.Danger -> Obsidian.danger
        tone == CallTone.Active -> Obsidian.accent
        else -> Obsidian.text2
    }
    val bg by animateColorAsState(if (hovered) Obsidian.hover else Obsidian.raised.copy(alpha = 0.4f), tween(140))
    Box(
        Modifier
            .size(46.dp)
            .clickScale(interaction)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, tint = fg, size = 20.dp)
    }
}

// Um participante no palco. muted so e conhecido pra mim (o engine não expoe o
// mute dos outros ainda) — nos outros o ícone fica de fora.
private data class Tile(
    // Identidade estavel do participante — e a CHAVE da animação de entrar/sair.
    // Sem ela o Compose reusaria o mesmo slot e a troca de gente seria muda.
    val key: String,
    val label: String,
    val speaking: Boolean,
    val avatarUrl: String?,
    val isMe: Boolean,
    val muted: Boolean,
    // Esta pessoa está compartilhando a tela.
    val transmitindo: Boolean = false,
    // ...e é a tela DELA que está no palco agora. Duas coisas diferentes assim que
    // duas pessoas transmitem ao mesmo tempo.
    val emCartaz: Boolean = false,
)

// Grid que quebra linha sozinho (FlowRow), centralizado.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantGrid(tiles: List<Tile>, aoEscolherTela: (String) -> Unit = {}) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Mesmo "estouro" da mini-tela: quem entra na call nasce pequeno e passa do
        // tamanho antes de assentar; quem sai encolhe. A key e a identidade — sem ela
        // o Compose reusaria o slot e a troca seria muda.
        tiles.forEach { t ->
            key(t.key) {
                PopIn { ParticipantTile(t, Modifier.width(164.dp)) { aoEscolherTela(t.key) } }
            }
        }
    }
}

// Cartao: avatar grande centralizado + nome; anel/halo ambar pulsa ao falar
// (respeita reduzir movimento — fica aceso e parado). Layout estavel: o halo
// vive num Box de tamanho fixo, entao falar não empurra o tile.
@Composable
private fun ParticipantTile(tile: Tile, modifier: Modifier = Modifier, aoEscolherTela: () -> Unit = {}) {
    val reduce = LocalReduceMotion.current
    val active = LocalWindowActive.current
    val interacao = remember { MutableInteractionSource() }
    // Só é alvo de clique quem tem tela para pôr no palco E ainda não está nele.
    // Cartão clicável que não faz nada ensina a desconfiar do próprio clique.
    val podeTrocar = tile.transmitindo && !tile.emCartaz
    // Estrela de fala: UMA fase de órbita, so quando fala + janela visivel +
    // movimento ligado, lida DENTRO do drawBehind. Antes um halo pulsante era
    // lido no corpo e recompunha o cartao inteiro (avatar/nome/mic) 60fps por
    // pessoa falando; agora so redesenha. (Auditoria de movimento, achado #1.)
    val orbit = if (tile.speaking && !reduce && active) {
        rememberInfiniteTransition(label = "orbit-${tile.label}").animateFloat(
            0f, (2.0 * Math.PI).toFloat(),
            infiniteRepeatable(tween(2600, easing = LinearEasing)),
        )
    } else null

    val borderColor by animateColorAsState(
        if (tile.speaking) Obsidian.accent else Obsidian.borderDim,
        tween(140),
    )
    // Inchada ao falar: o card cresce ~4% com mola suave (escala VISUAL via
    // graphicsLayer -> não empurra os vizinhos; cresce por cima). Reduzir movimento
    // = fica maior parado, sem animar.
    val swell by animateFloatAsState(
        targetValue = if (tile.speaking) 1.04f else 1f,
        animationSpec = if (reduce) snap()
            else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
    )
    Column(
        modifier
            .graphicsLayer { scaleX = swell; scaleY = swell }
            .clip(RoundedCornerShape(14.dp))
            // QUEM ESTÁ NO PALCO SOBE UM DEGRAU DA RAMPA, e não ganha cor. O accent já
            // significa "está falando" nesta faixa; usá-lo também para "está no palco"
            // faria duas coisas diferentes acenderem igual. Elevação é o que o app usa
            // para dizer "este é o mais próximo", e é o que cabe aqui.
            .background(if (tile.emCartaz) Obsidian.overlay else Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .then(
                if (podeTrocar) {
                    Modifier
                        .hoverable(interacao)
                        .clickScale(interacao, formaDoFoco = RoundedCornerShape(14.dp))
                        .clickable(interactionSource = interacao, indication = null, onClick = aoEscolherTela)
                } else Modifier,
            )
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
            if (tile.speaking) {
                Box(Modifier.fillMaxSize().drawBehind {
                    // Halo suave constante; a estrela órbita por cima (ou so o halo,
                    // se movimento reduzido / janela em segundo plano).
                    drawCircle(Obsidian.accent.copy(alpha = 0.16f), radius = size.minDimension / 2f)
                    orbit?.let { ph ->
                        val r = size.minDimension / 2f
                        val ang = ph.value
                        val trail = Offset(center.x + cos(ang - 0.35f) * r, center.y + sin(ang - 0.35f) * r)
                        val star = Offset(center.x + cos(ang) * r, center.y + sin(ang) * r)
                        drawCircle(Obsidian.accent.copy(alpha = 0.35f), radius = 1.5.dp.toPx(), center = trail)
                        drawCircle(Obsidian.accent, radius = 3.dp.toPx(), center = star)
                    }
                })
            }
            DesktopAvatar(tile.avatarUrl, tile.label, 62)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tile.muted) {
                LIcon(Lucide.MicOff, tint = Obsidian.text3, size = 13.dp)
                Spacer(Modifier.width(4.dp))
            }
            // O SINAL DE QUEM ESTÁ COMPARTILHANDO. Sem rótulo de leitor de tela porque
            // vem colado no nome: rotulado, o leitor diria "compartilhando tela, fulano"
            // toda vez que passasse pelo cartão, e o ícone aqui é decoração de um texto
            // que já está escrito.
            if (tile.transmitindo) {
                LIcon(
                    Lucide.ScreenShare,
                    tint = if (tile.emCartaz) Obsidian.text2 else Obsidian.text3,
                    size = 13.dp,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                tile.label,
                style = TextStyle(
                    color = if (tile.speaking) Obsidian.accent else Obsidian.text2,
                    fontSize = 12.sp,
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
