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
    // QUEM esta ligado no webrtcbin, guardado explicitamente.
    //
    // Antes o desmonte usava `ramoVideo.last()`, e isso quebrou no instante em que a
    // previa entrou na mesma lista: o ultimo passou a ser o appsink da previa, o
    // desligamento soltou o elemento errado, e o que continuava ligado no transporte foi
    // pra NULL ainda conectado -- memoria nativa corrompida e processo derrubado. Posicao
    // numa lista nao e identidade.
    private var saidaRtp: Element? = null

    private var cidMic: String? = null
    private var cidVideo: String? = null

    private var taxaAtual = 48000
    private var canaisAtuais = 1

    @Volatile private var mudo = false
    @Volatile var vivo = false
        private set

    // Promessas de estatistica que estouraram o prazo. Ver `estatisticas()`.
    private val promessasTardias = ArrayDeque<Promise>()

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
            // SEM `rtpgccbwe` POR ORA -- e uma ausencia deliberada, nao um esquecimento.
            //
            // Ele e o estimador de banda do WebRTC, e so entra pelo sinal
            // `request-aux-sender`. Esse sinal nao tem binding pronto nesta biblioteca, e
            // o que eu escrevi na mao estava errado em dois pontos: o segundo argumento e
            // um `GstWebRTCDTLSTransport`, e eu o declarei como `Element`; e o elemento
            // devolvido passa a ser do webrtcbin, sem jeito de o Java tirar a mao -- a
            // mesma armadilha de dois donos que acabou de derrubar a call.
            //
            // Ele tambem nao estava terminado: estimar banda so serve se alguem OUVIR a
            // estimativa e baixar o bitrate do encoder, e essa metade nunca existiu.
            // Entao o que sai daqui e um risco, nao uma funcionalidade. Volta como fatia
            // propria, com o laco fechado.
            //
            // O que se perde ate la: em subida apertada o video ENGASGA em vez de perder
            // nitidez. O audio nao muda -- o Opus ja lida com isso sozinho.
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

        // A PREVIA SAI DE UM TEE, e a reducao acontece NA PLACA antes de baixar.
        //
        // O ponto da migracao inteira e o quadro nao descer pra memoria principal. A
        // previa e a unica coisa que ainda obriga a descer -- entao ela desce o MENOR
        // quadro possivel: reduzir pra ~960px na placa e baixar isso da ~23 MB/s, contra
        // 83 MB/s do quadro inteiro a 60fps. Baixar primeiro e reduzir depois seria pagar
        // o preco cheio pra jogar 80% fora.
        //
        // `leaky=downstream max-size-buffers=1`: se a interface atrasar, o quadro velho
        // e DESCARTADO em vez de segurar a fila. Previa atrasada nao vale um engasgo na
        // transmissao de quem esta assistindo.
        val ramoPrevia = if (onPrevia != null) montarPrevia(largura, altura) else emptyList()
        val tee = if (ramoPrevia.isNotEmpty()) ElementFactory.make("tee", "tee") else null

        val principal = listOf(fonte, conv, formato) +
            (if (tee != null) listOf(tee) else emptyList()) +
            listOf(encoder, parser, pay, formatoRtp)
        val elementos = principal + ramoPrevia
        return runCatching {
            p.addMany(*elementos.toTypedArray())
            // Ligacao CONFERIDA elo a elo. O parseLaunch engole falha de ligacao com um
            // aviso, e foi assim que o encoder errado passou batido por duas rodadas.
            for (i in 0 until principal.size - 1) {
                if (!principal[i].link(principal[i + 1])) {
                    error("nao ligou ${principal[i].name} -> ${principal[i + 1].name}")
                }
            }
            if (tee != null) {
                for (i in 0 until ramoPrevia.size - 1) {
                    if (!ramoPrevia[i].link(ramoPrevia[i + 1])) {
                        error("previa: nao ligou ${ramoPrevia[i].name} -> ${ramoPrevia[i + 1].name}")
                    }
                }
                if (!tee.link(ramoPrevia.first())) error("previa: o tee nao ligou no ramo")
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
            saidaRtp = formatoRtp
            cidVideo = cid
            true
        }.getOrElse { e ->
            VoiceLog.nota("transporte novo: a tela nao subiu (${e.message})")
            runCatching { p.removeMany(*elementos.toTypedArray()) }
            false
        }
    }

    // O ramo da previa: reduz na placa, baixa pequeno, entrega BGRA pra interface.
    //
    // Devolve vazio se qualquer peca faltar -- e a previa some, nao a transmissao. Ela e
    // conferencia ("estou mostrando a tela certa"); quem esta do outro lado importa mais.
    private fun montarPrevia(larguraFonte: Int, alturaFonte: Int): List<Element> {
        val fila = ElementFactory.make("queue", "filaPrevia") ?: return emptyList()
        val reduz = ElementFactory.make("d3d11convert", "reduzPrevia") ?: return emptyList()
        val tamanho = ElementFactory.make("capsfilter", "tamanhoPrevia") ?: return emptyList()
        val baixa = ElementFactory.make("d3d11download", "baixaPrevia") ?: return emptyList()
        val cor = ElementFactory.make("videoconvert", "corPrevia") ?: return emptyList()
        val corFiltro = ElementFactory.make("capsfilter", "corFiltroPrevia") ?: return emptyList()
        val saida = ElementFactory.make("appsink", "previa") ?: return emptyList()

        runCatching {
            fila.set("leaky", 2) // descarta o VELHO; previa atrasada nao segura a fila
            fila.set("max-size-buffers", 1)
            fila.set("max-size-bytes", 0)
            fila.set("max-size-time", 0L)
        }
        // ALTURA EXPLICITA, calculada da proporcao da fonte.
        //
        // So `width` nao preserva proporcao: pedindo 960 de largura numa fonte 1280x720 o
        // d3d11convert devolveu 960x720 -- a tela da pessoa esticada na propria previa.
        // Par obrigatorio (NV12 tem croma pela metade em cada eixo; dimensao impar nao
        // existe nesse formato).
        val alturaPrevia = ((alturaFonte.toLong() * PREVIA_LARGURA / larguraFonte.coerceAtLeast(1)).toInt())
            .coerceAtLeast(2) and 1.inv()
        tamanho.set(
            "caps",
            Caps.fromString("video/x-raw(memory:D3D11Memory),format=NV12,width=$PREVIA_LARGURA,height=$alturaPrevia"),
        )
        // RGBA, e nao BGRA. Quem consome isto e o `PreviewRasters.wrap`, que monta a
        // imagem com `ColorType.RGBA_8888` -- ou seja, espera R,G,B,A NA ORDEM DA
        // MEMORIA. O `BGRA` do GStreamer e literalmente B,G,R,A na memoria: a previa
        // sairia com azul e vermelho trocados, e o erro nao aparece em nenhum log --
        // aparece na cara de quem transmite, com a propria tela em cores erradas.
        corFiltro.set("caps", Caps.fromString("video/x-raw,format=RGBA"))

        val sink = saida as? org.freedesktop.gstreamer.elements.AppSink ?: return emptyList()
        sink.set("emit-signals", true)
        sink.set("sync", false)
        sink.set("max-buffers", 1)
        sink.set("drop", true)
        sink.connect(org.freedesktop.gstreamer.elements.AppSink.NEW_SAMPLE { s ->
            // Puxar E soltar sempre: sem o pull o appsink enche e trava o cano inteiro
            // depois de max-buffers -- e ai a TRANSMISSAO para junto com a previa.
            val amostra = s.pullSample()
            if (amostra != null) {
                runCatching {
                    val estrutura = amostra.caps.getStructure(0)
                    val largura = estrutura.getInteger("width")
                    val altura = estrutura.getInteger("height")
                    val buf = amostra.buffer
                    val bytes = buf.map(false)
                    if (bytes != null) {
                        val copia = ByteArray(bytes.remaining())
                        bytes.get(copia)
                        onPrevia?.invoke(copia, largura, altura)
                    }
                    buf.unmap()
                }
                amostra.dispose()
            }
            org.freedesktop.gstreamer.FlowReturn.OK
        })
        return listOf(fila, reduz, tamanho, baixa, cor, corFiltro, saida)
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
        val saida = saidaRtp
        if (elementos.isEmpty()) return
        ramoVideo = emptyList()
        padVideo = null
        saidaRtp = null
        cidVideo = null

        p.setState(State.PAUSED)
        p.getState(3_000_000_000L)
        // `saida`, e nao `elementos.last()`: com a previa na lista o ultimo e o appsink
        // dela, e soltar o elemento errado deixa o certo indo pra NULL ainda ligado no
        // transporte -- foi assim que o processo caiu com a memoria corrompida.
        runCatching { saida?.getStaticPad("src")?.unlink(pad) }
        runCatching { pad?.let { b.releaseRequestPad(it) } }
        // DE TRAS PRA FRENTE, e isto e o conserto de um travamento de verdade.
        //
        // Desligando da fonte pra saida, o cano PENDURA PRA SEMPRE. O motivo: a thread da
        // fonte pode estar parada dentro de um `push` esperando quem esta abaixo aceitar o
        // quadro. Mandar a FONTE parar exige que essa thread termine -- e ela nao termina,
        // porque quem a segura ainda esta de pe. Um esperando o outro.
        //
        // Ao contrario funciona porque desligar um elemento marca as portas dele como "em
        // descarga": qualquer `push` que chegue ali volta na hora em vez de esperar. Cada
        // elemento que para solta o de cima, e a fila se desfaz sozinha ate a fonte.
        //
        // Custava um travamento a cada duas transmissoes encerradas -- e `stopScreenShare`
        // roda na thread da interface, entao o travamento era a JANELA INTEIRA congelada.
        elementos.reversed().forEach {
            val t0 = System.currentTimeMillis()
            runCatching { it.setState(State.NULL) }
            runCatching { it.getState(2_000_000_000L) }
            val gasto = System.currentTimeMillis() - t0
            // O caso saudavel inteiro leva ~10ms; 200 num elemento so ja e anormal.
            // Sem o NOME, "pendurou no desmonte" nao diz em que peca olhar.
            if (gasto > 200) VoiceLog.nota("transporte novo: '${it.name}' levou ${gasto}ms pra desligar")
        }
        runCatching { p.removeMany(*elementos.toTypedArray()) }
        p.setState(State.PLAYING)
        p.getState(5_000_000_000L)
    }

    // ---- negociacao ----

    fun negociar() {
        val b = bin ?: return
        b.createOffer { oferta ->
            // O `getSDPMessage` devolve uma COPIA nossa -- essa pode ser lida a vontade.
            val cru = runCatching { oferta.getSDPMessage().toString() }.getOrNull()
            // A OFERTA EM SI NAO E NOSSA, e e o segundo dono duplicado desta migracao.
            //
            // Ela vem de dentro da promessa, e a biblioteca a embrulha marcando "o Java e
            // o dono" -- so que `g_value_get_boxed`, de onde ela sai, nao transfere posse
            // nenhuma. Quem manda na memoria e a promessa, que se desfaz assim que este
            // bloco termina. Depois disso o coletor de lixo passa, libera de novo o que ja
            // morreu, e o processo cai com 0xC0000374 -- longe daqui, sem pilha e sem
            // laudo. `invalidate()` e o Java tirando a mao.
            runCatching { oferta.invalidate() }
            if (cru == null) return@createOffer
            val pronto = remendar(cru)
            runCatching {
                b.setLocalDescription(descricao(WebRTCSDPType.OFFER, pronto))
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
            b.setRemoteDescription(descricao(WebRTCSDPType.ANSWER, sdp))
        }.onFailure { VoiceLog.nota("transporte novo: resposta do servidor recusada (${it.message})") }
    }

    // Monta a descricao SEM deixar DOIS DONOS para o mesmo pedaco de memoria.
    //
    // ISTO DERRUBAVA O APP AO ENTRAR NA CALL, e foi o defeito mais caro da migracao.
    //
    // `gst_webrtc_session_description_new` FICA com o SDPMessage que recebe -- na
    // linguagem do GStreamer, `transfer full`. O binding Java de hoje nao marca esse
    // parametro como entregue (marca so o retorno, com @CallerOwnsReturn). Entao o objeto
    // Java continua achando que o SDP e dele e, quando o coletor de lixo passa, libera
    // memoria que o GStreamer ja tinha adotado. O segundo `free` corrompe o monte nativo
    // e o processo evapora com 0xC0000374.
    //
    // POR QUE NINGUEM VIU ANTES: nao quebra na hora. Quebra quando o coletor decide
    // passar, e ele nao passa num ensaio de trinta segundos. Quem pagava era a call de
    // verdade, que negocia tres vezes so pra entrar -- e a morte nao deixava rastro
    // nenhum: sem excecao Java, sem hs_err, sem registro no Windows.
    //
    // `invalidate()` e a mesma saida que a propria biblioteca usa no `getSDPMessage`:
    // o Java tira a mao do objeto sem liberar nada.
    private fun descricao(tipo: WebRTCSDPType, sdp: String): WebRTCSessionDescription {
        val mensagem = SDPMessage().apply { parseBuffer(sdp) }
        val pronta = WebRTCSessionDescription(tipo, mensagem)
        mensagem.invalidate()
        return pronta
    }

    fun candidatoRemoto(mlinha: Int, candidato: String) {
        runCatching { bin?.addIceCandidate(mlinha, candidato) }
    }

    // "new" / "connecting" / "connected" / "failed" / "closed".
    //
    // Existe porque "a call subiu" e "a call CONECTOU" sao coisas diferentes, e so a
    // segunda importa pra quem esta do outro lado. Sem isto, uma conexao que nunca fecha
    // e indistinguivel de uma que fechou e nao tem o que enviar.
    fun estadoDaConexao(): String =
        runCatching { bin?.connectionState?.name?.lowercase() }.getOrNull() ?: "?"

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
            if (!pronto.await(2, TimeUnit.SECONDS)) {
                // NAO RESPONDEU NO PRAZO -> a promessa tem que sobreviver ao coletor.
                //
                // O gst1-java-core guarda a funcao de retorno DENTRO do objeto Java da
                // promessa. Se o coletor levar a promessa e o webrtcbin responder depois,
                // o lado nativo chama um endereco que nao existe mais -- a mesma familia
                // de defeito que derrubava a call, agora armada durante a transmissao.
                //
                // O prazo estoura justamente quando o cano esta ocupado (entrar ou sair da
                // tela passa pelo repouso), e e nesse momento que esta funcao e chamada de
                // dois em dois segundos. Nao e hipotese.
                //
                // Segura as ultimas 64. Objeto minusculo, e 64 x 2s cobrem dois minutos --
                // muito depois de o webrtcbin ter desistido.
                synchronized(promessasTardias) {
                    promessasTardias.addLast(promessa)
                    while (promessasTardias.size > 64) promessasTardias.removeFirst()
                }
                return null
            }
            val texto = caixa[0]?.toString() ?: return null
            // O relatorio do webrtcbin vem como texto de GstStructure aninhada e
            // ESCAPADA (`frames\-per\-second\=\(double\)29.9`), entao a leitura e por
            // regex mesmo -- montar o parser da linguagem inteira pra tirar um numero
            // seria caro e fragil do mesmo jeito.
            //
            // Nao ha equivalente ao `qualityLimitationReason` do libwebrtc aqui, e por
            // isso o limite sai sempre "none". A linha de status continua mostrando
            // captura e envio, que e o que responde "esta fluindo?".
            Estatisticas(extrairNumero(texto, "frames-per-second"), "none")
        }.getOrNull()
    }

    private fun extrairNumero(texto: String, chave: String): Int {
        val i = texto.indexOf(chave)
        if (i < 0) return 0
        return Regex("(\\d+(?:\\.\\d+)?)").find(texto.substring(i, minOf(texto.length, i + 60)))
            ?.value?.toDoubleOrNull()?.toInt() ?: 0
    }

    private companion object {
        // Mesmo teto do caminho de hoje: o padrao do WebRTC pra voz e ~32kbps, calibrado
        // pra rede de celular de uma decada atras. TETO, nao piso -- rede ruim desce.
        const val OPUS_BITRATE = 64_000

        // Largura da previa local. Mesma do caminho de hoje: previa e conferencia de
        // enquadramento, nao precisa da resolucao da transmissao.
        const val PREVIA_LARGURA = 960
    }
}
