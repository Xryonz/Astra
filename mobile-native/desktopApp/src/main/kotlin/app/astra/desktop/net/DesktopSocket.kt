package app.astra.desktop.net

import app.astra.desktop.auth.SessionStore
import app.astra.desktop.voice.SoundboardPlayer
import app.astra.mobile.core.network.UserApi
import app.astra.shared.AstraShared
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.Polling
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class FastSendResult(
    val ok: Boolean,
    val error: String? = null,
    val code: String? = null,
    val secondsLeft: Int? = null,
)

class DesktopSocket(
    private val store: SessionStore,
    private val userApi: UserApi,
) {
    private var socket: Socket? = null
    private var heartbeatTimer: java.util.Timer? = null
    private val channels = ConcurrentHashMap.newKeySet<String>()
    private val dms = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    private val _pollUpdated = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val pollUpdated: SharedFlow<String> = _pollUpdated.asSharedFlow()

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

    private val _channelActivity = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val channelActivity: SharedFlow<String> = _channelActivity.asSharedFlow()

    private val _presenceUpdate = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val presenceUpdate: SharedFlow<String> = _presenceUpdate.asSharedFlow()

    private val _activityUpdate = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val activityUpdate: SharedFlow<String> = _activityUpdate.asSharedFlow()

    private val _friendsChanged = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val friendsChanged: SharedFlow<String> = _friendsChanged.asSharedFlow()

    private val _notification = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val notification: SharedFlow<String> = _notification.asSharedFlow()

    private val _voicePresence = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val voicePresence: SharedFlow<String> = _voicePresence.asSharedFlow()

    private val _chamadaChegando = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val chamadaChegando: SharedFlow<String> = _chamadaChegando.asSharedFlow()
    private val _chamadaAtendida = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val chamadaAtendida: SharedFlow<String> = _chamadaAtendida.asSharedFlow()
    private val _chamadaEncerrada = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val chamadaEncerrada: SharedFlow<String> = _chamadaEncerrada.asSharedFlow()

    private val _serverChannels = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val serverChannels: SharedFlow<String> = _serverChannels.asSharedFlow()

    private val _membroEntrou = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val membroEntrou: SharedFlow<String> = _membroEntrou.asSharedFlow()

    private val _membroSaiu = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val membroSaiu: SharedFlow<String> = _membroSaiu.asSharedFlow()

    private val _membroMudouDeCargo = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val membroMudouDeCargo: SharedFlow<String> = _membroMudouDeCargo.asSharedFlow()

    private val _serverJoined = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val serverJoined: SharedFlow<String> = _serverJoined.asSharedFlow()

    private val _profileUpdated = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val profileUpdated: SharedFlow<String> = _profileUpdated.asSharedFlow()

    private val _xpGain = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val xpGain: SharedFlow<String> = _xpGain.asSharedFlow()

    private val _missaoConcluida = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val missaoConcluida: SharedFlow<String> = _missaoConcluida.asSharedFlow()

    private val _serverUpdated = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val serverUpdated: SharedFlow<String> = _serverUpdated.asSharedFlow()

    private val _serverAccessLost = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val serverAccessLost: SharedFlow<String> = _serverAccessLost.asSharedFlow()

    private val _serverRoles = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val serverRoles: SharedFlow<String> = _serverRoles.asSharedFlow()

    private val _reconnected = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val reconnected: SharedFlow<Long> = _reconnected.asSharedFlow()

    private val servers = ConcurrentHashMap.newKeySet<String>()

    fun joinServer(id: String) {
        servers.add(id)
        socket?.emit("join_server", id)
    }

    private val recent = ArrayDeque<Pair<Long, String>>()
    private fun note(event: String) = synchronized(recent) {
        recent.addLast(System.currentTimeMillis() to event)
        while (recent.size > 40) recent.removeFirst()
    }

    @Volatile private var lastError: String? = null
    fun lastError(): String? = lastError

    fun noteLocal(texto: String) = note(texto)

    fun recentEvents(): List<Pair<Long, String>> = synchronized(recent) { recent.toList() }
    fun joinedRooms(): Triple<Set<String>, Set<String>, Set<String>> =
        Triple(channels.toSet(), dms.toSet(), servers.toSet())

    fun voiceJoin(channelId: String) { socket?.emit("voice_join", channelId) }
    fun voiceLeave(channelId: String) { socket?.emit("voice_leave", channelId) }

    fun voiceKeepalive(channelId: String) { socket?.emit("voice_keepalive", channelId) }

    fun enviarAtividade(texto: String) { socket?.emit("set_activity", texto) }

    @Volatile private var querConectar = false
    @Volatile private var proximaTentativa = 0L
    @Volatile private var falhasSeguidas = 0
    @Volatile private var jaConectou = false
    private val conectando = AtomicBoolean(false)

    fun connect() {
        querConectar = true
        proximaTentativa = 0
        tentar(agora = true)
        ligarRelogio()
    }

    private fun tentar(agora: Boolean) {
        if (!querConectar) return
        if (socket?.connected() == true) return
        if (!agora && System.currentTimeMillis() < proximaTentativa) return
        if (!conectando.compareAndSet(false, true)) return
        scope.launch {
            try { abrir() } catch (e: Exception) { note("· erro ao abrir: ${e.message?.take(50)}"); recuar() }
            finally { conectando.set(false) }
        }
    }

    private fun recuar() {
        val espera = minOf(30_000L, 1_000L shl minOf(falhasSeguidas++, 5))
        proximaTentativa = System.currentTimeMillis() + espera
    }

    private suspend fun tokenValido(): String? {
        val atual = store.load()?.accessToken ?: return null
        if (!vencendo(atual)) return atual
        note("· token vencido, renovando")
        runCatching { userApi.me() }
        return store.load()?.accessToken?.takeIf { !vencendo(it) }
    }

    private fun vencendo(jwt: String): Boolean {
        val exp = runCatching {
            val corpo = jwt.split('.').getOrNull(1) ?: return true
            val texto = String(Base64.getUrlDecoder().decode(corpo))
            Regex("\"exp\"\\s*:\\s*(\\d+)").find(texto)?.groupValues?.get(1)?.toLong()
        }.getOrNull() ?: return true
        return System.currentTimeMillis() + 60_000 >= exp * 1000
    }

    private suspend fun abrir() {
        val token = tokenValido()
        if (token == null) {
            lastError = "sessão expirada — entre de novo"
            note("· sem token válido")
            recuar()
            return
        }

        socket?.apply { runCatching { off() }; runCatching { disconnect() } }

        val opts = IO.Options().apply {
            transports = arrayOf(WebSocket.NAME, Polling.NAME)
            auth = mapOf("token" to token)
            reconnection = false
            forceNew = true
        }
        val s = runCatching { IO.socket(AstraShared.BASE_URL, opts) }.getOrNull() ?: run {
            recuar()
            return
        }
        socket = s

        s.onAnyIncoming { args -> note(args.firstOrNull()?.toString() ?: "?") }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val cru = args.firstOrNull()?.let { e -> (e as? Exception)?.message ?: e.toString() } ?: "desconhecido"
            lastError = emPortugues(cru)
            note("· falhou ao conectar: ${cru.take(60)}")
            recuar()
        }
        s.on(Socket.EVENT_DISCONNECT) { args ->
            note("· desconectou (${args.firstOrNull() ?: "?"})")
            lastError = "queda de conexão — reconectando"
        }
        s.on(Socket.EVENT_CONNECT) {
            lastError = null
            falhasSeguidas = 0
            proximaTentativa = 0
            note("· conectado")
            channels.forEach { s.emit("join_channel", it) }
            dms.forEach { s.emit("join_dm", it) }
            servers.forEach { s.emit("join_server", it) }
            s.emit("heartbeat")
            if (jaConectou) _reconnected.tryEmit(System.currentTimeMillis())
            jaConectou = true
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
        s.on("poll_updated") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _pollUpdated.tryEmit(it.toString()) }
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
        s.on("presence_update") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _presenceUpdate.tryEmit(it.toString()) }
        }
        s.on("activity_update") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _activityUpdate.tryEmit(it.toString()) }
        }
        s.on("friends_changed") { args ->
            _friendsChanged.tryEmit((args.firstOrNull() as? JSONObject)?.toString().orEmpty())
        }
        s.on("notification") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _notification.tryEmit(it.toString()) }
        }
        s.on("voice_presence") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _voicePresence.tryEmit(it.toString()) }
        }
        s.on("dm_call_invite") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _chamadaChegando.tryEmit(it.toString()) }
        }
        s.on("dm_call_accept") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _chamadaAtendida.tryEmit(it.toString()) }
        }
        s.on("dm_call_ended") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _chamadaEncerrada.tryEmit(it.toString()) }
        }
        s.on("soundboard_play") { args ->
            val url = (args.firstOrNull() as? JSONObject)?.optString("url").orEmpty()
            if (url.isNotBlank()) SoundboardPlayer.tocar(url)
        }
        s.on("server_channels") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _serverChannels.tryEmit(it.toString()) }
        }
        s.on("server_member_added") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _membroEntrou.tryEmit(it.toString()) }
        }
        s.on("server_member_removed") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _membroSaiu.tryEmit(it.toString()) }
        }
        s.on("server_member_role") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _membroMudouDeCargo.tryEmit(it.toString()) }
        }
        s.on("server_joined") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _serverJoined.tryEmit(it.toString()) }
        }
        s.on("profile_updated") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _profileUpdated.tryEmit(it.toString()) }
        }
        s.on("xp_gain") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _xpGain.tryEmit(it.toString()) }
        }
        s.on("mission_done") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _missaoConcluida.tryEmit(it.toString()) }
        }
        s.on("server_updated") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _serverUpdated.tryEmit(it.toString()) }
        }
        s.on("server_gone") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _serverAccessLost.tryEmit(it.toString()) }
        }
        s.on("server_left") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _serverAccessLost.tryEmit(it.toString()) }
        }
        s.on("server_roles") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _serverRoles.tryEmit(it.toString()) }
        }
        s.connect()
    }

    private fun ligarRelogio() {
        if (heartbeatTimer != null) return
        var tique = 0
        heartbeatTimer = java.util.Timer("astra-socket", true).apply {
            scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    tique++
                    val s = socket
                    if (s?.connected() == true) {
                        if (tique % 5 == 0) runCatching { s.emit("heartbeat") }
                    } else {
                        tentar(agora = false)
                    }
                }
            }, 5_000L, 5_000L)
        }
    }

    private fun emPortugues(cru: String): String = when {
        cru.contains("TOKEN_REVOKED") -> "sessão encerrada em outro lugar — entre de novo"
        cru.contains("INVALID_TOKEN") || cru.contains("AUTH_REQUIRED") -> "sessão vencida — renovando"
        cru.contains("timeout", true) -> "servidor não respondeu (pode estar acordando)"
        cru.contains("xhr", true) || cru.contains("websocket", true) -> "sem alcançar o servidor"
        else -> cru.take(60)
    }

    fun isConnected(): Boolean = socket?.connected() == true

    fun fastSendText(
        channelId: String,
        content: String,
        clientNonce: String,
        onResult: (FastSendResult) -> Unit,
    ) = fastSend("fast_send_text", "channelId", channelId, content, clientNonce, onResult)

    fun fastSendDm(
        conversationId: String,
        content: String,
        clientNonce: String,
        onResult: (FastSendResult) -> Unit,
    ) = fastSend("fast_send_dm", "conversationId", conversationId, content, clientNonce, onResult)

    private fun fastSend(
        event: String,
        idKey: String,
        id: String,
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
            put(idKey, id)
            put("content", content)
            put("clientNonce", clientNonce)
        }
        val fired = AtomicBoolean(false)
        s.emit(event, arrayOf<Any>(payload), Ack { args ->
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

    fun sendBotCommand(channelId: String, serverId: String, content: String): Boolean {
        val s = socket?.takeIf { it.connected() } ?: return false
        s.emit("bot_command", JSONObject(mapOf(
            "channelId" to channelId,
            "serverId" to serverId,
            "content" to content,
        )))
        return true
    }

    fun ligarNoSussurro(conversationId: String, video: Boolean) {
        socket?.emit("dm_call_invite", JSONObject(mapOf(
            "conversationId" to conversationId,
            "video" to video,
        )))
    }

    fun atenderSussurro(conversationId: String) {
        socket?.emit("dm_call_accept", JSONObject(mapOf("conversationId" to conversationId)))
    }

    fun desligarSussurro(conversationId: String) {
        socket?.emit("dm_call_end", JSONObject(mapOf("conversationId" to conversationId)))
    }

    fun joinDm(id: String) {
        dms.add(id)
        socket?.emit("join_dm", id)
    }

    fun leaveDm(id: String) {
        dms.remove(id)
        socket?.emit("leave_dm", id)
    }

    fun startTyping(channelId: String) { socket?.emit("typing_start", channelId) }
    fun stopTyping(channelId: String) { socket?.emit("typing_stop", channelId) }
    fun startDmTyping(conversationId: String) { socket?.emit("dm_typing_start", conversationId) }
    fun stopDmTyping(conversationId: String) { socket?.emit("dm_typing_stop", conversationId) }

    fun registrarDespedida() {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { socket?.disconnect() }
                runCatching { Thread.sleep(250) }
            },
        )
    }

    fun disconnect() {
        querConectar = false
        heartbeatTimer?.cancel()
        heartbeatTimer = null
        channels.clear()
        dms.clear()
        servers.clear()
        socket?.apply { off(); disconnect() }
        socket = null
        falhasSeguidas = 0
        jaConectou = false
        lastError = null
    }
}
