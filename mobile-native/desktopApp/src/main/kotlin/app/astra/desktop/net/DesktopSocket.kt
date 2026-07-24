package app.astra.desktop.net

import app.astra.desktop.auth.SessionStore
import app.astra.shared.AstraShared
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.Polling
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// Resposta do ack do fast_send_text (backend socket.ts). ok=false traz o motivo:
// code MUTED/SPAM_MUTED com secondsLeft, ou um error generico.
data class FastSendResult(
    val ok: Boolean,
    val error: String? = null,
    val code: String? = null,
    val secondsLeft: Int? = null,
)

// Socket.io do desktop — versao enxuta do SocketManager do Android (mesma lib
// Java, mesmo protocolo do backend socket.ts). Chat: new_message/new_dm + salas
// join_channel/join_dm. Acoes: message_edited/message_deleted/reaction_update/
// dm_deleted. Typing: user_typing/dm_user_typing (so chega pra quem esta na
// sala). Unread: channel_activity (global via sala pessoal). Presenca depois.
class DesktopSocket(private val store: SessionStore) {
    private var socket: Socket? = null
    private val channels = ConcurrentHashMap.newKeySet<String>()
    private val dms = ConcurrentHashMap.newKeySet<String>()

    private val _newChannelMessage = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val newChannelMessage: SharedFlow<String> = _newChannelMessage.asSharedFlow()

    private val _newDm = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val newDm: SharedFlow<String> = _newDm.asSharedFlow()

    private val _messageEdited = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messageEdited: SharedFlow<String> = _messageEdited.asSharedFlow()

    private val _messageDeleted = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messageDeleted: SharedFlow<String> = _messageDeleted.asSharedFlow()

    private val _reactionUpdate = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val reactionUpdate: SharedFlow<String> = _reactionUpdate.asSharedFlow()

    private val _dmDeleted = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val dmDeleted: SharedFlow<String> = _dmDeleted.asSharedFlow()

    private val _channelTyping = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val channelTyping: SharedFlow<String> = _channelTyping.asSharedFlow()

    private val _channelTypingStopped = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val channelTypingStopped: SharedFlow<String> = _channelTypingStopped.asSharedFlow()

    private val _dmTyping = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val dmTyping: SharedFlow<String> = _dmTyping.asSharedFlow()

    private val _dmTypingStopped = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val dmTypingStopped: SharedFlow<String> = _dmTypingStopped.asSharedFlow()

    // Novidade em canal de qualquer constelacao (vai pra sala pessoal user:{id}).
    private val _channelActivity = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val channelActivity: SharedFlow<String> = _channelActivity.asSharedFlow()

    fun connect() {
        if (socket?.connected() == true) return
        val token = store.load()?.accessToken ?: return

        val opts = IO.Options().apply {
            transports = arrayOf(WebSocket.NAME, Polling.NAME)
            auth = mapOf("token" to token)
            reconnection = true
        }
        val s = runCatching { IO.socket(AstraShared.BASE_URL, opts) }.getOrNull() ?: return
        socket = s

        s.on(Socket.EVENT_CONNECT) {
            // Re-entra nas salas apos reconectar.
            channels.forEach { s.emit("join_channel", it) }
            dms.forEach { s.emit("join_dm", it) }
        }
        s.on("new_message") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _newChannelMessage.tryEmit(it.toString()) }
        }
        s.on("new_dm") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _newDm.tryEmit(it.toString()) }
        }
        s.on("message_edited") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _messageEdited.tryEmit(it.toString()) }
        }
        s.on("message_deleted") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _messageDeleted.tryEmit(it.toString()) }
        }
        s.on("reaction_update") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _reactionUpdate.tryEmit(it.toString()) }
        }
        s.on("dm_deleted") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _dmDeleted.tryEmit(it.toString()) }
        }
        s.on("user_typing") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _channelTyping.tryEmit(it.toString()) }
        }
        s.on("user_stopped_typing") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _channelTypingStopped.tryEmit(it.toString()) }
        }
        s.on("dm_user_typing") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _dmTyping.tryEmit(it.toString()) }
        }
        s.on("dm_user_stopped_typing") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _dmTypingStopped.tryEmit(it.toString()) }
        }
        s.on("channel_activity") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _channelActivity.tryEmit(it.toString()) }
        }
        s.io().on(io.socket.client.Manager.EVENT_RECONNECT_ATTEMPT) {
            // Token pode ter rotacionado (authenticator http) — usa o mais fresco.
            store.load()?.accessToken?.let { fresh -> opts.auth = mapOf("token" to fresh) }
        }

        s.connect()
    }

    fun isConnected(): Boolean = socket?.connected() == true

    // Envio rapido de texto puro por socket (com ack) em vez de POST HTTP. O
    // backend insere, faz broadcast do new_message (com o clientNonce) e responde
    // o ack — a UI mostra a mensagem na hora e reconcilia quando o broadcast volta.
    // So texto puro em canal: reply e anexo continuam no HTTP (o handler nao os le).
    fun fastSendText(
        channelId: String,
        content: String,
        clientNonce: String,
        onResult: (FastSendResult) -> Unit,
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            onResult(FastSendResult(ok = false, error = "DISCONNECTED"))
            return
        }
        val payload = JSONObject().apply {
            put("channelId", channelId)
            put("content", content)
            put("clientNonce", clientNonce)
        }
        val fired = AtomicBoolean(false)
        s.emit("fast_send_text", arrayOf<Any>(payload), Ack { args ->
            if (!fired.compareAndSet(false, true)) return@Ack
            val obj = args.firstOrNull() as? JSONObject
            onResult(
                if (obj == null) FastSendResult(ok = false, error = "NO_ACK")
                else FastSendResult(
                    ok = obj.optBoolean("ok", false),
                    error = obj.optString("error").ifBlank { null },
                    code = obj.optString("code").ifBlank { null },
                    secondsLeft = if (obj.has("secondsLeft")) obj.optInt("secondsLeft") else null,
                ),
            )
        })
    }

    fun joinChannel(id: String) {
        channels.add(id)
        socket?.emit("join_channel", id)
    }

    fun leaveChannel(id: String) {
        channels.remove(id)
        socket?.emit("leave_channel", id)
    }

    fun joinDm(id: String) {
        dms.add(id)
        socket?.emit("join_dm", id)
    }

    fun leaveDm(id: String) {
        dms.remove(id)
        socket?.emit("leave_dm", id)
    }

    // Backend ignora typing de quem nao esta na sala (socket.rooms.has).
    fun startTyping(channelId: String) { socket?.emit("typing_start", channelId) }
    fun stopTyping(channelId: String) { socket?.emit("typing_stop", channelId) }
    fun startDmTyping(conversationId: String) { socket?.emit("dm_typing_start", conversationId) }
    fun stopDmTyping(conversationId: String) { socket?.emit("dm_typing_stop", conversationId) }

    fun disconnect() {
        channels.clear()
        dms.clear()
        socket?.apply { off(); disconnect() }
        socket = null
    }
}
