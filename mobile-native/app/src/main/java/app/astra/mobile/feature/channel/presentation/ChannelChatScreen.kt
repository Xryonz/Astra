package app.astra.mobile.feature.channel.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import app.astra.mobile.feature.server.presentation.ServerMembersScreen
import app.astra.mobile.ui.components.puxarDaDireita
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
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
import app.astra.mobile.ui.components.AbaDeExpressao
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
import app.astra.mobile.ui.components.EmojiPickerSheet
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.EsperaDaConversa
import app.astra.mobile.ui.components.LinhaDeEspera
import app.astra.mobile.ui.components.PendingAttachmentsBar
import app.astra.mobile.ui.components.PinnedMessagesDialog
import app.astra.mobile.ui.components.PollComposer
import app.astra.mobile.ui.components.PollOptionUi
import app.astra.mobile.ui.components.PollUi
import app.astra.mobile.ui.components.readImageBytes
import app.astra.mobile.ui.components.ReplyBanner
import app.astra.mobile.ui.components.TopBarAction
import app.astra.mobile.ui.components.TypingIndicator
import app.astra.mobile.ui.components.Viagem
import app.astra.mobile.ui.components.viajante
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChannelChatScreen(
    onBack: () -> Unit,
    onOpenProfile: (String, String) -> Unit = { _, _ -> },
    temOrbita: Boolean = false,
    viewModel: ChannelChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var deleteTarget by remember { mutableStateOf<ChatRow?>(null) }
    var pinnedOpen by remember { mutableStateOf(false) }
    var pollOpen by remember { mutableStateOf(false) }
    var folhaAberta by remember { mutableStateOf<AbaDeExpressao?>(null) }
    var reactionTarget by remember { mutableStateOf<ChatRow?>(null) }
    var emojiPendente by remember { mutableStateOf<String?>(null) }

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

    var membrosAbertos by remember { mutableStateOf(false) }

    CosmicBackground {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .edgeSwipeBack(onBack)
                .then(if (temOrbita) Modifier.puxarDaDireita { membrosAbertos = true } else Modifier),
        ) {
            EditorialTopBar(
                title = "# ${viewModel.channelName}",
                onBack = onBack,
                modificadorDoTitulo = Modifier.viajante(Viagem.nomeDoCanal(viewModel.channelId), ehTexto = true),
                trailing = {
                    NotifBellAction(mode = state.notifMode, onSelect = viewModel::setNotifMode)
                    TopBarAction(Lucide.Pin, "Mensagens fixadas", onClick = { viewModel.loadPinned(); pinnedOpen = true })
                },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {

                    state.loading && state.messages.isEmpty() -> EsperaDaConversa(carregando = true)
                    state.messages.isEmpty() -> EmptyState(
                        line = "Silêncio nesta órbita",
                        hint = "solte a primeira transmissão",
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
                                    authorColor = m.authorColor,
                                    authorFont = m.authorFont,
                                    content = m.content,
                                    edited = m.edited,
                                    pinned = m.pinned,
                                    reactions = m.reactions.map { ReactionChip(it.emoji, it.count, it.mine) },
                                    replyAuthor = m.replyToAuthor,
                                    replyContent = m.replyToContent,
                                    attachments = m.attachments,
                                    translation = state.translations[m.id],
                                    kind = m.kind,
                                    criadaEm = m.createdAt,
                                    mencionaVoce = m.mencionaVoce,
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
                            vivo = !state.loading,
                            modifier = Modifier.fillMaxSize(),
                            canReact = true,
                            canPin = true,
                            onEdit = { viewModel.startEdit(it.id, it.content) },
                            onDelete = { deleteTarget = it },
                            onReply = { viewModel.startReply(it.id, it.authorName, it.content) },
                            onTogglePin = { viewModel.togglePin(it.id, !it.pinned) },
                            onToggleReaction = { row, emoji -> viewModel.toggleReaction(row.id, emoji) },
                            onMoreReactions = { reactionTarget = it },
                            onTranslate = { viewModel.translate(it.id, it.content) },
                            onVotePoll = { row, optionId -> viewModel.votePoll(row.id, optionId) },
                            onClosePoll = { viewModel.closePoll(it.id) },
                            onOpenProfile = onOpenProfile,
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
                rascunhoExterno = state.input,
                emojiPendente = emojiPendente,
                aoUsarEmoji = { emojiPendente = null },
                sending = state.sending,
                onInput = viewModel::onInput,
                onSend = viewModel::send,
                onAttach = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onGif = { folhaAberta = AbaDeExpressao.GIFS },
                onPoll = { pollOpen = true },
                onEmoji = { folhaAberta = AbaDeExpressao.EMOJIS },
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

        folhaAberta?.let { aba ->
            EmojiPickerSheet(
                onPick = { emoji ->
                    emojiPendente = emoji
                    folhaAberta = null
                },
                onClose = { folhaAberta = null },
                orbitaId = viewModel.orbitaId,
                abaInicial = aba,
                aoFigurinha = viewModel::enviarFigurinha,
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

        reactionTarget?.let { target ->
            EmojiPickerSheet(
                onPick = { emoji ->
                    viewModel.toggleReaction(target.id, emoji)
                    reactionTarget = null
                },
                onClose = { reactionTarget = null },
            )
        }

        AnimatedVisibility(
            visible = membrosAbertos,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(astraColors.void.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { membrosAbertos = false },
            )
        }

        AnimatedVisibility(
            visible = membrosAbertos,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(260)) { it },
            exit = slideOutHorizontally(tween(220)) { it },
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.84f)
                    .background(astraColors.base),
            ) {
                ServerMembersScreen(
                    onBack = { membrosAbertos = false },
                    onOpenProfile = onOpenProfile,
                )
            }
        }

        if (membrosAbertos) {
            BackHandler { membrosAbertos = false }
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
}

@Composable
private fun NotifBellAction(mode: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.dp, astraColors.borderMid, CircleShape)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (mode == "mute") Lucide.BellOff else Lucide.Bell,
                contentDescription = "Notificações do canal",
                tint = if (mode == "mute") astraColors.text3 else astraColors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(astraColors.overlay),
        ) {
            NotifModeRow("Tudo", selected = mode == "all") { open = false; onSelect("all") }
            NotifModeRow("Só menções", selected = mode == "mentions") { open = false; onSelect("mentions") }
            NotifModeRow("Silenciado", selected = mode == "mute") { open = false; onSelect("mute") }
            NotifModeRow("Padrão do servidor", selected = mode == null) { open = false; onSelect(null) }
        }
    }
}

@Composable
private fun NotifModeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(232.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) astraColors.accent else astraColors.text1,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Lucide.Check, contentDescription = null, tint = astraColors.accent, modifier = Modifier.size(16.dp))
        }
    }
}
