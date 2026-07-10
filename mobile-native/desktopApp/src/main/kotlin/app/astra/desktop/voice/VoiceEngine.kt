package app.astra.desktop.voice

import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.VoiceTokenRequest
import dev.onvoid.webrtc.PeerConnectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

// Estado da sonda de voz (V1 do plano em docs/plans/2026-07-10-astra-voz-nativa.md).
sealed interface VoiceStatus {
    data object Connecting : VoiceStatus

    // WS aberto e primeiro frame binario recebido (JoinResponse do LiveKit).
    // O parse do protobuf e a V2 — aqui so provamos que o caminho existe.
    data object SignalOk : VoiceStatus
    data class Failed(val reason: String) : VoiceStatus
    data object Closed : VoiceStatus
}

// V1 — SONDA: prova os tres riscos da fase de voz nativa de uma vez:
// (1) os natives do webrtc-java carregam na JVM empacotada;
// (2) o backend entrega token+url de voz pro desktop;
// (3) o WS de signaling do LiveKit aceita a conexao (JoinResponse chega).
// Audio de verdade comeca na V3; a conexao cai por timeout de ping (sem pong
// ainda) — esperado nesta fase.
class VoiceEngine(
    private val scope: CoroutineScope,
    private val voiceApi: VoiceApi,
    private val wsClient: OkHttpClient,
) {
    private val _status = MutableStateFlow<VoiceStatus>(VoiceStatus.Connecting)
    val status = _status.asStateFlow()

    private var ws: WebSocket? = null
    private var factory: PeerConnectionFactory? = null

    fun connect(roomKind: String, roomId: String) {
        scope.launch {
            val nativesOk = withContext(Dispatchers.IO) {
                // UnsatisfiedLinkError e Error, nao Exception — catch amplo aqui.
                try {
                    factory = PeerConnectionFactory()
                    true
                } catch (t: Throwable) {
                    false
                }
            }
            if (!nativesOk) {
                _status.value = VoiceStatus.Failed("WebRTC nativo nao carregou nesta maquina")
                return@launch
            }

            val data = runCatching { voiceApi.token(VoiceTokenRequest(roomKind, roomId)).data }.getOrNull()
            if (data == null) {
                _status.value = VoiceStatus.Failed("Backend nao deu o token de voz")
                return@launch
            }

            val url = data.url.trimEnd('/') + "/rtc?access_token=" + data.token + "&auto_subscribe=1&protocol=15"
            ws = wsClient.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        _status.value = VoiceStatus.SignalOk
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (_status.value != VoiceStatus.SignalOk) {
                            _status.value = VoiceStatus.Failed("Signaling recusou: ${t.message ?: "erro"}")
                        } else {
                            _status.value = VoiceStatus.Closed
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (_status.value !is VoiceStatus.Failed) _status.value = VoiceStatus.Closed
                    }
                },
            )
        }
    }

    fun dispose() {
        ws?.close(1000, "leave")
        ws = null
        runCatching { factory?.dispose() }
        factory = null
    }
}
