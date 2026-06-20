package app.astra.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.SquarePen
import com.composables.icons.lucide.UserPlus
import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.ListSkeleton
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.Reveal
import app.astra.mobile.ui.components.StatusDot
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import coil.compose.AsyncImage

@Composable
fun HomeScreen(
    onOpenServer: (id: String, name: String) -> Unit,
    onOpenServers: () -> Unit,
    onOpenDm: (id: String, name: String) -> Unit,
    onOpenDms: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onJoinVoice: (channelId: String, name: String, serverId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // DM aberta pelo FAB -> navega pro chat e fecha o dialog.
    LaunchedEffect(Unit) {
        viewModel.opened.collect { conv ->
            showDialog = false
            onOpenDm(conv.conversationId, conv.otherName)
        }
    }

    val dms = remember(state.dms, query) {
        if (query.isBlank()) state.dms
        else state.dms.filter { it.otherName.contains(query.trim(), ignoreCase = true) }
    }

    CosmicBackground {
        Row(Modifier.fillMaxSize().statusBarsPadding()) {
            ServerRail(
                servers = state.servers,
                onOpenServer = onOpenServer,
                onAddServer = onOpenServers,
            )

            Column(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.padding(horizontal = 18.dp).padding(top = 14.dp)) {
                    Reveal {
                        Text(
                            text = "Mensagens",
                            fontFamily = DmSerif,
                            fontSize = 30.sp,
                            color = astraColors.text1,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Reveal(delayMillis = 70) {
                        SearchAddRow(
                            searchOpen = searchOpen,
                            query = query,
                            onToggleSearch = { searchOpen = !searchOpen; if (!searchOpen) query = "" },
                            onQuery = { query = it },
                            onAddFriends = onOpenFriends,
                        )
                    }
                }

                // Corpo: faixa "na voz" + lista de DMs.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize()) {
                        if (state.activeVoice.isNotEmpty() && query.isBlank()) {
                            Spacer(Modifier.height(16.dp))
                            MarginaliaLabel("na voz", Modifier.padding(horizontal = 18.dp))
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(state.activeVoice, key = { it.channelId }) { room ->
                                    VoiceRoomCard(room) {
                                        onJoinVoice(room.channelId, room.channelName, room.serverId)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MarginaliaLabel("conversas")
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { showDialog = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Lucide.SquarePen,
                                    contentDescription = "Nova conversa",
                                    tint = astraColors.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "ver todas",
                                style = MaterialTheme.typography.labelMedium,
                                color = astraColors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOpenDms)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))

                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                state.loading -> ListSkeleton(avatar = true)
                                dms.isEmpty() -> Text(
                                    text = if (query.isBlank())
                                        "Nenhuma conversa ainda — toque em Adicionar amigos."
                                    else "Nada encontrado.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = astraColors.text3,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                )
                                else -> LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        start = 18.dp,
                                        end = 18.dp,
                                        bottom = 12.dp,
                                    ),
                                ) {
                                    items(dms, key = { it.id }) { c ->
                                        DmRow(c, unread = c.id in state.unread) {
                                            viewModel.markSeen(c.id)
                                            onOpenDm(c.id, c.otherName)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                BottomUserBar(
                    name = state.myName,
                    avatar = state.myAvatar,
                    status = state.myStatus,
                    onPickStatus = viewModel::setStatus,
                    onProfile = onOpenProfile,
                    onSettings = onOpenSettings,
                    onLogout = { viewModel.logout() },
                )
            }
        }
    }

    if (showDialog) {
        NewConversationDialog(
            opening = state.opening,
            error = state.openError,
            onConfirm = viewModel::openConversation,
            onDismiss = { showDialog = false; viewModel.clearOpenError() },
        )
    }
}

// ── Rail de servidores (esquerda) ───────────────────────────────
@Composable
private fun ServerRail(
    servers: List<Server>,
    onOpenServer: (String, String) -> Unit,
    onAddServer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(astraColors.base)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RailTile(active = true, onClick = {}) {
            Icon(
                Lucide.MessageCircle,
                contentDescription = "Mensagens diretas",
                tint = astraColors.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        HairlineRule(Modifier.width(28.dp))
        servers.forEach { srv ->
            RailServer(srv) { onOpenServer(srv.id, srv.name) }
        }
        RailTile(active = false, onClick = onAddServer) {
            Text("+", fontFamily = DmSerif, fontSize = 24.sp, color = astraColors.accent)
        }
    }
}

@Composable
private fun RailTile(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(if (active) astraColors.accentDim else astraColors.raised)
            .border(1.dp, if (active) astraColors.accent.copy(alpha = 0.5f) else astraColors.border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun RailServer(server: Server, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!server.iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = server.iconUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(shape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = server.name.take(1).uppercase(),
                fontFamily = DmSerif,
                style = MaterialTheme.typography.titleLarge,
                color = astraColors.accent,
            )
        }
    }
}

// ── Busca + Adicionar amigos ────────────────────────────────────
@Composable
private fun SearchAddRow(
    searchOpen: Boolean,
    query: String,
    onToggleSearch: () -> Unit,
    onQuery: (String) -> Unit,
    onAddFriends: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Botao de busca circular
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(astraColors.raised)
                    .border(1.dp, if (searchOpen) astraColors.accent.copy(alpha = 0.5f) else astraColors.border, CircleShape)
                    .clickable(onClick = onToggleSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.Search,
                    contentDescription = "Buscar",
                    tint = if (searchOpen) astraColors.accent else astraColors.text2,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            // Pill "Adicionar amigos"
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(astraColors.raised)
                    .border(1.dp, astraColors.border, RoundedCornerShape(12.dp))
                    .clickable(onClick = onAddFriends)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Lucide.UserPlus,
                    contentDescription = null,
                    tint = astraColors.text1,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Adicionar amigos",
                    style = MaterialTheme.typography.titleSmall,
                    color = astraColors.text1,
                )
            }
        }
        if (searchOpen) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar conversa", color = astraColors.text3) },
            )
        }
    }
}

// ── Card de canal de voz ativo (faixa horizontal) ───────────────
@Composable
private fun VoiceRoomCard(room: ActiveVoiceRoom, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(184.dp)
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.accent.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(astraColors.accent))
            Spacer(Modifier.width(8.dp))
            MarginaliaLabel("na voz", color = astraColors.accent)
        }
        Text(
            text = room.channelName,
            style = MaterialTheme.typography.titleMedium,
            color = astraColors.text1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MarginaliaLabel("${room.serverName} · ${room.count} na sala")
    }
}

// ── Linha de conversa ───────────────────────────────────────────
@Composable
private fun DmRow(c: Conversation, unread: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AstraAvatar(c.otherAvatarUrl, c.otherName, size = 48)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = c.otherName,
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (c.lastFromMe) "Você: ${c.preview}" else c.preview,
                style = MaterialTheme.typography.bodySmall,
                color = if (unread) astraColors.text1 else astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val time = relativeShort(c.lastMessageAt)
            if (time.isNotEmpty()) MarginaliaLabel(time)
            if (unread) {
                Box(
                    Modifier.size(9.dp).clip(CircleShape).background(astraColors.accent),
                )
            }
        }
    }
}

// ── Card de usuario (rodape) — espelha o UserFooter do web ───────
@Composable
private fun BottomUserBar(
    name: String,
    avatar: String?,
    status: UserStatus,
    onPickStatus: (UserStatus) -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.navigationBarsPadding()) {
        HairlineRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(astraColors.base)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar + dot de status -> abre o seletor de status
            Box {
                Box(Modifier.clip(CircleShape).clickable { menuOpen = true }) {
                    AstraAvatar(url = avatar, name = name.ifBlank { "?" }, size = 38)
                    StatusDot(
                        status = status,
                        size = 14.dp,
                        bordered = true,
                        borderColor = astraColors.base,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
                StatusMenu(
                    expanded = menuOpen,
                    current = status,
                    onPick = { menuOpen = false; onPickStatus(it) },
                    onDismiss = { menuOpen = false },
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { menuOpen = true }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = name.ifBlank { "Astra" },
                    style = MaterialTheme.typography.titleSmall,
                    color = astraColors.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(status, size = 8.dp)
                    Spacer(Modifier.width(6.dp))
                    MarginaliaLabel(statusLabel(status))
                }
            }
            FooterIconBtn(Lucide.Pencil, "Editar perfil", onProfile)
            FooterIconBtn(Lucide.Settings, "Configuracoes", onSettings)
            FooterIconBtn(Lucide.LogOut, "Sair", onLogout, danger = true)
        }
    }
}

private fun statusLabel(s: UserStatus): String = when (s) {
    UserStatus.ONLINE -> "Online"
    UserStatus.IDLE -> "Ausente"
    UserStatus.DND -> "Não perturbe"
    UserStatus.INVISIBLE -> "Invisível"
    UserStatus.OFFLINE -> "Offline"
}

@Composable
private fun StatusMenu(
    expanded: Boolean,
    current: UserStatus,
    onPick: (UserStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf(UserStatus.ONLINE, UserStatus.IDLE, UserStatus.DND, UserStatus.INVISIBLE).forEach { s ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(s, size = 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = statusLabel(s),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (s == current) astraColors.accent else astraColors.text1,
                        )
                    }
                },
                onClick = { onPick(s) },
            )
        }
    }
}

@Composable
private fun FooterIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = if (danger) astraColors.danger else astraColors.text2,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun NewConversationDialog(
    opening: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!opening) onDismiss() },
        containerColor = astraColors.overlay,
        title = { Text("Nova conversa", style = MaterialTheme.typography.titleLarge, color = astraColors.text1) },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    enabled = !opening,
                    label = { Text("@username") },
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = astraColors.danger,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(username) }, enabled = username.isNotBlank() && !opening) {
                Text(if (opening) "Abrindo..." else "Abrir", color = astraColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !opening) {
                Text("Cancelar", color = astraColors.text2)
            }
        },
    )
}

// ISO -> "agora / 5m / 3h / 2d / 3 sem / 5 mês". Falha de parse = vazio.
private fun relativeShort(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val then = java.time.Instant.parse(iso)
        val sec = java.time.Duration.between(then, java.time.Instant.now()).seconds.coerceAtLeast(0)
        when {
            sec < 60 -> "agora"
            sec < 3600 -> "${sec / 60}m"
            sec < 86_400 -> "${sec / 3600}h"
            sec < 604_800 -> "${sec / 86_400}d"
            sec < 2_592_000 -> "${sec / 604_800} sem"
            else -> "${sec / 2_592_000} mês"
        }
    } catch (e: Exception) {
        ""
    }
}
