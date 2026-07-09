package app.astra.desktop.net

import app.astra.desktop.auth.SessionStore
import app.astra.shared.AstraShared
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.Polling
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

// Socket.io do desktop — versao enxuta do SocketManager do Android (mesma lib
// Java, mesmo protocolo do backend socket.ts). Chat: new_message/new_dm + salas
// join_channel/join_dm. Acoes: message_edited/message_deleted/reaction_update/
// dm_deleted. Typing/presenca/etc entram depois.
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
        s.io().on(io.socket.client.Manager.EVENT_RECONNECT_ATTEMPT) {
            // Token pode ter rotacionado (authenticator http) — usa o mais fresco.
            store.load()?.accessToken?.let { fresh -> opts.auth = mapOf("token" to fresh) }
        }

        s.connect()
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

    fun disconnect() {
        channels.clear()
        dms.clear()
        socket?.apply { off(); disconnect() }
        socket = null
    }
}
