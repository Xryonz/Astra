package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Crop
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Move
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.SmilePlus
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.X
import app.astra.desktop.profile.AvatarPicker
import app.astra.desktop.voice.AudioDevices
import app.astra.desktop.prefs.AuroraQuality
import app.astra.desktop.prefs.DensityPref
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.FontSizePref
import app.astra.desktop.prefs.ScreenQuality
import app.astra.desktop.prefs.UiFps
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.ThemePreset
import app.astra.desktop.ui.theme.ThemePresets
import app.astra.desktop.ui.theme.accentOption
import app.astra.desktop.ui.theme.bgOption
import app.astra.desktop.update.UpdateService
import app.astra.desktop.update.UpdateState
import app.astra.desktop.auth.SessionStore
import app.astra.mobile.core.network.SessionApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.MutualServerDto
import app.astra.mobile.core.network.dto.RevokeOthersRequest
import app.astra.mobile.core.network.dto.SessionDto
import app.astra.mobile.core.network.dto.ChangePasswordRequest
import app.astra.mobile.core.network.dto.CustomStatusRequest
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.SetPasswordRequest
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import retrofit2.HttpException
import zed.rainxch.rikkaui.components.ui.progress.Progress
import zed.rainxch.rikkaui.components.ui.progress.ProgressAnimation
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

// Publico (era private): entra na assinatura do SettingsScreen e do UserFooter —
// clicar no avatar do rodape abre as configurações JA no Perfil.
enum class SettingsTab(val label: String, val sub: String, val icon: ImageVector) {
    ACCOUNT("Conta", "email e senha", Lucide.User),
    PROFILE("Perfil", "avatar, nome e recado", Lucide.Pencil),
    SESSIONS("Sessões", "onde sua conta está logada", Lucide.LogOut),
    NOTIFICATIONS("Notificacoes", "avisos na bandeja", Lucide.Bell),
    APPEARANCE("Aparencia", "cores, fonte, densidade", Lucide.Palette),
    PERFORMANCE("Desempenho", "graficos, animações, fps", Lucide.ChartColumn),
    VOICE("Voz", "microfone e transmissão", Lucide.Volume2),
    PERMISSIONS("Permissoes", "o que o Windows libera", Lucide.ShieldCheck),
    ABOUT("Sobre", "versão e atualizacoes", Lucide.Info),
    // SO PRA DEV (decisao do dono). Ver `abaDeDev` abaixo.
    DIAGNOSTICS("Diagnostico", "o que o app esta vendo agora", Lucide.CircleDot),
}

// Rodando pelo Gradle/IDE = dev. No app empacotado o jpackage define
// `jpackage.app-path`; sem essa propriedade, estamos no ambiente de
// desenvolvimento. Da pra forcar no pacote com -Dastra.dev (util pra pedir o
// diagnostico a alguem sem publicar uma versão especial).
//
// Esconder a aba NAO cega o suporte: o diagnostico de boot e o registro de falhas
// continuam sendo gravados em %LOCALAPPDATA%\Astra pra todo mundo, entao ainda da
// pra pedir o arquivo a um amigo quando algo quebrar.
private val abaDeDev: Boolean =
    System.getProperty("jpackage.app-path") == null || System.getProperty("astra.dev") != null

private val abasVisiveis: List<SettingsTab> =
    SettingsTab.entries.filter { it != SettingsTab.DIAGNOSTICS || abaDeDev }

// Largura da coluna de previa. Era 300 fixa (e 420 empilhada): com DUAS previas
// lado a lado sobravam ~145dp pra cada cartao, e um cartao encolhido a 40% vira
// mancha — da pra ver que ha um cartao, nao COMO ele esta.
private val LARGURA_PREVIA = 420.dp
// A aba Perfil e a UNICA em que a previa e o assunto, e nao o comentario: o que
// se edita ali e o cartao, entao ver o cartao grande e ver o resultado. Nas
// outras abas a previa e uma nota de rodape ao vivo (um aviso deslizando, um
// medidor) e crescer so tiraria largura dos controles.
private val LARGURA_PREVIA_PERFIL = 470.dp
private val FORMA_DO_CARTAO_DE_CONFIG = RoundedCornerShape(16.dp)

// Settings em TAKEOVER estilo Discord (decisao do dono): ocupa o shell inteiro,
// nav de secoes na esquerda + conteudo na direita. Secoes v1: Conta (senha),
// Notificacoes (toggles do tray) e Movimento (reduzir animações).
@Composable
fun SettingsScreen(
    me: ProfileUserDto?,
    prefs: DesktopPrefs,
    onClose: () -> Unit,
    onProfileSaved: () -> Unit = {},
    initialTab: SettingsTab = SettingsTab.ACCOUNT,
    onTestarNotificacao: () -> Unit = {},
) {
    // ABA DE VOLTA. A rolagem unica (0.1.95) foi testada e reprovada pelo dono: a
    // secao trocava sozinha conforme a pagina descia, e o item aceso no menu
    // virava consequencia da rolagem em vez de escolha. Uma pagina por aba devolve
    // o controle — e o cartao grande, que veio junto, ja da a sensacao de
    // sobreposicao que o Discord tem sem precisar da rolagem continua.
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    val tabAtiva = tab
    // Abas que ja fizeram a cascata NESTA visita as configuracoes.
    //
    // O `remember` sem chave e o mecanismo inteiro: ele morre quando a tela sai da
    // composicao, ou seja, quando as configuracoes fecham. Dai sai de graca a regra
    // que o dono pediu — trocar de aba e voltar encontra a aba ja montada; fechar
    // as configuracoes e abrir de novo faz a cascata acontecer outra vez.
    val jaAnimaram = remember { mutableSetOf<SettingsTab>() }
    val prefState by prefs.state.collectAsState()
    // Rascunho do perfil VIVE AQUI (não dentro da secao): a previa e IRMA da
    // secao, não filha — hoisted, ela reage a cada tecla. Reseta quando o `me`
    // do shell muda (ex.: depois de salvar, o refreshMe traz o valor novo).
    var draft by remember(me) { mutableStateOf(ProfileDraft.from(me)) }

    // ESC fecha: foco no root do takeover + captura da tecla.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false
            },
    ) {
        // Fundo do takeover = a MESMA aurora do shell, continua, por baixo (o dono
        // pediu "mesma aurora, no mesmo lugar independente da aba"). O shell segura a
        // aurora/estrelas montadas e esconde o proprio conteudo enquanto isto abre ->
        // nada vaza atrás. Aqui so um veu segura a leitura. Pintar aurora nova aqui
        // era o "salto de posição" ao abrir configurações (relogio independente).
        // Scrim mais escuro que o veu antigo: as configuracoes deixaram de tomar a
        // tela e viraram um CARTAO GRANDE por cima do shell (referencia do dono, o
        // Discord). Com o app aparecendo nas beiradas, o veu precisa empurrar o
        // fundo pra tras — senao os dois competem pela leitura.
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 30.dp)
                .widthIn(max = 1180.dp)
                .clip(FORMA_DO_CARTAO_DE_CONFIG)
                .background(Obsidian.base)
                .border(1.dp, Obsidian.borderMid, FORMA_DO_CARTAO_DE_CONFIG)
                // Engole o clique: sem isto, clicar dentro do cartao fecha, porque
                // o scrim atras continua ouvindo.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            // Nav das secoes
            Column(
                Modifier.width(220.dp).fillMaxHeight().padding(horizontal = 12.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "configurações",
                    style = TextStyle(color = Obsidian.text1, fontSize = 18.sp, fontFamily = DmSerif),
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                )
                abasVisiveis.forEach { t ->
                    NavRow(t.icon, t.label, t.sub, active = t == tabAtiva) { tab = t }
                }
            }

            // Conteudo da secao — coluna capada (~720) estilo Discord: não esparrama
            // pelo palco todo (o "enxuto" que o dono pediu). O Box segura a coluna
            // encostada a esquerda; os controles leem como uma coluna so em vez de
            // soltos num vazao grande a direita. Titulo + fechar vivem dentro dela.
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            // LARGURA DA PREVIA FIXA PRA PAGINA INTEIRA, e nao mais por aba.
            //
            // Antes ela variava (a do Perfil e maior), e a coluna de conteudo era
            // calculada em cima dela. Numa pagina unica isso seria veneno: a
            // coluna inteira mudaria de largura no meio da rolagem, so porque a
            // secao do Perfil entrou na tela. Layout que se remexe enquanto se
            // rola e pior que uma previa 50dp menor em duas secoes.
            val larguraPrevia = LARGURA_PREVIA_PERFIL
            // PISO derivado da propria previa, e nao um numero fixo: 280dp e o
            // minimo em que um campo com rotulo ainda cabe numa linha. Abaixo
            // disso a previa desce pra dentro de cada secao.
            val pinned = maxWidth > larguraPrevia + 280.dp
            // Com a previa fixa, a coluna de conteudo encolhe pra não correr por
            // baixo dela: a previa + 32 do respiro na borda + 44 de vao.
            val contentMax =
                if (pinned) minOf(720.dp, (maxWidth - larguraPrevia - 76.dp).coerceAtLeast(280.dp)) else 720.dp
            Column(
                Modifier.align(Alignment.TopStart).widthIn(max = contentMax).fillMaxWidth()
                    .fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                // Troca de secao: SO fade. A entrada de verdade e a cascata, e ela
                // e vertical. O SizeTransform explicito continua sendo necessario —
                // o padrao dele e MOLA, e mola tem duracao proporcional a
                // distancia, entao a mesma troca de aba saia curta ou longa
                // conforme a diferenca de altura entre as duas.
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        fadeIn(tween(140)).togetherWith(fadeOut(tween(100))) using
                            SizeTransform(clip = false) { _, _ -> tween(180) }
                    },
                    label = "settingsSection",
                ) { current ->
                    val temPrevia = temPrevia(current)
                    // A CASCATA agora acontece quando a secao ENTRA NA TELA pela
                    // primeira vez, e nao quando se troca de aba. O conjunto
                    // `jaAnimaram` continua sendo o que impede o replay: a
                    // LazyColumn descarta o item que sai da tela e o recompoe ao
                    // voltar, entao sem ele a cascata tocaria de novo a cada
                    // rolagem pra cima. Ele morre quando as configuracoes fecham —
                    // exatamente a regra que o dono pediu.
                    val jaVisto = current in jaAnimaram
                    LaunchedEffect(current) { jaAnimaram += current }
                    // O Column NAO e decorativo: o container do AnimatedContent
                    // empilha os filhos da raiz no MESMO Y. Era o bug dos "textos
                    // sobrepostos".
                    Column(Modifier.fillMaxWidth()) {
                    Text(
                        current.label,
                        style = TextStyle(color = Obsidian.text1, fontSize = 26.sp, fontFamily = DmSerif),
                    )
                    Spacer(Modifier.height(18.dp))
                    // Janela estreita: a previa nao cabe ao lado, entao entra AQUI,
                    // logo abaixo do titulo da secao a que ela pertence.
                    if (!pinned && temPrevia) {
                        SettingsPreview(current, me, prefState, draft, Modifier.widthIn(max = larguraPrevia).fillMaxWidth())
                        Spacer(Modifier.height(18.dp))
                    }
                    CascataVertical(chave = current, animar = !jaVisto, modifier = Modifier.fillMaxWidth()) {
                    when (current) {
                        SettingsTab.ACCOUNT -> AccountSection(me)
                        SettingsTab.PROFILE -> ProfileSection(me, draft, { draft = it }, onProfileSaved)
                        SettingsTab.SESSIONS -> SessionsSection()
                        SettingsTab.NOTIFICATIONS -> Column {
                            ToggleRow(
                                "Sussurros (DMs)", "avisa quando chega mensagem privada",
                                prefState.notifyDms, prefs::setNotifyDms,
                            )
                            ToggleRow(
                                "Atividade de canal", "avisa nova mensagem nas constelações",
                                prefState.notifyChannels, prefs::setNotifyChannels,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "os avisos aparecem na bandeja so com a janela fechada ou minimizada.",
                                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                                modifier = Modifier.widthIn(max = 460.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            TestarNotificacao(onTestarNotificacao)
                        }
                        SettingsTab.APPEARANCE -> AppearanceSection(prefState, prefs)
                        SettingsTab.PERFORMANCE -> PerformanceSection(prefState, prefs)
                        SettingsTab.VOICE -> VoiceSection(prefState, prefs)
                        SettingsTab.PERMISSIONS -> PermissionsSection(onTestarNotificacao)
                        SettingsTab.ABOUT -> AboutSection()
                        SettingsTab.DIAGNOSTICS -> DiagnosticsSection()
                    }
                    }
                    // O botao de salvar segue no PE do formulario do Perfil quando
                    // a previa esta empilhada: salvar e o fim da tarefa, e o lugar
                    // dele e onde a tarefa acaba.
                    if (!pinned && current == SettingsTab.PROFILE) {
                        Spacer(Modifier.height(14.dp))
                        ProfileSaveButton(me, draft, { draft = it }, onProfileSaved, Modifier.widthIn(max = larguraPrevia).fillMaxWidth())
                    }
                    }
                }
            }
                // Coluna fixa da direita: fechar em cima, previa embaixo. Ela nao
                // rola junto — e a previa acompanha a secao que esta NO TOPO da
                // rolagem, com um fade curto na troca. Sem o fade, rolar entre duas
                // secoes com previa trocaria o cartao de uma vez so, seco.
                if (pinned) {
                    Column(
                        Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 32.dp).width(larguraPrevia),
                        horizontalAlignment = Alignment.End,
                    ) {
                        // Fechar (ESC também) volta pro shell. Subiu pra ca: com
                        // uma pagina so, ele nao pertence mais a nenhuma secao.
                        val hov = remember { MutableInteractionSource() }
                        val h by hov.collectIsHoveredAsState()
                        Box(
                            Modifier
                                .size(30.dp)
                                .clickScale(hov, formaDoFoco = FormaDeBotao)
                                .clip(FormaDeBotao)
                                .background(if (h) Obsidian.hover else Obsidian.overlay)
                                .border(1.dp, Obsidian.borderMid, FormaDeBotao)
                                .hoverable(hov)
                                .clickable(interactionSource = hov, indication = null, onClick = onClose),
                            contentAlignment = Alignment.Center,
                        ) {
                            LIcon(Lucide.X, tint = Obsidian.text2, size = 15.dp, rotulo = "fechar")
                        }
                        Spacer(Modifier.height(16.dp))
                        AnimatedContent(
                            targetState = tabAtiva,
                            transitionSpec = {
                                fadeIn(tween(160)).togetherWith(fadeOut(tween(120))) using
                                    SizeTransform(clip = false) { _, _ -> tween(200) }
                            },
                            label = "previaDaSecao",
                        ) { secao ->
                            Column(Modifier.fillMaxWidth()) {
                                // A regra de "esta secao tem previa?" vale AQUI
                                // tambem. Ela existia so no ramo empilhado, entao na
                                // coluna fixa Sessões, Permissões e Sobre mostravam o
                                // rotulo "previa" com o vazio embaixo — anunciando
                                // uma coisa que nao ha como existir nessas telas.
                                if (temPrevia(secao)) {
                                    SettingsPreview(secao, me, prefState, draft, Modifier.fillMaxWidth())
                                }
                                if (secao == SettingsTab.PROFILE) {
                                    Spacer(Modifier.height(14.dp))
                                    ProfileSaveButton(me, draft, { draft = it }, onProfileSaved, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Sessões, Permissões, Sobre e Diagnostico sao listas e acoes — nao ha estado
// visual pra antecipar. Uma unica regra pros DOIS jeitos de mostrar a previa
// (empilhada embaixo do titulo e fixa na coluna da direita): duplicada, ela
// divergiu, e foi assim que o rotulo "previa" apareceu sozinho nessas telas.
private fun temPrevia(tab: SettingsTab): Boolean = when (tab) {
    SettingsTab.SESSIONS, SettingsTab.PERMISSIONS,
    SettingsTab.ABOUT, SettingsTab.DIAGNOSTICS -> false
    else -> true
}

// Previa ao vivo (lado das configs). Cada aba mostra o efeito real do que se
// mexe: Conta = teu perfil como os OUTROS veem; Notificacoes = aviso deslizando
// na bandeja; Aparencia = mini-janela no tema/fonte/densidade; Desempenho =
// medidor de custo GPU/CPU; Voz = moldura da transmissão + nível do mic ao vivo.
@Composable
private fun SettingsPreview(
    tab: SettingsTab,
    me: ProfileUserDto?,
    p: DesktopPrefs.Prefs,
    draft: ProfileDraft,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        FieldLabel("previa")
        // A PREVIA NAO RESPONDE AO PONTEIRO.
        //
        // Ela e feita dos componentes DE VERDADE (o mesmo ProfileCard do popup, o
        // mesmo aviso da bandeja) — e era esse o objetivo, pra previa e realidade
        // nao divergirem. O efeito colateral e que os cliques deles vinham junto:
        // clicar no cartao da previa abria o perfil por cima das configuracoes.
        //
        // O veu por cima resolve num lugar so. A alternativa seria uma bandeira
        // "sou previa" em cada componente compartilhado, espalhando pelo app inteiro
        // uma regra que e desta tela.
        Box {
            when (tab) {
                // Conta = teu perfil SALVO; Perfil = o rascunho ao vivo (cada tecla).
                SettingsTab.ACCOUNT -> ProfileCardPreview(me, null)
                SettingsTab.PROFILE -> ProfileCardPreview(me, draft)
                SettingsTab.NOTIFICATIONS -> NotifPreviewCard(p.reduceMotionEff)
                SettingsTab.APPEARANCE -> UiSamplePreview(p.fontSize, p.density)
                SettingsTab.PERFORMANCE -> CostMeter(p)
                SettingsTab.VOICE -> VoicePreview(p)
                // Sessões, Sobre e Permissões são listas/ações — não ha o que previsualizar.
                SettingsTab.SESSIONS, SettingsTab.ABOUT, SettingsTab.DIAGNOSTICS, SettingsTab.PERMISSIONS -> Unit
            }
            Box(Modifier.matchParentSize().engoleOPonteiro())
        }
    }
}

// Come todo evento de ponteiro antes que ele chegue em quem esta embaixo.
// `matchParentSize` + este modificador = a subarvore vira imagem: nao clica, nao
// mostra hover, nao troca o cursor.
private fun Modifier.engoleOPonteiro(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

// Rascunho editavel do perfil. Fica no nível da tela pra a previa (irma da
// secao) conseguir ler enquanto você digita.
private data class ProfileDraft(
    val displayName: String = "",
    val pronouns: String = "",
    val bio: String = "",
    val statusEmoji: String = "",
    val customStatus: String = "",
    val avatarUrl: String? = null,
    // --- fatia 2 (banner) ---
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val bannerPositionY: Int = 50,
    val bannerScale: Int = 100,
    // --- fatia 3 (estilo do perfil) ---
    val profileTheme: String? = null,
    val displayFont: String? = null,
) {
    companion object {
        fun from(me: ProfileUserDto?) = ProfileDraft(
            displayName = me?.displayName.orEmpty(),
            pronouns = me?.pronouns.orEmpty(),
            bio = me?.bio.orEmpty(),
            statusEmoji = me?.statusEmoji.orEmpty(),
            customStatus = me?.customStatus.orEmpty(),
            avatarUrl = me?.avatarUrl,
            bannerUrl = me?.bannerUrl,
            bannerColor = me?.bannerColor,
            bannerPositionY = me?.bannerPositionY ?: 50,
            bannerScale = me?.bannerScale ?: 100,
            profileTheme = me?.profileTheme,
            displayFont = me?.displayFont,
        )
    }
}

// --- Previa do cartao de perfil. ---
//
// Usa o MESMO composable do cartao de verdade (ProfileCard), nao uma copia. Ja
// houve duas copias aqui e as duas divergiram do original — previa que mente e
// pior que previa nenhuma.
//
// As duas LADO A LADO (pedido do dono): cabem juntas, da pra comparar sem rolar,
// e clicar numa abre ela no tamanho de verdade. Espremidas em meia largura elas
// ficam apertadas de proposito — a previa serve pra ver a CARA do cartao; quem
// quiser conferir detalhe clica.
//
// draft = null -> perfil SALVO (aba Conta); draft != null -> rascunho ao vivo
// (aba Perfil), campo a campo, antes de salvar.
@Composable
private fun ProfileCardPreview(me: ProfileUserDto?, draft: ProfileDraft?) {
    if (me == null) {
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Text("carregando…", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
        }
        return
    }
    // O rascunho manda quando existe; senao, o valor salvo.
    val dados = DadosDoCartao(
        nome = draft?.displayName?.trim()?.ifBlank { null } ?: me.displayName ?: me.username,
        username = me.username,
        avatarUrl = draft?.avatarUrl ?: me.avatarUrl,
        bannerUrl = draft?.bannerUrl ?: me.bannerUrl,
        bannerColor = draft?.bannerColor ?: me.bannerColor,
        bannerPositionY = draft?.bannerPositionY ?: me.bannerPositionY ?: 50,
        bannerScale = draft?.bannerScale ?: me.bannerScale ?: 100,
        pronomes = draft?.pronouns ?: me.pronouns,
        bio = draft?.bio ?: me.bio,
        statusEmoji = draft?.statusEmoji ?: me.statusEmoji,
        recado = draft?.customStatus ?: me.customStatus,
        fonte = draft?.displayFont ?: me.displayFont,
        status = me.effectiveStatus,
        criadoEm = me.createdAt,
    )
    var ampliada by remember { mutableStateOf<CardVariante?>(null) }

    // POR QUE ISTO BUSCA DE NOVO um perfil que a tela ja tem: `me` vem de
    // /users/me, e la NAO existe "servidores em comum" — nao faria sentido a
    // rota que devolve voce mesmo calcular o que voce tem em comum com voce.
    // O cartao de verdade vem de /profile/{id}, que calcula. Resultado: a previa
    // desenhava um cartao mais CURTO que o real, faltando a secao inteira, e ela
    // existe justamente pra nao mentir. Uma chamada, uma vez, ao abrir.
    var mutuais by remember(me.id) { mutableStateOf<List<MutualServerDto>>(emptyList()) }
    LaunchedEffect(me.id) {
        mutuais = withContext(Dispatchers.IO) {
            runCatching { GlobalContext.get().get<UserApi>().profile(me.id).data?.mutualServers }
                .getOrNull().orEmpty()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CartaoDaPrevia(
                rotulo = "cartão completo",
                larguraReal = LARGURA_CARTAO_COMPLETO,
                modifier = Modifier.weight(1f),
                aoClicar = { ampliada = CardVariante.COMPLETO },
            ) {
                ProfileCard(dados, CardVariante.COMPLETO, Modifier.fillMaxWidth(), servidoresEmComum = mutuais, animar = false)
            }
            CartaoDaPrevia(
                rotulo = "ao clicar no avatar",
                larguraReal = LARGURA_CARTAO_NORMAL,
                modifier = Modifier.weight(1f),
                aoClicar = { ampliada = CardVariante.NORMAL },
            ) {
                ProfileCard(dados, CardVariante.NORMAL, Modifier.fillMaxWidth(), animar = false)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "é assim que os outros te veem — clique para ver em tamanho real",
            style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
        )
    }

    ampliada?.let { qual ->
        DialogShell(onClose = { ampliada = null }) {
            // Aqui SIM na largura de verdade e com a coreografia ligada: e o cartao
            // como os outros veem, nao a miniatura.
            ProfileCard(
                dados = dados,
                variante = qual,
                modifier = Modifier.width(
                    if (qual == CardVariante.COMPLETO) LARGURA_CARTAO_COMPLETO else LARGURA_CARTAO_NORMAL,
                ),
                servidoresEmComum = if (qual == CardVariante.COMPLETO) mutuais else emptyList(),
            )
        }
    }
}

// Rotulo + a MINIATURA clicavel.
//
// O cartao e desenhado na largura de VERDADE e depois encolhido por escala, em
// vez de ser desenhado apertado numa largura pequena. A diferenca importa: numa
// largura pequena o texto quebra em outros lugares, o avatar fica gigante perto
// do resto e a previa passa a mostrar um cartao que ninguem vai ver. Encolhido
// por escala, e o cartao real visto de longe — proporcao intacta.
//
// O clique fica na caixa de FORA: o cartao de verdade nao e clicavel inteiro, e
// enfiar um clickable nele so pra previa mudaria o componente compartilhado por
// causa de um caso de uso.
@Composable
private fun CartaoDaPrevia(
    rotulo: String,
    larguraReal: Dp,
    modifier: Modifier = Modifier,
    aoClicar: () -> Unit,
    conteudo: @Composable () -> Unit,
) {
    Column(modifier) {
        RotuloDaPrevia(rotulo)
        BoxWithConstraints {
            val larguraRealPx = with(LocalDensity.current) { larguraReal.roundToPx() }
            // Nunca AMPLIA: se sobrar espaco, o cartao fica no tamanho natural.
            val escala = (constraints.maxWidth.toFloat() / larguraRealPx).coerceAtMost(1f)
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val p = measurable.measure(Constraints.fixedWidth(larguraRealPx))
                        // A caixa reserva o tamanho JA ENCOLHIDO; sem isto sobraria
                        // um buraco do tamanho do cartao inteiro embaixo.
                        layout((p.width * escala).toInt(), (p.height * escala).toInt()) { p.place(0, 0) }
                    }
                    .graphicsLayer {
                        scaleX = escala
                        scaleY = escala
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = aoClicar,
                    ),
            ) {
                conteudo()
            }
        }
    }
}

// "Testar" dispara um aviso de bandeja DE VERDADE — o mesmo caminho do aviso de
// mensagem, nao um toast falso desenhado dentro do app. O que costuma falhar e
// justamente o lado do SO (foco de assistencia, notificacao do app desativada no
// Windows), e um toast interno passaria por cima disso e diria "funciona" quando
// nao funciona.
//
// Nao precisa minimizar antes: a regra "so avisa com a janela atras" mora no
// shell, no ponto em que a mensagem chega — nao dentro do envio. Aqui chamamos o
// envio direto, entao o aviso sai mesmo com o Astra na frente.
@Composable
private fun TestarNotificacao(onTestar: () -> Unit) {
    var avisou by remember { mutableStateOf(false) }
    Column {
        // Texto mantido: fica sozinho no fim da seção, e o que ele faz não tem ícone
        // universal — um sino solto leria como "abrir notificações".
        DialogButton(
            if (avisou) "mandei — olhe o canto da tela" else "testar notificação",
            accent = !avisou,
            icone = Lucide.Bell,
        ) {
            avisou = true
            onTestar()
        }
        if (avisou) {
            Spacer(Modifier.height(8.dp))
            Text(
                "não apareceu? o Windows pode estar com o foco de assistência ligado, " +
                    "ou as notificações do Astra desativadas em Sistema > Notificações.",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                modifier = Modifier.widthIn(max = 460.dp),
            )
        }
    }
}

@Composable
private fun RotuloDaPrevia(texto: String) {
    Text(
        texto.uppercase(),
        style = TextStyle(color = Obsidian.text3, fontSize = 9.sp, letterSpacing = 1.4.sp),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

// --- Notificacoes: um toast que desliza da direita, segura e sai — em loop.
// reduceMotion trava ele parado e visivel (respeita o ajuste de movimento). ---
@Composable
private fun NotifPreviewCard(reduceMotion: Boolean) {
    val t = rememberInfiniteTransition(label = "toast")
    val cycle by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
        label = "toastCycle",
    )
    var dx = 0f
    var a = 1f
    if (!reduceMotion) {
        when {
            cycle < 0.14f -> { val k = cycle / 0.14f; dx = (1f - k) * 44f; a = k }
            cycle < 0.82f -> { dx = 0f; a = 1f }
            else -> { val k = (cycle - 0.82f) / 0.18f; dx = k * 44f; a = 1f - k }
        }
    }
    Box(Modifier.fillMaxWidth().offset(x = dx.dp).alpha(a)) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Obsidian.accentDim),
                contentAlignment = Alignment.Center,
            ) {
                Text("A", style = TextStyle(color = Obsidian.accent, fontSize = 15.sp, fontFamily = DmSerif))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Astra", style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
                Text("novo sussurro", style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmSerif))
                Text("e assim que um aviso chega na bandeja.", style = TextStyle(color = Obsidian.text2, fontSize = 11.sp))
            }
        }
    }
}

// --- Aparencia: mini-janela do app (canal + duas mensagens + campo de escrever)
// no tema atual; fonte e densidade reagem ao vivo aos controles ao lado. ---
@Composable
private fun UiSamplePreview(fontSize: FontSizePref, density: DensityPref) {
    val s = fontSize.scale
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised.copy(alpha = 0.6f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#", style = TextStyle(color = Obsidian.text3, fontSize = 15.sp))
            Spacer(Modifier.width(6.dp))
            Text("geral", style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmSerif))
        }
        // As duas mensagens ganham superficie propria — a previa passa a imitar o
        // proprio shell (cabecalho, palco, campo de escrever em degraus), que e
        // exatamente o que ela promete mostrar. Os dois tracos que havia aqui
        // faziam a mini-janela parecer uma tabela de tres linhas.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Obsidian.overlay.copy(alpha = 0.5f))
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            SampleMsg("ana", "e ai, bora marcar a call?", s)
            Spacer(Modifier.height((density.topDp).dp))
            SampleMsg("você", "fechou, 21h entao", s)
        }
        Box(
            Modifier.padding(11.dp).fillMaxWidth().clip(RoundedCornerShape(9.dp))
                .background(Obsidian.void.copy(alpha = 0.5f))
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text("escrever…", style = TextStyle(color = Obsidian.text3, fontSize = (13 * s).sp))
        }
    }
}

@Composable
private fun SampleMsg(name: String, text: String, scale: Float) {
    Row(verticalAlignment = Alignment.Top) {
        val c = userColor(name)
        Box(
            Modifier.size((26 * scale).dp).clip(CircleShape).background(c),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), style = TextStyle(color = Obsidian.textInv, fontSize = (11 * scale).sp))
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(name, style = TextStyle(color = c, fontSize = (12 * scale).sp, fontFamily = DmSerif))
            Text(text, style = TextStyle(color = Obsidian.text2, fontSize = (13 * scale).sp, lineHeight = (18 * scale).sp))
        }
    }
}

// --- Desempenho: medidor de custo ESTIMADO (não mede a GPU real; deriva das
// escolhas). Custo zero de render — so barras que reagem aos toggles. ---
@Composable
private fun CostMeter(p: DesktopPrefs.Prefs) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("custo estimado", style = TextStyle(color = Obsidian.text1, fontSize = 14.sp, fontFamily = DmSerif))
        Spacer(Modifier.height(14.dp))
        val gpu = gpuCost(p)
        val cpu = cpuCost(p)
        CostBar("GPU", gpu)
        Spacer(Modifier.height(12.dp))
        CostBar("CPU", cpu)
        Spacer(Modifier.height(14.dp))
        Text(costVerdict(gpu, cpu), style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
    }
}

@Composable
private fun CostBar(label: String, value: Float) {
    val v by animateFloatAsState(value, tween(340), label = "cost-$label")
    val col = when {
        v < 0.36f -> Obsidian.success
        v < 0.68f -> Obsidian.accent
        else -> Obsidian.danger
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(color = Obsidian.text2, fontSize = 11.sp), modifier = Modifier.width(38.dp))
            Spacer(Modifier.weight(1f))
            Text(costWord(v), style = TextStyle(color = col, fontSize = 10.sp))
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                .background(Obsidian.void.copy(alpha = 0.6f)),
        ) {
            Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(col))
        }
    }
}

private fun gpuCost(p: DesktopPrefs.Prefs): Float {
    if (p.performanceMode) return 0.08f
    var c = 0.06f
    if (p.auroraOn) c += 0.18f + p.auroraQuality.octaves * 0.09f
    if (p.starsOn) c += 0.14f
    if (p.windowTransparent) c += 0.08f
    val mul = when (p.uiFps) { UiFps.FREE -> 1f; UiFps.CAP60 -> 0.82f; UiFps.CAP30 -> 0.6f }
    return (0.06f + (c - 0.06f) * mul).coerceIn(0.05f, 1f)
}

private fun cpuCost(p: DesktopPrefs.Prefs): Float {
    if (p.performanceMode) return 0.06f
    var c = 0.05f
    if (p.auroraOn) c += 0.08f
    if (p.starsOn) c += 0.07f
    if (!p.reduceMotionEff) c += 0.05f
    val mul = when (p.uiFps) { UiFps.FREE -> 1f; UiFps.CAP60 -> 0.85f; UiFps.CAP30 -> 0.65f }
    return (0.05f + (c - 0.05f) * mul).coerceIn(0.04f, 1f)
}

private fun costWord(v: Float) = when {
    v < 0.36f -> "leve"
    v < 0.68f -> "medio"
    else -> "pesado"
}

private fun costVerdict(gpu: Float, cpu: Float): String {
    val m = maxOf(gpu, cpu)
    return when {
        m < 0.36f -> "leve — sobra folga para jogar ou transmitir junto."
        m < 0.68f -> "equilibrado — visual completo sem pesar."
        else -> "pesado — o modo desempenho corta isso num toque."
    }
}

// --- Voz: moldura 16:9 na resolucao/fps escolhido + medidor do mic ao vivo.
// O medidor abre o microfone SO enquanto esta aba/previa esta visivel. ---
@Composable
private fun VoicePreview(p: DesktopPrefs.Prefs) {
    Column(Modifier.fillMaxWidth()) {
        var testing by remember { mutableStateOf(false) }
        val q = p.screenQuality
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Obsidian.raised.copy(alpha = 0.5f))
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                    .background(Obsidian.void.copy(alpha = 0.6f))
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("sua tela", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
            }
            Spacer(Modifier.height(9.dp))
            Text(
                "${q.height}p · ${q.fps}fps · ${q.bitrate / 1_000_000} Mbps",
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Obsidian.raised.copy(alpha = 0.5f))
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Text("seu microfone", style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmSerif))
            Spacer(Modifier.height(10.dp))
            // O medidor so abre o microfone quando VOCE manda testar. Antes ele
            // abria sozinho ao entrar na aba e ficava gravando em segundo plano
            // enquanto a previa vivesse — barulho de privacidade por nada.
            MicMeter(testing, p.micSensitivity)
            Spacer(Modifier.height(10.dp))
            AboutButton(if (testing) "parar teste" else "testar microfone", accent = !testing) {
                testing = !testing
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (p.micNoiseSuppression) "supressao de ruido: ligada" else "supressao de ruido: desligada",
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
            )
        }
    }
}

// Medidor de nível do mic: abre um TargetDataLine (Java Sound) numa thread
// daemon ENQUANTO O TESTE ESTA LIGADO, le o RMS dos samples e move as barras.
// onDispose fecha a linha (parar o teste / troca de aba / fecha configurações).
// Best-effort: sem mic ou em uso -> mostra aviso, não quebra.
@Composable
private fun MicMeter(active: Boolean, threshold: Float = 0f) {
    var level by remember { mutableFloatStateOf(0f) }
    var available by remember { mutableStateOf(true) }
    DisposableEffect(active) {
        if (!active) {
            // Teste desligado: barras zeradas (cinza) e nenhuma linha aberta.
            level = 0f
            available = true
            return@DisposableEffect onDispose { }
        }
        val running = AtomicBoolean(true)
        var line: TargetDataLine? = null
        val worker = thread(isDaemon = true, name = "astra-mic-preview") {
            val fmt = AudioFormat(16_000f, 16, 1, true, false)
            val l = runCatching {
                (AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, fmt)) as TargetDataLine)
                    .apply { open(fmt); start() }
            }.getOrNull()
            if (l == null) { available = false; return@thread }
            line = l
            val buf = ByteArray(1024)
            while (running.get()) {
                val n = runCatching { l.read(buf, 0, buf.size) }.getOrDefault(-1)
                if (n <= 0) break
                var sum = 0.0
                var i = 0
                while (i < n - 1) {
                    val sample = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
                    sum += sample.toDouble() * sample
                    i += 2
                }
                val rms = kotlin.math.sqrt(sum / (n / 2)).toFloat()
                val norm = (rms / 7000f).coerceIn(0f, 1f)
                // sobe rapido, desce suave (o pico decai) — leitura mais viva.
                level = if (norm > level) norm else level * 0.82f + norm * 0.18f
            }
            runCatching { l.stop(); l.close() }
        }
        onDispose {
            running.set(false)
            runCatching { line?.close() }
        }
    }
    if (!available) {
        Text("microfone indisponivel", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
        return
    }
    val lvl by animateFloatAsState(level, tween(90), label = "micLvl")
    // Termometro de qualidade, não alarme: CINZA parado (teste desligado ou
    // silencio — antes ficava vermelho o tempo todo, como se algo estivesse
    // errado), AMBAR quando o sinal e fraco demais pra te ouvirem bem, VERDE
    // quando esta bom. Ambar = warning (fixo) e não accent: com o tema branco
    // padrao o accent e quase cinza e a faixa do meio some.
    // Anima a troca de cor pra não piscar seco entre faixas.
    val meterColor by animateColorAsState(
        when {
            lvl < 0.10f -> Obsidian.text3
            lvl < 0.45f -> Obsidian.warning
            else -> Obsidian.success
        },
        tween(220),
        label = "micColor",
    )
    Box(Modifier.fillMaxWidth().height(30.dp)) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val bars = 16
            for (i in 0 until bars) {
                // envelope em cupula: barras do meio mais altas -> onda de audio.
                val shape = 0.45f + 0.55f * sin((i + 0.5f) / bars * PI).toFloat()
                val h = (lvl * shape).coerceIn(0.05f, 1f)
                Box(
                    Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(2.dp))
                        .background(meterColor.copy(alpha = 0.4f + 0.5f * h)),
                )
            }
        }
        // Marcador do limiar de sensibilidade: linha ambar vertical na fracao —
        // abaixo dela o mic não transmite. Spacers pesados = sem clipar nas pontas.
        if (threshold > 0f) {
            Row(Modifier.fillMaxSize()) {
                val f = threshold.coerceIn(0.02f, 0.98f)
                Spacer(Modifier.weight(f))
                Box(Modifier.width(2.dp).fillMaxHeight().background(Obsidian.accent.copy(alpha = 0.9f)))
                Spacer(Modifier.weight(1f - f))
            }
        }
    }
}

// Slider de sensibilidade de entrada (voice gate). 0 = sempre transmite; arraste
// a alca. O marcador ambar no medidor acima mostra o limiar vs a sua voz.
@Composable
private fun MicSensitivityRow(value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sensibilidade de entrada", style = TextStyle(color = Obsidian.text1, fontSize = 13.sp))
            Text(
                if (value <= 0f) "sempre transmite" else "${(value * 100).toInt()}%",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Obsidian.void.copy(alpha = 0.6f)))
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Obsidian.accent))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val f = value.coerceIn(0f, 1f)
                if (f > 0f) Spacer(Modifier.weight(f))
                Box(Modifier.size(14.dp).clip(CircleShape).background(Obsidian.accent).border(2.dp, Obsidian.raised, CircleShape))
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "abaixo desse nível o mic não transmite. 0 = sempre aberto. teste o mic acima para calibrar.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        )
    }
}

// Aba Perfil: identidade (avatar, nome, pronomes, bio, recado + emoji). O que
// aparece pros outros. Banner/tema/fonte ficam pra fatia 2.
@Composable
private fun ProfileSection(
    me: ProfileUserDto?,
    draft: ProfileDraft,
    onChange: (ProfileDraft) -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busyAvatar by remember { mutableStateOf(false) }
    var busyBanner by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // Recorte estilo Discord: a fonte aberta no modal. null = fechado.
    var cropAvatar by remember { mutableStateOf<CropSource?>(null) }
    var cropBanner by remember { mutableStateOf<CropSource?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        DesktopAvatar(draft.avatarUrl, draft.displayName.ifBlank { me?.username ?: "você" }, 64)
        Spacer(Modifier.width(16.dp))
        // Só ícone: estão colados na foto que operam.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoIcone(Lucide.Upload, "trocar avatar", accent = true, ocupado = busyAvatar) {
                // O dialogo nativo bloqueia (modal) — normal. O peso (ler/decodificar)
                // vai pra fora da thread de UI.
                val file = AvatarPicker.choose() ?: return@BotaoIcone
                busyAvatar = true
                msg = null
                scope.launch {
                    // Animado não pode ser assado num recorte -> caminho antigo.
                    val animated = withContext(Dispatchers.IO) { ImageCrop.isAnimated(file) }
                    if (!animated) {
                        busyAvatar = false
                        cropAvatar = CropSource.Local(file)
                        return@launch
                    }
                    val r = withContext(Dispatchers.IO) { AvatarPicker.encode(file) }
                    busyAvatar = false
                    r.onSuccess { onChange(draft.copy(avatarUrl = it)) }
                        .onFailure { msg = "não foi possível ler essa imagem" to false }
                }
            }
            val avatarAtual = draft.avatarUrl
            if (avatarAtual != null && !ImageCrop.isAnimated(avatarAtual)) {
                BotaoIcone(Lucide.Crop, "reenquadrar") { cropAvatar = CropSource.Remote(avatarAtual) }
            }
            if (draft.avatarUrl != null) {
                BotaoIcone(Lucide.Trash2, "remover avatar", danger = true) { onChange(draft.copy(avatarUrl = null)) }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "a imagem é reduzida para 512px e guardada no seu perfil (máximo 5MB).",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )

    SettingsDivider()
    ProfileField("nome", draft.displayName, me?.username ?: "seu nome") {
        onChange(draft.copy(displayName = it))
    }
    Spacer(Modifier.height(12.dp))
    ProfileField("pronomes", draft.pronouns, "ele/dela/elu…", max = 40) {
        onChange(draft.copy(pronouns = it))
    }
    Spacer(Modifier.height(12.dp))
    ProfileField("bio", draft.bio, "fale de você", multiline = true, max = 300) {
        onChange(draft.copy(bio = it))
    }

    SettingsDivider()
    FieldLabel("banner")
    // Previa ESTATICA. Antes o enquadramento era arrastando DIRETO nesta previa, e
    // cada arrasto recompunha a aba inteira -> a main thread saturava e o ticker do
    // gif do banner parava ("a animação que para"). Agora o enquadramento e no modal
    // "redimensionar" (fora da coluna), entao aqui a animação nunca e interrompida.
    var resizeOpen by remember { mutableStateOf(false) }
    ProfileBanner(
        css = draft.bannerColor,
        imageUrl = draft.bannerUrl,
        positionY = draft.bannerPositionY,
        scale = draft.bannerScale,
        fallback = Obsidian.overlay,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            // Mesma proporcao dos cards (popup/pagina/previa): o que você enquadra aqui e
            // exatamente o que aparece neles.
            .aspectRatio(ProfileBannerAspect)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp)),
    )
    Spacer(Modifier.height(10.dp))
    // Só ícone: a fileira fica logo abaixo do banner que ela opera. Os três nomes
    // por extenso ocupavam a largura inteira do painel.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotaoIcone(Lucide.Upload, "subir imagem", accent = true, ocupado = busyBanner) {
            val file = AvatarPicker.choose("Escolher banner") ?: return@BotaoIcone
            busyBanner = true
            msg = null
            scope.launch {
                val animated = withContext(Dispatchers.IO) { ImageCrop.isAnimated(file) }
                if (!animated) {
                    busyBanner = false
                    cropBanner = CropSource.Local(file)
                    return@launch
                }
                val r = withContext(Dispatchers.IO) {
                    AvatarPicker.encodeComMedidas(file, AvatarPicker.BANNER_DIM)
                }
                busyBanner = false
                // Chega JA PREENCHENDO a faixa. O estatico e assado em 3,5:1 pelo
                // recorte e cai exato; o animado pula o recorte (recortar mataria a
                // animação) e vinha com zoom 100, que em Fit quer dizer "cabe
                // inteira" — e uma imagem 16:9 numa faixa 3,5:1 cabe inteira
                // ocupando metade da largura, com tarja preta dos lados.
                r.onSuccess { img ->
                    onChange(
                        draft.copy(
                            bannerUrl = img.dataUri,
                            bannerPositionY = 50,
                            bannerScale = AvatarPicker.zoomQueCobre(img.largura, img.altura, ProfileBannerAspect),
                        ),
                    )
                }
                    .onFailure { msg = "não foi possível ler essa imagem" to false }
            }
        }
        val bannerNow = draft.bannerUrl
        if (!bannerNow.isNullOrBlank()) {
            // Animado continua no modal de posição+zoom (a animação sobrevive);
            // estatico abre o recorte, que ASSA o enquadramento na imagem.
            if (ImageCrop.isAnimated(bannerNow)) {
                BotaoIcone(Lucide.Move, "redimensionar") { resizeOpen = true }
            } else {
                BotaoIcone(Lucide.Crop, "reenquadrar") { cropBanner = CropSource.Remote(bannerNow) }
            }
            BotaoIcone(Lucide.Trash2, "remover imagem", danger = true) { onChange(draft.copy(bannerUrl = null)) }
        }
    }
    if (resizeOpen && !draft.bannerUrl.isNullOrBlank()) {
        ResizeBannerDialog(
            draft = draft,
            username = me?.username ?: "você",
            onSave = { posY, scale -> onChange(draft.copy(bannerPositionY = posY, bannerScale = scale)) },
            onClose = { resizeOpen = false },
        )
    }
    cropAvatar?.let { src ->
        CropDialog(
            source = src,
            aspect = 1f,
            round = true,
            title = "recortar avatar",
            outW = ImageCrop.AVATAR_OUT_W,
            onApply = { onChange(draft.copy(avatarUrl = it)) },
            onClose = { cropAvatar = null },
        )
    }
    cropBanner?.let { src ->
        CropDialog(
            source = src,
            aspect = ProfileBannerAspect,
            round = false,
            title = "recortar banner",
            outW = ImageCrop.BANNER_OUT_W,
            // Assado na proporcao do card -> posição/zoom voltam pro neutro.
            onApply = { onChange(draft.copy(bannerUrl = it, bannerPositionY = 50, bannerScale = 100)) },
            onClose = { cropBanner = null },
        )
    }
    Spacer(Modifier.height(14.dp))
    FieldLabel("cor do perfil")
    // UM seletor so: o gradiente atravessa banner + corpo como uma peca (Discord).
    // Grava nas DUAS colunas (bannerColor e profileTheme) de proposito — o web e o
    // mobile ainda pintam a faixa e o corpo separados, entao escrever as duas
    // mantem a mesma cor em todo cliente em vez de deixar um deles pra tras.
    ColorPickerButton(draft.bannerColor) {
        onChange(draft.copy(bannerColor = it, profileTheme = it))
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "a cor atravessa o cartao inteiro. com imagem de banner, ela aparece do banner para baixo.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 420.dp),
    )

    SettingsDivider()
    FieldLabel("fonte do seu nome")
    FontPicker(draft.displayFont) { onChange(draft.copy(displayFont = it)) }

    SettingsDivider()
    FieldLabel("recado")
    Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusEmojiButton(draft.statusEmoji) { onChange(draft.copy(statusEmoji = it)) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (draft.customStatus.isEmpty()) {
                Text("Como foi seu dia?", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
            }
            BasicTextField(
                value = draft.customStatus,
                onValueChange = { onChange(draft.copy(customStatus = it.take(100))) },
                singleLine = true,
                textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                cursorBrush = SolidColor(Obsidian.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    // Feedback de upload de avatar/banner. O salvar migrou pra BAIXO da previa
    // (sempre a vista enquanto edita) -> ProfileSaveButton, la no topo da tela.
    msg?.let { (text, ok) ->
        Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
    }
}

// Botao Salvar do perfil, HOISTADO pra baixo da previa (o dono pediu: sempre a
// vista, não no fim do formulario). Estado proprio (saving/msg/dirty); le o
// draft vivo + o `me` original. Recado tem rota propria (so manda se mudou).
@Composable
private fun ProfileSaveButton(
    me: ProfileUserDto?,
    draft: ProfileDraft,
    onChange: (ProfileDraft) -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    val original = remember(me) { ProfileDraft.from(me) }
    var saving by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val dirty = draft != original
    Column(modifier) {
        msg?.let { (text, ok) ->
            Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AboutButton(if (saving) "salvando…" else "salvar", accent = true, icone = Lucide.Check) {
                if (saving || !dirty) return@AboutButton
                saving = true
                msg = null
                scope.launch {
                    val api = koin.get<UserApi>()
                    val r = runCatching {
                        if (draft.customStatus.trim() != original.customStatus.trim()) {
                            api.setCustomStatus(CustomStatusRequest(draft.customStatus.trim()))
                        }
                        api.updateProfile(
                            UpdateProfileRequest(
                                // null = chave omitida = backend não mexe no campo.
                                displayName = draft.displayName.trim().ifBlank { null },
                                pronouns = draft.pronouns.trim(),
                                bio = draft.bio.trim(),
                                avatarUrl = draft.avatarUrl,
                                statusEmoji = draft.statusEmoji,
                                // Banner: "" limpa a imagem (null seria "não mexer").
                                bannerUrl = draft.bannerUrl ?: "",
                                bannerColor = draft.bannerColor,
                                bannerPositionY = draft.bannerPositionY,
                                bannerScale = draft.bannerScale,
                                profileTheme = draft.profileTheme,
                                displayFont = draft.displayFont,
                            ),
                        )
                    }
                    saving = false
                    if (r.isSuccess) { msg = "perfil salvo" to true; onSaved() }
                    // O ERRO REAL do backend. "tenta de novo" era conselho ruim: as
                    // causas comuns aqui (imagem grande demais -> 413, nome de
                    // usuário em uso -> 409) não melhoram tentando de novo, e a
                    // mensagem generica escondia justamente qual delas era.
                    else msg = saveErrorMessage(r.exceptionOrNull()) to false
                }
            }
            if (dirty && !saving) {
                AboutButton("descartar", accent = false) { onChange(original); msg = null }
            }
        }
        if (!dirty && msg == null) {
            Spacer(Modifier.height(6.dp))
            Text("nada mudou ainda.", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
        }
    }
}

// Traduz a falha do salvar pro que a pessoa precisa FAZER. O corpo de erro do
// backend e `{ "error": "..." }` e ja vem em português — so o 413 ganha um texto
// proprio, porque "Arquivo muito grande" sozinho não diz o que fazer a respeito.
private fun saveErrorMessage(t: Throwable?): String {
    val http = t as? HttpException ?: return "sem conexão com o servidor"
    if (http.code() == 413) return "a imagem ficou grande demais — escolha uma menor ou dê menos zoom"
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull()
    val parsed = body?.let {
        runCatching { Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }.getOrNull()
    }
    return parsed?.takeIf { it.isNotBlank() } ?: "não foi possível salvar (erro ${http.code()})"
}

// Zoom do banner: trilha arrastavel simples, de 50% a 300%.
//
// A FAIXA E A DO SERVIDOR, e nao um numero escolhido aqui. Ela ia de 0 a 300 e o
// schema aceitava 50 a 200: passar de 200 (ou ficar abaixo de 50) fazia o servidor
// recusar o PATCH INTEIRO com "Dados inválidos" — sumia o salvamento do nome e da
// bio junto, sem dizer de qual campo. O teto virou 300 nos dois lados; o piso de 50
// ficou, porque abaixo disso a imagem vira um ponto no meio da faixa.
private const val ZOOM_MIN = 50
private const val ZOOM_MAX = 300

@Composable
private fun ZoomTrack(scale: Int, onChange: (Int) -> Unit) {
    val faixa = (ZOOM_MAX - ZOOM_MIN).toFloat()
    val pct = ((scale - ZOOM_MIN) / faixa).coerceIn(0f, 1f)
    Row(
        Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("zoom", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp), modifier = Modifier.width(42.dp))
        Box(
            Modifier
                .weight(1f)
                .height(22.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        onChange(ZOOM_MIN + (f * faixa).toInt())
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Obsidian.void.copy(alpha = 0.6f)),
            )
            Box(
                Modifier.fillMaxWidth(pct).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Obsidian.accent),
            )
            // Alca agarravel no fim do preenchido: spacers pesados poem o centro na
            // fracao atual sem clipar nas pontas. Arrastar em qualquer ponto do
            // trilho também move (o gesto já cobre o Box inteiro).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val f = pct.coerceIn(0f, 1f)
                if (f > 0f) Spacer(Modifier.weight(f))
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(Obsidian.accent).border(2.dp, Obsidian.raised, CircleShape),
                )
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("${scale}%", style = TextStyle(color = Obsidian.text2, fontSize = 11.sp))
    }
}

// Popup que cobre a JANELA inteira (offset zero) — o conteudo desenha o scrim e
// centraliza o cartao. Mesmo idioma do modal central do ProfilePage.
private object OverlayCenter : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

// Modal de "redimensionar banner": mostra o MINI CARD (o que os outros veem) com a
// imagem arrastavel + zoom. Vive FORA da coluna das configs, entao arrastar aqui
// recompoe so este cartaozinho — não a aba inteira — e o gif do banner continua
// animando (era o bug: o drag na previa recompunha a pagina toda e matava o ticker).
// Trabalha em estado LOCAL (posY/scl) e so aplica no "salvar"; cancelar descarta.
@Composable
private fun ResizeBannerDialog(
    draft: ProfileDraft,
    username: String,
    onSave: (posY: Int, scale: Int) -> Unit,
    onClose: () -> Unit,
) {
    var posY by remember { mutableStateOf(draft.bannerPositionY) }
    var scl by remember { mutableStateOf(draft.bannerScale) }
    val name = draft.displayName.ifBlank { username }
    Popup(
        popupPositionProvider = OverlayCenter,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Obsidian.void.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(360.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Obsidian.raised)
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(14.dp))
                    // Clique no cartao NAO fecha (so o scrim atras fecha).
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .padding(18.dp),
            ) {
                Text(
                    "redimensionar banner",
                    style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "é isto que os outros veem. arraste na imagem para enquadrar.",
                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                )
                Spacer(Modifier.height(16.dp))
                // MINI CARD fiel ao popup: banner + avatar sobreposto + nome.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
                ) {
                    ProfileBanner(
                        css = draft.bannerColor,
                        imageUrl = draft.bannerUrl,
                        positionY = posY,
                        scale = scl,
                        fallback = Obsidian.overlay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ProfileBannerAspect)
                            // Cursor de mão sobre a area arrastavel (sinaliza "pega e move").
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    // ~0.9px por ponto: a faixa 0..100 cobre ~a altura do
                                    // cartao -> arraste 1:1, bem mais facil (era 1.4, lento).
                                    // Arrastar pra BAIXO revela o topo (posição diminui).
                                    posY = (posY - drag.y / 0.9f).toInt().coerceIn(0, 100)
                                }
                            },
                    )
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Box(Modifier.offset(y = (-30).dp)) {
                            DesktopAvatar(draft.avatarUrl, name, 72)
                        }
                        Column(Modifier.offset(y = (-8).dp)) {
                            Text(
                                name,
                                style = TextStyle(
                                    color = Obsidian.text1, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                                    fontFamily = profileFontFamily(draft.displayFont),
                                ),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "@$username",
                                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                ZoomTrack(scl) { scl = it }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AboutButton("cancelar", accent = false) { onClose() }
                    AboutButton("salvar", accent = true) { onSave(posY, scl); onClose() }
                }
            }
        }
    }
}

// Grade dos gradientes prontos (mesma lista do web). Cada pastilha pinta o
// proprio gradiente — o que você ve e o que salva.
@Composable
private fun ColorPickerButton(selected: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val hov = remember { MutableInteractionSource() }
    val h by hov.collectIsHoveredAsState()
    Box {
        Row(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(if (h) Obsidian.hover else Obsidian.raised)
                .border(
                    1.dp,
                    if (open) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim,
                    RoundedCornerShape(9.dp),
                )
                .hoverable(hov)
                .clickable { open = !open }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp, 22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .drawBehind {
                        drawRect(bannerBrush(selected, size.width, size.height, Obsidian.overlay))
                    }
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                colorLabel(selected),
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, fontFamily = DmMono),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            LIcon(Lucide.ChevronDown, tint = Obsidian.text3, size = 14.dp)
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 44),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .popupReveal()
                        .width(390.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    FieldLabel("código hex")
                    HexField(selected, onPick)
                    Spacer(Modifier.height(12.dp))
                    // Cor solida e gradiente sao duas escolhas irmas; cada uma no
                    // seu cartao le melhor que uma linha entre elas.
                    CartaoInterno(fundo = Obsidian.hover, padding = PaddingValues(10.dp)) {
                        FieldLabel("gradientes")
                        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                            GradientGrid(selected) { onPick(it); open = false }
                        }
                    }
                }
            }
        }
    }
}

// Campo de cor solida. Grava no MESMO campo do gradiente ("#rrggbb" e um valor
// valido pro bannerBrush e pro web/mobile), entao escolher um gradiente depois
// simplesmente sobrescreve. So aplica quando fecham 6 digitos: teclar no meio
// não deve pintar um valor pela metade.
@Composable
private fun HexField(selected: String?, onPick: (String) -> Unit) {
    // Se o valor de fora e um gradiente, o campo comeca vazio (não ha hex que o
    // represente). Rechaveia quando o valor muda por fora (ex.: clicou num
    // gradiente da grade logo abaixo).
    var text by remember(selected) {
        mutableStateOf(selected?.trim()?.takeIf { it.startsWith("#") }?.removePrefix("#").orEmpty())
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp, fontFamily = DmMono))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text(
                    "c9a96e",
                    style = TextStyle(color = Obsidian.text3, fontSize = 13.sp, fontFamily = DmMono),
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val clean = raw.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                        .lowercase().take(6)
                    text = clean
                    if (clean.length == 6) onPick("#$clean")
                },
                singleLine = true,
                textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmMono),
                cursorBrush = SolidColor(Obsidian.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        // Amostra do que esta digitado — so ganha cor com o hex completo.
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .drawBehind {
                    val css = if (text.length == 6) "#$text" else null
                    drawRect(bannerBrush(css, size.width, size.height, Obsidian.overlay))
                }
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(5.dp)),
        )
    }
}

// Rotulo da pastilha: nome do gradiente conhecido, o proprio hex, ou um aviso
// generico pra um CSS que veio de outro cliente e não esta na lista.
private fun colorLabel(css: String?): String {
    val raw = css?.trim().orEmpty()
    if (raw.isEmpty()) return "padrao"
    BANNER_GRADIENTS.find { it.css == raw }?.let { return it.label.lowercase() }
    return if (raw.startsWith("#")) raw.lowercase() else "gradiente proprio"
}

@Composable
private fun GradientGrid(selected: String?, onPick: (String) -> Unit) {
    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BANNER_GRADIENTS.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { g ->
                    val active = selected == g.css
                    Box(
                        Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .drawBehind { drawRect(bannerBrush(g.css, size.width, size.height, Obsidian.overlay)) }
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) Obsidian.accent else Obsidian.borderDim,
                                RoundedCornerShape(7.dp),
                            )
                            .clickable { onPick(g.css) },
                    )
                }
                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// Fonte do nome: cada linha se desenha NA PROPRIA fonte, entao da pra ver a
// diferenca antes de escolher (inclusive as que se parecem no desktop).
@Composable
private fun FontPicker(selected: String?, onPick: (String) -> Unit) {
    val current = selected ?: "serif"
    val cur = PROFILE_FONTS.find { it.id == current } ?: PROFILE_FONTS.first()
    var open by remember { mutableStateOf(false) }
    val hov = remember { MutableInteractionSource() }
    val h by hov.collectIsHoveredAsState()
    Box {
        // Botao (mesmo visual do "fundo do cartao"): mostra a fonte atual escrita
        // NELA MESMA + chevron. Clique abre o dropdown.
        Row(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(if (h) Obsidian.hover else Obsidian.raised)
                .border(
                    1.dp,
                    if (open) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim,
                    RoundedCornerShape(9.dp),
                )
                .hoverable(hov)
                .clickable { open = !open }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                cur.label,
                style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = cur.family),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            LIcon(Lucide.ChevronDown, tint = Obsidian.text3, size = 14.dp)
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 46),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .popupReveal()
                        .width(390.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    PROFILE_FONTS.forEach { f ->
                        val active = f.id == current
                        val rowHov = remember { MutableInteractionSource() }
                        val rh by rowHov.collectIsHoveredAsState()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Obsidian.active else if (rh) Obsidian.hover else Color.Transparent)
                                .hoverable(rowHov)
                                .clickable { onPick(f.id); open = false }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                f.label,
                                style = TextStyle(
                                    color = if (active) Obsidian.text1 else Obsidian.text2,
                                    fontSize = 15.sp,
                                    fontFamily = f.family,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            if (active) {
                                Spacer(Modifier.width(8.dp))
                                LIcon(Lucide.Check, tint = Obsidian.accent, size = 15.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Campo de texto simples do perfil (rotulo + caixa). Multilinha pra bio.
@Composable
private fun ProfileField(
    label: String,
    value: String,
    placeholder: String,
    multiline: Boolean = false,
    max: Int = 190,
    onChange: (String) -> Unit,
) {
    FieldLabel(label)
    Box(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
        }
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(max)) },
            singleLine = !multiline,
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp, lineHeight = 18.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = if (multiline) Modifier.fillMaxWidth().height(70.dp) else Modifier.fillMaxWidth(),
        )
    }
}

// Emoji do recado: reusa a MESMA grade das reacoes do chat (ReactionPicker).
// Clicar no emoji já escolhido limpa.
@Composable
private fun StatusEmojiButton(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.raised)
                .border(1.dp, if (open) Obsidian.accent else Obsidian.borderDim, RoundedCornerShape(8.dp))
                .clickable { if (current.isNotBlank()) onPick("") else open = true },
            contentAlignment = Alignment.Center,
        ) {
            if (current.isBlank()) {
                LIcon(Lucide.SmilePlus, tint = Obsidian.text3, size = 18.dp)
            } else {
                Text(current, style = TextStyle(fontSize = 18.sp))
            }
        }
        if (open) {
            Popup(
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                PopupReveal {
                    ReactionPicker(onPick = { onPick(it); open = false })
                }
            }
        }
    }
}

@Composable
private fun AccountSection(me: ProfileUserDto?) {
    ReadRow(Lucide.Mail, me?.email ?: "—")
    Spacer(Modifier.height(8.dp))
    ReadRow(Lucide.User, me?.let { "@${it.username}" } ?: "—")
    Spacer(Modifier.height(22.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        LIcon(Lucide.Key, tint = Obsidian.text2, size = 16.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            if (me?.hasPassword == false) "definir senha" else "trocar senha",
            style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif),
        )
    }
    Spacer(Modifier.height(4.dp))
    if (me?.hasPassword == false) {
        Text(
            "conta google sem senha — defina uma para entrar por email também.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        )
    }
    Spacer(Modifier.height(12.dp))
    PasswordForm(hasPassword = me?.hasPassword != false)
}

// Aba Sessões: cada login vivo da conta (um refresh token). Serve pra ver de
// onde a conta esta aberta e derrubar o que você não reconhece — o único item
// da migracao com peso de seguranca.
@Composable
private fun SessionsSection() {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionDto>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        sessions = runCatching { koin.get<SessionApi>().list().data?.sessions }.getOrNull() ?: emptyList()
    }

    Text(
        "cada linha e um login ativo na sua conta. não reconhece algum? derruba.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(14.dp))

    msg?.let { (text, ok) ->
        Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
        Spacer(Modifier.height(10.dp))
    }

    val list = sessions
    when {
        list == null -> Text("carregando…", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
        list.isEmpty() -> Text("nenhuma sessão ativa.", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
        else -> Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { s ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Obsidian.raised.copy(alpha = 0.5f))
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            prettyAgent(s.userAgent),
                            style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(s.ip, prettyDate(s.lastUsedAt)?.let { "visto $it" })
                                .joinToString("  ·  "),
                            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    // Só ícone: repete uma vez por linha e cada um está encostado na
                    // sessão que derruba. É onde ícone puro mais compensa — o rótulo
                    // repetido três vezes só empilhava ruído. E é reversível: quem
                    // for derrubado por engano só entra de novo.
                    BotaoIcone(Lucide.LogOut, "derrubar esta sessão", danger = true, ocupado = busy) {
                        busy = true; msg = null
                        scope.launch {
                            val r = runCatching { koin.get<SessionApi>().revoke(s.id) }
                            busy = false
                            msg = if (r.isSuccess) "sessão derrubada" to true
                            else "não foi possível derrubar" to false
                            reload++
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    // Texto mantido: é em lote e destrutivo. Um ícone sozinho aqui derrubaria todo
    // mundo com um clique de curiosidade.
    AboutButton(if (busy) "…" else "derrubar todas as outras", accent = false, icone = Lucide.LogOut) {
        if (busy) return@AboutButton
        busy = true; msg = null
        scope.launch {
            // A rota exige o refresh token DESTA sessão pra não te derrubar junto.
            val token = koin.get<SessionStore>().load()?.refreshToken
            if (token.isNullOrBlank()) {
                busy = false
                msg = "não achei o token desta sessão" to false
                return@launch
            }
            val r = runCatching { koin.get<SessionApi>().revokeOthers(RevokeOthersRequest(token)) }
            busy = false
            msg = r.map { "derrubadas: ${it.data?.revokedCount ?: 0}" to true }
                .getOrElse { "não foi possível derrubar as outras" to false }
            reload++
        }
    }
    Spacer(Modifier.height(20.dp))
}

// User-agent cru e ilegivel; extrai o navegador/app e o SO. O desktop manda o
// proprio identificador, entao normalmente e so "Astra Desktop".
private fun prettyAgent(ua: String?): String {
    val s = ua?.trim().orEmpty()
    if (s.isEmpty()) return "dispositivo desconhecido"
    if (s.contains("Astra", true)) return s.take(48)
    val os = when {
        s.contains("Windows", true) -> "Windows"
        s.contains("Android", true) -> "Android"
        s.contains("iPhone", true) || s.contains("iPad", true) -> "iOS"
        s.contains("Mac", true) -> "macOS"
        s.contains("Linux", true) -> "Linux"
        else -> null
    }
    val app = when {
        s.contains("Edg", true) -> "Edge"
        s.contains("Chrome", true) -> "Chrome"
        s.contains("Firefox", true) -> "Firefox"
        s.contains("Safari", true) -> "Safari"
        else -> "navegador"
    }
    return listOfNotNull(app, os).joinToString(" · ")
}

// ISO-8601 -> "19/07 15:40". Sem lib: corta os pedacos do proprio texto.
private fun prettyDate(iso: String?): String? {
    val s = iso?.trim().orEmpty()
    if (s.length < 16) return null
    val d = s.substring(8, 10)
    val m = s.substring(5, 7)
    val hm = s.substring(11, 16)
    return "$d/$m $hm"
}

// Aba Sobre: versão atual + auto-update (checagem manual, progresso e reinicio).
// O gate de boot já verifica sozinho; aqui e o controle manual + fallback.
@Composable
private fun AboutSection() {
    val updater = remember { GlobalContext.get().get<UpdateService>() }
    val st by updater.state.collectAsState()
    val scope = rememberCoroutineScope()

    ReadRow("versão", updater.currentVersion)
    Spacer(Modifier.height(22.dp))

    if (!updater.installed) {
        Text(
            "atualizacoes automaticas so no app instalado (isto e um build de dev).",
            style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
        return
    }

    Text("atualizacoes", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "o Astra verifica ao abrir e a cada 20 minutos. você também pode procurar agora.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(14.dp))

    when (val s = st) {
        is UpdateState.Checking -> AboutStatus("procurando atualizacoes…")
        // Diz CONTRA O QUE comparou e QUANDO. "você está na última versão" sozinho
        // nao da pra checar: quem acabou de ver uma release sair no GitHub nao tem
        // como saber se o app olhou agora ou quando abriu, de manha.
        is UpdateState.UpToDate -> AboutStatus(
            "você está na ${s.vista} — a mais nova publicada, conferido ${haQuantoTempo(s.conferidoEm)}",
        )
        is UpdateState.Available -> {
            AboutStatus("nova versão ${s.version} disponível")
            Spacer(Modifier.height(10.dp))
            AboutButton("baixar e reiniciar", accent = true) { scope.launch { updater.downloadAndStage(s) } }
        }
        is UpdateState.Downloading -> {
            AboutStatus("baixando ${s.version}… ${(s.progress * 100).toInt()}%")
            Spacer(Modifier.height(10.dp))
            Progress(
                s.progress,
                Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                Obsidian.accent,
                Obsidian.overlay,
                6.dp,
                ProgressAnimation.Spring,
            )
        }
        is UpdateState.Ready -> {
            AboutStatus("${s.version} baixada — reinicie para aplicar")
            Spacer(Modifier.height(10.dp))
            AboutButton("reiniciar agora", accent = true) { updater.restartToInstall() }
        }
        is UpdateState.Failed -> {
            AboutStatus(s.reason)
            if (s.releaseUrl != null) {
                Spacer(Modifier.height(10.dp))
                AboutButton("abrir pagina do release", accent = false) {
                    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(s.releaseUrl)) }
                }
            }
        }
        else -> {}
    }

    Spacer(Modifier.height(16.dp))
    AboutButton("procurar atualizações", accent = false, icone = Lucide.RefreshCw) { scope.launch { updater.check() } }
}

// "agora mesmo" / "há 12 min" / "há 2 h". Precisao grossa de proposito: o que
// importa e se a informacao e de agora ou de horas atras.
private fun haQuantoTempo(quando: Long): String {
    val min = (System.currentTimeMillis() - quando) / 60_000
    return when {
        min < 1L  -> "agora mesmo"
        min < 60L -> "há $min min"
        else      -> "há ${min / 60} h"
    }
}

@Composable
private fun AboutStatus(text: String) {
    Text(text, style = TextStyle(color = Obsidian.text2, fontSize = 13.sp))
}

// Mantém o TEXTO e ganha um ícone à esquerda. É o outro lado da regra dos ícones:
// vira ícone puro só quem está encostado no objeto que opera e repete. Estes ficam
// sozinhos no fim de uma seção — sem vizinho pra comparar, ícone puro seria charada.
@Composable
private fun AboutButton(label: String, accent: Boolean, icone: ImageVector? = null, onClick: () -> Unit) {
    val cor = if (accent) Obsidian.accent else Obsidian.text2
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (accent) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icone?.let {
            LIcon(it, tint = cor, size = 14.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(label, style = TextStyle(color = cor, fontSize = 13.sp))
    }
}

@Composable
private fun PasswordForm(hasPassword: Boolean) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // texto + ok?

    if (hasPassword) {
        PasswordField("senha atual", current) { current = it; msg = null }
        Spacer(Modifier.height(8.dp))
    }
    PasswordField("nova senha", next) { next = it; msg = null }
    Spacer(Modifier.height(8.dp))
    PasswordField("confirmar nova senha", confirm) { confirm = it; msg = null }
    Spacer(Modifier.height(12.dp))

    msg?.let { (text, ok) ->
        Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
        Spacer(Modifier.height(8.dp))
    }

    val canSave = !busy && next.length >= 8 && next == confirm && (!hasPassword || current.isNotBlank())
    val corSalvar = if (canSave) Obsidian.accent else Obsidian.text3
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (canSave) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(enabled = canSave) {
                busy = true
                msg = null
                scope.launch {
                    val result = runCatching {
                        val api = koin.get<UserApi>()
                        if (hasPassword) api.changePassword(ChangePasswordRequest(current, next))
                        else api.setPassword(SetPasswordRequest(next))
                    }
                    busy = false
                    if (result.isSuccess) {
                        current = ""; next = ""; confirm = ""
                        msg = "senha atualizada" to true
                    } else {
                        msg = "não deu — confira a senha atual" to false
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(Lucide.Check, tint = corSalvar, size = 14.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            if (busy) "salvando…" else "salvar",
            style = TextStyle(color = corSalvar, fontSize = 13.sp),
        )
    }
    if (next.isNotEmpty() && next.length < 8) {
        Spacer(Modifier.height(6.dp))
        Text("mínimo 8 caracteres", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
    }
}

@Composable
private fun PasswordField(placeholder: String, value: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            // Campo de formulario (~420), NAO a coluna toda. A ordem importa:
            // widthIn ANTES de fillMaxWidth — invertido, o fillMaxWidth fixava a
            // largura no pai e o cap de 360 era reconstrangido de volta (era o bug
            // do input de senha esticando pelo eixo X inteiro).
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReadRow(label: String, value: String) {
    Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TextStyle(color = Obsidian.text3, fontSize = 12.sp), modifier = Modifier.width(80.dp))
        Text(value, style = TextStyle(color = Obsidian.text1, fontSize = 13.sp))
    }
}

// Variante com ícone Lucide no lugar do rotulo (Conta: envelope no email, pessoa no usuário).
@Composable
private fun ReadRow(icon: ImageVector, value: String) {
    Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LIcon(icon, tint = Obsidian.text3, size = 16.dp)
        Spacer(Modifier.width(12.dp))
        Text(value, style = TextStyle(color = Obsidian.text1, fontSize = 13.sp))
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, sub: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (active) Obsidian.active else if (hovered) Obsidian.hover else androidx.compose.ui.graphics.Color.Transparent,
        tween(120),
    )
    // Borda cinza sutil pra o topico se destacar do fundo (senao "some" na aurora):
    // repouso = borderDim apagado, hover = borderMid, ativo = accent. Cada secao le
    // como um item clicavel mesmo parada.
    val border by animateColorAsState(
        when {
            active -> Obsidian.accent.copy(alpha = 0.45f)
            hovered -> Obsidian.borderMid
            else -> Obsidian.borderDim.copy(alpha = 0.55f)
        },
        tween(120),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickScale(interaction)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(
            icon,
            tint = if (active || hovered) Obsidian.text1 else Obsidian.text3,
            size = 16.dp,
        )
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                label,
                style = TextStyle(
                    color = if (active || hovered) Obsidian.text1 else Obsidian.text2,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                ),
            )
            Text(sub, style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
        }
    }
}

// Aba Voz: qualidade da transmissão de tela (presets) + processamento do mic.
// Seletor de dispositivo de audio. null = padrao do sistema — e a PRIMEIRA opcao
// de proposito: e o que funciona pra maioria e o que o dono pediu ("seguir o
// Windows"). Lista vazia (nenhum dispositivo achado) ainda mostra o padrao.
@Composable
private fun DeviceDropdown(devices: List<String>, selected: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val hov = remember { MutableInteractionSource() }
    val h by hov.collectIsHoveredAsState()
    Box {
        Row(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(if (h) Obsidian.hover else Obsidian.raised)
                .border(
                    1.dp,
                    if (open) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim,
                    RoundedCornerShape(9.dp),
                )
                .hoverable(hov)
                .clickable { open = !open }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected ?: "padrão do Windows",
                style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            LIcon(Lucide.ChevronDown, tint = Obsidian.text3, size = 14.dp)
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 46),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .popupReveal()
                        .widthIn(min = 240.dp, max = 460.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                ) {
                    DeviceRow("padrão do Windows", selected == null) { onPick(null); open = false }
                    devices.forEach { d ->
                        DeviceRow(d, selected == d) { onPick(d); open = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(label: String, active: Boolean, onClick: () -> Unit) {
    val hov = remember { MutableInteractionSource() }
    val h by hov.collectIsHoveredAsState()
    Text(
        label,
        style = TextStyle(color = if (active) Obsidian.accent else Obsidian.text2, fontSize = 12.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (h) Obsidian.hover else Obsidian.overlay)
            .hoverable(hov)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

// Nota explicativa ao lado do ajuste que ela explica. Comeca RECOLHIDA: quem so
// quer mexer no ajuste não tem um paragrafo na frente, e quem estranhou o
// comportamento acha a resposta onde procurou — em vez de num FAQ que não existe.
@Composable
private fun InfoNote(title: String, body: String) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Obsidian.overlay.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
            .clickable { open = !open }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LIcon(Lucide.Info, tint = Obsidian.accent, size = 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                modifier = Modifier.weight(1f),
            )
            Text(
                if (open) "−" else "+",
                style = TextStyle(color = Obsidian.text3, fontSize = 13.sp),
            )
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 17.sp),
            )
        }
    }
}

// Aba Permissões — a casa de quem já usava o Astra antes desta tela existir, ou
// de quem passou reto pelas boas-vindas. A lista e a mesma de lá
// (PainelDePermissoes); a diferença e o `detalhado`, que aqui mostra o estado até
// das linhas certas: quem abre esta aba veio investigar, e "ouvindo normalmente
// (Microfone Realtek)" e justamente o que responde "então o problema não é esse".
@Composable
private fun PermissionsSection(onTestarAviso: () -> Unit) {
    Text(
        "o Windows decide o que cada programa pode usar — e quando ele bloqueia, não avisa: o microfone entrega silêncio, o aviso não aparece, a call não conecta. aqui é possível ver o que está liberado e liberar o que faltar.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.5.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 560.dp),
    )
    Spacer(Modifier.height(16.dp))
    PainelDePermissoes(onTestarAviso = onTestarAviso, modifier = Modifier.widthIn(max = 560.dp))
    Spacer(Modifier.height(20.dp))
    InfoNote(
        "Por que não aparece a janelinha de \"permitir\"",
        "No navegador, um site pede permissão e você responde num pop-up. Programa " +
            "instalado no Windows não tem esse pedido: quem manda é um interruptor do " +
            "próprio sistema, o mesmo para todos os programas de área de trabalho.\n\n" +
            "Por isso o botão \"permitir\" aqui abre a página exata das Configurações do " +
            "Windows em vez de perguntar — e continua conferindo sozinho depois. Você liga " +
            "o interruptor lá, volta para cá, e a linha já está verde sem precisar clicar de novo.\n\n" +
            "Duas fogem da regra. Avisos não têm interruptor para ligar: o Windows só " +
            "registra o Astra quando ele manda o primeiro aviso, então permitir manda um. " +
            "E transmitir a tela não pede permissão nenhuma no Windows — inventar um " +
            "cadeado ali seria teatro.",
    )
}

@Composable
private fun VoiceSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    Text("Transmissao de tela", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "vale ao iniciar a transmissão. o padrao 1080p60 e o mínimo que combinamos.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(10.dp))
    RadioList(
        ScreenQuality.entries.map { it.label to it },
        p.screenQuality, prefs::setScreenQuality,
    )
    Spacer(Modifier.height(12.dp))
    // Explicacao ao lado do que ela afeta. Sem isto o usuario ve a propria janelinha
    // mais "travada" que a transmissão e acha que a transmissão esta ruim tambem.
    InfoNote(
        "Por que a sua prévia parece mais travada",
        "Quem está assistindo recebe os 60 quadros por segundo do preset acima — " +
            "isso não muda. A sua prévia aqui do lado é limitada a 30, de propósito.\n\n" +
            "O motivo: seu processador é quem comprime o vídeo (a placa de vídeo não " +
            "faz esse trabalho neste app). Cada quadro da prévia consome processador " +
            "que sairia da compressão — e quando falta, quem engasga é a transmissão " +
            "de quem está te assistindo, não a sua janelinha.\n\n" +
            "30 quadros já são fluidos para conferir o que você está mostrando, e " +
            "devolvem metade desse custo para quem importa: quem está do outro lado.",
    )

    // As permissões do Windows moram na aba Permissões. Ficavam aqui como um
    // atalho que abria um diálogo com a MESMA lista — duas casas pra uma coisa só
    // envelhece mal (uma das duas deixa de ser atualizada).

    SettingsDivider()
    Text("Ninguém te escuta?", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "entrar numa call passa por várias etapas, e todas falham do mesmo jeito: silêncio. a lista abaixo mostra até onde chegou — a etapa que faltar é a culpada.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(10.dp))
    VoicePassos()

    SettingsDivider()
    Text("Dispositivos", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "\"padrao do Windows\" segue o que você escolheu no sistema — inclusive se trocar depois.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(10.dp))
    // Enumerado uma vez ao abrir a aba: listar SAIDA abre um modulo do WebRTC, e
    // não vale refazer isso a cada recomposicao.
    val outs = remember { AudioDevices.outputs() }
    val ins = remember { AudioDevices.inputs() }
    FieldLabel("saída (quem você ouve)")
    DeviceDropdown(outs, p.audioOutput, prefs::setAudioOutput)
    Spacer(Modifier.height(12.dp))
    FieldLabel("entrada (seu microfone)")
    DeviceDropdown(ins, p.audioInput, prefs::setAudioInput)

    SettingsDivider()
    Text("Microfone", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(10.dp))
    ToggleRow("Supressao de ruido", "corta ventilador, teclado e chiado de fundo", p.micNoiseSuppression, prefs::setMicNoiseSuppression)
    ToggleRow("Cancelamento de eco", "evita o retorno do audio dos outros pelo seu mic", p.micEchoCancel, prefs::setMicEchoCancel)
    ToggleRow("Ganho automatico", "nivela o volume da sua voz sozinho", p.micAutoGain, prefs::setMicAutoGain)
    Spacer(Modifier.height(12.dp))
    MicSensitivityRow(p.micSensitivity, prefs::setMicSensitivity)
    Spacer(Modifier.height(4.dp))
    Text(
        "as opções de microfone valem na próxima vez que você entrar numa sala.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
}

// Lista de opção única (radio) — pra escolhas com rotulos longos (presets).
@Composable
private fun <T> RadioList(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        options.forEach { (label, value) ->
            val active = value == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val bg by animateColorAsState(
                when {
                    active -> Obsidian.active
                    hovered -> Obsidian.hover
                    else -> Obsidian.raised.copy(alpha = 0.5f)
                },
                tween(120),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.dp, if (active) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim, RoundedCornerShape(10.dp))
                    .hoverable(interaction)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LIcon(
                    if (active) Lucide.CircleDot else Lucide.Circle,
                    tint = if (active) Obsidian.accent else Obsidian.text3,
                    size = 15.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    style = TextStyle(color = if (active) Obsidian.text1 else Obsidian.text2, fontSize = 13.sp),
                )
            }
        }
    }
}

// Aba Desempenho: kill-switch gamer no topo, depois os controles finos (que ele
// sobrepoe — ficam esmaecidos com o modo ligado). Graficos + fps + transparencia.
@Composable
private fun PerformanceSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    ToggleRow(
        "Modo desempenho",
        "desliga aurora + estrelas e reduz animações de uma vez — para jogar ou transmitir",
        p.performanceMode, prefs::setPerformanceMode,
    )
    Spacer(Modifier.height(6.dp))

    // Controles finos: o modo desempenho já sobrepoe, entao esmaece quando ligado
    // (continuam clicaveis — são a tua preferencia fora do modo desempenho).
    Column(Modifier.alpha(if (p.performanceMode) 0.45f else 1f)) {
        // Escolher QUAL fundo mora em Aparencia; aqui fica so o ajuste de custo do
        // que ja foi escolhido. Um mesmo controle em duas abas envelhece mal: uma
        // hora as duas divergem e a pessoa nao sabe qual delas manda.
        LabeledControl("Qualidade da aurora", "mais detalhe = mais GPU; escolha o fundo em Aparencia") {
            SegmentedRow(
                listOf("Alta" to AuroraQuality.HIGH, "Media" to AuroraQuality.MEDIUM, "Baixa" to AuroraQuality.LOW),
                p.auroraQuality, prefs::setAuroraQuality,
            )
        }
        LabeledControl("FPS das animações", "teto de quadros do fundo (livre segue o monitor)") {
            SegmentedRow(
                listOf("Livre" to UiFps.FREE, "60" to UiFps.CAP60, "30" to UiFps.CAP30),
                p.uiFps, prefs::setUiFps,
            )
        }
        ToggleRow("Reduzir movimento", "congela a aurora e desliga cascatas e pulsos", p.reduceMotion, prefs::setReduceMotion)
    }

    Spacer(Modifier.height(6.dp))
    ToggleRow(
        "Janela translucida",
        "cantos arredondados + fundo vazando; opaca = mais nitido e leve",
        p.windowTransparent, prefs::setWindowTransparent,
    )
    Text(
        "a transparencia da janela so aplica ao reiniciar o app.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )

    Spacer(Modifier.height(6.dp))
    ToggleRow(
        "Fechar de vez ao fechar o app",
        "o X encerra o Astra em vez de minimizar para bandeja — sem nada em segundo plano",
        p.exitOnClose, prefs::setExitOnClose,
    )
}

// Rotulo + subtitulo + um controle embaixo (usado com o SegmentedRow).
@Composable
private fun LabeledControl(title: String, sub: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = TextStyle(color = Obsidian.text1, fontSize = 13.sp))
        Text(sub, style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
        Spacer(Modifier.height(8.dp))
        content()
    }
}

// Segmentado obsidiana: pilulas numa trilha; a ativa acende ambar.
@Composable
private fun <T> SegmentedRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.void.copy(alpha = 0.55f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (label, value) ->
            val active = value == selected
            val bg by animateColorAsState(if (active) Obsidian.accent else Color.Transparent, tween(140))
            val fg by animateColorAsState(if (active) Obsidian.textInv else Obsidian.text2, tween(140))
            val pillSrc = remember { MutableInteractionSource() }
            Text(
                label,
                style = TextStyle(
                    color = fg, fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                ),
                modifier = Modifier
                    .clickScale(pillSrc)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable(interactionSource = pillSrc, indication = null) { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
internal fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            // Preenche a coluna capada (~720, estilo Discord): interruptor grudado
            // na ponta direita. Quem limita a largura agora e a coluna, não a linha.
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(color = Obsidian.text1, fontSize = 13.sp))
            Text(sub, style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
        }
        Toggle(on, onChange)
    }
    Spacer(Modifier.height(8.dp))
}

// Interruptor obsidiana: trilho ambar quando ligado, botao desliza.
@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (on) Obsidian.accent else Obsidian.overlay, tween(160))
    val knobX by animateDpAsState(if (on) 18.dp else 2.dp, tween(160))
    Box(
        Modifier
            .width(38.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(track)
            .border(1.dp, if (on) Obsidian.accent else Obsidian.borderMid, RoundedCornerShape(11.dp))
            .clickable { onChange(!on) },
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (on) Obsidian.void else Obsidian.text3),
        )
    }
}

// Aba Aparencia: tema pronto (presets) + tamanho da fonte e densidade. O preset
// aplica AO VIVO no app inteiro (Obsidian reativo). Ajuste fino de accent/fundo
// avulso saiu (por ora so temas prontos); volta no futuro como tema editavel.
@Composable
private fun AppearanceSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    FieldLabel("tema")
    PresetGrid(p.accentId, p.bgId) { prefs.setTheme(it.accentId, it.bgId) }

    SettingsDivider()
    LabeledControl("Fundo", "liso e o padrao; a aurora e um shader animado e cobra GPU") {
        SegmentedRow(FundoPref.entries.map { it.label to it }, fundoAtual(p)) { aplicarFundo(prefs, it) }
    }

    SettingsDivider()
    LabeledControl("Tamanho da fonte", "das mensagens no chat") {
        SegmentedRow(FontSizePref.entries.map { it.label to it }, p.fontSize, prefs::setFontSize)
    }
    LabeledControl("Densidade das mensagens", "respiro entre as mensagens") {
        SegmentedRow(DensityPref.entries.map { it.label to it }, p.density, prefs::setDensity)
    }
    Spacer(Modifier.height(20.dp))
}

// Cascata de entrada pra conteudo ARBITRARIO.
//
// O CascadeIn que ja existe (Bits.kt) precisa de um indice, porque nasceu pra
// lista: quem chama esta dentro de um itemsIndexed e sabe quem e o item 3. Aqui
// nao ha lista — cada secao de configuracao emite os proprios filhos, e sao os
// filhos que devem entrar um a um. Este Layout resolve olhando os filhos DEPOIS
// de medidos: cada um ganha o degrau seguinte de atraso.
//
// Por que isso funciona: um @Composable que nao se embrulha em Column/Box emite
// os nos direto no pai. AccountSection, VoiceSection e as outras sao assim, entao
// o `measurables` daqui chega com os controles todos, separados.
//
// LARGURA > 0 e o filtro que pula os Spacer verticais — Spacer(Modifier.height(x))
// mede zero de largura. Sem ele, cada respiro entre controles gastaria um degrau e
// a cascata sairia com buracos no ritmo.
//
// O alpha e o deslocamento vao no placeWithLayer, ou seja, na fase de PLACEMENT:
// o relogio avancando re-executa o posicionamento, nunca a recomposicao. Numa tela
// com previa ao vivo, recompor 30 controles por frame seria bem caro.
private const val CASCATA_PASSO_MS = 40
private const val CASCATA_DURACAO_MS = 380
private const val CASCATA_DEGRAUS = 16
// Sobe mais do que antes (era 10dp). Curso curto demais com curva suave vira
// tremida: o olho ve o movimento comecar e acabar quase no mesmo lugar.
private val CASCATA_SUBIDA = 14.dp

@Composable
private fun CascataVertical(
    chave: Any?,
    animar: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val deveAnimar = animar && !LocalReduceMotion.current
    val totalMs = CASCATA_DURACAO_MS + CASCATA_PASSO_MS * CASCATA_DEGRAUS
    val relogio = remember(chave) { Animatable(if (deveAnimar) 0f else 1f) }
    LaunchedEffect(chave) {
        if (deveAnimar) relogio.animateTo(1f, tween(totalMs, easing = LinearEasing))
    }
    val deslocamento = with(LocalDensity.current) { CASCATA_SUBIDA.toPx() }
    Layout(content = content, modifier = modifier) { medidos, constraints ->
        // minWidth = 0 TAMBEM, nao so minHeight. Este Layout recebe
        // fillMaxWidth(), ou seja, constraints com minWidth == maxWidth: repassar
        // isso pros filhos OBRIGA cada um a ocupar a largura inteira. Era por isto
        // que "derrubar todas as outras", "salvar" e "procurar atualizações"
        // apareciam esticados de ponta a ponta — um botao de texto curto medindo
        // 700dp. Botao deve ter a largura do que ele diz.
        val filhos = medidos.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val largura = if (constraints.hasBoundedWidth) constraints.maxWidth
        else filhos.maxOfOrNull { it.width } ?: 0
        layout(largura, filhos.sumOf { it.height }) {
            val agora = relogio.value * totalMs
            var y = 0
            var degrau = 0
            filhos.forEach { filho ->
                val conta = filho.width > 0 && filho.height > 0
                val meu = if (conta) degrau++ else degrau
                val bruto =
                    ((agora - meu.coerceAtMost(CASCATA_DEGRAUS) * CASCATA_PASSO_MS) / CASCATA_DURACAO_MS)
                        .coerceIn(0f, 1f)
                // O relogio mestre e LINEAR de proposito (ele so distribui o tempo);
                // a curva vive aqui, em cada filho. Sem ela cada controle subia com
                // velocidade constante e parava seco no fim — e isso que se sente
                // como cascata "dura". EaseOutSoft chega desacelerando.
                val progresso = EaseOutSoft.transform(bruto)
                filho.placeWithLayer(0, y) {
                    alpha = progresso
                    translationY = (1f - progresso) * deslocamento
                }
                y += filho.height
            }
        }
    }
}

// Escolha de FUNDO. Não e uma preferencia nova: e a leitura conjunta de
// auroraEnabled + starsEnabled como uma escada de custo. "Aurora sem estrelas"
// era uma combinacao possivel que ninguem pedia, e cada combinacao a mais e uma
// pergunta a mais pra quem so quer decidir como o app parece.
// AURORA E ESTRELAS SAO INDEPENDENTES no `DesktopPrefs` — sempre foram. Quem
// amarrava as duas era esta escada: "Aurora" acendia as estrelas junto, e nao
// havia como pedir aurora SEM elas. O dono quis as duas soltas, entao a escada
// ganhou o quarto degrau em vez de virar dois interruptores: quatro opcoes
// nomeadas dizem o custo de cada escolha, dois interruptores fariam a pessoa
// descobrir a combinacao cara sozinha.
private enum class FundoPref(val label: String) {
    LISO("Liso"),
    ESTRELAS("Estrelas"),
    AURORA("Aurora"),
    AMBOS("Aurora e estrelas"),
}

private fun fundoAtual(p: DesktopPrefs.Prefs): FundoPref = when {
    p.auroraEnabled && p.starsEnabled -> FundoPref.AMBOS
    p.auroraEnabled -> FundoPref.AURORA
    p.starsEnabled -> FundoPref.ESTRELAS
    else -> FundoPref.LISO
}

private fun aplicarFundo(prefs: DesktopPrefs, f: FundoPref) {
    prefs.setAuroraEnabled(f == FundoPref.AURORA || f == FundoPref.AMBOS)
    prefs.setStarsEnabled(f == FundoPref.ESTRELAS || f == FundoPref.AMBOS)
}

// Quebra entre grupos de configuração.
//
// Traço CURTO e centralizado, e não mais de borda a borda. Linha inteira lê como
// linha de tabela: o olho passa a ver uma grade e conta as células em vez de ler
// o conteúdo. Curta, ela lê como quebra de capítulo — que é o que ela é.
//
// O respiro também cresceu (12/20 no lugar de 8/16): com o traço menor, quem
// separa de verdade passa a ser o espaço, e espaço curto demais faz o traço
// parecer enfeite solto no meio do nada.
@Composable
internal fun SettingsDivider() {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(28.dp).height(1.dp).background(Obsidian.borderDim.copy(alpha = 0.6f)))
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun PresetGrid(selAccent: String, selBg: String, onPick: (ThemePreset) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemePresets.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { preset ->
                    PresetCard(
                        preset,
                        active = selAccent == preset.accentId && selBg == preset.bgId,
                        onClick = { onPick(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PresetCard(preset: ThemePreset, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val bg = bgOption(preset.bgId)
    val accent = accentOption(preset.accentId).value
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Obsidian.accentDim else Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, if (active) Obsidian.accent else Obsidian.borderDim, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 38.dp, height = 26.dp).clip(RoundedCornerShape(6.dp))
                .background(bg.voidC).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
        ) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(9.dp)
                    .clip(CircleShape).background(accent),
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                preset.label,
                style = TextStyle(color = if (active) Obsidian.accent else Obsidian.text1, fontSize = 12.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                preset.hint,
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

