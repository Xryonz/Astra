package app.astra.mobile.feature.dm.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.PhoneOff
import android.net.Uri
import app.astra.mobile.core.deeplink.DeepLinkBus
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.upload.UploadFile
import app.astra.mobile.feature.gif.presentation.GifPicker
import app.astra.mobile.ui.components.ChatInputBar
import app.astra.mobile.ui.components.ChatMessageList
import app.astra.mobile.ui.components.ChatRow
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.DeleteMessageDialog
import app.astra.mobile.ui.components.edgeSwipeBack
import app.astra.mobile.ui.components.EmojiPickerSheet
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MessageListSkeleton
import app.astra.mobile.ui.components.PendingAttachmentsBar
import app.astra.mobile.ui.components.readImageBytes
import app.astra.mobile.ui.components.ReplyBanner
import app.astra.mobile.ui.components.TypingIndicator
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DmChatScreen(
    onBack: () -> Unit,
    onJoinCall: (conversationId: String, name: String) -> Unit = { _, _ -> },
    onOpenProfile: (String, String) -> Unit = { _, _ -> },
    viewModel: DmChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var deleteTarget by remember { mutableStateOf<ChatRow?>(null) }
    var gifOpen by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }

    // Outro lado aceitou a ligacao -> entra na sala.
    LaunchedEffect(Unit) {
        viewModel.joinCall.collect { onJoinCall(viewModel.conversationId, viewModel.otherName) }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hapticsOn = LocalAppPrefs.current.haptics
    val haptic = LocalHapticFeedback.current
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

    // Conteudo do Direct Share (texto vira rascunho, imagem vira anexo).
    LaunchedEffect(Unit) {
        val share = DeepLinkBus.pendingShare.value ?: return@LaunchedEffect
        if (share.conversationId != viewModel.conversationId) return@LaunchedEffect
        DeepLinkBus.pendingShare.value = null
        share.text?.takeIf { it.isNotBlank() }?.let { viewModel.onInput(it) }
        share.imageUri?.let { raw ->
            val file = withContext(Dispatchers.IO) {
                readImageBytes(context, Uri.parse(raw))?.let { (b, m, n) -> UploadFile(b, m, n) }
            }
            if (file != null) viewModel.attachImages(listOf(file))
        }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize().imePadding().edgeSwipeBack(onBack)) {
            EditorialTopBar(
                title = viewModel.otherName,
                marginalia = if (state.ringing) "chamando..." else "sussurro",
                onBack = onBack,
                trailing = {
                    // Sino: silencia/reativa a conversa (icone cortado = mutada).
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.toggleMute() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.muted) Lucide.BellOff else Lucide.Bell,
                            contentDescription = if (state.muted) "Reativar notificações" else "Silenciar conversa",
                            tint = if (state.muted) astraColors.text3 else astraColors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (state.ringing) astraColors.accentDim else Color.Transparent)
                            .clickable {
                                if (hapticsOn) {
                                    haptic.performHapticFeedback(
                                        if (state.ringing) HapticFeedbackType.Reject else HapticFeedbackType.Confirm,
                                    )
                                }
                                if (state.ringing) viewModel.cancelCall() else viewModel.startCall()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.ringing) Lucide.PhoneOff else Lucide.Phone,
                            contentDescription = if (state.ringing) "Cancelar ligação" else "Ligar",
                            tint = astraColors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {

                    state.loading && state.messages.isEmpty() -> MessageListSkeleton()
                    state.messages.isEmpty() -> EmptyState(
                        line = "Silencio cosmico",
                        hint = "diga oi 👋",
                    )
                    else -> {

                        val rows = remember(state.messages, state.translations) {
                            state.messages.map { m ->
                                ChatRow(
                                    id = m.id,
                                    mine = m.mine,
                                    authorId = m.authorId,
                                    authorName = m.authorName,
                                    authorAvatar = m.authorAvatar,
                                    authorFont = m.authorFont,
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
                            onOpenProfile = onOpenProfile,
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
                onEmoji = { emojiOpen = true },
                uploading = state.uploading,
                hasAttachments = state.pendingAttachments.isNotEmpty(),
            )
        }

        if (emojiOpen) {
            EmojiPickerSheet(
                onPick = { emoji ->
                    viewModel.onInput(state.input + emoji)
                    emojiOpen = false
                },
                onClose = { emojiOpen = false },
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
