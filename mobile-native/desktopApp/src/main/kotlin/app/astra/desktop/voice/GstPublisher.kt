package app.astra.desktop.voice

import app.astra.desktop.prefs.ScreenQuality
import org.freedesktop.gstreamer.Buffer
import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Caps
import org.freedesktop.gstreamer.Element
import org.freedesktop.gstreamer.ElementFactory
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.Pad
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.Promise
import org.freedesktop.gstreamer.SDPMessage
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.Structure
import org.freedesktop.gstreamer.elements.AppSrc
import org.freedesktop.gstreamer.lowlevel.GstAPI
import org.freedesktop.gstreamer.webrtc.WebRTCBin
import org.freedesktop.gstreamer.webrtc.WebRTCSDPType
import org.freedesktop.gstreamer.webrtc.WebRTCSessionDescription
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// O TRANSPORTE de saida pelo GStreamer: quem publica deixa de ser a PeerConnection do
// webrtc-java e passa a ser o `webrtcbin`.
//
// POR QUE TROCAR O TRANSPORTE, e nao so a captura. O perfil por thread, transmitindo a
// 720p60, deu isto em nucleos:
//
//   ffmpeg-cap      0,912   ler o cano e copiar quadro pro WebRTC
//   ffmpeg.exe      1,095   processo a parte: capturar, baixar da GPU, converter
//   (nativa)        0,258   COMPRIMIR de verdade
//
// Dois nucleos MOVENDO PIXEL, um quarto comprimindo. Eles existem por causa de uma
// fronteira: o webrtc-java so aceita quadro cru em memoria principal, entao o quadro TEM
// que descer da placa. Trocar so a captura mantem a fronteira e devolve metade. O
// webrtcbin aceita H264 JA COMPRIMIDO -- e ai o quadro nasce e morre dentro da placa.
//
// TUDO O QUE SAI passa por aqui, porque o LiveKit da UMA conexao de publicacao: o
// microfone vive nela a call inteira e a tela entra e sai no meio.
//
// O QUE ESTE ARQUIVO NAO FAZ: sinalizacao. Ele produz ofertas e candidatos e recebe
// respostas; quem conversa com o LiveKit continua sendo o VoiceEngine. A conexao de
// RECEBER (ouvir os outros, cancelamento de eco) tambem nao muda -- segue no webrtc-java.
class GstPublisher(
    private val onOferta: (String) -> Unit,
    private val onCandidato: (Int, String) -> Unit,
    private val onPrevia: ((ByteArray, Int, Int) -> Unit)? = null,
) {

    // `nvd3d11h264enc`, e nao `nvh264enc`: o segundo e o modo CUDA da NVIDIA e RECUSA
    // quadro em memoria D3D11. Com ele o cano nem liga -- e o GStreamer nao trata isso
    // como erro, apenas avisa e monta sem o ramo de video.
    private val ENCODERS = listOf("nvd3d11h264enc", "qsvh264enc", "amfh264enc", "mfh264enc")

    data class Estatisticas(val fpsEnvio: Int, val limite: String)

    private var pipeline: Pipeline? = null
    private var bin: WebRTCBin? = null
    private var mic: AppSrc? = null
    private var ramoVideo: List<Element> = emptyList()
    private var padVideo: Pad? = null

    private var cidMic: String? = null
    private var cidVideo: String? = null

    private var taxaAtual = 48000
    private var canaisAtuais = 1

    @Volatile private var mudo = false
    @Volatile var vivo = false
        private set

    val encoderDisponivel: String?
        get() = ENCODERS.firstOrNull { runCatching { ElementFactory.find(it) != null }.getOrDefault(false) }

    // ---- ciclo de vida ----

    // Sobe o cano com o microfone so. A tela entra depois, se entrar.
    //
    // `false` = esta maquina nao tem como seguir por aqui, e quem chama deve usar o
    // caminho de sempre. Nunca lanca: a ausencia do caminho novo e um caminho a menos,
    // nunca uma call quebrada.
    fun iniciar(cidDoMic: String, servidorStun: String?): Boolean {
        if (!GStreamerPack.iniciarGst()) return false
        if (encoderDisponivel == null) {
            VoiceLog.nota("transporte novo: sem encoder de hardware, seguindo pelo caminho de sempre")
            return false
        }
        return runCatching {
            // O formato do appsrc e exatamente o que o MicCapture ja entrega depois do
            // processamento (48kHz mono 16 bits) -- nao ha reamostragem no caminho.
            //
            // Sem `encoding-params`: por-lo como 2 (estereo) com o opusenc mono vira
            // "Internal data stream error" no appsrc, tres elementos antes do desencontro.
            val p = Gst.parseLaunch(
                "webrtcbin name=envio bundle-policy=max-bundle latency=0 " +
                    "appsrc name=mic is-live=true format=time do-timestamp=true " +
                    "caps=audio/x-raw,format=S16LE,rate=48000,channels=1,layout=interleaved " +
                    "! audioconvert ! audioresample ! opusenc bitrate=$OPUS_BITRATE inband-fec=true " +
                    "! rtpopuspay pt=111 " +
                    "! application/x-rtp,media=audio,encoding-name=OPUS,payload=111,clock-rate=48000 " +
                    "! envio.",
            ) as Pipeline
            val b = p.getElementByName("envio") as WebRTCBin

            // O barramento, e nao excecoes: elemento que falha no GStreamer posta a
            // queixa aqui e o cano segue vivo sem aquele ramo. Sem escutar, o unico
            // sintoma seria "a transmissao nao aparece pra ninguem".
            p.bus.connect(Bus.ERROR { fonte, _, msg ->
                VoiceLog.nota("transporte novo: erro em ${fonte.name} -> $msg")
            })

            servidorStun?.let { runCatching { b.setStunServer(it) } }
            b.connect(WebRTCBin.ON_ICE_CANDIDATE { mlinha, cand -> onCandidato(mlinha, cand) })
            pendurarAdaptacaoDeBanda(b)
            runCatching { b.getStaticPad("sink_0")?.set("msid", cidDoMic) }

            pipeline = p
            bin = b
            mic = p.getElementByName("mic") as? AppSrc
            cidMic = cidDoMic
            p.play()
            p.getState(5_000_000_000L)
            vivo = true
            true
        }.getOrElse {
            VoiceLog.nota("transporte novo nao subiu: ${it.javaClass.simpleName} ${it.message.orEmpty()}")
            parar()
            false
        }
    }

    fun parar() {
        vivo = false
        val p = pipeline
        pipeline = null
        bin = null
        mic = null
        ramoVideo = emptyList()
        padVideo = null
        runCatching { p?.setState(State.NULL) }
        runCatching { p?.dispose() }
    }

    // ---- microfone ----

    // Recebe o PCM do MicCapture. `mudo` zera as amostras em vez de parar de empurrar:
    // manter a cadencia de 10ms custa quase nada (o Opus comprime silencio a ~1kbps) e
    // evita que o outro lado veja a faixa morrer e reaja a isso.
    //
    // O FORMATO VEM JUNTO E PODE MUDAR. O caminho feliz entrega 48kHz mono (o APM ja
    // converteu), mas o MicCapture tem um caminho de recuperacao: se o APM falhar, ele
    // manda o PCM CRU do aparelho pra nao deixar a pessoa muda. Se o appsrc estivesse
    // preso a 48k mono, quem caisse nesse caminho ficaria mudo no transporte novo -- ou,
    // pior, sairia com a voz acelerada. Declarar o formato que de fato chegou deixa o
    // `audioconvert ! audioresample` do cano fazer a conversao que o Opus precisa.
    fun empurrarAudio(pcm: ByteArray, bits: Int, taxa: Int, canais: Int, quadros: Int) {
        val fonte = mic ?: return
        val bytes = quadros * canais * (bits / 8)
        if (bytes <= 0 || bytes > pcm.size) return
        runCatching {
            if (taxa != taxaAtual || canais != canaisAtuais) {
                fonte.caps = Caps.fromString(
                    "audio/x-raw,format=S16LE,rate=$taxa,channels=$canais,layout=interleaved",
                )
                taxaAtual = taxa
                canaisAtuais = canais
            }
            val buf = Buffer(bytes)
            val destino = buf.map(true) ?: return
            if (mudo) destino.put(ByteArray(bytes)) else destino.put(pcm, 0, bytes)
            buf.unmap()
            fonte.pushBuffer(buf)
        }
    }

    fun mudo(ligado: Boolean) { mudo = ligado }

    // ---- video ----

    fun publicarTela(cid: String, monitor: Int, q: ScreenQuality): Boolean =
        publicarVideo(cid, q.width, q.height, q.fps, q.bitrate) {
            ElementFactory.make("d3d11screencapturesrc", "tela")?.apply {
                runCatching { set("monitor-index", monitor) }
            }
        }

    fun publicarCamera(cid: String, largura: Int, altura: Int): Boolean =
        publicarVideo(cid, largura, altura, 30, 2_000_000) {
            ElementFactory.make("mfvideosrc", "camera")
        }

    // Monta o ramo de video e pendura no webrtcbin.
    //
    // O CANO DESCANSA PRIMEIRO, e isso nao e cautela: e a unica forma que funciona.
    // Fonte AO VIVO acrescentada a um cano andando fica em PLAYING, negocia formato e
    // entrega ZERO quadro -- sem erro e sem aviso (medido elo a elo). Herdar o tempo-base
    // nao resolve. Passar pelo repouso faz o ramo nascer junto com o resto.
    //
    // Custo medido: o audio fica parado 137ms. A conexao, o ICE e a criptografia
    // sobrevivem ao repouso -- nao e reconexao.
    private fun publicarVideo(
        cid: String,
        largura: Int,
        altura: Int,
        fps: Int,
        bitrate: Int,
        criarFonte: () -> Element?,
    ): Boolean {
        val p = pipeline ?: return false
        val b = bin ?: return false
        if (ramoVideo.isNotEmpty()) pararVideo()

        val enc = encoderDisponivel ?: return false
        val fonte = criarFonte() ?: return false
        val conv = ElementFactory.make("d3d11convert", "conv") ?: return false
        val formato = ElementFactory.make("capsfilter", "formato") ?: return false
        val encoder = ElementFactory.make(enc, "encoder") ?: return false
        val parser = ElementFactory.make("h264parse", "parser") ?: return false
        val pay = ElementFactory.make("rtph264pay", "pay") ?: return false
        val formatoRtp = ElementFactory.make("capsfilter", "formatoRtp") ?: return false

        formato.set(
            "caps",
            Caps.fromString("video/x-raw(memory:D3D11Memory),format=NV12,width=$largura,height=$altura,framerate=$fps/1"),
        )
        runCatching { encoder.set("bitrate", bitrate / 1000) } // kbps na maioria dos encoders
        // config-interval=-1 nos dois: o WebRTC exige que SPS/PPS voltem junto de todo
        // quadro-chave. Sem isso quem entra no meio da transmissao ve tela preta ate o
        // proximo, e "ate o proximo" pode ser meio minuto.
        parser.set("config-interval", -1)
        pay.set("pt", 96)
        pay.set("config-interval", -1)
        runCatching { pay.set("aggregate-mode", 1) } // zero-latency
        // clock-rate EXPLICITO: sem ele o webrtcbin nao monta uma descricao de midia
        // valida e desliga a linha, em silencio.
        formatoRtp.set(
            "caps",
            Caps.fromString("application/x-rtp,media=video,encoding-name=H264,payload=96,clock-rate=90000"),
        )

        val elementos = listOf(fonte, conv, formato, encoder, parser, pay, formatoRtp)
        return runCatching {
            p.addMany(*elementos.toTypedArray())
            // Ligacao CONFERIDA elo a elo. O parseLaunch engole falha de ligacao com um
            // aviso, e foi assim que o encoder errado passou batido por duas rodadas.
            for (i in 0 until elementos.size - 1) {
                if (!elementos[i].link(elementos[i + 1])) {
                    error("nao ligou ${elementos[i].name} -> ${elementos[i + 1].name}")
                }
            }
            val pad = b.getRequestPad("sink_%u") ?: error("o webrtcbin nao deu pad")
            formatoRtp.getStaticPad("src")?.link(pad) ?: error("sem pad de saida")
            runCatching { pad.set("msid", cid) }

            p.setState(State.PAUSED)
            p.getState(3_000_000_000L)
            elementos.forEach { it.syncStateWithParent() }
            p.setState(State.PLAYING)
            p.getState(5_000_000_000L)

            ramoVideo = elementos
            padVideo = pad
            cidVideo = cid
            true
        }.getOrElse { e ->
            VoiceLog.nota("transporte novo: a tela nao subiu (${e.message})")
            runCatching { p.removeMany(*elementos.toTypedArray()) }
            false
        }
    }

    // Desmonta o ramo de video.
    //
    // O REPOUSO AQUI TAMBEM E OBRIGATORIO. A primeira versao desligava os elementos com
    // o cano andando e quadros em voo, e CORROMPIA A MEMORIA NATIVA (0xC0000374) --
    // soltar memoria de quem ainda esta processando, o mesmo erro do pushFrame depois do
    // dispose na captura de tela. Custo medido: 12ms de audio parado.
    fun pararVideo() {
        val p = pipeline ?: return
        val b = bin ?: return
        val elementos = ramoVideo
        val pad = padVideo
        if (elementos.isEmpty()) return
        ramoVideo = emptyList()
        padVideo = null
        cidVideo = null

        p.setState(State.PAUSED)
        p.getState(3_000_000_000L)
        runCatching { elementos.last().getStaticPad("src")?.unlink(pad) }
        runCatching { pad?.let { b.releaseRequestPad(it) } }
        elementos.forEach {
            runCatching { it.setState(State.NULL) }
            runCatching { it.getState(2_000_000_000L) }
        }
        runCatching { p.removeMany(*elementos.toTypedArray()) }
        p.setState(State.PLAYING)
        p.getState(5_000_000_000L)
    }

    // ---- negociacao ----

    fun negociar() {
        val b = bin ?: return
        b.createOffer { oferta ->
            val cru = runCatching { oferta.getSDPMessage().toString() }.getOrNull() ?: return@createOffer
            val pronto = remendar(cru)
            runCatching {
                b.setLocalDescription(
                    WebRTCSessionDescription(WebRTCSDPType.OFFER, SDPMessage().apply { parseBuffer(pronto) }),
                )
            }.onFailure {
                VoiceLog.nota("transporte novo: o webrtcbin recusou a propria oferta remendada (${it.message})")
                return@createOffer
            }
            onOferta(pronto)
        }
    }

    fun aplicarResposta(sdp: String) {
        val b = bin ?: return
        runCatching {
            b.setRemoteDescription(
                WebRTCSessionDescription(WebRTCSDPType.ANSWER, SDPMessage().apply { parseBuffer(sdp) }),
            )
        }.onFailure { VoiceLog.nota("transporte novo: resposta do servidor recusada (${it.message})") }
    }

    fun candidatoRemoto(mlinha: Int, candidato: String) {
        runCatching { bin?.addIceCandidate(mlinha, candidato) }
    }

    // O REMENDO QUE FAZ O LIVEKIT RECONHECER A FAIXA.
    //
    // O LiveKit casa a faixa que chega pelo SDP com o AddTrackRequest usando o `cid`, e o
    // `cid` viaja dentro do `a=msid:<fluxo> <faixa>`. O webrtc-java poe o MESMO valor nos
    // dois campos; o webrtcbin so deixa escolher o do fluxo e gera o da faixa como
    // `webrtctransceiverN`.
    //
    // Reescrever aqui nao e gambiarra: e entregar byte a byte o que o servidor ja aceita
    // hoje, em vez de apostar no que ele talvez aceite. Tambem troca `sendrecv` por
    // `sendonly`, que e o que esta conexao de fato faz.
    private fun remendar(sdp: String): String {
        val saida = StringBuilder()
        fun linha(t: String) { saida.append(t).append("\r\n") }
        // null = esta midia nao tem faixa nossa agora (ex.: a tela que acabou de sair).
        var cid: String? = null
        var emMidia = false
        sdp.lineSequence().forEach { crua ->
            val l = crua.trimEnd('\r')
            when {
                l.startsWith("m=audio") -> { emMidia = true; cid = cidMic; linha(l) }
                l.startsWith("m=video") -> { emMidia = true; cid = cidVideo; linha(l) }
                // A MIDIA ORFA PRECISA DIZER QUE ESTA ORFA.
                //
                // Parar de transmitir nao apaga a linha de midia do SDP -- ela fica, e o
                // webrtcbin a reaproveita com um nome inventado (`webrtctransceiverN`).
                // Sem isto a oferta seguinte anunciava "continuo enviando" apontando pra
                // uma faixa que o servidor nunca ouviu falar: a transmissao ficaria
                // pendurada do lado de la depois de parar. `inactive` e o que faz o
                // servidor despublicar, e e o mesmo contrato do caminho de hoje.
                l == "a=sendrecv" || l == "a=sendonly" ->
                    linha(if (emMidia && cid == null) "a=inactive" else "a=sendonly")
                // Sem faixa nossa, o nome sai fora em vez de sair errado.
                l.startsWith("a=msid:") -> cid?.let { linha("a=msid:$it $it") }
                l.startsWith("a=ssrc:") && l.contains(" msid:") ->
                    cid?.let { linha(l.substringBefore(" msid:") + " msid:$it $it") }
                else -> linha(l)
            }
        }
        return saida.toString()
    }

    // ---- adaptacao de banda ----

    // O rtpgccbwe e o estimador de banda do WebRTC. Sem ele o webrtcbin manda no bitrate
    // fixo do encoder e nao desce quando a subida aperta -- o video CONGELA em vez de
    // perder nitidez. O webrtc-java de hoje traz isso de fabrica, entao ficar sem seria
    // regredir justamente pra quem tem internet ruim.
    private interface PedidoDeAuxiliar : GstAPI.GstCallback {
        fun callback(bin: Element, transporte: Element): Element?
    }

    private fun pendurarAdaptacaoDeBanda(b: WebRTCBin) {
        runCatching {
            val alvo = object : PedidoDeAuxiliar {
                override fun callback(bin: Element, transporte: Element): Element? =
                    ElementFactory.make("rtpgccbwe", null)
            }
            b.connect("request-aux-sender", PedidoDeAuxiliar::class.java, alvo, alvo)
        }.onFailure { VoiceLog.nota("transporte novo: sem adaptacao de banda (${it.message})") }
    }

    // ---- estatisticas ----

    // A promessa responde por CALLBACK e nao pelo waitResult(): `gst_promise_wait`
    // bloqueia ate alguem responder, e "alguem" aqui e o webrtcbin -- que pode nao
    // responder. Sem prazo, o poll de qualidade penduraria a coroutine pra sempre.
    fun estatisticas(): Estatisticas? {
        val b = bin ?: return null
        return runCatching {
            val pronto = CountDownLatch(1)
            val caixa = arrayOfNulls<Structure>(1)
            val promessa = Promise(Promise.PROMISE_CHANGE { pr ->
                caixa[0] = runCatching { pr.getReply() }.getOrNull()
                pronto.countDown()
            })
            b.emit("get-stats", null, promessa)
            if (!pronto.await(2, TimeUnit.SECONDS)) return null
            val texto = caixa[0]?.toString() ?: return null
            Estatisticas(extrairInteiro(texto, "frames-per-second"), "none")
        }.getOrNull()
    }

    private fun extrairInteiro(texto: String, chave: String): Int {
        val i = texto.indexOf(chave)
        if (i < 0) return 0
        return Regex("\\d+").find(texto.substring(i))?.value?.toIntOrNull() ?: 0
    }

    private companion object {
        // Mesmo teto do caminho de hoje: o padrao do WebRTC pra voz e ~32kbps, calibrado
        // pra rede de celular de uma decada atras. TETO, nao piso -- rede ruim desce.
        const val OPUS_BITRATE = 64_000
    }
}
