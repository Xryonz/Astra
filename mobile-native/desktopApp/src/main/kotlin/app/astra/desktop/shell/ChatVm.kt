package app.astra.desktop.shell

import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.net.FalhaDeRede
import app.astra.desktop.net.FastSendResult
import app.astra.desktop.net.insistir
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.dto.ApiError
import app.astra.mobile.core.network.dto.AttachmentDto
import app.astra.mobile.core.network.dto.ChannelMessageDto
import app.astra.mobile.core.network.dto.ChannelTypingEventDto
import app.astra.mobile.core.network.dto.DmDeletedEventDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.DmTypingEventDto
import app.astra.mobile.core.network.dto.EditChannelRequest
import app.astra.mobile.core.network.dto.GifResultDto
import app.astra.mobile.core.network.dto.ServerStickerDto
import app.astra.mobile.core.network.dto.MessageDeletedEventDto
import app.astra.mobile.core.network.dto.MessageEditedEventDto
import app.astra.mobile.core.network.dto.CreatePollRequest
import app.astra.mobile.core.network.dto.MsgAuthorDto
import app.astra.mobile.core.network.dto.CallLogDto
import app.astra.mobile.core.network.dto.PollDto
import app.astra.mobile.core.network.dto.PollUpdateDto
import app.astra.mobile.core.network.dto.ReactRequest
import app.astra.mobile.core.network.dto.VoteRequest
import app.astra.mobile.core.network.dto.ReactionDto
import app.astra.mobile.core.network.dto.ReactionUpdateDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ReplyToDto
import app.astra.mobile.core.network.dto.SendChannelRequest
import app.astra.mobile.core.network.dto.SendDmRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.nio.file.Files

sealed interface ChatTarget {
    val id: String
    val title: String

    data class Channel(override val id: String, override val title: String) : ChatTarget
    data class Dm(override val id: String, override val title: String) : ChatTarget
}

data class ChatMessage(
    val id: String,
    val content: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String?,
    val authorFont: String? = null,
    val createdAt: String?,
    val mine: Boolean = false,
    val edited: Boolean = false,
    val reactions: List<ReactionDto> = emptyList(),
    val mentions: List<String> = emptyList(),
    val replyTo: ReplyToDto? = null,
    val attachments: List<AttachmentDto> = emptyList(),
    val poll: PollDto? = null,
    val call: CallLogDto? = null,
    val deleting: Boolean = false,
    val clientNonce: String? = null,
    val pending: Boolean = false,
    val failed: Boolean = false,
)

data class PendingFile(val file: File, val mime: String)

fun expirada(poll: PollDto): Boolean {
    val fim = poll.expiresAt ?: return false
    val instante = runCatching { java.time.Instant.parse(fim) }.getOrNull() ?: return false
    return instante.isBefore(java.time.Instant.now())
}

data class ChatUiState(
    val loading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val replyingTo: ChatMessage? = null,
    val typing: Map<String, String> = emptyMap(),
    val pending: List<PendingFile> = emptyList(),
    val error: String? = null,
    val acordando: Boolean = false,
    val errorPermanente: Boolean = false,
)

private const val PAGE = 50
private const val FADE_OUT_MS = 340L
private const val FAST_SEND_TIMEOUT_MS = 6_000L

private const val TYPING_RESEND_MS = 3_000L
private const val TYPING_IDLE_MS = 3_000L
private const val TYPING_EXPIRY_MS = 8_000L

private const val MAX_FILE_BYTES = 25L * 1024 * 1024
private const val MAX_FILES = 10
private val ALLOWED_MIMES = setOf(
    "image/png", "image/jpeg", "image/gif", "image/webp", "image/avif",
    "video/mp4", "video/webm", "video/quicktime",
    "audio/mpeg", "audio/wav", "audio/ogg", "audio/webm", "audio/mp4", "audio/x-m4a", "audio/aac",
    "application/pdf", "text/plain", "application/zip", "application/json",
)

class ChatVm(
    private val scope: CoroutineScope,
    private val target: ChatTarget,
    private val channelApi: ChannelApi,
    private val dmApi: DmApi,
    private val uploadApi: UploadApi,
    private val socket: DesktopSocket,
    private val json: Json,
    val myId: String?,
    private val cache: CacheDeConversas,
    private val myProfile: () -> ProfileUserDto? = { null },
) {
    private val _state = MutableStateFlow(
        cache.ler(target)
            ?.let { ChatUiState(loading = false, messages = it) }
            ?: ChatUiState(),
    )
    val state = _state.asStateFlow()

    private var liveJob: Job? = null
    private var cargaJob: Job? = null
    private var typingIdleJob: Job? = null
    private var lastTypingEmit = 0L
    private val typingExpiry = mutableMapOf<String, Job>()

    init {
        load()
        listenLive()
        if (target is ChatTarget.Channel) socket.joinChannel(target.id)
        markRead()
    }

    fun dispose() {
        cache.guardar(target, _state.value.messages)
        liveJob?.cancel()
        cargaJob?.cancel()
        stopTypingEmit()
        if (target is ChatTarget.Channel) socket.leaveChannel(target.id)
    }

    fun tentarDeNovo() = load(forcar = true)

    private fun load(forcar: Boolean = false) {
        if (!forcar && cargaJob?.isActive == true) return
        cargaJob?.cancel()
        cargaJob = scope.launch {
            _state.update { it.copy(loading = it.messages.isEmpty(), error = null, acordando = false) }
            insistir(
                oQue = "esta conversa",
                aoTentarDeNovo = { _state.update { it.copy(acordando = true) } },
            ) {
                when (target) {
                    is ChatTarget.Channel ->
                        channelApi.messages(target.id, null, PAGE).data?.items.orEmpty().map { it.toChat() }
                    is ChatTarget.Dm ->
                        dmApi.messages(target.id, null, PAGE).data?.items.orEmpty().map { it.toChat() }
                }
            }
                .onSuccess { list ->
                    val frescas = list.sortedBy { m -> m.createdAt ?: "" }
                    cache.guardar(target, frescas)
                    _state.update {
                        it.copy(
                            loading = false, error = null, acordando = false,
                            messages = frescas,
                        )
                    }
                }
                .onFailure { t ->
                    val f = (t as? FalhaDeRede)?.falha
                    _state.update {
                        it.copy(
                            loading = false, acordando = false,
                            error = f?.motivo ?: "Não foi possível carregar esta conversa.",
                            errorPermanente = f?.permanente == true,
                        )
                    }
                }
        }
    }

    fun send(text: String, allowFast: Boolean = true) {
        val content = text.trim()
        val pending = _state.value.pending
        if ((content.isEmpty() && pending.isEmpty()) || _state.value.sending) return
        val replyToId = _state.value.replyingTo?.id

        if (allowFast && content.isNotEmpty() && pending.isEmpty() && replyToId == null && socket.isConnected()) {
            optimisticSend(target, content)
            return
        }

        stopTypingEmit()
        _state.update { it.copy(sending = true, error = null) }
        scope.launch {
            val attachments = if (pending.isEmpty()) emptyList() else {
                val uploaded = withContext(Dispatchers.IO) {
                    runCatching {
                        val parts = pending.map { pf ->
                            MultipartBody.Part.createFormData(
                                "files", pf.file.name,
                                pf.file.readBytes().toRequestBody(pf.mime.toMediaTypeOrNull()),
                            )
                        }
                        uploadApi.uploadMany(parts).data?.attachments.orEmpty()
                    }.getOrNull()
                }
                if (uploaded.isNullOrEmpty()) {
                    _state.update { it.copy(sending = false, error = "Não foi possível subir o anexo") }
                    return@launch
                }
                uploaded
            }
            val result = runCatching {
                when (target) {
                    is ChatTarget.Channel ->
                        channelApi.send(
                            target.id,
                            SendChannelRequest(content, replyToId = replyToId, attachments = attachments),
                        ).data?.toChat()
                    is ChatTarget.Dm ->
                        dmApi.send(
                            target.id,
                            SendDmRequest(content, replyToId = replyToId, attachments = attachments),
                        ).data?.toChat()
                }
            }
            result
                .onSuccess { msg ->
                    _state.update {
                        it.copy(
                            sending = false,
                            replyingTo = null,
                            pending = emptyList(),
                            messages = if (msg != null && it.messages.none { m -> m.id == msg.id }) it.messages + msg else it.messages,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(sending = false, error = sendError(t, "Mensagem não enviada")) } }
        }
    }

    private fun optimisticSend(target: ChatTarget, content: String) {
        val nonce = java.util.UUID.randomUUID().toString()
        val me = myProfile()
        val temp = ChatMessage(
            id = "tmp:$nonce",
            content = content,
            authorId = myId ?: "",
            authorName = me?.displayName ?: me?.username ?: "você",
            authorAvatar = me?.avatarUrl,
            authorFont = me?.displayFont,
            createdAt = java.time.Instant.now().toString(),
            mine = true,
            clientNonce = nonce,
            pending = true,
        )
        stopTypingEmit()
        _state.update { it.copy(messages = it.messages + temp, replyingTo = null, error = null) }

        val onResult: (FastSendResult) -> Unit = { result ->
            if (!result.ok) {
                if (result.error == "NO_ACK") fallbackToHttp(nonce, content)
                else markFailed(nonce, fastError(result))
            }
        }
        when (target) {
            is ChatTarget.Channel -> socket.fastSendText(target.id, content, nonce, onResult)
            is ChatTarget.Dm -> socket.fastSendDm(target.id, content, nonce, onResult)
        }

        scope.launch {
            delay(FAST_SEND_TIMEOUT_MS)
            fallbackToHttp(nonce, content)
        }
    }

    private fun fallbackToHttp(nonce: String, content: String) {
        var fire = false
        _state.update { st ->
            if (st.messages.none { it.clientNonce == nonce && it.pending }) return@update st
            fire = true
            st.copy(messages = st.messages.filterNot { it.clientNonce == nonce && it.pending })
        }
        if (fire) send(content, allowFast = false)
    }

    private fun markFailed(nonce: String, error: String) {
        _state.update { st ->
            if (st.messages.none { it.clientNonce == nonce && it.pending }) return@update st
            st.copy(
                messages = st.messages.map {
                    if (it.clientNonce == nonce && it.pending) it.copy(pending = false, failed = true) else it
                },
                error = error,
            )
        }
    }

    private fun fastError(r: FastSendResult): String {
        if (r.code == "MUTED" || r.code == "SPAM_MUTED") {
            val s = r.secondsLeft ?: 0
            val falta = if (s >= 60) "${s / 60}min" else "${s}s"
            return if (s > 0) "Você está silenciado — faltam $falta" else "Você está silenciado neste servidor"
        }
        return r.error?.takeIf { it.isNotBlank() && it != "DISCONNECTED" && it != "NO_ACK" }
            ?: "Mensagem não enviada"
    }

    fun retry(msg: ChatMessage) {
        if (!msg.failed) return
        _state.update { st -> st.copy(messages = st.messages.filterNot { it.id == msg.id }, error = null) }
        send(msg.content)
    }

    private fun sendError(t: Throwable, fallback: String): String {
        val http = t as? HttpException ?: return "$fallback — sem conexão"
        val parsed = runCatching { http.response()?.errorBody()?.string() }.getOrNull()
            ?.let { runCatching { json.decodeFromString<ApiError>(it) }.getOrNull() }
        if (parsed?.code == "MUTED" || parsed?.code == "SPAM_MUTED") {
            val s = parsed.secondsLeft ?: 0
            val falta = if (s >= 60) "${s / 60}min" else "${s}s"
            return if (s > 0) "Você está silenciado — faltam $falta" else "Você está silenciado neste servidor"
        }
        parsed?.error?.takeIf { it.isNotBlank() }?.let { return it }
        return when (http.code()) {
            403 -> "Sem permissão para falar aqui"
            404 -> "Esta órbita não existe mais"
            else -> "$fallback (erro ${http.code()})"
        }
    }

    fun sendGif(gif: GifResultDto) {
        if (_state.value.sending) return
        val replyToId = _state.value.replyingTo?.id
        _state.update { it.copy(sending = true, error = null) }
        scope.launch {
            val att = AttachmentDto(
                url = gif.full,
                type = "image/gif",
                name = gif.title.ifBlank { "gif" } + ".gif",
                size = gif.size,
                width = gif.width,
                height = gif.height,
            )
            val result = runCatching {
                when (target) {
                    is ChatTarget.Channel -> channelApi.send(
                        target.id,
                        SendChannelRequest("", replyToId = replyToId, attachments = listOf(att)),
                    ).data?.toChat()
                    is ChatTarget.Dm -> dmApi.send(
                        target.id,
                        SendDmRequest("", replyToId = replyToId, attachments = listOf(att)),
                    ).data?.toChat()
                }
            }
            result
                .onSuccess { msg ->
                    _state.update {
                        it.copy(
                            sending = false,
                            replyingTo = null,
                            messages = if (msg != null && it.messages.none { m -> m.id == msg.id }) it.messages + msg else it.messages,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(sending = false, error = sendError(t, "GIF não enviado")) } }
        }
    }

    fun sendSticker(fig: ServerStickerDto) {
        if (_state.value.sending) return
        val replyToId = _state.value.replyingTo?.id
        _state.update { it.copy(sending = true, error = null) }
        scope.launch {
            val ext = fig.url.substringAfterLast('.', "png").substringBefore('?').lowercase()
            val att = AttachmentDto(
                url = fig.url,
                type = "image/" + (if (ext.length in 2..4) ext else "png"),
                name = fig.name,
                size = 0,
                width = fig.width.takeIf { it > 0 },
                height = fig.height.takeIf { it > 0 },
                sticker = true,
            )
            val result = runCatching {
                when (target) {
                    is ChatTarget.Channel -> channelApi.send(
                        target.id,
                        SendChannelRequest("", replyToId = replyToId, attachments = listOf(att)),
                    ).data?.toChat()
                    is ChatTarget.Dm -> dmApi.send(
                        target.id,
                        SendDmRequest("", replyToId = replyToId, attachments = listOf(att)),
                    ).data?.toChat()
                }
            }
            result
                .onSuccess { msg ->
                    _state.update {
                        it.copy(
                            sending = false,
                            replyingTo = null,
                            messages = if (msg != null && it.messages.none { m -> m.id == msg.id }) it.messages + msg else it.messages,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(sending = false, error = sendError(t, "Figurinha não enviada")) } }
        }
    }

    fun addFiles(files: List<File>) {
        var error: String? = null
        val current = _state.value.pending.toMutableList()
        for (f in files) {
            if (current.size >= MAX_FILES) {
                error = "Maximo de $MAX_FILES arquivos por mensagem"
                break
            }
            if (!f.isFile) continue
            if (f.length() > MAX_FILE_BYTES) {
                error = "${f.name} passa de 25MB"
                continue
            }
            val mime = mimeOf(f)
            if (mime == null || mime !in ALLOWED_MIMES) {
                error = "Tipo não suportado: ${f.name}"
                continue
            }
            current += PendingFile(f, mime)
        }
        _state.update { it.copy(pending = current, error = error) }
    }

    fun removePending(index: Int) {
        _state.update { it.copy(pending = it.pending.filterIndexed { i, _ -> i != index }) }
    }

    private fun mimeOf(f: File): String? {
        runCatching { Files.probeContentType(f.toPath()) }.getOrNull()?.let { return it }
        return when (f.extension.lowercase()) {
            "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"
            "webp" -> "image/webp"; "avif" -> "image/avif"
            "mp4" -> "video/mp4"; "webm" -> "video/webm"; "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "ogg" -> "audio/ogg"
            "m4a" -> "audio/x-m4a"; "aac" -> "audio/aac"
            "pdf" -> "application/pdf"; "txt" -> "text/plain"
            "zip" -> "application/zip"; "json" -> "application/json"
            else -> null
        }
    }

    fun typing() {
        val now = System.currentTimeMillis()
        if (now - lastTypingEmit > TYPING_RESEND_MS) {
            lastTypingEmit = now
            when (target) {
                is ChatTarget.Channel -> socket.startTyping(target.id)
                is ChatTarget.Dm -> socket.startDmTyping(target.id)
            }
        }
        typingIdleJob?.cancel()
        typingIdleJob = scope.launch {
            delay(TYPING_IDLE_MS)
            stopTypingEmit()
        }
    }

    private fun stopTypingEmit() {
        typingIdleJob?.cancel()
        if (lastTypingEmit == 0L) return
        lastTypingEmit = 0
        when (target) {
            is ChatTarget.Channel -> socket.stopTyping(target.id)
            is ChatTarget.Dm -> socket.stopDmTyping(target.id)
        }
    }

    private fun userTyping(userId: String, username: String?) {
        if (userId == myId) return
        _state.update { it.copy(typing = it.typing + (userId to (username ?: "alguem"))) }
        typingExpiry.remove(userId)?.cancel()
        typingExpiry[userId] = scope.launch {
            delay(TYPING_EXPIRY_MS)
            userStoppedTyping(userId)
        }
    }

    private fun userStoppedTyping(userId: String) {
        typingExpiry.remove(userId)?.cancel()
        _state.update { if (userId in it.typing) it.copy(typing = it.typing - userId) else it }
    }

    private fun markRead() {
        scope.launch {
            runCatching {
                when (target) {
                    is ChatTarget.Channel -> channelApi.markRead(target.id)
                    is ChatTarget.Dm -> dmApi.markRead(target.id)
                }
            }
        }
    }

    fun startReply(msg: ChatMessage) {
        _state.update { it.copy(replyingTo = msg) }
    }

    fun cancelReply() {
        _state.update { it.copy(replyingTo = null) }
    }

    fun react(messageId: String, emoji: String) {
        val channelId = (target as? ChatTarget.Channel)?.id ?: return
        scope.launch {
            runCatching { channelApi.react(channelId, messageId, ReactRequest(emoji)) }
                .onSuccess { res -> res.data?.let { setReactions(messageId, it.reactions) } }
                .onFailure { _state.update { it.copy(error = "Não foi possível reagir") } }
        }
    }

    fun edit(messageId: String, newContent: String) {
        val channelId = (target as? ChatTarget.Channel)?.id ?: return
        val content = newContent.trim()
        if (content.isEmpty()) return
        if (_state.value.messages.firstOrNull { it.id == messageId }?.content == content) return
        scope.launch {
            runCatching { channelApi.editMessage(channelId, messageId, EditChannelRequest(content)) }
                .onSuccess {
                    _state.update { st ->
                        st.copy(messages = st.messages.map {
                            if (it.id == messageId) it.copy(content = content, edited = true) else it
                        })
                    }
                }
                .onFailure { _state.update { it.copy(error = "Não foi possível editar") } }
        }
    }

    fun delete(messageId: String) {
        scope.launch {
            val result = runCatching {
                when (target) {
                    is ChatTarget.Channel -> channelApi.deleteMessage(target.id, messageId)
                    is ChatTarget.Dm -> dmApi.deleteMessage(target.id, messageId)
                }
            }
            result
                .onSuccess { fadeOutAndRemove(messageId) }
                .onFailure { _state.update { it.copy(error = "Não foi possível apagar") } }
        }
    }

    fun pin(messageId: String) {
        val channelId = (target as? ChatTarget.Channel)?.id ?: return
        scope.launch {
            runCatching { channelApi.pin(channelId, messageId) }
                .onFailure { _state.update { it.copy(error = "Não foi possível fixar") } }
        }
    }

    fun sendBotCommand(serverId: String, content: String) {
        val channelId = (target as? ChatTarget.Channel)?.id ?: return
        stopTypingEmit()
        if (!socket.sendBotCommand(channelId, serverId, content.trim())) {
            _state.update { it.copy(error = "Sem conexão — o comando não chegou na bot") }
        }
    }

    fun createPoll(question: String, options: List<String>, allowMultiple: Boolean, durationHours: Int?) {
        val channelId = (target as? ChatTarget.Channel)?.id ?: return
        val limpas = options.map { it.trim() }.filter { it.isNotBlank() }
        if (question.isBlank() || limpas.size < 2) return
        _state.update { it.copy(sending = true, error = null) }
        scope.launch {
            runCatching {
                channelApi.createPoll(
                    channelId,
                    CreatePollRequest(question.trim(), limpas, allowMultiple, durationHours),
                ).data?.toChat()
            }
                .onSuccess { msg ->
                    _state.update { it.copy(sending = false) }
                    if (msg != null) append(msg)
                }
                .onFailure { t ->
                    _state.update { it.copy(sending = false, error = sendError(t, "Enquete não criada")) }
                }
        }
    }

    fun vote(messageId: String, optionId: String) {
        val channelId = (target as? ChatTarget.Channel)?.id ?: return
        val me = myId ?: return
        val antes = _state.value.messages.firstOrNull { it.id == messageId }?.poll ?: return
        if (antes.closed || expirada(antes)) return

        setPoll(messageId, votoLocal(antes, optionId, me))
        scope.launch {
            runCatching { channelApi.votePoll(channelId, messageId, VoteRequest(optionId)).data?.poll }
                .onSuccess { p -> if (p != null) setPoll(messageId, p) else setPoll(messageId, antes) }
                .onFailure {
                    setPoll(messageId, antes)
                    _state.update { it.copy(error = "Voto não registrado") }
                }
        }
    }

    fun closePoll(messageId: String) {
        val channelId = (target as? ChatTarget.Channel)?.id ?: return
        scope.launch {
            runCatching { channelApi.closePoll(channelId, messageId).data?.poll }
                .onSuccess { p -> if (p != null) setPoll(messageId, p) }
                .onFailure { _state.update { it.copy(error = "Não foi possível encerrar a enquete") } }
        }
    }

    private fun votoLocal(poll: PollDto, optionId: String, me: String): PollDto {
        val jaVotou = poll.options.any { it.id == optionId && me in it.votes }
        return poll.copy(options = poll.options.map { o ->
            val votos = when {
                o.id == optionId && jaVotou -> o.votes - me
                o.id == optionId            -> o.votes + me
                poll.allowMultiple          -> o.votes
                else                        -> o.votes - me
            }
            o.copy(votes = votos)
        })
    }

    private fun setPoll(messageId: String, poll: PollDto) {
        _state.update { st ->
            st.copy(messages = st.messages.map { if (it.id == messageId) it.copy(poll = poll) else it })
        }
    }

    private fun listenLive() {
        liveJob = scope.launch {
            launch {
                socket.reconnected.collect { load() }
            }
            when (target) {
                is ChatTarget.Channel -> {
                    launch {
                        socket.newChannelMessage.collect { raw ->
                            val msg = decode<ChannelMessageDto>(raw) ?: return@collect
                            if (msg.channelId == target.id) append(msg.toChat())
                        }
                    }
                    launch {
                        socket.messageEdited.collect { raw ->
                            val ev = decode<MessageEditedEventDto>(raw) ?: return@collect
                            if (ev.channelId != target.id) return@collect
                            _state.update { st ->
                                st.copy(messages = st.messages.map {
                                    if (it.id == ev.messageId) it.copy(content = ev.content, edited = true) else it
                                })
                            }
                        }
                    }
                    launch {
                        socket.messageDeleted.collect { raw ->
                            val ev = decode<MessageDeletedEventDto>(raw) ?: return@collect
                            if (ev.channelId == target.id) fadeOutAndRemove(ev.messageId)
                        }
                    }
                    launch {
                        socket.reactionUpdate.collect { raw ->
                            val ev = decode<ReactionUpdateDto>(raw) ?: return@collect
                            if (ev.channelId == target.id) setReactions(ev.messageId, ev.reactions)
                        }
                    }
                    launch {
                        socket.pollUpdated.collect { raw ->
                            val ev = decode<PollUpdateDto>(raw) ?: return@collect
                            if (ev.channelId == target.id) setPoll(ev.messageId, ev.poll)
                        }
                    }
                    launch {
                        socket.channelTyping.collect { raw ->
                            val ev = decode<ChannelTypingEventDto>(raw) ?: return@collect
                            if (ev.channelId == target.id) userTyping(ev.userId, ev.username)
                        }
                    }
                    launch {
                        socket.channelTypingStopped.collect { raw ->
                            val ev = decode<ChannelTypingEventDto>(raw) ?: return@collect
                            if (ev.channelId == target.id) userStoppedTyping(ev.userId)
                        }
                    }
                }
                is ChatTarget.Dm -> {
                    launch {
                        socket.newDm.collect { raw ->
                            val msg = decode<DmMessageDto>(raw) ?: return@collect
                            if (msg.conversationId == target.id) append(msg.toChat())
                        }
                    }
                    launch {
                        socket.dmDeleted.collect { raw ->
                            val ev = decode<DmDeletedEventDto>(raw) ?: return@collect
                            if (ev.conversationId == target.id) fadeOutAndRemove(ev.messageId)
                        }
                    }
                    launch {
                        socket.dmTyping.collect { raw ->
                            val ev = decode<DmTypingEventDto>(raw) ?: return@collect
                            if (ev.conversationId == target.id) userTyping(ev.userId, ev.username)
                        }
                    }
                    launch {
                        socket.dmTypingStopped.collect { raw ->
                            val ev = decode<DmTypingEventDto>(raw) ?: return@collect
                            if (ev.conversationId == target.id) userStoppedTyping(ev.userId)
                        }
                    }
                }
            }
        }
    }

    private fun append(msg: ChatMessage) {
        userStoppedTyping(msg.authorId)
        _state.update { st ->
            val base = if (msg.clientNonce != null)
                st.messages.filterNot { it.pending && it.clientNonce == msg.clientNonce }
            else st.messages
            if (base.any { m -> m.id == msg.id }) st.copy(messages = base)
            else st.copy(messages = base + msg)
        }
        if (!msg.mine) markRead()
    }

    private fun setReactions(messageId: String, reactions: List<ReactionDto>) {
        _state.update { st ->
            st.copy(messages = st.messages.map {
                if (it.id == messageId) it.copy(reactions = reactions) else it
            })
        }
    }

    private fun fadeOutAndRemove(messageId: String) {
        val current = _state.value.messages.firstOrNull { it.id == messageId }
        if (current == null || current.deleting) return
        _state.update { st ->
            st.copy(
                messages = st.messages.map { if (it.id == messageId) it.copy(deleting = true) else it },
                replyingTo = if (st.replyingTo?.id == messageId) null else st.replyingTo,
            )
        }
        scope.launch {
            delay(FADE_OUT_MS)
            _state.update { st -> st.copy(messages = st.messages.filterNot { it.id == messageId }) }
        }
    }

    private inline fun <reified T> decode(raw: String): T? =
        runCatching { json.decodeFromString<T>(raw) }.getOrNull()

    private fun MsgAuthorDto?.name(fallbackId: String): String =
        this?.displayName ?: this?.username ?: if (fallbackId == myId) "você" else "alguem"

    private fun ChannelMessageDto.toChat(): ChatMessage {
        val autor = authorId.ifBlank { author?.id.orEmpty() }
        return ChatMessage(
            id = id, content = content, authorId = autor,
            authorName = author.name(autor), authorAvatar = author?.avatarUrl,
            authorFont = author?.displayFont,
            createdAt = createdAt,
            mine = autor.isNotBlank() && autor == myId,
            edited = edited,
            reactions = reactions, mentions = mentions, replyTo = replyTo,
            attachments = attachments,
            poll = poll,
            clientNonce = clientNonce,
        )
    }

    private fun DmMessageDto.toChat() = ChatMessage(
        id = id, content = content, authorId = senderId,
        authorName = author.name(senderId), authorAvatar = author?.avatarUrl,
        authorFont = author?.displayFont,
        createdAt = createdAt,
        mine = senderId == myId,
        replyTo = replyTo,
        attachments = attachments,
        clientNonce = clientNonce,
        call = call,
    )
}
