package app.astra.desktop.voice

import app.astra.desktop.prefs.ScreenQuality
import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.SDPMessage
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.webrtc.WebRTCBin
import org.freedesktop.gstreamer.webrtc.WebRTCSDPType
import org.freedesktop.gstreamer.webrtc.WebRTCSessionDescription
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
        Thread.sleep(180_000)
        p("")
        p("!!! ESTOUROU O TEMPO (180s). Pilhas de quem estava vivo:")
        // A foto das pilhas, e nao so "pendurou". Pendurar no GStreamer quase sempre e
        // uma espera de estado que nunca chega, e o nome do metodo que esta esperando
        // diz qual elemento nao respondeu. Sem isto sobra adivinhar.
        Thread.getAllStackTraces()
            .filterKeys { it.name != "cao-de-guarda" }
            .forEach { (t, pilha) ->
                if (pilha.isEmpty()) return@forEach
                p("  --- ${t.name} (${t.state}) ---")
                pilha.take(12).forEach { p("      $it") }
            }
        System.out.flush()
        Runtime.getRuntime().halt(2)
    }, "cao-de-guarda").apply { isDaemon = true; start() }

    val ofertas = ConcurrentLinkedQueue<String>()
    val candidatos = ConcurrentLinkedQueue<String>()

    val previas = java.util.concurrent.atomic.AtomicInteger(0)
    val tamanhoPrevia = arrayOfNulls<String>(1)

    val pub = GstPublisher(
        onOferta = { ofertas.add(it) },
        onCandidato = { _, c -> candidatos.add(c) },
        onPrevia = { _, largura, altura ->
            previas.incrementAndGet()
            tamanhoPrevia[0] = "${largura}x$altura"
        },
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

    // TODA QUALIDADE QUE O DONO PODE ESCOLHER, e nao so a que eu escolhi pra testar.
    //
    // O ensaio exercitava 720p30 e passava. O dono usa 720p60 -- e a transmissao dele nao
    // aparecia pra ninguem, nem na propria previa. Um preset que nao sobe e invisivel aqui
    // e fatal la, e a diferenca entre os dois e uma linha de configuracao.
    linha("2b. cada qualidade que o dono pode escolher")
    ScreenQuality.entries.forEach { q ->
        previas.set(0)
        tamanhoPrevia[0] = null
        val t = System.currentTimeMillis()
        val subiu = pub.publicarTela(CID_TELA, 0, q)
        Thread.sleep(1200)
        pub.estatisticas() // finca o marco; a primeira leitura nao tem intervalo
        Thread.sleep(2000)
        val fps = pub.estatisticas()
        val quadros = previas.get()
        p("  ${q.label}: publicou=$subiu . previa=$quadros quadro(s) ${tamanhoPrevia[0] ?: ""} . ${System.currentTimeMillis() - t} ms")
        p("      captura ${fps?.fpsCaptura ?: "?"} fps . COMPRIMIDO ${fps?.fpsEnvio ?: "?"} fps")
        // O caso do dono: a previa aparece (a fonte esta produzindo) e o envio e zero.
        // Se a captura anda e o comprimido nao, quem esta parado e o ENCODER -- e ai o
        // lugar de procurar e a placa, nao a captura.
        if (subiu && (fps?.fpsEnvio ?: 0) == 0) {
            p("      !!! NADA COMPRIMIDO -- e exatamente o que o dono ve (envio 0fps)")
        }
        if (subiu && quadros == 0) p("      !!! SEM PREVIA")
        pub.pararVideo()
        Thread.sleep(400)
    }
    previas.set(0)
    tamanhoPrevia[0] = null

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

    linha("5. previa local")
    p("  quadros entregues: ${previas.get()}  tamanho: ${tamanhoPrevia[0] ?: "nenhum"}")
    if (previas.get() == 0) p("  !!! SEM PREVIA -- quem transmitir nao ve a propria tela")

    linha("6. estatisticas")
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

    conexaoDeVerdade()
    linha("fim")

    // SAI DE VERDADE, e isto nao e detalhe de arrumacao.
    //
    // Voltar de `main` nao encerrava o processo: sobra thread nao-daemon viva, e o JVM
    // ficava pendurado segurando a captura de tela e uma sessao de NVENC. Placa GeForce
    // limita quantas sessoes de encoder existem ao mesmo tempo -- e depois de algumas
    // rodadas eu tinha SETE ensaios fantasmas segurando as vagas. A rodada seguinte
    // travava no desmonte, e o travamento parecia defeito do app. Nao era: era sujeira
    // deixada pelo proprio banco de testes.
    System.out.flush()
    Runtime.getRuntime().halt(0)
}

// A CONEXAO DE VERDADE, com os dois lados dentro deste processo.
//
// POR QUE ISTO PRECISOU EXISTIR. Tudo acima prova MONTAGEM: o cano sobe, o SDP sai certo,
// o ramo entra e sai. Nada acima prova CONEXAO -- e o app so morria quando conectava de
// verdade. O sintoma era o pior possivel: a call subia, o log parava na linha do join e o
// processo sumia. Sem excecao Java, sem hs_err, sem registro no Windows.
//
// (O silencio tem explicacao: quando o GLib aborta, ele desliga o relatorio de falhas do
// Windows antes -- `_set_abort_behavior(0, _CALL_REPORTFAULT)`. E como o lancador do
// jpackage nao tem console, a mensagem que ele imprime antes de morrer nao vai pra lugar
// nenhum. Processo evapora sem deixar bilhete.)
//
// Um segundo webrtcbin respondendo a oferta e trocando candidatos basta pra nascer o
// DTLS. E o nascimento do DTLS que dispara o `request-aux-sender` -- o unico sinal que
// nenhum ensaio anterior chegou a encostar, e por isso o unico defeito que sobreviveu.
private fun conexaoDeVerdade() {
    linha("8. conexao de verdade (os dois lados aqui dentro)")

    // Montado na mao, e nao por parseLaunch: com UM elemento so o parseLaunch devolve o
    // proprio elemento, nao um cano -- e o cano e quem toca o estado.
    val eco = Pipeline("cano-eco")
    val binEco = WebRTCBin("eco")
    runCatching { binEco.set("bundle-policy", 3) } // 3 = max-bundle, igual ao nosso lado
    runCatching { binEco.set("latency", 0) }
    eco.add(binEco)
    eco.bus.connect(Bus.ERROR { fonte, _, msg -> p("  [eco] erro em ${fonte.name}: $msg") })

    val ofertas = ConcurrentLinkedQueue<String>()
    val pub = GstPublisher(
        onOferta = { ofertas.add(it) },
        onCandidato = { m, c -> runCatching { binEco.addIceCandidate(m, c) } },
    )
    binEco.connect(WebRTCBin.ON_ICE_CANDIDATE { m, c -> pub.candidatoRemoto(m, c) })
    eco.play()

    if (!pub.iniciar("mic-loop01", null)) { p("  nao subiu"); return }
    alimentarSilencio(pub)
    Thread.sleep(800)

    pub.negociar()
    val oferta = esperar(8_000) { ofertas.poll() } ?: run {
        p("  !!! a oferta nao saiu"); pub.parar(); return
    }
    p("  oferta feita (${oferta.lineSequence().count()} linhas)")

    binEco.setRemoteDescription(
        WebRTCSessionDescription(WebRTCSDPType.OFFER, SDPMessage().apply { parseBuffer(oferta) }),
    )
    binEco.createAnswer { resposta ->
        runCatching {
            binEco.setLocalDescription(resposta)
            val sdp = resposta.getSDPMessage().toString()
            resposta.invalidate() // mesma armadilha da oferta: a dona e a promessa
            pub.aplicarResposta(sdp)
        }.onFailure { p("  !!! o eco nao respondeu: ${it.message}") }
    }

    // O estado e impresso a CADA MUDANCA, e nao so no fim: se o processo morrer no meio,
    // a ultima linha impressa diz ate onde a conexao tinha chegado.
    var anterior = ""
    val ate = System.currentTimeMillis() + 25_000
    var conectou = false
    while (System.currentTimeMillis() < ate) {
        val agora = pub.estadoDaConexao()
        if (agora != anterior) { p("  estado: $agora"); anterior = agora }
        if (agora == "connected") { conectou = true; break }
        Thread.sleep(250)
    }
    if (!conectou) { p("  !!! NAO CONECTOU em 25s (ultimo estado: $anterior)"); pub.parar(); return }

    // Depois de conectado o microfone segue empurrando. O defeito que derrubava o app nao
    // aparecia no instante da conexao: aparecia logo depois, quando o lixo do Java passava
    // a solta um objeto que o lado nativo ainda estava usando.
    p("  conectado. mantendo 15s com o microfone empurrando (e cobrando o coletor)...")
    repeat(15) {
        Thread.sleep(700)
        System.gc()
        Thread.sleep(300)
        if (!pub.vivo) { p("  !!! o transporte morreu no segundo $it"); return }
    }
    p("  SOBREVIVEU 15s conectado -- estado final: ${pub.estadoDaConexao()}")

    pub.parar()
    runCatching { eco.setState(State.NULL); eco.dispose() }
}

private fun <T> esperar(ms: Long, tentar: () -> T?): T? {
    val ate = System.currentTimeMillis() + ms
    while (System.currentTimeMillis() < ate) {
        tentar()?.let { return it }
        Thread.sleep(100)
    }
    return null
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
            pub.empurrarAudio(quadro, 16, 48000, 1, 480)
            Thread.sleep(10)
        }
    }, "silencio").apply { isDaemon = true; start() }
}

private fun linha(t: String) {
    // COBRA O COLETOR ANTES DE CADA ETAPA, de proposito.
    //
    // Os dois defeitos que derrubavam a call eram objetos com DOIS DONOS: o Java achava
    // que a memoria era dele, o GStreamer tambem, e o segundo `free` corrompia o monte.
    // Isso nao quebra na hora -- quebra quando o coletor passa. Num ensaio de trinta
    // segundos ele podia nao passar, e ai o ensaio dizia "passou" sobre codigo que
    // derrubava a call de verdade em cinco segundos.
    //
    // Chamar aqui troca sorte por prova: se sobrou algum dono duplicado, ele morre NESTA
    // linha, com o nome da etapa impresso logo acima.
    System.gc()
    Thread.sleep(300)
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
