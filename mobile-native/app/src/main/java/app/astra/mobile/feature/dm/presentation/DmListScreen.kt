package app.astra.mobile.feature.dm.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.ui.AstraCopy
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.ListSkeleton
import app.astra.mobile.ui.components.TopBarAction
import app.astra.mobile.ui.theme.astraColors

@Composable
fun DmListScreen(
    onBack: () -> Unit,
    onOpenConversation: (id: String, name: String) -> Unit,
    viewModel: DmListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.opened.collect { conv ->
            showDialog = false
            onOpenConversation(conv.conversationId, conv.otherName)
        }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = "Sussurros",
                marginalia = "mensagens diretas",
                onBack = onBack,
                trailing = { TopBarAction("+", onClick = { showDialog = true }) },
            )
            when {
                state.loading -> ListSkeleton(avatar = true)
                state.error != null -> CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
                        TextButton(onClick = viewModel::load) {
                            Text("Tentar de novo", color = astraColors.accent)
                        }
                    }
                }
                state.conversations.isEmpty() -> EmptyState(
                    line = AstraCopy.Empties.noDMs.title,
                    hint = "toque em + pra iniciar um sussurro",
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.conversations, key = { it.id }) { conv ->
                        ConversationRow(conv, unread = conv.id in state.unread) {
                            viewModel.markSeen(conv.id)
                            onOpenConversation(conv.id, conv.otherName)
                        }
                    }
                }
            }
        }
    }

    NewConversationDialog(
        open = showDialog,
        opening = state.opening,
        error = state.openError,
        onConfirm = viewModel::openConversation,
        onDismiss = { showDialog = false; viewModel.clearOpenError() },
    )
}

@Composable
private fun ConversationRow(conv: Conversation, unread: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AstraAvatar(conv.otherAvatarUrl, conv.otherName)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = conv.otherName,
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = conv.preview,
                style = MaterialTheme.typography.bodySmall,
                color = if (unread) astraColors.text1 else astraColors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (unread) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(astraColors.accent),
            )
        }
    }
}

@Composable
private fun NewConversationDialog(
    open: Boolean,
    opening: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember(open) { mutableStateOf("") }
    AstraDialog(
        open = open,
        onDismiss = { if (!opening) onDismiss() },
        title = AstraCopy.Action.startDM,
        confirmText = if (opening) "Abrindo..." else "Abrir",
        onConfirm = { onConfirm(username) },
        confirmEnabled = username.isNotBlank() && !opening,
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            singleLine = true,
            enabled = !opening,
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
private fun CenterBox(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
}
