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

// Resposta do ack do fast_send_text (backend socket.ts). ok=false traz o motivo:
// code MUTED/SPAM_MUTED com secondsLeft, ou um error generico.
data class FastSendResult(
    val ok: Boolean,
    val error: String? = null,
    val code: String? = null,
    val secondsLeft: Int? = null,
)

// Socket.io do desktop — versão enxuta do SocketManager do Android (mesma lib
// Java, mesmo protocolo do backend socket.ts). Chat: new_message/new_dm + salas
// join_channel/join_dm. Acoes: message_edited/message_deleted/reaction_update/
// dm_deleted. Typing: user_typing/dm_user_typing (so chega pra quem esta na
// sala). Unread: channel_activity (global via sala pessoal). Presenca depois.
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

    // Alguem votou (ou encerraram a enquete). Vem o objeto INTEIRO da enquete, nao
    // um delta — a contagem de voto e disputada por natureza e somar +1 no cliente
    // dessincroniza na primeira corrida. Substituir o todo sempre acerta.
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

    // Novidade em canal de qualquer constelação (vai pra sala pessoal user:{id}).
    private val _channelActivity = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val channelActivity: SharedFlow<String> = _channelActivity.asSharedFlow()

    // Presenca de alguem mudou ({userId, status}) — broadcast global. O painel de
    // membros so aplica se o userId já estiver na lista da constelação atual.
    private val _presenceUpdate = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val presenceUpdate: SharedFlow<String> = _presenceUpdate.asSharedFlow()

    // Notificacao nova (o backend ja emitia 'notification' pra sala user:<id>; o
    // desktop so não escutava e descobria pelo poll de 30s do sino).
    private val _notification = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val notification: SharedFlow<String> = _notification.asSharedFlow()

    // Alguem entrou/saiu de um canal de voz (delta imediato; o poll ainda corrige).
    private val _voicePresence = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val voicePresence: SharedFlow<String> = _voicePresence.asSharedFlow()

    // Constelacao mexeu (sala server:<id>). Sao PINGS, nao deltas: quem recebe
    // refaz a busca. Canal privado faz cada membro ver uma lista diferente, entao
    // mesclar no cliente erraria — quem decide o que cada um enxerga e o backend.
    private val _serverChannels = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val serverChannels: SharedFlow<String> = _serverChannels.asSharedFlow()

    private val _serverMembers = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val serverMembers: SharedFlow<String> = _serverMembers.asSharedFlow()

    // Fui adicionado a uma constelacao (chega na sala pessoal — ainda nao estou na
    // sala dela). A rail ganha a constelacao nova sem reabrir o app.
    private val _serverJoined = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val serverJoined: SharedFlow<String> = _serverJoined.asSharedFlow()

    // Alguem editou o perfil (nome, foto, banner, cor, recado). A mesma pessoa
    // aparece em varias telas ao mesmo tempo, entao o evento e global e cada tela
    // ignora se nao tiver essa pessoa em cena.
    private val _profileUpdated = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val profileUpdated: SharedFlow<String> = _profileUpdated.asSharedFlow()

    // Ganhei XP. Chega so pra mim (sala user:{id}) e ja traz o progresso inteiro —
    // o anel do rodape nao precisa voltar no servidor perguntar quanto ficou.
    private val _xpGain = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val xpGain: SharedFlow<String> = _xpGain.asSharedFlow()

    // Fechei uma missao. Separado do xp_gain porque sao coisas diferentes na tela: o
    // XP move o anel em silencio, a missao aparece com nome e recompensa. Quem fecha
    // as tres do dia recebe quatro eventos seguidos (as tres + o bonus) — a fila do
    // aviso e que resolve mostrar um de cada vez.
    private val _missaoConcluida = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val missaoConcluida: SharedFlow<String> = _missaoConcluida.asSharedFlow()

    // A constelacao em si mudou (nome, icone, banner) — a rail e o cabecalho
    // rebuscam.
    private val _serverUpdated = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val serverUpdated: SharedFlow<String> = _serverUpdated.asSharedFlow()

    // PERDI acesso a uma constelacao: apagaram, fui expulso ou banido. As tres
    // levam a mesma reacao (sair dela e tirar da rail), entao viram um fluxo so —
    // fluxo separado que dispara a mesma coisa e so mais lugar pra esquecer.
    private val _serverAccessLost = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val serverAccessLost: SharedFlow<String> = _serverAccessLost.asSharedFlow()

    // Cargos mexeram: cor do nome e agrupamento da lista de membros mudam.
    private val _serverRoles = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val serverRoles: SharedFlow<String> = _serverRoles.asSharedFlow()

    // Reconectou. Enquanto o socket esteve fora, TUDO que aconteceu se perdeu —
    // evento e dispare-e-esqueça. Quem escuta isto refaz o que precisa estar
    // certo (mensagens da órbita aberta, lista de canais, membros). Sem isto,
    // uma queda de 10s deixava a tela mentindo ate o próximo boot.
    private val _reconnected = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val reconnected: SharedFlow<Long> = _reconnected.asSharedFlow()

    // Constelacoes em que ja pedi entrada na sala. O backend ja inscreve nas do
    // connect; isto cobre as que entrei DEPOIS (convite/descoberta) e reinscreve
    // apos reconectar.
    private val servers = ConcurrentHashMap.newKeySet<String>()

    fun joinServer(id: String) {
        servers.add(id)
        socket?.emit("join_server", id)
    }

    // ---- Diagnostico (Configuracoes > Diagnostico) ----
    // Ultimos eventos RECEBIDOS, so nome + hora. Quando alguem diz "não apareceu
    // pra mim", isto responde na hora se o aviso chegou e o app ignorou, ou se
    // nunca chegou — que sao problemas em pontas opostas do sistema. Sem isto a
    // unica saida e adivinhar. NAO guarda conteudo: e diagnostico, não espionagem.
    private val recent = ArrayDeque<Pair<Long, String>>()
    private fun note(event: String) = synchronized(recent) {
        recent.addLast(System.currentTimeMillis() to event)
        while (recent.size > 40) recent.removeFirst()
    }

    // POR QUE caiu. "desconectado" sozinho não diz se e token vencido, servidor
    // dormindo (o Render free dorme em 15min) ou rede — causas com conserto
    // totalmente diferente.
    @Volatile private var lastError: String? = null
    fun lastError(): String? = lastError

    // Deixa outra parte do app registrar algo estranho na mesma linha do tempo dos
    // eventos de rede — e ali que "chegou torto" e "nunca chegou" ficam lado a lado.
    fun noteLocal(texto: String) = note(texto)

    fun recentEvents(): List<Pair<Long, String>> = synchronized(recent) { recent.toList() }
    fun joinedRooms(): Triple<Set<String>, Set<String>, Set<String>> =
        Triple(channels.toSet(), dms.toSet(), servers.toSet())

    // Avisa a constelação que entrei/sai da call — o resto ve na hora.
    fun voiceJoin(channelId: String) { socket?.emit("voice_join", channelId) }
    fun voiceLeave(channelId: String) { socket?.emit("voice_leave", channelId) }

    // ================= CONEXAO =================
    //
    // POR QUE ISTO E MAIS COMPLICADO QUE UM `IO.socket().connect()`:
    //
    // O access token vale 15 MINUTOS. O app quase sempre reabre depois disso, ou
    // seja: o token que esta no disco chega VENCIDO no aperto de mao. O servidor
    // recusa no middleware (INVALID_TOKEN) e — verificado no bytecode do
    // socket.io-client 2.1.0 — o cliente Java chama destroy() no socket ANTES de
    // emitir connect_error. Socket destruido não retenta. Nunca.
    //
    // Resultado: o socket morria pra sempre no boot enquanto TODO o resto do app
    // continuava funcionando (o OkHttp renova sozinho no 401). Dai o sintoma
    // esquisito de "abre, tudo carrega, mas nada chega ao vivo" — e reabrir o app
    // não resolvia, porque o token do disco continuava vencido.
    //
    // Pior: a renovacao no EVENT_RECONNECT_ATTEMPT era CODIGO MORTO. O construtor
    // do Socket copia opts.auth pra um campo proprio (`this.auth = opts.auth`),
    // entao trocar opts.auth depois nunca chegava no aperto de mao.
    //
    // Conserto: a retentativa e NOSSA (reconnection = false), e TODA tentativa
    // comeca garantindo um token valido. Um relogio de 5s cuida de tudo — token
    // vencido, servidor dormindo (Render free dorme em 15min), rede caida — com
    // recuo progressivo pra não martelar.

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

    // Recuo progressivo: 1s, 2s, 4s… ate 30s. Sem isto, servidor dormindo vira
    // martelada de 5 em 5s por 50 segundos.
    private fun recuar() {
        val espera = minOf(30_000L, 1_000L shl minOf(falhasSeguidas++, 5))
        proximaTentativa = System.currentTimeMillis() + espera
    }

    // Token VALIDO na mão antes de tentar o aperto de mão.
    // A renovacao passa pelo mesmo caminho do HTTP (uma chamada autenticada barata
    // toma o 401 e o authenticator rotaciona sob lock). Ter um segundo renovador
    // aqui seria pior que o bug: o refresh token e de uso unico, os dois brigariam
    // por ele e a sessão morreria de vez.
    private suspend fun tokenValido(): String? {
        val atual = store.load()?.accessToken ?: return null
        if (!vencendo(atual)) return atual
        note("· token vencido, renovando")
        runCatching { userApi.me() }
        return store.load()?.accessToken?.takeIf { !vencendo(it) }
    }

    // Le o `exp` do JWT sem verificar assinatura — não e checagem de seguranca
    // (quem valida e o servidor), e so pra saber se vale a pena tentar. 60s de
    // folga cobre relogio fora de hora e a viagem do aperto de mão.
    private fun vencendo(jwt: String): Boolean {
        val exp = runCatching {
            val corpo = jwt.split('.').getOrNull(1) ?: return true
            val texto = String(Base64.getUrlDecoder().decode(corpo))
            Regex("\"exp\"\\s*:\\s*(\\d+)").find(texto)?.groupValues?.get(1)?.toLong()
        }.getOrNull() ?: return true // ilegivel = trata como vencido
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

        // Socket NOVO a cada tentativa: o anterior pode ter sido destruido pelo
        // proprio cliente (recusa de auth), e socket destruido não reabre direito.
        socket?.apply { runCatching { off() }; runCatching { disconnect() } }

        val opts = IO.Options().apply {
            transports = arrayOf(WebSocket.NAME, Polling.NAME)
            auth = mapOf("token" to token)
            reconnection = false // a retentativa e nossa (so ela renova o token)
            forceNew = true      // não reaproveita o Manager do socket morto
        }
        val s = runCatching { IO.socket(AstraShared.BASE_URL, opts) }.getOrNull() ?: run {
            recuar()
            return
        }
        socket = s

        // Um so lugar registra TODO evento que entra (em vez de 17 chamadas
        // espalhadas que alguem esqueceria de somar ao adicionar o 18o).
        s.onAnyIncoming { args -> note(args.firstOrNull()?.toString() ?: "?") }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val cru = args.firstOrNull()?.let { e -> (e as? Exception)?.message ?: e.toString() } ?: "desconhecido"
            lastError = emPortugues(cru)
            note("· falhou ao conectar: ${cru.take(60)}")
            recuar() // o relogio tenta de novo, com token fresco
        }
        s.on(Socket.EVENT_DISCONNECT) { args ->
            note("· desconectou (${args.firstOrNull() ?: "?"})")
            lastError = "queda de conexão — reconectando"
            // Sem recuar(): queda limpa merece retentativa rapida (proximo tique).
        }
        s.on(Socket.EVENT_CONNECT) {
            lastError = null
            falhasSeguidas = 0
            proximaTentativa = 0
            note("· conectado")
            // Re-entra nas salas apos reconectar.
            channels.forEach { s.emit("join_channel", it) }
            dms.forEach { s.emit("join_dm", it) }
            servers.forEach { s.emit("join_server", it) }
            s.emit("heartbeat") // presenca viva já no connect (o timer refresca depois)
            // So a partir da SEGUNDA conexao. Na primeira, as telas acabaram de
            // carregar sozinhas — avisar aqui so repetiria as mesmas buscas no
            // pior momento possivel (boot, com o servidor free ainda acordando).
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
        s.on("notification") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _notification.tryEmit(it.toString()) }
        }
        s.on("voice_presence") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _voicePresence.tryEmit(it.toString()) }
        }
        // Soundboard: toca DIRETO daqui, sem passar pela UI. O som e um efeito da
        // call, nao um estado de tela — mandar isso subir ate um ViewModel pra
        // descer de novo so adiaria o audio e criaria uma dependencia entre tocar
        // som e ter a tela da call composta.
        s.on("soundboard_play") { args ->
            val url = (args.firstOrNull() as? JSONObject)?.optString("url").orEmpty()
            if (url.isNotBlank()) SoundboardPlayer.tocar(url)
        }
        s.on("server_channels") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _serverChannels.tryEmit(it.toString()) }
        }
        s.on("server_members") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _serverMembers.tryEmit(it.toString()) }
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
        // Apagaram / me expulsaram / me baniram: reacao identica, um fluxo so.
        s.on("server_gone") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _serverAccessLost.tryEmit(it.toString()) }
        }
        s.on("server_left") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _serverAccessLost.tryEmit(it.toString()) }
        }
        s.on("server_roles") { args ->
            (args.firstOrNull() as? JSONObject)?.let { _serverRoles.tryEmit(it.toString()) }
        }
        // message_pinned e poll_updated o backend JA manda, mas o desktop ainda nao
        // desenha nem fixado nem enquete — escutar agora seria fio ligado em nada.
        // Entram junto com a tela, nao antes dela.
        s.connect()
    }

    // Um relogio so, duas funcoes:
    //
    // 1. Batida de presenca (a cada 25s). Mantem a chave viva no Redis (TTL 60s).
    //    Sem ela o usuário aparece OFFLINE pros outros em 1 minuto — era a
    //    "presenca atrasada". 25s da folga de 2 batidas dentro do TTL.
    // 2. Vigia da conexao (a cada 5s). Caiu? tenta de novo, respeitando o recuo.
    //    E a rede de seguranca que garante que NENHUM caminho de falha deixa o
    //    socket morto pra sempre — inclusive os que eu não previ.
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

    // Traduz o motivo cru pro que a pessoa precisa FAZER. "INVALID_TOKEN" não
    // ajuda ninguem; "sessão vencida, renovando" diz que o app esta cuidando.
    private fun emPortugues(cru: String): String = when {
        cru.contains("TOKEN_REVOKED") -> "sessão encerrada em outro lugar — entre de novo"
        cru.contains("INVALID_TOKEN") || cru.contains("AUTH_REQUIRED") -> "sessão vencida — renovando"
        cru.contains("timeout", true) -> "servidor não respondeu (pode estar acordando)"
        cru.contains("xhr", true) || cru.contains("websocket", true) -> "sem alcançar o servidor"
        else -> cru.take(60)
    }

    fun isConnected(): Boolean = socket?.connected() == true

    // Envio rapido de texto puro por socket (com ack) em vez de POST HTTP. O
    // backend insere, faz broadcast do new_message (com o clientNonce) e responde
    // o ack — a UI mostra a mensagem na hora e reconcilia quando o broadcast volta.
    // So texto puro em canal: reply e anexo continuam no HTTP (o handler não os le).
    fun fastSendText(
        channelId: String,
        content: String,
        clientNonce: String,
        onResult: (FastSendResult) -> Unit,
    ) = fastSend("fast_send_text", "channelId", channelId, content, clientNonce, onResult)

    // Mesmo caminho rapido pro SUSSURRO (fast_send_dm no backend): a bolha aparece na
    // hora e o broadcast new_dm reconcilia pelo clientNonce. So texto puro — reply e
    // anexo continuam no HTTP.
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

    // Chama a bot. NAO e uma mensagem: o backend nao guarda o comando, so responde
    // — igual a barra do Discord, onde o que voce digitou some e so a resposta
    // fica. Sem socket, devolve false pra quem chamou avisar em vez de o comando
    // sumir no vazio.
    fun sendBotCommand(channelId: String, serverId: String, content: String): Boolean {
        val s = socket?.takeIf { it.connected() } ?: return false
        s.emit("bot_command", JSONObject(mapOf(
            "channelId" to channelId,
            "serverId" to serverId,
            "content" to content,
        )))
        return true
    }

    fun joinDm(id: String) {
        dms.add(id)
        socket?.emit("join_dm", id)
    }

    fun leaveDm(id: String) {
        dms.remove(id)
        socket?.emit("leave_dm", id)
    }

    // Backend ignora typing de quem não está na sala (socket.rooms.has).
    fun startTyping(channelId: String) { socket?.emit("typing_start", channelId) }
    fun stopTyping(channelId: String) { socket?.emit("typing_stop", channelId) }
    fun startDmTyping(conversationId: String) { socket?.emit("dm_typing_start", conversationId) }
    fun stopDmTyping(conversationId: String) { socket?.emit("dm_typing_stop", conversationId) }

    // TCHAU EXPLICITO AO FECHAR O APP.
    //
    // O backend so marca OFFLINE quando o socket cai, e ele descobre isso de dois
    // jeitos com custos MUITO diferentes: fechamento limpo (frame de close) e na
    // hora; queda abrupta e so pelo relogio — pingInterval 25s + pingTimeout 20s,
    // ou seja, ate ~45 segundos de fantasma online.
    //
    // Sair do app nunca mandava esse frame: exitApplication/exitProcess derrubam a
    // JVM e o socket morre junto, sem despedida. Por isso "fechei e continuo online
    // por vários segundos" — nao era lentidao de rede, era ninguem avisar.
    //
    // Registrado como shutdown hook pra cobrir TODAS as saidas de uma vez: o X, o
    // "Sair" da bandeja e o exitProcess do atualizador. Nao cobre kill -9, e nao
    // tem como cobrir — nesse caso o relogio do servidor volta a ser a rede de
    // seguranca, que e exatamente pra isso que ele existe.
    fun registrarDespedida() {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { socket?.disconnect() }
                // O disconnect e assincrono: sem esta pausa a JVM pode terminar
                // antes de o frame sair do buffer, e ai a despedida nao acontece.
                runCatching { Thread.sleep(250) }
            },
        )
    }

    fun disconnect() {
        // Primeiro desliga a VONTADE de estar conectado: senao o vigia de 5s
        // reconectaria sozinho logo depois do logout, com a conta que acabou de sair.
        querConectar = false
        heartbeatTimer?.cancel()
        heartbeatTimer = null
        channels.clear()
        dms.clear()
        servers.clear()
        socket?.apply { off(); disconnect() }
        socket = null
        falhasSeguidas = 0
        jaConectou = false // proximo login e um primeiro connect, nao uma reconexao
        lastError = null
    }
}
