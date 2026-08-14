package app.astra.desktop.voice

import app.astra.desktop.prefs.ScreenQuality
import org.freedesktop.gstreamer.Buffer
import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Caps
import org.freedesktop.gstreamer.Element
import org.freedesktop.gstreamer.ElementFactory
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.Pad
import org.freedesktop.gstreamer.PadProbeReturn
import org.freedesktop.gstreamer.PadProbeType
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.SDPMessage
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.elements.AppSrc
import org.freedesktop.gstreamer.webrtc.WebRTCBin
import org.freedesktop.gstreamer.webrtc.WebRTCSDPType
import org.freedesktop.gstreamer.webrtc.WebRTCSessionDescription
import kotlin.math.roundToInt

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
    // Fabricante da placa que o dono escolheu em Configuracoes ("Intel", "NVIDIA",
    // "AMD"), ou nulo pra automatico. Ver `encoderQueComprime`: e um pedido, e a captura
    // de tela so o atende quando ele coincide com a placa que desenha o monitor.
    private val placaPedida: String? = null,
) {

    // `nvd3d11h264enc`, e nao `nvh264enc`: o segundo e o modo CUDA da NVIDIA e RECUSA
    // quadro em memoria D3D11. Com ele o cano nem liga -- e o GStreamer nao trata isso
    // como erro, apenas avisa e monta sem o ramo de video.
    private val ENCODERS =
        System.getProperty("astra.encoder")?.let { listOf(it) }
            ?: listOf("nvd3d11h264enc", "qsvh264enc", "amfh264enc", "mfh264enc")

    // `kbpsReal` e o que SAIU comprimido; `kbpsPedido` e o que se pediu ao encoder. Os
    // dois juntos, e nao so o segundo: e a diferenca entre eles que diz se o encoder esta
    // obedecendo. Um sozinho nao responde nada.
    data class Estatisticas(
        val fpsCaptura: Int,
        val fpsEnvio: Int,
        val limite: String,
        val kbpsReal: Int = 0,
        val kbpsPedido: Int = 0,
    )

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

    // QUADROS CONTADOS NO PROPRIO CANO, e nao perguntados a ninguem.
    //
    // A linha de status dizia "captura 60fps . envio 0fps", e os dois numeros estavam
    // errados de maneiras diferentes: a captura era o numero do PRESET repetido de volta
    // (nunca foi medida), e o envio saia de uma varredura por texto no relatorio do
    // webrtcbin, que devolvia zero sem dizer se era "nao esta enviando" ou "nao achei o
    // campo". Duas mentiras, e a segunda escondia a primeira.
    //
    // Contar buffer na porta e a unica resposta honesta pra "esta fluindo?": formato
    // negociado nao prova quadro entregue -- uma porta ganha `currentCaps` de um EVENTO
    // de negociacao, com zero quadro produzido.
    private val quadrosCapturados = java.util.concurrent.atomic.AtomicLong(0)
    private val quadrosCodificados = java.util.concurrent.atomic.AtomicLong(0)
    // Bytes que sairam comprimidos. Contar quadro diz se ESTA FLUINDO; contar byte diz
    // QUANTO esta custando -- e sem esse segundo numero nao da pra saber se um pedido de
    // bitrate foi obedecido. Pedir e nao conferir e o mesmo que nao pedir.
    private val bytesCodificados = java.util.concurrent.atomic.AtomicLong(0)
    private var marcoQuando = 0L
    private var marcoCapturados = 0L
    private var marcoCodificados = 0L
    private var marcoBytes = 0L

    // Contador por elo, so pra depuracao (`-Dastra.contarelos`). Ver `elosAgora()`.
    private val elos = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()

    fun elosAgora(): String = elos.entries.sortedBy { it.key }.joinToString(" . ") { "${it.key}=${it.value.get()}" }

    // O encoder do ramo atual e o ultimo teto pedido a ele, em kbps.
    private var encoderVivo: Element? = null
    @Volatile private var tetoPedido = 0

    // PEDE um teto novo ao encoder. Devolve false se nao havia a quem pedir.
    //
    // "Pede" e nao "define", e a diferenca nao e modestia: o `qsvh264enc` NAO declara a
    // propriedade como alteravel com o cano andando (o `nvd3d11h264enc` declara). Sem essa
    // marca, o GStreamer aceita a escrita e o elemento pode simplesmente ignora-la ate a
    // proxima renegociacao. Por isso quem chama tem que CONFERIR no `kbpsReal` das
    // estatisticas se o pedido virou realidade -- um laco de controle mandando numero que
    // ninguem obedece e pior do que nao ter laco: ele acha que consertou.
    fun pedirBitrate(kbps: Int): Boolean {
        val e = encoderVivo ?: return false
        tetoPedido = kbps
        return runCatching { e.set("bitrate", kbps) }.isSuccess
    }

    val encoderDisponivel: String?
        get() = encoderEscolhido ?: ENCODERS.firstOrNull { existe(it) }

    // Qual encoder de fato comprimiu, descoberto na primeira transmissao da sessao.
    // Depois disso ninguem mais paga o custo de descobrir.
    @Volatile private var encoderEscolhido: String? = null

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
            // O quadro ja NASCE na placa: nada a subir, o d3d11convert recebe direto.
            val tela = ElementFactory.make("d3d11screencapturesrc", "tela") ?: return@publicarVideo null
            runCatching { tela.set("monitor-index", monitor) }
            // O PONTEIRO DO MOUSE ENTRA NA IMAGEM. O padrao deste elemento e nao
            // desenhar, e quem assiste alguem mostrando a tela precisa saber pra onde
            // a pessoa esta apontando -- sem o ponteiro, "olha aqui" nao aponta nada.
            // O caminho de sempre (ddagrab) ja desenhava; a falta era regressao.
            runCatching { tela.set("show-cursor", true) }
            listOf(tela)
        }

    // A CAMERA NAO E A TELA, e o cano precisava saber disso.
    //
    // Esta funcao existia e NAO PODIA ter funcionado nunca: ela entregava o `mfvideosrc`
    // direto no `d3d11convert`, e as portas dos dois nao se encontram. Conferido no
    // gst-inspect do proprio pacote:
    //
    //   mfvideosrc   src  -> video/x-raw (memoria principal) | image/jpeg
    //   d3d11convert sink -> video/x-raw(memory:D3D11Memory) SO
    //
    // Sem interseccao nao ha ligacao, e `publicarVideo` devolvia false calado. A diferenca
    // e de origem, nao de configuracao: quadro de tela nasce NA PLACA (o capturador le a
    // area de trabalho de onde ela ja mora), quadro de webcam nasce na MEMORIA PRINCIPAL
    // (o cabo USB entrega ali). Pra webcam a subida pra placa e inevitavel -- o que se
    // evita e o vaivem, e por isso ela sobe UMA vez e nao desce mais ate a previa.
    //
    // As tres pecas novas, cada uma por um motivo:
    //   . capsfilter video/x-raw -- fecha a porta pro `image/jpeg`. Camera que oferece os
    //     dois entrega JPEG na maior resolucao, e o pacote enxuto NAO leva decodificador
    //     de JPEG: a negociacao acharia um caminho que nao existe.
    //   . d3d11upload -- a subida propriamente dita.
    //
    // A CADENCIA NAO E FIXADA (fps=0 la embaixo), e isso e obrigacao e nao preguica.
    // Webcam anuncia 30000/1001 (29,97) tanto quanto 30/1, e um filtro pedindo 30/1 exato
    // mataria a negociacao por causa de um milesimo. O conserto natural seria um
    // `videorate` -- que NAO EXISTE NESTE PACOTE (conferido: o pacote leva 23 plugins e
    // esse nao esta entre eles). Entao a camera manda a cadencia dela, e o encoder aceita.
    //
    // `nomeDoAparelho` existe porque quem tem duas cameras escolhe uma na interface, e o
    // `mfvideosrc` sem endereco abre a primeira que achar -- a pessoa apontaria a webcam
    // do rosto e transmitiria a do teto.
    fun publicarCamera(cid: String, nomeDoAparelho: String?, largura: Int, altura: Int): Boolean =
        publicarVideo(cid, largura, altura, 0, 2_000_000) {
            val cam = ElementFactory.make("mfvideosrc", "camera")
            if (!nomeDoAparelho.isNullOrBlank()) runCatching { cam.set("device-name", nomeDoAparelho) }
            val cru = ElementFactory.make("capsfilter", "cruDaCamera")
            val sobe = ElementFactory.make("d3d11upload", "sobeCamera")
            cru.set("caps", Caps.fromString("video/x-raw,width=$largura,height=$altura"))
            listOf(cam, cru, sobe)
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
        // A ENTRADA E UMA LISTA, e nao um elemento: quem produz o quadro decide quantas
        // pecas precisa ate entregar em memoria de placa. Tela entrega uma; camera entrega
        // quatro (ver `publicarCamera`). Daqui pra frente o cano e o mesmo pros dois.
        criarFonte: () -> List<Element>?,
    ): Boolean {
        if (ramoVideo.isNotEmpty()) pararVideo()

        // ESCOLHE O ENCODER MEDINDO, e nao por lista de preferencia.
        //
        // A lista dizia `nvd3d11h264enc` primeiro, e nesta maquina ele NAO CODIFICA NADA:
        // um quadro entra, nenhum sai, o cano congela. A causa e que o notebook tem duas
        // placas -- a tela e desenhada pela Intel, e e no aparelho D3D11 DELA que o quadro
        // capturado nasce. A NVIDIA e outro aparelho: ela nao consegue ler aquela textura,
        // e a queixa que sobrava no log era so um `assertion 'GST_IS_D3D11_DEVICE'
        // failed`, sem erro no barramento e sem nada na tela.
        //
        // Medido lado a lado, mesmo cano, so trocando o encoder:
        //     qsvh264enc      captura 60 fps . comprimido 60 fps  (oito segundos firmes)
        //     nvd3d11h264enc  captura  1     . comprimido  0      (morto no primeiro)
        //
        // POR QUE MEDIR EM VEZ DE SO INVERTER A LISTA: inverter conserta este notebook e
        // quebra a maquina de mesa onde a NVIDIA desenha a tela -- la a Intel e que seria
        // a placa errada. Nenhuma ordem fixa esta certa nas duas. O que vale nas duas e
        // "usa quem realmente comprime", e isso e uma pergunta com resposta medida: sobe o
        // ramo, olha se saiu quadro, e se nao saiu tenta o proximo. Custa ate meio segundo
        // uma vez por sessao -- o acerto fica guardado.
        val enc = encoderQueComprime() ?: return false
        return montarRamo(cid, largura, altura, fps, bitrate, enc, criarFonte)
    }

    private fun existe(nome: String) =
        runCatching { ElementFactory.find(nome) != null }.getOrDefault(false)

    // Escolhe o encoder pela PLACA QUE DESENHA A TELA, e essa e a regra inteira.
    //
    // Um quadro de captura de tela nasce no aparelho D3D11 da placa que desenha o monitor,
    // e um encoder so consegue ler textura da PROPRIA placa. Notebook com duas placas --
    // Intel desenhando a tela, NVIDIA so renderizando -- e o caso comum, e nele a NVENC
    // recebe uma textura que nao e dela e nao devolve nada: um quadro entra, nenhum sai, o
    // cano congela e o unico vestigio e um `assertion 'GST_IS_D3D11_DEVICE' failed`.
    //
    // O GSTREAMER JA DIZ QUEM E QUEM, no nome longo de cada elemento. O decodificador sem
    // sufixo e sempre o do adaptador 0, que e o do monitor:
    //
    //     d3d11h264dec         "... H.264 Intel(R) UHD Graphics Decoder"      <- a tela
    //     d3d11h264device1dec  "... H.264 NVIDIA GeForce RTX 4060 ... Decoder"
    //     qsvh264enc           "... Intel(R) UHD Graphics H.264 Encoder"      <- combina
    //     nvd3d11h264enc       "NVENC H.264 Video Encoder Direct3D11 Mode"
    //
    // Entao basta perguntar de que fabricante e a placa da tela e preferir o encoder do
    // mesmo. Nao e adivinhacao de plataforma: e ler a resposta que ja esta escrita.
    //
    // TENTEI ANTES por medicao -- subir um cano de prova e ver se saia quadro comprimido.
    // Nao presta: o resultado MUDOU entre duas provas na mesma maquina, porque o GStreamer
    // guarda os aparelhos D3D11 que ja criou e a prova cai num ou noutro conforme a ordem.
    // Teste que responde diferente pra mesma pergunta nao decide nada.
    //
    // Se nada casar, segue a ordem de sempre -- maquina de uma placa so nao tem conflito.
    //
    // A ESCOLHA DO DONO (Configuracoes > Desempenho) entra aqui, mas NAO PODE emudecer a
    // transmissao. Ela vale enquanto a placa escolhida for a que desenha a tela; se for a
    // outra, a captura de tela continua na placa certa -- nao ha o que negociar, o quadro
    // nasce onde nasce. A tela de configuracoes ja diz isso ao lado da opcao, entao aqui
    // nao ha surpresa: ha coerencia com o que estava escrito.
    private fun encoderQueComprime(): String? {
        encoderEscolhido?.let { return it }
        val candidatos = ENCODERS.filter { existe(it) }
        if (candidatos.isEmpty()) return null
        val marca = marcaDaTela()
        val pedida = placaPedida?.takeIf { it == marca }
        val alvo = pedida ?: marca
        val escolhido = candidatos.firstOrNull { alvo != null && marcaDe(nomeLongo(it)) == alvo }
            ?: candidatos.first()
        encoderEscolhido = escolhido
        VoiceLog.nota(
            "transporte novo: a tela e desenhada por ${marca ?: "placa desconhecida"}; " +
                "comprimindo com '$escolhido' (tinha ${candidatos.joinToString()})",
        )
        return escolhido
    }

    private fun nomeLongo(fabrica: String) =
        runCatching { ElementFactory.find(fabrica)?.longName }.getOrNull().orEmpty()

    // O decodificador D3D11 SEM sufixo de aparelho e o do adaptador 0 -- o do monitor.
    private fun marcaDaTela(): String? =
        listOf("d3d11h264dec", "d3d11vp9dec", "d3d11h265dec")
            .firstNotNullOfOrNull { marcaDe(nomeLongo(it)) }

    private fun marcaDe(texto: String): String? {
        val t = texto.lowercase()
        return when {
            t.contains("nvidia") || t.contains("geforce") || t.contains("nvenc") -> "NVIDIA"
            t.contains("intel") || t.contains("quick sync") -> "Intel"
            t.contains("amd") || t.contains("radeon") -> "AMD"
            else -> null
        }
    }

    private fun montarRamo(
        cid: String,
        largura: Int,
        altura: Int,
        fps: Int,
        bitrate: Int,
        enc: String,
        criarFonte: () -> List<Element>?,
    ): Boolean {
        val p = pipeline ?: return false
        val b = bin ?: return false
        // `ElementFactory.make` LANCA quando o plugin nao esta no pacote -- nao devolve
        // nulo. Descobri isso pelo pior caminho: pedi um `videorate` que o pacote enxuto
        // nao leva, e a excecao subiu por `publicarCamera` ate quem chamou, que nao
        // esperava nenhuma. Fonte que nao pode ser montada tem que virar "false" aqui, do
        // mesmo jeito que maquina sem encoder -- um caminho a menos, nunca uma call
        // quebrada.
        val entrada = runCatching { criarFonte() }
            .onFailure { VoiceLog.nota("transporte novo: nao montei a fonte de video (${it.message})") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return false
        val fonte = entrada.first()
        val conv = ElementFactory.make("d3d11convert", "conv") ?: return false
        val formato = ElementFactory.make("capsfilter", "formato") ?: return false
        val encoder = ElementFactory.make(enc, "encoder") ?: return false
        val parser = ElementFactory.make("h264parse", "parser") ?: return false
        val pay = ElementFactory.make("rtph264pay", "pay") ?: return false
        val formatoRtp = ElementFactory.make("capsfilter", "formatoRtp") ?: return false

        // `fps = 0` quer dizer "a fonte manda". Fixar cadencia so faz sentido pra tela, que
        // e capturada sob demanda e entrega o que se pedir; webcam entrega a cadencia dela
        // e nao negocia -- pedir 30/1 exato de quem oferece 30000/1001 derruba a
        // negociacao inteira por causa de um milesimo.
        val cadencia = if (fps > 0) ",framerate=$fps/1" else ""
        formato.set(
            "caps",
            Caps.fromString("video/x-raw(memory:D3D11Memory),format=NV12,width=$largura,height=$altura$cadencia"),
        )
        runCatching { encoder.set("bitrate", bitrate / 1000) } // kbps na maioria dos encoders
        aoVivo(encoder, enc)
        encoderVivo = encoder
        tetoPedido = bitrate / 1000
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

        val principal = entrada + listOf(conv, formato) +
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

            // Os dois pontos que decidem se a transmissao existe: o quadro SAINDO da
            // fonte, e o quadro JA COMPRIMIDO saindo do parser. Se o primeiro anda e o
            // segundo nao, o encoder e quem esta parado -- e essa distincao e a diferenca
            // entre procurar na captura e procurar na placa.
            //
            // O parser, e nao o payloader: o rtph264pay quebra um quadro em varios pacotes
            // RTP, e contar pacote como quadro daria um numero inflado e sem sentido.
            quadrosCapturados.set(0)
            quadrosCodificados.set(0)
            bytesCodificados.set(0)
            elos.clear() // por transmissao, senao a leitura soma as anteriores e mente
            marcoQuando = 0L
            marcoCapturados = 0L
            marcoCodificados = 0L
            marcoBytes = 0L
            // ELO A ELO, so quando o banco de testes pede. Em producao sao dois contadores;
            // com `astra.contarelos` cada porta do ramo ganha o seu, e a primeira que
            // parar de crescer e o elemento que esta segurando o cano.
            if (System.getProperty("astra.contarelos") != null) {
                principal.forEach { elo ->
                    val nome = elo.name
                    runCatching {
                        elo.getStaticPad("src")?.addProbe(PadProbeType.BUFFER) { _, _ ->
                            elos.computeIfAbsent(nome) { java.util.concurrent.atomic.AtomicLong(0) }
                                .incrementAndGet()
                            PadProbeReturn.OK
                        }
                    }
                }
            }
            runCatching {
                fonte.getStaticPad("src")?.addProbe(PadProbeType.BUFFER) { _, _ ->
                    quadrosCapturados.incrementAndGet(); PadProbeReturn.OK
                }
                parser.getStaticPad("src")?.addProbe(PadProbeType.BUFFER) { _, info ->
                    quadrosCodificados.incrementAndGet()
                    // O TAMANHO do quadro comprimido, e nao so a contagem. E o unico jeito
                    // honesto de saber se um pedido de bitrate foi ATENDIDO: o valor da
                    // propriedade conta o que se pediu, nao o que saiu.
                    //
                    // Este binding nao expoe o tamanho do buffer, entao e preciso mapear.
                    // O custo e o de devolver um ponteiro -- o ramo da previa ja mapeia na
                    // mesma cadencia e ainda COPIA o quadro inteiro; isto aqui e uma
                    // fracao disso. O `unmap` mora no mesmo bloco que o `map`: mapear sem
                    // soltar vaza memoria nativa, que ja custou caro neste app.
                    val buf = info.buffer
                    if (buf != null) {
                        runCatching {
                            val bytes = buf.map(false)
                            if (bytes != null) bytesCodificados.addAndGet(bytes.remaining().toLong())
                            buf.unmap()
                        }
                    }
                    PadProbeReturn.OK
                }
            }

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
            VoiceLog.nota("transporte novo: o ramo de video nao subiu (${e.message})")
            runCatching { p.removeMany(*elementos.toTypedArray()) }
            false
        }
    }

    // POE O ENCODER EM MODO AO VIVO. Sem isto a transmissao NAO EXISTE -- e nao "fica
    // ruim": para de sair depois de um quadro.
    //
    // ISTO ERA O DEFEITO DO "envia 0fps". Medido elo a elo, com a conexao viva:
    //
    //     tela=5 . conv=5 . formato=5 . encoder=1 . parser=1 . pay=1 . formatoRtp=1
    //
    // A fonte entregou cinco quadros, o encoder devolveu UM, e o cano inteiro congelou --
    // pra sempre. O que sai do encoder atravessa o resto sem tropeco (parser, payloader e
    // saida receberam o mesmo 1), entao o `webrtcbin` nunca foi o culpado, apesar de ser
    // sempre o primeiro suspeito.
    //
    // POR QUE ELE TRAVA. Encoder de hardware, na configuracao de fabrica, encoda pra
    // ARQUIVO: usa quadros B e olha alguns quadros a frente antes de decidir. Pra isso ele
    // SEGURA os quadros de entrada. So que os quadros de entrada nao sao dele -- sao do
    // conjunto que o capturador de tela empresta, e esse conjunto tem cerca de cinco. O
    // encoder segurou os cinco, a fonte pediu o sexto, nao havia sexto, e ela ficou parada
    // esperando um quadro que so voltaria se o encoder soltasse -- o que so aconteceria se
    // chegasse mais quadro. Um esperando o outro, calados, sem erro no barramento.
    //
    // Nada disso deveria existir numa chamada ao vivo de qualquer jeito: quadro B atrasa a
    // imagem de proposito, e atraso e exatamente o que nao se pode gastar aqui. A
    // configuracao certa pra tempo real e a mesma que conserta o travamento.
    //
    // Cada fabricante com seus nomes; `runCatching` em cada um porque versao de plugin
    // muda propriedade, e um nome que sumiu nao pode derrubar a transmissao inteira.
    private fun aoVivo(encoder: Element, nome: String) {
        when (nome) {
            "qsvh264enc" -> {
                runCatching { encoder.set("low-latency", true) }
                runCatching { encoder.set("b-frames", 0) }
                runCatching { encoder.set("ref-frames", 1) }
                runCatching { encoder.set("target-usage", 7) } // 7 = mais rapido
                runCatching { encoder.set("rate-control", 1) } // 1 = cbr, o que a rede pede
            }
            "nvd3d11h264enc", "nvautogpuh264enc", "nvh264enc" -> {
                runCatching { encoder.set("zerolatency", true) }
                runCatching { encoder.set("bframes", 0) }
                runCatching { encoder.set("rc-lookahead", 0) }
                runCatching { encoder.set("rc-mode", 2) } // 2 = cbr
                runCatching { encoder.set("tune", 3) } // 3 = ultra-low-latency
            }
            "amfh264enc" -> {
                runCatching { encoder.set("usage", 1) } // 1 = ultra-low-latency
                runCatching { encoder.set("rate-control", 3) } // 3 = cbr
            }
            "mfh264enc" -> {
                runCatching { encoder.set("low-latency", true) }
                runCatching { encoder.set("bframes", 0) }
            }
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

    // Quadros por segundo MEDIDOS na porta, no intervalo desde a leitura anterior.
    //
    // SUBSTITUIU a consulta `get-stats` ao webrtcbin, e a troca conserta tres coisas de
    // uma vez:
    //
    //   . o numero de envio saia de uma varredura por texto no relatorio e devolvia zero
    //     sem distinguir "nao esta enviando" de "nao achei o campo" -- foi o que fez o
    //     dono ver "envio 0fps" sem ninguem saber o que aquilo significava;
    //   . o de captura nem era medido: quem chamava repetia o numero do PRESET de volta,
    //     entao "captura 60fps" apareceria igual com o cano inteiro parado;
    //   . a promessa da consulta precisava ser mantida viva na mao pra o coletor nao
    //     derrubar o processo, e esse risco some junto com a consulta.
    //
    // Contador na porta e mais barato (um incremento por quadro), nao tem prazo pra
    // estourar, e responde a pergunta certa: quadro ENTREGUE, nao formato negociado.
    fun estatisticas(): Estatisticas? {
        if (ramoVideo.isEmpty()) return null
        val agora = System.nanoTime()
        val cap = quadrosCapturados.get()
        val cod = quadrosCodificados.get()
        val bytes = bytesCodificados.get()
        val antes = marcoQuando
        val dCap = cap - marcoCapturados
        val dCod = cod - marcoCodificados
        val dBytes = bytes - marcoBytes
        marcoQuando = agora
        marcoCapturados = cap
        marcoCodificados = cod
        marcoBytes = bytes
        // A primeira leitura nao tem intervalo com que dividir: ela so finca o marco.
        if (antes == 0L) return null
        val segundos = (agora - antes) / 1_000_000_000.0
        if (segundos <= 0.0) return null
        return Estatisticas(
            fpsCaptura = (dCap / segundos).roundToInt(),
            fpsEnvio = (dCod / segundos).roundToInt(),
            // Nao ha equivalente ao `qualityLimitationReason` do libwebrtc aqui. Os dois
            // numeros ja dizem o essencial: captura andando com envio parado aponta pra
            // placa; os dois parados apontam pra captura.
            limite = "none",
            kbpsReal = (dBytes * 8 / 1000.0 / segundos).roundToInt(),
            kbpsPedido = tetoPedido,
        )
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
