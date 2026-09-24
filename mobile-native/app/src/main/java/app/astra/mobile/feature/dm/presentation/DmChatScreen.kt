package app.astra.mobile.feature.dm.presentation

import app.astra.mobile.feature.friends.domain.model.Presence
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.StatusDot
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Phone
import android.net.Uri
import app.astra.mobile.core.deeplink.DeepLinkBus
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.upload.UploadFile
import app.astra.mobile.ui.components.AbaDeExpressao
import app.astra.mobile.ui.components.ChatInputBar
import app.astra.mobile.ui.components.ChatMessageList
import app.astra.mobile.ui.components.ChatRow
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.DeleteMessageDialog
import app.astra.mobile.ui.components.edgeSwipeBack
import app.astra.mobile.ui.components.EmojiPickerSheet
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.EsperaDaConversa
import app.astra.mobile.ui.components.LinhaDeEspera
import app.astra.mobile.ui.components.PendingAttachmentsBar
import app.astra.mobile.ui.components.readImageBytes
import app.astra.mobile.ui.components.ReplyBanner
import app.astra.mobile.ui.components.TypingIndicator
import app.astra.mobile.ui.components.Viagem
import app.astra.mobile.ui.components.viajante
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DmChatScreen(
    onBack: () -> Unit,
    onOpenProfile: (String, String) -> Unit = { _, _ -> },
    aoAbrirMeuPerfil: () -> Unit = {},
    pedirChamada: Boolean = false,
    aoAtenderPedido: () -> Unit = {},
    viewModel: DmChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val pedirMicrofone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { liberado ->
        if (liberado) viewModel.ligar()
    }
    val ligar = {
        val tem = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (tem) viewModel.ligar() else pedirMicrofone.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(pedirChamada) {
        if (pedirChamada) {
            aoAtenderPedido()
            ligar()
        }
    }
    var deleteTarget by remember { mutableStateOf<ChatRow?>(null) }
    var folhaAberta by remember { mutableStateOf<AbaDeExpressao?>(null) }
    var emojiPendente by remember { mutableStateOf<String?>(null) }

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
                onBack = onBack,
                modificadorDoTitulo = Modifier.viajante(Viagem.nomeDoSussurro(viewModel.conversationId), ehTexto = true),
                leading = {
                    Box {
                        AstraAvatar(
                            state.outroAvatar,
                            viewModel.otherName,
                            modifier = Modifier.viajante(Viagem.fotoDoSussurro(viewModel.conversationId)),
                            size = 38,
                        )
                        state.outroStatus?.let { presenca ->
                            StatusDot(
                                status = quandoStatus(presenca),
                                bordered = true,
                                borderColor = astraColors.base,
                                cutoutColor = astraColors.base,
                                modifier = Modifier.align(Alignment.BottomEnd),
                            )
                        }
                    }
                },
                trailing = {
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
                            .clickable {
                                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                ligar()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Lucide.Phone,
                            contentDescription = "Ligar",
                            tint = astraColors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {

                    state.loading && state.messages.isEmpty() -> EsperaDaConversa(carregando = true)
                    state.messages.isEmpty() -> EmptyState(
                        line = "Silêncio cósmico",
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
                                    criadaEm = m.createdAt,
                                )
                            }
                        }

                        ChatMessageList(
                            rows = rows,
                            vivo = !state.loading,
                            modifier = Modifier.fillMaxSize(),
                            canEdit = false,
                            onDelete = { deleteTarget = it },
                            onReply = { viewModel.startReply(it.id, it.authorName, it.content) },
                            onTranslate = { viewModel.translate(it.id, it.content) },
                            onOpenProfile = onOpenProfile,
                            aoAbrirMeuPerfil = aoAbrirMeuPerfil,
                        )
                    }
                }
                LinhaDeEspera(state.loading, Modifier.align(Alignment.TopCenter))
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
                rascunhoExterno = state.input,
                emojiPendente = emojiPendente,
                aoUsarEmoji = { emojiPendente = null },
                sending = state.sending,
                onInput = viewModel::onInput,
                onSend = viewModel::send,
                onAttach = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onGif = { folhaAberta = AbaDeExpressao.GIFS },
                onEmoji = { folhaAberta = AbaDeExpressao.EMOJIS },
                uploading = state.uploading,
                hasAttachments = state.pendingAttachments.isNotEmpty(),
            )
        }

        folhaAberta?.let { aba ->
            EmojiPickerSheet(
                onPick = { emoji ->
                    emojiPendente = emoji
                    folhaAberta = null
                },
                onClose = { folhaAberta = null },
                abaInicial = aba,
                aoGif = { g ->
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
            )
        }
    }

    DeleteMessageDialog(
        open = deleteTarget != null,
        onConfirm = { deleteTarget?.let { viewModel.deleteMessage(it.id) }; deleteTarget = null },
        onDismiss = { deleteTarget = null },
    )
}

private fun quandoStatus(presenca: Presence): UserStatus = when (presenca) {
    Presence.ONLINE -> UserStatus.ONLINE
    Presence.IDLE -> UserStatus.IDLE
    Presence.DND -> UserStatus.DND
    Presence.OFFLINE -> UserStatus.OFFLINE
}
