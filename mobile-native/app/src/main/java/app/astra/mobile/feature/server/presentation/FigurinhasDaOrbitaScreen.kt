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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.ServerStickerDto
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
fun FigurinhasDaOrbitaScreen(
    onBack: () -> Unit,
    viewModel: FigurinhasDaOrbitaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val contexto = LocalContext.current

    var escolhida by remember { mutableStateOf<Triple<ByteArray, String, String>?>(null) }
    var aApagar by remember { mutableStateOf<ServerStickerDto?>(null) }

    val toast = LocalToastHostState.current
    LaunchedEffect(state.aviso) {
        state.aviso?.let {
            toast.show(it, variant = ToastVariant.Destructive)
            viewModel.esquecerAviso()
        }
    }

    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        escolhida = readImageBytes(contexto, uri)
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = "Figurinhas",
                marginalia = "${state.figurinhas.size} na constelação",
                onBack = onBack,
            )

            when {
                state.carregando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                state.erro != null -> Box(Modifier.padding(20.dp)) { AuthErrorBox(state.erro!!) }
                else -> Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MarginaliaLabel("vão inteiras, sem recompressão")
                        Spacer(Modifier.weight(1f))
                        val forma = RoundedCornerShape(10.dp)
                        Text(
                            text = if (state.subindo) "subindo..." else "+ adicionar",
                            style = MaterialTheme.typography.labelLarge,
                            color = astraColors.accent,
                            modifier = Modifier
                                .clip(forma)
                                .border(1.dp, astraColors.border, forma)
                                .clickable(enabled = !state.subindo) {
                                    seletor.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }

                    if (state.figurinhas.isEmpty()) {
                        MarginaliaLabel("nenhuma figurinha ainda — suba a primeira", Modifier.padding(20.dp))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(104.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.figurinhas, key = { it.id }) { fig ->
                                val forma = RoundedCornerShape(12.dp)
                                Column(
                                    modifier = Modifier
                                        .clip(forma)
                                        .border(1.dp, astraColors.border, forma)
                                        .clickable { aApagar = fig }
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AsyncImage(
                                        model = fig.url,
                                        contentDescription = fig.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = fig.name,
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

    escolhida?.let { (bytes, mime, arquivo) ->
        var nome by remember(bytes) { mutableStateOf("") }
        AstraDialog(
            open = true,
            onDismiss = { escolhida = null },
            title = "Nome da figurinha",
            confirmText = "Adicionar",
            confirmEnabled = nome.trim().isNotEmpty(),
            onConfirm = {
                viewModel.adicionar(nome, bytes, mime, arquivo)
                escolhida = null
            },
        ) {
            MarginaliaLabel("é assim que ela aparece na lista")
            Spacer(Modifier.height(12.dp))
            EditorialField(
                value = nome,
                onValue = { nome = it.take(40) },
                label = "nome",
                placeholder = "pensativo",
                enabled = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            )
        }
    }

    AstraDialog(
        open = aApagar != null,
        onDismiss = { aApagar = null },
        title = "Apagar ${aApagar?.name ?: ""}?",
        confirmText = "Apagar",
        onConfirm = { aApagar?.let { viewModel.apagar(it.id) }; aApagar = null },
    ) {
        MarginaliaLabel("some da lista para toda a constelação")
    }
}
