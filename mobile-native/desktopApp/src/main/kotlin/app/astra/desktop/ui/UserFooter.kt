package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Headphones
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.MicOff
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.User
import app.astra.desktop.xp.XpStore
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.SetStatusRequest
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import app.astra.desktop.ui.theme.Tipo
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.astra.desktop.voice.LeituraDoCaminho

private val UserPalette = listOf(
    Color(0xFFC9A96E), Color(0xFF7C6FC4), Color(0xFF6FA8C9), Color(0xFFC97C6E), Color(0xFF6EC98A),
)

fun userColor(id: String): Color {
    var h = 0
    for (c in id) h = (h * 31 + c.code) and 0x7FFFFFFF
    return UserPalette[h % UserPalette.size]
}

private fun statusLabel(status: UserStatus) = when (status) {
    UserStatus.ONLINE -> "brilhando"
    UserStatus.IDLE -> "ausente"
    UserStatus.DND -> "não perturbe"
    UserStatus.INVISIBLE, UserStatus.OFFLINE -> "invisivel"
}

internal object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = (anchorBounds.left).coerceAtMost(windowSize.width - popupContentSize.width),
        y = (anchorBounds.top - popupContentSize.height - 8).coerceAtLeast(0),
    )
}

@Composable
fun UserFooter(
    me: ProfileUserDto?,
    fallbackName: String,
    onEdited: () -> Unit,
    onOpenSettings: (SettingsTab) -> Unit,
    onAbrirJornada: () -> Unit,
    onLogout: () -> Unit,
    mudo: Boolean,
    ensurdecido: Boolean,
    onAlternarMudo: () -> Unit,
    onAlternarEnsurdecer: () -> Unit,
    caminho: LeituraDoCaminho? = null,
    modifier: Modifier = Modifier,
) {
    val name = me?.displayName ?: me?.username ?: fallbackName
    val status = userStatus(me?.effectiveStatus)
    val xpStore = remember { GlobalContext.get().get<XpStore>() }
    val progresso by xpStore.progresso.collectAsState()
    val visualXp = rememberVisualDeXp(xpStore)
    val hoverCartao = remember { MutableInteractionSource() }
    val cartaoSobHover by hoverCartao.collectIsHoveredAsState()
    var profileOpen by remember { mutableStateOf(false) }
    var statusOpen by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val pickStatus: (UserStatus) -> Unit = { picked ->
        statusOpen = false
        scope.launch {
            runCatching {
                GlobalContext.get().get<UserApi>().setStatus(SetStatusRequest(picked.name))
            }.onSuccess { onEdited() }
        }
    }

    EditorialContextMenu(modifier = modifier, entries = {
        buildList {
            add(MenuEntry.Item("definir status", icon = Lucide.CircleDot) { statusOpen = true })
            add(MenuEntry.Item("abrir perfil", icon = Lucide.User) { profileOpen = true })
            me?.let { add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(it.id)) }) }
            add(MenuEntry.Item("configurações", icon = Lucide.Settings) { onOpenSettings(SettingsTab.ACCOUNT) })
            add(MenuEntry.Separator)
            add(MenuEntry.Item("sair", danger = true, icon = Lucide.LogOut) { confirmLogout = true })
        }
    }) {
    val forma = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 7.dp, vertical = 7.dp)
            .clip(forma)
            .background(Obsidian.void.copy(alpha = 0.72f))
            .border(1.dp, Obsidian.borderMid.copy(alpha = 0.65f), forma)
            .hoverable(hoverCartao)
            .onGloballyPositioned { PisoDoPet.caixa = it.boundsInWindow() }
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.anelDeXp(
                fracao = visualXp.fracao,
                aceso = visualXp.aceso,
                varredura = visualXp.varredura,
                cor = corDoAnel,
                trilho = trilhoDoAnel,
            ),
        ) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAbrirJornada),
            ) {
                DesktopAvatar(me?.avatarUrl, name, 30)
                MoedasDeXp(xpStore)
            }
            StatusDot(
                status = status,
                size = 11.dp,
                bordered = true,
                borderColor = Obsidian.void,
                cutoutColor = Obsidian.void,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
            if (profileOpen && me != null) {
                Popup(
                    popupPositionProvider = AboveAnchor,
                    onDismissRequest = { profileOpen = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    PopupReveal(originX = 0.5f, originY = 1f) {
                        ProfileCard(me, onEdited = onEdited, onClose = { profileOpen = false })
                    }
                }
            }
        }
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            Column(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { statusOpen = true },
            ) {
                Text(
                    text = name,
                    style = Tipo.corpo,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                AnimatedContent(
                    targetState = cartaoSobHover,
                    transitionSpec = {
                        val entra = if (targetState) 1 else -1
                        (slideInVertically(tween(200, easing = EaseOutStd)) { h -> entra * h } +
                            fadeIn(tween(150))) togetherWith
                            (slideOutVertically(tween(200, easing = EaseOutStd)) { h -> -entra * h } +
                                fadeOut(tween(110))) using SizeTransform(clip = false)
                    },
                    label = "statusOuNivel",
                ) { emHover ->
                    Text(
                        if (emHover) "nível ${progresso.nivel} · ${progresso.noNivel}/${progresso.paraOProximo}"
                        else statusLabel(status),
                        style = TextStyle(
                            color = if (emHover) Obsidian.text2 else Obsidian.text3,
                            fontSize = 11.sp,
                        ),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (statusOpen) {
                Popup(
                    popupPositionProvider = AboveAnchor,
                    onDismissRequest = { statusOpen = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    PopupReveal(originX = 0f, originY = 1f) {
                        StatusMenu(current = status, onPick = pickStatus)
                    }
                }
            }
        }
        if (caminho != null) {
            SinalDaChamada(caminho)
            Spacer(Modifier.width(4.dp))
        }
        FooterIcon(
            icon = if (mudo) Lucide.MicOff else Lucide.Mic,
            rotulo = if (mudo) "voltar a falar" else "ficar mudo",
            danger = false,
            aceso = mudo,
            onClick = onAlternarMudo,
        )
        Spacer(Modifier.width(2.dp))
        FooterIcon(
            icon = Lucide.Headphones,
            rotulo = if (ensurdecido) "voltar a ouvir" else "ensurdecer",
            danger = false,
            aceso = ensurdecido,
            riscado = ensurdecido,
            onClick = onAlternarEnsurdecer,
        )
        Spacer(Modifier.width(2.dp))
        FooterIcon(Lucide.Settings, rotulo = "configurações", danger = false, onClick = { onOpenSettings(SettingsTab.ACCOUNT) })
        Spacer(Modifier.width(2.dp))
        FooterIcon(Lucide.LogOut, rotulo = "sair da conta", danger = true, onClick = { confirmLogout = true })
    }
    if (confirmLogout) CenteredConfirmDialog(
        message = "sair da conta?",
        confirmLabel = "sair",
        onConfirm = onLogout,
        onDismiss = { confirmLogout = false },
    )
    }
}

@Composable
private fun StatusMenu(current: UserStatus, onPick: (UserStatus) -> Unit) {
    Column(
        Modifier
            .width(184.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
            .padding(vertical = 5.dp),
    ) {
        listOf(UserStatus.ONLINE, UserStatus.IDLE, UserStatus.DND, UserStatus.INVISIBLE).forEach { s ->
            val hov = remember { MutableInteractionSource() }
            val h by hov.collectIsHoveredAsState()
            val bg = if (h) Obsidian.hover else Obsidian.raised
            Row(
                Modifier
                    .fillMaxWidth()
                    .hoverable(hov)
                    .background(bg)
                    .clickable { onPick(s) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(status = s, size = 11.dp, cutoutColor = bg)
                Spacer(Modifier.width(10.dp))
                Text(
                    statusLabel(s),
                    style = TextStyle(
                        color = if (s == current) Obsidian.accent else Obsidian.text2,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FooterIcon(
    icon: ImageVector,
    rotulo: String,
    danger: Boolean,
    onClick: () -> Unit,
    aceso: Boolean = false,
    riscado: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color by animateColorAsState(
        when {
            aceso -> Obsidian.danger
            hovered && danger -> Obsidian.danger
            hovered -> Obsidian.text1
            else -> Obsidian.text3
        },
        tween(120),
    )
    val fundo by animateColorAsState(
        when {
            aceso -> Obsidian.danger.copy(alpha = if (hovered) 0.22f else 0.14f)
            hovered -> Obsidian.hover
            else -> Color.Transparent
        },
        tween(120),
    )
    Box(
        Modifier
            .size(26.dp)
            .clickScale(interaction, pressedScale = 0.90f, formaDoFoco = RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(fundo)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(
            icon,
            tint = color,
            size = 15.dp,
            rotulo = rotulo,
            modifier = if (!riscado) Modifier else Modifier.drawWithContent {
                drawContent()
                val m = size.minDimension
                drawLine(
                    color = color,
                    start = Offset(m * 0.12f, m * 0.12f),
                    end = Offset(m * 0.88f, m * 0.88f),
                    strokeWidth = m * 0.11f,
                    cap = StrokeCap.Round,
                )
            },
        )
    }
}

@Composable
private fun ProfileCard(me: ProfileUserDto, onEdited: () -> Unit, onClose: () -> Unit) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var nameField by remember { mutableStateOf(me.displayName ?: "") }
    var pronounsField by remember { mutableStateOf(me.pronouns ?: "") }
    var bioField by remember { mutableStateOf(me.bio ?: "") }

    Column(
        Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
    ) {
        val bannerColor = me.bannerColor?.removePrefix("#")?.toLongOrNull(16)
            ?.let { Color(0xFF000000 or it) } ?: Obsidian.overlay
        Box(Modifier.fillMaxWidth().height(124.dp).background(bannerColor)) {
            if (!me.bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = me.bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Box(
                Modifier.offset(y = (-20).dp),
            ) {
                DesktopAvatar(me.avatarUrl, me.displayName ?: me.username, 56)
            }
            Column(Modifier.offset(y = (-8).dp)) {
                if (!editing) {
                    Text(
                        me.displayName ?: me.username,
                        style = TextStyle(
                            color = Obsidian.text1, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                            fontFamily = profileFontFamily(me.displayFont),
                        ),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "@${me.username}",
                            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                        )
                        if (!me.pronouns.isNullOrBlank()) {
                            Text(
                                "  ·  ${me.pronouns}",
                                style = Tipo.apoio,
                            )
                        }
                    }
                    if (!me.customStatus.isNullOrBlank() || !me.statusEmoji.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            listOfNotNull(me.statusEmoji, me.customStatus).joinToString(" "),
                            style = Tipo.rotulo,
                        )
                    }
                    if (!me.bio.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        CartaoInterno(fundo = Obsidian.hover, padding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)) {
                            Text(
                                me.bio.orEmpty(),
                                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        CardButton("editar perfil", accent = true) { editing = true }
                    }
                } else {
                    EditField("nome", nameField, single = true) { nameField = it }
                    Spacer(Modifier.height(8.dp))
                    EditField("pronomes", pronounsField, single = true) { pronounsField = it }
                    Spacer(Modifier.height(8.dp))
                    EditField("bio", bioField, single = false) { bioField = it }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        CardButton("cancelar", accent = false, enabled = !saving) { editing = false }
                        CardButton(if (saving) "salvando…" else "salvar", accent = true, enabled = !saving) {
                            saving = true
                            scope.launch {
                                runCatching {
                                    koin.get<UserApi>().updateProfile(
                                        UpdateProfileRequest(
                                            displayName = nameField.trim().ifBlank { null },
                                            pronouns = pronounsField.trim(),
                                            bio = bioField.trim(),
                                        ),
                                    )
                                }.onSuccess {
                                    onEdited()
                                    onClose()
                                }
                                saving = false
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun CardButton(label: String, accent: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(
            color = if (accent) Obsidian.accent else Obsidian.text3,
            fontSize = 12.sp,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, if (accent) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun EditField(label: String, value: String, single: Boolean, onChange: (String) -> Unit) {
    Column {
        Text(label, style = Tipo.nota)
        Spacer(Modifier.height(3.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = single,
            maxLines = if (single) 1 else 4,
            textStyle = Tipo.corpo,
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.base)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

private fun forcaDoSinal(c: LeituraDoCaminho): Int = when {
    c.perda >= 8 || c.ida >= 300 || c.tremor >= 60 -> 1
    c.perda >= 3 || c.ida >= 150 || c.tremor >= 30 -> 2
    else -> 3
}

@Composable
private fun SinalDaChamada(caminho: LeituraDoCaminho) {
    val forca = forcaDoSinal(caminho)
    val cor = when (forca) {
        1 -> Obsidian.danger
        2 -> Obsidian.warning
        else -> Obsidian.text3
    }
    val comoEsta = when (forca) {
        1 -> "chamada instável"
        2 -> "chamada oscilando"
        else -> "chamada estável"
    }
    val detalhe = "$comoEsta — ida e volta ${caminho.ida} ms, tremor ${caminho.tremor} ms, perda ${caminho.perda}%"

    val interacao = remember { MutableInteractionSource() }
    val sobHover by interacao.collectIsHoveredAsState()

    Box(contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .height(26.dp)
                .padding(horizontal = 5.dp)
                .hoverable(interacao)
                .semantics { contentDescription = detalhe }
                .wrapContentHeight(Alignment.CenterVertically),
        ) {
            for (barra in 1..3) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height((4 + barra * 3).dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (barra <= forca) cor else Obsidian.borderDim),
                )
            }
        }
        if (sobHover) {
            Popup(popupPositionProvider = AboveAnchor) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(comoEsta, style = Tipo.rotulo)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "ida e volta ${caminho.ida} ms · tremor ${caminho.tremor} ms · perda ${caminho.perda}%",
                        style = Tipo.nota,
                    )
                }
            }
        }
    }
}
