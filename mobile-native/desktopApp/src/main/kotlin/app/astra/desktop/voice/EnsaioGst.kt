package app.astra.desktop.voice

import org.freedesktop.gstreamer.Bin
import org.freedesktop.gstreamer.Buffer
import org.freedesktop.gstreamer.Caps
import org.freedesktop.gstreamer.Element
import org.freedesktop.gstreamer.ElementFactory
import org.freedesktop.gstreamer.Gst
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

// BANCO DE TESTES do transporte novo. NAO VAI PRO APP -- apagar antes do commit.
//
// Existe porque tres pecas do desenho nao dava pra confirmar lendo documentacao, e as
// tres tem a mesma consequencia quando falham: a call sobe e ninguem se ouve. Descobrir
// isso dentro do VoiceEngine seria descobrir com a voz de alguem no meio.
//
//   1. o `msid` que o LiveKit usa pra casar a faixa -- da pra reescrever no SDP?
//   2. o rtpgccbwe (adaptacao de banda) -- da pra pendurar pelo request-aux-sender?
//   3. o get-stats do webrtcbin -- responde o que o auto-ajuste de qualidade le hoje?
//
// Roda com:  gradlew :desktopApp:ensaioGst
object EnsaioGst

private const val CID_AUDIO = "mic-teste01"
private const val CID_VIDEO = "screen-teste1"

fun main() {
    // Cao de guarda. Negociacao WebRTC trava de varios jeitos (promessa que nunca
    // responde, pipeline que nao sai de PAUSED), e um banco de testes que pendura pra
    // sempre nao ensina nada -- so come o tempo de quem esta esperando resposta.
    Thread({
        Thread.sleep(90_000)
        p("")
        p("!!! ESTOUROU O TEMPO (90s) -- alguma etapa pendurou. Ultima linha acima e onde.")
        System.out.flush()
        Runtime.getRuntime().halt(2)
    }, "cao-de-guarda").apply { isDaemon = true; start() }

    linha("preparando o pacote")
    if (!GStreamerPack.iniciarGst()) {
        p("  FALHOU: o pacote nao esta instalado ou o GStreamer nao carregou")
        return
    }
    p("  GStreamer ${Gst.getVersionString()}")

    val enc = listOf("nvd3d11h264enc", "qsvh264enc", "amfh264enc", "mfh264enc")
        .firstOrNull { runCatching { ElementFactory.find(it) != null }.getOrDefault(false) }
    if (enc == null) {
        p("  FALHOU: sem encoder de hardware")
        return
    }
    p("  encoder: $enc")

    // --- 1. o cano -------------------------------------------------------------
    linha("montando o cano (audio + video num webrtcbin so)")
    val descricao =
        "webrtcbin name=envio bundle-policy=max-bundle latency=0 " +
            // AUDIO: e o mesmo formato que o MicCapture ja entrega depois do
            // processamento (48kHz mono 16 bits), entao nao ha reamostragem no caminho.
            "appsrc name=mic is-live=true format=time do-timestamp=true " +
            "caps=audio/x-raw,format=S16LE,rate=48000,channels=1,layout=interleaved " +
            "! audioconvert ! audioresample ! opusenc bitrate=64000 " +
            "! rtpopuspay pt=111 " +
            // Sem `encoding-params`: eu tinha posto 2 (estereo) e o opusenc aqui e MONO.
            // O desencontro nao aparece como erro de formato -- aparece como
            // "Internal data stream error" no appsrc, tres elementos antes.
            "! application/x-rtp,media=audio,encoding-name=OPUS,payload=111,clock-rate=48000 " +
            "! envio. " +
            // VIDEO: o quadro nasce e morre dentro da placa.
            "d3d11screencapturesrc ! d3d11convert " +
            "! video/x-raw(memory:D3D11Memory),format=NV12,width=1280,height=720,framerate=30/1 " +
            "! $enc ! h264parse config-interval=-1 " +
            "! rtph264pay pt=96 config-interval=-1 aggregate-mode=zero-latency " +
            // clock-rate EXPLICITO nos dois. Sem ele o webrtcbin nao consegue montar
            // uma descricao de midia valida e desliga a linha (porta 0) em silencio.
            "! application/x-rtp,media=video,encoding-name=H264,payload=96,clock-rate=90000 " +
            "! envio."

    val pipeline = runCatching { Gst.parseLaunch(descricao) as Pipeline }.getOrElse {
        p("  FALHOU ao montar: ${it.javaClass.simpleName} ${it.message}")
        return
    }
    val bin = pipeline.getElementByName("envio") as WebRTCBin
    p("  montado")

    // --- 2. adaptacao de banda -------------------------------------------------
    linha("2. rtpgccbwe pelo request-aux-sender")
    val bweOk = pendurarBwe(bin)
    p(if (bweOk) "  pendurado (o retorno so vem quando a conexao negocia)" else "  NAO deu pra pendurar")

    // --- 3. nomear as faixas ---------------------------------------------------
    linha("3. msid nos pads de entrada")
    val pads = listOf("sink_0", "sink_1")
    pads.forEachIndexed { i, nome ->
        val cid = if (i == 0) CID_AUDIO else CID_VIDEO
        val pad = bin.getStaticPad(nome)
        if (pad == null) {
            p("  $nome: NAO EXISTE")
        } else {
            runCatching { pad.set("msid", cid) }
            p("  $nome <- msid=$cid  (leitura: ${runCatching { pad.get("msid") }.getOrNull()})")
        }
    }

    val candidatos = ConcurrentLinkedQueue<String>()
    bin.connect(WebRTCBin.ON_ICE_CANDIDATE { _, c -> candidatos.add(c) })

    // O BARRAMENTO, que faltava na primeira rodada.
    //
    // Elemento que falha no GStreamer nao levanta excecao: ele posta a queixa no
    // barramento do cano e o cano segue vivo, so que sem aquele ramo. Sem ouvir aqui,
    // "o video saiu com porta zero" e todo o diagnostico disponivel -- o motivo fica
    // guardado numa mensagem que ninguem leu.
    pipeline.bus.connect(org.freedesktop.gstreamer.Bus.ERROR { fonte, _, msg ->
        p("  [ERRO] ${fonte.name}: $msg")
    })
    pipeline.bus.connect(org.freedesktop.gstreamer.Bus.WARNING { fonte, _, msg ->
        p("  [aviso] ${fonte.name}: $msg")
    })

    val oferta = CountDownLatch(1)
    var sdpCru: String? = null

    linha("4. subindo o cano")
    pipeline.play()
    alimentarSilencio(pipeline)
    // Espera o cano chegar em PLAYING DE VERDADE em vez de contar ate um numero. O
    // NVENC leva um tempo pra acordar, e pedir a oferta antes de o video ter formato
    // negociado e o caminho mais curto pra uma linha de midia desligada.
    val estado = pipeline.getState(5_000_000_000L)
    p("  estado: $estado")

    // ESPERAR O FORMATO, e nao um relogio.
    //
    // O webrtcbin escreve a descricao da sessao com o que sabe NO INSTANTE em que a
    // oferta e pedida. Se o encoder ainda nao disse qual formato vai produzir, aquela
    // midia entra na descricao DESLIGADA (porta 0) -- e nao ha erro, nao ha aviso, a
    // oferta sai inteira e bem formada, so que sem video. Foi exatamente o que
    // aconteceu duas rodadas seguidas, e nas duas o relogio de 1,2s parecia generoso.
    //
    // O formato so aparece depois que o primeiro quadro entra no encoder, e quando
    // isso acontece nao e escolha nossa: depende da placa acordar. Entao a espera e
    // pelo FATO, com prazo -- nao por um numero que pareca suficiente.
    listOf("sink_0" to "audio", "sink_1" to "video").forEach { (pad, nome) ->
        val caps = esperarFormato(bin, pad, 8000)
        p("  formato de $nome: " + (caps ?: "NAO NEGOCIOU EM 8s"))
    }

    linha("5. oferta")
    bin.createOffer { desc ->
        sdpCru = runCatching { desc.getSDPMessage().toString() }.getOrNull()
        oferta.countDown()
    }
    if (!oferta.await(8, TimeUnit.SECONDS) || sdpCru == null) {
        p("  FALHOU: a oferta nao ficou pronta")
        encerrar(pipeline)
        return
    }
    val cru = sdpCru!!
    // PORTA ZERO NEM SEMPRE E FALHA -- e eu tinha escrito que era.
    //
    // Com `bundle-policy=max-bundle` todas as midias dividem UMA conexao, e a regra do
    // BUNDLE manda oferecer as linhas seguintes com porta 0 e `a=bundle-only`: e o jeito
    // de dizer "so aceite isto agrupado". Ou seja, a mesma porta 0 significa "midia
    // morta" quando o encoder nao ligou e "midia agrupada, tudo certo" aqui.
    //
    // O que separa os dois casos e o `a=bundle-only` e o formato negociado no pad -- nao
    // a porta. Imprimir a oferta INTEIRA custa vinte linhas de log e me impede de
    // inventar significado pra numero solto, que foi o que eu acabei de fazer.
    p("  oferta completa:")
    cru.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.forEach { p("    $it") }

    // --- 5. reescrever o msid --------------------------------------------------
    //
    // O LiveKit casa a faixa pelo `cid`, e o cliente de hoje (webrtc-java) manda o
    // MESMO valor no fluxo e na faixa: `a=msid:<cid> <cid>`. Produzir byte a byte o
    // que ja funciona hoje troca uma aposta por uma certeza -- em vez de descobrir o
    // que o servidor aceita, a gente entrega o que ele ja aceita.
    linha("5. reescrevendo o msid pro formato que o LiveKit ja aceita")
    val remendado = remendarMsid(cru)
    remendado.lineSequence().map { it.trim() }.filter { it.startsWith("a=msid") }
        .forEach { p("    $it") }

    val msg = SDPMessage().apply { parseBuffer(remendado) }
    val descRemendada = WebRTCSessionDescription(WebRTCSDPType.OFFER, msg)
    runCatching { bin.setLocalDescription(descRemendada) }
        .onSuccess { p("  aceito pelo webrtcbin") }
        .onFailure { p("  RECUSADO: ${it.javaClass.simpleName} ${it.message}") }

    Thread.sleep(4000)
    p("  candidatos de rede: ${candidatos.size}")

    // --- 6. estatisticas -------------------------------------------------------
    linha("6. get-stats (o que alimenta o auto-ajuste de qualidade)")
    lerStats(bin)

    encerrar(pipeline)
    linha("fim")
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

// O request-aux-sender pede um elemento que fica no caminho do RTP e estima a banda.
// A assinatura em C devolve GstElement*, e por isso o retorno do callback importa:
// devolver nada aqui e o mesmo que nao ter adaptacao nenhuma.
private interface AuxSender : GstAPI.GstCallback {
    fun callback(bin: Element, transporte: Element): Element?
}

private fun pendurarBwe(bin: WebRTCBin): Boolean = runCatching {
    val alvo = object : AuxSender {
        override fun callback(b: Element, transporte: Element): Element? {
            val bwe = ElementFactory.make("rtpgccbwe", null)
            p("    [sinal] request-aux-sender -> ${if (bwe != null) "rtpgccbwe entregue" else "rtpgccbwe NAO existe"}")
            return bwe
        }
    }
    bin.connect("request-aux-sender", AuxSender::class.java, alvo, alvo)
    true
}.getOrElse {
    p("    ${it.javaClass.simpleName}: ${it.message}")
    false
}

// Troca `a=msid:<qualquer> <qualquer>` pelo `<cid> <cid>` da faixa daquela m-line.
// A ordem das m-lines segue a ordem em que os pads entraram: audio primeiro.
private fun remendarMsid(sdp: String): String {
    val saida = StringBuilder()
    var cidAtual: String? = null
    sdp.lineSequence().forEach { linhaCrua ->
        val l = linhaCrua.trimEnd('\r')
        when {
            l.startsWith("m=audio") -> { cidAtual = CID_AUDIO; saida.append(l).append("\r\n") }
            l.startsWith("m=video") -> { cidAtual = CID_VIDEO; saida.append(l).append("\r\n") }
            l.startsWith("a=msid:") && cidAtual != null ->
                saida.append("a=msid:$cidAtual $cidAtual").append("\r\n")
            // O msid tambem viaja no atributo de ssrc, e o LiveKit le esse tambem.
            l.startsWith("a=ssrc:") && l.contains(" msid:") && cidAtual != null -> {
                val ssrc = l.substringBefore(" msid:")
                saida.append("$ssrc msid:$cidAtual $cidAtual").append("\r\n")
            }
            else -> saida.append(l).append("\r\n")
        }
    }
    return saida.toString()
}

private fun lerStats(bin: WebRTCBin) {
    runCatching {
        // A promessa responde por CALLBACK, e nao pelo waitResult().
        //
        // `gst_promise_wait` bloqueia ate alguem responder, e "alguem" aqui e o
        // webrtcbin -- que pode simplesmente nao responder se o pedido nao servir. Sem
        // prazo, o banco de testes fica pendurado pra sempre esperando a resposta que
        // era justamente o que ele veio medir.
        val pronto = CountDownLatch(1)
        val caixa = arrayOfNulls<Structure>(1)
        val promessa = Promise(Promise.PROMISE_CHANGE { pr ->
            caixa[0] = runCatching { pr.getReply() }.getOrNull()
            pronto.countDown()
        })
        bin.emit("get-stats", null, promessa)
        if (!pronto.await(5, TimeUnit.SECONDS)) {
            p("  o webrtcbin nao respondeu em 5s")
            return
        }
        val r: Structure? = caixa[0]
        if (r == null) {
            p("  veio vazio")
            return
        }
        // Interessa saber SE existem os campos que o auto-ajuste le hoje:
        // framesPerSecond (envio) e a razao de limitacao.
        val texto = r.toString()
        p("  tamanho do relatorio: ${texto.length} caracteres")
        listOf("frames-per-second", "framesPerSecond", "bitrate", "packets-sent", "rtp-outbound", "outbound-rtp")
            .forEach { chave -> if (texto.contains(chave)) p("    contem: $chave") }
        p("  amostra: " + texto.take(400))
    }.onFailure { p("  FALHOU: ${it.javaClass.simpleName} ${it.message}") }
}

// O appsrc precisa de dados senao o audio nunca negocia de verdade.
private fun alimentarSilencio(pipeline: Pipeline) {
    val mic = pipeline.getElementByName("mic") as? AppSrc ?: return
    Thread({
        val quadro = ByteArray(960 * 2) // 10ms de 48kHz mono 16 bits
        repeat(600) {
            val buf = Buffer(quadro.size)
            buf.map(true)?.put(quadro)
            buf.unmap()
            runCatching { mic.pushBuffer(buf) }
            Thread.sleep(10)
        }
    }, "silencio").apply { isDaemon = true; start() }
}

private fun encerrar(pipeline: Pipeline) {
    runCatching { pipeline.setState(State.NULL) }
    runCatching { (pipeline as Bin).dispose() }
}

private fun linha(t: String) {
    p("")
    p("=== $t ===")
}

// Imprime E DESCARREGA. O Gradle liga o stdout num cano, e cano deixa a saida
// TOTALMENTE tamponada -- a primeira tentativa deste banco de testes rodou, pendurou e
// devolveu um arquivo de zero byte. Nao havia nada errado com o codigo: as linhas
// estavam vivas dentro do buffer, esperando um fim que nao veio. Num programa que pode
// travar, saida sem descarga e saida que nao existe.
private fun p(s: String) {
    println(s)
    System.out.flush()
}
