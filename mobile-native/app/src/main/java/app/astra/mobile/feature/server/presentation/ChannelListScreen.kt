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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Context
import android.content.Intent
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import app.astra.mobile.BuildConfig
import app.astra.mobile.feature.server.domain.model.Channel
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.ListSkeleton
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@Composable
fun ChannelListScreen(
    onBack: () -> Unit,
    onOpenChannel: (channelId: String, channelName: String) -> Unit,
    onOpenVoice: (serverId: String, channelId: String, channelName: String) -> Unit,
    onOpenEdit: () -> Unit,
    viewModel: ChannelListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = viewModel.serverName,
                marginalia = "orbitas",
                onBack = onBack,
                trailing = {
                    if (state.isOwner) {
                        Icon(
                            Lucide.Settings,
                            contentDescription = "Editar constelacao",
                            tint = astraColors.accent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onOpenEdit)
                                .padding(6.dp)
                                .size(20.dp),
                        )
                    }
                    val code = state.inviteCode
                    if (!code.isNullOrBlank()) {
                        Text(
                            text = "Convidar",
                            style = MaterialTheme.typography.labelLarge,
                            color = astraColors.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { shareInvite(context, code) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                },
            )
            when {
                state.loading -> ListSkeleton(avatar = false)
                state.error != null -> CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
                        TextButton(onClick = viewModel::load) {
                            Text("Tentar de novo", color = astraColors.accent)
                        }
                    }
                }
                state.channels.isEmpty() -> EmptyState(
                    line = "Nenhuma orbita visivel",
                    hint = "esta constelacao nao tem orbitas pra voce",
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.channels, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            unread = channel.id in state.unread,
                            onClick = {
                                viewModel.markSeen(channel.id)
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
private fun ChannelRow(channel: Channel, unread: Boolean, onClick: () -> Unit) {
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
            color = if (unread) astraColors.text1 else astraColors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (unread) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(astraColors.accent),
            )
        } else if (channel.isVoice) {
            MarginaliaLabel("voz", color = astraColors.text3)
        }
    }
}

@Composable
private fun CenterBox(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
}

// Link /i/:code = pagina OG da API que redireciona pro /invite/:code do site
// (mesmo link que o web compartilha). Abre o share sheet do Android.
private fun shareInvite(context: Context, code: String) {
    val link = BuildConfig.BASE_URL.trimEnd('/') + "/i/" + code
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Entra na minha constelacao no Astra: $link")
    }
    context.startActivity(Intent.createChooser(send, "Compartilhar convite"))
}
