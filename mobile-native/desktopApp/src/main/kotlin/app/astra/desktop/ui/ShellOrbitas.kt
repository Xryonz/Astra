package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
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
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.Selection
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ChannelVisibilityDto
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.RoleDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.BotOff
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Volume2
import kotlin.math.roundToInt

@Composable
internal fun Sidebar(
    selection: Selection,
    servers: List<ServerDto>,
    dms: List<ConversationDto>,
    activeChatId: String?,
    unread: Set<String>,
    unreadCounts: Map<String, Int>,
    dmTyping: Set<String>,
    dmPresence: Map<String, String>,
    loading: Boolean,
    members: List<ServerMemberDto>,
    voicePresence: Map<String, List<String>>,
    memberPresence: Map<String, String>,
    myId: String?,
    myVoiceChannelId: String?,
    onConvidar: (ServerDto) -> Unit,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    onToggleMute: (ConversationDto) -> Unit,
    onMarkRead: (String) -> Unit,
    onCloseDm: (String) -> Unit,
    friendsOpen: Boolean,
    onOpenFriends: () -> Unit,
    onCreateChannel: (serverId: String, name: String, type: String, categoryId: String?) -> Unit,
    onCreateCategory: (serverId: String, name: String) -> Unit,
    onRenameCategory: (serverId: String, categoryId: String, name: String) -> Unit,
    onDeleteCategory: (serverId: String, categoryId: String) -> Unit,
    onReorderChannels: (serverId: String, orderedIds: List<String>) -> Unit,
    onMoveChannelToCategory: (serverId: String, channelId: String, categoryId: String) -> Unit,
    onReorderCategories: (serverId: String, orderedIds: List<String>) -> Unit,
    onRenameChannel: (serverId: String, channelId: String, name: String) -> Unit,
    onDeleteChannel: (serverId: String, channelId: String) -> Unit,
    onMarkChannelRead: (channelId: String) -> Unit,
    silenciada: (channelId: String) -> Boolean,
    onToggleChannelMute: (channelId: String) -> Unit,
    onToggleChannelBot: (serverId: String, channelId: String, ligar: Boolean) -> Unit,
    onToggleChannelKeepBot: (serverId: String, channelId: String, guardar: Boolean) -> Unit,
    onToggleCatBot: (serverId: String, categoryId: String, ligar: Boolean) -> Unit,
    membersOpen: Boolean,
    onToggleMembers: () -> Unit,
    canManageSelected: (String) -> Boolean,
    podeGerenciarOrbitas: (String) -> Boolean,
    onOpenServerSettings: (String) -> Unit,
    visibilidade: QuemVeAOrbita,
    firstSteps: (@Composable () -> Unit)? = null,
) {
    var chanDialog by remember { mutableStateOf<ChanDialog?>(null) }
    Column(Modifier.width(LARGURA_SIDEBAR).fillMaxHeight().panelSurface(Obsidian.base, 0.62f)) {
        AnimatedContent(
            targetState = selection,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { -it / 12 })
                    .togetherWith(fadeOut(tween(120)))
            },
            modifier = Modifier.weight(1f),
        ) { sel ->
            val srv = (sel as? Selection.Server)?.let { s -> servers.find { it.id == s.id } }
            Column(Modifier.fillMaxSize()) {
                val header = @Composable {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            text = when {
                                sel is Selection.Dms -> "Sussurros"
                                sel is Selection.Discover -> "Descobrir"
                                else -> srv?.name ?: ""
                            },
                            style = TextStyle(
                                color = Obsidian.text1, fontSize = 16.sp,
                                fontFamily = DmSerif,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (srv != null) {
                    val isOwnerHere = srv.ownerId == myId
                    val podeMexerNasOrbitas = isOwnerHere || podeGerenciarOrbitas(srv.id)
                    EditorialContextMenu(entries = {
                        buildList {
                            add(MenuEntry.Item("marcar tudo como lido", icon = Lucide.CheckCheck) {
                                srv.channels.forEach { if (it.id in unread) onMarkChannelRead(it.id) }
                            })
                            if (podeMexerNasOrbitas) {
                                add(MenuEntry.Separator)
                                add(MenuEntry.Item("criar órbita", icon = Lucide.Plus) { chanDialog = ChanDialog.NewChannel(srv.id, null) })
                                add(MenuEntry.Item("criar categoria", icon = Lucide.FolderPlus) { chanDialog = ChanDialog.NewCategory(srv.id) })
                            }
                        }
                    }) { ServerHeaderBanner(srv) }
                    FaixaDaConstelacao(
                        nome = srv.name,
                        membros = members.size,
                        online = members.count { it.userId == myId || memberPresence[it.userId]?.let { p -> p != "OFFLINE" } == true },
                        membrosAbertos = membersOpen,
                        onToggleMembros = onToggleMembers,
                        onConvidar = { onConvidar(srv) },
                        podeConfigurar = isOwnerHere || canManageSelected(srv.id),
                        onAbrirConfig = { onOpenServerSettings(srv.id) },
                    )
                } else {
                    header()
                }

                Box(Modifier.weight(1f)) {
                    when {
                        loading -> SidebarSkeleton()
                        sel is Selection.Dms -> Column(Modifier.fillMaxSize()) {
                            FriendsNavRow(active = friendsOpen, onClick = onOpenFriends)
                            DmList(dms, servers, onToggleMute, onMarkRead, onCloseDm, activeChatId, unread, dmTyping, dmPresence, onOpenChat)
                        }
                        sel is Selection.Discover -> DiscoverSidebarMap()
                        else -> {
                            val orbits: @Composable () -> Unit = {
                                OrbitList(
                                    srv, activeChatId, unread, unreadCounts, members, voicePresence, myId, myVoiceChannelId,
                                    onOpenChat, onOpenVoice,
                                    podeGerenciarOrbitas = podeGerenciarOrbitas,
                                    onNewChannelInCat = { catId -> srv?.let { chanDialog = ChanDialog.NewChannel(it.id, catId) } },
                                    onRenameCat = { catId, cur -> srv?.let { chanDialog = ChanDialog.RenameCategory(it.id, catId, cur) } },
                                    onDeleteCat = { catId -> srv?.let { onDeleteCategory(it.id, catId) } },
                                    onReorderChannels = { ids -> srv?.let { onReorderChannels(it.id, ids) } },
                                    onMoveToCategory = { cid, catId -> srv?.let { onMoveChannelToCategory(it.id, cid, catId) } },
                                    onReorderCategories = { ids -> srv?.let { onReorderCategories(it.id, ids) } },
                                    onOpenChannelRename = { cid, cur -> srv?.let { chanDialog = ChanDialog.RenameChannel(it.id, cid, cur) } },
                                    onOpenChannelVisibility = { cid, name -> srv?.let { chanDialog = ChanDialog.Visibilidade(it.id, cid, name) } },
                                    onExcluirCanal = { cid -> srv?.let { onDeleteChannel(it.id, cid) } },
                                    onMarkChannelRead = onMarkChannelRead,
                                    silenciada = silenciada,
                                    onToggleChannelMute = onToggleChannelMute,
                                    onToggleChannelBot = { cid, on -> srv?.let { onToggleChannelBot(it.id, cid, on) } },
                                    onToggleChannelKeepBot = { cid, on -> srv?.let { onToggleChannelKeepBot(it.id, cid, on) } },
                                    onToggleCatBot = { catId, on -> srv?.let { onToggleCatBot(it.id, catId, on) } },
                                )
                            }
                            if (srv != null) {
                                val podeMexerAqui = srv.ownerId == myId || podeGerenciarOrbitas(srv.id)
                                EditorialContextMenu(entries = {
                                    buildList {
                                        add(MenuEntry.Item("marcar tudo como lido", icon = Lucide.CheckCheck) {
                                            srv.channels.forEach { if (it.id in unread) onMarkChannelRead(it.id) }
                                        })
                                        if (podeMexerAqui) {
                                            add(MenuEntry.Separator)
                                            add(MenuEntry.Item("criar órbita", icon = Lucide.Plus) { chanDialog = ChanDialog.NewChannel(srv.id, null) })
                                            add(MenuEntry.Item("criar categoria", icon = Lucide.FolderPlus) { chanDialog = ChanDialog.NewCategory(srv.id) })
                                        }
                                    }
                                }) { orbits() }
                            } else orbits()
                        }
                    }
                }
            }
        }

        firstSteps?.let { fs ->
            fs()
            Spacer(Modifier.height(8.dp))
        }
    }

    when (val d = chanDialog) {
        is ChanDialog.NewChannel -> EditorialInputDialog(
            title = "nova órbita",
            placeholder = "nome-da-órbita",
            initial = "",
            confirmLabel = "criar",
            channelType = true,
            onDismiss = { chanDialog = null },
            onConfirm = { name, type -> onCreateChannel(d.serverId, name, type, d.categoryId) },
        )
        is ChanDialog.NewCategory -> EditorialInputDialog(
            title = "nova categoria",
            placeholder = "nome da categoria",
            initial = "",
            confirmLabel = "criar",
            channelType = false,
            onDismiss = { chanDialog = null },
            onConfirm = { name, _ -> onCreateCategory(d.serverId, name) },
        )
        is ChanDialog.RenameCategory -> EditorialInputDialog(
            title = "renomear categoria",
            placeholder = "nome da categoria",
            initial = d.current,
            confirmLabel = "salvar",
            channelType = false,
            onDismiss = { chanDialog = null },
            onConfirm = { name, _ -> onRenameCategory(d.serverId, d.categoryId, name) },
        )
        is ChanDialog.RenameChannel -> EditorialInputDialog(
            title = "renomear órbita",
            placeholder = "nome-da-órbita",
            initial = d.current,
            confirmLabel = "salvar",
            channelType = true,
            onDismiss = { chanDialog = null },
            onConfirm = { name, _ -> onRenameChannel(d.serverId, d.channelId, name) },
        )
        is ChanDialog.Visibilidade -> VisibilidadeDaOrbitaDialog(
            nomeDaOrbita = "#${d.name}",
            aoCentro = CenterInWindow,
            carregar = { pronto -> visibilidade.ler(d.serverId, d.channelId, pronto) },
            carregarCargos = { pronto -> visibilidade.cargos(d.serverId, pronto) },
            salvar = { privada, cargos, pronto ->
                visibilidade.salvar(d.serverId, d.channelId, privada, cargos, pronto)
            },
            onDismiss = { chanDialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun OrbitList(
    server: ServerDto?,
    activeChatId: String?,
    unread: Set<String>,
    unreadCounts: Map<String, Int>,
    members: List<ServerMemberDto>,
    voicePresence: Map<String, List<String>>,
    myId: String?,
    myVoiceChannelId: String?,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    podeGerenciarOrbitas: (String) -> Boolean,
    onNewChannelInCat: (categoryId: String) -> Unit,
    onRenameCat: (categoryId: String, current: String) -> Unit,
    onDeleteCat: (categoryId: String) -> Unit,
    onReorderChannels: (orderedIds: List<String>) -> Unit,
    onMoveToCategory: (channelId: String, categoryId: String) -> Unit,
    onReorderCategories: (orderedIds: List<String>) -> Unit,
    onOpenChannelRename: (channelId: String, current: String) -> Unit,
    onOpenChannelVisibility: (channelId: String, name: String) -> Unit,
    onExcluirCanal: (channelId: String) -> Unit,
    onMarkChannelRead: (channelId: String) -> Unit,
    silenciada: (channelId: String) -> Boolean,
    onToggleChannelMute: (channelId: String) -> Unit,
    onToggleChannelBot: (channelId: String, ligar: Boolean) -> Unit,
    onToggleChannelKeepBot: (channelId: String, guardar: Boolean) -> Unit,
    onToggleCatBot: (categoryId: String, ligar: Boolean) -> Unit,
) {
    if (server == null) return
    val podeGerenciar = server.ownerId == myId || podeGerenciarOrbitas(server.id)
    val clipboard = LocalClipboardManager.current
    var collapsedCats by remember(server.id) { mutableStateOf(setOf<String>()) }
    val pessoaPorId = remember(members) { members.associateBy { it.userId } }
    val catIds = remember(server.categories) { server.categories.map { it.id }.toSet() }
    val loose = remember(server.channels, catIds) {
        server.channels.filter { it.categoryId == null || it.categoryId !in catIds }.sortedBy { it.position }
    }
    val cats = remember(server.categories) { server.categories.sortedBy { it.position } }
    val byCat = remember(server.channels) { server.channels.groupBy { it.categoryId } }
    val looseIds = remember(loose) { loose.map { it.id } }
    val drag = remember(server.id) { ChannelDragState() }
    val chMenu = ChannelMenu(
        podeGerenciar, silenciada, onMarkChannelRead, onOpenChannelRename, onOpenChannelVisibility,
        onExcluirCanal, onToggleChannelMute,
        botAtende = { ch ->
            ch.botEnabled ?: server.categories.find { it.id == ch.categoryId }?.botEnabled ?: true
        },
        onToggleBot = onToggleChannelBot,
        onToggleKeepBot = onToggleChannelKeepBot,
    )

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
        itemsIndexed(loose, key = { _, ch -> ch.id }) { i, ch ->
            CascadeIn(i, server.id) {
                OrbitEntry(
                    ch, ch.id == activeChatId, ch.id in unread, unreadCounts[ch.id] ?: 0,
                    lembrarVozes(ch, voicePresence, pessoaPorId, myId, myVoiceChannelId),
                    onOpenChat, onOpenVoice,
                    dragCtx = if (podeGerenciar) ChannelDragCtx(drag, "loose", i, loose.size, looseIds, onReorderChannels, onMoveToCategory) else null,
                    menu = chMenu,
                )
            }
        }
        var offset = loose.size
        val orderedCatIds = cats.map { it.id }
        cats.forEachIndexed { catIndex, cat ->
            val channels = byCat[cat.id].orEmpty().sortedBy { it.position }
            val headerRow = offset
            val collapsed = cat.id in collapsedCats
            val channelIds = channels.map { it.id }
            val visible =
                if (collapsed) channels.filter { it.id == activeChatId || it.id in unread }
                else channels
            item(key = "cat-${cat.id}") {
                val highlight = drag.dragging && drag.hoverCat == cat.id && drag.section != "cat:${cat.id}"
                val hi by animateFloatAsState(if (highlight) 1f else 0f, tween(120), label = "catHitbox")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { drag.catBounds[cat.id] = it.boundsInWindow() }
                        .drawBehind {
                            if (hi > 0f) {
                                val inset = 6.dp.toPx()
                                val tl = Offset(inset, 3.dp.toPx())
                                val sz = Size(size.width - inset * 2f, size.height - 6.dp.toPx())
                                val rad = CornerRadius(10.dp.toPx())
                                drawRoundRect(Obsidian.accent.copy(alpha = 0.07f * hi), tl, sz, rad)
                                drawRoundRect(Obsidian.accent.copy(alpha = 0.55f * hi), tl, sz, rad, style = Stroke(1.5.dp.toPx()))
                            }
                        },
                ) {
                    CascadeIn(headerRow, server.id) {
                        val head = @Composable {
                            CategoryHeader(
                                name = cat.name,
                                collapsed = collapsed,
                                onToggle = {
                                    collapsedCats =
                                        if (cat.id in collapsedCats) collapsedCats - cat.id else collapsedCats + cat.id
                                },
                                dragCtx = if (podeGerenciar) CategoryDragCtx(drag, catIndex, orderedCatIds, onReorderCategories) else null,
                            )
                        }
                        val catUnread = channels.any { it.id in unread }
                        var confirmDelCat by remember(cat.id) { mutableStateOf(false) }
                        EditorialContextMenu(entries = {
                            buildList {
                                if (catUnread) add(MenuEntry.Item("marcar categoria como lida", icon = Lucide.CheckCheck) {
                                    channels.forEach { if (it.id in unread) onMarkChannelRead(it.id) }
                                })
                                add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(cat.id)) })
                                if (podeGerenciar) {
                                    add(MenuEntry.Separator)
                                    add(MenuEntry.Item("criar órbita aqui", icon = Lucide.Plus) { onNewChannelInCat(cat.id) })
                                    val botNaCat = cat.botEnabled ?: true
                                    add(
                                        MenuEntry.Item(
                                            if (botNaCat) "silenciar a bot na categoria" else "deixar a bot atender na categoria",
                                            icon = if (botNaCat) Lucide.BotOff else Lucide.Bot,
                                        ) { onToggleCatBot(cat.id, !botNaCat) },
                                    )
                                    add(MenuEntry.Item("renomear categoria", icon = Lucide.Pencil) { onRenameCat(cat.id, cat.name) })
                                    add(MenuEntry.Item("excluir categoria", danger = true, icon = Lucide.Trash2) { confirmDelCat = true })
                                }
                            }
                        }) {
                            head()
                            if (confirmDelCat) ConfirmPopup(
                                message = "excluir a categoria ${cat.name}? não há como desfazer.",
                                confirmLabel = "excluir",
                                onConfirm = { onDeleteCat(cat.id) },
                                onDismiss = { confirmDelCat = false },
                            )
                        }
                    }
                    visible.forEachIndexed { i, ch ->
                        key(ch.id) {
                        CascadeIn(
                            i,
                            "${server.id}:${cat.id}:$collapsed",
                            startDelayMs = minOf(headerRow, 6).toLong() * 26L,
                        ) {
                            OrbitEntry(
                                ch, ch.id == activeChatId, ch.id in unread, unreadCounts[ch.id] ?: 0,
                                lembrarVozes(ch, voicePresence, pessoaPorId, myId, myVoiceChannelId),
                                onOpenChat, onOpenVoice,
                                dragCtx = if (podeGerenciar && !collapsed)
                                    ChannelDragCtx(drag, "cat:${cat.id}", i, channels.size, channelIds, onReorderChannels, onMoveToCategory) else null,
                                menu = chMenu,
                            )
                        }
                        }
                    }
                }
            }
            offset = headerRow + 1 + visible.size
        }
    }
    ChannelDragBubble(drag)
    }
}

internal class ChannelDragState {
    var id by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var isVoice by mutableStateOf(false)
    var section by mutableStateOf<String?>(null)
    var fromIndex by mutableStateOf(-1)
    var targetIndex by mutableStateOf(-1)
    var windowPos by mutableStateOf(Offset.Zero)
    var fadingOut by mutableStateOf(false)
    var hoverCat by mutableStateOf<String?>(null)
    val catBounds = mutableStateMapOf<String, Rect>()
    var isCategory by mutableStateOf(false)
    val dragging: Boolean get() = id != null && !fadingOut
    fun reset() {
        id = null; name = ""; isVoice = false; section = null
        fromIndex = -1; targetIndex = -1; fadingOut = false; hoverCat = null; isCategory = false
    }
}

internal class ChannelDragCtx(
    val state: ChannelDragState,
    val section: String,
    val index: Int,
    val sectionSize: Int,
    val orderedIds: List<String>,
    val onReorder: (List<String>) -> Unit,
    val onMoveToCategory: (channelId: String, categoryId: String) -> Unit,
)

@Composable
internal fun Modifier.channelDrag(ch: ChannelDto, ctx: ChannelDragCtx?): Modifier {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var itemH by remember { mutableStateOf(1f) }
    if (ctx == null) return this
    val d = ctx.state
    return this
        .onGloballyPositioned { coords = it; itemH = it.size.height.toFloat().coerceAtLeast(1f) }
        .pointerInput(ch.id, ctx.section, ctx.index, ctx.sectionSize) {
            var accY = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { pos ->
                    accY = 0f
                    d.reset()
                    d.id = ch.id
                    d.name = ch.name
                    d.isVoice = ch.type == "VOICE"
                    d.section = ctx.section
                    d.fromIndex = ctx.index
                    d.targetIndex = ctx.index
                    coords?.let { c -> d.windowPos = c.localToWindow(pos) }
                },
                onDrag = { change, delta ->
                    change.consume()
                    accY += delta.y
                    coords?.let { c -> d.windowPos = c.localToWindow(change.position) }
                    d.targetIndex = (ctx.index + (accY / itemH).roundToInt()).coerceIn(0, ctx.sectionSize - 1)
                    d.hoverCat = d.catBounds.entries.firstOrNull { it.value.contains(d.windowPos) }?.key
                },
                onDragEnd = {
                    if (d.id == ch.id) {
                        val srcCat = if (ctx.section.startsWith("cat:")) ctx.section.removePrefix("cat:") else null
                        val over = d.hoverCat
                        if (over != null && over != srcCat) {
                            ctx.onMoveToCategory(ch.id, over)
                        } else if (d.targetIndex in 0 until ctx.sectionSize && d.targetIndex != d.fromIndex) {
                            val list = ctx.orderedIds.toMutableList()
                            list.add(d.targetIndex, list.removeAt(d.fromIndex))
                            ctx.onReorder(list)
                        }
                        d.fadingOut = true
                    }
                },
                onDragCancel = { if (d.id == ch.id) d.reset() },
            )
        }
}

internal class CategoryDragCtx(
    val state: ChannelDragState,
    val index: Int,
    val orderedIds: List<String>,
    val onReorder: (List<String>) -> Unit,
)

@Composable
internal fun Modifier.categoryDrag(name: String, ctx: CategoryDragCtx?): Modifier {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    if (ctx == null) return this
    val catId = ctx.orderedIds.getOrNull(ctx.index) ?: return this
    val d = ctx.state
    return this
        .onGloballyPositioned { coords = it }
        .pointerInput(catId, ctx.index, ctx.orderedIds.size) {
            detectDragGesturesAfterLongPress(
                onDragStart = { pos ->
                    d.reset()
                    d.id = catId
                    d.name = name
                    d.isCategory = true
                    d.fromIndex = ctx.index
                    d.targetIndex = ctx.index
                    coords?.let { c -> d.windowPos = c.localToWindow(pos) }
                },
                onDrag = { change, _ ->
                    change.consume()
                    coords?.let { c -> d.windowPos = c.localToWindow(change.position) }
                    val overId = d.catBounds.entries.firstOrNull { it.value.contains(d.windowPos) }?.key
                    val idx = ctx.orderedIds.indexOf(overId)
                    if (idx >= 0) d.targetIndex = idx
                },
                onDragEnd = {
                    if (d.id == catId) {
                        if (d.targetIndex in ctx.orderedIds.indices && d.targetIndex != d.fromIndex) {
                            val list = ctx.orderedIds.toMutableList()
                            list.add(d.targetIndex, list.removeAt(d.fromIndex))
                            ctx.onReorder(list)
                        }
                        d.fadingOut = true
                    }
                },
                onDragCancel = { if (d.id == catId) d.reset() },
            )
        }
}

@Composable
private fun ChannelDragBubble(d: ChannelDragState) {
    if (d.id == null) return
    val reduce = LocalReduceMotion.current
    val enter = remember(d.id) { Animatable(0f) }
    val splat = remember(d.id) { Animatable(0f) }
    LaunchedEffect(d.id) {
        if (reduce) enter.snapTo(1f)
        else enter.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(d.fadingOut) {
        if (d.fadingOut) {
            if (reduce) splat.snapTo(1f) else splat.animateTo(1f, tween(170, easing = FastOutLinearInEasing))
            d.reset()
        }
    }
    val pos = d.windowPos
    val name = d.name
    val voice = d.isVoice
    Popup(
        popupPositionProvider = remember(pos) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = IntOffset(
                    (pos.x - popupContentSize.width / 2f).roundToInt(),
                    (pos.y - popupContentSize.height / 2f).roundToInt(),
                )
            }
        },
        properties = PopupProperties(focusable = false),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        val e = enter.value
                        val ec = e.coerceIn(0f, 1f)
                        val squash = (1f - ec) * 0.22f
                        val x = splat.value
                        scaleX = e * (1f - squash) * (1f + 0.55f * x)
                        scaleY = e * (1f + squash) * (1f - 0.5f * x)
                        alpha = ec * (1f - x)
                    }
                    .clip(CircleShape)
                    .background(Obsidian.overlay)
                    .border(1.dp, Obsidian.accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(if (d.isCategory) Lucide.Folder else if (voice) Lucide.Volume2 else Lucide.Hash, tint = Obsidian.accent, size = 20.dp)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                name,
                style = TextStyle(color = Obsidian.text1, fontSize = 11.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) * (1f - splat.value) }
                    .widthIn(max = 140.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Obsidian.raised)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

private data class VozNaOrbita(val nome: String, val avatarUrl: String?, val souEu: Boolean)

@Composable
private fun lembrarVozes(
    ch: ChannelDto,
    voicePresence: Map<String, List<String>>,
    pessoaPorId: Map<String, ServerMemberDto>,
    myId: String?,
    myVoiceChannelId: String?,
): List<VozNaOrbita> =
    remember(ch.id, ch.type, voicePresence, pessoaPorId, myId, myVoiceChannelId) {
        if (ch.type != "VOICE") return@remember emptyList()
        val base = voicePresence[ch.id].orEmpty()
        val ids =
            if (myVoiceChannelId == ch.id && myId != null && myId !in base) listOf(myId) + base
            else base
        ids.map { uid ->
            val u = pessoaPorId[uid]?.user
            VozNaOrbita(u?.displayName ?: u?.username ?: "…", u?.avatarUrl, uid == myId)
        }
    }

@Composable
private fun OrbitEntry(
    ch: ChannelDto,
    active: Boolean,
    unread: Boolean,
    unreadCount: Int,
    naVoz: List<VozNaOrbita>,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    dragCtx: ChannelDragCtx? = null,
    menu: ChannelMenu,
) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth()) {
        var confirmDelCh by remember(ch.id) { mutableStateOf(false) }
        EditorialContextMenu(entries = {
            buildList {
                if (unread) add(MenuEntry.Item("marcar como lido", icon = Lucide.Check) { menu.onMarkRead(ch.id) })
                val calada = menu.silenciada(ch.id)
                add(MenuEntry.Item(if (calada) "reativar órbita" else "silenciar órbita", icon = if (calada) Lucide.Bell else Lucide.BellOff) { menu.onToggleMute(ch.id) })
                add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(ch.id)) })
                if (menu.podeGerenciar) {
                    add(MenuEntry.Separator)
                    val temBot = menu.botAtende(ch)
                    add(
                        MenuEntry.Item(
                            if (temBot) "silenciar a bot aqui" else "deixar a bot atender aqui",
                            icon = if (temBot) Lucide.BotOff else Lucide.Bot,
                        ) { menu.onToggleBot(ch.id, !temBot) },
                    )
                    if (temBot) add(
                        MenuEntry.Item(
                            if (ch.botKeepReplies) "não guardar as respostas" else "guardar as respostas aqui",
                            icon = if (ch.botKeepReplies) Lucide.EyeOff else Lucide.Archive,
                        ) { menu.onToggleKeepBot(ch.id, !ch.botKeepReplies) },
                    )
                    add(MenuEntry.Item("renomear", icon = Lucide.Pencil) { menu.onRename(ch.id, ch.name) })
                    add(
                        MenuEntry.Item("quem vê esta órbita", icon = Lucide.Lock) {
                            menu.onOpenVisibility(ch.id, ch.name)
                        },
                    )
                    add(MenuEntry.Item("excluir órbita", danger = true, icon = Lucide.Trash2) { confirmDelCh = true })
                }
            }
        }) {
            OrbitItem(ch, active, unread, unreadCount, onOpenChat, onOpenVoice, dragCtx)
            if (confirmDelCh) ConfirmPopup(
                message = "excluir a órbita ${ch.name}? apaga as mensagens dela — não há como desfazer.",
                confirmLabel = "excluir",
                onConfirm = { menu.onDelete(ch.id) },
                onDismiss = { confirmDelCh = false },
            )
        }
        naVoz.forEach { voz ->
            VoicePresenceRow(avatarUrl = voz.avatarUrl, name = voz.nome, isMe = voz.souEu)
        }
    }
}

@Composable
private fun VoicePresenceRow(avatarUrl: String?, name: String, isMe: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 8.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopAvatar(avatarUrl, name, 20)
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = TextStyle(color = if (isMe) Obsidian.text2 else Obsidian.text3, fontSize = 12.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CategoryHeader(name: String, collapsed: Boolean, onToggle: () -> Unit, dragCtx: CategoryDragCtx? = null) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rotation = animateFloatAsState(if (collapsed) -90f else 0f, tween(140))
    val tint = if (hovered) Obsidian.text2 else Obsidian.text3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(top = 10.dp, bottom = 2.dp)
            .categoryDrag(name, dragCtx)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(
            Lucide.ChevronDown,
            tint = tint,
            size = 13.dp,
            modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = name.uppercase(),
            style = TextStyle(color = tint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

internal class QuemVeAOrbita(
    val ler: (String, String, (ChannelVisibilityDto?, String?) -> Unit) -> Unit,
    val cargos: (String, (List<RoleDto>?, String?) -> Unit) -> Unit,
    val salvar: (String, String, Boolean, List<String>, (String?) -> Unit) -> Unit,
)

private sealed interface ChanDialog {
    data class NewChannel(val serverId: String, val categoryId: String?) : ChanDialog
    data class NewCategory(val serverId: String) : ChanDialog
    data class RenameCategory(val serverId: String, val categoryId: String, val current: String) : ChanDialog
    data class RenameChannel(val serverId: String, val channelId: String, val current: String) : ChanDialog
    data class Visibilidade(val serverId: String, val channelId: String, val name: String) : ChanDialog
}

private class ChannelMenu(
    val podeGerenciar: Boolean,
    val silenciada: (channelId: String) -> Boolean,
    val onMarkRead: (channelId: String) -> Unit,
    val onRename: (channelId: String, current: String) -> Unit,
    val onOpenVisibility: (channelId: String, name: String) -> Unit,
    val onDelete: (channelId: String) -> Unit,
    val onToggleMute: (channelId: String) -> Unit,
    val botAtende: (ChannelDto) -> Boolean,
    val onToggleBot: (channelId: String, ligar: Boolean) -> Unit,
    val onToggleKeepBot: (channelId: String, guardar: Boolean) -> Unit,
)

internal object CenterInWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
        y = ((windowSize.height - popupContentSize.height) / 2).coerceAtLeast(0),
    )
}
