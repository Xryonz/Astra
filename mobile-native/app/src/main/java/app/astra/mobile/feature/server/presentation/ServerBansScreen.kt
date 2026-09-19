package app.astra.mobile.feature.server.presentation

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
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
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors
import zed.rainxch.rikkaui.components.ui.toast.LocalToastHostState
import zed.rainxch.rikkaui.components.ui.toast.ToastVariant

@Composable
fun ServerBansScreen(
    onBack: () -> Unit,
    viewModel: ServerBansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var unbanTarget by remember { mutableStateOf<BanUi?>(null) }

    val toastState = LocalToastHostState.current
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            toastState.show(it, variant = ToastVariant.Destructive)
            viewModel.clearActionError()
        }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = "Banimentos",
                marginalia = "${state.bans.size} banida${if (state.bans.size == 1) "" else "s"}",
                onBack = onBack,
            )

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                state.error != null -> Box(Modifier.padding(20.dp)) { AuthErrorBox(state.error!!) }
                state.bans.isEmpty() -> EmptyState(
                    line = "Ninguem banido",
                    hint = "estrelas banidas aparecem aqui",
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(state.bans, key = { it.userId }) { b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AstraAvatar(b.avatarUrl, b.name, size = 42)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = b.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = astraColors.text1,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MarginaliaLabel(b.reason?.takeIf { it.isNotBlank() } ?: "@${b.username}")
                            }
                            val shape = RoundedCornerShape(10.dp)
                            Text(
                                text = "Desbanir",
                                style = MaterialTheme.typography.labelLarge,
                                color = astraColors.accent,
                                modifier = Modifier
                                    .clip(shape)
                                    .border(1.dp, astraColors.border, shape)
                                    .clickable { unbanTarget = b }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    AstraDialog(
        open = unbanTarget != null,
        onDismiss = { unbanTarget = null },
        title = "Desbanir ${unbanTarget?.name ?: ""}?",
        confirmText = "Desbanir",
        onConfirm = { unbanTarget?.let { viewModel.unban(it.userId) }; unbanTarget = null },
    ) {
        MarginaliaLabel("volta a poder entrar com um convite")
    }
}
