package app.astra.desktop.voice

import app.astra.desktop.prefs.ScreenQuality
import java.util.concurrent.ConcurrentLinkedQueue

// BANCO DE TESTES do transporte novo:  gradlew :desktopApp:ensaioGst
//
// ELE EXERCITA A CLASSE QUE VAI PRO APP, e nao uma copia. Um banco de testes que
// reimplementa o que quer testar prova que a copia funciona -- e a copia nao e o que
// roda na call de ninguem. Toda vez que este arquivo e o GstPublisher discordarem, quem
// esta errado e este arquivo.
//
// Existe porque as pecas do transporte falham todas do mesmo jeito -- a call sobe e
// ninguem se ouve -- e o GStreamer nao levanta excecao quando um elemento falha: posta a
// queixa no barramento e o cano segue vivo sem aquele ramo.
//
// JA PROVADO por ele, nesta maquina (e ja corrigido no app):
//   . nvh264enc e o modo CUDA e RECUSA quadro em memoria D3D11 -> nvd3d11h264enc
//   . Gst.init sem versao pede a BASELINE (1.8) e tranca o setLocalDescription
//   . encoding-params=2 com opusenc mono -> "Internal data stream error" no appsrc
//   . fonte ao vivo acrescentada a cano ANDANDO nao arranca: zero quadro, sem aviso
//     (conserto: entrar e sair pelo repouso -- 137ms de audio parado ao comecar, 12 ao parar)
//   . desmontar o ramo com o cano andando CORROMPE a memoria nativa (0xC0000374)
//   . remendar o SDP funciona: o webrtcbin aceita a oferta reescrita
object EnsaioGst

fun main() {
    Thread({
        Thread.sleep(120_000)
        p("")
        p("!!! ESTOUROU O TEMPO (120s) -- a ultima linha acima e onde pendurou.")
        System.out.flush()
        Runtime.getRuntime().halt(2)
    }, "cao-de-guarda").apply { isDaemon = true; start() }

    val ofertas = ConcurrentLinkedQueue<String>()
    val candidatos = ConcurrentLinkedQueue<String>()

    val pub = GstPublisher(
        onOferta = { ofertas.add(it) },
        onCandidato = { _, c -> candidatos.add(c) },
    )

    linha("1. subindo o transporte com o microfone")
    // Passo a passo, porque "nao subiu" tem tres causas com consertos diferentes: o
    // pacote nao esta em disco, o GStreamer nao carregou, ou a maquina nao tem encoder.
    // Perguntar o encoder ANTES do init sempre devolve null -- foi o meu proprio erro na
    // primeira rodada, e ele parecia falta de hardware.
    p("  pacote em disco: ${GStreamerPack.disponivel}")
    p("  GStreamer carregou: ${GStreamerPack.iniciarGst()}")
    p("  encoder disponivel: ${runCatching { pub.encoderDisponivel }.getOrNull()}")
    if (!pub.iniciar(CID_MIC, null)) {
        p("  NAO SUBIU -- nesta maquina o app seguiria pelo caminho de sempre")
        return
    }
    p("  no ar")
    alimentarSilencio(pub)
    Thread.sleep(1500)

    linha("2. oferta com o microfone so")
    pub.negociar()
    Thread.sleep(2500)
    mostrar(ofertas.poll())

    linha("3. entrando com a tela (o audio nao pode cair)")
    val t0 = System.currentTimeMillis()
    val entrou = pub.publicarTela(CID_TELA, 0, ScreenQuality.LIGHT_720_30)
    p("  publicarTela = $entrou, levou ${System.currentTimeMillis() - t0} ms")
    if (!entrou) { pub.parar(); return }
    Thread.sleep(2000)

    linha("4. oferta com microfone + tela")
    pub.negociar()
    Thread.sleep(2500)
    mostrar(ofertas.poll())
    p("  candidatos de rede: ${candidatos.size}")

    linha("5. estatisticas")
    p("  ${pub.estatisticas()}")

    linha("6. saindo da tela (o audio nao pode cair)")
    val t1 = System.currentTimeMillis()
    pub.pararVideo()
    p("  pararVideo levou ${System.currentTimeMillis() - t1} ms")
    Thread.sleep(1500)

    linha("7. oferta depois de sair")
    pub.negociar()
    Thread.sleep(2500)
    mostrar(ofertas.poll())

    pub.parar()
    linha("fim")
}

private const val CID_MIC = "mic-ensaio01"
private const val CID_TELA = "screen-ensaio1"

// Confere o que decide a migracao, e nao "saiu uma oferta".
private fun mostrar(sdp: String?) {
    if (sdp == null) { p("  NENHUMA OFERTA CHEGOU"); return }
    var cid: String? = null
    sdp.lineSequence().map { it.trim() }.forEach { l ->
        when {
            l.startsWith("m=") -> { cid = null; p("  $l") }
            l == "a=bundle-only" -> p("      (agrupada -- porta 0 aqui e o esperado)")
            l == "a=sendonly" -> p("      so envia (correto pra publicacao)")
            l == "a=inactive" -> p("      inativa (o servidor despublica -- correto depois de parar)")
            l == "a=sendrecv" -> p("      !!! sendrecv -- o remendo nao pegou nesta midia")
            l.startsWith("a=rtpmap:") -> p("      $l")
            l.contains(" msid:") -> {
                val partes = l.substringAfter(" msid:").trim().split(" ")
                val casa = partes.size == 2 && partes[0] == partes[1]
                p("      msid: ${partes.joinToString(" ")} ${if (casa) "(o LiveKit reconhece)" else "!!! OS DOIS CAMPOS DIFEREM"}")
            }
        }
    }
    val temAssinatura = sdp.contains("a=fingerprint:")
    val temIce = sdp.contains("a=ice-ufrag:")
    p("  criptografia: ${if (temAssinatura) "ok" else "AUSENTE"} . busca de rede: ${if (temIce) "ok" else "AUSENTE"}")
}

// Faz as vezes do MicCapture: 10ms de silencio a cada 10ms, no mesmo formato.
private fun alimentarSilencio(pub: GstPublisher) {
    Thread({
        val quadro = ByteArray(480 * 2) // 10ms de 48kHz mono 16 bits
        while (pub.vivo) {
            pub.empurrarAudio(quadro, quadro.size)
            Thread.sleep(10)
        }
    }, "silencio").apply { isDaemon = true; start() }
}

private fun linha(t: String) {
    p("")
    p("=== $t ===")
}

// Imprime E DESCARREGA: o Gradle liga o stdout num cano, e cano deixa a saida totalmente
// tamponada. A primeira rodada deste banco pendurou e devolveu um arquivo de zero byte --
// as linhas estavam vivas dentro do buffer, esperando um fim que nao veio.
private fun p(s: String) {
    println(s)
    System.out.flush()
}
