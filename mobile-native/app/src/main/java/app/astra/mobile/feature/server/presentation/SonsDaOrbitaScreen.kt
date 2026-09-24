package app.astra.mobile.feature.server.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.ServerSoundDto
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Trash2
import zed.rainxch.rikkaui.components.ui.toast.LocalToastHostState
import zed.rainxch.rikkaui.components.ui.toast.ToastVariant

@Composable
fun SonsDaOrbitaScreen(
    onBack: () -> Unit,
    viewModel: SonsDaOrbitaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var aApagar by remember { mutableStateOf<ServerSoundDto?>(null) }

    val toast = LocalToastHostState.current
    LaunchedEffect(state.aviso) {
        state.aviso?.let {
            toast.show(it, variant = ToastVariant.Destructive)
            viewModel.esquecerAviso()
        }
    }

    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.adicionar(it) }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = "Sons",
                marginalia = "${state.sons.size} na constelação",
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
                        MarginaliaLabel("tocam para todos na call")
                        Spacer(Modifier.weight(1f))
                        val forma = RoundedCornerShape(10.dp)
                        Text(
                            text = if (state.subindo) "convertendo..." else "+ adicionar",
                            style = MaterialTheme.typography.labelLarge,
                            color = astraColors.accent,
                            modifier = Modifier
                                .clip(forma)
                                .border(1.dp, astraColors.border, forma)
                                .clickable(enabled = !state.subindo) { seletor.launch("audio/*") }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }

                    if (state.sons.isEmpty()) {
                        MarginaliaLabel("nenhum som ainda — qualquer formato serve", Modifier.padding(20.dp))
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.sons, key = { it.id }) { som ->
                                LinhaDeSom(
                                    som = som,
                                    aoOuvir = { viewModel.ouvir(som.url) },
                                    aoApagar = { aApagar = som },
                                )
                            }
                        }
                    }
                }
            }
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

@Composable
private fun LinhaDeSom(
    som: ServerSoundDto,
    aoOuvir: () -> Unit,
    aoApagar: () -> Unit,
) {
    val forma = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .border(1.dp, astraColors.border, forma)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = aoOuvir),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.Play,
                contentDescription = "ouvir ${som.name}",
                tint = astraColors.accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = som.name,
                style = MaterialTheme.typography.bodyMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (som.durationMs > 0) MarginaliaLabel("%.1fs".format(som.durationMs / 1000f))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = aoApagar),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.Trash2,
                contentDescription = "apagar ${som.name}",
                tint = astraColors.danger,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
