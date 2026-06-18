package app.astra.mobile.feature.server.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.TopBarAction
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
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

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = "Servidores",
                marginalia = "comunidades",
                onBack = onBack,
                trailing = { TopBarAction("+", onClick = { showDialog = true }) },
            )
            when {
                state.loading -> CenterBox { CosmicSpinner() }
                state.error != null -> CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
                        TextButton(onClick = viewModel::load) {
                            Text("Tentar de novo", color = astraColors.accent)
                        }
                    }
                }
                state.servers.isEmpty() -> EmptyState(
                    line = "Nenhum servidor ainda",
                    hint = "toque em + pra criar um",
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.servers, key = { it.id }) { server ->
                        ServerRow(server) { onOpenServer(server.id, server.name) }
                    }
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
private fun ServerRow(server: Server, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServerIcon(server.iconUrl, server.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${server.memberCount} membro${if (server.memberCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.text2,
            )
        }
        Text("›", fontFamily = DmSerif, color = astraColors.text3, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ServerIcon(url: String?, name: String) {
    val shape = RoundedCornerShape(14.dp)
    val mod = Modifier
        .size(46.dp)
        .clip(shape)
        .background(astraColors.raised)
        .border(1.dp, astraColors.borderMid, shape)
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, modifier = mod, contentScale = ContentScale.Crop)
    } else {
        Box(mod, contentAlignment = Alignment.Center) {
            Text(
                text = name.take(1).uppercase(),
                fontFamily = DmSerif,
                style = MaterialTheme.typography.titleLarge,
                color = astraColors.accent,
            )
        }
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
        containerColor = astraColors.overlay,
        title = { Text("Novo servidor", style = MaterialTheme.typography.titleLarge, color = astraColors.text1) },
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
                        color = astraColors.danger,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank() && !creating) {
                Text(if (creating) "Criando..." else "Criar", color = astraColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) {
                Text("Cancelar", color = astraColors.text2)
            }
        },
    )
}

@Composable
private fun CenterBox(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
}
