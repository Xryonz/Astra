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
        runCatching {
            val k = com.sun.jna.platform.win32.Kernel32.INSTANCE
            k.TerminateProcess(k.GetCurrentProcess(), 2)
        }
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
    // A AREA DE TRABALHO PRECISA MEXER, senao o numero medido nao quer dizer nada.
    //
    // O Desktop Duplication do Windows so entrega quadro quando a tela MUDA: com o
    // desktop parado a fonte da ~1 quadro por segundo, e "0 fps" seria a resposta certa
    // pra uma pergunta que ninguem quis fazer. Foi assim que eu quase confundi captura
    // dormindo com captura quebrada -- e o dono ia esperar por um conserto do lado errado.
    val agitador = Agitador().apply { comecar() }
    ScreenQuality.entries.forEach { q ->
        previas.set(0)
        tamanhoPrevia[0] = null
        val t = System.currentTimeMillis()
        val subiu = pub.publicarTela(CID_TELA, 0, q)
        Thread.sleep(1200)
        // A PREVIA E OS CONTADORES TEM QUE OLHAR A MESMA JANELA DE TEMPO.
        //
        // Antes a previa somava desde o arranque e os fps mediam so os 2s do fim -- e a
        // leitura saia "previa=5 quadros, captura 0 fps", que parece contradicao e nao e:
        // os 5 quadros tinham chegado todos no arranque. Comparar dois numeros medidos em
        // periodos diferentes nao prova nada, e essa aparente contradicao quase me fez
        // procurar defeito numa sonda que estava certa.
        previas.set(0)
        pub.estatisticas() // finca o marco; a primeira leitura nao tem intervalo
        Thread.sleep(2000)
        val fps = pub.estatisticas()
        val quadros = previas.get()
        p("  ${q.label}: publicou=$subiu . ${System.currentTimeMillis() - t} ms")
        p("      nos mesmos 2s: captura ${fps?.fpsCaptura ?: "?"} fps . COMPRIMIDO ${fps?.fpsEnvio ?: "?"} fps . previa $quadros quadro(s) ${tamanhoPrevia[0] ?: ""}")
        // Tres numeros da mesma janela, e cada combinacao aponta um lugar diferente:
        //   captura anda + comprimido parado -> a placa
        //   os tres parados                  -> a captura (ou a tela nao mudou)
        //   previa anda + captura zerada     -> a SONDA esta mentindo, nao o cano
        if (subiu && (fps?.fpsCaptura ?: 0) == 0 && quadros > 0) {
            p("      !!! A SONDA MENTE: chegou previa sem quadro contado na fonte")
        }
        if (subiu && (fps?.fpsEnvio ?: 0) == 0) {
            p("      !!! NADA COMPRIMIDO -- e exatamente o que o dono ve (envio 0fps)")
        }
        if (subiu && quadros == 0) p("      !!! SEM PREVIA")
        pub.pararVideo()
        Thread.sleep(400)
    }
    agitador.parar()
    previas.set(0)
    tamanhoPrevia[0] = null

    // SEGUNDO A SEGUNDO, num preset so, com movimento de verdade na tela.
    //
    // A media de 2s escondia a forma da curva. "captura 0 fps" pode ser tres coisas
    // diferentes -- nunca produziu, produziu no arranque e parou, ou produz devagar -- e
    // as tres pedem consertos em lugares distintos. Dez leituras de um segundo mostram
    // qual das tres e, sem interpretacao.
    linha("2c. dez segundos num preset so, com a tela mexendo")
    val piscante = Piscante().apply { comecar() }
    Thread.sleep(500)
    previas.set(0)
    if (pub.publicarTela(CID_TELA, 0, ScreenQuality.SMOOTH_720_60)) {
        pub.estatisticas() // finca o marco
        repeat(10) {
            val antes = previas.get()
            Thread.sleep(1000)
            val e = pub.estatisticas()
            p("  ${it + 1}s: captura ${e?.fpsCaptura ?: "?"} . comprimido ${e?.fpsEnvio ?: "?"} . previa ${previas.get() - antes}")
        }
        pub.pararVideo()
        Thread.sleep(400)
    } else {
        p("  nao publicou")
    }
    piscante.parar()
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

    // A CAMERA MUDOU DE LUGAR: agora e medida no estagio 8, dentro da conexao viva.
    // Aqui ela so podia dar zero -- este cano nunca negociou video -- e um teste que nao
    // consegue dar outra resposta nao e teste.
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
    // PELO WINDOWS, e nao pelo JVM. O `halt` era a versao anterior disto e NAO BASTA:
    // medido em tres rodadas seguidas, o processo ficava vivo por mais de sete minutos
    // depois do "fim" -- e nem o `jstack` o alcancava, porque as threads do GStreamer
    // estavam presas em codigo nativo e o JVM nunca chegava a um safepoint. Cada rodada
    // custava sete minutos e um `Stop-Process` na mao.
    //
    // `TerminateProcess` sobre o proprio processo nao pede licenca a ninguem dentro do
    // JVM: o Windows derruba tudo, incluindo quem esta preso em nativo.
    System.out.flush()
    System.err.flush()
    runCatching {
        val k = com.sun.jna.platform.win32.Kernel32.INSTANCE
        k.TerminateProcess(k.GetCurrentProcess(), 0)
    }
    Runtime.getRuntime().halt(0) // se ate isso falhar, ao menos tenta o caminho antigo
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
// Mexe o PONTEIRO DO MOUSE, so pra a area de trabalho ter o que mudar.
//
// Nao e enfeite: o Desktop Duplication do Windows so entrega quadro quando a tela MUDA.
// Com o desktop parado a fonte entrega um punhado no arranque e depois cala -- e "0 fps"
// vira a resposta certa pra uma pergunta que ninguem quis fazer. Medir captura de tela
// sem nada mexendo e como medir microfone numa sala em silencio.
//
// PELO PONTEIRO, e nao por uma janela que pisca. A primeira versao abria um JFrame
// sempre-no-topo repintando a 60Hz. Funcionou como movimento e DEIXOU O PROCESSO
// IMORTAL: as threads do AWT somadas ao `halt` ja pouco confiavel do GStreamer davam um
// JVM que nem o `jstack` conseguia parar -- ele nao alcancava safepoint, porque estava
// presa em codigo nativo. Mover o ponteiro nao cria janela, nao acorda o AWT, e ainda
// exercita o `show-cursor` que faltava: quem se move na imagem passa a ser exatamente a
// coisa que estava invisivel.
private class Agitador {
    private val vivo = java.util.concurrent.atomic.AtomicBoolean(false)

    fun comecar() {
        val u = runCatching { com.sun.jna.platform.win32.User32.INSTANCE }.getOrNull()
        if (u == null) {
            p("  (sem agitador: o User32 nao respondeu -- os fps da tela vao sair baixos)")
            return
        }
        vivo.set(true)
        Thread({
            var i = 0
            while (vivo.get()) {
                // Circulo pequeno num canto: movimento continuo sem passar por cima de
                // nada clicavel. So a posicao muda -- nenhum clique, nenhum atalho.
                val ang = i * 0.2
                runCatching {
                    u.SetCursorPos(
                        (300 + 120 * kotlin.math.cos(ang)).toLong(),
                        (300 + 120 * kotlin.math.sin(ang)).toLong(),
                    )
                }
                i++
                Thread.sleep(16)
            }
        }, "agitador").apply { isDaemon = true; start() }
    }

    fun parar() = vivo.set(false)
}

// Uma janelinha que troca de cor 60 vezes por segundo. PIXEL MUDANDO DE VERDADE.
//
// O `Agitador` acima mexe so o ponteiro, e isso testa o `show-cursor` -- mas nao serve
// de prova quando a pergunta e "a captura esta viva?". O Desktop Duplication distingue
// as duas coisas: movimento de ponteiro chega como atualizacao de POSICAO, sem quadro
// novo, enquanto pixel trocando de cor forca quadro. Medir captura com o ponteiro era
// arriscar concluir "morta" de uma fonte apenas quieta.
//
// A versao anterior disto deixava o processo imortal (threads do AWT + `halt` fraco do
// GStreamer). Voltou a ser usavel porque a saida agora e `TerminateProcess`, que o
// Windows executa sem pedir licenca a ninguem dentro do JVM.
private class Piscante {
    private var janela: javax.swing.JFrame? = null
    private val vivo = java.util.concurrent.atomic.AtomicBoolean(false)

    fun comecar() {
        runCatching {
            javax.swing.SwingUtilities.invokeAndWait {
                janela = javax.swing.JFrame().apply {
                    isUndecorated = true
                    isAlwaysOnTop = true
                    setSize(320, 240)
                    setLocation(60, 60)
                    isVisible = true
                }
            }
            val alvo = janela ?: return@runCatching
            vivo.set(true)
            Thread({
                var i = 0
                while (vivo.get()) {
                    val cor = java.awt.Color.getHSBColor((i % 60) / 60f, 0.95f, 0.95f)
                    javax.swing.SwingUtilities.invokeLater { alvo.contentPane.background = cor; alvo.repaint() }
                    i++
                    Thread.sleep(16)
                }
            }, "piscante").apply { isDaemon = true; start() }
        }.onFailure { p("  (sem piscante: ${it.javaClass.simpleName} -- a tela vai ficar parada e os fps vao mentir)") }
    }

    fun parar() {
        vivo.set(false)
        runCatching { javax.swing.SwingUtilities.invokeLater { janela?.dispose() } }
        janela = null
    }
}

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

    responder(binEco, pub, oferta)

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

    // A TELA AQUI DENTRO, e este e o unico lugar do ensaio onde medir fps significa algo.
    //
    // O 2c mede a tela num cano que NUNCA NEGOCIOU: sem conexao, o `webrtcbin` engole uns
    // poucos quadros e para de aceitar, o `tee` trava junto (o ramo do encoder nao tem
    // fila) e a fonte fica parada dentro de um `push` que nao volta. O log do GStreamer
    // mostrou isso em letras miudas: cinco "Capture done" a 60fps e silencio absoluto
    // depois. Concluir dali que "a captura morre" seria culpar a fonte por um cano sem
    // saida -- eu quase fiz exatamente isso.
    p("  agora a TELA, dentro da conexao viva:")
    val piscante = Piscante().apply { comecar() }
    Thread.sleep(500)
    medir(pub, binEco, ofertas, "tela 720p60", 8) {
        pub.publicarTela(CID_TELA, 0, ScreenQuality.SMOOTH_720_60)
    }
    provarAtuador(pub, binEco, ofertas)
    piscante.parar()

    // A CAMERA TAMBEM AQUI DENTRO, e nao la em cima com as outras fontes.
    //
    // Ela morava num estagio proprio, num cano que nunca negociou video -- exatamente o
    // erro que fez a tela parecer morta por tres rodadas. Naquele lugar a camera SO PODIA
    // dar zero, e eu teria concluido "a camera nao funciona" de um teste que nao
    // conseguia dar outra resposta. Fonte de video so pode ser medida com transporte
    // negociado do outro lado.
    val aparelhos = runCatching { dev.onvoid.webrtc.media.MediaDevices.getVideoCaptureDevices() }
        .getOrElse {
            p("  nao consegui listar cameras (${it.javaClass.simpleName})")
            emptyList()
        }
    if (aparelhos.isEmpty()) p("  NENHUMA CAMERA nesta maquina -- o cano da camera fica sem prova")
    aparelhos.forEach { aparelho ->
        // O QUE A CAMERA DIZ QUE SABE FAZER, ao lado do que ela de fato entregou. Sem esta
        // linha, "17 fps" e um numero sem regua: pode ser cano ruim ou pode ser o limite do
        // aparelho, e as duas leituras mandam procurar em lugares opostos.
        runCatching { dev.onvoid.webrtc.media.MediaDevices.getVideoCaptureCapabilities(aparelho) }
            .getOrDefault(emptyList())
            .filter { it.width in 641..1280 }
            .distinctBy { "${it.width}x${it.height}@${it.frameRate}" }
            .take(8)
            .forEach { p("    o aparelho anuncia ${it.width}x${it.height} @ ${it.frameRate}fps") }
        // Todas, e nao a primeira: a ordem da lista se inverteu sozinha entre duas rodadas,
        // e a "OBS Virtual Camera" entrega zero com o OBS fechado -- zero por camera
        // desligada e zero por cano quebrado sao a mesma linha no relatorio.
        medir(pub, binEco, ofertas, "camera \"${aparelho.name}\"", 4) {
            pub.publicarCamera(CID_TELA, aparelho.name, 1280, 720)
        }
    }

    pub.parar()
    runCatching { eco.setState(State.NULL); eco.dispose() }
}

// Publica uma fonte de video DENTRO da conexao viva, responde a renegociacao e mede.
//
// A RESPOSTA A SEGUNDA OFERTA E O CORACAO DISTO. Entrar com video renegocia; se ninguem
// responde, a linha de video existe na oferta e NAO EXISTE no transporte. O `webrtcbin`
// aceita entao exatamente um quadro e trava -- o log mostrou a fonte presa nove segundos e
// meio dentro de um unico `gst_pad_push`. Eu quase registrei isso como defeito do
// aplicativo; era defeito do banco de testes, que so sabia responder uma vez.
private fun medir(
    pub: GstPublisher,
    binEco: WebRTCBin,
    ofertas: java.util.Queue<String>,
    rotulo: String,
    segundos: Int,
    publicar: () -> Boolean,
) {
    p("  $rotulo:")
    if (!publicar()) {
        p("    !!! nao publicou")
        return
    }
    pub.negociar()
    val oferta = esperar(8_000) { ofertas.poll() }
    if (oferta == null) p("    !!! a oferta com video nao saiu") else responder(binEco, pub, oferta)
    Thread.sleep(1500)
    pub.estatisticas() // finca o marco; a primeira leitura nao tem intervalo
    repeat(segundos) {
        Thread.sleep(1000)
        val e = pub.estatisticas()
        p(
            "    ${it + 1}s: captura ${e?.fpsCaptura ?: "?"} . comprimido ${e?.fpsEnvio ?: "?"}" +
                " . ${e?.kbpsReal ?: "?"} kbps (pedido ${e?.kbpsPedido ?: "?"})",
        )
        pub.elosAgora().takeIf { s -> s.isNotBlank() }?.let { s -> p("        $s") }
    }
    pub.pararVideo()
    Thread.sleep(400)
}

// O ATUADOR DO CONTROLE DE CONGESTIONAMENTO, provado antes de existir controle nenhum.
//
// Um laco que mede a rede e pede menos bitrate so serve se o encoder OBEDECER. E o
// `gst-inspect` levanta uma duvida seria: o `nvd3d11h264enc` declara a propriedade como
// alteravel com o cano andando, e o `qsvh264enc` -- que e o encoder desta maquina -- NAO
// declara. Sem essa marca o GStreamer aceita a escrita e o elemento pode ignora-la ate a
// proxima renegociacao.
//
// Entao: metade do teto no meio da transmissao, e olha-se o kbps REAL. Se ele nao descer,
// nao adianta escrever o laco -- ele mandaria numeros pra ninguem.
private fun provarAtuador(pub: GstPublisher, binEco: WebRTCBin, ofertas: java.util.Queue<String>) {
    p("  o encoder obedece um teto novo com o cano andando?")
    if (!pub.publicarTela(CID_TELA, 0, ScreenQuality.SMOOTH_720_60)) {
        p("    !!! nao publicou")
        return
    }
    pub.negociar()
    esperar(8_000) { ofertas.poll() }?.let { responder(binEco, pub, it) }
    Thread.sleep(1500)
    pub.estatisticas()
    repeat(3) {
        Thread.sleep(1000)
        val e = pub.estatisticas()
        p("    antes  ${it + 1}s: ${e?.kbpsReal ?: "?"} kbps (pedido ${e?.kbpsPedido ?: "?"})")
    }
    val novo = 800
    p("    --> pedindo $novo kbps: aceito=${pub.pedirBitrate(novo)}")
    Thread.sleep(1000)
    pub.estatisticas() // marco novo, pra a media nao misturar os dois regimes
    repeat(4) {
        Thread.sleep(1000)
        val e = pub.estatisticas()
        p("    depois ${it + 1}s: ${e?.kbpsReal ?: "?"} kbps (pedido ${e?.kbpsPedido ?: "?"})")
    }
    pub.pararVideo()
    Thread.sleep(400)
}

// O eco recebe uma oferta e devolve a resposta. Serve pra PRIMEIRA e pra toda
// renegociacao -- entrar com a tela produz uma segunda oferta, e sem resposta pra ela o
// video fica anunciado e sem transporte.
private fun responder(binEco: WebRTCBin, pub: GstPublisher, oferta: String) {
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
