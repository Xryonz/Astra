package app.astra.mobile.feature.dm.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.upload.UploadFile
import app.astra.mobile.feature.gif.presentation.GifPicker
import app.astra.mobile.ui.components.ChatInputBar
import app.astra.mobile.ui.components.ChatMessageList
import app.astra.mobile.ui.components.ChatRow
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.DeleteMessageDialog
import app.astra.mobile.ui.components.edgeSwipeBack
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MessageListSkeleton
import app.astra.mobile.ui.components.PendingAttachmentsBar
import app.astra.mobile.ui.components.readImageBytes
import app.astra.mobile.ui.components.ReplyBanner
import app.astra.mobile.ui.components.TypingIndicator
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DmChatScreen(
    onBack: () -> Unit,
    viewModel: DmChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var deleteTarget by remember { mutableStateOf<ChatRow?>(null) }
    var gifOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    readImageBytes(context, uri)?.let { (b, m, n) -> UploadFile(b, m, n) }
                }
            }
            viewModel.attachImages(files)
        }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize().imePadding().edgeSwipeBack(onBack)) {
            EditorialTopBar(title = viewModel.otherName, marginalia = "sussurro", onBack = onBack)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {

                    state.loading && state.messages.isEmpty() -> MessageListSkeleton()
                    state.messages.isEmpty() -> Text(
                        text = "Diga oi 👋",
                        style = MaterialTheme.typography.bodyMedium,
                        color = astraColors.text2,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> {

                        val rows = remember(state.messages, state.translations) {
                            state.messages.map { m ->
                                ChatRow(
                                    id = m.id,
                                    mine = m.mine,
                                    authorName = m.authorName,
                                    authorAvatar = m.authorAvatar,
                                    content = m.content,
                                    replyAuthor = m.replyToAuthor,
                                    replyContent = m.replyToContent,
                                    attachments = m.attachments,
                                    translation = state.translations[m.id],
                                )
                            }
                        }

                        ChatMessageList(
                            rows = rows,
                            modifier = Modifier.fillMaxSize(),
                            canEdit = false,
                            onDelete = { deleteTarget = it },
                            onReply = { viewModel.startReply(it.id, it.authorName, it.content) },
                            onTranslate = { viewModel.translate(it.id, it.content) },
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

            PendingAttachmentsBar(
                attachments = state.pendingAttachments,
                onRemove = viewModel::removeAttachment,
            )

            ChatInputBar(
                text = state.input,
                sending = state.sending,
                onInput = viewModel::onInput,
                onSend = viewModel::send,
                onAttach = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onGif = { gifOpen = true },
                uploading = state.uploading,
                hasAttachments = state.pendingAttachments.isNotEmpty(),
            )
        }

        if (gifOpen) {
            GifPicker(
                onPick = { g ->
                    viewModel.addAttachment(
                        Attachment(
                            url = g.full,
                            type = "image/gif",
                            name = (g.title.ifBlank { "gif" }) + ".gif",
                            size = g.size,
                            width = g.width,
                            height = g.height,
                        ),
                    )
                },
                onClose = { gifOpen = false },
            )
        }
    }

    DeleteMessageDialog(
        open = deleteTarget != null,
        onConfirm = { deleteTarget?.let { viewModel.deleteMessage(it.id) }; deleteTarget = null },
        onDismiss = { deleteTarget = null },
    )
}
