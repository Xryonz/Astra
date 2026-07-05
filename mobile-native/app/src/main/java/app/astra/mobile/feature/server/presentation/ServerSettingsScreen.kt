package app.astra.mobile.feature.server.presentation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage

// Hub de configuracao da constelacao: secoes gated por permissao + sair/excluir.
@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit,
    onClosed: () -> Unit,
    onOpenOverview: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenBadges: () -> Unit,
    viewModel: ServerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var leaveOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.closed) {
        if (state.closed) onClosed()
    }

    // Nome/icone editados na Visao geral refletem aqui ao voltar.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Configurar constelacao", marginalia = "gestao", onBack = onBack)

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                    CosmicSpinner()
                }
                return@CosmicBackground
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(shape)
                        .background(astraColors.raised)
                        .border(1.dp, astraColors.borderMid, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!state.iconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = state.iconUrl,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize().clip(shape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = state.name.take(1).uppercase().ifBlank { "?" },
                            fontFamily = DmSerif,
                            fontSize = 24.sp,
                            color = astraColors.accent,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = state.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = astraColors.text1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MarginaliaLabel(if (state.isGroup) "grupo" else "constelacao")
                }
            }

            if (state.error != null) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { AuthErrorBox(state.error!!) }
            }

            Spacer(Modifier.height(8.dp))
            if (state.canManageServer) {
                MarginaliaLabel("— geral", Modifier.padding(start = 22.dp, bottom = 8.dp))
                HubRow("Visao geral", "icone, nome e visibilidade", onOpenOverview)
                Spacer(Modifier.height(20.dp))
            }

            MarginaliaLabel("— comunidade", Modifier.padding(start = 22.dp, bottom = 8.dp))
            HubRow("Membros", "estrelas desta constelacao", onOpenMembers)
            if (state.canManageServer) {
                HubRow("Insignias", "crie e conceda", onOpenBadges)
            }

            Spacer(Modifier.height(28.dp))
            val dangerShape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(dangerShape)
                    .background(astraColors.raised)
                    .border(1.dp, astraColors.danger.copy(alpha = 0.4f), dangerShape)
                    .clickable(enabled = !state.working) {
                        if (state.isOwner) deleteOpen = true else leaveOpen = true
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.isOwner) "Excluir constelacao" else "Desorbitar",
                    style = MaterialTheme.typography.titleMedium,
                    color = astraColors.danger,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    AstraDialog(
        open = leaveOpen,
        onDismiss = { leaveOpen = false },
        title = "Desorbitar?",
        confirmText = "Sair",
        onConfirm = { leaveOpen = false; viewModel.leave() },
    ) {
        MarginaliaLabel("voce sai de ${state.name}; pode voltar com um convite")
    }

    AstraDialog(
        open = deleteOpen,
        onDismiss = { deleteOpen = false },
        title = "Excluir constelacao?",
        confirmText = "Excluir",
        onConfirm = { deleteOpen = false; viewModel.deleteServer() },
    ) {
        MarginaliaLabel("apaga ${state.name} pra todo mundo — orbitas, mensagens, tudo. Sem volta.")
    }
}

@Composable
private fun HubRow(title: String, sub: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = astraColors.text1)
            MarginaliaLabel(sub)
        }
        Text(
            text = "›",
            fontFamily = DmSerif,
            style = MaterialTheme.typography.titleLarge,
            color = astraColors.text3,
        )
    }
}
