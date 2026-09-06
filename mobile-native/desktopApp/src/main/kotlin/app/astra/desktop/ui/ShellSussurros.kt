package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.BlockApi
import app.astra.mobile.core.network.FriendApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.ServerDto
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.User
import com.composables.icons.lucide.UserCheck
import com.composables.icons.lucide.UserMinus
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

@Composable
internal fun DmList(
    dms: List<ConversationDto>,
    servers: List<ServerDto>,
    onToggleMute: (ConversationDto) -> Unit,
    onMarkRead: (String) -> Unit,
    onCloseDm: (String) -> Unit,
    activeChatId: String?,
    unread: Set<String>,
    dmTyping: Set<String>,
    dmPresence: Map<String, String>,
    onOpenChat: (ChatTarget) -> Unit,
) {
    if (dms.isEmpty()) {
        EmptyHint("nenhum sussurro ainda")
        return
    }
    val clipboard = LocalClipboardManager.current
    var profileFor by remember { mutableStateOf<String?>(null) }
    var inviteFor by remember { mutableStateOf<ConversationDto?>(null) }
    val friendApi = remember { GlobalContext.get().get<FriendApi>() }
    var friendships by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val blockApi = remember { GlobalContext.get().get<BlockApi>() }
    var blocked by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        friendships = runCatching { friendApi.friends().data.orEmpty() }
            .getOrDefault(emptyList())
            .associate { it.user.id to it.friendshipId }
        blocked = runCatching { blockApi.blocked().data.orEmpty() }
            .getOrDefault(emptyList()).map { it.id }.toSet()
    }
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Obsidian.base)
                        .border(1.dp, Obsidian.borderMid.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LIcon(
                        Lucide.Search,
                        tint = if (query.isEmpty()) Obsidian.text3 else Obsidian.accent,
                        size = 13.dp,
                    )
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("encontrar conversa", style = Tipo.descricao)
                        }
                        inner()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        )
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("nada encontrado", style = Tipo.descricao)
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            itemsIndexed(filtered, key = { _, c -> c.id }) { cascadeRow, conv ->
            val u = conv.otherUser
            val name = u?.displayName ?: u?.username ?: "?"
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val active = conv.id == activeChatId
            val isUnread = !active && conv.id in unread
            val alvo = if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent
            val fundoAnimado by animateColorAsState(alvo, tween(120))
            val itemBg = if (hovered && !active) alvo else fundoAnimado
            CascadeIn(cascadeRow, Unit) {
            EditorialContextMenu(entries = {
                buildList {
                    u?.id?.let { uid ->
                        add(MenuEntry.Item("ver perfil", icon = Lucide.User) { profileFor = uid })
                    }
                    if (isUnread) add(MenuEntry.Item("marcar como lida", icon = Lucide.Check) { onMarkRead(conv.id) })
                    add(MenuEntry.Separator)
                    if (u?.username != null && servers.isNotEmpty()) {
                        add(MenuEntry.Item("convidar para constelação", icon = Lucide.Users) { inviteFor = conv })
                    }
                    add(
                        MenuEntry.Item(if (conv.muted) "desmutar sussurro" else "mutar sussurro", icon = if (conv.muted) Lucide.Bell else Lucide.BellOff) {
                            onToggleMute(conv)
                        },
                    )
                    u?.id?.let { uid -> add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(uid)) }) }
                    add(MenuEntry.Separator)
                    add(MenuEntry.Item("fechar sussurro", icon = Lucide.X) { onCloseDm(conv.id) })
                    friendships[u?.id]?.let { fid ->
                        add(MenuEntry.Separator)
                        add(MenuEntry.Item("desfazer amizade", danger = true, icon = Lucide.UserMinus) {
                            scope.launch {
                                runCatching { friendApi.remove(fid) }
                                friendships = friendships - (u?.id ?: "")
                            }
                        })
                    }
                    u?.id?.let { uid ->
                        val jaBloqueado = uid in blocked
                        if (!friendships.containsKey(uid)) add(MenuEntry.Separator)
                        add(
                            MenuEntry.Item(
                                if (jaBloqueado) "desbloquear" else "bloquear",
                                danger = !jaBloqueado,
                                icon = if (jaBloqueado) Lucide.UserCheck else Lucide.Ban,
                            ) {
                                scope.launch {
                                    if (jaBloqueado) {
                                        runCatching { blockApi.unblock(uid) }.onSuccess { blocked = blocked - uid }
                                    } else {
                                        runCatching { blockApi.block(uid) }.onSuccess {
                                            blocked = blocked + uid
                                            friendships = friendships - uid
                                            onCloseDm(conv.id)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(itemBg)
                        .hoverable(interaction)
                        .clickable { onOpenChat(ChatTarget.Dm(conv.id, name)) }
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        DesktopAvatar(u?.avatarUrl, name, 28)
                        StatusDot(
                            status = userStatus(dmPresence[u?.id]),
                            size = 10.dp,
                            bordered = true,
                            borderColor = Obsidian.base,
                            cutoutColor = Obsidian.base,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
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
                                Text(
                                    text = "digitando…",
                                    style = TextStyle(color = Obsidian.accent, fontSize = 11.sp),
                                )
                            }
                        } else {
                            val preview = conv.lastMessage?.content?.ifBlank { "anexo" }
                            if (preview != null) {
                                Text(
                                    text = preview,
                                    style = Tipo.apoio,
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
    }

    profileFor?.let { uid ->
        ProfilePage(
            userId = uid,
            isMe = false,
            onStartDm = { _, _ -> profileFor = null },
            onClose = { profileFor = null },
        )
    }
    inviteFor?.let { conv ->
        val uname = conv.otherUser?.username
        if (uname == null) inviteFor = null
        else PickServerDialog(
            username = uname,
            servers = servers,
            onClose = { inviteFor = null },
        )
    }
}

@Composable
private fun PickServerDialog(username: String, servers: List<ServerDto>, onClose: () -> Unit) {
    val api = remember { GlobalContext.get().get<ServerApi>() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    DialogShell(onClose = onClose) {
        Text(
            "convidar @$username",
            style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "para qual constelação?",
            style = Tipo.apoio,
        )
        Spacer(Modifier.height(12.dp))
        servers.forEach { srv ->
            val loading = busy == srv.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = busy == null) {
                        busy = srv.id
                        msg = null
                        scope.launch {
                            val r = runCatching { api.addMember(srv.id, username) }
                            busy = null
                            msg = if (r.isSuccess) "entrou em ${srv.name}" to true
                            else "não deu — já é membro, ou você não tem permissão" to false
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopAvatar(srv.iconUrl, srv.name, 26)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (loading) "…" else srv.name,
                    style = Tipo.corpo,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        msg?.let { (text, ok) ->
            Spacer(Modifier.height(10.dp))
            Text(
                text,
                style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp),
            )
        }
    }
}

@Composable
internal fun BotaoDeLigar(icone: ImageVector, titulo: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        Modifier
            .size(28.dp)
            .clickScale(src, formaDoFoco = RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (hov) Obsidian.accentDim else Color.Transparent, RoundedCornerShape(8.dp))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icone, tint = if (hov) Obsidian.accent else Obsidian.text3, size = 15.dp, rotulo = titulo)
    }
}
