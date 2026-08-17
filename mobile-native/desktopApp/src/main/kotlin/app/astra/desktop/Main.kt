package app.astra.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.astra.desktop.auth.AuthRepository
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.di.appModule
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.net.AtividadePublicador
import app.astra.desktop.net.Servidor
import app.astra.desktop.net.DataUriMapper
import app.astra.desktop.net.RelativeUrlMapper
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.update.UpdateService
import app.astra.desktop.voice.Transmitindo
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import androidx.compose.foundation.LocalContextMenuRepresentation
import app.astra.desktop.ui.AstraTextContextMenu
import app.astra.desktop.ui.AstraTitleBar
import app.astra.desktop.ui.EmblemaDaBarra
import app.astra.desktop.ui.LocalReduceMotion
import app.astra.desktop.ui.LocalRenderPrefs
import app.astra.desktop.ui.LocalWindowActive
import app.astra.desktop.ui.RenderPrefs
import app.astra.desktop.ui.ServidorAcordandoStrip
import app.astra.desktop.ui.LoginScreen
import app.astra.desktop.ui.OnboardingScreen
import app.astra.desktop.ui.ShellScreen
import app.astra.desktop.ui.StarField
import app.astra.desktop.ui.auroraBackground

import app.astra.desktop.ui.UpdaterGate
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.shared.AstraShared
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import okio.Path.Companion.toPath
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import zed.rainxch.rikkaui.foundation.RikkaColors
import zed.rainxch.rikkaui.foundation.RikkaTheme

// Coletor ativo (so pro log de boot): o nome do GC vem do proprio MXBean, entao o
// log conta a VERDADE do runtime empacotado, não o que achamos que passamos.
private fun gcName(): String = runCatching {
    java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
        .joinToString("+") { it.name }
}.getOrDefault("?")

// Grava o diagnostico de boot num ARQUIVO. O println sozinho não servia: app de
// janela no Windows (jpackage) não tem console anexado, entao a linha ia pro nada —
// ninguem conseguia ler. Fica em %LOCALAPPDATA%\Astra\diagnostico.txt (mesma pasta
// do cache de imagens). Sobrescreve a cada abertura: e um retrato do boot atual.
// Onde o crash nativo cai: a JVM grava o hs_err na pasta de TRABALHO do processo,
// que no pacote do jpackage e a pasta do Astra.exe. Sem jpackage (Gradle), e de
// onde o build rodou.
private fun pastaDaInstalacao(): String =
    System.getProperty("jpackage.app-path")?.let { java.io.File(it).parent }
        ?: System.getProperty("user.dir").orEmpty().ifBlank { "?" }

// A placa que desenha a INTERFACE, escolhida em Configuracoes > Desempenho.
//
// TEM QUE SER AQUI, antes de `application {}`. O Skiko le esta propriedade UMA vez, no
// instante em que cria a primeira janela, e nunca mais olha -- por isso a tela de
// configuracoes avisa que esta metade so vale no proximo arranque. Trocar depois nao
// falharia com erro: simplesmente nao teria efeito, que e pior.
//
// O Skiko so aceita tres respostas -- automatico, integrada, dedicada -- e nao um
// aparelho especifico. Entao a escolha do dono, que e por PLACA, vira um dos tres. A parte
// do video nao passa por aqui e usa o aparelho exato.
private fun escolherPlacaDaInterface() = runCatching {
    val prefs = GlobalContext.get().get<DesktopPrefs>()
    val placa = Placas.porId(prefs.state.value.placaVideo) ?: return@runCatching
    // PEDIR A PLACA QUE NAO DESENHA O MONITOR DEIXA O APP MAIS LENTO, nao mais rapido.
    //
    // Num notebook hibrido a dedicada renderiza, mas quem apresenta na tela e a
    // integrada: cada quadro desenhado na dedicada tem que ser COPIADO de volta pro
    // adaptador do monitor antes de aparecer. A copia atravessa o PCIe todo frame, e o
    // ganho de desenhar numa placa mais forte vai embora na conta — o dono ligou a
    // dedicada e sentiu o app ficar MENOS fluido, que e exatamente o previsto.
    //
    // Entao a escolha se apaga sozinha em vez de ficar valendo em silencio. Apagar e nao
    // so ignorar: se ficasse gravada, a tela de configuracoes continuaria mostrando uma
    // opcao marcada que nao faz nada, e uma opcao que mente e pior que uma que falta.
    if (!placa.desenhaATela) {
        prefs.setPlacaVideo("")
        return@runCatching
    }
    System.setProperty("skiko.gpu.priority", if (placa.dedicada) "discrete" else "integrated")
}

// FOCO REAL DA JANELA, perguntado ao AWT — e o `window.isFocused` do inicio importa
// tanto quanto os eventos.
//
// A primeira tentativa gateava por `LocalWindowInfo.isWindowFocused` e nao economizava
// nada. Instrumentado, o motivo apareceu: quando o Astra abre ATRAS de outra janela ele
// nunca GANHA foco, entao nunca dispara o evento de perder — e um sinal que so existe por
// evento fica preso no valor inicial pra sempre. O `KeyboardFocusManager` tinha o mesmo
// defeito por outro caminho: disparava uma vez ao ganhar e nunca mais voltava a nulo.
//
// Terceira tentativa, e a que funciona: perguntar ao WINDOWS quem esta em primeiro plano
// e comparar com o nosso processo. Instrumentado, o AWT deste app registra "GANHOU foco"
// no arranque e NUNCA mais dispara nada — a janela e frameless e translucida, e nessa
// configuracao os eventos de foco do AWT simplesmente nao chegam. Um sinal que so existe
// por evento fica preso no valor inicial pra sempre, e foi por isso que as duas versoes
// anteriores mediram exatamente a mesma coisa com e sem foco.
//
// Comparar o PROCESSO, e nao a janela, tem um bonus: menu de contexto e popup do Compose
// sao janelas separadas mas NOSSAS, entao continuam contando como "o app esta na frente"
// e o ceu nao congela a cada clique com o botao direito — que era a objecao original a
// gatear por foco.
internal object FocoDoSistema {
    interface U32 : StdCallLibrary {
        fun GetForegroundWindow(): Pointer?
        fun GetWindowThreadProcessId(janela: Pointer?, pid: IntByReference): Int
        fun AllowSetForegroundWindow(pid: Int): Boolean
        companion object {
            val I: U32? = runCatching {
                Native.load("user32", U32::class.java, W32APIOptions.DEFAULT_OPTIONS)
            }.getOrNull()
        }
    }

    // CEDE A VEZ DE IR PRA FRENTE a outro processo.
    //
    // O Windows não deixa um programa qualquer roubar a frente de quem você está
    // usando — e faz muito bem. A consequência é que um processo que ABRE outro não
    // passa esse direito adiante automaticamente: o filho nasce atrás de tudo.
    //
    // Era exatamente isso na atualização: o Astra velho abria o novo e saía, e a
    // janela nova aparecia ATRÁS do navegador. Da poltrona parecia que o app tinha
    // sumido ou ficado só na bandeja.
    //
    // Isto só funciona quando QUEM CEDE está na frente — que é o caso normal (você
    // acabou de abrir o Astra e ele se atualizou). Se o Astra estava atrás, ninguém
    // tem direito nenhum pra ceder, e o filho nasce atrás também. Isso é o correto:
    // app que se atualiza sozinho no fundo não deveria pular na sua frente.
    fun cederAFrenteA(pid: Long) {
        runCatching { U32.I?.AllowSetForegroundWindow(pid.toInt()) }
    }

    private val meuPid = runCatching { ProcessHandle.current().pid().toInt() }.getOrDefault(-1)

    // "Alguma janela DESTE processo esta em primeiro plano?" — inclui popup e menu, que
    // sao janelas separadas mas nossas.
    fun appNaFrente(): Boolean {
        val u = U32.I ?: return true // sem JNA nao ha como saber; nunca congelar e o seguro
        val janela = u.GetForegroundWindow() ?: return false
        val dono = IntByReference()
        u.GetWindowThreadProcessId(janela, dono)
        return dono.value == meuPid
    }
}

@Composable
private fun lembrarFocoDoApp(): Boolean {
    var foco by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            foco = FocoDoSistema.appNaFrente()
            // 400ms: uma chamada de user32 nesse ritmo e ruido (microssegundos), e o olho
            // nao percebe o ceu voltando a andar 4 decimos depois do clique. Poll e feio,
            // mas e o unico sinal que se provou confiavel aqui — ver o paragrafo abaixo.
            delay(400)
        }
    }
    return foco
}

private fun writeDiagnostics() = runCatching {
    val os = System.getProperty("os.name").orEmpty()
    val dir = CrashLog.dataDir()
    val rt = Runtime.getRuntime()
    val txt = buildString {
        appendLine("Astra — diagnostico de boot")
        appendLine("quando       : ${java.time.LocalDateTime.now()}")
        appendLine("versao       : ${System.getProperty("astra.version") ?: "dev"}")
        appendLine("render (Skia): ${org.jetbrains.skiko.SkikoProperties.renderApi}")
        appendLine("   ^ SOFTWARE_* aqui = a CPU esta desenhando cada pixel (causa de engasgo)")
        // QUAL PLACA DESENHA A INTERFACE. Sem esta linha, "o Astra esta lagado" numa
        // maquina de duas placas e uma frase sem resposta possivel: o Skiko sem escolha
        // explicita cai em "Auto", e Auto pega o adaptador padrao -- que num notebook
        // hibrido e a INTEGRADA, com a dedicada parada do lado. Adivinhar isso custa uma
        // sessao; ler custa uma linha.
        appendLine("placa (pedido): ${System.getProperty("skiko.gpu.priority") ?: "auto (o Skiko decide)"}")
        Placas.todas.forEach {
            val papel = if (it.desenhaATela) "desenha a tela" else "so renderiza"
            appendLine("placa        : ${it.nome} — $papel, ${if (it.dedicada) "dedicada" else "integrada"}")
        }
        appendLine("GC           : ${gcName()}")
        appendLine("heap maximo  : ${rt.maxMemory() / 1024 / 1024} MB")
        appendLine("nucleos      : ${rt.availableProcessors()}")
        appendLine("java         : ${System.getProperty("java.version")}")
        appendLine("SO           : $os ${System.getProperty("os.version")}")
        appendLine()
        appendLine("Fechou sozinho? o motivo fica em falhas.txt, nesta mesma pasta.")
        // O nome e o lugar do arquivo sao os que a JVM usa DE VERDADE — conferidos
        // num crash real. O texto antigo inventava "falha-jvm-*.log na pasta do
        // app", e procurar por um arquivo que nao existe com esse nome e pior do
        // que nao ter dica nenhuma: parece que nao houve registro.
        appendLine("(sem falhas.txt = a JVM morreu por fora, em código nativo. O laudo é")
        appendLine(" hs_err_pid<numero>.log, na pasta da instalação — ${pastaDaInstalacao()})")
        // Terceiro caso, e o mais traicoeiro dos tres: morte NATIVA SEM laudo nenhum.
        // Quando o GLib aborta, ele desliga o relatorio de falhas do Windows antes de
        // morrer, e como o app nao tem console a mensagem se perde. Foi assim que a call
        // com o motor novo derrubou o Astra tres vezes sem deixar um bilhete. O gst.txt
        // existe justamente pra esse caso.
        appendLine("(nem falhas.txt nem hs_err, e estava numa call? veja gst.txt, aqui do lado.)")
    }
    java.io.File(dir, "diagnostico.txt").writeText(txt)
    println(txt)
}

// QUAL JANELA SOMOS: a principal, ou uma segunda conta aberta pra teste.
//
// Tres lugares precisam saber disto e precisam CONCORDAR — a trava de instancia única,
// a pasta da sessão e o atualizador. Quando cada um lia a flag por conta própria, bastava
// um deles enxergar diferente pra sair um caso absurdo: duas janelas com a MESMA conta,
// ou a segunda se atualizando por cima da instalação principal.
//
// Le a variável de ambiente ASTRA_MULTI **e** a propriedade -Dastra.multi. A variável é
// a que importa hoje: ela é o único canal que atravessa o Astra.exe do jpackage sem
// editar o Astra.cfg de dentro da instalação — e era editar o cfg que obrigava a manter
// uma CÓPIA inteira do app em disco, cópia que vivia atrasada uma versão. A propriedade
// fica pro modo dev (`./gradlew :desktopApp:run -Pastra.multi`).
object Multi {
    // "1", "2", "3"… — vira o apelido da pasta de sessão. null = janela principal.
    val slot: String? =
        System.getProperty("astra.multi")?.let { if (it.isBlank() || it == "true") "1" else it }
            ?: System.getenv("ASTRA_MULTI")?.takeIf { it.isNotBlank() }

    val ligado: Boolean get() = slot != null
}

// Instancia única: lock por ServerSocket no loopback. Se já tem Astra rodando (a
// porta esta ocupada), sinaliza o processo existente pra aparecer e ESTE sai — sem
// dois apps na bandeja. O primeiro escuta e traz a janela pra frente ao ser tocado.
object SingleInstance {
    private const val PORT = 47821
    val activate = MutableStateFlow(0)
    private var server: ServerSocket? = null

    // Abre um SEGUNDO Astra na mesma maquina, com sessão propria:
    //   wscript C:\Astra\launch.vbs 2      (o atalho "Astra (2a conta)")
    //   ./gradlew :desktopApp:run -Pastra.multi
    //
    // Por que isto existe: a maioria dos bugs que aparecem aqui e do tipo
    // "funciona pra quem fez a ação, não funciona pro outro" — canal novo que não
    // aparecia, presenca congelada, status que não propagava, membro que não
    // surgia. Nenhum deles e azar: e consequencia de so dar pra testar com UMA
    // conta. Com duas janelas lado a lado, cada um desses aparece em segundos, na
    // hora de escrever o codigo, em vez de semanas depois pela boca de um amigo.
    val multi: Boolean get() = Multi.ligado

    // Solta a trava. Existe pro AUTO-UPDATE: o processo velho abre o Astra novo e
    // so depois morre — se ele ainda estivesse segurando a porta, o novo concluiria
    // "já tem um Astra aberto" e sairia sozinho. Fechava tudo e nao reabria nada.
    fun release() {
        runCatching { server?.close() }
        server = null
    }

    // true = somos a instancia primaria; false = já tem uma (sinalizamos, hora de sair).
    fun acquireOrSignal(): Boolean = if (multi) true else try {
        server = ServerSocket().also { s ->
            // Reaproveitar o endereco: sem isto, a porta pode ficar presa por alguns
            // segundos depois de um fechamento com conexao aceita — justo a janela em
            // que o auto-update reabre o app.
            s.reuseAddress = true
            s.bind(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 1)
            thread(isDaemon = true, name = "astra-single-instance") {
                while (!s.isClosed) runCatching { s.accept().close(); activate.value++ }
            }
        }
        true
    } catch (e: IOException) {
        // Bind falhou — mas isso NAO prova que ha outro Astra. Firewall, porta tomada
        // por outro programa ou socket preso do boot anterior dao o mesmo IOException,
        // e antes o app simplesmente SUMIA nesses casos (um "fecha do nada" perfeito:
        // sem janela, sem erro, sem log). So sai se alguem de fato atender do outro
        // lado; sem resposta, seguimos como primaria mesmo sem o lock.
        val existe = runCatching {
            Socket().use { it.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 800) }
        }.isSuccess
        !existe
    }
}

// Marca que este processo foi aberto pela ATUALIZACAO, e não por uma pessoa. So
// nesse caso o app pede pra ir pra frente — num boot normal a janela ja nasce na
// frente porque foi voce que abriu, e pedir de novo seria um app que se impoe.
const val ARG_POS_ATUALIZACAO = "--depois-da-atualizacao"

// Nascer direto na bandeja, sem janela. Quem pede isto é o arranque do Windows
// (ver InicioComWindows) — abrir sessão e levar uma janela na cara de um app que
// você não mandou abrir agora é o oposto do que "abrir junto" deveria significar.
const val ARG_MINIMIZADO = "--minimizado"

fun main(args: Array<String>) {
    val voltandoDeAtualizacao = args.any { it == ARG_POS_ATUALIZACAO }
    val nascerEscondido = args.any { it == ARG_MINIMIZADO }
    // ANTES de tudo: sem isto, excecao não tratada mata o app em silencio (jpackage
    // não tem console) — era o "fecha do nada" sem rastro nenhum.
    CrashLog.install()
    // Identidade do processo pro Windows. TEM que vir antes de qualquer coisa
    // grafica: o Windows carimba a identidade quando o icone de bandeja nasce, e
    // sem ela o Astra e um app anonimo — o aviso sai sem dono e nao aparece em
    // Configuracoes > Notificacoes pra ninguem ligar ou desligar.
    WindowsAppId.aplicar()
    // Segundo processo: pede pro Astra aberto aparecer e encerra aqui mesmo.
    if (!SingleInstance.acquireOrSignal()) return
    startKoin { modules(appModule) }
    escolherPlacaDaInterface()
    // Retrato do boot (API grafica do Skia, GC, heap) num arquivo legivel.
    writeDiagnostics()
    // Avisar o servidor ao sair, pra a pessoa ficar offline na hora em vez de
    // depois do timeout de ping. Aqui e nao dentro do application{}: precisa valer
    // pra qualquer saida, inclusive as que nunca passam pela janela.
    GlobalContext.get().get<DesktopSocket>().registrarDespedida()
    application {
        // Fechar a janela NAO mata o app: minimiza pra bandeja (decisao do dono).
        var windowVisible by remember { mutableStateOf(!nascerEscondido) }
        val state = rememberWindowState(width = 1280.dp, height = 820.dp)
        // Transparencia e param de CRIACAO da janela -> le a pref UMA vez no boot
        // (Settings > Desempenho avisa "aplica ao reiniciar"). Opaca = mais leve.
        val transparentWindow = remember { GlobalContext.get().get<DesktopPrefs>().state.value.windowTransparent }
        // Fechar o X: encerra de vez (sem bandeja) ou minimiza pra bandeja, conforme a
        // pref (Settings > Desempenho). Reativa -> togglar aplica na hora. Ligado, a
        // Tray nem e criada = zero presenca em segundo plano ao fechar.
        val topPrefState by remember { GlobalContext.get().get<DesktopPrefs>() }.state.collectAsState()
        val exitOnClose = topPrefState.exitOnClose
        val onCloseApp = { if (exitOnClose) exitApplication() else { windowVisible = false } }
        // Logo real do Astra (planeta) — mesma do PWA/favicon do site.
        val appIcon = painterResource("astra-icon.png")
        // Alça pros avisos da bandeja. Criada aqui, e não dentro da bandeja, porque
        // quem avisa (o ShellScreen) nasce antes de o ícone existir — ver Bandeja.
        val bandeja = remember { Bandeja() }

        // Outro processo tentou abrir o Astra -> traz esta janela (a única) pra frente.
        val activate by SingleInstance.activate.collectAsState()
        LaunchedEffect(activate) {
            if (activate > 0) {
                windowVisible = true
                state.isMinimized = false
            }
        }

        // Auto-update: gate de boot (janelinha estilo Discord) so no app instalado;
        // dev/IDE pula direto pro app. Tema aplicado já aqui -> o gate (logo +
        // estrelas orbitando) sai no accent que o usuário escolheu.
        val updater = remember { GlobalContext.get().get<UpdateService>() }
        val bootPrefs = remember { GlobalContext.get().get<DesktopPrefs>().state.value }
        LaunchedEffect(Unit) {
            // Contraste ANTES do tema: o apply() re-deriva as bordas a partir dele.
            Obsidian.aplicarContraste(bootPrefs.altoContraste)
            Obsidian.apply(bootPrefs.accentId, bootPrefs.bgId)
        }
        // Nascendo escondido, o gate NÃO aparece: ele é uma janela alwaysOnTop, e
        // pular na frente de quem acabou de ligar o computador é exatamente o que
        // "abrir minimizado" pediu para não acontecer. Não se perde a atualização —
        // a ronda logo abaixo continua procurando enquanto o app estiver de pé.
        var gateDone by remember { mutableStateOf(!updater.installed || nascerEscondido) }
        // Ronda: o app deixa de depender de reiniciar pra saber que saiu versao
        // nova. Vive no escopo da janela — some junto com ela.
        val escopoDaJanela = rememberCoroutineScope()
        LaunchedEffect(Unit) { updater.iniciarRonda(escopoDaJanela) }
        // Faxina das versoes antigas, 20s depois de abrir: se este pacote estiver
        // quebrado e o app morrer antes disso, a versao anterior sobrevive e o
        // launcher volta pra ela.
        LaunchedEffect(Unit) { updater.agendarFaxina(escopoDaJanela) }
        // Pergunta ao /health se a API esta de pe. Nao acelera o arranque em nada — so
        // permite que a tela diga "acordando o servidor" em vez de ficar parada calada
        // durante o minuto que a hospedagem gratuita leva pra religar.
        LaunchedEffect(Unit) { Servidor.vigiar(escopoDaJanela) }
        // Modo transmissão. O laço nasce sempre, mas a varredura de processos só
        // roda com o automático ligado — desligado, ele não olha nada.
        LaunchedEffect(Unit) { ModoTransmissao.vigiar(escopoDaJanela, GlobalContext.get().get()) }
        // Atividade ("o que estou usando"). O laço nasce sempre, mas confere a
        // preferência antes de olhar o sistema — desligado, ele não lê nada.
        LaunchedEffect(Unit) {
            AtividadePublicador(
                GlobalContext.get().get(),
                GlobalContext.get().get(),
            ).iniciar(escopoDaJanela)
        }

        // A BANDEJA E SEMPRE CRIADA, e isso nao e detalhe de enfeite: no Windows o
        // aviso do sistema SAI DO ICONE DA BANDEJA. Sem icone nao existe dono pro aviso,
        // e o sendNotification vai pro vazio, calado.
        //
        // Antes ela so nascia quando o X minimizava ("com exitOnClose ligado um icone
        // seria presenca inutil em segundo plano"). O raciocinio parecia certo e estava
        // errado pelo meio: com exitOnClose ligado nao ha segundo plano nenhum — o app
        // encerra no X — e o que aquela condicao desligava de fato era a NOTIFICACAO.
        // Quem tinha "fechar de vez" marcado nunca recebeu um aviso de mensagem, e o
        // botao "testar notificação" respondia "mandei — olhe o canto da tela" sobre uma
        // mensagem que nunca teve por onde sair. Era o caso do dono.
        //
        // O item "Abrir o Astra" so faz sentido quando ha janela escondida pra reabrir;
        // com exitOnClose a bandeja fica so com o icone e o "Sair".
        // MENU DESENHADO PELO ASTRA, e não o do Windows. O `Tray` do Compose usa o
        // `PopupMenu` do AWT, que é um menu Win32: quem pinta é o sistema, e ele não
        // aceita cor, fonte, canto nem espaçamento. Era o único pedaço do app que
        // não parecia o app. Ver BandejaDoAstra.kt.
        BandejaComMenu(
            bandeja = bandeja,
            dica = "Astra",
            aoAtivar = { windowVisible = true }, // duplo clique no ícone reabre
            itens = {
                buildList {
                    if (!exitOnClose) add(ItemDaBandeja("Abrir o Astra") { windowVisible = true })
                    add(ItemDaBandeja("Sair", perigo = true) { exitApplication() })
                }
            },
        )

        // Gate de update primeiro: verifica a versão (logo + estrelas girando) e,
        // se houver nova, baixa com barra de progresso; senao segue pro app. So
        // enquanto não terminou (gateDone) — depois some e o app abre normal.
        if (!gateDone) {
            val gateState = rememberWindowState(
                width = 380.dp,
                height = 470.dp,
                position = WindowPosition(Alignment.Center),
            )
            Window(
                onCloseRequest = { gateDone = true },
                title = "Astra",
                icon = appIcon,
                state = gateState,
                undecorated = true,
                transparent = true,
                resizable = false,
                alwaysOnTop = true,
            ) {
                UpdaterGate(updater, bootPrefs.reduceMotionEff, onDone = { gateDone = true })
            }
            return@application
        }

        Window(
            onCloseRequest = onCloseApp,
            title = "Astra",
            icon = appIcon,
            state = state,
            visible = windowVisible,
            undecorated = true, // frameless: a barra-titulo obsidiana e nossa
            // Fundo da janela transparente so pra dar cantos arredondados ao
            // conteudo (polish). Toggle em Settings > Desempenho (aplica ao reiniciar).
            transparent = transparentWindow,
        ) {
            // A outra metade do conserto da atualizacao (ver UpdateService): o processo
            // velho cedeu o direito de ir pra frente, e aqui a janela nova o USA. Sem
            // este pedido o direito cedido nao move nada — ele autoriza, nao levanta.
            //
            // O respiro existe porque `toFront` so vale depois de a janela existir de
            // verdade pro sistema; chamado no mesmo instante da composicao, cai no vazio.
            if (voltandoDeAtualizacao) {
                LaunchedEffect(Unit) {
                    delay(400)
                    runCatching { window.toFront(); window.requestFocus() }
                }
            }
            // Coil global: data-URIs (avatares no banco) + URLs relativas /uploads.
            // + cache em disco (300MB) pra não rebaixar a mesma imagem toda vez —
            // vive no cache do SO (fora da instalacao, sobrevive a updates). Coil
            // faz a eviction LRU sozinho ao passar do teto.
            setSingletonImageLoaderFactory { ctx ->
                ImageLoader.Builder(ctx)
                    .components {
                        add(DataUriMapper())
                        add(RelativeUrlMapper(AstraShared.BASE_URL))
                    }
                    // TETO do cache de imagens EM MEMORIA. Sem isto o Coil usa a regra
                    // dele (~25% da memoria do app) — num heap de 512MB sao ~128MB so de
                    // bitmap decodificado, e era parte do "Astra incha sozinho". 48MB
                    // segura avatares e previas de sobra; o resto vem do cache em DISCO
                    // abaixo (que não custa RAM).
                    .memoryCache {
                        coil3.memory.MemoryCache.Builder().maxSizeBytes(48L * 1024 * 1024).build()
                    }
                    .diskCache {
                        val home = System.getProperty("user.home")
                        val os = System.getProperty("os.name").orEmpty()
                        val base = when {
                            os.startsWith("Windows", true) -> System.getenv("LOCALAPPDATA") ?: "$home\\AppData\\Local"
                            os.contains("Mac", true) -> "$home/Library/Caches"
                            else -> System.getenv("XDG_CACHE_HOME") ?: "$home/.cache"
                        }
                        DiskCache.Builder()
                            .directory(java.io.File(base, "Astra/image-cache").absolutePath.toPath())
                            .maxSizeBytes(300L * 1024 * 1024)
                            .build()
                    }
                    .build()
            }

            // 1o run: garante um atalho do Astra na area de trabalho (Windows).
            LaunchedEffect(Unit) { DesktopShortcut.ensureWindows() }

            val koin = GlobalContext.get()
            // Foco da janela (nao e o mesmo que visivel): alimenta a regra de aviso.
            val windowInfo = LocalWindowInfo.current
            // Perguntado ao AWT, com o estado inicial lido do sistema — e o que decide se
            // o ceu anima. Ver `lembrarFocoDoApp`.
            val janelaComFoco = lembrarFocoDoApp()
            val store = remember { koin.get<SessionStore>() }
            val authRepo = remember { koin.get<AuthRepository>() }
            var session by remember { mutableStateOf(store.load()) }
            // 1o acesso: takeover de onboarding, disparado SO apos criar conta
            // (isNew no onLoggedIn). Nunca re-onboarda quem já tinha sessão/logou.
            var needsOnboarding by remember { mutableStateOf(false) }
            // Overlays disparados pelo titlebar (lupa/sino) mas renderizados no
            // shell (onde vive o vm de navegacao). Estado hasteado aqui no meio.
            var searchOpen by remember { mutableStateOf(false) }
            var notifOpen by remember { mutableStateOf(false) }
            var desejosOpen by remember { mutableStateOf(false) }
            var missoesOpen by remember { mutableStateOf(false) }
            var notifUnread by remember { mutableStateOf(0) }

            // O círculo com o número, colado no ícone da barra de tarefas. Mora
            // AQUI e não no ShellScreen porque precisa da janela do AWT, e porque
            // o emblema tem que sobreviver a qualquer troca de tela lá dentro —
            // ele é do aplicativo, não de uma página.
            EmblemaDaBarra(window, notifUnread)

            // Tema do usuário (Settings > Aparencia): aplica o par accent/fundo nos
            // tokens reativos do Obsidian -> o app inteiro recolore ao vivo.
            val prefs = remember { koin.get<DesktopPrefs>() }
            val prefState by prefs.state.collectAsState()
            LaunchedEffect(prefState.accentId, prefState.bgId) {
                Obsidian.aplicarContraste(prefState.altoContraste)
                Obsidian.apply(prefState.accentId, prefState.bgId)
            }

            // Cantos arredondados so com a janela solta E translucida; maximizada
            // ou opaca volta ao reto (senao sobra fresta/canto preto).
            val rounded = transparentWindow && state.placement == WindowPlacement.Floating
            val windowShape = if (rounded) RoundedCornerShape(10.dp) else RectangleShape

            // RikkaUI e CMP (foundation-only): mesmo tema do mobile, tokens obsidiana.
            // Reconstruido AQUI (não top-level) pra ler os tokens reativos do Obsidian
            // -> os componentes RikkaUI recolorem junto quando o tema muda.
            //
            // O menu de botao-direito dos campos de texto entra AQUI, e não dentro de
            // cada campo: assim vale pro compositor do chat, pra busca, pro login e pra
            // qualquer campo futuro de uma vez so.
            CompositionLocalProvider(LocalContextMenuRepresentation provides AstraTextContextMenu) {
            RikkaTheme(colors = obsidianRikkaColors()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(windowShape)
                        .background(Obsidian.void)
                        .then(
                            if (rounded) Modifier.border(1.dp, Obsidian.borderDim.copy(alpha = 0.6f), windowShape)
                            else Modifier,
                        ),
                ) {
                    AstraTitleBar(
                        state = state,
                        onClose = onCloseApp,
                        showActions = session != null && !needsOnboarding,
                        notifUnread = notifUnread,
                        onOpenSearch = { searchOpen = true },
                        onOpenNotifications = { notifOpen = !notifOpen },
                        onOpenMissions = { missoesOpen = !missoesOpen },
                        onOpenDesejos = { desejosOpen = !desejosOpen },
                        atualizacao = updater,
                    )
                    // Logo abaixo da barra de titulo, acima de tudo: vale no login e no
                    // shell, porque a espera pela API acontece nos dois.
                    ServidorAcordandoStrip()
                    // ATIVA = VISIVEL, NAO MINIMIZADA **E COM O APP NA FRENTE**.
                    //
                    // O foco entrou aqui porque congelar so o ceu nao bastava: medido, com
                    // o ceu ja parado o app ainda gastava 0,28 nucleo em segundo plano. O
                    // motivo e que ALGUEM continuava pedindo quadro, e cada quadro repinta
                    // a aurora inteira. Quem pedia era o resto do enfeite que le este
                    // mesmo sinal — em especial o pulso do marcador de nao-lida, que e um
                    // relogio POR CANAL nao lido, e o dono tem varios.
                    //
                    // Quem mais depende disto (conferido antes de mexer): a estrela de
                    // quem fala na call e a PREVIA da propria transmissao. Os dois so
                    // fazem sentido com alguem olhando, e a previa desligada nao muda nada
                    // do que os outros recebem. O video dos outros nao passa por aqui.
                    CompositionLocalProvider(
                        LocalWindowActive provides
                            (windowVisible && !state.isMinimized && janelaComFoco),
                    ) {
                    // O CEU DA JANELA: aurora + estrelas atrás do login E do shell.
                    // Morava dentro do ShellScreen, e o login pintava a propria aurora
                    // num painel de 45% — como o uv do shader e normalizado pelo
                    // tamanho, eram imagens diferentes e a entrada saltava. Aqui em
                    // cima ela não se mexe quando o conteudo troca: entra-se NO app,
                    // não se troca de tela. E fica um shader so, nunca dois.
                    Box(Modifier.fillMaxSize()) {
                    // Pulso de login: o ceu "respira" uma vez quando você entra. Lido
                    // no draw da aurora (não recompoe); disparado no onLoggedIn abaixo.
                    val auroraPulse = remember { Animatable(0f) }
                    val pulseScope = rememberCoroutineScope()
                    // O ENFEITE PARA QUANDO NINGUEM ESTA OLHANDO — e este e o maior custo
                    // parado do app inteiro, medido.
                    //
                    // Com a conversa carregada e nada acontecendo:
                    //     ceu ligado, janela visivel ....... 0,35 nucleo
                    //     ceu desligado, janela visivel .... 0,037 nucleo
                    //     minimizado ....................... 0,047 nucleo
                    // Ou seja: a aurora sozinha custa ~0,31 nucleo o tempo todo, inclusive
                    // com o Astra atras do navegador. O perfil (JFR) mostra onde: 90% das
                    // amostras da thread do skiko estao em Direct3DContextHandler.flush,
                    // esperando a GPU — a janela apresenta a 165Hz (a taxa do monitor)
                    // porque ha sempre um frame novo pedido.
                    //
                    // "Nao minimizada" nao e o mesmo que "visivel": o Windows nao para de
                    // entregar frames pra janela coberta.
                    //
                    // A POLITICA, decidida pelo dono: NA FRENTE sem teto nenhum — o Astra
                    // usa o processador e a placa que precisar pra tudo ficar liso. ATRAS,
                    // o mais perto de zero possivel. O unico recurso com teto e a RAM.
                    //
                    // E o gate de foco que sustenta essa conta: sem ele, "sem teto"
                    // significaria 0,42 nucleo o dia inteiro em segundo plano. Ver
                    // `lembrarFocoDoApp` pra saber por que o sinal vem do Windows e nao do
                    // AWT — as duas tentativas em cima do AWT nao economizaram nada.
                    //
                    // So aqui dentro: LocalWindowActive continua significando visibilidade
                    // pro resto (video de call nao pode congelar porque a pessoa clicou
                    // noutra janela do segundo monitor).
                    // E O `LocalRenderPrefs` TEM QUE VIR DAQUI, nao do ShellScreen.
                    //
                    // Ele e provido la embaixo, dentro do ShellScreen — e o ceu mora AQUI
                    // EM CIMA, acima daquele provedor na arvore. Resultado: a aurora e as
                    // estrelas sempre leram o valor PADRAO (`RenderPrefs()`), ou seja
                    // 3 oitavas e teto de fps ZERO. Na pratica, dois ajustes de
                    // Configuracoes › Desempenho nao faziam nada ha tempo:
                    //   - "qualidade da aurora" (baixa/media/alta) — sempre alta;
                    //   - "teto de FPS" (60/30) — sempre livre, ou seja a taxa do monitor.
                    // O dono estava com aurora em "baixa" e o app desenhava em alta.
                    //
                    // O ceu subiu pro Main quando o login e o shell passaram a dividir o
                    // mesmo ceu; o provedor ficou pra tras, e como CompositionLocal cai no
                    // default em silencio, nada quebrou visivelmente — so parou de obedecer.
                    // O mesmo vale pro ceu e pro login, que vivem acima do ShellScreen:
                    // fora da frente, "reduzir movimento" ligado. Ver o comentario longo
                    // no provedor equivalente do ShellScreen.
                    //
                    // E TRANSMITINDO O CEU SAI DA FRENTE. O dono relatou a transmissao
                    // caindo de 47 pra 35 fps na 0.2.18 — a versao em que o teto de fps do
                    // ceu saiu e ele voltou a rodar na taxa do monitor (165Hz). Neste
                    // notebook a MESMA placa integrada desenha a tela, captura os quadros e
                    // comprime; um shader de tela cheia a 165Hz disputa exatamente com o
                    // compressor. A transmissao e o produto, o fundo e enfeite — enquanto
                    // uma esta no ar, o outro espera.
                    val transmitindo by Transmitindo.ativo.collectAsState()
                    CompositionLocalProvider(
                        LocalRenderPrefs provides
                            RenderPrefs(prefState.auroraQuality.octaves, prefState.uiFps.cap),
                        LocalReduceMotion provides
                            (prefState.reduceMotionEff || !janelaComFoco || transmitindo),
                    ) {
                        if (prefState.auroraOn) {
                            // Camada propria (graphicsLayer): so ela invalida por frame —
                            // os paineis translucidos por cima não redesenham com o shader.
                            Box(Modifier.fillMaxSize().graphicsLayer {}.auroraBackground { auroraPulse.value })
                        } else {
                            Box(Modifier.fillMaxSize().background(Obsidian.void))
                        }
                        if (prefState.starsOn) StarField(Modifier.fillMaxSize())
                    }

                    // Entrada do Astra: um reveal único (uma vez por abertura) quando o
                    // conteudo aparece depois do gate — o app sobe com fade + escala
                    // sutil POR CIMA do ceu, que já esta aceso. GPU-only, ~520ms.
                    val reveal = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(520, easing = EaseOutStd)) }
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha = reveal.value
                            translationY = (1f - reveal.value) * 12.dp.toPx()
                            val sc = 0.99f + 0.01f * reveal.value
                            scaleX = sc
                            scaleY = sc
                        },
                    ) {
                        // Entrar no app = os paineis do login se dissolverem e os do
                        // shell aparecerem SOBRE o mesmo ceu, que não se mexe. Por
                        // isso Crossfade e não slide: o ceu ancora as duas telas, e
                        // qualquer deslocamento denunciaria que são telas diferentes.
                        Crossfade(
                            targetState = session,
                            animationSpec = tween(420, easing = EaseOutStd),
                            label = "entrada",
                        ) { s ->
                            if (s == null) {
                                LoginScreen(repo = authRepo, onLoggedIn = { sess, isNew ->
                                    session = sess
                                    if (isNew) needsOnboarding = true
                                    // O ceu respira: sobe na hora e decai em 900ms.
                                    pulseScope.launch {
                                        auroraPulse.snapTo(1f)
                                        auroraPulse.animateTo(0f, tween(900, easing = EaseOutSoft))
                                    }
                                })
                            } else {
                                // Onboarding (so no 1o acesso) e o shell dividem o mesmo
                                // ceu: crossfade entre eles, sem trocar de "tela".
                                Crossfade(
                                    targetState = needsOnboarding,
                                    animationSpec = tween(420, easing = EaseOutStd),
                                    label = "onboarding",
                                ) { onb ->
                                    if (onb) {
                                        OnboardingScreen(
                                            displayName = s.displayName,
                                            // Permitir "Avisos" = MANDAR um aviso: e o
                                            // unico jeito de o Windows registrar o app.
                                            // Pelo caminho de verdade (bandeja do SO) —
                                            // um toast desenhado dentro do app não
                                            // registraria nada.
                                            onTestarAviso = {
                                                bandeja.avisar("Astra", "Pronto — os avisos do Astra estão liberados.")
                                            },
                                            onDone = {
                                                store.setUiPref("onboarded:${s.userId}", "1")
                                                // Liga o checklist residual no palco (a outra metade do combo).
                                                store.setUiPref("checklist:${s.userId}", "1")
                                                // Ja viu a lista de permissões AQUI: o aviso
                                                // da primeira abertura (ShellScreen) seria a
                                                // mesma lista duas vezes seguidas.
                                                store.setUiPref("permsVistas", "1")
                                                needsOnboarding = false
                                            },
                                        )
                                    } else {
                                        ShellScreen(
                                            session = s,
                                            // Toast da bandeja so quando o app não esta na frente.
                                            // FOCO conta: antes exigia minimizado/bandeja, entao
                                            // com o Astra aberto atras de outra janela — o caso
                                            // comum — não vinha aviso nenhum.
                                            windowInactive = {
                                                !windowVisible || state.isMinimized || !windowInfo.isWindowFocused
                                            },
                                            notify = { title, body ->
                                                bandeja.avisar(title, body)
                                                // Em transmissão, o som não toca: ele
                                                // entra no áudio da gravação igual, e
                                                // "chegou mensagem agora" é informação
                                                // sobre você mesmo sem texto nenhum.
                                                if (ModoTransmissao.ativo.value) return@ShellScreen
                                                // Som JUNTO do aviso da bandeja, no mesmo
                                                // funil: quem decide QUANDO avisar é o
                                                // ShellScreen (só com a janela fora de
                                                // foco), e o som não pode ter uma regra
                                                // própria — tocar sem o aviso na tela, ou
                                                // com o app na frente, seria barulho sem
                                                // referente.
                                                tocarAvisoDeMensagem()
                                            },
                                            onLogout = {
                                                // O escopo e o da JANELA, e nao o do shell: o
                                                // shell sai da composicao no instante em que a
                                                // sessao vira nula, e um escopo morto
                                                // cancelaria o aviso de logout antes de ele sair.
                                                authRepo.logout(escopoDaJanela)
                                                session = null
                                            },
                                            searchOpen = searchOpen,
                                            onCloseSearch = { searchOpen = false },
                                            notifOpen = notifOpen,
                                            onCloseNotif = { notifOpen = false },
                                            desejosOpen = desejosOpen,
                                            onCloseDesejos = { desejosOpen = false },
                                            missoesOpen = missoesOpen,
                                            onAbrirMissoes = { missoesOpen = true },
                                            onCloseMissoes = { missoesOpen = false },
                                            onNotifUnread = { notifUnread = it },
                                        )
                                    }
                                }
                            }
                        }
                        // Banner de update (topo): lembrete quando adiado ("depois")
                        // ou achado na checagem manual — conduz o mesmo mini-fluxo.
                        // O aviso saiu do canto inferior direito e virou o ponto na
                        // barra-titulo (ver TitleBar.PontoDeAtualizacao). Manter os
                        // dois seria a mesma coisa dita em dois cantos da tela.
                    }
                    }
                    }
                }
            }
            }
        }
    }
}

// Mapeamento RikkaColors identico ao AstraTheme do mobile (Theme.kt), com os
// tokens obsidiana do desktop. Funcao (não val) pra ler os tokens reativos DENTRO
// da composicao -> recolore quando o tema muda.
private fun obsidianRikkaColors() = RikkaColors(
    background = Obsidian.raised,
    onBackground = Obsidian.text1,
    surface = Obsidian.overlay,
    onSurface = Obsidian.text1,
    primary = Obsidian.accent,
    onPrimary = Obsidian.textInv,
    secondary = Obsidian.hover,
    onSecondary = Obsidian.text1,
    muted = Obsidian.base,
    onMuted = Obsidian.text3,
    destructive = Obsidian.danger,
    onDestructive = Color.White,
    warning = Obsidian.warning,
    onWarning = Obsidian.textInv,
    success = Obsidian.success,
    onSuccess = Obsidian.textInv,
    border = Obsidian.borderMid,
    ring = Obsidian.accent,
    inverseSurface = Obsidian.text1,
    onInverseSurface = Obsidian.void,
    primaryTinted = Obsidian.accentDim,
    onPrimaryTinted = Obsidian.accent,
    destructiveTinted = Color(0x26E07A7A),
    onDestructiveTinted = Obsidian.danger,
)
