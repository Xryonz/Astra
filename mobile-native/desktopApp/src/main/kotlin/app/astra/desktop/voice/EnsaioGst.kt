package app.astra.desktop.voice

import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Buffer
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// BANCO DE TESTES do transporte novo. Roda negociacao WebRTC de verdade FORA do app:
//   gradlew :desktopApp:ensaioGst
//
// Existe porque as pecas que faltavam confirmar falham todas do mesmo jeito -- a call
// sobe e ninguem se ouve -- e o GStreamer nao levanta excecao quando um elemento falha:
// ele posta a queixa no BARRAMENTO e o cano segue vivo, so que sem aquele ramo.
//
// JA PROVADO (e corrigido no app):
//   . nvh264enc e o modo CUDA e RECUSA quadro em memoria D3D11 -> nvd3d11h264enc
//   . Gst.init sem versao pede a BASELINE (1.8) e tranca setLocalDescription
//   . encoding-params=2 com opusenc mono -> "Internal data stream error" no appsrc
//   . remendar o SDP funciona: o webrtcbin aceita a oferta reescrita
//   . request-aux-sender conecta, get-stats responde, ICE junta 28 caminhos
//
// O QUE ESTA RODADA MEDE, e e a ultima duvida do desenho:
//
//   O LiveKit da UMA conexao de publicacao pra tudo que sai. O microfone vive nela a
//   call inteira; a tela entra e sai no meio. Ou seja: o ramo de video tem que nascer e
//   morrer com o cano ANDANDO, sem levar o audio junto. Se isso nao der, comecar a
//   transmitir corta a voz de quem transmite -- e ai o caminho novo nao serve.
object EnsaioGst

private const val CID_AUDIO = "mic-teste01"
private const val CID_VIDEO = "screen-teste1"

private class RamoDeVideo(val elementos: List<Element>, val pad: Pad)

fun main() {
    Thread({
        Thread.sleep(120_000)
        p("")
        p("!!! ESTOUROU O TEMPO (120s) -- alguma etapa pendurou. A ultima linha acima e onde.")
        System.out.flush()
        Runtime.getRuntime().halt(2)
    }, "cao-de-guarda").apply { isDaemon = true; start() }

    linha("preparando")
    if (!GStreamerPack.iniciarGst()) {
        p("  FALHOU: pacote nao instalado ou GStreamer nao carregou")
        return
    }
    p("  ${Gst.getVersionString()}")
    val enc = listOf("nvd3d11h264enc", "qsvh264enc", "amfh264enc", "mfh264enc")
        .firstOrNull { runCatching { ElementFactory.find(it) != null }.getOrDefault(false) }
    if (enc == null) {
        p("  FALHOU: sem encoder de hardware")
        return
    }
    p("  encoder: $enc")

    // --- o cano nasce SO com audio, como numa call em que ninguem transmite ---
    linha("1. cano so com o microfone")
    val descricao =
        "webrtcbin name=envio bundle-policy=max-bundle latency=0 " +
            "appsrc name=mic is-live=true format=time do-timestamp=true " +
            "caps=audio/x-raw,format=S16LE,rate=48000,channels=1,layout=interleaved " +
            "! audioconvert ! audioresample ! opusenc bitrate=64000 " +
            "! rtpopuspay pt=111 " +
            "! application/x-rtp,media=audio,encoding-name=OPUS,payload=111,clock-rate=48000 " +
            "! envio."
    val pipeline = runCatching { Gst.parseLaunch(descricao) as Pipeline }.getOrElse {
        p("  FALHOU ao montar: ${it.javaClass.simpleName} ${it.message}")
        return
    }
    val bin = pipeline.getElementByName("envio") as WebRTCBin

    pipeline.bus.connect(Bus.ERROR { fonte, _, msg -> p("  [ERRO] ${fonte.name}: $msg") })
    pipeline.bus.connect(Bus.WARNING { fonte, _, msg -> p("  [aviso] ${fonte.name}: $msg") })

    val candidatos = ConcurrentLinkedQueue<String>()
    bin.connect(WebRTCBin.ON_ICE_CANDIDATE { _, c -> candidatos.add(c) })
    pendurarBwe(bin)
    runCatching { bin.getStaticPad("sink_0")?.set("msid", CID_AUDIO) }

    pipeline.play()
    alimentarSilencio(pipeline)
    p("  estado: ${pipeline.getState(5_000_000_000L)}")
    p("  formato do audio: ${if (esperarFormato(bin, "sink_0", 8000) != null) "negociado" else "NAO NEGOCIOU"}")

    linha("2. oferta com o microfone so")
    mostrarMidias(pedirOferta(bin) ?: return encerrar(pipeline))

    // --- a tela entra COM O CANO ANDANDO ---
    linha("3. acrescentando a tela sem parar o audio")
    val ramo = acrescentarVideo(pipeline, bin, enc)
    if (ramo == null) {
        p("  FALHOU: nao consegui montar o ramo de video")
        return encerrar(pipeline)
    }
    p("  ramo ligado no pad ${ramo.pad.name}")
    runCatching { ramo.pad.set("msid", CID_VIDEO) }
    p("  formato do video: ${if (esperarFormato(bin, ramo.pad.name, 10000) != null) "negociado" else "NAO NEGOCIOU"}")
    p("  audio continua? ${if (esperarFormato(bin, "sink_0", 1000) != null) "SIM" else "PERDEU O FORMATO"}")

    linha("4. oferta com microfone + tela")
    val comVideo = pedirOferta(bin) ?: return encerrar(pipeline)
    mostrarMidias(comVideo)

    linha("5. remendando o msid pro formato que o LiveKit ja aceita")
    val remendado = remendarMsid(comVideo)
    remendado.lineSequence().map { it.trim() }.filter { it.startsWith("a=msid") || it.contains(" msid:") }
        .forEach { p("    $it") }
    runCatching {
        bin.setLocalDescription(WebRTCSessionDescription(WebRTCSDPType.OFFER, SDPMessage().apply { parseBuffer(remendado) }))
    }.onSuccess { p("  aceito pelo webrtcbin") }.onFailure { p("  RECUSADO: ${it.message}") }

    Thread.sleep(3000)
    p("  candidatos de rede: ${candidatos.size}")

    linha("6. estatisticas")
    lerStats(bin)

    // --- a tela sai, e o audio tem que sobreviver ---
    linha("7. tirando a tela sem parar o audio")
    tirarVideo(pipeline, bin, ramo)
    Thread.sleep(1500)
    p("  audio continua? ${if (esperarFormato(bin, "sink_0", 2000) != null) "SIM" else "PERDEU O FORMATO"}")
    p("  estado do cano: ${pipeline.getState(3_000_000_000L)}")

    linha("8. oferta depois de tirar a tela")
    pedirOferta(bin)?.let { mostrarMidias(it) }

    encerrar(pipeline)
    linha("fim")
}

// Monta o ramo de video e pendura no webrtcbin com o cano ANDANDO.
//
// Cada elo e conferido. O parseLaunch engole falha de ligacao com um aviso (foi como o
// encoder errado passou batido duas rodadas), entao aqui a ligacao e explicita e um
// `false` interrompe na hora, no elo exato.
private fun acrescentarVideo(pipeline: Pipeline, bin: WebRTCBin, enc: String): RamoDeVideo? {
    val fonte = ElementFactory.make("d3d11screencapturesrc", "tela") ?: return null
    val conv = ElementFactory.make("d3d11convert", "conv") ?: return null
    val filtro = ElementFactory.make("capsfilter", "formato") ?: return null
    val encoder = ElementFactory.make(enc, "encoder") ?: return null
    val parser = ElementFactory.make("h264parse", "parser") ?: return null
    val pay = ElementFactory.make("rtph264pay", "pay") ?: return null
    val filtroRtp = ElementFactory.make("capsfilter", "formatoRtp") ?: return null

    filtro.set("caps", Caps.fromString("video/x-raw(memory:D3D11Memory),format=NV12,width=1280,height=720,framerate=30/1"))
    parser.set("config-interval", -1)
    pay.set("pt", 96)
    pay.set("config-interval", -1)
    filtroRtp.set("caps", Caps.fromString("application/x-rtp,media=video,encoding-name=H264,payload=96,clock-rate=90000"))

    val elementos = listOf(fonte, conv, filtro, encoder, parser, pay, filtroRtp)
    pipeline.addMany(*elementos.toTypedArray())
    for (i in 0 until elementos.size - 1) {
        if (!elementos[i].link(elementos[i + 1])) {
            p("  nao ligou: ${elementos[i].name} -> ${elementos[i + 1].name}")
            return null
        }
    }

    val pad = bin.getRequestPad("sink_%u") ?: run { p("  o webrtcbin nao deu pad"); return null }
    val saida = filtroRtp.getStaticPad("src") ?: return null
    // Pad.link LANCA em vez de devolver codigo -- ao contrario de Element.link, que
    // devolve boolean. Duas convencoes na mesma biblioteca; confundir uma com a outra
    // e como o erro passaria calado.
    runCatching { saida.link(pad) }.onFailure {
        p("  nao ligou o ramo no webrtcbin: ${it.message}")
        return null
    }
    // O CANO PARA, O RAMO ENTRA, O CANO VOLTA A ANDAR.
    //
    // Herdar o tempo-base e chamar syncStateWithParent NAO BASTA -- medido: a fonte
    // fica em PLAYING, negocia formato e entrega ZERO quadro em tres segundos. Fonte ao
    // vivo acrescentada a um cano que ja anda simplesmente nao arranca, e nao reclama.
    //
    // Passar pelo repouso poe o ramo novo pra nascer junto com o resto, que e o unico
    // caminho que a gente ja viu funcionar. O preco e um engasgo curto no audio ao
    // comecar e ao parar a transmissao -- a conexao, o ICE e a criptografia SOBREVIVEM
    // ao repouso (nao e reconexao), entao o custo e o engasgo e nada alem dele.
    val t0 = System.currentTimeMillis()
    pipeline.setState(State.PAUSED)
    pipeline.getState(3_000_000_000L)
    elementos.forEach { it.syncStateWithParent() }
    pipeline.setState(State.PLAYING)
    pipeline.getState(5_000_000_000L)
    // O tamanho do engasgo importa: e uma regressao visivel (hoje comecar a transmitir
    // nao mexe na voz). Medir e o que separa "custa pouco" de "custa pouco, eu acho".
    p("  o audio ficou parado por ${System.currentTimeMillis() - t0} ms")
    elementos.forEach { e ->
        val st = runCatching { e.getState(3_000_000_000L) }.getOrNull()
        if (st != State.PLAYING) p("  ${e.name} ficou em $st")
    }

    // FORMATO NEGOCIADO NAO E QUADRO ENTREGUE, e confundir os dois me custou duas
    // rodadas. Um pad ganha `currentCaps` quando um EVENTO de formato passa por ele --
    // e isso acontece na negociacao, sem um unico quadro ter sido produzido. Contar
    // buffer e a unica pergunta que separa "a fonte nao esta gerando" de "o encoder
    // esta recebendo e engolindo".
    val contagem = elementos.associate { e ->
        val n = java.util.concurrent.atomic.AtomicInteger(0)
        runCatching {
            e.getStaticPad("src")?.addProbe(org.freedesktop.gstreamer.PadProbeType.BUFFER) { _, _ ->
                n.incrementAndGet()
                org.freedesktop.gstreamer.PadProbeReturn.OK
            }
        }
        e.name to n
    }

    // ONDE o quadro para, em vez de so "nao chegou".
    //
    // Um pad so ganha formato depois que passa dado por ele. Perguntar elo a elo
    // transforma "o video nao negociou" em "parou entre X e Y" -- que e a diferenca
    // entre consertar e adivinhar. Sem isto eu ja tinha trocado o encoder e mexido no
    // relogio no chute, e nenhum dos dois era o problema.
    Thread.sleep(3000)
    p("  quadros SAINDO de cada elo em 3s (e o formato negociado):")
    elementos.forEach { e ->
        val c = runCatching { e.getStaticPad("src")?.currentCaps?.toString() }.getOrNull()
        p("    ${e.name.padEnd(12)} ${(contagem[e.name]?.get() ?: -1).toString().padStart(4)} quadros  ${if (c == null) "sem formato" else "formato ok"}")
    }
    return RamoDeVideo(elementos, pad)
}

// Desmonta o ramo.
//
// A PRIMEIRA VERSAO DISTO CORROMPEU A MEMORIA NATIVA (0xC0000374) e derrubou o processo.
// Ela desligava os elementos com o cano ANDANDO e quadros em voo -- soltar a memoria de
// um elemento que ainda esta processando e exatamente o mesmo erro que o `pushFrame`
// depois do `dispose` na captura de tela, um andar abaixo.
//
// E o mais instrutivo: essa remocao PASSOU nas rodadas anteriores. So que naquelas o
// ramo nunca produzira um unico quadro -- nao havia nada em voo pra atropelar. O defeito
// estava escondido atras do outro defeito, e so apareceu quando o video comecou a
// funcionar. Teste que passa porque a funcionalidade nao existe nao prova nada.
//
// Agora: o cano DESCANSA primeiro (todo mundo para de processar), depois desliga. Mesmo
// caminho da entrada, e pelo mesmo motivo.
private fun tirarVideo(pipeline: Pipeline, bin: WebRTCBin, ramo: RamoDeVideo) {
    val t0 = System.currentTimeMillis()
    pipeline.setState(State.PAUSED)
    pipeline.getState(3_000_000_000L)
    runCatching { ramo.elementos.last().getStaticPad("src")?.unlink(ramo.pad) }
    runCatching { bin.releaseRequestPad(ramo.pad) }
    ramo.elementos.forEach {
        runCatching { it.setState(State.NULL) }
        runCatching { it.getState(2_000_000_000L) }
    }
    runCatching { pipeline.removeMany(*ramo.elementos.toTypedArray()) }
    pipeline.setState(State.PLAYING)
    pipeline.getState(5_000_000_000L)
    p("  o audio ficou parado por ${System.currentTimeMillis() - t0} ms")
}

private fun pedirOferta(bin: WebRTCBin): String? {
    val pronto = CountDownLatch(1)
    val caixa = arrayOfNulls<String>(1)
    bin.createOffer { desc ->
        caixa[0] = runCatching { desc.getSDPMessage().toString() }.getOrNull()
        pronto.countDown()
    }
    if (!pronto.await(10, TimeUnit.SECONDS)) {
        p("  a oferta nao ficou pronta em 10s")
        return null
    }
    return caixa[0]
}

// Porta 0 SOZINHA nao quer dizer nada: em max-bundle a regra manda oferecer as linhas
// seguintes com porta 0 + a=bundle-only. O que distingue midia viva de midia morta e o
// a=bundle-only estar la.
private fun mostrarMidias(sdp: String) {
    val linhas = sdp.lineSequence().map { it.trim() }.toList()
    var atual: String? = null
    linhas.forEach { l ->
        when {
            l.startsWith("m=") -> {
                atual = l
                p("  $l")
            }
            l == "a=bundle-only" -> p("      (agrupada -- porta 0 aqui e o esperado)")
            l == "a=inactive" -> p("      !!! INATIVA")
            l.startsWith("a=rtpmap:") -> p("      $l")
        }
    }
    if (atual == null) p("  (nenhuma midia)")
}

private fun esperarFormato(bin: WebRTCBin, pad: String, prazoMs: Long): String? {
    val fim = System.currentTimeMillis() + prazoMs
    while (System.currentTimeMillis() < fim) {
        val c = runCatching { bin.getStaticPad(pad)?.currentCaps?.toString() }.getOrNull()
        if (!c.isNullOrBlank()) return c
        Thread.sleep(100)
    }
    return null
}

private interface AuxSender : GstAPI.GstCallback {
    fun callback(bin: Element, transporte: Element): Element?
}

private fun pendurarBwe(bin: WebRTCBin): Boolean = runCatching {
    val alvo = object : AuxSender {
        override fun callback(b: Element, transporte: Element): Element? {
            val bwe = ElementFactory.make("rtpgccbwe", null)
            p("    [sinal] request-aux-sender -> ${if (bwe != null) "rtpgccbwe entregue" else "NAO existe"}")
            return bwe
        }
    }
    bin.connect("request-aux-sender", AuxSender::class.java, alvo, alvo)
    true
}.getOrElse { p("    bwe: ${it.message}"); false }

private fun remendarMsid(sdp: String): String {
    val saida = StringBuilder()
    var cid: String? = null
    sdp.lineSequence().forEach { crua ->
        val l = crua.trimEnd('\r')
        when {
            l.startsWith("m=audio") -> { cid = CID_AUDIO; saida.append(l).append("\r\n") }
            l.startsWith("m=video") -> { cid = CID_VIDEO; saida.append(l).append("\r\n") }
            l.startsWith("a=msid:") && cid != null -> saida.append("a=msid:$cid $cid").append("\r\n")
            l.startsWith("a=ssrc:") && l.contains(" msid:") && cid != null ->
                saida.append(l.substringBefore(" msid:")).append(" msid:$cid $cid").append("\r\n")
            else -> saida.append(l).append("\r\n")
        }
    }
    return saida.toString()
}

private fun lerStats(bin: WebRTCBin) {
    runCatching {
        val pronto = CountDownLatch(1)
        val caixa = arrayOfNulls<Structure>(1)
        val promessa = Promise(Promise.PROMISE_CHANGE { pr ->
            caixa[0] = runCatching { pr.getReply() }.getOrNull()
            pronto.countDown()
        })
        bin.emit("get-stats", null, promessa)
        if (!pronto.await(5, TimeUnit.SECONDS)) { p("  nao respondeu em 5s"); return }
        val texto = caixa[0]?.toString() ?: run { p("  veio vazio"); return }
        p("  ${texto.length} caracteres")
        listOf("rtp-outbound", "frames-per-second", "bitrate", "packets-sent", "codec-stats")
            .forEach { if (texto.contains(it)) p("    contem: $it") }
    }.onFailure { p("  FALHOU: ${it.message}") }
}

private fun alimentarSilencio(pipeline: Pipeline) {
    val mic = pipeline.getElementByName("mic") as? AppSrc ?: return
    Thread({
        val quadro = ByteArray(960 * 2) // 10ms de 48kHz mono 16 bits
        while (true) {
            val buf = Buffer(quadro.size)
            buf.map(true)?.put(quadro)
            buf.unmap()
            if (runCatching { mic.pushBuffer(buf) }.isFailure) return@Thread
            Thread.sleep(10)
        }
    }, "silencio").apply { isDaemon = true; start() }
}

private fun encerrar(pipeline: Pipeline) {
    runCatching { pipeline.setState(State.NULL) }
    runCatching { pipeline.dispose() }
}

private fun linha(t: String) {
    p("")
    p("=== $t ===")
}

// Imprime E DESCARREGA: o Gradle liga o stdout num cano, e cano deixa a saida
// totalmente tamponada. A primeira rodada deste banco pendurou e devolveu um arquivo de
// zero byte -- as linhas estavam vivas dentro do buffer, esperando um fim que nao veio.
private fun p(s: String) {
    println(s)
    System.out.flush()
}
