package app.astra.desktop.voice

import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.ScreenQuality
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
import dev.onvoid.webrtc.RTCRtpEncodingParameters
import dev.onvoid.webrtc.RTCRtpReceiver
import dev.onvoid.webrtc.RTCRtpSender
import dev.onvoid.webrtc.RTCRtpTransceiver
import dev.onvoid.webrtc.RTCRtpTransceiverDirection
import dev.onvoid.webrtc.RTCRtpTransceiverInit
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription
import dev.onvoid.webrtc.RTCStatsReport
import dev.onvoid.webrtc.RTCStatsType
import dev.onvoid.webrtc.SetSessionDescriptionObserver
import dev.onvoid.webrtc.media.MediaStream
import dev.onvoid.webrtc.media.MediaType
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.audio.AudioTrack
import dev.onvoid.webrtc.media.video.CustomVideoSource
import dev.onvoid.webrtc.media.video.VideoDesktopSource
import dev.onvoid.webrtc.media.video.VideoTrack
import dev.onvoid.webrtc.media.video.VideoTrackSource
import dev.onvoid.webrtc.media.video.desktop.DesktopSource
import dev.onvoid.webrtc.media.video.desktop.ScreenCapturer
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
    data class Connected(
        val others: List<VoiceParticipant>,
        val audioLive: Boolean = false,
        val mySpeaking: Boolean = false,
    ) : VoiceStatus
    data class Failed(val reason: String) : VoiceStatus
    data object Closed : VoiceStatus
}

data class VoiceParticipant(val identity: String, val label: String, val speaking: Boolean)

// Transmissao de outro participante (track de video remota; render no VoiceView).
class RemoteVideo(val ownerSid: String, val ownerLabel: String, val track: VideoTrack)

// Metricas da MINHA transmissao (poll de getStats). captureFps = fps que o
// capturer de tela produz; sendFps = fps que sai codificado; limit = por que o
// WebRTC degradou (none/cpu/bandwidth/other). captureFps baixo = gargalo na
// CAPTURA (nao da pra trocar o metodo nesta lib); sendFps < captureFps = encoder
// degradando framerate (default de conteudo de tela e manter resolucao).
data class ScreenStats(val captureFps: Int, val sendFps: Int, val limit: String)

// Frame do auto-preview local (Discord): ARGB cru + dimensoes. Vem direto da
// captura (ScreenCaptureFfmpeg), NAO do sink da track — o webrtc-java nao entrega
// frames de CustomVideoSource pra sink local. A UI faz makeRaster disto.
class ScreenPreview(val argb: ByteArray, val width: Int, val height: Int)

// V3+V4+V5 — OUVIR, FALAR e TRANSMITIR. Subscriber PC (LiveKit e subscriber-
// primary: o SERVIDOR manda o offer; a gente responde answer) + publisher PC
// (a gente manda o offer DEPOIS do AddTrackRequest ser aceito — ordem do
// protocolo). ICE via trickle nos dois alvos. Audio remoto toca sozinho no
// device padrao; mic sobe com AEC/NS/AGC (AudioOptions); tela sobe a 60fps
// (VideoDesktopSource + encodings maxFramerate/maxBitrate + H264 preferido).
class VoiceEngine(
    private val scope: CoroutineScope,
    private val voiceApi: VoiceApi,
    private val wsClient: OkHttpClient,
    private val prefs: DesktopPrefs,
) {
    private val _status = MutableStateFlow<VoiceStatus>(VoiceStatus.Connecting)
    val status = _status.asStateFlow()

    private var ws: WebSocket? = null
    private var factory: PeerConnectionFactory? = null
    private var pingJob: Job? = null
    private var myIdentity: String? = null
    private var mySid: String? = null
    @Volatile private var mySpeaking = false
    private var joined = false

    // identity -> outro participante (ordem de chegada). speaking vem por sid
    // (SpeakersChanged fala em sid, nao identity).
    private class Remote(val sid: String, val label: String, var speaking: Boolean = false)
    private val others = linkedMapOf<String, Remote>()

    // Transmissoes remotas (video). Track de audio remota nao entra aqui: toca
    // sozinha no device padrao.
    private val _remoteVideos = MutableStateFlow<List<RemoteVideo>>(emptyList())
    val remoteVideos = _remoteVideos.asStateFlow()

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

    // Transmissao de tela (V5). screenSource = capturador GDI (fallback); quando o
    // caminho rapido (ffmpeg DXGI) pega, a fonte e a customSource + ffmpegCap.
    private var screenSource: VideoDesktopSource? = null
    private var customSource: CustomVideoSource? = null
    private var ffmpegCap: ScreenCaptureFfmpeg? = null
    private var screenTrack: VideoTrack? = null
    private var screenCid: String? = null
    private var screenSender: RTCRtpSender? = null
    private val _screenOn = MutableStateFlow(false)
    val screenOn = _screenOn.asStateFlow()

    // Auto-preview: a MINHA track de tela pra eu ver o que estou transmitindo
    // (igual Discord). Track local aceita sink como remota; some ao parar.
    private val _localScreen = MutableStateFlow<VideoTrack?>(null)
    val localScreen = _localScreen.asStateFlow()

    // Frames do auto-preview (caminho ffmpeg). Null = sem preview direto (ex.:
    // fallback GDI) -> a UI cai pro sink da track.
    private val _localPreview = MutableStateFlow<ScreenPreview?>(null)
    val localPreview = _localPreview.asStateFlow()

    // Metricas da transmissao (poll a cada ~1.5s enquanto compartilho).
    private val _screenStats = MutableStateFlow<ScreenStats?>(null)
    val screenStats = _screenStats.asStateFlow()
    private var statsJob: Job? = null

    // Preset ativo da transmissao (Settings > Voz). Capturado da pref no
    // startScreenShare; TrackPublished/attachScreen leem daqui. Default = 1080p60
    // (requisito original do dono: 60fps no minimo).
    private var screenQ: ScreenQuality = ScreenQuality.HIGH_1080_60

    // Ultima fonte transmitida — pra reiniciar a captura no MESMO monitor quando o
    // dono troca a qualidade ao vivo pelo gear da call.
    private var lastScreenSource: DesktopSource? = null

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
                mySid = res.join.participant.sid
                others.clear()
                res.join.otherParticipantsList.forEach { others[it.identity] = it.remote() }
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
                        _remoteVideos.value = _remoteVideos.value.filterNot { it.ownerSid == p.sid }
                    } else {
                        // Preserva speaking: UPDATE nao fala de voz ativa.
                        others[p.identity] = p.remote(others[p.identity]?.speaking ?: false)
                    }
                }
                if (joined) publishConnected()
            }
            LivekitRtc.SignalResponse.MessageCase.SPEAKERS_CHANGED -> {
                res.speakersChanged.speakersList.forEach { sp ->
                    if (sp.sid == mySid) mySpeaking = sp.active
                    else others.values.find { it.sid == sp.sid }?.speaking = sp.active
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

            // Track de video remota = transmissao de alguem. O LiveKit nomeia o
            // MediaStream como "participantSid|trackSid" — dai sai o dono.
            override fun onAddTrack(receiver: RTCRtpReceiver, streams: Array<MediaStream>) {
                val track = receiver.track as? VideoTrack ?: return
                val ownerSid = streams.firstOrNull()?.id()?.substringBefore('|') ?: return
                val label = others.values.find { it.sid == ownerSid }?.label ?: "transmissao"
                _remoteVideos.value = _remoteVideos.value + RemoteVideo(ownerSid, label, track)
            }

            override fun onRemoveTrack(receiver: RTCRtpReceiver) {
                val gone = runCatching { receiver.track?.id }.getOrNull() ?: return
                _remoteVideos.value = _remoteVideos.value.filterNot { v ->
                    runCatching { v.track.id == gone }.getOrDefault(true)
                }
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
        // Sem mic/permissao nao derruba a sala: segue so ouvindo. Processamento
        // vem das prefs (Settings > Voz); highpass acompanha a supressao de ruido.
        val ap = prefs.state.value
        val source = runCatching {
            f.createAudioSource(AudioOptions().apply {
                echoCancellation = ap.micEchoCancel
                noiseSuppression = ap.micNoiseSuppression
                autoGainControl = ap.micAutoGain
                highpassFilter = ap.micNoiseSuppression
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
        when (res.cid) {
            micCid -> {
                micSid = res.track.sid
                attachMic(res.cid)
            }
            screenCid -> attachScreen(res.cid)
        }
    }

    private fun attachMic(cid: String) {
        val track = micTrack ?: return
        val init = RTCRtpTransceiverInit().apply {
            direction = RTCRtpTransceiverDirection.SEND_ONLY
            streamIds = listOf(cid)
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

    // ---- V5: transmissao de tela — 60fps NO MINIMO (requisito do dono) --------

    // Monitores disponiveis (id + titulo). Enumeracao pontual; capturer descartado.
    fun screens(): List<DesktopSource> {
        val cap = runCatching { ScreenCapturer() }.getOrNull() ?: return emptyList()
        return try {
            cap.desktopSources
        } finally {
            runCatching { cap.dispose() }
        }
    }

    // Caminho rapido (ffmpeg DXGI). Retorna a fonte se os frames COMECARAM a fluir;
    // null = indisponivel/falhou nesta maquina (o chamador tenta o GDI).
    private fun startFastCapture(source: DesktopSource?, q: ScreenQuality): VideoTrackSource? {
        if (factory == null) return null
        val ffPath = FfmpegLocator.path ?: return null
        val outIdx = source?.let { s -> screens().indexOfFirst { it.id == s.id } }?.coerceAtLeast(0) ?: 0
        val custom = CustomVideoSource()
        val cap = ScreenCaptureFfmpeg(ffPath, custom) { argb, w, h ->
            _localPreview.value = ScreenPreview(argb, w, h)
        }
        if (!cap.start(outIdx, q.width, q.height, q.fps)) {
            cap.stop()
            runCatching { custom.dispose() }
            return null
        }
        customSource = custom
        ffmpegCap = cap
        return custom
    }

    // Fallback GDI (VideoDesktopSource): roda em qualquer maquina, ~20-30fps. fps
    // na captura; resolucao capada no preset (o webrtc-java nao expoe
    // degradationPreference, entao nao dar ao encoder mais pixels do que segura).
    private fun startGdiCapture(source: DesktopSource?, q: ScreenQuality): VideoTrackSource? {
        val target = source ?: screens().firstOrNull() ?: return null
        val src = runCatching {
            VideoDesktopSource().apply {
                setSourceId(target.id, false)
                setFrameRate(q.fps)
                setMaxFrameSize(q.width, q.height)
                start()
            }
        }.getOrNull() ?: return null
        screenSource = src
        return src
    }

    fun startScreenShare(source: DesktopSource? = null) {
        if (_screenOn.value) return
        val f = factory ?: return
        lastScreenSource = source
        screenQ = prefs.state.value.screenQuality
        val q = screenQ

        // Caminho RAPIDO: ffmpeg ddagrab (DXGI) empurrando frames num CustomVideoSource
        // — o equivalente desktop da captura de hardware que o mobile ganha do
        // MediaProjection. Se falhar nesta maquina, FALLBACK pro GDI (VideoDesktopSource,
        // ~20-30fps) — assim roda em todo PC, so muda o fps.
        val trackSource: VideoTrackSource? = startFastCapture(source, q) ?: startGdiCapture(source, q)
        if (trackSource == null) return

        val cid = "screen-" + UUID.randomUUID().toString().take(8)
        screenCid = cid
        screenTrack = f.createVideoTrack(cid, trackSource)
        _localScreen.value = screenTrack // preview local ja com os frames da captura
        _screenOn.value = true
        val req = LivekitRtc.SignalRequest.newBuilder()
            .setAddTrack(
                LivekitRtc.AddTrackRequest.newBuilder()
                    .setCid(cid)
                    .setName("screen")
                    .setType(LivekitModels.TrackType.VIDEO)
                    .setSource(LivekitModels.TrackSource.SCREEN_SHARE)
                    .setWidth(q.width)
                    .setHeight(q.height)
                    .addLayers(
                        LivekitModels.VideoLayer.newBuilder()
                            .setQuality(LivekitModels.VideoQuality.HIGH)
                            .setWidth(q.width)
                            .setHeight(q.height)
                            .setBitrate(q.bitrate),
                    ),
            )
            .build()
        ws?.send(req.toByteArray().toByteString())
    }

    private fun attachScreen(cid: String) {
        val track = screenTrack ?: return
        val init = RTCRtpTransceiverInit().apply {
            direction = RTCRtpTransceiverDirection.SEND_ONLY
            streamIds = listOf(cid)
            sendEncodings = listOf(
                RTCRtpEncodingParameters().apply {
                    maxFramerate = screenQ.fps.toDouble()
                    maxBitrate = screenQ.bitrate
                },
            )
        }
        val transceiver = runCatching { pub?.addTransceiver(track, init) }.getOrNull() ?: return
        screenSender = transceiver.sender
        preferH264(transceiver)
        negotiatePublisher()
        // Reforca os encodings no sender ja negociado (o init nem sempre carrega
        // maxBitrate/maxFramerate pela munge do SDP) + comeca a medir os fps.
        scope.launch {
            delay(1200)
            reinforceScreenSender()
            startScreenStats()
        }
    }

    // Re-aplica maxFramerate/maxBitrate direto no sender (via setParameters) — jeito
    // confiavel de garantir que o teto vale, ja que init encodings as vezes se perde.
    private fun reinforceScreenSender() {
        val sender = screenSender ?: return
        runCatching {
            val params = sender.parameters ?: return
            params.encodings?.firstOrNull()?.apply {
                active = true
                maxFramerate = screenQ.fps.toDouble()
                maxBitrate = screenQ.bitrate
                scaleResolutionDownBy = 1.0
            }
            sender.parameters = params
        }
    }

    // Poll de getStats no sender da tela: expoe fps de captura e de envio + o motivo
    // da degradacao. E como saber SE bateu 60 e, se nao, ONDE travou (captura/cpu/banda).
    private fun startScreenStats() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive && _screenOn.value) {
                val pc = pub
                val sender = screenSender
                if (pc != null && sender != null) {
                    runCatching { pc.getStats(sender) { report -> parseScreenStats(report) } }
                }
                delay(1500)
            }
        }
    }

    private fun parseScreenStats(report: RTCStatsReport) {
        var capture = 0
        var send = 0
        var limit = "none"
        report.stats.values.forEach { s ->
            val a = s.attributes
            when (s.type) {
                RTCStatsType.MEDIA_SOURCE -> (a["framesPerSecond"] as? Number)?.let { capture = it.toInt() }
                RTCStatsType.OUTBOUND_RTP -> {
                    (a["framesPerSecond"] as? Number)?.let { send = it.toInt() }
                    (a["qualityLimitationReason"] as? String)?.let { limit = it }
                }
                else -> Unit
            }
        }
        _screenStats.value = ScreenStats(capture, send, limit)
    }

    // H264 primeiro (paridade com o web tunado 60fps H264); rtx/red/fec continuam
    // na lista — so reordena.
    private fun preferH264(transceiver: RTCRtpTransceiver) {
        runCatching {
            val codecs = factory?.getRtpSenderCapabilities(MediaType.VIDEO)?.codecs ?: return
            val (h264, rest) = codecs.partition { it.name.equals("H264", ignoreCase = true) }
            if (h264.isNotEmpty()) transceiver.setCodecPreferences(h264 + rest)
        }
    }

    fun stopScreenShare() {
        if (!_screenOn.value) return
        _screenOn.value = false
        _localScreen.value = null
        _localPreview.value = null
        statsJob?.cancel()
        _screenStats.value = null
        screenSender?.let { runCatching { pub?.removeTrack(it) } }
        screenSender = null
        ffmpegCap?.stop()
        ffmpegCap = null
        runCatching { customSource?.dispose() }
        customSource = null
        runCatching { screenSource?.stop() }
        runCatching { screenTrack?.dispose() }
        runCatching { screenSource?.dispose() }
        screenTrack = null
        screenSource = null
        screenCid = null
        // m-line desativada na renegociacao => o server despublica a track.
        negotiatePublisher()
    }

    // Troca a qualidade/fluidez da transmissao ao vivo (gear da call): persiste a
    // pref e, se ja estou transmitindo, reinicia a captura no MESMO monitor com o
    // novo preset (o ffmpeg e spawnado com w/h/fps assados -> so reiniciando muda).
    fun setScreenQuality(q: ScreenQuality) {
        prefs.setScreenQuality(q)
        if (!_screenOn.value) return
        val src = lastScreenSource
        stopScreenShare()
        scope.launch {
            delay(350) // deixa a renegociacao do stop assentar antes de republicar
            startScreenShare(src)
        }
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
        _status.value = VoiceStatus.Connected(
            others.map { (identity, r) -> VoiceParticipant(identity, r.label, r.speaking) },
            audioLive,
            mySpeaking,
        )
    }

    private fun LivekitModels.ParticipantInfo.remote(speaking: Boolean = false) =
        Remote(sid, name.ifBlank { identity }, speaking)

    fun dispose() {
        pingJob?.cancel()
        statsJob?.cancel()
        // Despedida educada: o servidor libera o slot na hora em vez de esperar timeout.
        runCatching {
            val leave = LivekitRtc.SignalRequest.newBuilder()
                .setLeave(LivekitRtc.LeaveRequest.newBuilder().build())
                .build()
            ws?.send(leave.toByteArray().toByteString())
        }
        ws?.close(1000, "leave")
        ws = null
        _remoteVideos.value = emptyList()
        _localScreen.value = null
        _localPreview.value = null
        ffmpegCap?.stop()
        ffmpegCap = null
        runCatching { customSource?.dispose() }
        customSource = null
        runCatching { screenSource?.stop() }
        runCatching { screenTrack?.dispose() }
        runCatching { screenSource?.dispose() }
        screenTrack = null
        screenSource = null
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
