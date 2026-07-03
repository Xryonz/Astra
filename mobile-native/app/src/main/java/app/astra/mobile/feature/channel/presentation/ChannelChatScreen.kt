package app.astra.mobile.feature.channel.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import app.astra.mobile.feature.channel.domain.model.MessageEdit
import app.astra.mobile.feature.gif.presentation.GifPicker
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.ChatInputBar
import app.astra.mobile.ui.components.ChatMessageList
import app.astra.mobile.ui.components.ChatRow
import app.astra.mobile.ui.components.ReactionChip
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.DeleteMessageDialog
import app.astra.mobile.ui.components.edgeSwipeBack
import app.astra.mobile.ui.components.EditingBanner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.MessageListSkeleton
import app.astra.mobile.ui.components.PendingAttachmentsBar
import app.astra.mobile.ui.components.PinnedMessagesDialog
import app.astra.mobile.ui.components.PollComposer
import app.astra.mobile.ui.components.PollOptionUi
import app.astra.mobile.ui.components.PollUi
import app.astra.mobile.ui.components.readImageBytes
import app.astra.mobile.ui.components.ReplyBanner
import app.astra.mobile.ui.components.TopBarAction
import app.astra.mobile.ui.components.TypingIndicator
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChannelChatScreen(
    onBack: () -> Unit,
    viewModel: ChannelChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var deleteTarget by remember { mutableStateOf<ChatRow?>(null) }
    var pinnedOpen by remember { mutableStateOf(false) }
    var gifOpen by remember { mutableStateOf(false) }
    var pollOpen by remember { mutableStateOf(false) }

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
            EditorialTopBar(
                title = "# ${viewModel.channelName}",
                marginalia = "orbita de texto",
                onBack = onBack,
                trailing = { TopBarAction("📌", onClick = { viewModel.loadPinned(); pinnedOpen = true }) },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {

                    state.loading && state.messages.isEmpty() -> MessageListSkeleton()
                    state.messages.isEmpty() -> EmptyState(
                        line = "Silencio nesta orbita",
                        hint = "solte a primeira transmissao",
                    )
                    else -> {

                        val rows = remember(state.messages, state.translations) {
                            state.messages.map { m ->
                                ChatRow(
                                    id = m.id,
                                    mine = m.mine,
                                    authorName = m.authorName,
                                    authorAvatar = m.authorAvatar,
                                    authorColor = m.authorColor,
                                    content = m.content,
                                    edited = m.edited,
                                    pinned = m.pinned,
                                    reactions = m.reactions.map { ReactionChip(it.emoji, it.count, it.mine) },
                                    replyAuthor = m.replyToAuthor,
                                    replyContent = m.replyToContent,
                                    attachments = m.attachments,
                                    translation = state.translations[m.id],
                                    poll = m.poll?.let { p ->
                                        PollUi(
                                            question = p.question,
                                            options = p.options.map { o -> PollOptionUi(o.id, o.text, o.votes, o.mine) },
                                            allowMultiple = p.allowMultiple,
                                            expiresAt = p.expiresAt,
                                            closed = p.closed,
                                        )
                                    },
                                )
                            }
                        }
                        ChatMessageList(
                            rows = rows,
                            modifier = Modifier.fillMaxSize(),
                            canReact = true,
                            canPin = true,
                            onEdit = { viewModel.startEdit(it.id, it.content) },
                            onDelete = { deleteTarget = it },
                            onReply = { viewModel.startReply(it.id, it.authorName, it.content) },
                            onTogglePin = { viewModel.togglePin(it.id, !it.pinned) },
                            onToggleReaction = { row, emoji -> viewModel.toggleReaction(row.id, emoji) },
                            onTranslate = { viewModel.translate(it.id, it.content) },
                            onVotePoll = { row, optionId -> viewModel.votePoll(row.id, optionId) },
                            onClosePoll = { viewModel.closePoll(it.id) },
                            onHistory = { viewModel.loadEditHistory(it.id) },
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

            if (state.editingId != null) EditingBanner(onCancel = viewModel::cancelEdit)
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
                onPoll = { pollOpen = true },
                uploading = state.uploading,
                hasAttachments = state.pendingAttachments.isNotEmpty(),
            )
        }

        if (pollOpen) {
            PollComposer(
                onCreate = { question, options, allowMultiple, durationHours ->
                    viewModel.createPoll(question, options, allowMultiple, durationHours)
                },
                onClose = { pollOpen = false },
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

    PinnedMessagesDialog(
        open = pinnedOpen,
        items = state.pinned.map { it.authorName to it.content },
        onDismiss = { pinnedOpen = false },
    )

    EditHistoryDialog(
        open = state.editHistory != null || state.editHistoryLoading,
        loading = state.editHistoryLoading,
        edits = state.editHistory.orEmpty(),
        onDismiss = viewModel::closeEditHistory,
    )
}

@Composable
private fun EditHistoryDialog(
    open: Boolean,
    loading: Boolean,
    edits: List<MessageEdit>,
    onDismiss: () -> Unit,
) {
    AstraDialog(
        open = open,
        onDismiss = onDismiss,
        title = "Historico de edicoes",
        confirmText = "Fechar",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Text("Carregando…", style = MaterialTheme.typography.bodyMedium, color = astraColors.text3)
            }
            edits.isEmpty() -> Text(
                "Sem versoes anteriores guardadas.",
                style = MaterialTheme.typography.bodyMedium,
                color = astraColors.text2,
            )
            else -> Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                edits.forEach { e ->
                    Column {
                        MarginaliaLabel("versao anterior · ${editRelTime(e.editedAt)}")
                        Spacer(Modifier.height(3.dp))
                        Text(e.content, style = MaterialTheme.typography.bodyMedium, color = astraColors.text1)
                    }
                }
            }
        }
    }
}

private fun editRelTime(iso: String?): String {
    if (iso == null) return ""
    return runCatching {
        val then = java.time.Instant.parse(iso)
        val sec = java.time.Duration.between(then, java.time.Instant.now()).seconds.coerceAtLeast(0)
        when {
            sec < 60 -> "agora"
            sec < 3600 -> "${sec / 60}m atras"
            sec < 86400 -> "${sec / 3600}h atras"
            sec < 2592000 -> "${sec / 86400}d atras"
            else -> "${sec / 2592000}mes atras"
        }
    }.getOrDefault("")
}
