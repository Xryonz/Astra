package app.astra.mobile.core.realtime

import android.util.Log
import app.astra.mobile.BuildConfig
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.RefreshApi
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

/**
 * Conexao Socket.io unica do app (espelha apps/web/src/lib/socket.ts).
 * - auth.token = access token JWT (mesmo do REST)
 * - heartbeat 30s pra manter presenca viva no Redis
 * - a lib reconecta sozinha (reconnection=true); cada (re)handshake le o token
 *   atual do TokenStore. Se o handshake recusa por token expirado, refrescamos
 *   o token e deixamos a proxima tentativa automatica usa-lo (sem connect manual,
 *   pra nao competir com a auto-reconexao).
 * - re-join das conversas abertas ao (re)conectar: as rooms sao do servidor e
 *   se perdem na queda.
 */
@Singleton
class SocketManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshApi: RefreshApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var options: IO.Options? = null
    private var heartbeat: Job? = null

    // Conversas/canais abertos — re-emitidos como join a cada (re)conexao.
    private val activeRooms = ConcurrentHashMap.newKeySet<String>()
    private val activeChannels = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var refreshing = false
    @Volatile private var lastRefreshAtMs = 0L

    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // Eventos de DM (JSON cru — a data layer parseia). replay=0: quem nao esta
    // ouvindo na hora perde (so o chat aberto importa).
    private val _newDm = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val newDm: SharedFlow<String> = _newDm.asSharedFlow()

    private val _dmDeleted = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val dmDeleted: SharedFlow<Pair<String, String>> = _dmDeleted.asSharedFlow()

    // Mensagens de canal (new_message) e delete (message_deleted: messageId, channelId).
    private val _newChannelMessage = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val newChannelMessage: SharedFlow<String> = _newChannelMessage.asSharedFlow()

    private val _channelMessageDeleted = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val channelMessageDeleted: SharedFlow<Pair<String, String>> = _channelMessageDeleted.asSharedFlow()

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
            // re-entra nas conversas/canais abertos (rooms se perdem na queda)
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
            // Handshake recusou: se for token, refresca. A lib segue reconectando.
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

        // A cada tentativa automatica, manda o token atual do TokenStore (pode
        // ter rotacionado via REST/Authenticator ou pelo refresh abaixo).
        s.io().on(Manager.EVENT_RECONNECT_ATTEMPT) {
            val fresh = runBlocking { tokenStore.currentAccess() }
            if (fresh != null) options?.auth = mapOf("token" to fresh)
        }

        _state.value = ConnectionState.Connecting
        s.connect()
    }

    // Refresca o token (cooldown 10s pra nao queimar refresh tokens em loop).
    // Nao chama connect(): a auto-reconexao da lib pega o token novo no proximo
    // reconnect_attempt.
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

    private suspend fun tryRefresh(): String? {
        val refresh = tokenStore.currentRefresh() ?: return null
        return try {
            val data = refreshApi.refresh("Bearer $refresh").data ?: return null
            tokenStore.save(data.accessToken, data.refreshToken)
            data.accessToken
        } catch (e: Exception) {
            Log.w(TAG, "refresh falhou: ${e.message}")
            null
        }
    }

    fun joinDm(conversationId: String) {
        activeRooms.add(conversationId)
        socket?.emit("join_dm", conversationId)
    }

    fun leaveDm(conversationId: String) {
        activeRooms.remove(conversationId)
        socket?.emit("leave_dm", conversationId)
    }

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
