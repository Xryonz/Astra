package app.astra.desktop.voice

import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.VoiceTokenRequest
import dev.onvoid.webrtc.CreateSessionDescriptionObserver
import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.PeerConnectionObserver
import dev.onvoid.webrtc.RTCAnswerOptions
import dev.onvoid.webrtc.RTCConfiguration
import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCIceServer
import dev.onvoid.webrtc.RTCOfferOptions
import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCPeerConnectionState
import dev.onvoid.webrtc.RTCRtpTransceiverDirection
import dev.onvoid.webrtc.RTCRtpTransceiverInit
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription
import dev.onvoid.webrtc.SetSessionDescriptionObserver
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.audio.AudioTrack
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import livekit.LivekitModels
import livekit.LivekitRtc
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

// Estado da voz (plano em docs/plans/2026-07-10-astra-voz-nativa.md).
sealed interface VoiceStatus {
    data object Connecting : VoiceStatus

    // Join aceito e ping/pong mantendo a sessao viva. audioLive = subscriber PC
    // conectado (DTLS/RTP fluindo — audio remoto toca no device padrao).
    data class Connected(val others: List<String>, val audioLive: Boolean = false) : VoiceStatus
    data class Failed(val reason: String) : VoiceStatus
    data object Closed : VoiceStatus
}

// V3+V4 — OUVIR e FALAR. Subscriber PC (LiveKit e subscriber-primary: o SERVIDOR
// manda o offer; a gente responde answer) + publisher PC (a gente manda o offer
// DEPOIS do AddTrackRequest ser aceito — ordem do protocolo). ICE via trickle nos
// dois alvos. Audio remoto toca sozinho no device padrao; mic sobe com AEC/NS/AGC
// (AudioOptions). Transmissao de tela 60fps = V5.
class VoiceEngine(
    private val scope: CoroutineScope,
    private val voiceApi: VoiceApi,
    private val wsClient: OkHttpClient,
) {
    private val _status = MutableStateFlow<VoiceStatus>(VoiceStatus.Connecting)
    val status = _status.asStateFlow()

    private var ws: WebSocket? = null
    private var factory: PeerConnectionFactory? = null
    private var pingJob: Job? = null
    private var myIdentity: String? = null
    private var joined = false

    // identity -> nome exibido dos outros participantes (ordem de chegada).
    private val others = linkedMapOf<String, String>()

    // Subscriber PC (server -> a gente). Callbacks nativos chegam em threads do
    // WebRTC e o signaling na thread do WS — dai o lock nos candidatos pendentes
    // (trickle pode chegar antes do setRemoteDescription concluir).
    private var sub: RTCPeerConnection? = null
    private val pendingCandidates = mutableListOf<RTCIceCandidate>()
    private var subRemoteSet = false
    @Volatile private var audioLive = false

    // Publisher PC (a gente -> server): mic na V4, tela na V5.
    private var pub: RTCPeerConnection? = null
    private val pubPendingCandidates = mutableListOf<RTCIceCandidate>()
    private var pubRemoteSet = false
    private var micTrack: AudioTrack? = null
    private var micCid: String? = null
    private var micSid: String? = null

    // Mic comeca ligado ao entrar (padrao Discord); toggleMic() alterna.
    private val _micOn = MutableStateFlow(true)
    val micOn = _micOn.asStateFlow()

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
                        val res = runCatching { LivekitRtc.SignalResponse.parseFrom(bytes.toByteArray()) }
                            .getOrNull() ?: return
                        handleSignal(res)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        pingJob?.cancel()
                        _status.value =
                            if (joined) VoiceStatus.Closed
                            else VoiceStatus.Failed("Signaling recusou: ${t.message ?: "erro"}")
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        pingJob?.cancel()
                        if (_status.value !is VoiceStatus.Failed) _status.value = VoiceStatus.Closed
                    }
                },
            )
        }
    }

    private fun handleSignal(res: LivekitRtc.SignalResponse) {
        when (res.messageCase) {
            LivekitRtc.SignalResponse.MessageCase.JOIN -> {
                joined = true
                myIdentity = res.join.participant.identity
                others.clear()
                res.join.otherParticipantsList.forEach { others[it.identity] = it.label() }
                createSubscriber(res.join.iceServersList)
                createPublisher(res.join.iceServersList)
                publishMic()
                startPing(res.join.pingInterval)
                publishConnected()
            }
            LivekitRtc.SignalResponse.MessageCase.OFFER -> onServerOffer(res.offer.sdp)
            LivekitRtc.SignalResponse.MessageCase.ANSWER -> onServerAnswer(res.answer.sdp)
            LivekitRtc.SignalResponse.MessageCase.TRICKLE -> onTrickle(res.trickle)
            LivekitRtc.SignalResponse.MessageCase.TRACK_PUBLISHED -> onTrackPublished(res.trackPublished)
            LivekitRtc.SignalResponse.MessageCase.UPDATE -> {
                res.update.participantsList.forEach { p ->
                    if (p.identity == myIdentity) return@forEach
                    if (p.state == LivekitModels.ParticipantInfo.State.DISCONNECTED) {
                        others.remove(p.identity)
                    } else {
                        others[p.identity] = p.label()
                    }
                }
                if (joined) publishConnected()
            }
            LivekitRtc.SignalResponse.MessageCase.LEAVE -> {
                _status.value = VoiceStatus.Closed
                ws?.close(1000, "leave")
            }
            // speakers_changed/connection_quality etc entram na V6 (UI da sala).
            else -> Unit
        }
    }

    // ---- V3: subscriber PC (ouvir) -------------------------------------------

    private fun rtcConfig(iceServers: List<LivekitRtc.ICEServer>) = RTCConfiguration().apply {
        this.iceServers = iceServers.map { s ->
            RTCIceServer().apply {
                urls = s.urlsList
                username = s.username
                password = s.credential
            }
        }
    }

    // Formato do LiveKit: candidateInit em JSON (igual ao client-sdk-js).
    private fun sendTrickle(candidate: RTCIceCandidate, target: LivekitRtc.SignalTarget) {
        val init = buildJsonObject {
            put("candidate", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        val req = LivekitRtc.SignalRequest.newBuilder()
            .setTrickle(
                LivekitRtc.TrickleRequest.newBuilder()
                    .setCandidateInit(init.toString())
                    .setTarget(target),
            )
            .build()
        ws?.send(req.toByteArray().toByteString())
    }

    private fun createSubscriber(iceServers: List<LivekitRtc.ICEServer>) {
        sub = factory?.createPeerConnection(rtcConfig(iceServers), object : PeerConnectionObserver {
            override fun onIceCandidate(candidate: RTCIceCandidate) =
                sendTrickle(candidate, LivekitRtc.SignalTarget.SUBSCRIBER)

            override fun onConnectionChange(state: RTCPeerConnectionState) {
                audioLive = state == RTCPeerConnectionState.CONNECTED
                if (joined) publishConnected()
            }
        })
    }

    // Server manda o offer (subscriber-primary); renegociacoes (alguem publicou
    // track nova) chegam pelo mesmo caminho — a cadeia inteira se repete.
    private fun onServerOffer(sdp: String) {
        val pc = sub ?: return
        pc.setRemoteDescription(
            RTCSessionDescription(RTCSdpType.OFFER, sdp),
            object : SetSessionDescriptionObserver {
                override fun onSuccess() {
                    synchronized(pendingCandidates) {
                        subRemoteSet = true
                        pendingCandidates.forEach { pc.addIceCandidate(it) }
                        pendingCandidates.clear()
                    }
                    pc.createAnswer(
                        RTCAnswerOptions(),
                        object : CreateSessionDescriptionObserver {
                            override fun onSuccess(desc: RTCSessionDescription) {
                                pc.setLocalDescription(
                                    desc,
                                    object : SetSessionDescriptionObserver {
                                        override fun onSuccess() = sendAnswer(desc.sdp)
                                        override fun onFailure(error: String) = Unit
                                    },
                                )
                            }
                            override fun onFailure(error: String) = Unit
                        },
                    )
                }
                override fun onFailure(error: String) = Unit
            },
        )
    }

    private fun sendAnswer(sdp: String) {
        val req = LivekitRtc.SignalRequest.newBuilder()
            .setAnswer(LivekitRtc.SessionDescription.newBuilder().setType("answer").setSdp(sdp))
            .build()
        ws?.send(req.toByteArray().toByteString())
    }

    private fun onTrickle(trickle: LivekitRtc.TrickleRequest) {
        val init = runCatching { Json.parseToJsonElement(trickle.candidateInit).jsonObject }
            .getOrNull() ?: return
        val candidate = RTCIceCandidate(
            init["sdpMid"]?.jsonPrimitive?.content,
            init["sdpMLineIndex"]?.jsonPrimitive?.int ?: 0,
            init["candidate"]?.jsonPrimitive?.content ?: return,
        )
        if (trickle.target == LivekitRtc.SignalTarget.SUBSCRIBER) {
            synchronized(pendingCandidates) {
                if (subRemoteSet) sub?.addIceCandidate(candidate) else pendingCandidates.add(candidate)
            }
        } else {
            synchronized(pubPendingCandidates) {
                if (pubRemoteSet) pub?.addIceCandidate(candidate) else pubPendingCandidates.add(candidate)
            }
        }
    }

    // ---- V4: publisher PC (falar) --------------------------------------------

    private fun createPublisher(iceServers: List<LivekitRtc.ICEServer>) {
        pub = factory?.createPeerConnection(rtcConfig(iceServers), object : PeerConnectionObserver {
            override fun onIceCandidate(candidate: RTCIceCandidate) =
                sendTrickle(candidate, LivekitRtc.SignalTarget.PUBLISHER)
        })
    }

    // Ordem do protocolo: AddTrackRequest primeiro; a track so entra no PC (e a
    // negociacao so acontece) quando o server responde TrackPublished com o cid.
    private fun publishMic() {
        val f = factory ?: return
        // Sem mic/permissao nao derruba a sala: segue so ouvindo.
        val source = runCatching {
            f.createAudioSource(AudioOptions().apply {
                echoCancellation = true
                noiseSuppression = true
                autoGainControl = true
                highpassFilter = true
            })
        }.getOrNull() ?: return
        val cid = "mic-" + UUID.randomUUID().toString().take(8)
        micCid = cid
        micTrack = f.createAudioTrack(cid, source)
        val req = LivekitRtc.SignalRequest.newBuilder()
            .setAddTrack(
                LivekitRtc.AddTrackRequest.newBuilder()
                    .setCid(cid)
                    .setName("microphone")
                    .setType(LivekitModels.TrackType.AUDIO)
                    .setSource(LivekitModels.TrackSource.MICROPHONE),
            )
            .build()
        ws?.send(req.toByteArray().toByteString())
    }

    private fun onTrackPublished(res: LivekitRtc.TrackPublishedResponse) {
        if (res.cid != micCid) return
        micSid = res.track.sid
        val track = micTrack ?: return
        val init = RTCRtpTransceiverInit().apply {
            direction = RTCRtpTransceiverDirection.SEND_ONLY
            streamIds = listOf(res.cid)
        }
        runCatching { pub?.addTransceiver(track, init) }.onFailure { return }
        negotiatePublisher()
    }

    private fun negotiatePublisher() {
        val pc = pub ?: return
        pc.createOffer(
            RTCOfferOptions(),
            object : CreateSessionDescriptionObserver {
                override fun onSuccess(desc: RTCSessionDescription) {
                    pc.setLocalDescription(
                        desc,
                        object : SetSessionDescriptionObserver {
                            override fun onSuccess() {
                                val req = LivekitRtc.SignalRequest.newBuilder()
                                    .setOffer(
                                        LivekitRtc.SessionDescription.newBuilder()
                                            .setType("offer")
                                            .setSdp(desc.sdp),
                                    )
                                    .build()
                                ws?.send(req.toByteArray().toByteString())
                            }
                            override fun onFailure(error: String) = Unit
                        },
                    )
                }
                override fun onFailure(error: String) = Unit
            },
        )
    }

    private fun onServerAnswer(sdp: String) {
        val pc = pub ?: return
        pc.setRemoteDescription(
            RTCSessionDescription(RTCSdpType.ANSWER, sdp),
            object : SetSessionDescriptionObserver {
                override fun onSuccess() {
                    synchronized(pubPendingCandidates) {
                        pubRemoteSet = true
                        pubPendingCandidates.forEach { pc.addIceCandidate(it) }
                        pubPendingCandidates.clear()
                    }
                }
                override fun onFailure(error: String) = Unit
            },
        )
    }

    // Mute local (track para de mandar frames) + aviso pro server (icone de mute
    // aparece pros outros).
    fun toggleMic() {
        val track = micTrack ?: return
        val on = !_micOn.value
        track.isEnabled = on
        _micOn.value = on
        val sid = micSid ?: return
        val req = LivekitRtc.SignalRequest.newBuilder()
            .setMute(LivekitRtc.MuteTrackRequest.newBuilder().setSid(sid).setMuted(!on))
            .build()
        ws?.send(req.toByteArray().toByteString())
    }

    // O servidor derruba quem fica mudo: ping no intervalo do JoinResponse.
    private fun startPing(intervalSec: Int) {
        val interval = (if (intervalSec > 0) intervalSec else 15) * 1000L
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(interval)
                val req = LivekitRtc.SignalRequest.newBuilder()
                    .setPing(System.currentTimeMillis())
                    .build()
                ws?.send(req.toByteArray().toByteString())
            }
        }
    }

    private fun publishConnected() {
        _status.value = VoiceStatus.Connected(others.values.toList(), audioLive)
    }

    private fun LivekitModels.ParticipantInfo.label(): String = name.ifBlank { identity }

    fun dispose() {
        pingJob?.cancel()
        // Despedida educada: o servidor libera o slot na hora em vez de esperar timeout.
        runCatching {
            val leave = LivekitRtc.SignalRequest.newBuilder()
                .setLeave(LivekitRtc.LeaveRequest.newBuilder().build())
                .build()
            ws?.send(leave.toByteArray().toByteString())
        }
        ws?.close(1000, "leave")
        ws = null
        runCatching { micTrack?.dispose() }
        micTrack = null
        runCatching { pub?.close() }
        pub = null
        runCatching { sub?.close() }
        sub = null
        runCatching { factory?.dispose() }
        factory = null
    }
}
