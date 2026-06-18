package app.astra.mobile.feature.channel.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.ChatInputBar
import app.astra.mobile.ui.components.ChatMessageList
import app.astra.mobile.ui.components.ChatRow
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.theme.astraColors

@Composable
fun ChannelChatScreen(
    onBack: () -> Unit,
    viewModel: ChannelChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    CosmicBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            EditorialTopBar(title = "# ${viewModel.channelName}", marginalia = "canal de texto", onBack = onBack)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CosmicSpinner(Modifier.align(Alignment.Center))
                    state.messages.isEmpty() -> Text(
                        text = "Comece a conversa neste canal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = astraColors.text2,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> {
                        // So re-mapeia quando as mensagens mudam — digitar no input
                        // (mesmo state) nao re-aloca a lista inteira.
                        val rows = remember(state.messages) {
                            state.messages.map { ChatRow(it.id, it.mine, it.authorName, it.content) }
                        }
                        ChatMessageList(rows = rows, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            if (state.error != null) {
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = astraColors.danger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            ChatInputBar(
                text = state.input,
                sending = state.sending,
                onInput = viewModel::onInput,
                onSend = viewModel::send,
            )
        }
    }
}
