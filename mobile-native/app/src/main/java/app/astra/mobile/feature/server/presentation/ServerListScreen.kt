package app.astra.mobile.feature.server.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.astra.mobile.feature.server.domain.model.Server
import coil.compose.AsyncImage

@Composable
fun ServerListScreen(
    onBack: () -> Unit,
    onOpenServer: (id: String, name: String) -> Unit,
    viewModel: ServerListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.created.collect { showDialog = false }
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
            state.servers.isEmpty() -> CenterBox {
                Text(
                    text = "Nenhum servidor ainda. Toque em + pra criar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.servers, key = { it.id }) { server ->
                    ServerRow(server) { onOpenServer(server.id, server.name) }
                }
            }
        }
    }

    if (showDialog) {
        CreateServerDialog(
            creating = state.creating,
            error = state.createError,
            onConfirm = viewModel::createServer,
            onDismiss = { showDialog = false; viewModel.clearCreateError() },
        )
    }
}

@Composable
private fun CreateServerDialog(
    creating: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text("Novo servidor") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    enabled = !creating,
                    label = { Text("Nome do servidor") },
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
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank() && !creating) {
                Text(if (creating) "Criando..." else "Criar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) { Text("Cancelar") }
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
            text = "Servidores",
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
private fun ServerRow(server: Server, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServerIcon(server.iconUrl, server.name)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = server.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${server.memberCount} membro${if (server.memberCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerIcon(url: String?, name: String) {
    val mod = Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, modifier = mod, contentScale = ContentScale.Crop)
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
