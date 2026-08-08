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
import androidx.compose.runtime.collectAsState
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
import com.composables.icons.lucide.LogOut
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

// Rodape do usuário (F2 da fase de design) — estrutura do UserFooter do web:
// avatar com anel na cor do usuário + StatusDot, nome + status, engrenagem e
// sair. Clicar no avatar abre o card de perfil (com editar), como no mobile.

// Mesma paleta/hash do web (userColor): cor fixa por usuário.
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
    onOpenSettings: (SettingsTab) -> Unit,
    onAbrirJornada: () -> Unit,
    onLogout: () -> Unit,
) {
    val name = me?.displayName ?: me?.username ?: fallbackName
    val status = userStatus(me?.effectiveStatus)
    // Progressao: o anel em volta do avatar e o numero no lugar do status quando o
    // mouse passa. O `progresso` e lido na composicao (muda no maximo 1x por
    // minuto, e so pra trocar o TEXTO); a barra em si e animada na fase de desenho
    // pelo VisualDeXp, entao ganhar XP nao recompoe a barra lateral.
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

    // Escolher status: PATCH /api/profile/status (o backend já aceitava
    // ONLINE|IDLE|DND|INVISIBLE e atualiza a presenca no Redis) e recarrega o
    // perfil pro rodape refletir na hora.
    val pickStatus: (UserStatus) -> Unit = { picked ->
        statusOpen = false
        scope.launch {
            runCatching {
                GlobalContext.get().get<UserApi>().setStatus(SetStatusRequest(picked.name))
            }.onSuccess { onEdited() }
        }
    }

    // Botao direito no rodape: definir status / abrir perfil / copiar ID / configurações / sair.
    EditorialContextMenu(entries = {
        buildList {
            add(MenuEntry.Item("definir status", icon = Lucide.CircleDot) { statusOpen = true })
            add(MenuEntry.Item("abrir perfil", icon = Lucide.User) { profileOpen = true })
            me?.let { add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(it.id)) }) }
            add(MenuEntry.Item("configurações", icon = Lucide.Settings) { onOpenSettings(SettingsTab.ACCOUNT) })
            add(MenuEntry.Separator)
            add(MenuEntry.Item("sair", danger = true, icon = Lucide.LogOut) { confirmLogout = true })
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
            .hoverable(hoverCartao)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // O anel vive AQUI, no Box de fora: o de dentro tem .clip(CircleShape) e
        // cortaria tudo que e desenhado pra fora da foto. Ele passa do avatar em
        // ~3.5dp de cada lado, que cabem na folga vertical de 9dp do cartao.
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
                    // Clicar na foto abre SUA JORNADA — nível, missões e conquistas —
                    // e não mais a aba Perfil das configurações.
                    //
                    // A troca é do dono, e a razão é boa: a foto com o anel de XP em
                    // volta promete progresso, não formulário. Quem clica ali quer
                    // saber onde chegou; quem quer editar avatar e banner vai pela
                    // engrenagem, que está a três centímetros de distância.
                    .clickable(onClick = onAbrirJornada),
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
                    PopupReveal(originX = 0.5f, originY = 1f) {
                        ProfileCard(me, onEdited = onEdited, onClose = { profileOpen = false })
                    }
                }
            }
        }
        Spacer(Modifier.width(9.dp))
        // Clicar no nome/status abre o seletor de status (igual Discord). Antes não
        // havia NENHUM jeito de sair do "brilhando" no desktop.
        Box(Modifier.weight(1f)) {
            Column(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { statusOpen = true },
            ) {
                Text(
                    text = name,
                    style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // A linha de baixo tem dois papeis. Em repouso e o status ("brilhando");
                // com o mouse em cima vira o numero do nivel. Escolher assim em vez de
                // mostrar os dois evita crescer o cartao — e ninguem precisa do XP o
                // tempo todo, so quando quer saber.
                // Desliza em vez de dissolver: o status sai por cima e o nível entra
                // por baixo, como um marcador girando. Dissolução lê como "a tela
                // piscou"; movimento lê como troca de conteúdo — e a altura do cartão
                // não muda, então a barra lateral não reflui.
                AnimatedContent(
                    targetState = cartaoSobHover,
                    transitionSpec = {
                        val entra = if (targetState) 1 else -1   // hover: sobe; saindo: desce
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
        FooterIcon(Lucide.Settings, danger = false, onClick = { onOpenSettings(SettingsTab.ACCOUNT) })
        Spacer(Modifier.width(2.dp))
        FooterIcon(Lucide.LogOut, danger = true, onClick = { confirmLogout = true })
    }
    if (confirmLogout) CenteredConfirmDialog(
        message = "sair da conta?",
        confirmLabel = "sair",
        onConfirm = onLogout,
        onDismiss = { confirmLogout = false },
    )
    }
}

// Seletor de status. So os quatro que o backend aceita (profile.ts: StatusSchema)
// — OFFLINE não entra porque não e escolha, e sim ausencia de presenca.
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
                // cutoutColor acompanha o fundo: a "mordida" da lua (ausente) e a
                // barra do não-perturbe sao recortes, não pintura.
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
    val fundo by animateColorAsState(if (hovered) Obsidian.hover else Color.Transparent, tween(120))
    Box(
        Modifier
            .size(26.dp)
            // 0.90 e nao 0.96: em alvo de 26dp, 4% de encolhimento sao meio pixel.
            // A forma do anel acompanha o clip do proprio botao — anel de 8dp em
            // volta de um canto de 6dp desenha duas curvas diferentes.
            .clickScale(interaction, pressedScale = 0.90f, formaDoFoco = RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(fundo)
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
                // Sem anel por enquanto (decoracoes de perfil tipo Discord virao depois).
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
