package app.astra.desktop.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    // ChatVm por alvo aberto; sai da sala/cancela o live ao trocar ou fechar.
    val chat = state.chat
    val chatVm = if (chat != null) {
        val created = remember(chat) {
            ChatVm(scope, chat, koin.get<ChannelApi>(), koin.get<DmApi>(), socket, koin.get<Json>(), session.userId)
        }
        DisposableEffect(chat) { onDispose { created.dispose() } }
        created
    } else null

    Row(Modifier.fillMaxSize().background(Obsidian.base)) {
        Rail(
            servers = state.servers,
            selection = state.selection,
            onSelect = vm::select,
        )
        Sidebar(
            selection = state.selection,
            server = state.selectedServer,
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
            chatVm = chatVm,
            membersOpen = state.membersOpen,
            onToggleMembers = vm::toggleMembers,
            loading = state.loading,
            error = state.error,
            onRetry = vm::load,
            modifier = Modifier.weight(1f),
        )
        if (state.selection is Selection.Server && state.membersOpen) {
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
    // Pill morph: circulo -> quadrado arredondado no hover/ativo (assinatura da rail).
    val shape = RoundedCornerShape(if (active || hovered) 14.dp else 22.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(if (active) Obsidian.overlay else Obsidian.raised)
            .border(1.dp, if (active) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim, shape)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---- Sidebar (260dp): orbitas da constelacao OU sussurros + painel do user ----

@Composable
private fun Sidebar(
    selection: Selection,
    server: ServerDto?,
    dms: List<ConversationDto>,
    activeChatId: String?,
    unread: Set<String>,
    dmTyping: Set<String>,
    meName: String,
    meAvatar: String?,
    onOpenChat: (ChatTarget) -> Unit,
) {
    Column(Modifier.width(260.dp).fillMaxHeight().background(Obsidian.raised)) {
        // Header
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            BasicText(
                text = if (selection is Selection.Dms) "Sussurros" else server?.name ?: "",
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
            if (selection is Selection.Dms) DmList(dms, activeChatId, unread, dmTyping, onOpenChat)
            else OrbitList(server, activeChatId, unread, onOpenChat)
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
        items(server.channels, key = { it.id }) { ch ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val active = ch.id == activeChatId
            val isUnread = !active && ch.id in unread
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent)
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
        items(dms, key = { it.id }) { conv ->
            val u = conv.otherUser
            val name = u?.displayName ?: u?.username ?: "?"
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val active = conv.id == activeChatId
            val isUnread = !active && conv.id in unread
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent)
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

// ---- Palco central ----

@Composable
private fun Stage(
    server: ServerDto?,
    chat: ChatTarget?,
    chatVm: ChatVm?,
    membersOpen: Boolean,
    onToggleMembers: () -> Unit,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxHeight().background(Obsidian.base)) {
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

        if (chat != null && chatVm != null) {
            ChatView(chat, chatVm)
            return@Column
        }

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

// ---- Painel de membros (240dp) ----

@Composable
private fun MembersPanel(members: List<ServerMemberDto>) {
    Column(Modifier.width(240.dp).fillMaxHeight().background(Obsidian.raised)) {
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
