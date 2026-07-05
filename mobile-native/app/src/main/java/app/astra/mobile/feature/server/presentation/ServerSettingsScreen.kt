package app.astra.mobile.feature.server.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import app.astra.mobile.ui.components.SETTINGS_ROW_STAGGER_MS
import app.astra.mobile.ui.components.SettingsGroup
import app.astra.mobile.ui.components.SettingsRow
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
    onOpenRoles: () -> Unit,
    onOpenBans: () -> Unit,
    onOpenEmojis: () -> Unit,
    onOpenChannels: () -> Unit,
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

            // — geral: gestao do servidor (gated). Um card suave, nao N caixas.
            val geralRows = buildList<@Composable () -> Unit> {
                if (state.canManageServer) {
                    add { SettingsRow("Visao geral", subtitle = "icone, nome, banner e convite", onClick = onOpenOverview) }
                }
                if (state.canManageChannels) {
                    add { SettingsRow("Orbitas", subtitle = "renomear, privar e apagar canais", onClick = onOpenChannels) }
                    add { SettingsRow("Emojis", subtitle = "emojis custom da constelacao", onClick = onOpenEmojis) }
                }
            }
            if (geralRows.isNotEmpty()) {
                SettingsGroup(label = "geral", rows = geralRows)
                Spacer(Modifier.height(20.dp))
            }

            // — comunidade: cascata continua a onda a partir do fim do grupo acima.
            val comunidadeRows = buildList<@Composable () -> Unit> {
                add { SettingsRow("Membros", subtitle = "estrelas desta constelacao", onClick = onOpenMembers) }
                if (state.canManageRoles) add { SettingsRow("Cargos", subtitle = "papeis e permissoes", onClick = onOpenRoles) }
                if (state.canManageServer) add { SettingsRow("Insignias", subtitle = "crie e conceda", onClick = onOpenBadges) }
                if (state.canBan) add { SettingsRow("Banimentos", subtitle = "quem nao pode voltar", onClick = onOpenBans) }
            }
            SettingsGroup(
                label = "comunidade",
                delayStartMs = geralRows.size * SETTINGS_ROW_STAGGER_MS,
                rows = comunidadeRows,
            )

            Spacer(Modifier.height(28.dp))
            SettingsGroup(
                delayStartMs = (geralRows.size + comunidadeRows.size) * SETTINGS_ROW_STAGGER_MS,
                rows = listOf {
                    SettingsRow(
                        title = if (state.isOwner) "Excluir constelacao" else "Desorbitar",
                        danger = true,
                        enabled = !state.working,
                        onClick = { if (state.isOwner) deleteOpen = true else leaveOpen = true },
                        trailing = null,
                    )
                },
            )
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
