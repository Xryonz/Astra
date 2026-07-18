package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
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
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Settings
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

// Rodape do usuario (F2 da fase de design) — estrutura do UserFooter do web:
// avatar com anel na cor do usuario + StatusDot, nome + status, engrenagem e
// sair. Clicar no avatar abre o card de perfil (com editar), como no mobile.

// Mesma paleta/hash do web (userColor): cor fixa por usuario.
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
    UserStatus.DND -> "nao perturbe"
    UserStatus.INVISIBLE, UserStatus.OFFLINE -> "invisivel"
}

// Card abre ACIMA do rodape, encostado na esquerda da ancora.
private object AboveAnchor : PopupPositionProvider {
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
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    val name = me?.displayName ?: me?.username ?: fallbackName
    val status = userStatus(me?.effectiveStatus)
    var profileOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // Botao direito no rodape: abrir perfil / copiar ID / configuracoes / sair.
    // "definir status" fica pra fatia do submenu generico + API de status.
    EditorialContextMenu(entries = {
        buildList {
            add(MenuEntry.Item("abrir perfil") { profileOpen = true })
            me?.let { add(MenuEntry.Item("copiar ID") { clipboard.setText(AnnotatedString(it.id)) }) }
            add(MenuEntry.Item("configuracoes") { onOpenSettings() })
            add(MenuEntry.Separator)
            add(MenuEntry.Item("sair", danger = true) { onLogout() })
        }
    }) {
    // Cartao flutuante estilo Discord: inset das bordas da sidebar, cantos
    // arredondados e borda fina — parece sobreposto ao painel, com a aurora
    // vazando por baixo (translucido).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.void.copy(alpha = 0.46f))
            .border(1.dp, Obsidian.borderMid.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            val ring = if (me != null) userColor(me.id) else Obsidian.borderMid
            Box(
                Modifier
                    .clip(CircleShape)
                    .border(2.dp, ring, CircleShape)
                    .padding(2.dp)
                    .clickable(onClick = { profileOpen = true }),
            ) {
                DesktopAvatar(me?.avatarUrl, name, 30)
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
                    ProfileCard(me, onEdited = onEdited, onClose = { profileOpen = false })
                }
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(statusLabel(status), style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
        }
        FooterIcon(Lucide.Settings, danger = false, onClick = onOpenSettings)
        Spacer(Modifier.width(2.dp))
        FooterIcon(Lucide.LogOut, danger = true, onClick = onLogout)
    }
    }
}

@Composable
private fun FooterIcon(icon: ImageVector, danger: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color by animateColorAsState(
        when {
            hovered && danger -> Obsidian.danger
            hovered -> Obsidian.text1
            else -> Obsidian.text3
        },
        tween(120),
    )
    Box(
        Modifier
            .size(26.dp)
            .clickScale(interaction)
            .clip(RoundedCornerShape(6.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, tint = color, size = 15.dp)
    }
}

// Card de perfil (ver + editar nome/pronomes/bio). Avatar/banner por upload
// ficam pra fatia do perfil completo.
@Composable
private fun ProfileCard(me: ProfileUserDto, onEdited: () -> Unit, onClose: () -> Unit) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var nameField by remember { mutableStateOf(me.displayName ?: "") }
    var pronounsField by remember { mutableStateOf(me.pronouns ?: "") }
    var bioField by remember { mutableStateOf(me.bio ?: "") }
    val ring = userColor(me.id)

    Column(
        Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
    ) {
        // Banner: imagem > cor > accent apagado.
        val bannerColor = me.bannerColor?.removePrefix("#")?.toLongOrNull(16)
            ?.let { Color(0xFF000000 or it) } ?: Obsidian.overlay
        Box(Modifier.fillMaxWidth().height(80.dp).background(bannerColor)) {
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
                Modifier
                    .offset(y = (-26).dp)
                    .clip(CircleShape)
                    .background(Obsidian.raised)
                    .border(3.dp, ring, CircleShape)
                    .padding(3.dp),
            ) {
                DesktopAvatar(me.avatarUrl, me.displayName ?: me.username, 52)
            }
            Column(Modifier.offset(y = (-14).dp)) {
                if (!editing) {
                    Text(
                        me.displayName ?: me.username,
                        style = TextStyle(color = Obsidian.text1, fontSize = 18.sp, fontFamily = DmSerif),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "@${me.username}",
                            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                        )
                        if (!me.pronouns.isNullOrBlank()) {
                            Text(
                                "  ·  ${me.pronouns}",
                                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                            )
                        }
                    }
                    if (!me.customStatus.isNullOrBlank() || !me.statusEmoji.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            listOfNotNull(me.statusEmoji, me.customStatus).joinToString(" "),
                            style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                        )
                    }
                    if (!me.bio.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        HairRule()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            me.bio.orEmpty(),
                            style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
                        )
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
                Spacer(Modifier.height(4.dp))
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
        Text(label, style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
        Spacer(Modifier.height(3.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = single,
            maxLines = if (single) 1 else 4,
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
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
