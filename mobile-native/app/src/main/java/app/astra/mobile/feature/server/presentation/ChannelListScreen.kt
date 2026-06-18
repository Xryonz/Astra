package app.astra.mobile.feature.server.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.feature.server.domain.model.Channel
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@Composable
fun ChannelListScreen(
    onBack: () -> Unit,
    onOpenChannel: (channelId: String, channelName: String) -> Unit,
    onOpenVoice: (serverId: String, channelId: String, channelName: String) -> Unit,
    viewModel: ChannelListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = viewModel.serverName,
                marginalia = "canais",
                onBack = onBack,
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
                state.channels.isEmpty() -> EmptyState(
                    line = "Nenhum canal visivel",
                    hint = "este servidor nao tem canais pra voce",
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.channels, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            onClick = {
                                if (channel.isVoice) onOpenVoice(viewModel.serverId, channel.id, channel.name)
                                else onOpenChannel(channel.id, channel.name)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, onClick: () -> Unit) {
    // Texto abre o chat; voz entra na chamada. Ambos clicaveis.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (channel.isVoice) "🔊" else "#",
            style = MaterialTheme.typography.titleMedium,
            color = if (channel.isVoice) astraColors.accent else astraColors.text3,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            color = astraColors.text1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (channel.isVoice) {
            MarginaliaLabel("voz", color = astraColors.text3)
        }
    }
}

@Composable
private fun CenterBox(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
}
