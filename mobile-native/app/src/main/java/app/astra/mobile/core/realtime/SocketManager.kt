package app.astra.mobile.core.realtime

import android.util.Log
import app.astra.mobile.BuildConfig
import app.astra.mobile.core.data.TokenStore
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conexao Socket.io unica do app (espelha apps/web/src/lib/socket.ts).
 * - auth.token = access token JWT (mesmo do REST)
 * - heartbeat a cada 30s pra manter presenca viva no Redis
 * - reconnect automatico; no reconnect re-le o token (pode ter rotacionado)
 *
 * @Singleton via constructor injection — sem modulo Hilt extra.
 */
@Singleton
class SocketManager @Inject constructor(
    private val tokenStore: TokenStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var options: IO.Options? = null
    private var heartbeat: Job? = null

    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

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
        }
        s.on(Socket.EVENT_DISCONNECT) {
            Log.d(TAG, "desconectado")
            _state.value = ConnectionState.Disconnected
            stopHeartbeat()
        }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.w(TAG, "connect_error: ${args.firstOrNull()}")
            _state.value = ConnectionState.Disconnected
        }

        // M4a: so prova que o realtime chega. Vira state real no M4b/c.
        s.on("presence_update") { args ->
            Log.d(TAG, "presence_update ${args.firstOrNull()}")
        }

        // Reconnect com token possivelmente rotacionado: atualiza o auth que o
        // proximo handshake envia (o Manager le do mesmo objeto opts).
        s.io().on(Manager.EVENT_RECONNECT_ATTEMPT) {
            val fresh = runBlocking { tokenStore.currentAccess() }
            if (fresh != null) options?.auth = mapOf("token" to fresh)
        }

        _state.value = ConnectionState.Connecting
        s.connect()
    }

    fun disconnect() {
        stopHeartbeat()
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
    }
}
