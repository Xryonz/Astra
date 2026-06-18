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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conexao Socket.io unica do app (espelha apps/web/src/lib/socket.ts).
 * - auth.token = access token JWT (mesmo do REST)
 * - heartbeat a cada 30s pra manter presenca viva no Redis
 * - reconnect automatico da lib; alem disso, se o handshake falhar por token
 *   expirado, refresca o token e reconecta (o server revalida o token em TODA
 *   conexao via io.use).
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

    @Volatile private var refreshing = false
    // Corta loop: no maximo N refresh+reconnect seguidos sem um connect OK
    // (evita queimar refresh tokens se o server estiver fora). Zera ao conectar.
    private var refreshReconnects = 0

    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // Eventos de DM (JSON cru — a data layer parseia). replay=0: quem nao esta
    // ouvindo na hora perde (so o chat aberto importa).
    private val _newDm = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val newDm: SharedFlow<String> = _newDm.asSharedFlow()

    private val _dmDeleted = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val dmDeleted: SharedFlow<Pair<String, String>> = _dmDeleted.asSharedFlow()

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
            refreshReconnects = 0
            _state.value = ConnectionState.Connected
            startHeartbeat()
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
            // So age se o erro for de auth (token). Erro de transporte/server-fora
            // a propria lib reconecta sozinha (reconnection=true).
            if (msg.contains("TOKEN", true) || msg.contains("AUTH", true)) {
                refreshAndReconnect(s)
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

        // Reconnect automatico da lib: atualiza o auth que o proximo handshake
        // envia (o Manager le do mesmo objeto opts).
        s.io().on(Manager.EVENT_RECONNECT_ATTEMPT) {
            val fresh = runBlocking { tokenStore.currentAccess() }
            if (fresh != null) options?.auth = mapOf("token" to fresh)
        }

        _state.value = ConnectionState.Connecting
        s.connect()
    }

    // Handshake rejeitou o token: refresca uma vez e reconecta com o novo.
    private fun refreshAndReconnect(s: Socket) {
        if (refreshing || refreshReconnects >= MAX_REFRESH_RECONNECTS) return
        refreshing = true
        refreshReconnects++
        scope.launch {
            val newAccess = tryRefresh()
            refreshing = false
            if (newAccess != null) {
                options?.auth = mapOf("token" to newAccess)
                _state.value = ConnectionState.Connecting
                s.connect()
            }
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
        socket?.emit("join_dm", conversationId)
    }

    fun leaveDm(conversationId: String) {
        socket?.emit("leave_dm", conversationId)
    }

    fun disconnect() {
        stopHeartbeat()
        socket?.apply { off(); disconnect() }
        socket = null
        options = null
        refreshReconnects = 0
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
        const val MAX_REFRESH_RECONNECTS = 3
    }
}
