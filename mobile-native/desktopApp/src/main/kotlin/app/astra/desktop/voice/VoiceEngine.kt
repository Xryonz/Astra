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
import dev.onvoid.webrtc.media.audio.AudioTrackSink
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

// Teto do Opus, em bits por segundo. 64k mono e o patamar de "voz presente" que
// os apps de call usam hoje; o padrao do WebRTC (~32k) foi calibrado pra rede
// muito pior que a de um PC com banda. Como e TETO, rede ruim desce sozinha.
private const val OPUS_BITRATE = 64_000

// Atraso estimado entre o que sai na caixa e o que volta pelo mic. O AEC3 tem
// estimador proprio e ajusta sozinho; este valor so acelera a convergencia no
// comeco da call.
private const val AEC_DELAY_MS = 60

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

    // O servidor ja avisa quem esta falando? Enquanto nao souber, medimos por
    // conta propria (caro). Volta a false a cada nova sala: e propriedade do
    // servidor daquela conexao, nao do app.
    @Volatile private var servidorAvisaFala = false

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

    // Faixas de audio remoto, pra alimentar o cancelador de eco (ver `reavaliarAec`).
    private val remoteAudioTracks = linkedMapOf<String, AudioTrack>()
    private var aecTrack: AudioTrack? = null
    private var aecSink: AudioTrackSink? = null
    private var aecSid: String? = null

    private var micSender: RTCRtpSender? = null
    private var entradaJob: Job? = null

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

    // Transporte de saida pelo GStreamer. `null` = esta call vai pelo caminho de sempre.
    //
    // A ESCOLHA E POR CALL E NAO MUDA NO MEIO, porque o LiveKit da uma conexao de
    // publicacao so: trocar de transporte com a call no ar seria derrubar e refazer a
    // conexao inteira, com a voz de alguem em cima. Quem nao tiver o pacote ou encoder
    // de hardware simplesmente segue pelo caminho de hoje, sem aviso e sem perda.
    private var gstPub: GstPublisher? = null

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

    // Quando a call comecou (millis). Null ate o primeiro CONNECTED. Quem mostra o
    // cronometro conta a partir daqui — assim o relogio nasce no servidor de voz e
    // nao no instante em que uma tela qualquer entrou na composicao.
    private val _inicio = MutableStateFlow<Long?>(null)
    val inicio = _inicio.asStateFlow()
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
    private var streakCpu = 0
    private var streakBanda = 0
    private var streakLimpo = 0

    // Preset pro qual o app desceu SOZINHO nesta transmissao, ou null se nao desceu.
    //
    // Existe pra a queda ser dita em voz alta. Baixar a qualidade em silencio faz a
    // pessoa ver a propria transmissao piorar sem motivo aparente — e a conclusao
    // dela nao vai ser "meu PC nao aguentou", vai ser "o Astra e ruim".
    private val _quedaAutomatica = MutableStateFlow<ScreenQuality?>(null)
    val quedaAutomatica = _quedaAutomatica.asStateFlow()

    // Ultima enumeracao de telas. Guardada porque a chamada nativa e cara (constroi
    // um ScreenCapturer, varre os monitores e descarta) e o caminho de transmitir a
    // fazia DUAS vezes: uma pro seletor montar a lista, outra pro startFastCapture
    // descobrir o indice do monitor escolhido — que e posicao NESTA mesma lista.
    private var telasCache: List<DesktopSource> = emptyList()

    // Ultima fonte transmitida — pra reiniciar a captura no MESMO monitor quando o
    // dono troca a qualidade ao vivo pelo gear da call.
    private var lastScreenSource: DesktopSource? = null

    // Tamanho que a webcam entrega, guardado pro motor novo montar o cano com ele.
    private var tamanhoDaCamera: Pair<Int, Int>? = null

    // QUAL webcam, pelo nome. O motor novo abre a camera pelo `mfvideosrc`, que sem
    // endereco pega a primeira que a maquina listar -- quem tem duas escolheria uma na
    // interface e transmitiria a outra.
    private var nomeDaCamera: String? = null

    // A primeira medida da transmissao ja foi pro diario? (uma por transmissao)
    private var medidaAnotada = false

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
            anotarSaida()

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

    // QUAL caixa de som o WebRTC vai usar. Quando o passo 9 disser que os pacotes
    // CHEGAM e mesmo assim ninguem ouvir nada, a resposta esta nesta linha: o som
    // esta saindo, so que num aparelho que ninguem esta escutando. Lista vazia aqui
    // ja e veredito — sem aparelho de saida visivel, nenhuma call vai ter audio.
    private fun anotarSaida() {
        val m = adm ?: run {
            VoiceLog.nota("1b. saida de audio: modulo proprio nao subiu — no padrao do webrtc")
            return
        }
        val nomes = runCatching { m.playoutDevices.map { it.name } }.getOrNull().orEmpty()
        val salvo = prefs.state.value.audioOutput
        VoiceLog.nota(
            if (nomes.isEmpty()) "1b. saida de audio: NENHUM aparelho visivel — nao ha como ouvir ninguem"
            else "1b. saida de audio EM USO: " + (saidaEmUso ?: "padrao do webrtc") +
                (if (!salvo.isNullOrBlank() && salvo != saidaEmUso) " [salvo \"" + salvo + "\" NAO foi aplicado]" else "") +
                " (de " + nomes.size + ": " + nomes.joinToString(", ") + ")",
        )
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
    // O aparelho que REALMENTE entrou no ADM — que nao e a mesma coisa que a
    // preferencia salva. Se o aparelho salvo sumiu da enumeracao (fone tirado da
    // tomada, driver reenumerado, aparelho desativado), a preferencia continua no
    // disco e o som vai pro padrao do WebRTC, que pode ser outro aparelho. Guardar
    // os dois separados e o que deixa o log dizer o que ESTA acontecendo em vez de
    // repetir o que a gente queria que acontecesse.
    private var saidaEmUso: String? = null

    private fun applyPlayoutDevice(m: AudioDeviceModule, name: String?) {
        saidaEmUso = null
        if (name.isNullOrBlank()) return
        runCatching {
            val achado = m.playoutDevices.firstOrNull { it.name == name }
            if (achado == null) {
                // Grita. Este caso era silencioso, e silencio aqui vira "o audio
                // chega e ninguem ouve" — que le exatamente como um bug de rede.
                VoiceLog.nota(
                    "1b! aparelho de saida salvo (\"" + name + "\") NAO esta na lista deste momento — " +
                        "o som vai para o padrao do WebRTC, que pode ser outro aparelho",
                )
                return@runCatching
            }
            m.setPlayoutDevice(achado)
            saidaEmUso = achado.name
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
        val src = micSource
        // No motor novo NAO HA `micSource` — o PCM vai direto pro cano. Sair aqui por
        // `?: return` faria a troca de microfone no meio da call virar um botao que nao
        // faz nada, e so pra quem estivesse no caminho novo.
        if (src == null && gstPub == null) return
        runCatching { micCapture?.stop() }
        val p = prefs.state.value
        val cap = MicCapture(destinoDoMic(src), p.micNoiseSuppression, p.micAutoGain, p.micEchoCancel, name, p.micSensitivity) { level -> onMicLevel(level) }
        micCapture = cap
        cap.start()
    }

    // Para onde o PCM do microfone vai.
    //
    // Enquanto o transporte novo nao estiver ligado numa call, isto devolve sempre a
    // fonte de hoje — a decisao mora aqui pra a troca ser UMA linha, e nao uma caçada
    // pelos dois pontos que constroem MicCapture (um deles e a troca de aparelho ao
    // vivo, que e fácil de esquecer e so quebra pra quem troca de microfone no meio).
    private fun destinoDoMic(fonte: CustomAudioSource?): DestinoDeAudio {
        val gst = gstPub
        if (gst != null) {
            return DestinoDeAudio { pcm, bits, taxa, canais, quadros ->
                gst.empurrarAudio(pcm, bits, taxa, canais, quadros)
            }
        }
        return DestinoDeAudio { pcm, bits, taxa, canais, quadros ->
            fonte?.pushAudio(pcm, bits, taxa, canais, quadros)
        }
    }

    // Tenta subir o motor novo. `false` = siga pelo caminho de sempre, sem drama.
    //
    // Tres portoes, e a ordem importa: a pessoa tem que ter PEDIDO (a chave nas
    // configuracoes), o pacote tem que estar em disco, e a maquina tem que ter encoder
    // de hardware. Faltando qualquer um, nada acontece e ninguem fica sabendo -- e o
    // mesmo contrato do ddagrab caindo pro GDI.
    private fun subirMotorNovo(iceServers: List<LivekitRtc.ICEServer>): Boolean {
        if (!prefs.state.value.motorNovo) return false
        val cid = "mic-" + UUID.randomUUID().toString().take(8)
        // O LiveKit manda os servidores como `stun:host:porta`; o GStreamer quer
        // `stun://host:porta`. Sem TURN por ora: se a rede exigir TURN, o motor novo nao
        // conecta e o certo e a pessoa voltar a chave -- por isso ela existe.
        val stun = iceServers.asSequence()
            .flatMap { it.urlsList.asSequence() }
            .firstOrNull { it.startsWith("stun:") }
            ?.replaceFirst("stun:", "stun://")
        val p = GstPublisher(
            onOferta = { sdp -> enviarOferta(sdp) },
            onCandidato = { mlinha, cand -> enviarTrickleDoMotorNovo(mlinha, cand) },
            onPrevia = { rgba, largura, altura ->
                _localPreview.value = ScreenPreview(previewRasters.wrap(rgba, largura, altura), largura, altura)
            },
        )
        if (!p.iniciar(cid, stun)) return false
        gstPub = p
        micCid = cid
        VoiceLog.nota("4b. motor novo no ar (video direto da placa)")
        acompanharConexao(p)
        return true
    }

    // Anota no log de voz a conexao do motor novo chegando -- ou nao chegando.
    //
    // Existe por causa de como o motor novo falhou da primeira vez: o log parava na linha
    // do join e o processo sumia. Sem uma linha entre "entrei na sala" e "estou
    // transmitindo", nao dava pra saber se a conexao tinha sequer sido tentada — e essa
    // duvida custou horas. Trinta segundos cobrem a subida inteira; depois disso o estado
    // so muda se a rede cair, e ai quem conta e a reconexao.
    private fun acompanharConexao(p: GstPublisher) {
        scope.launch {
            var anterior = ""
            repeat(30) {
                if (!p.vivo) return@launch
                val agora = p.estadoDaConexao()
                if (agora != anterior) {
                    VoiceLog.nota("4c. conexao do motor novo: $agora")
                    anterior = agora
                }
                if (agora == "connected" || agora == "failed" || agora == "closed") return@launch
                delay(1000)
            }
            VoiceLog.nota("4c. o motor novo NAO conectou em 30s (parou em \"$anterior\") — se ninguem te ouve, desligue a chave em Configuracoes > Voz")
        }
    }

    private fun enviarOferta(sdp: String) {
        val req = LivekitRtc.SignalRequest.newBuilder()
            .setOffer(LivekitRtc.SessionDescription.newBuilder().setType("offer").setSdp(sdp))
            .build()
        ws?.send(req.toByteArray().toByteString())
    }

    private fun enviarTrickleDoMotorNovo(mlinha: Int, candidato: String) {
        val init = buildJsonObject {
            put("candidate", candidato)
            put("sdpMLineIndex", mlinha)
        }
        val req = LivekitRtc.SignalRequest.newBuilder()
            .setTrickle(
                LivekitRtc.TrickleRequest.newBuilder()
                    .setCandidateInit(init.toString())
                    .setTarget(LivekitRtc.SignalTarget.PUBLISHER),
            )
            .build()
        ws?.send(req.toByteArray().toByteString())
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
                // A ESCOLHA DO TRANSPORTE ACONTECE AQUI E VALE A CALL INTEIRA. O
                // LiveKit da uma conexao de publicacao so; trocar no meio seria derrubar
                // e refazer tudo com a voz de alguem em cima.
                if (!subirMotorNovo(res.join.iceServersList)) createPublisher(res.join.iceServersList)
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
                        // Saiu gente: pode ser que agora sobre so um do outro lado,
                        // e ai o cancelamento de eco volta a ser possivel.
                        remoteAudioTracks.remove(p.sid)
                        reavaliarAec()
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
                if (state == RTCPeerConnectionState.CONNECTED) conferirEntradaDeAudio()
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
                    VoiceLog.nota("7. chegou audio/video SEM dono no nome do stream (streams=${streams.size}) — da para ouvir, mas nao da para saber de quem e")
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
                        remoteAudioTracks[ownerSid] = track
                        reavaliarAec()
                    }
                    else -> Unit
                }
            }

            override fun onRemoveTrack(receiver: RTCRtpReceiver) {
                remoteAudioReceivers.entries.removeIf { it.value == receiver }
                runCatching { receiver.track }.getOrNull()?.let { t ->
                    remoteAudioTracks.entries.removeIf { it.value == t }
                }
                reavaliarAec()
                val gone = runCatching { receiver.track?.id }.getOrNull() ?: return
                _remoteVideos.value = _remoteVideos.value.filterNot { v ->
                    runCatching { v.track.id == gone }.getOrDefault(true)
                }
            }
        })
    }

    // Server manda o offer (subscriber-primary); renegociacoes (alguem publicou
    // track nova) chegam pelo mesmo caminho — a cadeia inteira se repete.
    // ESTE e o caminho pelo qual o audio dos outros chega ate voce: toda vez que
    // alguem publica, o servidor manda uma proposta nova e a gente responde. Se
    // qualquer um dos tres passos falhar, a faixa nunca e entregue.
    //
    // Os quatro `onFailure` daqui eram `= Unit`. Quatro maneiras de a call ficar
    // muda sem deixar um unico rastro — e "ninguem escuta ninguem" e exatamente o
    // que se ve de fora quando isso acontece. Agora cada uma fala.
    private fun onServerOffer(sdp: String) {
        val pc = sub ?: return
        // Quantas faixas de audio o servidor esta oferecendo. Zero aqui ja mata a
        // duvida: o problema seria do lado de LA (ninguem publicou, ou nao fomos
        // inscritos), nao no nosso tratamento da proposta.
        val faixasDeAudio = sdp.lineSequence().count { it.startsWith("m=audio") }
        VoiceLog.nota("7a. proposta do servidor recebida ($faixasDeAudio faixa(s) de audio)")
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
                                        override fun onFailure(error: String) =
                                            VoiceLog.nota("7a. FALHOU ao aplicar a propria resposta: $error — o audio dos outros nao vai chegar")
                                    },
                                )
                            }
                            override fun onFailure(error: String) =
                                VoiceLog.nota("7a. FALHOU ao montar a resposta: $error — o audio dos outros nao vai chegar")
                        },
                    )
                }
                override fun onFailure(error: String) =
                    VoiceLog.nota("7a. FALHOU ao aceitar a proposta do servidor: $error — o audio dos outros nao vai chegar")
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
            // O motor novo nao precisa da fila de espera: o webrtcbin guarda sozinho os
            // candidatos que chegam antes da resposta assentar.
            gstPub?.let {
                it.candidatoRemoto(init["sdpMLineIndex"]?.jsonPrimitive?.int ?: 0, candidate.sdp)
                return
            }
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
        // No motor novo o PCM vai pro cano do GStreamer: nao ha fonte nem faixa do
        // webrtc-java pra criar. O `cid` ja foi escolhido ao subir o motor, porque o cano
        // precisa dele pra nomear a faixa antes de qualquer oferta.
        val gst = gstPub
        val source = if (gst != null) null else runCatching { CustomAudioSource() }.getOrNull() ?: run {
            VoiceLog.nota("5. mic: nao consegui criar a fonte de audio")
            return
        }
        val cid = micCid ?: ("mic-" + UUID.randomUUID().toString().take(8))
        micCid = cid
        micSource = source
        if (source != null) micTrack = f.createAudioTrack(cid, source)
        val p = prefs.state.value
        val cap = MicCapture(destinoDoMic(source), p.micNoiseSuppression, p.micAutoGain, p.micEchoCancel, p.audioInput, p.micSensitivity) { level -> onMicLevel(level) }
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
        // No motor novo o microfone ja esta no cano desde o `iniciar`: o servidor
        // aceitou a publicacao, entao so falta descrever a sessao.
        gstPub?.let { it.negociar(); return }
        val track = micTrack ?: return
        val init = RTCRtpTransceiverInit().apply {
            direction = RTCRtpTransceiverDirection.SEND_ONLY
            streamIds = listOf(cid)
        }
        val tr = runCatching { pub?.addTransceiver(track, init) }.getOrNull() ?: return
        micSender = tr.sender
        negotiatePublisher()
        // Mesmo caminho do video: o teto so cola depois da negociacao assentar.
        scope.launch {
            delay(1200)
            reforcarMic()
        }
    }

    // Teto de bitrate do Opus.
    //
    // O mic subia SEM parametro nenhum, entao valia o padrao do WebRTC pra voz —
    // por volta de 32kbps. Nao e "quebrado", e economico: foi calibrado pra rede
    // de celular de uma decada atras. Em call de PC com banda sobrando, dobrar
    // esse teto e a diferenca entre voz que da pra entender e voz que soa presente.
    //
    // TETO, nao piso: se a rede apertar, o WebRTC desce sozinho. Nao ha risco de
    // insistir em bitrate alto numa conexao ruim.
    private fun reforcarMic() {
        val sender = micSender ?: return
        runCatching {
            val params = sender.parameters ?: return
            params.encodings?.firstOrNull()?.apply {
                active = true
                maxBitrate = OPUS_BITRATE
            }
            sender.parameters = params
        }
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
        gstPub?.let { it.aplicarResposta(sdp); return }
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
    //
    // CARA e BLOQUEANTE — chamar sempre fora da thread da UI.
    fun screens(): List<DesktopSource> {
        val cap = runCatching { ScreenCapturer() }.getOrNull() ?: return telasCache
        return try {
            cap.desktopSources.also { telasCache = it }
        } finally {
            runCatching { cap.dispose() }
        }
    }

    // Caminho rapido (ffmpeg DXGI). Retorna a fonte se os frames COMECARAM a fluir;
    // null = indisponivel/falhou nesta maquina (o chamador tenta o GDI).
    private fun startFastCapture(source: DesktopSource?, q: ScreenQuality): VideoTrackSource? {
        if (factory == null) return null
        val ffPath = FfmpegLocator.path ?: return null
        // O `output_idx` do ddagrab e a POSICAO do monitor na enumeracao. Reusar a
        // lista que o seletor acabou de montar e mais correto que enumerar de novo:
        // e dela que o `source` saiu. Enumera so se nao houver lista nenhuma (ex.:
        // republicacao automatica logo apos abrir o app).
        val lista = telasCache.ifEmpty { screens() }
        val outIdx = source?.let { s -> lista.indexOfFirst { it.id == s.id } }?.coerceAtLeast(0) ?: 0
        val custom = CustomVideoSource()
        // makeRaster AQUI (thread 'ffmpeg-preview', fora da UI): a UI so desenha o
        // ImageBitmap pronto. Antes a UI fazia o raster por frame e engasgava no
        // conteudo animado. wrap() copia sincronamente antes do buffer ser reusado.
        val cap = ScreenCaptureFfmpeg(ffPath, custom) { argb, w, h ->
            _localPreview.value = ScreenPreview(previewRasters.wrap(argb, w, h), w, h)
        }
        if (!cap.start(outIdx, q.width, q.height, q.fps)) {
            // So descarta a fonte se a thread de captura morreu (ver ScreenCaptureFfmpeg.stop).
            if (cap.stop()) runCatching { custom.dispose() }
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
        // No motor novo a captura NAO acontece aqui: o cano so sobe quando o servidor
        // aceitar a publicacao (attachScreen), porque e la que o `cid` vira o nome da
        // faixa. Aqui so se anuncia a intencao.
        val gst = gstPub
        if (gst == null) {
            val trackSource: VideoTrackSource? = startFastCapture(source, q) ?: startGdiCapture(source, q)
            if (trackSource == null) return
            val cidAntigo = "screen-" + UUID.randomUUID().toString().take(8)
            screenCid = cidAntigo
            screenTrack = f.createVideoTrack(cidAntigo, trackSource)
            _localScreen.value = screenTrack // preview local já com os frames da captura
        } else {
            screenCid = "screen-" + UUID.randomUUID().toString().take(8)
            // A previa vem do appsink do cano; a UI escolhe esse caminho por aqui.
            _directPreview.value = true
            // A INTENCAO FICA REGISTRADA, e nao so o resultado.
            //
            // Quando a transmissao nao apareceu pra ninguem, o log nao tinha UMA linha
            // entre entrar na call e sair dela -- e "nao aconteceu nada" era compativel
            // com tres historias diferentes: o pedido nao saiu, o servidor nao respondeu,
            // ou o cano subiu e nao mandou nada. Sem distinguir, so restava adivinhar.
            VoiceLog.nota("8a. pedi para transmitir (motor novo, ${q.label}) — esperando o servidor aceitar")
        }
        val cid = screenCid ?: return
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
        val w = cap?.width ?: 1280
        val h = cap?.height ?: 720
        val cid = "camera-" + UUID.randomUUID().toString().take(8)

        // No motor novo a camera tambem sobe pelo cano do GStreamer: MISTURAR OS DOIS
        // TRANSPORTES NAO E OPCAO, porque o LiveKit da uma conexao de publicacao so.
        // Abrir a webcam aqui pelo webrtc-java e publicar pelo GStreamer daria uma faixa
        // anunciada que nunca receberia quadro -- camera "ligada" e imagem preta.
        val gst = gstPub
        if (gst == null) {
            val src = runCatching {
                VideoDeviceSource().apply {
                    setVideoCaptureDevice(device)
                    cap?.let { setVideoCaptureCapability(it) }
                    start()
                }
            }.getOrNull() ?: return
            cameraSource = src
            screenTrack = f.createVideoTrack(cid, src)
            _localScreen.value = screenTrack
            _localPreview.value = null // camera não tem tee ffmpeg -> RemoteVideoView renderiza a track
        } else {
            // A previa da camera vem do mesmo appsink da tela.
            _directPreview.value = true
            // O tamanho da CAMERA, e nao o preset da tela: o `screenQ` fala de
            // transmissao de tela e nao tem relacao com o que a webcam entrega.
            tamanhoDaCamera = w to h
            nomeDaCamera = runCatching { device.name }.getOrNull()
        }
        screenCid = cid
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
        gstPub?.let { gst ->
            // A POSICAO do monitor na lista e o que o capturador entende, e e a mesma
            // lista que o seletor montou -- reusar evita enumerar de novo (caro).
            val lista = telasCache.ifEmpty { screens() }
            val indice = lastScreenSource?.let { s -> lista.indexOfFirst { it.id == s.id } }?.coerceAtLeast(0) ?: 0
            val ok = if (_sharingCamera.value) {
                val (lc, ac) = tamanhoDaCamera ?: (1280 to 720)
                gst.publicarCamera(cid, nomeDaCamera, lc, ac)
            } else {
                gst.publicarTela(cid, indice, screenQ)
            }
            if (!ok) {
                VoiceLog.nota("8b! o servidor aceitou, mas o cano de video nao subiu — encerrando a transmissao")
                stopScreenShare(silent = true)
                return
            }
            VoiceLog.nota("8b. o servidor aceitou e o cano de video subiu (monitor $indice) — renegociando")
            gst.negociar()
            scope.launch { delay(1200); startScreenStats() }
            return
        }
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
        medidaAnotada = false
        streakCpu = 0
        streakBanda = 0
        streakLimpo = 0
        statsJob = scope.launch {
            while (isActive && _screenOn.value) {
                val gst = gstPub
                if (gst != null) {
                    // NO MOTOR NOVO A ESCADA DE PRESET NAO CORRE, e isso e desenho, nao
                    // esquecimento. Ela existia porque o encoder era SOFTWARE e nao dava
                    // conta: derrubar o preset era a unica forma de aliviar o processador.
                    // Com o encoder da placa (0,07 nucleo contra 0,84) essa pressao
                    // acabou, e a falta de banda passa a ser tratada pelo rtpgccbwe, que
                    // baixa o BITRATE continuamente -- perder nitidez sem perder quadro e
                    // melhor que pular degraus de resolucao.
                    // Os DOIS numeros vem medidos do cano agora. Antes a captura era o
                    // `screenQ.fps` — o numero do preset repetido de volta, que apareceria
                    // como "captura 60fps" mesmo com o cano inteiro parado. Foi o que
                    // escondeu, por uma versao inteira, que o encoder e que estava mudo.
                    val e = withContext(Dispatchers.IO) { gst.estatisticas() }
                    if (e != null) {
                        _screenStats.value = ScreenStats(e.fpsCaptura, e.fpsEnvio, e.limite)
                        // A PRIMEIRA MEDIDA VAI PRO DIARIO, uma vez por transmissao.
                        //
                        // Sem isto, "a transmissao nao aparece" chega como frase e nao como
                        // numero, e as duas historias possiveis pedem consertos em lugares
                        // opostos: se a captura anda e o comprimido nao, o problema e local;
                        // se os dois andam, o quadro esta saindo e quem nao recebe e o outro
                        // lado. O arquivo o dono manda inteiro; a tela de status, nao.
                        if (!medidaAnotada) {
                            medidaAnotada = true
                            VoiceLog.nota("8c. transmissao medida: captura ${e.fpsCaptura} fps, comprimido ${e.fpsEnvio} fps")
                        }
                    }
                } else {
                    val pc = pub
                    val sender = screenSender
                    if (pc != null && sender != null) {
                        runCatching { pc.getStats(sender) { report -> parseScreenStats(report) } }
                    }
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
        maybeAutoStepDown(limit, send)
    }

    // Limitado por ~3 leituras (≈4.5s) seguidas => baixa 1 degrau sozinho. So desce,
    // nunca sobe (evita ficar oscilando). O cap vale so nesta sessão.
    //
    // 'bandwidth' conta junto com 'cpu', e nao contava. Era um buraco: numa conexao de
    // subida fraca o WebRTC reporta 'bandwidth', o contador zerava a cada leitura e o
    // app NUNCA descia — ficava eternamente tentando mandar 4Mbps por um cano que nao
    // comporta. Como cada degrau da escada baixa pixels E bitrate ao mesmo tempo, a
    // mesma descida serve pros dois casos.
    private fun maybeAutoStepDown(limit: String, send: Int) {
        // O fps de envio decide se a limitacao esta DOENDO ou sendo absorvida.
        //
        // Isto muda o significado de 'bandwidth'. O WebRTC ja trata falta de banda
        // sozinho, baixando o bitrate — quando ele consegue absorver, o fps continua
        // no alvo e a imagem so fica um pouco menos nitida. Descer o preset nesse caso
        // nao devolve banda nenhuma: so tira qualidade de quem ja estava bem. So conta
        // como problema quando o fps DESABA junto.
        val alvo = screenQ.fps
        val fpsSaudavel = send >= alvo * 6 / 10

        when {
            limit == "cpu" -> { streakCpu++; streakBanda = 0; streakLimpo = 0 }
            limit == "bandwidth" && !fpsSaudavel -> { streakBanda++; streakCpu = 0; streakLimpo = 0 }
            (limit == "none" || limit.isBlank()) && fpsSaudavel -> { streakLimpo++; streakCpu = 0; streakBanda = 0 }
            else -> { streakCpu = 0; streakBanda = 0; streakLimpo = 0 }
        }

        // VOLTAR A SUBIR — o que faltava, e o que o dono pediu ("que nem o Discord").
        //
        // O ajuste so descia, e estava escrito no codigo que era de proposito: "so
        // desce, nunca sobe (evita ficar oscilando)". O efeito real era pior que
        // oscilar — um engasgo de dez segundos no comeco da call rebaixava a
        // transmissao pelo resto dela, mesmo com a rede tendo voltado ao normal em
        // seguida. A pessoa passava uma hora em 720p30 por causa de um instante ruim.
        //
        // A histerese e ASSIMETRICA de proposito: cair exige 3 leituras (~4,5s), voltar
        // exige 20 (~30s) TODAS limpas. Piorar rapido e melhorar devagar e o que impede
        // o vaivem — e errar pro lado de descer custa nitidez, errar pro lado de subir
        // custa a transmissao engasgar de novo.
        if (streakLimpo >= 20) {
            streakLimpo = 0
            val teto = prefs.state.value.screenQuality
            val acima = screenQ.stepUp()
            if (sessionQualityCap != null && acima != null && acima.fps <= teto.fps && acima.width <= teto.width) {
                scope.launch { autoStepUpTo(acima, teto) }
            }
            return
        }
        // DOIS CONTADORES, e a banda precisa de MUITO mais paciencia que a CPU.
        //
        // Isto e conserto de uma regressao minha, e a regressao ensina o porque: eu
        // tinha juntado 'bandwidth' com 'cpu' no mesmo contador e ainda criado uma
        // regra de "colapso" que descia DOIS degraus com UMA leitura quando o envio
        // estava abaixo de um terco do capturado. Numa maquina otima (RTX 4060, 16
        // threads) a transmissao caiu pra 540p30 em segundos.
        //
        // A razao: os primeiros segundos de qualquer transmissao TEM essa cara. O
        // estimador de banda do WebRTC comeca baixo de proposito e sobe testando a
        // rede — durante a subida ele reporta 'bandwidth' e o envio fica bem abaixo da
        // captura. Isso nao e a maquina desistindo, e a rede sendo medida. A regra de
        // colapso lia esse momento como catastrofe e punia quem nao tinha problema
        // nenhum.
        //
        // Agora: CPU derruba em 3 leituras (~4,5s) porque falta de processador e
        // imediata e nao melhora sozinha; BANDA precisa de 8 (~12s), tempo de sobra
        // pro estimador terminar de subir. E a queda voltou a ser de um degrau por vez.
        if (streakCpu < 3 && streakBanda < 8) return

        val next = screenQ.stepDownForCpu() ?: run { streakCpu = 0; streakBanda = 0; return } // já no piso
        streakCpu = 0
        streakBanda = 0
        streakLimpo = 0
        // Fora da thread do getStats (callback nativo) -> pro escopo do engine.
        scope.launch { autoStepDownTo(next) }
    }

    // Primeiro cai o fps (60 -> 30), depois a resolucao (720p -> 540p). Nessa ordem
    // porque fps e o que o olho perdoa menos numa tela em movimento, mas e tambem o
    // que corta mais custo por degrau: o encoder por software gasta quase em linha
    // reta com a taxa de quadros. Piso = 540p30.
    private fun ScreenQuality.stepDownForCpu(): ScreenQuality? = when (this) {
        ScreenQuality.SMOOTH_720_60 -> ScreenQuality.LIGHT_720_30
        ScreenQuality.LIGHT_720_30  -> ScreenQuality.TINY_540_30
        ScreenQuality.TINY_540_30   -> null
    }

    // O caminho de volta. Nunca passa da escolha do dono: quem pediu 720p30 na mao nao
    // vai receber 720p60 porque a rede melhorou — a escolha dele nao e um piso, e a
    // decisao dele.
    private fun ScreenQuality.stepUp(): ScreenQuality? = when (this) {
        ScreenQuality.TINY_540_30   -> ScreenQuality.LIGHT_720_30
        ScreenQuality.LIGHT_720_30  -> ScreenQuality.SMOOTH_720_60
        ScreenQuality.SMOOTH_720_60 -> null
    }

    private suspend fun autoStepUpTo(q: ScreenQuality, teto: ScreenQuality) {
        // Chegou de volta no que a pessoa escolheu: o teto de sessao deixa de existir,
        // senao a proxima republicacao voltaria a rebaixar sozinha.
        sessionQualityCap = if (q == teto) null else q
        if (!_screenOn.value) return
        val src = lastScreenSource
        stopScreenShare(silent = true)
        delay(350)
        startScreenShare(src, silent = true)
        _quedaAutomatica.value = if (sessionQualityCap == null) null else q
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
        // DEPOIS de republicar: o stopScreenShare limpa este aviso, entao marcar antes
        // seria marcar pra ninguem. Quem le isto e a linha de status da transmissao.
        _quedaAutomatica.value = q
    }

    // Fala: a MINHA vem do RMS do mic (onMicLevel -> mySpeakUntil); a dos OUTROS vem
    // do getStats do audio remoto (INBOUND_RTP audioLevel). Independe do
    // speakers_changed do servidor. Hangover segura o "falando" ~400ms apos cair
    // (anti-flicker) — a inchada do card reage a isso.
    private fun startSpeakingPoll() {
        speakingJob?.cancel()
        // Sala nova, servidor possivelmente outro: volta a medir por conta propria
        // ate o primeiro speakers_changed provar que nao precisa.
        servidorAvisaFala = false
        speakingJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (!_micOn.value) mySpeakUntil = 0L // mudo => não "fala"
                // Medir o nivel de cada um custa CARO: um getStats por pessoa, cinco
                // vezes por segundo, e cada chamada monta um relatorio inteiro de
                // estatisticas (dezenas de objetos com mapas de atributos) que vira
                // lixo no instante seguinte. Numa sala de cinco sao vinte relatorios
                // por segundo pra responder uma pergunta que o servidor ja responde
                // de graca pelo speakers_changed.
                //
                // Fica como rede de seguranca ate o primeiro aviso do servidor
                // chegar — servidor que nao mande o evento continua funcionando.
                if (!servidorAvisaFala) {
                    sub?.let { pc ->
                        remoteAudioReceivers.entries.toList().forEach { (sid, recv) ->
                            runCatching { pc.getStats(recv) { r -> if (audioLevelOf(r) > SPEAK_THRESHOLD) markRemoteSpeak(sid) } }
                        }
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

    // PASSO 9 — "o audio dos outros esta CHEGANDO?"
    //
    // Nenhum dos passos 1 a 8 responde isso. Todos falam do que SAI (microfone,
    // publicacao) ou da conexao existir. So que "ninguem escuta ninguem" pode ser
    // tres coisas COMPLETAMENTE diferentes, e as tres sao silencio identico:
    //
    //   sem receptor de audio  -> nunca fomos inscritos nas faixas dos outros
    //                             (assinatura/negociacao — problema de sinalizacao)
    //   receptor sem pacote    -> inscritos, mas a midia nao chega
    //                             (rede, servidor, ICE)
    //   pacotes chegando       -> a midia CHEGA e nao vira som
    //                             (saida de audio: o modulo de som ou o aparelho)
    //
    // Cada uma tem conserto em um lugar oposto do codigo, e sem esta linha a
    // escolha entre elas e chute. Duas fotos porque uma leitura sozinha mente:
    // cedo demais e ninguem publicou ainda, tarde demais e a pessoa ja desistiu.
    private fun conferirEntradaDeAudio() {
        entradaJob?.cancel()
        entradaJob = scope.launch {
            delay(6_000)
            fotografarEntrada()
            delay(14_000)
            fotografarEntrada()
        }
    }

    private fun fotografarEntrada() {
        val pc = sub ?: return
        val receptores = runCatching {
            pc.getReceivers().count { runCatching { it.track is AudioTrack }.getOrDefault(false) }
        }.getOrDefault(-1)
        runCatching {
            pc.getStats { r ->
                var fontes = 0
                var pacotes = 0L
                r.stats.values.forEach { s ->
                    if (s.type != RTCStatsType.INBOUND_RTP) return@forEach
                    val a = s.attributes
                    if ((a["kind"] as? String) != "audio") return@forEach
                    fontes++
                    pacotes += (a["packetsReceived"] as? Number)?.toLong() ?: 0L
                }
                val veredito = when {
                    receptores <= 0 && fontes == 0 ->
                        "NAO estamos inscritos no audio de ninguem — o problema e na assinatura, nao no som"
                    pacotes <= 0L ->
                        "inscritos, mas ZERO pacote chegou — a midia nao esta vindo (rede/servidor)"
                    else ->
                        "o audio CHEGA ($pacotes pacotes) — se nao da para ouvir, o problema e a saida de som"
                }
                VoiceLog.nota("9. entrada de audio: $receptores receptor(es), $fontes fonte(s), $pacotes pacotes -> $veredito")
            }
        }
    }

    // CANCELAMENTO DE ECO — decide qual faixa remota alimenta o APM.
    //
    // O AEC subtrai do microfone aquilo que esta SAINDO na caixa de som. Pra isso
    // ele precisa ouvir o que sai. So que a lib nao oferece torneira do audio JA
    // MISTURADO — o que da pra capturar e uma faixa por pessoa, cada uma no ritmo
    // do proprio decodificador.
    //
    // Por isso o AEC so liga com UMA pessoa do outro lado. Com duas ou mais, o
    // sinal que a gente entregaria seria METADE do que sai na caixa, e AEC com
    // referencia errada nao "cancela menos": ele subtrai coisa que nao e eco, e o
    // estrago aparece na SUA voz, picotada. Desligado e melhor que estragado.
    //
    // Cobre o caso que importa hoje (conversa de dois) e degrada de forma honesta.
    // Pra cobrir grupo faltaria misturar as faixas na mao, com sincronia — outra
    // fatia, e com risco proprio.
    private fun reavaliarAec() {
        val alvo = remoteAudioTracks.entries.singleOrNull()?.takeIf { prefs.state.value.micEchoCancel }
        if (alvo?.key == aecSid) return

        aecSink?.let { s -> aecTrack?.let { t -> runCatching { t.removeSink(s) } } }
        aecSink = null
        aecTrack = null
        aecSid = null

        if (alvo == null) {
            VoiceLog.nota("8. cancelamento de eco desligado (" + motivoAecDesligado() + ")")
            return
        }
        val sink = AudioTrackSink { data, bits, taxa, canais, quadros ->
            micCapture?.processarReverso(data, bits, taxa, canais, quadros)
        }
        runCatching { alvo.value.addSink(sink) }
            .onSuccess {
                aecSink = sink
                aecTrack = alvo.value
                aecSid = alvo.key
                runCatching { micCapture?.avisarAtraso(AEC_DELAY_MS) }
                VoiceLog.nota("8. cancelamento de eco LIGADO")
            }
            .onFailure { VoiceLog.nota("8. cancelamento de eco falhou ao ligar: " + (it.message ?: "erro")) }
    }

    private fun motivoAecDesligado(): String = when {
        !prefs.state.value.micEchoCancel -> "desligado nas preferencias"
        remoteAudioTracks.isEmpty() -> "ninguem mais na sala"
        else -> "${remoteAudioTracks.size} pessoas — so funciona em conversa de dois"
    }

    // speakers_changed e um DELTA: vem quem mudou de estado, com active=false pra
    // quem parou. Quem nao aparece na lista fica como estava.
    //
    // A minha propria linha e ignorada de proposito: o servidor so sabe da minha
    // voz depois que ela sobe, comprime e volta — uns 200ms de atraso num anel
    // que reage ao meu proprio rosto. O RMS do meu mic e instantaneo e ja resolve.
    private fun onSpeakersChanged(speakers: List<LivekitModels.SpeakerInfo>) {
        // Chegou aviso do servidor pelo menos uma vez => o poll de getStats vira
        // custo puro e se desliga (ver startSpeakingPoll). Nao da pra decidir isso
        // no comeco da call: so descobrimos que o servidor manda quando ele manda.
        if (!servidorAvisaFala) {
            servidorAvisaFala = true
            VoiceLog.nota("quem fala vem do servidor — medicao local de nivel desligada (economia)")
        }
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
        statsJob?.cancel()
        _screenStats.value = null
        _quedaAutomatica.value = null
        // Motor novo: o ramo de video sai do cano e a linha de midia vira inativa na
        // renegociacao, que e o que faz o servidor despublicar.
        gstPub?.let { gst ->
            gst.pararVideo()
            screenCid = null
            gst.negociar()
        }
        screenSender?.let { runCatching { pub?.removeTrack(it) } }
        screenSender = null
        // A ESPERA IMPORTA, pelo mesmo motivo do microfone logo abaixo no dispose():
        // o stop() so volta depois que a thread da captura morreu, e e isso que
        // autoriza soltar a fonte nativa que ela usa a cada 16ms. Antes o dispose
        // corria por cima de um pushFrame em andamento e a thread morria com
        // "Object handle is null" — que, numa thread solta, leva o app junto.
        val capturaParou = ffmpegCap?.stop() ?: true
        ffmpegCap = null
        if (capturaParou) {
            runCatching { customSource?.dispose() }
        } else {
            VoiceLog.nota("a captura de tela nao encerrou a tempo — fonte de video nao liberada (seguro, mas anormal)")
        }
        customSource = null
        // Depois da captura, nunca antes: o wrap() destes rasters roda na thread de
        // preview, que so para junto com ela. Fechar as imagens Skia com a thread
        // ainda viva e a mesma corrida, um andar acima.
        previewRasters.dispose()
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
        if (gstPub == null) negotiatePublisher()
    }

    // Troca a qualidade/fluidez da transmissão ao vivo (gear da call): persiste a
    // pref e, se já estou transmitindo, reinicia a captura no MESMO monitor com o
    // novo preset (o ffmpeg e spawnado com w/h/fps assados -> so reiniciando muda).
    fun setScreenQuality(q: ScreenQuality) {
        sessionQualityCap = null // escolha manual = usuário no controle; limpa o auto-cap
        _quedaAutomatica.value = null // e o aviso da queda automatica sai junto
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
        val gst = gstPub
        val track = micTrack
        if (gst == null && track == null) return
        val on = !_micOn.value
        // No motor novo o silencio e feito zerando as amostras, e nao parando de
        // empurrar: manter a cadencia de 10ms custa quase nada (o Opus comprime silencio
        // a ~1kbps) e evita que o outro lado veja a faixa morrer e reaja a isso.
        gst?.mudo(!on)
        track?.isEnabled = on
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
        // Relogio da call. Marcado no PRIMEIRO connected e nunca remarcado: uma queda
        // de rede que reconecta nao devolve o tempo ao zero — o tempo e da sua sessao
        // na sala, nao da conexao. Sai junto com o engine (uma call por vez).
        if (_inicio.value == null) _inicio.value = System.currentTimeMillis()
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
        // Solta o AEC ANTES de largar as faixas: o sink e nativo e ficaria
        // apontando pra uma track que vai embora.
        remoteAudioTracks.clear()
        reavaliarAec()
        _localScreen.value = null
        _localPreview.value = null
        val capturaParou = runCatching { ffmpegCap?.stop() ?: true }.getOrDefault(false)
        ffmpegCap = null
        if (capturaParou) {
            runCatching { customSource?.dispose() }
        } else {
            VoiceLog.nota("a captura de tela nao encerrou a tempo — fonte de video nao liberada (seguro, mas anormal)")
        }
        customSource = null
        previewRasters.dispose()
        runCatching { screenSource?.stop() }
        runCatching { screenTrack?.dispose() }
        runCatching { screenSource?.dispose() }
        runCatching { cameraSource?.stop() }
        runCatching { cameraSource?.dispose() }
        cameraSource = null
        screenTrack = null
        screenSource = null
        // A ORDEM E A ESPERA IMPORTAM. O stop() agora so volta depois que a thread
        // da captura morreu de fato, e e por isso que o dispose logo abaixo pode
        // acontecer: ele libera memoria NATIVA que aquela thread usa a cada 10ms.
        // Antes o stop() voltava na hora e o dispose corria por cima de um
        // pushAudio em andamento — heap nativo corrompido, processo derrubado pelo
        // Windows sem uma linha de log. Era o "o Astra fecha sozinho".
        val paradaLimpa = runCatching { micCapture?.stop() ?: true }.getOrDefault(false)
        micCapture = null
        runCatching { micTrack?.dispose() }
        micTrack = null
        if (paradaLimpa) {
            runCatching { micSource?.dispose() }
        } else {
            // Deixa vazar de proposito: alguns KB ate a proxima call, contra
            // derrubar o app agora. Fica registrado porque, se aparecer sempre,
            // e sinal de que ha algo travando o driver do microfone.
            VoiceLog.nota("a captura do mic nao encerrou a tempo — fonte de audio nao liberada (seguro, mas anormal)")
        }
        micSource = null
        // Antes de fechar o resto: o cano do GStreamer tem memoria NATIVA e threads
        // proprias, e o `parar()` so volta depois que o cano descansou.
        runCatching { gstPub?.parar() }
        gstPub = null
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
