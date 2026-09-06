package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
import app.astra.desktop.shell.Selection
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.dto.ServerDto
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.Users
import kotlinx.coroutines.delay

@Composable
internal fun Rail(
    servers: List<ServerDto>,
    selection: Selection,
    myId: String?,
    mutedServers: Set<String>,
    sussurroNaoLido: Boolean,
    canManageSelected: (String) -> Boolean,
    onOpenServerSettings: (String) -> Unit,
    onSelect: (Selection) -> Unit,
    onLeaveServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onCreateServer: (name: String) -> Unit,
    onToggleServerMute: (String) -> Unit,
    onMarkServerRead: (String) -> Unit,
    onAddMember: (serverId: String, username: String, onResult: (String?) -> Unit) -> Unit,
    onJoinInvite: (raw: String, onResult: (String?) -> Unit) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var inviteFor by remember { mutableStateOf<ServerDto?>(null) }
    Column(
        modifier = Modifier.width(LARGURA_RAIL).fillMaxHeight().panelSurface(Obsidian.void, 0.72f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier.size(72.dp).drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            Obsidian.accent.copy(alpha = 0.16f),
                            Obsidian.accent.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * 0.52f,
                    ),
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(44.dp)) {
                RailItem(
                    active = selection is Selection.Dms,
                    onClick = { onSelect(Selection.Dms) },
                    rotulo = "sussurros",
                ) {
                    Image(
                        painter = painterResource("astra-glyph.png"),
                        contentDescription = "sussurros",
                        modifier = Modifier.size(26.dp),
                    )
                }
                if (sussurroNaoLido) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Obsidian.void),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Obsidian.accent))
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        DivisoriaDaRail()
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(servers, key = { it.id }) { srv ->
                var confirmLeave by remember(srv.id) { mutableStateOf(false) }
                var confirmDelete by remember(srv.id) { mutableStateOf(false) }
                val isOwner = srv.ownerId == myId
                EditorialContextMenu(entries = {
                    buildList {
                        add(MenuEntry.Item("convidar pessoas", icon = Lucide.Users) { inviteFor = srv })
                        srv.inviteCode?.let { code ->
                            add(MenuEntry.Item("copiar link do convite", icon = Lucide.Link) {
                                clipboard.setText(AnnotatedString(inviteLink(code)))
                            })
                        }
                        add(MenuEntry.Item(if (srv.id in mutedServers) "reativar constelação" else "silenciar constelação", icon = if (srv.id in mutedServers) Lucide.Bell else Lucide.BellOff) { onToggleServerMute(srv.id) })
                        add(MenuEntry.Item("marcar tudo como lido", icon = Lucide.CheckCheck) { onMarkServerRead(srv.id) })
                        add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(srv.id)) })
                        if (isOwner || canManageSelected(srv.id)) {
                            add(MenuEntry.Separator)
                            add(MenuEntry.Item("configurações", icon = Lucide.Settings) { onOpenServerSettings(srv.id) })
                        }
                        add(MenuEntry.Separator)
                        if (isOwner) add(MenuEntry.Item("excluir constelação", danger = true, icon = Lucide.Trash2) { confirmDelete = true })
                        else add(MenuEntry.Item("sair da constelação", danger = true, icon = Lucide.LogOut) { confirmLeave = true })
                    }
                }) {
                if (confirmLeave) {
                    Popup(
                        onDismissRequest = { confirmLeave = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Obsidian.overlay)
                                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                                .padding(14.dp),
                        ) {
                            Text(
                                "sair de ${srv.name}?",
                                style = Tipo.corpo,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "ficar",
                                    style = Tipo.descricao,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp))
                                        .clickable { confirmLeave = false }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                                Text(
                                    "sair",
                                    style = Tipo.erro,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Obsidian.danger, RoundedCornerShape(7.dp))
                                        .clickable {
                                            confirmLeave = false
                                            onLeaveServer(srv.id)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                if (confirmDelete) {
                    Popup(
                        onDismissRequest = { confirmDelete = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Obsidian.overlay)
                                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                                .padding(14.dp),
                        ) {
                            Text(
                                "excluir ${srv.name}? apaga a constelação para todos — não há como desfazer.",
                                style = Tipo.corpo,
                                modifier = Modifier.widthIn(max = 240.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "cancelar",
                                    style = Tipo.descricao,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp))
                                        .clickable { confirmDelete = false }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                                Text(
                                    "excluir",
                                    style = Tipo.erro,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Obsidian.danger, RoundedCornerShape(7.dp))
                                        .clickable {
                                            confirmDelete = false
                                            onDeleteServer(srv.id)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                RailItem(
                    active = (selection as? Selection.Server)?.id == srv.id,
                    onClick = { onSelect(Selection.Server(srv.id)) },
                    rotulo = srv.name,
                ) {
                    if (!srv.iconUrl.isNullOrBlank() && !imagemMorreu(srv.iconUrl)) {
                        AsyncImage(
                            model = srv.iconUrl,
                            contentDescription = srv.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onState = { lembrarQueMorreu(srv.iconUrl, it) },
                        )
                    } else {
                        Text(
                            text = srv.name.take(1).uppercase(),
                            style = TextStyle(color = Obsidian.accent, fontSize = 17.sp, fontFamily = DmSerif),
                        )
                    }
                }
                }
            }
            item(key = "create-server") { CreateServerButton(onCreateServer, onJoinInvite) }
            item(key = "descobrir") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(4.dp))
                    DivisoriaDaRail()
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier.size(72.dp).drawBehind {
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Obsidian.accent.copy(alpha = 0.16f),
                                        Obsidian.accent.copy(alpha = 0.05f),
                                        Color.Transparent,
                                    ),
                                    center = center,
                                    radius = size.minDimension * 0.52f,
                                ),
                            )
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        RailItem(
                            active = selection is Selection.Discover,
                            onClick = { onSelect(Selection.Discover) },
                            rotulo = "descobrir",
                        ) {
                            LIcon(Lucide.Compass, tint = Obsidian.accent, size = 20.dp, rotulo = "descobrir")
                        }
                    }
                }
            }
        }
    }
    inviteFor?.let { srv ->
        InvitePeopleDialog(
            serverName = srv.name,
            inviteCode = srv.inviteCode,
            onAdd = { username, onResult -> onAddMember(srv.id, username, onResult) },
            onClose = { inviteFor = null },
        )
    }
}

@Composable
private fun CreateServerButton(
    onCreateServer: (name: String) -> Unit,
    onJoinInvite: (raw: String, onResult: (String?) -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var joinOpen by remember { mutableStateOf(false) }
    var criando by remember { mutableStateOf(false) }
    Box {
        RailItem(
            active = false,
            onClick = { menuOpen = true },
            rotulo = if (menuOpen) null else "adicionar",
        ) {
            Text("+", style = TextStyle(color = Obsidian.accent, fontSize = 22.sp))
        }
        if (menuOpen) {
            Popup(
                popupPositionProvider = RailMenuBeside,
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .popupReveal()
                        .widthIn(min = 170.dp, max = 230.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                ) {
                    CreateMenuRow(glyph = "✦", label = "criar constelação") { menuOpen = false; criando = true }
                    CreateMenuRow(icon = Lucide.Link, label = "entrar com convite") { menuOpen = false; joinOpen = true }
                }
            }
        }
    }
    if (joinOpen) {
        JoinByInviteDialog(onJoin = onJoinInvite, onClose = { joinOpen = false })
    }
    if (criando) {
        EditorialInputDialog(
            title = "nova constelação",
            placeholder = "nome da constelação",
            initial = "",
            confirmLabel = "criar",
            channelType = false,
            onDismiss = { criando = false },
            onConfirm = { name, _ -> onCreateServer(name) },
        )
    }
}

@Composable
private fun CreateMenuRow(
    label: String,
    glyph: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) Obsidian.hover else Color.Transparent, tween(100))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            glyph != null -> Text(glyph, style = TextStyle(color = Obsidian.accent, fontSize = 14.sp))
            icon != null -> LIcon(icon, tint = Obsidian.accent, size = 14.dp)
        }
        Spacer(Modifier.width(9.dp))
        Text(label, style = TextStyle(color = if (hovered) Obsidian.text1 else Obsidian.text2, fontSize = 13.sp))
    }
}

private object RailMenuBeside : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = (anchorBounds.right + 8).coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0),
        y = anchorBounds.top.coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0),
    )
}

@Composable
private fun DivisoriaDaRail() {
    Box(Modifier.width(24.dp).height(1.dp).background(Obsidian.borderDim.copy(alpha = 0.6f)))
}

private const val ATRASO_DO_BALAO_MS = 90L
private const val ENTRADA_DO_BALAO_MS = 120

private class BalaoDaRail(private val margem: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = (anchorBounds.right + margem).coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0),
        y = (anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2)
            .coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0),
    )
}

@Composable
private fun BalaoDoNome(nome: String) {
    val reduzir = LocalReduceMotion.current
    val entrada = remember { Animatable(if (reduzir) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduzir) entrada.animateTo(1f, tween(ENTRADA_DO_BALAO_MS, easing = EaseOutStd))
    }
    val desliza = with(LocalDensity.current) { 6.dp.toPx() }
    val forma = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer {
            alpha = entrada.value
            translationX = -(1f - entrada.value) * desliza
        },
    ) {
        Canvas(Modifier.size(width = 5.dp, height = 10.dp)) {
            drawPath(
                Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(0f, size.height / 2f)
                    lineTo(size.width, size.height)
                    close()
                },
                Obsidian.overlay,
            )
        }
        Text(
            nome,
            style = TextStyle(color = Obsidian.text1, fontSize = 12.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .clip(forma)
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderDim, forma)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun RailItem(
    active: Boolean,
    onClick: () -> Unit,
    rotulo: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var balaoAberto by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        if (!hovered) {
            balaoAberto = false
        } else {
            delay(ATRASO_DO_BALAO_MS)
            balaoAberto = true
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val bg by animateColorAsState(
        when {
            active -> Obsidian.overlay
            hovered -> Obsidian.hover
            else -> Obsidian.raised
        },
        tween(140),
    )
    val borderColor by animateColorAsState(
        when {
            active -> Obsidian.accent.copy(alpha = 0.55f)
            hovered -> Obsidian.borderMid
            else -> Obsidian.borderDim
        },
        tween(140),
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickScale(interaction)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (rotulo != null && balaoAberto) {
            val margem = with(LocalDensity.current) { 10.dp.roundToPx() }
            Popup(popupPositionProvider = remember(margem) { BalaoDaRail(margem) }) {
                BalaoDoNome(rotulo)
            }
        }
    }
}

@Composable
private fun BotaoDaFaixa(
    icone: ImageVector,
    rotulo: String,
    aceso: Boolean = false,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val forma = RoundedCornerShape(8.dp)
    val fundo by animateColorAsState(
        when {
            aceso -> Obsidian.accent.copy(alpha = 0.14f)
            hov -> Obsidian.hover
            else -> Color.Transparent
        },
        tween(120),
    )
    val cor by animateColorAsState(
        when {
            aceso -> Obsidian.accent
            hov -> Obsidian.text1
            else -> Obsidian.text3
        },
        tween(120),
    )
    Box(
        Modifier
            .size(30.dp)
            .clickScale(src, pressedScale = 0.92f, formaDoFoco = forma)
            .clip(forma)
            .background(fundo)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icone, tint = cor, size = 15.dp, rotulo = rotulo)
    }
}

@Composable
internal fun FaixaDaConstelacao(
    nome: String,
    membros: Int,
    online: Int,
    membrosAbertos: Boolean,
    onToggleMembros: () -> Unit,
    onConvidar: () -> Unit,
    podeConfigurar: Boolean,
    onAbrirConfig: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                nome,
                style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Obsidian.success))
                Spacer(Modifier.width(5.dp))
                Text(
                    "$online/$membros " + (if (membros == 1) "membro" else "membros") + " online",
                    style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        AcoesDaFaixa(
            membrosAbertos = membrosAbertos,
            podeConfigurar = podeConfigurar,
            onConvidar = onConvidar,
            onToggleMembros = onToggleMembros,
            onAbrirConfig = onAbrirConfig,
        )
    }
}

@Composable
private fun AcoesDaFaixa(
    membrosAbertos: Boolean,
    podeConfigurar: Boolean,
    onConvidar: () -> Unit,
    onToggleMembros: () -> Unit,
    onAbrirConfig: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BotaoDaFaixa(Lucide.UserPlus, rotulo = "convidar pessoas", onClick = onConvidar)
        Spacer(Modifier.width(2.dp))
        BotaoDaFaixa(
            Lucide.Users,
            rotulo = if (membrosAbertos) "ocultar membros" else "mostrar membros",
            aceso = membrosAbertos,
            onClick = onToggleMembros,
        )
        if (podeConfigurar) {
            Spacer(Modifier.width(2.dp))
            BotaoDaFaixa(Lucide.Settings, rotulo = "configurações da constelação", onClick = onAbrirConfig)
        }
    }
}

@Composable
internal fun ServerHeaderBanner(srv: ServerDto) {
    val forma = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .aspectRatio(ServerBannerAspect)
            .clip(forma)
            .border(1.dp, Obsidian.borderMid.copy(alpha = 0.6f), forma),
    ) {
        if (!srv.bannerUrl.isNullOrBlank()) {
            ProfileBanner(
                css = null,
                imageUrl = srv.bannerUrl,
                positionY = srv.bannerPositionY,
                scale = srv.bannerScale,
                fallback = Obsidian.overlay,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Obsidian.overlay, Obsidian.raised))))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    1f to Obsidian.void.copy(alpha = 0.85f),
                ),
            ),
        )
    }
}
