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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.feature.dm.domain.model.Conversation
import coil.compose.AsyncImage

@Composable
fun DmListScreen(
    onBack: () -> Unit,
    onOpenConversation: (id: String, name: String) -> Unit,
    viewModel: DmListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    // Conversa aberta com sucesso -> fecha o dialog e navega pro chat.
    LaunchedEffect(Unit) {
        viewModel.opened.collect { conv ->
            showDialog = false
            onOpenConversation(conv.conversationId, conv.otherName)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header(onBack = onBack, onNew = { showDialog = true })
        when {
            state.loading -> CenterBox {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.error != null -> CenterBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = viewModel::load) { Text("Tentar de novo") }
                }
            }
            state.conversations.isEmpty() -> CenterBox {
                Text(
                    text = "Nenhuma conversa ainda",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.conversations, key = { it.id }) { conv ->
                    ConversationRow(conv) { onOpenConversation(conv.id, conv.otherName) }
                }
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
        title = { Text("Nova conversa") },
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
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(username) },
                enabled = username.isNotBlank() && !opening,
            ) {
                Text(if (opening) "Abrindo..." else "Abrir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !opening) { Text("Cancelar") }
        },
    )
}

@Composable
private fun Header(onBack: () -> Unit, onNew: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Mensagens",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "+",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onNew).padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun ConversationRow(conv: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(conv.otherAvatarUrl, conv.otherName)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = conv.otherName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = conv.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Avatar(url: String?, name: String) {
    val mod = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = mod,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(mod, contentAlignment = Alignment.Center) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CenterBox(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
}
