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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.composables.icons.lucide.Bot
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
import com.composables.icons.lucide.Accessibility
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.Keyboard
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.SmilePlus
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.X
import app.astra.desktop.Placas
import app.astra.desktop.profile.AvatarPicker
import app.astra.desktop.voice.AparelhoDeAudio
import app.astra.desktop.voice.FonteDeAparelhos
import app.astra.desktop.prefs.AuroraQuality
import app.astra.desktop.prefs.DensityPref
import app.astra.desktop.AtalhosGlobais
import app.astra.desktop.AtividadeDoSistema
import app.astra.desktop.Canal
import app.astra.desktop.InicioComWindows
import app.astra.desktop.ModoTransmissao
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.FontSizePref
import app.astra.desktop.prefs.ScreenQuality
import app.astra.desktop.prefs.UiFps
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.ThemePreset
import app.astra.desktop.ui.theme.FamiliaDeTema
import app.astra.desktop.ui.theme.ThemePresets
import app.astra.desktop.ui.theme.accentOption
import app.astra.desktop.ui.theme.bgOption
import app.astra.desktop.update.UpdateService
import app.astra.desktop.update.UpdateState
import app.astra.desktop.auth.SessionStore
import app.astra.mobile.core.network.SessionApi
import app.astra.mobile.core.network.BotPersonaApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.MutualServerDto
import app.astra.mobile.core.network.dto.RevokeOthersRequest
import app.astra.mobile.core.network.dto.SessionDto
import app.astra.mobile.core.network.dto.ChangePasswordRequest
import app.astra.mobile.core.network.dto.CustomStatusRequest
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.dto.ApagarContaRequest
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.RecusaDeApagar
import kotlinx.serialization.json.Json
import app.astra.mobile.core.network.dto.SetPasswordRequest
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
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
import kotlin.math.roundToInt
import kotlin.math.sin
import app.astra.desktop.ui.theme.Tipo

enum class SettingsTab(val label: String, val sub: String, val icon: ImageVector) {
    ACCOUNT("Conta", "email e senha", Lucide.User),
    PROFILE("Perfil", "avatar, nome e recado", Lucide.Pencil),
    SESSIONS("Sessões", "onde sua conta está logada", Lucide.LogOut),
    NOTIFICATIONS("Notificacoes", "avisos na bandeja", Lucide.Bell),
    PRIVACY("Privacidade", "o que os outros veem de você", Lucide.Eye),
    APPEARANCE("Aparencia", "cores e fundo", Lucide.Palette),
    PETS("Pets", "companheiro, cor e gestos", Lucide.PawPrint),
    ACCESSIBILITY("Acessibilidade", "leitura, contraste e movimento", Lucide.Accessibility),
    PERFORMANCE("Desempenho", "graficos, animações, fps", Lucide.ChartColumn),
    VOICE("Voz", "microfone e transmissão", Lucide.Volume2),
    SHORTCUTS("Atalhos", "teclas do app", Lucide.Keyboard),
    PERMISSIONS("Permissoes", "o que o Windows libera", Lucide.ShieldCheck),
    ABOUT("Sobre", "versão e atualizacoes", Lucide.Info),
    BOTS("Bots", "aparencia da Sparkle e da Sparxie", Lucide.Bot),
    DIAGNOSTICS("Diagnostico", "o que o app esta vendo agora", Lucide.CircleDot),
}

private val abaDeDev: Boolean =
    System.getProperty("jpackage.app-path") == null || System.getProperty("astra.dev") != null

private val abasVisiveis: List<SettingsTab> =
    SettingsTab.entries.filter {
        (it != SettingsTab.DIAGNOSTICS || abaDeDev) && it != SettingsTab.BOTS
    }

private val LARGURA_DA_PREVIA = 470.dp
internal val FORMA_DO_CARTAO_DE_CONFIG = RoundedCornerShape(16.dp)

@Composable
fun SettingsScreen(
    me: ProfileUserDto?,
    prefs: DesktopPrefs,
    aparelhos: FonteDeAparelhos,
    onClose: () -> Unit,
    onProfileSaved: () -> Unit = {},
    initialTab: SettingsTab = SettingsTab.ACCOUNT,
    onTestarNotificacao: () -> Unit = {},
    aoSairDaConta: () -> Unit = {},
) {
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    val tabAtiva = tab
    val jaAnimaram = remember { mutableSetOf<SettingsTab>() }
    val prefState by prefs.state.collectAsState()
    var draft by remember(me) { mutableStateOf(ProfileDraft.from(me)) }

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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                Modifier.width(220.dp).fillMaxHeight().padding(horizontal = 12.dp, vertical = 18.dp),
            ) {
                Text(
                    "configurações",
                    style = TextStyle(color = Obsidian.text1, fontSize = 18.sp, fontFamily = DmSerif),
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                )
                var ehDono by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    ehDono = runCatching {
                        GlobalContext.get().get<BotPersonaApi>().personas().data != null
                    }.getOrDefault(false)
                }
                val abas = remember(ehDono) {
                    if (ehDono) abasVisiveis + SettingsTab.BOTS else abasVisiveis
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    abas.forEach { t ->
                        NavRow(t.icon, t.label, t.sub, active = t == tabAtiva) { tab = t }
                    }
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val larguraPrevia = LARGURA_DA_PREVIA
            val pinned = maxWidth > larguraPrevia + 280.dp
            val acoesDoCartao = remember { AcoesDoCartao() }
            val contentMax =
                if (pinned) minOf(720.dp, (maxWidth - larguraPrevia - 76.dp).coerceAtLeast(280.dp)) else 720.dp
            Column(
                Modifier.align(Alignment.TopStart).widthIn(max = contentMax).fillMaxWidth()
                    .fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        fadeIn(tween(140)).togetherWith(fadeOut(tween(100))) using
                            SizeTransform(clip = false) { _, _ -> tween(180) }
                    },
                    label = "settingsSection",
                ) { current ->
                    val temPrevia = temPrevia(current)
                    val jaVisto = current in jaAnimaram
                    LaunchedEffect(current) { jaAnimaram += current }
                    Column(Modifier.fillMaxWidth()) {
                    Text(
                        current.label,
                        style = TextStyle(color = Obsidian.text1, fontSize = 26.sp, fontFamily = DmSerif),
                    )
                    Spacer(Modifier.height(18.dp))
                    if (!pinned && temPrevia) {
                        SettingsPreview(current, me, prefState, draft, Modifier.widthIn(max = larguraPrevia).fillMaxWidth(), acoesDoCartao)
                        Spacer(Modifier.height(18.dp))
                    }
                    CascataVertical(chave = current, animar = !jaVisto, modifier = Modifier.fillMaxWidth()) {
                    when (current) {
                        SettingsTab.ACCOUNT -> AccountSection(me, aoSairDaConta)
                        SettingsTab.PROFILE -> ProfileSection(me, draft, { draft = it }, onProfileSaved, acoesDoCartao)
                        SettingsTab.SESSIONS -> SessionsSection()
                        SettingsTab.NOTIFICATIONS -> Column {
                            BlocoDeAjustes(
                                "neste computador",
                                "mandam no balão da bandeja desta máquina. Não mudam o que o " +
                                    "servidor envia, então o sino continua contando.",
                            ) {
                                ToggleRow(
                                    "Sussurros (DMs)", "avisa quando chega mensagem privada",
                                    prefState.notifyDms, prefs::setNotifyDms,
                                )
                                ToggleRow(
                                    "Atividade de canal", "avisa nova mensagem nas constelações",
                                    prefState.notifyChannels, prefs::setNotifyChannels,
                                )
                                ToggleRow(
                                    "Som do aviso",
                                    "duas notas curtas quando chega algo dirigido a você",
                                    prefState.somDeAviso, prefs::setSomDeAviso,
                                )
                                ToggleRow(
                                    "Aviso sem conteúdo",
                                    "some quem escreveu e o que escreveu — fica só “sussurro novo”",
                                    prefState.avisoDiscreto, prefs::setAvisoDiscreto,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "os avisos aparecem na bandeja so com a janela fechada ou minimizada.",
                                    style = Tipo.apoio,
                                    modifier = Modifier.widthIn(max = 460.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "o aviso sem conteúdo serve para transmitir a tela: o balão do Windows " +
                                        "aparece por cima de tudo, e o que estiver escrito nele entra na gravação.",
                                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
                                    modifier = Modifier.widthIn(max = 460.dp),
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            AvisosDaContaBloco()
                        }
                        SettingsTab.PRIVACY -> PrivacySection(prefState, prefs, me, onProfileSaved)
                        SettingsTab.APPEARANCE -> AppearanceSection(prefState, prefs)
                        SettingsTab.PETS -> PetsSection(prefState, prefs)

                        SettingsTab.ACCESSIBILITY -> AccessibilitySection(prefState, prefs)
                        SettingsTab.PERFORMANCE -> PerformanceSection(prefState, prefs)
                        SettingsTab.VOICE -> VoiceSection(prefState, prefs, aparelhos)
                        SettingsTab.SHORTCUTS -> AtalhosSection(prefState, prefs)
                        SettingsTab.PERMISSIONS -> PermissionsSection(onTestarNotificacao)
                        SettingsTab.ABOUT -> AboutSection()
                        SettingsTab.DIAGNOSTICS -> DiagnosticsSection()
                        SettingsTab.BOTS -> BotsSection()
                    }
                    }
                    if (!pinned && current == SettingsTab.PROFILE) {
                        Spacer(Modifier.height(14.dp))
                        ProfileSaveButton(me, draft, { draft = it }, onProfileSaved, Modifier.widthIn(max = larguraPrevia).fillMaxWidth())
                    }
                    }
                }
            }
                if (pinned) {
                    Column(
                        Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 32.dp).width(larguraPrevia),
                        horizontalAlignment = Alignment.End,
                    ) {
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
                                if (temPrevia(secao)) {
                                    SettingsPreview(secao, me, prefState, draft, Modifier.fillMaxWidth(), acoesDoCartao)
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

private fun temPrevia(tab: SettingsTab): Boolean = when (tab) {
    SettingsTab.SESSIONS, SettingsTab.PERMISSIONS,
    SettingsTab.ABOUT, SettingsTab.DIAGNOSTICS, SettingsTab.BOTS,
    SettingsTab.SHORTCUTS,
    SettingsTab.PETS,
    SettingsTab.ACCOUNT -> false
    else -> true
}

@Composable
private fun SettingsPreview(
    tab: SettingsTab,
    me: ProfileUserDto?,
    p: DesktopPrefs.Prefs,
    draft: ProfileDraft,
    modifier: Modifier = Modifier,
    acoesDoCartao: AcoesDoCartao? = null,
) {
    Column(modifier) {
        FieldLabel("previa")
        Box {
            when (tab) {
                SettingsTab.PROFILE -> ProfileCardPreview(me, draft, acoesDoCartao)
                SettingsTab.NOTIFICATIONS -> NotifPreviewCard(p.reduceMotionEff, p.avisoDiscreto)
                SettingsTab.PRIVACY -> AtividadePreview(p.atividadeVisivel)
                SettingsTab.APPEARANCE, SettingsTab.ACCESSIBILITY -> UiSamplePreview(p.fontSize, p.density)
                SettingsTab.PERFORMANCE -> CostMeter(p)
                SettingsTab.VOICE -> VoicePreview(p)
                SettingsTab.SESSIONS, SettingsTab.ABOUT, SettingsTab.DIAGNOSTICS,
                SettingsTab.PERMISSIONS, SettingsTab.BOTS, SettingsTab.SHORTCUTS,
                SettingsTab.PETS, SettingsTab.ACCOUNT -> Unit
            }
            if (tab != SettingsTab.PROFILE) {
                Box(Modifier.matchParentSize().engoleOPonteiro())
            }
        }
    }
}

private fun Modifier.engoleOPonteiro(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

private data class ProfileDraft(
    val displayName: String = "",
    val pronouns: String = "",
    val bio: String = "",
    val statusEmoji: String = "",
    val customStatus: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val bannerPositionY: Int = 50,
    val bannerScale: Int = 100,
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

@Composable
private fun ProfileCardPreview(
    me: ProfileUserDto?,
    draft: ProfileDraft?,
    acoes: AcoesDoCartao? = null,
) {
    if (me == null) {
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Text("carregando…", style = Tipo.descricao)
        }
        return
    }
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
    var mutuais by remember(me.id) { mutableStateOf<List<MutualServerDto>>(emptyList()) }
    LaunchedEffect(me.id) {
        mutuais = withContext(Dispatchers.IO) {
            runCatching { GlobalContext.get().get<UserApi>().profile(me.id).data?.mutualServers }
                .getOrNull().orEmpty()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        CartaoDaPrevia(
            rotulo = "cartão completo",
            larguraReal = LARGURA_CARTAO_COMPLETO,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ProfileCard(
                dados,
                CardVariante.COMPLETO,
                Modifier.fillMaxWidth(),
                servidoresEmComum = mutuais,
                animar = false,
                acoesDaFoto = acoes?.let { { it.foto() } },
                acoesDoBanner = acoes?.let { { it.banner() } },
            )
        }
    }
}

@Composable
private fun CartaoDaPrevia(
    rotulo: String,
    larguraReal: Dp,
    modifier: Modifier = Modifier,
    conteudo: @Composable () -> Unit,
) {
    Column(modifier) {
        RotuloDaPrevia(rotulo)
        BoxWithConstraints {
            val larguraRealPx = with(LocalDensity.current) { larguraReal.roundToPx() }
            val escala = (constraints.maxWidth.toFloat() / larguraRealPx).coerceAtMost(1f)
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val p = measurable.measure(Constraints.fixedWidth(larguraRealPx))
                        layout((p.width * escala).toInt(), (p.height * escala).toInt()) { p.place(0, 0) }
                    }
                    .graphicsLayer {
                        scaleX = escala
                        scaleY = escala
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                conteudo()
            }
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

private const val PASSEIO_DO_AVISO = 14f

@Composable
private fun PrivacySection(
    prefState: DesktopPrefs.Prefs,
    prefs: DesktopPrefs,
    me: ProfileUserDto?,
    onSalvou: () -> Unit,
) {
    Column {
        Text("Quem pode te mandar sussurro", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
        Spacer(Modifier.height(4.dp))
        Text(
            "vale para conversa NOVA. quem já está falando com você continua falando — " +
                "apertar isto não cala ninguém que você já estava respondendo.",
            style = Tipo.apoio,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(10.dp))
        FiltroDeSussurro(me, onSalvou)
        Spacer(Modifier.height(8.dp))
        Text(
            "quem for barrado recebe a mesma recusa de quem foi bloqueado. não dá para " +
                "descobrir, do outro lado, qual é o seu ajuste.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )

        SettingsDivider()
        ModoTransmissaoBloco(prefState, prefs)

        SettingsDivider()
        Text("O que os outros veem", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            "Mostrar o que estou usando",
            "quem te vê passa a ver o nome do programa em primeiro plano",
            prefState.atividadeVisivel,
            prefs::setAtividadeVisivel,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "sai apenas o nome do programa, o mesmo que o Windows mostra no Gerenciador de Tarefas.",
            style = Tipo.rotulo,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "o título da janela nunca é lido. Ele entregaria arquivo aberto, aba, endereço e busca — " +
                "por isso navegador aparece só como “Navegando”, sem dizer qual nem o quê.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "nada disso é guardado: some sozinho um minuto depois de você fechar o Astra, " +
                "e desligar aqui apaga na hora.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "desligado, o Astra nem chega a olhar qual programa está na frente.",
            style = Tipo.apoio,
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
}

@Composable
private fun ModoTransmissaoBloco(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    val detectado by ModoTransmissao.detectado.collectAsState()
    val ativo by ModoTransmissao.ativo.collectAsState()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Modo transmissão", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
        if (ativo) {
            Spacer(Modifier.width(8.dp))
            Text(
                if (detectado && !p.modoTransmissao) "valendo — programa de transmissão aberto" else "valendo",
                style = TextStyle(color = Obsidian.accent, fontSize = 11.sp),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "com ele valendo: o aviso da bandeja perde nome e texto, o som de aviso não toca, " +
            "e o seu e-mail vira máscara na aba Conta.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(10.dp))
    ToggleRow(
        "Ligar agora", "vale enquanto estiver marcado",
        p.modoTransmissao, prefs::setModoTransmissao,
    )
    ToggleRow(
        "Ligar sozinho quando eu abrir o OBS",
        "também vale para Streamlabs, XSplit e Twitch Studio",
        p.modoTransmissaoAuto, prefs::setModoTransmissaoAuto,
    )
    if (p.modoTransmissaoAuto) {
        Spacer(Modifier.height(10.dp))
        InfoNote(
            "O que o Astra olha para detectar",
            "Para saber que o OBS está aberto é preciso olhar a lista de programas em " +
                "execução — uma leitura mais ampla que a do “o que estou usando”, que só " +
                "olha a janela da frente. Por isso isto é uma escolha sua, e não o padrão.\n\n" +
                "O que se faz com essa lista: comparar o nome do executável com quatro " +
                "nomes conhecidos de programas de transmissão. Nada além disso é lido — " +
                "título de janela continua sendo o que o Astra nunca olha.\n\n" +
                "O resultado é um sim ou não que fica nesta máquina. Nem o servidor vê.",
        )
    }
}

@Composable
private fun FiltroDeSussurro(me: ProfileUserDto?, onSalvou: () -> Unit) {
    val koin = GlobalContext.get()
    val escopo = rememberCoroutineScope()
    val doServidor = me?.dmPrivacy ?: "all"
    var escolhido by remember(doServidor) { mutableStateOf(doServidor) }
    var erro by remember { mutableStateOf<String?>(null) }

    RadioList(
        listOf(
            "qualquer pessoa" to "all",
            "quem divide constelação comigo" to "shared",
            "só meus amigos" to "friends",
        ),
        escolhido,
    ) { novo ->
        if (novo == escolhido) return@RadioList
        val anterior = escolhido
        escolhido = novo
        erro = null
        escopo.launch {
            val r = runCatching {
                koin.get<UserApi>().updateProfile(UpdateProfileRequest(dmPrivacy = novo))
            }
            if (r.isSuccess) {
                onSalvou()
            } else {
                escolhido = anterior
                erro = "não deu para salvar. verifique a conexão e tente de novo."
            }
        }
    }
    erro?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = TextStyle(color = Obsidian.danger, fontSize = 11.sp))
    }
}

@Composable
private fun AtividadePreview(ligado: Boolean) {
    val agora = if (ligado) {
        remember { AtividadeDoSistema.emPrimeiroPlano() } ?: "Navegando"
    } else null
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Obsidian.base))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("você", style = Tipo.corpo)
                if (agora != null) {
                    Text(agora, style = Tipo.apoio)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (ligado) "é isto que aparece pra quem te vê." else "ninguém vê nada além do seu nome.",
            style = Tipo.apoio,
        )
    }
}

@Composable
private fun NotifPreviewCard(reduceMotion: Boolean, discreto: Boolean) {
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
            cycle < 0.12f -> {
                val k = EaseOutStd.transform(cycle / 0.12f)
                dx = (1f - k) * PASSEIO_DO_AVISO
                a = k
            }
            cycle < 0.84f -> { dx = 0f; a = 1f }
            else -> {
                val k = EaseOutSoft.transform((cycle - 0.84f) / 0.16f)
                dx = k * PASSEIO_DO_AVISO
                a = 1f - k
            }
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().alpha(0.4f)) {
            UiSamplePreview(FontSizePref.MD, DensityPref.COMFORTABLE)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .fillMaxWidth(0.92f)
                .offset(x = dx.dp)
                .alpha(a),
        ) {
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
                Text("Astra", style = Tipo.nota)
                if (discreto) {
                    Text("sussurro novo", style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmSerif))
                    Text("sem nome e sem texto — é tudo que aparece.", style = Tipo.apoio)
                } else {
                    Text("ana", style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmSerif))
                    Text("e ai, bora marcar a call?", style = TextStyle(color = Obsidian.text2, fontSize = 11.sp))
                }
            }
        }
        }
    }
}

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
        Text(costVerdict(gpu, cpu), style = Tipo.apoio)
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
                Text("sua tela", style = Tipo.descricao)
            }
            Spacer(Modifier.height(9.dp))
            Text(
                "${q.height}p · ${q.fps}fps · ${q.bitrate / 1_000_000} Mbps",
                style = Tipo.rotulo,
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
            MicMeter(testing, p.micSensitivity)
            Spacer(Modifier.height(10.dp))
            AboutButton(if (testing) "parar teste" else "testar microfone", accent = !testing) {
                testing = !testing
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (p.micNoiseSuppression) "supressao de ruido: ligada" else "supressao de ruido: desligada",
                style = Tipo.nota,
            )
        }
    }
}

@Composable
private fun MicMeter(active: Boolean, threshold: Float = 0f) {
    var level by remember { mutableFloatStateOf(0f) }
    var available by remember { mutableStateOf(true) }
    DisposableEffect(active) {
        if (!active) {
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
        Text("microfone indisponivel", style = Tipo.apoio)
        return
    }
    val lvl by animateFloatAsState(level, tween(90), label = "micLvl")
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
                val shape = 0.45f + 0.55f * sin((i + 0.5f) / bars * PI).toFloat()
                val h = (lvl * shape).coerceIn(0.05f, 1f)
                Box(
                    Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(2.dp))
                        .background(meterColor.copy(alpha = 0.4f + 0.5f * h)),
                )
            }
        }
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

@Composable
private fun MicSensitivityRow(value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sensibilidade de entrada", style = Tipo.corpo)
            Text(
                if (value <= 0f) "sempre transmite" else "${(value * 100).toInt()}%",
                style = Tipo.apoio,
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
            style = Tipo.apoio,
        )
    }
}

@Composable
private fun ProfileSection(
    me: ProfileUserDto?,
    draft: ProfileDraft,
    onChange: (ProfileDraft) -> Unit,
    onSaved: () -> Unit,
    acoesDoCartao: AcoesDoCartao,
) {
    val scope = rememberCoroutineScope()
    var busyAvatar by remember { mutableStateOf(false) }
    var busyBanner by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var cropAvatar by remember { mutableStateOf<CropSource?>(null) }
    var cropBanner by remember { mutableStateOf<CropSource?>(null) }

    fun escolherAvatar() {
        val file = AvatarPicker.choose() ?: return
        busyAvatar = true
        msg = null
        scope.launch {
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

    if (busyAvatar || busyBanner) {
        Text(
            "lendo a imagem…",
            style = Tipo.rotulo,
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
    SideEffect {
        acoesDoCartao.foto = {
            val atual = draft.avatarUrl
            buildList {
                add(MenuEntry.Item("trocar imagem", icon = Lucide.Upload) { escolherAvatar() })
                if (atual != null && !ImageCrop.isAnimated(atual)) {
                    add(MenuEntry.Item("reenquadrar", icon = Lucide.Crop) {
                        cropAvatar = CropSource.Remote(atual)
                    })
                }
                if (atual != null) {
                    add(MenuEntry.Separator)
                    add(MenuEntry.Item("remover", danger = true, icon = Lucide.Trash2) {
                        onChange(draft.copy(avatarUrl = null))
                    })
                }
            }
        }
    }

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

    var resizeOpen by remember { mutableStateOf(false) }
    fun escolherBanner() {
        val file = AvatarPicker.choose("Escolher banner") ?: return
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
    SideEffect {
        acoesDoCartao.banner = {
            val atual = draft.bannerUrl
            buildList {
                add(MenuEntry.Item("trocar imagem", icon = Lucide.Upload) { escolherBanner() })
                if (!atual.isNullOrBlank()) {
                    if (ImageCrop.isAnimated(atual)) {
                        add(MenuEntry.Item("reposicionar", icon = Lucide.Move) { resizeOpen = true })
                    } else {
                        add(MenuEntry.Item("reenquadrar", icon = Lucide.Crop) {
                            cropBanner = CropSource.Remote(atual)
                        })
                    }
                    add(MenuEntry.Separator)
                    add(MenuEntry.Item("remover", danger = true, icon = Lucide.Trash2) {
                        onChange(draft.copy(bannerUrl = null))
                    })
                }
            }
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
            onApply = { onChange(draft.copy(bannerUrl = it, bannerPositionY = 50, bannerScale = 100)) },
            onClose = { cropBanner = null },
        )
    }
    Spacer(Modifier.height(14.dp))
    FieldLabel("cor do perfil")
    ColorPickerButton(draft.bannerColor) {
        onChange(draft.copy(bannerColor = it, profileTheme = it))
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "a cor atravessa o cartao inteiro. com imagem de banner, ela aparece do banner para baixo.",
        style = Tipo.apoio,
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
                textStyle = Tipo.corpo,
                cursorBrush = SolidColor(Obsidian.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    msg?.let { (text, ok) ->
        Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
    }
}

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
                                displayName = draft.displayName.trim().ifBlank { null },
                                pronouns = draft.pronouns.trim(),
                                bio = draft.bio.trim(),
                                avatarUrl = draft.avatarUrl,
                                statusEmoji = draft.statusEmoji,
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
                    else msg = saveErrorMessage(r.exceptionOrNull()) to false
                }
            }
            if (dirty && !saving) {
                AboutButton("descartar", accent = false) { onChange(original); msg = null }
            }
        }
        if (!dirty && msg == null) {
            Spacer(Modifier.height(6.dp))
            Text("nada mudou ainda.", style = Tipo.apoio)
        }
    }
}

private fun saveErrorMessage(t: Throwable?): String {
    val http = t as? HttpException ?: return "sem conexão com o servidor"
    if (http.code() == 413) return "a imagem ficou grande demais — escolha uma menor ou dê menos zoom"
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull()
    val parsed = body?.let {
        runCatching { Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }.getOrNull()
    }
    return parsed?.takeIf { it.isNotBlank() } ?: "não foi possível salvar (erro ${http.code()})"
}

private const val ZOOM_MIN = 50
private const val ZOOM_MAX = 300

@Composable
private fun LinhaDeVolume(rotulo: String, valor: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rotulo,
            style = Tipo.rotulo,
            modifier = Modifier.width(150.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(22.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onChange(((change.position.x / size.width).coerceIn(0f, 1f) * 100).roundToInt())
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val fracao = (valor / 100f).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Obsidian.void.copy(alpha = 0.6f)),
            )
            Box(
                Modifier.fillMaxWidth(fracao).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Obsidian.accent),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (fracao > 0f) Spacer(Modifier.weight(fracao))
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(Obsidian.accent).border(2.dp, Obsidian.raised, CircleShape),
                )
                if (fracao < 1f) Spacer(Modifier.weight(1f - fracao))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "$valor%",
            style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
            modifier = Modifier.width(34.dp),
        )
    }
}

@Composable
private fun ZoomTrack(scale: Int, onChange: (Int) -> Unit) {
    val faixa = (ZOOM_MAX - ZOOM_MIN).toFloat()
    val pct = ((scale - ZOOM_MIN) / faixa).coerceIn(0f, 1f)
    Row(
        Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("zoom", style = Tipo.apoio, modifier = Modifier.width(42.dp))
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
    DialogShell(onClose = onClose, largura = 360.dp) {
        Column {
                Text(
                    "redimensionar banner",
                    style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "é isto que os outros veem. arraste na imagem para enquadrar.",
                    style = Tipo.apoio,
                )
                Spacer(Modifier.height(16.dp))
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
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
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

@Composable
private fun HexField(selected: String?, onPick: (String) -> Unit) {
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

@Composable
private fun FontPicker(selected: String?, onPick: (String) -> Unit) {
    val current = selected ?: "serif"
    val cur = PROFILE_FONTS.find { it.id == current } ?: PROFILE_FONTS.first()
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
private fun AccountSection(me: ProfileUserDto?, aoSairDaConta: () -> Unit) {
    val emTransmissao by ModoTransmissao.ativo.collectAsState()
    val semSenha = me?.hasPassword == false
    var trocandoSenha by remember { mutableStateOf(false) }
    var conferindoEmail by remember { mutableStateOf(false) }
    var conferidoAgora by remember { mutableStateOf(false) }
    var trocandoEmail by remember { mutableStateOf(false) }
    var trocandoUsuario by remember { mutableStateOf(false) }
    var usuarioAgora by remember { mutableStateOf<String?>(null) }
    var emailAgora by remember { mutableStateOf<String?>(null) }

    var sessoes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(me?.id) {
        sessoes = runCatching { GlobalContext.get().get<SessionApi>().list().data?.sessions?.size }
            .getOrNull()
    }

    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        val usuario = usuarioAgora ?: me?.username
        val email = emailAgora ?: me?.email
        LinhaDaConta(
            rotulo = "Nome de usuário",
            valor = usuario?.let { "@$it" } ?: "—",
            acao = if (usuario == null) null else "Editar",
            aoAgir = { trocandoUsuario = true },
        )
        LinhaDaConta(
            rotulo = "E-mail",
            valor = email?.let { if (emTransmissao) mascarar(it) else it } ?: "—",
            acao = if (email == null || semSenha) null else "Editar",
            aoAgir = { trocandoEmail = true },
        )
        LinhaDaConta(
            rotulo = "Senha",
            valor = if (semSenha) "não definida" else "••••••••",
            acao = if (semSenha) "Definir" else "Editar",
            aoAgir = { trocandoSenha = true },
        )
        val conferido = me?.emailVerifiedAt != null || conferidoAgora
        LinhaDaConta(
            rotulo = "Verificação",
            valor = if (conferido) "e-mail conferido" else "e-mail não conferido",
            acao = if (conferido) null else "Conferir",
            aoAgir = { conferindoEmail = true },
        )
        LinhaDaConta("Sessões", sessoes?.let { if (it == 1) "1 aberta" else "$it abertas" } ?: "…")
        LinhaDaConta("Membro desde", mesEAno(me?.createdAt))
    }

    if (semSenha) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Conta Google sem senha. Defina uma para entrar também por e-mail.",
            style = Tipo.apoio,
        )
    }

    if (trocandoSenha) {
        DialogoDeSenha(hasPassword = !semSenha, onClose = { trocandoSenha = false })
    }

    if (conferindoEmail) {
        VerificarEmailDialog(
            email = emailAgora ?: me?.email,
            onClose = { conferindoEmail = false },
            aoConferir = { conferidoAgora = true },
        )
    }

    if (trocandoEmail) {
        TrocarEmailDialog(
            emailAtual = emailAgora ?: me?.email,
            onClose = { trocandoEmail = false },
            aoTrocar = { emailAgora = it; conferidoAgora = true },
        )
    }

    if (trocandoUsuario) {
        TrocarUsuarioDialog(
            atual = usuarioAgora ?: me?.username,
            onClose = { trocandoUsuario = false },
            aoTrocar = { usuarioAgora = it },
        )
    }

    SettingsDivider()
    ApagarConta(me, aoSairDaConta)
}

@Composable
private fun LinhaDaConta(
    rotulo: String,
    valor: String,
    acao: String? = null,
    aoAgir: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rotulo,
            style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
            modifier = Modifier.weight(1f),
        )
        Text(
            valor,
            style = Tipo.corpo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 240.dp),
        )
        if (acao != null) {
            Spacer(Modifier.width(12.dp))
            val toque = remember { MutableInteractionSource() }
            val sobre by toque.collectIsHoveredAsState()
            Box(
                Modifier
                    .clickScale(toque)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sobre) Obsidian.hover else Obsidian.overlay)
                    .hoverable(toque)
                    .clickable(interactionSource = toque, indication = null, onClick = aoAgir)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(acao, style = TextStyle(color = Obsidian.text1, fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun ApagarConta(me: ProfileUserDto?, aoSairDaConta: () -> Unit) {
    val koin = GlobalContext.get()
    val escopo = rememberCoroutineScope()
    var aberto by remember { mutableStateOf(false) }
    var confirmacao by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var indo by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var presas by remember { mutableStateOf<List<String>>(emptyList()) }
    val temSenha = me?.hasPassword != false
    val arroba = me?.username.orEmpty()

    Text("Apagar conta", style = TextStyle(color = Obsidian.danger, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "acaba na hora e não tem volta.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(12.dp))

    if (!aberto) {
        BotaoDePerigo("apagar minha conta", Lucide.Trash2) {
            aberto = true; confirmacao = ""; senha = ""; erro = null; presas = emptyList()
        }
        return
    }
    Text(
        "o que você escreveu fica, assinado “conta apagada” — a conversa é de duas " +
            "pessoas, e sua saída não deveria abrir buracos no que a outra leu.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(12.dp))

    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
        ProfileField("digite @$arroba para confirmar", confirmacao, "@$arroba", max = 40) { confirmacao = it }
        if (temSenha) {
            Spacer(Modifier.height(10.dp))
            FieldLabel("sua senha")
            PasswordField("senha atual", senha) { senha = it }
        }
        if (presas.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "você ainda é dono de: ${presas.joinToString(", ")}. transfira ou exclua " +
                    "antes — constelação com gente dentro não some junto com a sua conta.",
                style = TextStyle(color = Obsidian.danger, fontSize = 11.sp, lineHeight = 16.sp),
            )
        } else erro?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = TextStyle(color = Obsidian.danger, fontSize = 11.sp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AboutButton(if (indo) "apagando…" else "apagar para sempre", accent = false, icone = Lucide.Trash2) {
                if (indo || confirmacao.trim().lowercase().removePrefix("@") != arroba.lowercase()) {
                    erro = "digite exatamente @$arroba."
                    return@AboutButton
                }
                indo = true; erro = null; presas = emptyList()
                escopo.launch {
                    val r = runCatching {
                        koin.get<AuthApi>().apagarConta(
                            ApagarContaRequest(confirmacao.trim().removePrefix("@"), senha.ifBlank { null }),
                        )
                    }.getOrNull()
                    indo = false
                    when {
                        r?.isSuccessful == true -> {
                            aoSairDaConta()
                        }
                        r?.code() == 409 -> {
                            val corpo = runCatching {
                                koin.get<Json>().decodeFromString<RecusaDeApagar>(
                                    r.errorBody()?.string().orEmpty(),
                                )
                            }.getOrNull()
                            presas = corpo?.constelacoes?.map { it.name }.orEmpty()
                            if (presas.isEmpty()) erro = corpo?.error ?: "não foi possível apagar."
                        }
                        r?.code() == 401 -> erro = "senha incorreta."
                        else -> erro = "não foi possível apagar. verifique a conexão."
                    }
                }
            }
            AboutButton("cancelar", accent = false) { aberto = false }
        }
    }
}

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
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(14.dp))

    msg?.let { (text, ok) ->
        Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
        Spacer(Modifier.height(10.dp))
    }

    val list = sessions
    when {
        list == null -> Text("carregando…", style = Tipo.descricao)
        list.isEmpty() -> Text("nenhuma sessão ativa.", style = Tipo.descricao)
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
                            style = Tipo.corpo,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(s.ip, prettyDate(s.lastUsedAt)?.let { "visto $it" })
                                .joinToString("  ·  "),
                            style = Tipo.apoio,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
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
    AboutButton(if (busy) "…" else "derrubar todas as outras", accent = false, icone = Lucide.LogOut) {
        if (busy) return@AboutButton
        busy = true; msg = null
        scope.launch {
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

private fun prettyDate(iso: String?): String? {
    val s = iso?.trim().orEmpty()
    if (s.length < 16) return null
    val d = s.substring(8, 10)
    val m = s.substring(5, 7)
    val hm = s.substring(11, 16)
    return "$d/$m $hm"
}

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
            style = Tipo.descricao,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        return
    }

    Text("atualizacoes", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "o Astra verifica ao abrir e a cada 20 minutos. você também pode procurar agora.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(14.dp))

    when (val s = st) {
        is UpdateState.Checking -> AboutStatus("procurando atualizacoes…")
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
    BotaoProcurarAtualizacao { updater.check() }
}

private const val PISO_DA_BUSCA = 1_800L
private val ETAPAS_DA_BUSCA = listOf("consultando o repositório…", "comparando versões…")

@Composable
private fun BotaoProcurarAtualizacao(procurar: suspend () -> Unit) {
    val escopo = rememberCoroutineScope()
    var procurando by remember { mutableStateOf(false) }
    var etapa by remember { mutableIntStateOf(0) }

    Column {
        AboutButton(
            label = if (procurando) ETAPAS_DA_BUSCA[etapa] else "procurar atualizações",
            accent = false,
            icone = Lucide.RefreshCw,
        ) {
            if (procurando) return@AboutButton
            procurando = true
            etapa = 0
            escopo.launch {
                val comecou = System.currentTimeMillis()
                val trabalho = launch { runCatching { procurar() } }
                delay(PISO_DA_BUSCA / ETAPAS_DA_BUSCA.size)
                etapa = 1
                trabalho.join()
                val resta = PISO_DA_BUSCA - (System.currentTimeMillis() - comecou)
                if (resta > 0) delay(resta)
                procurando = false
            }
        }
        if (procurando) {
            Spacer(Modifier.height(6.dp))
            BarraDeVarredura()
        }
    }
}

@Composable
private fun BarraDeVarredura() {
    val reduzMovimento = LocalReduceMotion.current
    val transicao = rememberInfiniteTransition(label = "varredura")
    val posicao by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "posicao",
    )
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawRect(color = Obsidian.borderDim, size = size)
        if (reduzMovimento) {
            drawRect(color = Obsidian.accentDim, size = size)
        } else {
            val largura = size.width * 0.35f
            val x = posicao * (size.width + largura) - largura
            drawRect(
                color = Obsidian.accentDim,
                topLeft = Offset(x.coerceAtLeast(0f), 0f),
                size = Size(
                    width = (x + largura).coerceAtMost(size.width) - x.coerceAtLeast(0f),
                    height = size.height,
                ),
            )
        }
    }
}

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

private fun mesEAno(iso: String?): String {
    val data = iso?.let { runCatching { java.time.OffsetDateTime.parse(it) }.getOrNull() } ?: return "—"
    val meses = listOf(
        "jan", "fev", "mar", "abr", "mai", "jun",
        "jul", "ago", "set", "out", "nov", "dez",
    )
    return "${meses[data.monthValue - 1]} ${data.year}"
}

@Composable
private fun BotaoDePerigo(label: String, icone: ImageVector, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val fundo by animateColorAsState(
        if (hov) Obsidian.danger.copy(alpha = 0.16f) else Color.Transparent, tween(140),
    )
    val borda by animateColorAsState(
        if (hov) Obsidian.danger else Obsidian.danger.copy(alpha = 0.55f), tween(140),
    )
    Row(
        modifier = Modifier
            .clickScale(src)
            .clip(RoundedCornerShape(8.dp))
            .background(fundo)
            .border(1.dp, borda, RoundedCornerShape(8.dp))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(icone, tint = Obsidian.danger, size = 14.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = TextStyle(color = Obsidian.danger, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun DialogoDeSenha(hasPassword: Boolean, onClose: () -> Unit) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    val podeSalvar = !busy && next.length >= 8 && next == confirm && (!hasPassword || current.isNotBlank())

    fun salvar() {
        if (!podeSalvar) return
        busy = true
        erro = null
        scope.launch {
            val r = runCatching {
                val api = koin.get<UserApi>()
                if (hasPassword) api.changePassword(ChangePasswordRequest(current, next))
                else api.setPassword(SetPasswordRequest(next))
            }
            busy = false
            if (r.isSuccess) onClose()
            else erro = if (hasPassword) "Não deu. Confira a senha atual." else "Não deu. Tente de novo."
        }
    }

    DialogShell(onClose = onClose, respiro = 20.dp) {
        Column {
                Text(
                    if (hasPassword) "Mudança de senha" else "Definir senha",
                    style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (hasPassword) "Informe a senha atual e escolha a nova."
                    else "Escolha uma senha para entrar também por e-mail.",
                    style = Tipo.descricao,
                )
                Spacer(Modifier.height(18.dp))

                if (hasPassword) {
                    CampoDoDialogo("Senha atual")
                    PasswordField("senha atual", current) { current = it; erro = null }
                    Spacer(Modifier.height(14.dp))
                }
                CampoDoDialogo("Nova senha")
                PasswordField("nova senha", next) { next = it; erro = null }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ao menos 8 caracteres.",
                    style = Tipo.apoio,
                )
                Spacer(Modifier.height(14.dp))
                CampoDoDialogo("Confirmar nova senha")
                PasswordField("confirmar nova senha", confirm) { confirm = it; erro = null }

                if (confirm.isNotEmpty() && confirm != next) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "As duas não são iguais.",
                        style = TextStyle(color = Obsidian.danger, fontSize = 11.sp),
                    )
                }
                erro?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = Tipo.erro)
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotaoDoDialogo("Cancelar", primario = false, ligado = !busy, onClick = onClose)
                    Spacer(Modifier.width(10.dp))
                    BotaoDoDialogo(
                        if (busy) "Salvando…" else "Pronto",
                        primario = true,
                        ligado = podeSalvar,
                        onClick = { salvar() },
                    )
                }
        }
    }
}

@Composable
private fun CampoDoDialogo(texto: String) {
    Text(
        texto,
        style = Tipo.rotulo,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
internal fun BotaoDoDialogo(rotulo: String, primario: Boolean, ligado: Boolean, onClick: () -> Unit) {
    val toque = remember { MutableInteractionSource() }
    val sobre by toque.collectIsHoveredAsState()
    val fundo = when {
        primario && ligado -> if (sobre) Obsidian.accent else Obsidian.accentDim
        primario -> Obsidian.overlay
        sobre -> Obsidian.hover
        else -> Obsidian.overlay
    }
    val cor = when {
        primario && ligado -> Obsidian.void
        ligado -> Obsidian.text1
        else -> Obsidian.text3
    }
    Box(
        Modifier
            .clickScale(toque)
            .clip(RoundedCornerShape(8.dp))
            .background(fundo)
            .hoverable(toque, enabled = ligado)
            .clickable(enabled = ligado, interactionSource = toque, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(rotulo, style = TextStyle(color = cor, fontSize = 13.sp))
    }
}

@Composable
private fun PasswordField(placeholder: String, value: String, onChange: (String) -> Unit) {
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
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = Tipo.corpo,
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReadRow(label: String, value: String) {
    Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Tipo.descricao, modifier = Modifier.width(80.dp))
        Text(value, style = Tipo.corpo)
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
            Text(sub, style = Tipo.nota)
        }
    }
}

@Composable
private fun DeviceDropdown(
    devices: List<AparelhoDeAudio>,
    selected: String?,
    onPick: (String?) -> Unit,
) {
    val nomeAtual = devices.firstOrNull { it.id == selected }?.nome
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
                nomeAtual ?: "padrão do Windows",
                style = Tipo.corpo,
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
                        DeviceRow(d.nome, selected == d.id) { onPick(d.id); open = false }
                    }
                    if (devices.isEmpty()) {
                        DeviceRow("procurando os aparelhos…", false) {}
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

@Composable
private fun TituloExplicavel(titulo: String, explicacao: String) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val cor by animateColorAsState(if (hov) Obsidian.accent else Obsidian.text3, tween(140))

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.hoverable(src),
        ) {
            Text(
                titulo,
                style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif),
            )
            Spacer(Modifier.width(7.dp))
            Box(Modifier.size(4.dp).clip(CircleShape).background(cor))
        }
        if (hov) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 30),
                properties = PopupProperties(focusable = false),
            ) {
                Text(
                    explicacao,
                    style = TextStyle(color = Obsidian.text2, fontSize = 11.5.sp, lineHeight = 17.sp),
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

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
                style = Tipo.rotulo,
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
private fun VoiceSection(
    p: DesktopPrefs.Prefs,
    prefs: DesktopPrefs,
    aparelhos: FonteDeAparelhos,
) {
    TituloExplicavel(
        "Transmissao de tela",
        "Vale ao iniciar a transmissão. O padrão de estreia sai da força do computador — " +
            "quem tem quatro núcleos ou menos começa em 540p, porque comprimir vídeo aqui " +
            "é trabalho do processador.",
    )
    RadioList(
        ScreenQuality.entries.map { it.label to it },
        p.screenQuality, prefs::setScreenQuality,
    )

    SettingsDivider()
    TituloExplicavel(
        "Motor de vídeo novo",
        "Comprime a transmissão na placa de vídeo, sem trazer o quadro para o processador. " +
            "Vale ao entrar na próxima chamada — não muda uma que já esteja em andamento.",
    )
    ToggleRow(
        "Usar o motor novo",
        "em teste. sem o pacote ou sem placa compatível, a chamada segue pelo caminho de sempre",
        p.motorNovo,
        prefs::setMotorNovo,
    )
    ToggleRow(
        "Transmitir em duas qualidades",
        "em teste. manda a tela cheia e uma versão menor ao mesmo tempo, para que quem está com " +
            "a rede curta receba a menor em vez de derrubar a qualidade de todo mundo. exige placa " +
            "com aceleração; sem ela, segue em uma qualidade só",
        p.duasCamadas,
        prefs::setDuasCamadas,
    )

    if (Canal.ehDeDesenvolvimento) {
        SettingsDivider()
        Text("Ninguém te escuta?", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
        Spacer(Modifier.height(4.dp))
        Text(
            "entrar numa call passa por várias etapas, e todas falham do mesmo jeito: silêncio. a lista abaixo mostra até onde chegou — a etapa que faltar é a culpada.",
            style = Tipo.apoio,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(10.dp))
        VoicePassos()
    }

    SettingsDivider()
    Text("Dispositivos", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "\"padrao do Windows\" segue o que você escolheu no sistema — inclusive se trocar depois.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(10.dp))
    LaunchedEffect(Unit) { aparelhos.listar() }
    FieldLabel("saída (quem você ouve)")
    DeviceDropdown(aparelhos.saidas, p.audioOutput, prefs::setAudioOutput)
    Spacer(Modifier.height(12.dp))
    FieldLabel("entrada (seu microfone)")
    DeviceDropdown(aparelhos.microfones, p.audioInput, prefs::setAudioInput)

    Spacer(Modifier.height(16.dp))
    LinhaDeVolume("volume do microfone", p.volumeDoMicrofone, prefs::setVolumeDoMicrofone)
    Spacer(Modifier.height(10.dp))
    LinhaDeVolume("volume da escuta", p.volumeDaEscuta, prefs::setVolumeDaEscuta)
    Spacer(Modifier.height(6.dp))
    Text(
        "os dois só abaixam. para uma pessoa específica, use o botão direito no cartão dela na chamada.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )

    SettingsDivider()
    Text("Microfone", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(10.dp))
    ToggleRow("Cancelamento de eco", "evita o retorno do audio dos outros pelo seu mic", p.micEchoCancel, prefs::setMicEchoCancel)
    ToggleRow("Supressao de ruido", "corta ventilador, teclado e chiado de fundo", p.micNoiseSuppression, prefs::setMicNoiseSuppression)
    ToggleRow("Ganho automatico", "nivela o volume da sua voz sozinho", p.micAutoGain, prefs::setMicAutoGain)
    if (!p.micEchoCancel) {
        Spacer(Modifier.height(10.dp))
        Text(
            "sem o cancelamento de eco, os dois ajustes acima não têm efeito: no " +
                "Windows os três vivem no mesmo componente, e sem ele o microfone entra cru.",
            style = Tipo.apoio,
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    MicSensitivityRow(p.micSensitivity, prefs::setMicSensitivity)
    Spacer(Modifier.height(4.dp))
    Text(
        "os ajustes do microfone valem na hora, mesmo com a call aberta — o som corta " +
            "por um instante enquanto o microfone reabre.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )
}

@Composable
private fun AtalhosSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    CapturaDeTecla("mudo", p.teclaMudo, prefs::setTeclaMudo)
    Spacer(Modifier.height(12.dp))
    CapturaDeTecla("ensurdecer", p.teclaEnsurdecer, prefs::setTeclaEnsurdecer)

    SettingsDivider()
    FieldLabel("fixas")
    Spacer(Modifier.height(4.dp))
    AtalhoFixo("Ctrl K", "buscar")
    AtalhoFixo("Esc", "fechar o que estiver aberto")
    AtalhoFixo("Enter", "enviar a mensagem")
    AtalhoFixo("Shift Enter", "quebrar linha", ultima = true)

    Spacer(Modifier.height(16.dp))
    InfoNote(
        "O que o Astra escuta do seu teclado",
        "Para uma tecla funcionar com o jogo em primeiro plano, o Windows exige um " +
            "gancho de teclado do sistema — não existe outro caminho, e é o mesmo que " +
            "Discord e TeamSpeak usam.\n\n" +
            "O que o Astra faz com ele: compara a tecla apertada com as escolhidas " +
            "aqui em cima. Só isso. Nada é guardado, contado ou enviado para lugar " +
            "nenhum, e a tecla segue o caminho dela normalmente.\n\n" +
            "Sem nenhuma tecla escolhida, o gancho nem chega a ser instalado.",
    )
}

@Composable
private fun AtalhoFixo(tecla: String, oQueFaz: String, ultima: Boolean = false) {
    Row(
        Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tecla,
            style = TextStyle(color = Obsidian.text2, fontSize = 11.sp, fontFamily = DmMono),
            modifier = Modifier
                .widthIn(min = 92.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Obsidian.void.copy(alpha = 0.55f))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(oQueFaz, style = Tipo.descricao)
    }
    if (!ultima) Spacer(Modifier.height(7.dp))
}

@Composable
private fun CapturaDeTecla(rotulo: String, vk: Int, onEscolher: (Int) -> Unit) {
    var ouvindo by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val nome = remember(vk) { AtalhosGlobais.nomeDaTecla(vk) }

    DisposableEffect(Unit) { onDispose { AtalhosGlobais.cancelarCaptura() } }

    Row(
        Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rotulo, style = TextStyle(color = Obsidian.text2, fontSize = 13.sp))
            Text(
                if (ouvindo) "aperte a tecla — esc deixa sem nenhuma" else "clique para trocar",
                style = Tipo.apoio,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .clickScale(interaction, pressedScale = 0.97f, formaDoFoco = RoundedCornerShape(8.dp))
                .widthIn(min = 116.dp)
                .clip(FormaDeBotao)
                .background(
                    when {
                        ouvindo -> Obsidian.accent.copy(alpha = 0.16f)
                        hovered -> Obsidian.hover
                        else -> Obsidian.raised
                    },
                )
                .border(
                    1.dp,
                    if (ouvindo) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim,
                    FormaDeBotao,
                )
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) {
                    ouvindo = true
                    AtalhosGlobais.capturarProxima { escolhida ->
                        escopo.launch {
                            ouvindo = false
                            onEscolher(escolhida)
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (ouvindo) "ouvindo…" else nome,
                style = TextStyle(
                    color = if (ouvindo) Obsidian.accent else if (vk == 0) Obsidian.text3 else Obsidian.text1,
                    fontSize = 12.sp,
                    fontFamily = DmMono,
                ),
                maxLines = 1,
            )
        }
    }
}

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

@Composable
private fun PerformanceSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    if (p.perfAutomatico.isNotBlank()) {
        Box(
            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                "O Astra ligou isto sozinho na primeira abertura: encontrou ${p.perfAutomatico} " +
                    "nesta máquina. Desligue à vontade — a escolha passa a ser sua e ele não " +
                    "mexe mais.",
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
    ToggleRow(
        "Modo desempenho",
        "desliga aurora + estrelas e reduz animações de uma vez — para jogar ou transmitir",
        p.performanceMode, prefs::setPerformanceMode,
    )
    Spacer(Modifier.height(6.dp))

    Column(Modifier.alpha(if (p.performanceMode) 0.45f else 1f)) {
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
    }

    Spacer(Modifier.height(6.dp))
    ToggleRow(
        "Janela translucida",
        "cantos arredondados + fundo vazando; opaca = mais nitido e leve",
        p.windowTransparent, prefs::setWindowTransparent,
    )
    Text(
        "a transparencia da janela so aplica ao reiniciar o app.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )

    Spacer(Modifier.height(6.dp))
    ToggleRow(
        "Fechar de vez ao fechar o app",
        "o X encerra o Astra em vez de minimizar para bandeja — sem nada em segundo plano",
        p.exitOnClose, prefs::setExitOnClose,
    )
    Spacer(Modifier.height(6.dp))
    ArranqueComWindows()
}

private fun mascarar(email: String): String {
    val arroba = email.indexOf('@')
    if (arroba <= 0) return "•••"
    return email.first() + "•••" + email.substring(arroba)
}

@Composable
private fun ArranqueComWindows() {
    if (!InicioComWindows.disponivel()) return
    var ligado by remember { mutableStateOf(InicioComWindows.ligado()) }
    var escondido by remember { mutableStateOf(InicioComWindows.escondido()) }
    var falhou by remember { mutableStateOf(false) }

    fun gravar(novoLigado: Boolean, novoEscondido: Boolean) {
        val ok = InicioComWindows.aplicar(novoLigado, novoEscondido)
        falhou = !ok
        if (!ok) return
        ligado = novoLigado
        escondido = novoEscondido
    }

    ToggleRow(
        "Abrir junto com o Windows",
        "o Astra já sobe na bandeja ao ligar o computador — sem esperar você lembrar dele",
        ligado,
    ) { gravar(it, escondido) }
    if (ligado) {
        Spacer(Modifier.height(6.dp))
        ToggleRow(
            "Ao subir assim, começar sem janela",
            "só o ícone na bandeja: nada aparece na frente de quem acabou de ligar o PC",
            escondido,
        ) { gravar(true, it) }
    }
    if (falhou) {
        Spacer(Modifier.height(6.dp))
        Text(
            "o Windows recusou a mudança no arranque. dá para ligar e desligar isto também " +
                "pelo Gerenciador de Tarefas, na aba Inicializar.",
            style = TextStyle(color = Obsidian.danger, fontSize = 11.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
}


@Composable
private fun LabeledControl(title: String, sub: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = Tipo.corpo)
        Text(sub, style = Tipo.apoio)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

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
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Tipo.corpo)
            Text(sub, style = Tipo.apoio)
        }
        Toggle(on, onChange)
    }
    Spacer(Modifier.height(8.dp))
}

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

@Composable
private fun AppearanceSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    FieldLabel("tema")
    PresetGrid(p.accentId, p.bgId) { prefs.setTheme(it.accentId, it.bgId) }

    SettingsDivider()
    LabeledControl("Fundo", "liso e o padrao; a aurora e um shader animado e cobra GPU") {
        SegmentedRow(FundoPref.entries.map { it.label to it }, fundoAtual(p)) { aplicarFundo(prefs, it) }
    }

    SettingsDivider()
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun PetsSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    TituloExplicavel(
        "Companheiro",
        "A cor troca a rampa que o artista desenhou, degrau por degrau — os olhos, o " +
            "contorno e os detalhes ficam como estão, e é isso que mantém o pet " +
            "reconhecível em vez de virar uma mancha de uma cor só. O nome aparece " +
            "sobre ele quando reage a uma mensagem. Clique nele para ver o que ele " +
            "faz; insistir demais o cansa.",
    )

    if (!p.petLigado) {
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                "O companheiro está desligado. Ligue em Acessibilidade › movimento.",
                style = Tipo.rotulo,
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    val pet = Pet.de(p.petTipo)
    var gesto by remember { mutableStateOf(Anim.PARADO) }
    if (gesto !in pet.passos) gesto = Anim.PARADO

    PetPalco(pet, Pelagem.de(p.petPelagem), gesto)
    Spacer(Modifier.height(12.dp))
    GestosDoPet(pet, gesto) { gesto = it }

    SettingsDivider()
    if (Pet.disponiveis.size > 1) {
        FieldLabel("pet")
        SegmentedRow(
            Pet.disponiveis.map { it.rotulo to it.name },
            p.petTipo,
            prefs::setPetTipo,
        )
        Spacer(Modifier.height(18.dp))
    }
    FieldLabel("pelagem")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pelagem.entries.forEach { pel ->
            AmostraDePelagem(pel, pel.name == p.petPelagem) { prefs.setPetPelagem(pel.name) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        Pelagem.de(p.petPelagem).rotulo,
        style = Tipo.rotulo,
    )

    Spacer(Modifier.height(18.dp))
    FieldLabel("nome")
    Box(
        Modifier.widthIn(max = 260.dp).fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (p.petNome.isEmpty()) {
            Text("Sem nome", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
        }
        BasicTextField(
            value = p.petNome,
            onValueChange = prefs::setPetNome,
            singleLine = true,
            textStyle = Tipo.corpo,
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SettingsDivider()
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun AmostraDePelagem(pelagem: Pelagem, escolhida: Boolean, onClick: () -> Unit) {
    val fonte = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(pelagem.amostra)
            .border(
                width = if (escolhida) 2.dp else 1.dp,
                color = if (escolhida) Obsidian.accent else Obsidian.borderDim,
                shape = CircleShape,
            )
            .clickable(interactionSource = fonte, indication = null, onClick = onClick)
            .clickScale(fonte, formaDoFoco = CircleShape)
            .semantics { contentDescription = pelagem.rotulo },
    )
}

@Composable
private fun AccessibilitySection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    Text("Legibilidade do texto", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(10.dp))
    LabeledControl("Tamanho da fonte", "das mensagens no chat") {
        SegmentedRow(FontSizePref.entries.map { it.label to it }, p.fontSize, prefs::setFontSize)
    }
    LabeledControl("Densidade das mensagens", "respiro entre as mensagens") {
        SegmentedRow(DensityPref.entries.map { it.label to it }, p.density, prefs::setDensity)
    }

    SettingsDivider()
    TituloExplicavel(
        "Contraste",
        "O padrão do Astra é mais suave de propósito: contraste alto demais em fundo " +
            "escuro faz a borda da letra vibrar, e isso cansa em sessão longa à noite. " +
            "Ligue o alto contraste se o padrão for difícil de ler — a troca é sua, e " +
            "legibilidade ganha de conforto.",
    )
    ToggleRow(
        "Alto contraste",
        "clareia texto e bordas — vale na hora, em todas as telas",
        p.altoContraste, prefs::setAltoContraste,
    )

    SettingsDivider()
    TituloExplicavel(
        "Companheiro",
        "Um pet em pixel art que caminha por cima da interface. Ele passa a maior " +
            "parte do tempo parado e só anda em trechos curtos: movimento contínuo " +
            "no canto do olho ensina o olho a ignorar o resto da tela. Reage quando " +
            "chega mensagem, e some junto se você reduzir movimento. A cor e o nome " +
            "estão em Aparência.",
    )
    ToggleRow(
        "Pet na tela",
        "anda pela interface e reage a mensagem nova",
        p.petLigado, prefs::setPetLigado,
    )

    SettingsDivider()
    TituloExplicavel(
        "Movimento",
        "Congela a aurora e desliga as cascatas de entrada e os pulsos. Vale em todas " +
            "as telas, na hora. O Astra também obedece ao ajuste de movimento do próprio " +
            "Windows — este interruptor é para quando você quer parar tudo sem mexer no " +
            "sistema inteiro.",
    )
    ToggleRow(
        "Reduzir movimento",
        "congela a aurora e desliga cascatas e pulsos",
        p.reduceMotion, prefs::setReduceMotion,
    )
    Spacer(Modifier.height(20.dp))
}

private const val CASCATA_PASSO_MS = 40
private const val CASCATA_DURACAO_MS = 380
private const val CASCATA_DEGRAUS = 16
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

@Composable
internal fun SettingsDivider() {
    Spacer(Modifier.height(32.dp))
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
        FamiliaDeTema.entries.forEach { familia ->
            val doGrupo = ThemePresets.filter { it.familia == familia }
            if (doGrupo.isEmpty()) return@forEach
            Text(
                familia.titulo.uppercase(),
                style = TextStyle(color = Obsidian.text3, fontSize = 9.sp, letterSpacing = 1.5.sp),
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
            doGrupo.chunked(2).forEach { pair ->
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
                style = Tipo.nota,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
