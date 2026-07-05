package app.astra.mobile.feature.server.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.readImageBytes
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import zed.rainxch.rikkaui.components.ui.toast.LocalToastHostState
import zed.rainxch.rikkaui.components.ui.toast.ToastVariant

@Composable
fun ServerEmojisScreen(
    onBack: () -> Unit,
    viewModel: ServerEmojisViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    var pendingBytes by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    var editing by remember { mutableStateOf<EmojiUi?>(null) }
    var deleteTarget by remember { mutableStateOf<EmojiUi?>(null) }

    val toast = LocalToastHostState.current
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            toast.show(it, variant = ToastVariant.Destructive)
            viewModel.clearActionError()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        readImageBytes(ctx, uri)?.let { (bytes, mime, _) -> pendingBytes = bytes to mime }
    }
    val imageRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = "Emojis",
                marginalia = "${state.emojis.size}/50",
                onBack = onBack,
            )

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                state.error != null -> Box(Modifier.padding(20.dp)) { AuthErrorBox(state.error!!) }
                else -> Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MarginaliaLabel("use no chat como :nome:")
                        Spacer(Modifier.weight(1f))
                        val shape = RoundedCornerShape(10.dp)
                        Text(
                            text = if (state.uploading) "..." else "+ adicionar",
                            style = MaterialTheme.typography.labelLarge,
                            color = astraColors.accent,
                            modifier = Modifier
                                .clip(shape)
                                .border(1.dp, astraColors.border, shape)
                                .clickable(enabled = !state.uploading) { picker.launch(imageRequest) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }

                    if (state.emojis.isEmpty()) {
                        MarginaliaLabel("nenhum emoji ainda — adicione o primeiro", Modifier.padding(20.dp))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(90.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.emojis, key = { it.id }) { e ->
                                val shape = RoundedCornerShape(12.dp)
                                Column(
                                    modifier = Modifier
                                        .clip(shape)
                                        .border(1.dp, astraColors.border, shape)
                                        .clickable { editing = e }
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AsyncImage(
                                        model = e.url,
                                        contentDescription = e.name,
                                        modifier = Modifier.size(48.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = ":${e.name}:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = astraColors.text2,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Adicionar: escolheu imagem -> pede o nome.
    NameEmojiDialog(
        open = pendingBytes != null,
        onConfirm = { name ->
            pendingBytes?.let { (b, m) -> viewModel.addEmoji(name, b, m) }
            pendingBytes = null
        },
        onDismiss = { pendingBytes = null },
    )

    // Editar: renomear ou apagar.
    editing?.let { e ->
        AstraDialog(
            open = true,
            onDismiss = { editing = null },
            title = ":${e.name}:",
            confirmText = "Fechar",
            onConfirm = { editing = null },
            dismissText = null,
        ) {
            AsyncImage(model = e.url, contentDescription = e.name, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(14.dp))
            var newName by remember(e.id) { mutableStateOf(e.name) }
            EditorialField(
                value = newName, onValue = { newName = it.take(32) },
                label = "renomear", placeholder = "nome",
                enabled = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val shape = RoundedCornerShape(10.dp)
                Text(
                    text = "Salvar nome",
                    style = MaterialTheme.typography.labelLarge,
                    color = astraColors.accent,
                    modifier = Modifier
                        .clip(shape).border(1.dp, astraColors.border, shape)
                        .clickable { if (newName.trim() != e.name) viewModel.rename(e.id, newName); editing = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    text = "Apagar",
                    style = MaterialTheme.typography.labelLarge,
                    color = astraColors.danger,
                    modifier = Modifier
                        .clip(shape).border(1.dp, astraColors.border, shape)
                        .clickable { deleteTarget = e; editing = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    AstraDialog(
        open = deleteTarget != null,
        onDismiss = { deleteTarget = null },
        title = "Apagar :${deleteTarget?.name ?: ""}:?",
        confirmText = "Apagar",
        onConfirm = { deleteTarget?.let { viewModel.delete(it.id) }; deleteTarget = null },
    ) {
        MarginaliaLabel("some das mensagens que ja usaram")
    }
}

@Composable
private fun NameEmojiDialog(
    open: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return
    var name by remember { mutableStateOf("") }
    AstraDialog(
        open = true,
        onDismiss = onDismiss,
        title = "Nome do emoji",
        confirmText = "Adicionar",
        confirmEnabled = name.trim().length in 2..32,
        onConfirm = { onConfirm(name) },
    ) {
        MarginaliaLabel("2-32 chars · letras, numeros e _")
        Spacer(Modifier.height(12.dp))
        EditorialField(
            value = name, onValue = { name = it.take(32) },
            label = "nome (sem os :)", placeholder = "vibe",
            enabled = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
        )
    }
}
