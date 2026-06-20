package app.astra.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import app.astra.mobile.core.realtime.ConnectionState
import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.ListSkeleton
import app.astra.mobile.ui.components.MarginaliaLabel
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
    onJoinVoice: (channelId: String, name: String, serverId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val socket by viewModel.socketState.collectAsState()
    val state by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    // DM aberta pelo FAB -> navega pro chat e fecha o dialog.
    LaunchedEffect(Unit) {
        viewModel.opened.collect { conv ->
            showDialog = false
            onOpenDm(conv.conversationId, conv.otherName)
        }
    }

    CosmicBackground {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().statusBarsPadding()) {
                ServerRail(
                    servers = state.servers,
                    onOpenServer = onOpenServer,
                    onAddServer = onOpenServers,
                )
                MainPanel(
                    state = state,
                    socket = socket,
                    onJoinVoice = onJoinVoice,
                    onOpenDm = { c -> viewModel.markSeen(c.id); onOpenDm(c.id, c.otherName) },
                    onOpenDms = onOpenDms,
                    onOpenFriends = onOpenFriends,
                    onOpenSettings = onOpenSettings,
                )
            }
            NewMessageFab(
                onClick = { showDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            )
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
            Text("✦", color = astraColors.accent, fontSize = 20.sp)
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

// ── Painel principal (direita) ──────────────────────────────────
@Composable
private fun RowScope.MainPanel(
    state: HomeUiState,
    socket: ConnectionState,
    onJoinVoice: (String, String, String) -> Unit,
    onOpenDm: (Conversation) -> Unit,
    onOpenDms: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        // Header: avatar (-> settings) + nome + status do socket
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AstraAvatar(
                url = state.myAvatar,
                name = state.myName.ifBlank { "?" },
                size = 42,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onOpenSettings),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                MarginaliaLabel("bem-vindo")
                Text(
                    text = state.myName.ifBlank { "Astra" },
                    style = MaterialTheme.typography.titleLarge,
                    color = astraColors.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SocketChip(socket)
        }

        Spacer(Modifier.height(18.dp))
        EntryRow("Amigos", "sua constelacao", onOpenFriends)

        // Faixa "na voz" — so aparece quando ha alguem em canal de voz agora.
        if (state.activeVoice.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            MarginaliaLabel("na voz")
            Spacer(Modifier.height(6.dp))
            state.activeVoice.forEach { room ->
                ActiveVoiceCard(room) { onJoinVoice(room.channelId, room.channelName, room.serverId) }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MarginaliaLabel("mensagens")
            Spacer(Modifier.weight(1f))
            Text(
                text = "ver todas",
                style = MaterialTheme.typography.labelMedium,
                color = astraColors.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenDms).padding(6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> ListSkeleton(avatar = true)
                state.dms.isEmpty() -> Text(
                    text = "Nenhuma conversa ainda — abra Amigos pra comecar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = astraColors.text3,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.dms, key = { it.id }) { c ->
                        DmPreviewRow(c, unread = c.id in state.unread) { onOpenDm(c) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(title: String, sub: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = astraColors.text1)
            MarginaliaLabel(sub)
        }
        Text("›", fontFamily = DmSerif, fontSize = 24.sp, color = astraColors.text3)
    }
}

@Composable
private fun DmPreviewRow(c: Conversation, unread: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AstraAvatar(c.otherAvatarUrl, c.otherName, size = 44)
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
                text = c.preview,
                style = MaterialTheme.typography.bodySmall,
                color = if (unread) astraColors.text1 else astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (unread) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(9.dp).clip(CircleShape).background(astraColors.accent))
        }
    }
}

// Card de canal de voz com gente dentro agora — tap entra na call.
@Composable
private fun ActiveVoiceCard(room: ActiveVoiceRoom, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.accent.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("◉", color = astraColors.accent, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = room.channelName,
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MarginaliaLabel("${room.serverName} · ${room.count} na voz")
        }
        Text("entrar", style = MaterialTheme.typography.labelMedium, color = astraColors.accent)
    }
}

@Composable
private fun NewMessageFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(astraColors.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("✎", color = astraColors.base, fontSize = 22.sp)
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

@Composable
private fun SocketChip(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.Connected -> "online" to astraColors.success
        ConnectionState.Connecting -> "conectando" to astraColors.text3
        ConnectionState.Disconnected -> "offline" to astraColors.danger
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        MarginaliaLabel(label, color = color)
    }
}
