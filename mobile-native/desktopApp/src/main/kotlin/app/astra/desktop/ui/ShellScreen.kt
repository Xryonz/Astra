package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import app.astra.desktop.auth.Session
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.ChatVm
import app.astra.desktop.shell.Selection
import app.astra.desktop.shell.ShellVm
import app.astra.desktop.ui.theme.Obsidian
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext

// Shell desktop (fatia 2): rail 72 | sidebar 260 | palco | membros 240.
// Compacto (decisao do dono); chat de verdade e a proxima fatia.
@Composable
fun ShellScreen(session: Session) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    val socket = remember { koin.get<DesktopSocket>() }
    val vm = remember {
        ShellVm(
            scope, koin.get<ServerApi>(), koin.get<UserApi>(), koin.get<DmApi>(), koin.get<SessionStore>(),
            socket, koin.get<Json>(), session.userId,
        )
    }
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { socket.connect() }

    // ChatVm nasce DENTRO da pagina do AnimatedContent do palco: a conversa
    // antiga continua renderizando durante o fade e o dispose so roda quando a
    // pagina sai da composicao de vez.
    val chat = state.chat
    val createChatVm = remember {
        { target: ChatTarget ->
            ChatVm(scope, target, koin.get<ChannelApi>(), koin.get<DmApi>(), socket, koin.get<Json>(), session.userId)
        }
    }

    // Divisorias com respiro: os paineis flutuam sobre o void, separados por
    // gaps e com sombra propria (pedido do dono).
    Row(
        Modifier
            .fillMaxSize()
            .background(Obsidian.void)
            .padding(top = 6.dp, end = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Rail(
            servers = state.servers,
            selection = state.selection,
            onSelect = vm::select,
        )
        Sidebar(
            selection = state.selection,
            servers = state.servers,
            dms = state.dms,
            activeChatId = chat?.id,
            unread = state.unread,
            dmTyping = state.dmTyping,
            meName = state.me?.displayName ?: state.me?.username ?: session.displayName,
            meAvatar = state.me?.avatarUrl,
            onOpenChat = vm::openChat,
        )
        Stage(
            state.selectedServer,
            chat = chat,
            createChatVm = createChatVm,
            membersOpen = state.membersOpen,
            onToggleMembers = vm::toggleMembers,
            loading = state.loading,
            error = state.error,
            onRetry = vm::load,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = state.selection is Selection.Server && state.membersOpen,
            enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
            exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120)),
        ) {
            MembersPanel(state.members)
        }
    }
}

// ---- Rail de constelacoes (72dp) ----

@Composable
private fun Rail(
    servers: List<ServerDto>,
    selection: Selection,
    onSelect: (Selection) -> Unit,
) {
    Column(
        modifier = Modifier.width(72.dp).fillMaxHeight().background(Obsidian.void),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        RailItem(
            active = selection is Selection.Dms,
            onClick = { onSelect(Selection.Dms) },
        ) {
            BasicText("✦", style = TextStyle(color = Obsidian.accent, fontSize = 20.sp))
        }
        Spacer(Modifier.height(8.dp))
        HairRule()
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(servers, key = { it.id }) { srv ->
                RailItem(
                    active = (selection as? Selection.Server)?.id == srv.id,
                    onClick = { onSelect(Selection.Server(srv.id)) },
                ) {
                    if (!srv.iconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = srv.iconUrl,
                            contentDescription = srv.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        BasicText(
                            text = srv.name.take(1).uppercase(),
                            style = TextStyle(color = Obsidian.accent, fontSize = 17.sp, fontFamily = FontFamily.Serif),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RailItem(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Pill morph: circulo -> quadrado arredondado no hover/ativo (assinatura da
    // rail). Canto, fundo e borda transicionam (polish).
    val corner by animateDpAsState(if (active || hovered) 14.dp else 22.dp, tween(140))
    val shape = RoundedCornerShape(corner)
    val bg by animateColorAsState(if (active) Obsidian.overlay else Obsidian.raised, tween(140))
    val borderColor by animateColorAsState(
        if (active) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim,
        tween(140),
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---- Sidebar (260dp): orbitas da constelacao OU sussurros + painel do user ----

@Composable
private fun Sidebar(
    selection: Selection,
    servers: List<ServerDto>,
    dms: List<ConversationDto>,
    activeChatId: String?,
    unread: Set<String>,
    dmTyping: Set<String>,
    meName: String,
    meAvatar: String?,
    onOpenChat: (ChatTarget) -> Unit,
) {
    Column(
        Modifier
            .width(260.dp)
            .fillMaxHeight()
            .shadow(12.dp, RoundedCornerShape(10.dp))
            .background(Obsidian.raised),
    ) {
        // Transicao ao trocar na rail (sussurros <-> constelacao): header + lista
        // viram uma "pagina" que desliza de leve e faz fade. A pagina que sai
        // resolve o servidor pela PROPRIA selecao antiga (por isso a lista
        // inteira de servers entra aqui, nao so o selecionado).
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
                // Header
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    BasicText(
                        text = if (sel is Selection.Dms) "Sussurros" else srv?.name ?: "",
                        style = TextStyle(
                            color = Obsidian.text1, fontSize = 16.sp,
                            fontFamily = FontFamily.Serif,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HairRule()

                Box(Modifier.weight(1f)) {
                    if (sel is Selection.Dms) DmList(dms, activeChatId, unread, dmTyping, onOpenChat)
                    else OrbitList(srv, activeChatId, unread, onOpenChat)
                }
            }
        }

        // Painel do usuario (canto inferior esquerdo, estilo Discord)
        HairRule()
        Row(
            modifier = Modifier.fillMaxWidth().background(Obsidian.void.copy(alpha = 0.55f)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopAvatar(meAvatar, meName, 30)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = meName,
                    style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF7BC98A)))
                    Spacer(Modifier.width(5.dp))
                    BasicText("brilhando", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
                }
            }
        }
    }
}

@Composable
private fun OrbitList(server: ServerDto?, activeChatId: String?, unread: Set<String>, onOpenChat: (ChatTarget) -> Unit) {
    if (server == null) return
    // Estrutura Discord: orbitas soltas primeiro, depois categorias colapsaveis.
    var collapsedCats by remember(server.id) { mutableStateOf(setOf<String>()) }
    val catIds = server.categories.map { it.id }.toSet()
    val loose = server.channels.filter { it.categoryId == null || it.categoryId !in catIds }.sortedBy { it.position }
    val cats = server.categories.sortedBy { it.position }
    val byCat = server.channels.groupBy { it.categoryId }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
        items(loose, key = { it.id }) { ch ->
            OrbitItem(ch, ch.id == activeChatId, ch.id in unread, onOpenChat)
        }
        cats.forEach { cat ->
            val channels = byCat[cat.id].orEmpty().sortedBy { it.position }
            item(key = "cat-${cat.id}") {
                CategoryHeader(
                    name = cat.name,
                    collapsed = cat.id in collapsedCats,
                    onToggle = {
                        collapsedCats =
                            if (cat.id in collapsedCats) collapsedCats - cat.id else collapsedCats + cat.id
                    },
                )
            }
            // Colapsada ainda mostra a ativa e as nao lidas (comportamento Discord).
            val visible =
                if (cat.id in collapsedCats) channels.filter { it.id == activeChatId || it.id in unread }
                else channels
            items(visible, key = { it.id }) { ch ->
                OrbitItem(ch, ch.id == activeChatId, ch.id in unread, onOpenChat)
            }
        }
    }
}

@Composable
private fun CategoryHeader(name: String, collapsed: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Chevron gira ao colapsar (▾ -> ▸).
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, tween(140))
    val tint = if (hovered) Obsidian.text2 else Obsidian.text3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(top = 10.dp, bottom = 2.dp)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "▾",
            style = TextStyle(color = tint, fontSize = 9.sp),
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
        Spacer(Modifier.width(5.dp))
        BasicText(
            text = name.uppercase(),
            style = TextStyle(color = tint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OrbitItem(ch: ChannelDto, active: Boolean, unread: Boolean, onOpenChat: (ChatTarget) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val isUnread = !active && unread
    val itemBg by animateColorAsState(
        if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent,
        tween(120),
    )
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(itemBg)
                .hoverable(interaction)
                // Orbita de voz nao abre chat (voz e fatia futura).
                .clickable(enabled = ch.type != "VOICE") { onOpenChat(ChatTarget.Channel(ch.id, ch.name)) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = if (ch.type == "VOICE") "◉" else "#",
                style = TextStyle(color = if (ch.type == "VOICE") Obsidian.accent else Obsidian.text3, fontSize = 13.sp),
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = ch.name,
                style = TextStyle(
                    color = if (active || hovered || isUnread) Obsidian.text1 else Obsidian.text2,
                    fontSize = 13.sp,
                    fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (isUnread) UnreadPill(Modifier.align(Alignment.CenterStart))
    }
}

// Traco accent na borda esquerda do item — marca de nao-lida (estilo Discord,
// tokens obsidiana).
@Composable
private fun UnreadPill(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(3.dp)
            .height(16.dp)
            .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
            .background(Obsidian.accent),
    )
}

@Composable
private fun DmList(
    dms: List<ConversationDto>,
    activeChatId: String?,
    unread: Set<String>,
    dmTyping: Set<String>,
    onOpenChat: (ChatTarget) -> Unit,
) {
    if (dms.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            BasicText("nenhum sussurro ainda", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
        }
        return
    }
    // Estrutura Discord: busca no topo dos sussurros (filtro local por nome).
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) dms else dms.filter { c ->
        val n = c.otherUser?.displayName ?: c.otherUser?.username ?: ""
        n.contains(query.trim(), ignoreCase = true)
    }
    Column(Modifier.fillMaxSize()) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Obsidian.base)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    if (query.isEmpty()) {
                        BasicText("encontrar conversa", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        )
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                BasicText("nada encontrado", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            items(filtered, key = { it.id }) { conv ->
            val u = conv.otherUser
            val name = u?.displayName ?: u?.username ?: "?"
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val active = conv.id == activeChatId
            val isUnread = !active && conv.id in unread
            val itemBg by animateColorAsState(
                if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent,
                tween(120),
            )
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(itemBg)
                        .hoverable(interaction)
                        .clickable { onOpenChat(ChatTarget.Dm(conv.id, name)) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopAvatar(u?.avatarUrl, name, 28)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            text = name,
                            style = TextStyle(
                                color = if (active || hovered || isUnread) Obsidian.text1 else Obsidian.text2,
                                fontSize = 13.sp,
                                fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                            ),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (conv.id in dmTyping) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TypingDots(Obsidian.accent, dotSize = 3.dp)
                                Spacer(Modifier.width(5.dp))
                                BasicText(
                                    text = "digitando…",
                                    style = TextStyle(color = Obsidian.accent, fontSize = 11.sp),
                                )
                            }
                        } else {
                            val preview = conv.lastMessage?.content?.ifBlank { "anexo" }
                            if (preview != null) {
                                BasicText(
                                    text = preview,
                                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (isUnread) UnreadPill(Modifier.align(Alignment.CenterStart))
            }
            }
        }
    }
}

// ---- Palco central ----

@Composable
private fun Stage(
    server: ServerDto?,
    chat: ChatTarget?,
    createChatVm: (ChatTarget) -> ChatVm,
    membersOpen: Boolean,
    onToggleMembers: () -> Unit,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .shadow(12.dp, RoundedCornerShape(10.dp))
            .background(Obsidian.base),
    ) {
        // Top bar do palco
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = when {
                    chat is ChatTarget.Channel -> "# ${chat.title}"
                    chat is ChatTarget.Dm -> "sussurro · ${chat.title}"
                    server != null -> "constelacao · ${server.name}"
                    else -> "sussurros"
                },
                style = TextStyle(color = if (chat != null) Obsidian.text1 else Obsidian.text3, fontSize = 13.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (server != null) {
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (hovered) Obsidian.hover else Color.Transparent)
                        .border(1.dp, Obsidian.borderMid, CircleShape)
                        .hoverable(interaction)
                        .clickable(onClick = onToggleMembers),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        "☰",
                        style = TextStyle(color = if (membersOpen) Obsidian.accent else Obsidian.text3, fontSize = 13.sp),
                    )
                }
            }
        }
        HairRule()

        // Fade ao abrir/trocar conversa: a pagina antiga (com seu ChatVm) segue
        // renderizando ate o fade acabar; o dispose roda no fim.
        AnimatedContent(
            targetState = chat,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            if (target != null) {
                val chatVm = remember { createChatVm(target) }
                DisposableEffect(Unit) { onDispose { chatVm.dispose() } }
                ChatView(target, chatVm)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> BasicText("carregando o ceu…", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
                        error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(error, style = TextStyle(color = Obsidian.danger, fontSize = 13.sp))
                            Spacer(Modifier.height(10.dp))
                            BasicText(
                                "tentar de novo",
                                style = TextStyle(color = Obsidian.accent, fontSize = 13.sp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                                    .clickable(onClick = onRetry)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(
                                text = "✦",
                                style = TextStyle(color = Obsidian.borderMid, fontSize = 40.sp),
                            )
                            Spacer(Modifier.height(10.dp))
                            BasicText(
                                text = if (server != null) "escolha uma orbita — a conversa chega na proxima fatia"
                                else "escolha um sussurro — a conversa chega na proxima fatia",
                                style = TextStyle(color = Obsidian.text3, fontSize = 13.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Painel de membros (240dp) ----

@Composable
private fun MembersPanel(members: List<ServerMemberDto>) {
    Column(
        Modifier
            .width(240.dp)
            .fillMaxHeight()
            .shadow(12.dp, RoundedCornerShape(10.dp))
            .background(Obsidian.raised),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            BasicText(
                text = "membros — ${members.size}",
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
            )
        }
        HairRule()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            items(members, key = { it.userId }) { m ->
                val name = m.user.displayName ?: m.user.username
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopAvatar(m.user.avatarUrl, name, 26)
                    Spacer(Modifier.width(9.dp))
                    BasicText(
                        text = name,
                        style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---- Pecinhas ----

@Composable
private fun HairRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Obsidian.borderDim.copy(alpha = 0.6f)))
}
