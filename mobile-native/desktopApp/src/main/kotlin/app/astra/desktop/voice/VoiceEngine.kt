package app.astra.desktop.voice

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.ScreenQuality
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Image as SkiaImage
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
import dev.onvoid.webrtc.media.audio.AudioDeviceModule
import dev.onvoid.webrtc.media.audio.AudioTrack
import dev.onvoid.webrtc.media.audio.CustomAudioSource
import dev.onvoid.webrtc.media.MediaDevices
import dev.onvoid.webrtc.media.video.CustomVideoSource
import dev.onvoid.webrtc.media.video.VideoDesktopSource
import dev.onvoid.webrtc.media.video.VideoDevice
import dev.onvoid.webrtc.media.video.VideoDeviceSource
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
import kotlinx.serialization.json.contentOrNull
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

    // Join aceito e ping/pong mantendo a sessão viva. audioLive = subscriber PC
    // conectado (DTLS/RTP fluindo — audio remoto toca no device padrao).
    data class Connected(
        val others: List<VoiceParticipant>,
        val audioLive: Boolean = false,
        val mySpeaking: Boolean = false,
    ) : VoiceStatus
    data class Failed(val reason: String) : VoiceStatus
    data object Closed : VoiceStatus
}

data class VoiceParticipant(val identity: String, val label: String, val speaking: Boolean, val avatarUrl: String? = null)

// Transmissao de outro participante (track de video remota; render no VoiceView).
class RemoteVideo(val ownerSid: String, val ownerLabel: String, val track: VideoTrack)

// Metricas da MINHA transmissão (poll de getStats). captureFps = fps que o
// capturer de tela produz; sendFps = fps que sai codificado; limit = por que o
// WebRTC degradou (none/cpu/bandwidth/other). captureFps baixo = gargalo na
// CAPTURA (não da pra trocar o metodo nesta lib); sendFps < captureFps = encoder
// degradando framerate (default de conteudo de tela e manter resolucao).
data class ScreenStats(val captureFps: Int, val sendFps: Int, val limit: String)

// Frame do auto-preview local (Discord): já como ImageBitmap PRONTO. Vem direto da
// captura (ScreenCaptureFfmpeg), NAO do sink da track — o webrtc-java não entrega
// frames de CustomVideoSource pra sink local. O makeRaster roda na thread do preview
// (fora da UI) via PreviewRasters: antes a UI fazia makeRaster (~2MB) por frame e, com
// conteudo animado (video/jogo), o palco engasgava.
class ScreenPreview(val image: ImageBitmap, val width: Int, val height: Int)

// Recicla as Skia Images do preview local. makeRaster COPIA os pixels pra memoria
// NATIVA (off-heap, liberada so no GC) — a 60fps empilha GBs. Fecha cada imagem 2
// quadros depois de sair de cena (o render ja passou dela). Mesma tecnica do
// RasterRecycler do VoiceView, aqui pro caminho do preview direto.
private class PreviewRasters {
    private var prev: SkiaImage? = null
    private var prevPrev: SkiaImage? = null
    fun wrap(argb: ByteArray, w: Int, h: Int): ImageBitmap {
        val img = SkiaImage.makeRaster(
            ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.OPAQUE), argb, w * 4,
        )
        runCatching { prevPrev?.close() }
        prevPrev = prev; prev = img
        return img.toComposeImageBitmap()
    }
    fun dispose() {
        runCatching { prev?.close() }; runCatching { prevPrev?.close() }
        prev = null; prevPrev = null
    }
}

// Deteccao de fala por NIVEL DE AUDIO (getStats) — independe do speakers_changed do
// servidor (que não chega no nosso WS hand-rolled). Nivel 0..1; voz ativa passa do
// threshold. Hangover segura o "falando" um tico apos cair (anti-flicker).
private const val SPEAK_THRESHOLD = 0.015
private const val SPEAK_POLL_MS = 200L
private const val SPEAK_HANGOVER_MS = 400L

// V3+V4+V5 — OUVIR, FALAR e TRANSMITIR. Subscriber PC (LiveKit e subscriber-
// primary: o SERVIDOR manda o offer; a gente responde answer) + publisher PC
// (a gente manda o offer DEPOIS do AddTrackRequest ser aceito — ordem do
// protocolo). ICE via trickle nos dois alvos. Audio remoto toca sozinho no
// device padrao (ADM de hardware); mic sobe via Java Sound (MicCapture ->
// CustomAudioSource.pushAudio — o Core Audio do webrtc-java quebra a captura);
// tela sobe a 60fps (DXGI ffmpeg / GDI + encodings maxFramerate/maxBitrate + H264).
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
    // ADM explicito: controla o PLAYOUT (ouvir os outros) + escolha do device de saida.
    private var adm: AudioDeviceModule? = null
    private var pingJob: Job? = null
    // Guarda a coroutine de connect() pra dispose() poder cancela-la, e a flag
    // marca "já fui descartado" pras atribuicoes pos-suspensao (carregar a lib
    // nativa não e instantaneo) desistirem em vez de escrever num engine morto.
    private var connectJob: Job? = null
    @Volatile private var disposed = false
    private var myIdentity: String? = null
    private var mySid: String? = null
    @Volatile private var mySpeaking = false
    private var joined = false
    private var speakingJob: Job? = null
    @Volatile private var mySpeakUntil = 0L

    // identity -> outro participante (ordem de chegada). speaking vem por sid
    // (SpeakersChanged fala em sid, não identity).
    //
    // DUAS fontes pra "esta falando", e elas não competem:
    //  • signalSpeaking — o SERVIDOR avisando (speakers_changed). É liga/desliga,
    //    sem prazo: o LiveKit só manda quando MUDA, então quem fala dois minutos
    //    seguidos gera um aviso e mais nada. Prazo aqui apagaria o anel no meio
    //    da frase.
    //  • speakUntil — o nível medido no getStats do áudio dele. Esse SIM tem
    //    prazo (hangover), porque é uma amostra e não um evento.
    private class Remote(
        val sid: String,
        val label: String,
        val avatarUrl: String? = null,
        var speaking: Boolean = false,
        var speakUntil: Long = 0,
        var signalSpeaking: Boolean = false,
    )
    private val others = linkedMapOf<String, Remote>()
    // Receiver do audio remoto por dono (ownerSid) — pra medir o nível de fala de cada um.
    private val remoteAudioReceivers = linkedMapOf<String, RTCRtpReceiver>()

    // Transmissoes remotas (video). Track de audio remota não entra aqui: toca
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
    private var micSource: CustomAudioSource? = null
    private var micCapture: MicCapture? = null
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
    // Camera reusa TODA a maquinaria da tela (track/cid/sender/_screenOn/_localScreen/
    // attachScreen/stop) — so uma fonte de video por vez. So a FONTE muda: aqui um
    // VideoDeviceSource (camera) em vez do ffmpeg/GDI. sharingCamera diz a UI se o
    // "meu palco" e camera (rotulo + preview vem do sink da track, não do ffmpeg).
    private var cameraSource: VideoDeviceSource? = null
    private val _sharingCamera = MutableStateFlow(false)
    val sharingCamera = _sharingCamera.asStateFlow()
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
    // true = ha preview DIRETO (caminho ffmpeg ligado). A UI decide o branch por AQUI
    // (muda so no start/stop) em vez de observar localPreview -> o palco NAO recompoe a
    // cada frame (60fps) so pra escolher a view. Era isto que engasgava com video/jogo.
    private val _directPreview = MutableStateFlow(false)
    val directPreview = _directPreview.asStateFlow()
    // makeRaster do preview roda na thread do preview (fora da UI) e recicla as imagens.
    private val previewRasters = PreviewRasters()

    // Metricas da transmissão (poll a cada ~1.5s enquanto compartilho).
    private val _screenStats = MutableStateFlow<ScreenStats?>(null)
    val screenStats = _screenStats.asStateFlow()
    private var statsJob: Job? = null

    // Preset ativo da transmissão (Settings > Voz). Capturado da pref no
    // startScreenShare; TrackPublished/attachScreen leem daqui. Default = 720p60
    // (foco em fluidez; 1080p foi removido — o encoder H264 e software).
    private var screenQ: ScreenQuality = ScreenQuality.SMOOTH_720_60

    // Auto-ajuste de fps (#60fps): o webrtc-java so tem encoder por SOFTWARE (sem
    // HW/NVENC — verificado no binario), entao ate 720p60 pode ser CPU demais e o fps
    // despenca. Quando o envio fica 'cpu'-limitado por ~3 leituras seguidas, baixamos
    // 1 degrau de preset (priorizando o framerate). sessionQualityCap = teto SO desta
    // sessão — NAO mexe na pref explicita do usuário (zera quando ele escolhe na mao).
    private var sessionQualityCap: ScreenQuality? = null
    private var cpuStreak = 0

    // Ultima fonte transmitida — pra reiniciar a captura no MESMO monitor quando o
    // dono troca a qualidade ao vivo pelo gear da call.
    private var lastScreenSource: DesktopSource? = null

    fun connect(roomKind: String, roomId: String) {
        VoiceLog.nota("--- entrando em $roomKind:$roomId ---")
        Sfx.callJoin() // entrar na call: som fino/agudo
        connectJob = scope.launch {
            // Escreve num LOCAL, não no campo: se dispose() correu enquanto a lib
            // nativa carregava, o factory recem-criado seria orfao (dispose já
            // rodou com factory==null e não teria o que fechar). Checa disposed
            // antes de adotar; se já fui descartado, fecho o que acabei de criar.
            val f = withContext(Dispatchers.IO) {
                // UnsatisfiedLinkError e Error, não Exception — catch amplo aqui.
                // Factory padrao = ADM de hardware pro PLAYOUT (ouvir os outros toca
                // sozinho no device padrao). A CAPTURA do mic NAO usa esse ADM (ele
                // quebra nesse caminho): vem do MicCapture (Java Sound) via
                // CustomAudioSource. UnsatisfiedLinkError e Error -> catch amplo.
                try {
                    createFactory()
                } catch (t: Throwable) {
                    null
                }
            }
            if (disposed) { runCatching { f?.dispose() }; return@launch }
            if (f == null) {
                VoiceLog.nota("1. webrtc nativo NAO CARREGOU (sem isso nao ha audio nenhum)")
                _status.value = VoiceStatus.Failed("WebRTC nativo não carregou nesta maquina")
                return@launch
            }
            factory = f
            VoiceLog.nota("1. webrtc nativo ok")

            val data = runCatching { voiceApi.token(VoiceTokenRequest(roomKind, roomId)).data }.getOrNull()
            if (disposed) return@launch
            if (data == null) {
                VoiceLog.nota("2. token de voz NEGADO pelo backend (LiveKit configurado? acesso ao canal?)")
                _status.value = VoiceStatus.Failed("Backend não deu o token de voz")
                return@launch
            }

            VoiceLog.nota("2. token ok, servidor de voz = " + data.url)
            val url = data.url.trimEnd('/') + "/rtc?access_token=" + data.token + "&auto_subscribe=1&protocol=15"
            val socket = wsClient.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        val res = runCatching { LivekitRtc.SignalResponse.parseFrom(bytes.toByteArray()) }
                            .getOrNull() ?: return
                        handleSignal(res)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        VoiceLog.nota("3. signaling CAIU: " + (t.message ?: "erro") + " (http " + (response?.code ?: 0) + ")")
                        pingJob?.cancel()
                        _status.value =
                            if (joined) VoiceStatus.Closed
                            else VoiceStatus.Failed("Signaling recusou: ${t.message ?: "erro"}")
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        VoiceLog.nota("3. signaling fechou ($code $reason)")
                        pingJob?.cancel()
                        if (_status.value !is VoiceStatus.Failed) _status.value = VoiceStatus.Closed
                    }
                },
            )
            // Mesma corrida do factory: se dispose() rodou durante o token/handshake,
            // este ws ficaria aberto pra sempre. Fecha em vez de adotar.
            if (disposed) { runCatching { socket.close(1000, "leave") } } else { ws = socket }
        }
    }

    // Factory com ADM explicito: o ADM default não estava tocando o audio remoto
    // nesta maquina (você não ouvia ninguem, mas te ouviam). Escolhe a saida da
    // pref (ou a 1a). Se o caminho do ADM explodir, cai no factory padrao pra pelo
    // menos ENTRAR na sala (mesmo comportamento de antes).
    private fun createFactory(): PeerConnectionFactory {
        val module = runCatching {
            AudioDeviceModule().also { m -> applyPlayoutDevice(m, prefs.state.value.audioOutput) }
        }.getOrNull()
        adm = module
        return runCatching {
            if (module != null) PeerConnectionFactory(module) else PeerConnectionFactory()
        }.getOrElse {
            runCatching { module?.dispose() }
            adm = null
            PeerConnectionFactory()
        }
    }

    // Dispositivos pro seletor da call. Saida = ADM (WebRTC); entrada = Java Sound.
    fun outputDevices(): List<String> = runCatching { adm?.playoutDevices?.map { it.name } }.getOrNull().orEmpty()
    fun inputDevices(): List<String> = AudioDevices.inputs()

    // Aplica a saida escolhida no ADM. name null (ou dispositivo sumido) = NAO
    // mexe: o modulo fica no padrao do sistema.
    //
    // Era aqui o bug de "não ouco ninguem": sem preferencia salva o codigo fazia
    // `?: devs.firstOrNull()` e FORCAVA o primeiro dispositivo da enumeracao —
    // que raramente e o que o Windows usa. Pior: isso quebrava justamente quem
    // nunca abriu as configurações de voz, ou seja, todo mundo por padrao.
    private fun applyPlayoutDevice(m: AudioDeviceModule, name: String?) {
        if (name.isNullOrBlank()) return
        runCatching {
            m.playoutDevices.firstOrNull { it.name == name }?.let { m.setPlayoutDevice(it) }
        }
    }

    // Troca a SAIDA ao vivo (ADM) + persiste. name null = padrao do Windows.
    fun setOutputDevice(name: String?) {
        prefs.setAudioOutput(name)
        val m = adm ?: return
        applyPlayoutDevice(m, name)
    }

    // Troca a ENTRADA ao vivo: persiste + reabre so a captura (mesma track/source).
    fun setInputDevice(name: String?) {
        prefs.setAudioInput(name)
        val src = micSource ?: return
        runCatching { micCapture?.stop() }
        val p = prefs.state.value
        val cap = MicCapture(src, p.micNoiseSuppression, p.micAutoGain, p.micEchoCancel, name, p.micSensitivity) { level -> onMicLevel(level) }
        micCapture = cap
        cap.start()
    }

    private fun handleSignal(res: LivekitRtc.SignalResponse) {
        when (res.messageCase) {
            LivekitRtc.SignalResponse.MessageCase.JOIN -> {
                joined = true
                VoiceLog.nota("4. join aceito. ja estavam na sala: " + res.join.otherParticipantsCount)
                myIdentity = res.join.participant.identity
                mySid = res.join.participant.sid
                others.clear()
                res.join.otherParticipantsList.forEach { others[it.identity] = it.remote() }
                createSubscriber(res.join.iceServersList)
                createPublisher(res.join.iceServersList)
                publishMic()
                startSpeakingPoll()
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
                        remoteAudioReceivers.remove(p.sid)
                        _remoteVideos.value = _remoteVideos.value.filterNot { it.ownerSid == p.sid }
                    } else {
                        // Preserva a fala: UPDATE conta de nome/metadata, não de voz
                        // ativa. Sem isto, qualquer troca de avatar de qualquer um
                        // apagaria o anel de quem estivesse falando naquele instante.
                        val antes = others[p.identity]
                        others[p.identity] = p.remote(antes?.speaking ?: false).also {
                            it.speakUntil = antes?.speakUntil ?: 0
                            it.signalSpeaking = antes?.signalSpeaking ?: false
                        }
                    }
                }
                if (joined) publishConnected()
            }
            LivekitRtc.SignalResponse.MessageCase.LEAVE -> {
                _status.value = VoiceStatus.Closed
                ws?.close(1000, "leave")
            }
            // Quem está falando, pela boca do servidor.
            //
            // ISTO ESTAVA FALTANDO, e era o motivo de o anel de "falando" só acender
            // no proprio usuario: a minha fala vem do RMS do meu mic (local, sempre
            // disponivel), e a dos outros dependia inteiramente do getStats do
            // receiver de audio deles — que so existe se `onAddTrack` tiver
            // registrado o receiver. Quando nao registra, o audio TOCA do mesmo
            // jeito (o ADM nativo cuida disso sozinho, sem passar por nos) e a
            // interface fica cega: da pra ouvir a pessoa e nao da pra ver quem e.
            //
            // O servidor ja calcula isso pra sala inteira e avisa de graca. Nao
            // depende de track registrada, de stats, nem de nome de stream.
            LivekitRtc.SignalResponse.MessageCase.SPEAKERS_CHANGED ->
                onSpeakersChanged(res.speakersChanged.speakersList)
            // connection_quality etc entram na V6 (UI da sala).
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
                // ESTE e o passo que decide se voce OUVE alguem. CONNECTED = a midia
                // achou caminho pela rede; FAILED = firewall/UDP bloqueado (o sintoma
                // e silencio total, sem erro nenhum na tela).
                VoiceLog.nota("6. canal de audio de ENTRADA: " + state)
                audioLive = state == RTCPeerConnectionState.CONNECTED
                if (joined) publishConnected()
            }

            // Track de video remota = transmissão de alguem. O LiveKit nomeia o
            // MediaStream como "participantSid|trackSid" — dai sai o dono.
            override fun onAddTrack(receiver: RTCRtpReceiver, streams: Array<MediaStream>) {
                // Sem stream nomeado não dá pra saber DE QUEM é a track. Antes isso
                // era um `return` mudo, e essa era a pior parte: o audio tocava
                // normalmente (o ADM nativo nao passa por aqui), entao nada parecia
                // errado — so o anel de "quem esta falando" nunca acendia, sem uma
                // linha sequer no diario explicando por que.
                val ownerSid = streams.firstOrNull()?.id()?.substringBefore('|')
                if (ownerSid.isNullOrBlank()) {
                    VoiceLog.nota("7. chegou audio/video SEM dono no nome do stream (streams=${streams.size}) — da pra ouvir, mas nao da pra saber de quem e")
                    return
                }
                when (val track = receiver.track) {
                    is VideoTrack -> {
                        val label = others.values.find { it.sid == ownerSid }?.label ?: "transmissão"
                        _remoteVideos.value = _remoteVideos.value + RemoteVideo(ownerSid, label, track)
                    }
                    // Audio remoto toca sozinho no device padrao; guardamos o receiver so
                    // pra medir o nível de fala (inchada do card de quem fala).
                    is AudioTrack -> {
                        VoiceLog.nota("7. audio de alguem chegou (" + (others.values.find { it.sid == ownerSid }?.label ?: ownerSid) + ")")
                        remoteAudioReceivers[ownerSid] = receiver
                    }
                    else -> Unit
                }
            }

            override fun onRemoveTrack(receiver: RTCRtpReceiver) {
                remoteAudioReceivers.entries.removeIf { it.value == receiver }
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
        // Captura o mic por Java Sound (MicCapture) e empurra o PCM num
        // CustomAudioSource — o Core Audio do webrtc-java quebra a captura anexado ao
        // factory ("Start recording failed"); Java Sound abre o mic por outro caminho
        // e roda em qualquer maquina. O MicCapture passa cada bloco pelo APM do WebRTC
        // (NS + high-pass + AGC, saida 48k mono) conforme as prefs de Voz — sem isso a
        // voz saia "robo com ruido". Sem mic não derruba a sala: segue so ouvindo.
        val source = runCatching { CustomAudioSource() }.getOrNull() ?: run {
            VoiceLog.nota("5. mic: nao consegui criar a fonte de audio")
            return
        }
        val cid = "mic-" + UUID.randomUUID().toString().take(8)
        micCid = cid
        micSource = source
        micTrack = f.createAudioTrack(cid, source)
        val p = prefs.state.value
        val cap = MicCapture(source, p.micNoiseSuppression, p.micAutoGain, p.micEchoCancel, p.audioInput, p.micSensitivity) { level -> onMicLevel(level) }
        micCapture = cap
        // ESTE e o passo que decide se te OUVEM. false = nao abriu o microfone
        // (privacidade do Windows fechada, sem aparelho, ou aparelho ocupado).
        val micOk = cap.start()
        VoiceLog.nota(if (micOk) "5. mic capturando (" + (p.audioInput ?: "padrao do Windows") + ")" else "5. mic NAO ABRIU - ninguem vai te ouvir (privacidade do Windows? aparelho ocupado?)")
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

    // ---- V5: transmissão de tela — 60fps NO MINIMO (requisito do dono) --------

    // Janela escondida (bandeja/minimizada) = ninguem olhando o auto-preview: desliga
    // a conversao/entrega do quadradinho local. A transmissão pros OUTROS não muda —
    // o encoder e outro caminho. So economia de CPU enquanto ninguem ve.
    fun setPreviewEnabled(on: Boolean) {
        ffmpegCap?.previewEnabled = on
        if (!on) _localPreview.value = null
    }

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
        // makeRaster AQUI (thread 'ffmpeg-preview', fora da UI): a UI so desenha o
        // ImageBitmap pronto. Antes a UI fazia o raster por frame e engasgava no
        // conteudo animado. wrap() copia sincronamente antes do buffer ser reusado.
        val cap = ScreenCaptureFfmpeg(ffPath, custom) { argb, w, h ->
            _localPreview.value = ScreenPreview(previewRasters.wrap(argb, w, h), w, h)
        }
        if (!cap.start(outIdx, q.width, q.height, q.fps)) {
            cap.stop()
            runCatching { custom.dispose() }
            return null
        }
        customSource = custom
        ffmpegCap = cap
        _directPreview.value = true
        return custom
    }

    // Fallback GDI (VideoDesktopSource): roda em qualquer maquina, ~20-30fps. fps
    // na captura; resolucao capada no preset (o webrtc-java não expoe
    // degradationPreference, entao não dar ao encoder mais pixels do que segura).
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

    fun startScreenShare(source: DesktopSource? = null, silent: Boolean = false) {
        if (_screenOn.value) return
        val f = factory ?: return
        if (!silent) Sfx.shareStart() // transmitir: 3 fases subindo
        lastScreenSource = source
        // O cap da sessão (auto-ajuste) vence a pref: numa maquina que não aguenta o
        // preset escolhido, republicar voltaria pro preset alto e re-derrubaria o fps.
        screenQ = sessionQualityCap ?: prefs.state.value.screenQuality
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
        _localScreen.value = screenTrack // preview local já com os frames da captura
        _sharingCamera.value = false
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

    // Cameras disponiveis (webcams). Enumeracao nativa; vazio se não houver ou falhar.
    fun cameras(): List<VideoDevice> =
        runCatching { MediaDevices.getVideoCaptureDevices() }.getOrDefault(emptyList())

    // Transmite a CAMERA. Mesma maquinaria da tela (uma fonte por vez), so a fonte
    // muda pra VideoDeviceSource. TrackSource.CAMERA na sinalizacao; o preview local
    // NAO usa o tee ffmpeg (fica null) -> a UI cai no RemoteVideoView, que le a track
    // real (webcam entrega frames ao sink; CustomVideoSource da tela não entregava).
    fun startCameraShare(device: VideoDevice, silent: Boolean = false) {
        if (_screenOn.value) return
        val f = factory ?: return
        if (!silent) Sfx.shareStart()
        // Capacidade: prefere ~720p30 (bom pra rosto sem exagerar banda); senao a 1a.
        val caps = runCatching { MediaDevices.getVideoCaptureCapabilities(device) }.getOrDefault(emptyList())
        val cap = caps.filter { it.width <= 1280 && it.height <= 720 }.maxByOrNull { it.width * it.height + it.frameRate }
            ?: caps.firstOrNull()
        val src = runCatching {
            VideoDeviceSource().apply {
                setVideoCaptureDevice(device)
                cap?.let { setVideoCaptureCapability(it) }
                start()
            }
        }.getOrNull() ?: return
        cameraSource = src

        val w = cap?.width ?: 1280
        val h = cap?.height ?: 720
        val cid = "camera-" + UUID.randomUUID().toString().take(8)
        screenCid = cid
        screenTrack = f.createVideoTrack(cid, src)
        _localScreen.value = screenTrack
        _localPreview.value = null // camera não tem tee ffmpeg -> RemoteVideoView renderiza a track
        _sharingCamera.value = true
        _screenOn.value = true
        val req = LivekitRtc.SignalRequest.newBuilder()
            .setAddTrack(
                LivekitRtc.AddTrackRequest.newBuilder()
                    .setCid(cid)
                    .setName("camera")
                    .setType(LivekitModels.TrackType.VIDEO)
                    .setSource(LivekitModels.TrackSource.CAMERA)
                    .setWidth(w)
                    .setHeight(h)
                    .addLayers(
                        LivekitModels.VideoLayer.newBuilder()
                            .setQuality(LivekitModels.VideoQuality.HIGH)
                            .setWidth(w)
                            .setHeight(h)
                            .setBitrate(2_000_000),
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
        // Reforca os encodings no sender já negociado (o init nem sempre carrega
        // maxBitrate/maxFramerate pela munge do SDP) + comeca a medir os fps.
        scope.launch {
            delay(1200)
            reinforceScreenSender()
            startScreenStats()
        }
    }

    // Re-aplica maxFramerate/maxBitrate direto no sender (via setParameters) — jeito
    // confiavel de garantir que o teto vale, já que init encodings as vezes se perde.
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
    // da degradacao. E como saber SE bateu 60 e, se não, ONDE travou (captura/cpu/banda).
    private fun startScreenStats() {
        statsJob?.cancel()
        cpuStreak = 0
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
        maybeAutoStepDown(limit)
    }

    // 'cpu'-limitado por ~3 leituras (≈4.5s) seguidas => baixa 1 degrau sozinho. So
    // desce, nunca sobe (evita ficar oscilando). O cap vale so nesta sessão.
    private fun maybeAutoStepDown(limit: String) {
        if (limit == "cpu") cpuStreak++ else cpuStreak = 0
        if (cpuStreak < 3) return
        val next = screenQ.stepDownForCpu() ?: run { cpuStreak = 0; return } // já no piso
        cpuStreak = 0
        // Fora da thread do getStats (callback nativo) -> pro escopo do engine.
        scope.launch { autoStepDownTo(next) }
    }

    // So 720p agora: único degrau possível e cair o fps (60 -> 30). Piso = 720p30.
    private fun ScreenQuality.stepDownForCpu(): ScreenQuality? = when (this) {
        ScreenQuality.SMOOTH_720_60 -> ScreenQuality.LIGHT_720_30
        ScreenQuality.LIGHT_720_30  -> null
    }

    // Reinicia a captura no preset menor SEM persistir (respeita a escolha do usuário
    // pra próxima sessão). Espelha o setScreenQuality manual, mas via sessionQualityCap.
    private suspend fun autoStepDownTo(q: ScreenQuality) {
        sessionQualityCap = q
        if (!_screenOn.value) return
        val src = lastScreenSource
        stopScreenShare(silent = true)
        delay(350) // deixa a renegociacao do stop assentar antes de republicar
        startScreenShare(src, silent = true)
    }

    // Fala: a MINHA vem do RMS do mic (onMicLevel -> mySpeakUntil); a dos OUTROS vem
    // do getStats do audio remoto (INBOUND_RTP audioLevel). Independe do
    // speakers_changed do servidor. Hangover segura o "falando" ~400ms apos cair
    // (anti-flicker) — a inchada do card reage a isso.
    private fun startSpeakingPoll() {
        speakingJob?.cancel()
        speakingJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (!_micOn.value) mySpeakUntil = 0L // mudo => não "fala"
                sub?.let { pc ->
                    remoteAudioReceivers.entries.toList().forEach { (sid, recv) ->
                        runCatching { pc.getStats(recv) { r -> if (audioLevelOf(r) > SPEAK_THRESHOLD) markRemoteSpeak(sid) } }
                    }
                }
                // Aplica (com base no que já voltou dos ciclos anteriores).
                var changed = false
                val meSpeak = now < mySpeakUntil
                if (meSpeak != mySpeaking) { mySpeaking = meSpeak; changed = true }
                runCatching {
                    others.values.toList().forEach { r ->
                        // Servidor OU nivel medido. Basta um dos dois: o nivel e mais
                        // rapido a reagir, o servidor funciona mesmo sem receiver
                        // registrado — e o segundo sozinho ja acende o anel.
                        val sp = r.signalSpeaking || now < r.speakUntil
                        if (r.speaking != sp) { r.speaking = sp; changed = true }
                    }
                }
                if (changed && joined) publishConnected()
                delay(SPEAK_POLL_MS)
            }
        }
    }

    // speakers_changed e um DELTA: vem quem mudou de estado, com active=false pra
    // quem parou. Quem nao aparece na lista fica como estava.
    //
    // A minha propria linha e ignorada de proposito: o servidor so sabe da minha
    // voz depois que ela sobe, comprime e volta — uns 200ms de atraso num anel
    // que reage ao meu proprio rosto. O RMS do meu mic e instantaneo e ja resolve.
    private fun onSpeakersChanged(speakers: List<LivekitModels.SpeakerInfo>) {
        var changed = false
        speakers.forEach { s ->
            if (s.sid == mySid) return@forEach
            val r = others.values.find { it.sid == s.sid } ?: return@forEach
            if (r.signalSpeaking != s.active) { r.signalSpeaking = s.active; changed = true }
        }
        if (changed && joined) publishConnected()
    }

    private fun markRemoteSpeak(sid: String) {
        runCatching { others.values.find { it.sid == sid }?.speakUntil = System.currentTimeMillis() + SPEAK_HANGOVER_MS }
    }

    // RMS do meu mic (0..1) por bloco de 10ms (thread do MicCapture) -> fala.
    private fun onMicLevel(level: Float) {
        if (_micOn.value && level > SPEAK_THRESHOLD) {
            mySpeakUntil = System.currentTimeMillis() + SPEAK_HANGOVER_MS
        }
    }

    // audioLevel (0..1) de qualquer stat que reporte — usado pro nível dos remotos.
    private fun audioLevelOf(report: RTCStatsReport): Double {
        var lvl = 0.0
        report.stats.values.forEach { s ->
            (s.attributes["audioLevel"] as? Number)?.let { lvl = maxOf(lvl, it.toDouble()) }
        }
        return lvl
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

    fun stopScreenShare(silent: Boolean = false) {
        if (!_screenOn.value) return
        if (!silent) Sfx.shareStop() // parar transmissão: 3 fases descendo (invertido)
        _screenOn.value = false
        _sharingCamera.value = false
        _localScreen.value = null
        _localPreview.value = null
        _directPreview.value = false
        previewRasters.dispose()
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
        // Camera (quando a fonte era webcam): para e libera a fonte nativa.
        runCatching { cameraSource?.stop() }
        runCatching { cameraSource?.dispose() }
        cameraSource = null
        screenTrack = null
        screenSource = null
        screenCid = null
        // m-line desativada na renegociacao => o server despublica a track.
        negotiatePublisher()
    }

    // Troca a qualidade/fluidez da transmissão ao vivo (gear da call): persiste a
    // pref e, se já estou transmitindo, reinicia a captura no MESMO monitor com o
    // novo preset (o ffmpeg e spawnado com w/h/fps assados -> so reiniciando muda).
    fun setScreenQuality(q: ScreenQuality) {
        sessionQualityCap = null // escolha manual = usuário no controle; limpa o auto-cap
        prefs.setScreenQuality(q)
        if (!_screenOn.value) return
        val src = lastScreenSource
        stopScreenShare(silent = true)
        scope.launch {
            delay(350) // deixa a renegociacao do stop assentar antes de republicar
            startScreenShare(src, silent = true)
        }
    }

    // Mute local (track para de mandar frames) + aviso pro server (ícone de mute
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
            others.map { (identity, r) -> VoiceParticipant(identity, r.label, r.speaking, r.avatarUrl) },
            audioLive,
            mySpeaking,
        )
    }

    private fun LivekitModels.ParticipantInfo.remote(speaking: Boolean = false) =
        Remote(sid, name.ifBlank { identity }, avatarFromMetadata(metadata), speaking)

    // O backend embute {avatarUrl, username} no metadata do token. Le a foto pra
    // exibir no palco da call sem depender da lista de membros (funciona em DM tb).
    private fun avatarFromMetadata(metadata: String?): String? {
        if (metadata.isNullOrBlank()) return null
        return runCatching {
            Json.parseToJsonElement(metadata).jsonObject["avatarUrl"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    fun dispose() {
        disposed = true
        connectJob?.cancel()
        Sfx.callLeave() // sair da call: som grosso/grave
        pingJob?.cancel()
        statsJob?.cancel()
        speakingJob?.cancel()
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
        remoteAudioReceivers.clear()
        _localScreen.value = null
        _localPreview.value = null
        ffmpegCap?.stop()
        ffmpegCap = null
        runCatching { customSource?.dispose() }
        customSource = null
        runCatching { screenSource?.stop() }
        runCatching { screenTrack?.dispose() }
        runCatching { screenSource?.dispose() }
        runCatching { cameraSource?.stop() }
        runCatching { cameraSource?.dispose() }
        cameraSource = null
        screenTrack = null
        screenSource = null
        runCatching { micCapture?.stop() }
        micCapture = null
        runCatching { micTrack?.dispose() }
        micTrack = null
        runCatching { micSource?.dispose() }
        micSource = null
        runCatching { pub?.close() }
        pub = null
        runCatching { sub?.close() }
        sub = null
        runCatching { factory?.dispose() }
        factory = null
        // O factory e dono do ADM (foi passado no construtor) — não dispomos de novo
        // pra não arriscar double-free nativo; so soltamos a referencia.
        adm = null
    }
}
