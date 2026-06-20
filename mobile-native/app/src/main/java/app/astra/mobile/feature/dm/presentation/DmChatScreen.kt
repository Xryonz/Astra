package app.astra.mobile.feature.dm.presentation

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.ChatInputBar
import app.astra.mobile.ui.components.ChatMessageList
import app.astra.mobile.ui.components.ChatRow
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.DeleteMessageDialog
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MessageListSkeleton
import app.astra.mobile.ui.components.ReplyBanner
import app.astra.mobile.ui.components.TypingIndicator
import app.astra.mobile.ui.theme.astraColors

@Composable
fun DmChatScreen(
    onBack: () -> Unit,
    viewModel: DmChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var deleteTarget by remember { mutableStateOf<ChatRow?>(null) }

    CosmicBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            EditorialTopBar(title = viewModel.otherName, marginalia = "sussurro", onBack = onBack)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> MessageListSkeleton()
                    state.messages.isEmpty() -> Text(
                        text = "Diga oi 👋",
                        style = MaterialTheme.typography.bodyMedium,
                        color = astraColors.text2,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> {
                        // So re-mapeia quando as mensagens mudam — digitar no input
                        // (mesmo state) nao re-aloca a lista inteira.
                        val rows = remember(state.messages) {
                            state.messages.map { m ->
                                ChatRow(
                                    id = m.id,
                                    mine = m.mine,
                                    authorName = m.authorName,
                                    content = m.content,
                                    replyAuthor = m.replyToAuthor,
                                    replyContent = m.replyToContent,
                                )
                            }
                        }
                        // DM nao tem editar/reagir no backend -> so apagar + responder.
                        ChatMessageList(
                            rows = rows,
                            modifier = Modifier.fillMaxSize(),
                            canEdit = false,
                            onDelete = { deleteTarget = it },
                            onReply = { viewModel.startReply(it.id, it.authorName, it.content) },
                        )
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

            TypingIndicator(state.typingUsers)

            if (state.replyToId != null) {
                ReplyBanner(
                    author = state.replyToAuthor ?: "mensagem",
                    preview = state.replyToPreview.orEmpty(),
                    onCancel = viewModel::cancelReply,
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

    deleteTarget?.let { target ->
        DeleteMessageDialog(
            onConfirm = { viewModel.deleteMessage(target.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}
