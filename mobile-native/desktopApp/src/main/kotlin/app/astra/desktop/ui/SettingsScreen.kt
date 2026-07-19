package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.SmilePlus
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.X
import app.astra.desktop.profile.AvatarPicker
import app.astra.desktop.prefs.AuroraQuality
import app.astra.desktop.prefs.DensityPref
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.FontSizePref
import app.astra.desktop.prefs.ScreenQuality
import app.astra.desktop.prefs.UiFps
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.ThemePreset
import app.astra.desktop.ui.theme.ThemePresets
import app.astra.desktop.ui.theme.accentOption
import app.astra.desktop.ui.theme.bgOption
import app.astra.desktop.update.UpdateService
import app.astra.desktop.update.UpdateState
import app.astra.mobile.core.network.UserApi
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

private enum class SettingsTab(val label: String, val sub: String, val icon: ImageVector) {
    ACCOUNT("Conta", "email e senha", Lucide.User),
    PROFILE("Perfil", "avatar, nome e recado", Lucide.Pencil),
    NOTIFICATIONS("Notificacoes", "avisos na bandeja", Lucide.Bell),
    APPEARANCE("Aparencia", "cores, fonte, densidade", Lucide.Palette),
    PERFORMANCE("Desempenho", "graficos, animacoes, fps", Lucide.ChartColumn),
    VOICE("Voz", "microfone e transmissao", Lucide.Volume2),
    ABOUT("Sobre", "versao e atualizacoes", Lucide.Info),
}

// Settings em TAKEOVER estilo Discord (decisao do dono): ocupa o shell inteiro,
// nav de secoes na esquerda + conteudo na direita. Secoes v1: Conta (senha),
// Notificacoes (toggles do tray) e Movimento (reduzir animacoes).
@Composable
fun SettingsScreen(
    me: ProfileUserDto?,
    prefs: DesktopPrefs,
    onClose: () -> Unit,
    onProfileSaved: () -> Unit = {},
) {
    var tab by remember { mutableStateOf(SettingsTab.ACCOUNT) }
    val prefState by prefs.state.collectAsState()
    // Rascunho do perfil VIVE AQUI (nao dentro da secao): a previa e IRMA da
    // secao, nao filha — hoisted, ela reage a cada tecla. Reseta quando o `me`
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
        // nada vaza atras. Aqui so um veu segura a leitura. Pintar aurora nova aqui
        // era o "salto de posicao" ao abrir configuracoes (relogio independente).
        Box(Modifier.matchParentSize().background(Obsidian.base.copy(alpha = 0.5f)))
        Row(Modifier.fillMaxSize()) {
            // Nav das secoes
            Column(
                Modifier.width(220.dp).fillMaxHeight().padding(horizontal = 12.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "configuracoes",
                    style = TextStyle(color = Obsidian.text1, fontSize = 18.sp, fontFamily = DmSerif),
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                )
                SettingsTab.entries.forEach { t ->
                    NavRow(t.icon, t.label, t.sub, active = t == tab) { tab = t }
                }
            }

            // Conteudo da secao — coluna capada (~720) estilo Discord: nao esparrama
            // pelo palco todo (o "enxuto" que o dono pediu). O Box segura a coluna
            // encostada a esquerda; os controles leem como uma coluna so em vez de
            // soltos num vazao grande a direita. Titulo + fechar vivem dentro dela.
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            // Larga o bastante pra previa caber AO LADO da coluna capada (720) sem
            // encostar; senao ela empilha embaixo. Sobre = so a aba sem previa.
            val wide = maxWidth > 1080.dp
            val showPreview = tab != SettingsTab.ABOUT
            Column(
                Modifier.align(Alignment.TopStart).widthIn(max = 720.dp).fillMaxWidth()
                    .fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tab.label,
                        style = TextStyle(color = Obsidian.text1, fontSize = 26.sp, fontFamily = DmSerif),
                        modifier = Modifier.weight(1f),
                    )
                    // Fechar (ESC tambem, via foco no shell) volta pro shell.
                    val hov = remember { MutableInteractionSource() }
                    val h by hov.collectIsHoveredAsState()
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (h) Obsidian.hover else Obsidian.overlay)
                            .border(1.dp, Obsidian.borderMid, CircleShape)
                            .hoverable(hov)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        LIcon(Lucide.X, tint = Obsidian.text2, size = 15.dp)
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Troca de secao com fade + leve zoom (decisao do dono).
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f))
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "settingsSection",
                ) { current ->
                    // Cada secao emite varios filhos DIRETO. Sem esta Column o
                    // container do AnimatedContent os empilha no mesmo Y (era o bug
                    // dos "textos sobrepostos"). A Column relaya em vertical.
                    Column(Modifier.fillMaxWidth()) {
                    when (current) {
                        SettingsTab.ACCOUNT -> AccountSection(me)
                        SettingsTab.PROFILE -> ProfileSection(me, draft, { draft = it }, onProfileSaved)
                        SettingsTab.NOTIFICATIONS -> Column {
                            ToggleRow(
                                "Sussurros (DMs)", "avisa quando chega mensagem privada",
                                prefState.notifyDms, prefs::setNotifyDms,
                            )
                            ToggleRow(
                                "Atividade de canal", "avisa nova mensagem nas constelacoes",
                                prefState.notifyChannels, prefs::setNotifyChannels,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "os avisos aparecem na bandeja so com a janela fechada ou minimizada.",
                                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                                modifier = Modifier.widthIn(max = 460.dp),
                            )
                        }
                        SettingsTab.APPEARANCE -> AppearanceSection(prefState, prefs)
                        SettingsTab.PERFORMANCE -> PerformanceSection(prefState, prefs)
                        SettingsTab.VOICE -> VoiceSection(prefState, prefs)
                        SettingsTab.ABOUT -> AboutSection()
                    }
                    }
                }

                // Janela estreita: a previa nao cabe ao lado -> empilha embaixo,
                // separada por um fio.
                if (!wide && showPreview) {
                    SettingsDivider()
                    SettingsPreview(tab, me, prefState, draft, Modifier.widthIn(max = 420.dp).fillMaxWidth())
                }
            }
                // Janela larga: previa como card fixo no vazio a direita (nao rola
                // junto; ancorado ao centro-direita do palco).
                if (wide && showPreview) {
                    SettingsPreview(tab, me, prefState, draft, Modifier.align(Alignment.CenterEnd).padding(end = 32.dp).width(300.dp))
                }
            }
        }
    }
}

// Previa ao vivo (lado das configs). Cada aba mostra o efeito real do que se
// mexe: Conta = teu perfil como os OUTROS veem; Notificacoes = aviso deslizando
// na bandeja; Aparencia = mini-janela no tema/fonte/densidade; Desempenho =
// medidor de custo GPU/CPU; Voz = moldura da transmissao + nivel do mic ao vivo.
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
        when (tab) {
            // Conta = teu perfil SALVO; Perfil = o rascunho ao vivo (cada tecla).
            SettingsTab.ACCOUNT -> ProfileCardPreview(me, null)
            SettingsTab.PROFILE -> ProfileCardPreview(me, draft)
            SettingsTab.NOTIFICATIONS -> NotifPreviewCard(p.reduceMotionEff)
            SettingsTab.APPEARANCE -> UiSamplePreview(p.fontSize, p.density)
            SettingsTab.PERFORMANCE -> CostMeter(p)
            SettingsTab.VOICE -> VoicePreview(p)
            SettingsTab.ABOUT -> Unit
        }
    }
}

// Rascunho editavel do perfil. Fica no nivel da tela pra a previa (irma da
// secao) conseguir ler enquanto voce digita.
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
        )
    }
}

// --- Card de perfil: espelha o ProfilePopupCard (o que os outros abrem no teu
// avatar). draft = null -> mostra o perfil SALVO (aba Conta); draft != null ->
// mostra o rascunho ao vivo (aba Perfil), campo a campo. ---
@Composable
private fun ProfileCardPreview(me: ProfileUserDto?, draft: ProfileDraft?) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
    ) {
        if (me == null) {
            Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                Text("carregando…", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
            }
            return@Column
        }
        // Rascunho manda quando existe (aba Perfil); senao, o valor salvo (Conta).
        val avatar = draft?.avatarUrl ?: me.avatarUrl
        val name = draft?.displayName?.trim()?.ifBlank { null } ?: me.displayName ?: me.username
        val pronouns = draft?.pronouns ?: me.pronouns
        val bio = draft?.bio ?: me.bio
        val emoji = draft?.statusEmoji ?: me.statusEmoji
        val recado = draft?.customStatus ?: me.customStatus
        val ring = userColor(me.id)
        ProfileBanner(
            css = draft?.bannerColor ?: me.bannerColor,
            imageUrl = draft?.bannerUrl ?: me.bannerUrl,
            positionY = draft?.bannerPositionY ?: me.bannerPositionY ?: 50,
            scale = draft?.bannerScale ?: me.bannerScale ?: 100,
            fallback = Obsidian.overlay,
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
        Column(Modifier.padding(horizontal = 14.dp)) {
            Box(
                Modifier.offset(y = (-24).dp).clip(CircleShape).background(Obsidian.raised)
                    .border(3.dp, ring, CircleShape).padding(3.dp),
            ) {
                DesktopAvatar(avatar, name, 48)
            }
            Column(Modifier.offset(y = (-14).dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = TextStyle(color = Obsidian.text1, fontSize = 16.sp, fontFamily = DmSerif),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusDot(status = userStatus(me.effectiveStatus), size = 10.dp, cutoutColor = Obsidian.raised)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@${me.username}", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono))
                    if (!pronouns.isNullOrBlank()) {
                        Text("  ·  $pronouns", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
                    }
                }
                if (!recado.isNullOrBlank() || !emoji.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        listOfNotNull(emoji?.ifBlank { null }, recado?.ifBlank { null }).joinToString(" "),
                        style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                    )
                }
                if (!bio.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp)); HairRule(); Spacer(Modifier.height(8.dp))
                    Text(
                        bio,
                        style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("e assim que os outros te veem", style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
                Spacer(Modifier.height(6.dp))
            }
        }
    }
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
        HairRule()
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            SampleMsg("ana", "e ai, bora marcar a call?", s)
            Spacer(Modifier.height((density.topDp).dp))
            SampleMsg("voce", "fechou, 21h entao", s)
        }
        HairRule()
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

// --- Desempenho: medidor de custo ESTIMADO (nao mede a GPU real; deriva das
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
        m < 0.36f -> "leve — sobra folga pra jogar ou transmitir junto."
        m < 0.68f -> "equilibrado — visual completo sem pesar."
        else -> "pesado — o modo desempenho corta isso num toque."
    }
}

// --- Voz: moldura 16:9 na resolucao/fps escolhido + medidor do mic ao vivo.
// O medidor abre o microfone SO enquanto esta aba/previa esta visivel. ---
@Composable
private fun VoicePreview(p: DesktopPrefs.Prefs) {
    Column(Modifier.fillMaxWidth()) {
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
            MicMeter()
            Spacer(Modifier.height(8.dp))
            Text(
                if (p.micNoiseSuppression) "supressao de ruido: ligada" else "supressao de ruido: desligada",
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
            )
        }
    }
}

// Medidor de nivel do mic: abre um TargetDataLine (Java Sound) numa thread
// daemon enquanto a previa vive, le o RMS dos samples e move as barras.
// onDispose fecha a linha (troca de aba / fecha configuracoes). Best-effort:
// sem mic ou em uso -> mostra aviso, nao quebra.
@Composable
private fun MicMeter() {
    var level by remember { mutableFloatStateOf(0f) }
    var available by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
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
    // Cor por qualidade do sinal: verde = bom (forte), amarelo = medio, vermelho
    // = ruim (baixo demais pra te ouvir bem). Mesma escala de 3 cores do CostBar.
    // Anima a troca de cor pra nao piscar seco entre faixas.
    val meterColor by animateColorAsState(
        when {
            lvl < 0.24f -> Obsidian.danger
            lvl < 0.52f -> Obsidian.accent
            else -> Obsidian.success
        },
        tween(220),
        label = "micColor",
    )
    Row(
        Modifier.fillMaxWidth().height(30.dp),
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
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    val original = remember(me) { ProfileDraft.from(me) }
    var saving by remember { mutableStateOf(false) }
    var busyAvatar by remember { mutableStateOf(false) }
    var busyBanner by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val dirty = draft != original

    Row(verticalAlignment = Alignment.CenterVertically) {
        DesktopAvatar(draft.avatarUrl, draft.displayName.ifBlank { me?.username ?: "voce" }, 64)
        Spacer(Modifier.width(16.dp))
        Column {
            AboutButton(if (busyAvatar) "processando…" else "trocar avatar", accent = true) {
                if (busyAvatar) return@AboutButton
                // O dialogo nativo bloqueia (modal) — normal. O peso (decodificar
                // e reduzir) vai pra fora da thread de UI.
                val file = AvatarPicker.choose() ?: return@AboutButton
                busyAvatar = true
                msg = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) { AvatarPicker.encode(file) }
                    busyAvatar = false
                    r.onSuccess { onChange(draft.copy(avatarUrl = it)) }
                        .onFailure { msg = "nao deu pra ler essa imagem" to false }
                }
            }
            if (draft.avatarUrl != null) {
                Spacer(Modifier.height(6.dp))
                AboutButton("remover", accent = false) { onChange(draft.copy(avatarUrl = null)) }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "a imagem e reduzida pra 512px e guardada no teu perfil (maximo 5MB).",
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
    ProfileField("bio", draft.bio, "fale de voce", multiline = true, max = 300) {
        onChange(draft.copy(bio = it))
    }

    SettingsDivider()
    FieldLabel("banner")
    // Arrastar na vertical reposiciona a imagem (bannerPositionY); so faz sentido
    // com imagem — no gradiente nao ha o que enquadrar.
    ProfileBanner(
        css = draft.bannerColor,
        imageUrl = draft.bannerUrl,
        positionY = draft.bannerPositionY,
        scale = draft.bannerScale,
        fallback = Obsidian.overlay,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .then(
                if (draft.bannerUrl.isNullOrBlank()) Modifier
                else Modifier.pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        // 110dp de altura -> ~1.4 px por ponto de posicao. Arrastar
                        // pra BAIXO revela o topo da imagem (posicao diminui).
                        val next = (draft.bannerPositionY - drag.y / 1.4f).toInt()
                        onChange(draft.copy(bannerPositionY = next.coerceIn(0, 100)))
                    }
                },
            ),
    )
    if (!draft.bannerUrl.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
            "arraste na imagem pra enquadrar.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        )
        Spacer(Modifier.height(10.dp))
        ZoomTrack(draft.bannerScale) { onChange(draft.copy(bannerScale = it)) }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AboutButton(if (busyBanner) "processando…" else "subir imagem", accent = true) {
            if (busyBanner) return@AboutButton
            val file = AvatarPicker.choose("Escolher banner") ?: return@AboutButton
            busyBanner = true
            msg = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { AvatarPicker.encode(file, AvatarPicker.BANNER_DIM) }
                busyBanner = false
                r.onSuccess { onChange(draft.copy(bannerUrl = it, bannerPositionY = 50, bannerScale = 100)) }
                    .onFailure { msg = "nao deu pra ler essa imagem" to false }
            }
        }
        if (!draft.bannerUrl.isNullOrBlank()) {
            AboutButton("remover imagem", accent = false) { onChange(draft.copy(bannerUrl = null)) }
        }
    }
    Spacer(Modifier.height(14.dp))
    FieldLabel("ou um gradiente")
    GradientGrid(draft.bannerColor) { onChange(draft.copy(bannerColor = it)) }

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
                Text("no que voce esta?", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
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

    Spacer(Modifier.height(20.dp))
    msg?.let { (text, ok) ->
        Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
        Spacer(Modifier.height(8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AboutButton(if (saving) "salvando…" else "salvar", accent = true) {
            if (saving || !dirty) return@AboutButton
            saving = true
            msg = null
            scope.launch {
                val api = koin.get<UserApi>()
                val r = runCatching {
                    // Recado tem rota propria; so manda se mudou.
                    if (draft.customStatus.trim() != original.customStatus.trim()) {
                        api.setCustomStatus(CustomStatusRequest(draft.customStatus.trim()))
                    }
                    api.updateProfile(
                        UpdateProfileRequest(
                            // null = chave omitida = backend nao mexe no campo
                            // (mesma convencao do card do rodape).
                            displayName = draft.displayName.trim().ifBlank { null },
                            pronouns = draft.pronouns.trim(),
                            bio = draft.bio.trim(),
                            avatarUrl = draft.avatarUrl,
                            statusEmoji = draft.statusEmoji,
                            // Banner: "" limpa a imagem (null seria "nao mexer").
                            bannerUrl = draft.bannerUrl ?: "",
                            bannerColor = draft.bannerColor,
                            bannerPositionY = draft.bannerPositionY,
                            bannerScale = draft.bannerScale,
                        ),
                    )
                }
                saving = false
                if (r.isSuccess) {
                    msg = "perfil salvo" to true
                    onSaved()
                } else {
                    msg = "nao deu pra salvar — tenta de novo" to false
                }
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
    Spacer(Modifier.height(20.dp))
}

// Zoom do banner (bannerScale 100..300%): trilha arrastavel simples. Slider
// proprio pra nao puxar componente novo so por isto.
@Composable
private fun ZoomTrack(scale: Int, onChange: (Int) -> Unit) {
    val pct = ((scale - 100) / 200f).coerceIn(0f, 1f)
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
                        onChange((100 + f * 200).toInt())
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
        }
        Spacer(Modifier.width(10.dp))
        Text("${scale}%", style = TextStyle(color = Obsidian.text2, fontSize = 11.sp))
    }
}

// Grade dos gradientes prontos (mesma lista do web). Cada pastilha pinta o
// proprio gradiente — o que voce ve e o que salva.
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
// Clicar no emoji ja escolhido limpa.
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
                ReactionPicker(onPick = { onPick(it); open = false })
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
            "conta google sem senha — defina uma pra entrar por email tambem.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        )
    }
    Spacer(Modifier.height(12.dp))
    PasswordForm(hasPassword = me?.hasPassword != false)
}

// Aba Sobre: versao atual + auto-update (checagem manual, progresso e reinicio).
// O gate de boot ja verifica sozinho; aqui e o controle manual + fallback.
@Composable
private fun AboutSection() {
    val updater = remember { GlobalContext.get().get<UpdateService>() }
    val st by updater.state.collectAsState()
    val scope = rememberCoroutineScope()

    ReadRow("versao", updater.currentVersion)
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
        "o Astra verifica sozinho ao abrir. voce tambem pode procurar agora.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(14.dp))

    when (val s = st) {
        is UpdateState.Checking -> AboutStatus("procurando atualizacoes…")
        is UpdateState.UpToDate -> AboutStatus("voce esta na ultima versao")
        is UpdateState.Available -> {
            AboutStatus("nova versao ${s.version} disponivel")
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
            AboutStatus("${s.version} baixada — reinicie pra aplicar")
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
    AboutButton("procurar atualizacoes", accent = false) { scope.launch { updater.check(silent = false) } }
}

@Composable
private fun AboutStatus(text: String) {
    Text(text, style = TextStyle(color = Obsidian.text2, fontSize = 13.sp))
}

@Composable
private fun AboutButton(label: String, accent: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(color = if (accent) Obsidian.accent else Obsidian.text2, fontSize = 13.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (accent) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
    Text(
        if (busy) "salvando…" else "salvar",
        style = TextStyle(color = if (canSave) Obsidian.accent else Obsidian.text3, fontSize = 13.sp),
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
                        msg = "nao deu — confira a senha atual" to false
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (next.isNotEmpty() && next.length < 8) {
        Spacer(Modifier.height(6.dp))
        Text("minimo 8 caracteres", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
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

// Variante com icone Lucide no lugar do rotulo (Conta: envelope no email, pessoa no usuario).
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

// Aba Voz: qualidade da transmissao de tela (presets) + processamento do mic.
@Composable
private fun VoiceSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    Text("Transmissao de tela", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "vale ao iniciar a transmissao. o padrao 1080p60 e o minimo que combinamos.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(10.dp))
    RadioList(
        ScreenQuality.entries.map { it.label to it },
        p.screenQuality, prefs::setScreenQuality,
    )

    SettingsDivider()
    Text("Microfone", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(10.dp))
    ToggleRow("Supressao de ruido", "corta ventilador, teclado e chiado de fundo", p.micNoiseSuppression, prefs::setMicNoiseSuppression)
    ToggleRow("Cancelamento de eco", "evita o retorno do audio dos outros pelo seu mic", p.micEchoCancel, prefs::setMicEchoCancel)
    ToggleRow("Ganho automatico", "nivela o volume da sua voz sozinho", p.micAutoGain, prefs::setMicAutoGain)
    Spacer(Modifier.height(4.dp))
    Text(
        "as opcoes de microfone valem na proxima vez que voce entrar numa sala.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
}

// Lista de opcao unica (radio) — pra escolhas com rotulos longos (presets).
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
        "desliga aurora + estrelas e reduz animacoes de uma vez — pra jogar ou transmitir",
        p.performanceMode, prefs::setPerformanceMode,
    )
    Spacer(Modifier.height(6.dp))

    // Controles finos: o modo desempenho ja sobrepoe, entao esmaece quando ligado
    // (continuam clicaveis — sao a tua preferencia fora do modo desempenho).
    Column(Modifier.alpha(if (p.performanceMode) 0.45f else 1f)) {
        ToggleRow("Aurora", "fundo animado em shader", p.auroraEnabled, prefs::setAuroraEnabled)
        LabeledControl("Qualidade da aurora", "mais detalhe = mais GPU") {
            SegmentedRow(
                listOf("Alta" to AuroraQuality.HIGH, "Media" to AuroraQuality.MEDIUM, "Baixa" to AuroraQuality.LOW),
                p.auroraQuality, prefs::setAuroraQuality,
            )
        }
        ToggleRow("Estrelas", "campo de estrelas + meteoros sobre a aurora", p.starsEnabled, prefs::setStarsEnabled)
        LabeledControl("FPS das animacoes", "teto de quadros do fundo (livre segue o monitor)") {
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
private fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            // Preenche a coluna capada (~720, estilo Discord): interruptor grudado
            // na ponta direita. Quem limita a largura agora e a coluna, nao a linha.
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
    LabeledControl("Tamanho da fonte", "das mensagens no chat") {
        SegmentedRow(FontSizePref.entries.map { it.label to it }, p.fontSize, prefs::setFontSize)
    }
    LabeledControl("Densidade das mensagens", "respiro entre as mensagens") {
        SegmentedRow(DensityPref.entries.map { it.label to it }, p.density, prefs::setDensity)
    }
    Spacer(Modifier.height(20.dp))
}

// Linha separadora entre grupos de configuracao (legibilidade — pedido do dono).
@Composable
private fun SettingsDivider() {
    Spacer(Modifier.height(8.dp))
    HairRule()
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun FieldLabel(text: String) {
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

