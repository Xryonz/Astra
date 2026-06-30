package app.astra.mobile.core.realtime

import android.util.Log
import app.astra.mobile.BuildConfig
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.TokenRefresher
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.transports.Polling
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val tokenRefresher: TokenRefresher,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var options: IO.Options? = null
    private var heartbeat: Job? = null

    private val activeRooms = ConcurrentHashMap.newKeySet<String>()
    private val activeChannels = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var refreshing = false
    @Volatile private var lastRefreshAtMs = 0L

    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _newDm = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val newDm: SharedFlow<String> = _newDm.asSharedFlow()

    private val _dmDeleted = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val dmDeleted: SharedFlow<Pair<String, String>> = _dmDeleted.asSharedFlow()

    private val _newChannelMessage = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val newChannelMessage: SharedFlow<String> = _newChannelMessage.asSharedFlow()

    private val _channelMessageDeleted = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val channelMessageDeleted: SharedFlow<Pair<String, String>> = _channelMessageDeleted.asSharedFlow()

    private val _channelMessageEdited = MutableSharedFlow<Triple<String, String, String>>(extraBufferCapacity = 16)
    val channelMessageEdited: SharedFlow<Triple<String, String, String>> = _channelMessageEdited.asSharedFlow()

    private val _channelReactionUpdate = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val channelReactionUpdate: SharedFlow<String> = _channelReactionUpdate.asSharedFlow()

    private val _channelMessagePinned = MutableSharedFlow<Triple<String, String, Boolean>>(extraBufferCapacity = 16)
    val channelMessagePinned: SharedFlow<Triple<String, String, Boolean>> = _channelMessagePinned.asSharedFlow()

    private val _channelPollUpdate = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val channelPollUpdate: SharedFlow<String> = _channelPollUpdate.asSharedFlow()

    private val _channelTyping = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 64)
    val channelTyping: SharedFlow<TypingEvent> = _channelTyping.asSharedFlow()

    private val _dmTyping = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 64)
    val dmTyping: SharedFlow<TypingEvent> = _dmTyping.asSharedFlow()

    private val _channelActivity = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val channelActivity: SharedFlow<String> = _channelActivity.asSharedFlow()

    fun connect() {
        if (socket?.connected() == true) return
        val token = runBlocking { tokenStore.currentAccess() } ?: return

        val opts = IO.Options().apply {
            transports = arrayOf(WebSocket.NAME, Polling.NAME)
            auth = mapOf("token" to token)
            reconnection = true
        }
        options = opts

        val s = try {
            IO.socket(BuildConfig.BASE_URL, opts)
        } catch (e: Exception) {
            Log.e(TAG, "URI invalida: ${e.message}")
            _state.value = ConnectionState.Disconnected
            return
        }
        socket = s

        s.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "conectado id=${s.id()}")
            _state.value = ConnectionState.Connected
            startHeartbeat()

            activeRooms.forEach { s.emit("join_dm", it) }
            activeChannels.forEach { s.emit("join_channel", it) }
        }
        s.on(Socket.EVENT_DISCONNECT) { args ->
            Log.d(TAG, "desconectado: ${args.firstOrNull()}")
            _state.value = ConnectionState.Disconnected
            stopHeartbeat()
        }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val msg = args.firstOrNull()?.toString().orEmpty()
            Log.w(TAG, "connect_error: $msg")
            _state.value = ConnectionState.Disconnected

            if (msg.contains("TOKEN", true) || msg.contains("AUTH", true)) {
                refreshTokenForReconnect()
            }
        }

        s.on("presence_update") { args ->
            Log.d(TAG, "presence_update ${args.firstOrNull()}")
        }
        s.on("new_dm") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _newDm.tryEmit(it.toString()) }
        }
        s.on("dm_deleted") { args ->
            (args.firstOrNull() as? JSONObject)?.let {
                _dmDeleted.tryEmit(it.optString("messageId") to it.optString("conversationId"))
            }
        }
        s.on("new_message") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _newChannelMessage.tryEmit(it.toString()) }
        }
        s.on("message_deleted") { args ->
            (args.firstOrNull() as? JSONObject)?.let {
                _channelMessageDeleted.tryEmit(it.optString("messageId") to it.optString("channelId"))
            }
        }
        s.on("message_edited") { args ->
            (args.firstOrNull() as? JSONObject)?.let {
                _channelMessageEdited.tryEmit(
                    Triple(it.optString("messageId"), it.optString("content"), it.optString("channelId")),
                )
            }
        }
        s.on("reaction_update") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _channelReactionUpdate.tryEmit(it.toString()) }
        }
        s.on("poll_updated") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _channelPollUpdate.tryEmit(it.toString()) }
        }
        s.on("message_pinned") { args ->
            (args.firstOrNull() as? JSONObject)?.let {
                _channelMessagePinned.tryEmit(
                    Triple(it.optString("messageId"), it.optString("channelId"), it.optBoolean("pinned")),
                )
            }
        }
        s.on("user_typing") { args ->
            (args.firstOrNull() as? JSONObject)?.let {
                _channelTyping.tryEmit(TypingEvent(it.optString("userId"), it.optString("username"), it.optString("channelId"), true))
            }
        }
        s.on("user_stopped_typing") { args ->
            (args.firstOrNull() as? JSONObject)?.let {
                _channelTyping.tryEmit(TypingEvent(it.optString("userId"), "", it.optString("channelId"), false))
            }
        }
        s.on("dm_user_typing") { args ->
            (args.firstOrNull() as? JSONObject)?.let {
                _dmTyping.tryEmit(TypingEvent(it.optString("userId"), it.optString("username"), it.optString("conversationId"), true))
            }
        }
        s.on("dm_user_stopped_typing") { args ->
            (args.firstOrNull() as? JSONObject)?.let {
                _dmTyping.tryEmit(TypingEvent(it.optString("userId"), "", it.optString("conversationId"), false))
            }
        }
        s.on("channel_activity") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _channelActivity.tryEmit(it.optString("channelId")) }
        }

        s.io().on(Manager.EVENT_RECONNECT_ATTEMPT) {
            val fresh = runBlocking { tokenStore.currentAccess() }
            if (fresh != null) options?.auth = mapOf("token" to fresh)
        }

        _state.value = ConnectionState.Connecting
        s.connect()
    }

    private fun refreshTokenForReconnect() {
        val now = System.currentTimeMillis()
        if (refreshing || now - lastRefreshAtMs < REFRESH_COOLDOWN_MS) return
        refreshing = true
        scope.launch {
            val newAccess = tryRefresh()
            lastRefreshAtMs = System.currentTimeMillis()
            refreshing = false
            if (newAccess != null) options?.auth = mapOf("token" to newAccess)
        }
    }

    private suspend fun tryRefresh(): String? =
        tokenRefresher.refresh(tokenStore.currentAccess())

    fun joinDm(conversationId: String) {
        activeRooms.add(conversationId)
        socket?.emit("join_dm", conversationId)
    }

    fun leaveDm(conversationId: String) {
        activeRooms.remove(conversationId)
        socket?.emit("leave_dm", conversationId)
    }

    fun startTyping(channelId: String) { socket?.emit("typing_start", channelId) }
    fun stopTyping(channelId: String) { socket?.emit("typing_stop", channelId) }
    fun startDmTyping(conversationId: String) { socket?.emit("dm_typing_start", conversationId) }
    fun stopDmTyping(conversationId: String) { socket?.emit("dm_typing_stop", conversationId) }

    fun joinChannel(channelId: String) {
        activeChannels.add(channelId)
        socket?.emit("join_channel", channelId)
    }

    fun leaveChannel(channelId: String) {
        activeChannels.remove(channelId)
        socket?.emit("leave_channel", channelId)
    }

    fun disconnect() {
        stopHeartbeat()
        activeRooms.clear()
        activeChannels.clear()
        socket?.apply { off(); disconnect() }
        socket = null
        options = null
        _state.value = ConnectionState.Disconnected
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeat = scope.launch {
            while (isActive) {
                socket?.emit("heartbeat")
                delay(30_000)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeat?.cancel()
        heartbeat = null
    }

    private companion object {
        const val TAG = "SocketManager"
        const val REFRESH_COOLDOWN_MS = 10_000L
    }
}

data class TypingEvent(
    val userId: String,
    val username: String,
    val room: String,
    val typing: Boolean,
)
