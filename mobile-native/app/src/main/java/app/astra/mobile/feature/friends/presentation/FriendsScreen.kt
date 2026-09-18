package app.astra.mobile.feature.friends.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.feature.friends.domain.model.Friend
import app.astra.mobile.feature.friends.domain.model.FriendRequest
import app.astra.mobile.feature.friends.domain.model.Presence
import app.astra.mobile.ui.AstraCopy
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.AstraTabs
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.ListSkeleton
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.TopBarAction
import app.astra.mobile.ui.theme.astraColors

@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onOpenProfile: (userId: String, name: String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.added.collect { showAdd = false }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = "Estrelas",
                marginalia = "sua constelacao de amigos",
                onBack = onBack,
                trailing = { TopBarAction("+", onClick = { showAdd = true }) },
            )

            AstraTabs(
                tabs = listOf("Estrelas", "Pedidos", "Enviados"),
                counts = listOf(state.friends.size, state.incoming.size, state.outgoing.size),
                selectedIndex = state.tab.ordinal,
                onSelect = { viewModel.selectTab(FriendsTab.values()[it]) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )

            when {
                state.loading -> ListSkeleton(avatar = true)
                state.error != null -> CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
                        TextButton(onClick = viewModel::load) { Text("Tentar de novo", color = astraColors.accent) }
                    }
                }
                else -> when (state.tab) {
                    FriendsTab.AMIGOS ->
                        if (state.friends.isEmpty()) EmptyState(AstraCopy.Empties.noFriends.title, "toque em + pra alinhar uma estrela")
                        else LazyColumn(Modifier.fillMaxSize()) {
                            items(state.friends, key = { it.friendshipId }) { f ->
                                FriendRow(
                                    f,
                                    onClick = { onOpenProfile(f.userId, f.displayName) },
                                    onRemove = { viewModel.remove(f.friendshipId) },
                                )
                            }
                        }

                    FriendsTab.PEDIDOS ->
                        if (state.incoming.isEmpty()) EmptyState("Nenhum pedido", "convites recebidos aparecem aqui")
                        else LazyColumn(Modifier.fillMaxSize()) {
                            items(state.incoming, key = { it.friendshipId }) { r ->
                                RequestRow(
                                    r,
                                    primaryLabel = "Aceitar",
                                    onPrimary = { viewModel.accept(r.friendshipId) },
                                    onSecondary = { viewModel.remove(r.friendshipId) },
                                )
                            }
                        }

                    FriendsTab.ENVIADOS ->
                        if (state.outgoing.isEmpty()) EmptyState("Nada enviado", "seus convites pendentes aparecem aqui")
                        else LazyColumn(Modifier.fillMaxSize()) {
                            items(state.outgoing, key = { it.friendshipId }) { r ->
                                RequestRow(
                                    r,
                                    primaryLabel = null,
                                    onPrimary = {},
                                    onSecondary = { viewModel.remove(r.friendshipId) },
                                )
                            }
                        }
                }
            }
        }
    }

    AddFriendDialog(
        open = showAdd,
        adding = state.adding,
        error = state.addError,
        onConfirm = viewModel::sendRequest,
        onDismiss = { showAdd = false; viewModel.clearAddError() },
    )
}

@Composable
private fun FriendRow(f: Friend, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarWithPresence(f.avatarUrl, f.displayName, f.presence)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = f.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = f.customStatus?.takeIf { it.isNotBlank() } ?: presenceLabel(f.presence),
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "✕",
            style = MaterialTheme.typography.titleMedium,
            color = astraColors.text3,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onRemove).padding(8.dp),
        )
    }
}

@Composable
private fun RequestRow(
    r: FriendRequest,
    primaryLabel: String?,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AstraAvatar(r.avatarUrl, r.displayName)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = r.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MarginaliaLabel("@${r.username}")
        }
        if (primaryLabel != null) {
            Pill(primaryLabel, accent = true, onClick = onPrimary)
            Spacer(Modifier.width(8.dp))
        }
        Pill(if (primaryLabel == null) "Cancelar" else "Recusar", accent = false, onClick = onSecondary)
    }
}

@Composable
private fun AvatarWithPresence(url: String?, name: String, presence: Presence) {
    Box {
        AstraAvatar(url, name)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(14.dp)
                .clip(CircleShape)
                .background(astraColors.void)
                .padding(2.dp)
                .clip(CircleShape)
                .background(presenceColor(presence)),
        )
    }
}

@Composable
private fun Pill(label: String, accent: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .then(
                if (accent) Modifier.background(astraColors.accent)
                else Modifier.border(1.dp, astraColors.borderMid, shape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (accent) astraColors.textInv else astraColors.text1,
        )
    }
}

@Composable
private fun AddFriendDialog(
    open: Boolean,
    adding: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember(open) { mutableStateOf("") }
    AstraDialog(
        open = open,
        onDismiss = { if (!adding) onDismiss() },
        title = AstraCopy.Action.addStar,
        confirmText = if (adding) "Enviando..." else "Adicionar",
        onConfirm = { onConfirm(username) },
        confirmEnabled = username.isNotBlank() && !adding,
    ) {
        Text(
            "Alinhe uma estrela pelo @username (ou pela coordenada Astra).",
            style = MaterialTheme.typography.bodySmall,
            color = astraColors.text2,
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            singleLine = true,
            enabled = !adding,
            label = { Text("@username") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.danger,
            )
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun presenceColor(p: Presence): Color = when (p) {
    Presence.ONLINE -> astraColors.success
    Presence.IDLE -> astraColors.warning
    Presence.DND -> astraColors.danger
    Presence.OFFLINE -> astraColors.text3
}

private fun presenceLabel(p: Presence): String = when (p) {
    Presence.ONLINE -> AstraCopy.statusLabel("ONLINE")
    Presence.IDLE -> AstraCopy.statusLabel("IDLE")
    Presence.DND -> AstraCopy.statusLabel("DND")
    Presence.OFFLINE -> AstraCopy.statusLabel("OFFLINE")
}
