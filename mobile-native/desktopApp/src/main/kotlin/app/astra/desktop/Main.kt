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
import kotlinx.coroutines.awaitCancellation
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.loadImageBitmap
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
import app.astra.desktop.update.TentativasDeInstalar
import app.astra.desktop.update.Trocador
import app.astra.desktop.update.UpdateService
import app.astra.desktop.voice.QuemFala
import app.astra.desktop.voice.Transmitindo
import app.astra.desktop.xp.MissoesStore
import app.astra.desktop.xp.quantasProntas
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import androidx.compose.foundation.LocalContextMenuRepresentation
import app.astra.desktop.ui.AstraTextContextMenu
import app.astra.desktop.ui.DecodificadorNitido
import app.astra.desktop.ui.TelaDeCarregamento
import app.astra.desktop.ui.Quadros
import app.astra.desktop.ui.contandoQuadros
import app.astra.desktop.ui.AstraTitleBar
import app.astra.desktop.ui.EmblemaDaBarra
import app.astra.desktop.ui.LocalReduceMotion
import app.astra.desktop.ui.LocalRenderPrefs
import app.astra.desktop.ui.LocalJanelaNaTela
import app.astra.desktop.ui.LocalWindowActive
import app.astra.desktop.ui.RenderPrefs
import app.astra.desktop.ui.ServidorAcordandoStrip
import app.astra.desktop.ui.LoginScreen
import app.astra.desktop.ui.OnboardingScreen
import app.astra.desktop.ui.ShellScreen
import app.astra.desktop.ui.StarField
import app.astra.desktop.ui.UserStatus
import app.astra.desktop.ui.auroraBackground
import app.astra.desktop.ui.statusLabel

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
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import zed.rainxch.rikkaui.foundation.RikkaColors
import zed.rainxch.rikkaui.foundation.RikkaTheme

private fun gcName(): String = runCatching {
    java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
        .joinToString("+") { it.name }
}.getOrDefault("?")

private fun tetoDoCacheDeImagens(economia: Boolean): Long {
    val fatia = if (economia) 0.004 else 0.02
    val piso = if (economia) 12L else 32L
    val teto = if (economia) 24L else 160L
    val ram = runCatching {
        (java.lang.management.ManagementFactory.getOperatingSystemMXBean()
            as com.sun.management.OperatingSystemMXBean).totalMemorySize
    }.getOrNull() ?: 0L
    val mb = if (ram > 0) (ram / 1024 / 1024 * fatia).toLong() else piso
    return mb.coerceIn(piso, teto) * 1024 * 1024
}

private fun pastaDaInstalacao(): String =
    System.getProperty("jpackage.app-path")?.let { java.io.File(it).parent }
        ?: System.getProperty("user.dir").orEmpty().ifBlank { "?" }

internal val janelaAceitaTransparencia: Boolean by lazy {
    runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .isWindowTranslucencySupported(
                java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT,
            )
    }.getOrDefault(false)
}

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

    private const val QUALQUER_PROCESSO = -1

    fun cederAFrenteA(pid: Long) {
        runCatching { U32.I?.AllowSetForegroundWindow(pid.toInt()) }
    }

    fun cederAFrenteAQualquerUm() {
        runCatching { U32.I?.AllowSetForegroundWindow(QUALQUER_PROCESSO) }
    }

    private val meuPid = runCatching { ProcessHandle.current().pid().toInt() }.getOrDefault(-1)

    fun appNaFrente(): Boolean = Nativo.tentar("foco da janela") {
        val u = U32.I ?: return@tentar true
        val janela = u.GetForegroundWindow() ?: return@tentar false
        val dono = IntByReference()
        u.GetWindowThreadProcessId(janela, dono)
        dono.value == meuPid
    } ?: true
}

@Composable
private fun marcoDoArranque(passo: String) {
    remember(passo) { Arranque.marcar(passo) }
}

@Composable
private fun lembrarFocoDoApp(): Boolean {
    var foco by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            foco = FocoDoSistema.appNaFrente()
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
        if (Arranque.modoSeguro) {
            appendLine("MODO SEGURO  : ligado — janela opaca, desenho por CPU e conversa com o Windows desligada")
            appendLine("   ^ sem bateria, sem placas, sem atividade, sem foco e sem identidade na barra")
            appendLine("   ^ ligado porque uma abertura criou a janela e nao desenhou.")
            appendLine("     Segue ligado ate ser desligado em Configuracoes > Diagnostico.")
            appendLine("     arranque-anterior.txt guarda a trilha que falhou.")
        }
        appendLine("transparencia: ${if (janelaAceitaTransparencia) "aceita" else "NAO aceita — janela opaca"}")
        appendLine("   ^ NAO aceita e janela transparente = janela invisivel, so o icone na barra")
        appendLine("placa (pedido): ${System.getProperty("skiko.gpu.priority") ?: "auto (o Skiko decide)"}")
        Placas.todas.forEach {
            val papel = if (it.desenhaATela) "desenha a tela" else "so renderiza"
            appendLine("placa        : ${it.nome} — $papel, ${if (it.dedicada) "dedicada" else "integrada"}")
        }
        Nativo.recusados().forEach { appendLine("nativo recusado: $it") }
        appendLine("GC           : ${gcName()}")
        appendLine("heap maximo  : ${rt.maxMemory() / 1024 / 1024} MB")
        appendLine("nucleos      : ${rt.availableProcessors()}")
        appendLine("java         : ${System.getProperty("java.version")}")
        appendLine("SO           : $os ${System.getProperty("os.version")}")
        appendLine()
        appendLine("Abriu e nao mostrou nada? arranque.txt, aqui do lado, diz ate onde chegou.")
        appendLine("(a trilha da abertura ANTERIOR fica em arranque-anterior.txt, intacta.)")
        appendLine("Erro que a interface engoliu? saida.txt guarda tudo que o app imprimiu.")
        appendLine("Fechou sozinho? o motivo fica em falhas.txt, nesta mesma pasta.")
        appendLine("(sem falhas.txt = a JVM morreu por fora, em código nativo. O laudo é")
        appendLine(" hs_err_pid<numero>.log, na pasta da instalação — ${pastaDaInstalacao()})")
        appendLine("(nem falhas.txt nem hs_err, e estava numa call? veja gst.txt, aqui do lado.)")
    }
    java.io.File(dir, "diagnostico.txt").writeText(txt)
    println(txt)
}

object Multi {
    val slot: String? =
        System.getProperty("astra.multi")?.let { if (it.isBlank() || it == "true") "1" else it }
            ?: System.getenv("ASTRA_MULTI")?.takeIf { it.isNotBlank() }

    val ligado: Boolean get() = slot != null
}

private object Cadeado {
    private const val NOME = "Local\\Astra-copia-unica"
    private const val JA_EXISTE = 183

    private var punho: WinNT.HANDLE? = null

    fun segurar(): Boolean? = runCatching {
        if (punho != null) return@runCatching true
        val nucleo = Kernel32.INSTANCE
        val novo = nucleo.CreateMutex(null, false, NOME) ?: return@runCatching null
        if (nucleo.GetLastError() == JA_EXISTE) {
            nucleo.CloseHandle(novo)
            false
        } else {
            punho = novo
            true
        }
    }.getOrNull()

    fun soltar() {
        val aberto = punho ?: return
        punho = null
        runCatching { Kernel32.INSTANCE.CloseHandle(aberto) }
    }

    fun esperarAbrir(prazoMs: Long): Boolean {
        val fim = System.nanoTime() + prazoMs * 1_000_000L
        while (System.nanoTime() < fim) {
            when (segurar()) {
                true -> return true
                null -> return false
                false -> Thread.sleep(50)
            }
        }
        return segurar() == true
    }
}

object SingleInstance {
    private const val PORT = 47821
    private const val PRAZO_DA_RESPOSTA_MS = 5_000
    private const val PASSO_DA_ESPERA_MS = 50L
    private const val PRAZO_DO_CADEADO_MS = 4_000L
    private const val RESPOSTA_ATENDIDO = "atendido"
    private const val RESPOSTA_SUBINDO = "subindo"
    private const val ARQUIVO_DO_DONO = "dono.txt"
    private const val ARQUIVO_DO_AVISO = "aviso.sock"

    private enum class Resposta { ATENDIDO, NINGUEM, TRAVADO }

    val activate = MutableStateFlow(0)
    private val atendidos = AtomicInteger(0)
    @Volatile private var janelaViva = false
    private var ouvinte: ServerSocketChannel? = null

    val multi: Boolean get() = Multi.ligado

    private val fichaDoDono: File get() = File(CrashLog.dataDir(), ARQUIVO_DO_DONO)

    private fun enderecoDoAviso(): UnixDomainSocketAddress? = runCatching {
        UnixDomainSocketAddress.of(File(CrashLog.dataDir(), ARQUIVO_DO_AVISO).toPath())
    }.getOrNull()

    private fun enderecoDaPorta() = InetSocketAddress(InetAddress.getLoopbackAddress(), PORT)

    fun aJanelaRespondeu() { janelaViva = true }

    fun chamadoAtendido() { atendidos.incrementAndGet() }

    fun release() {
        runCatching { ouvinte?.close() }
        ouvinte = null
        Cadeado.soltar()
    }

    fun acquireOrSignal(): Boolean {
        if (multi) return true
        return when (Cadeado.segurar()) {
            true -> { abrirOAviso(); true }
            null -> semCadeado()
            false -> cederOuAssumir()
        }
    }

    private fun semCadeado(): Boolean {
        if (cedeuAoDono()) return false
        abrirOAviso()
        return true
    }

    private fun cederOuAssumir(): Boolean {
        if (cedeuAoDono()) return false
        if (Cadeado.esperarAbrir(PRAZO_DO_CADEADO_MS)) abrirOAviso()
        return true
    }

    private fun cedeuAoDono(): Boolean {
        FocoDoSistema.cederAFrenteAQualquerUm()
        when (perguntarAoDono()) {
            Resposta.ATENDIDO -> return true
            Resposta.TRAVADO -> encerrarOTravado()
            Resposta.NINGUEM -> Unit
        }
        return false
    }

    private fun abrirOAviso(): Boolean {
        val canal = porArquivo() ?: porPorta() ?: return false
        ouvinte = canal
        runCatching { fichaDoDono.writeText(ProcessHandle.current().pid().toString()) }
        thread(isDaemon = true, name = "astra-copia-unica") {
            while (canal.isOpen) runCatching { atender(canal.accept()) }
        }
        return true
    }

    private fun porArquivo(): ServerSocketChannel? {
        val onde = enderecoDoAviso() ?: return null
        return runCatching {
            Files.deleteIfExists(onde.path)
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).also { it.bind(onde) }
        }.getOrNull()
    }

    private fun porPorta(): ServerSocketChannel? = runCatching {
        ServerSocketChannel.open().also { it.bind(enderecoDaPorta(), 1) }
    }.getOrNull()

    private fun atender(cliente: SocketChannel) = cliente.use {
        val alvo = activate.value + 1
        activate.value = alvo
        val recado = if (!janelaViva) RESPOSTA_SUBINDO else esperarAtendimento(alvo)
        if (recado != null) it.write(ByteBuffer.wrap(recado.toByteArray()))
    }

    private fun esperarAtendimento(alvo: Int): String? {
        val fim = System.nanoTime() + PRAZO_DA_RESPOSTA_MS * 1_000_000L
        while (System.nanoTime() < fim) {
            if (atendidos.get() >= alvo) return RESPOSTA_ATENDIDO
            Thread.sleep(PASSO_DA_ESPERA_MS)
        }
        return null
    }

    private fun conectarAoDono(): SocketChannel? {
        enderecoDoAviso()?.let { onde ->
            runCatching { SocketChannel.open(onde) }.getOrNull()?.let { return it }
        }
        return runCatching { SocketChannel.open(enderecoDaPorta()) }.getOrNull()
    }

    private fun perguntarAoDono(): Resposta {
        val canal = conectarAoDono() ?: return Resposta.NINGUEM
        val cortador = thread(isDaemon = true, name = "astra-prazo-do-aviso") {
            runCatching { Thread.sleep(PRAZO_DA_RESPOSTA_MS.toLong()) }
            runCatching { canal.close() }
        }
        return try {
            val lidos = runCatching { canal.read(ByteBuffer.allocate(64)) }.getOrDefault(-1)
            if (lidos > 0) Resposta.ATENDIDO else Resposta.TRAVADO
        } finally {
            cortador.interrupt()
            runCatching { canal.close() }
        }
    }

    private fun encerrarOTravado(): Boolean {
        val pid = runCatching { fichaDoDono.readText().trim().toLong() }.getOrNull() ?: return false
        if (pid == ProcessHandle.current().pid()) return false
        if (ProcessHandle.of(pid).isEmpty) return false
        Arranque.recuouDeUmTravado(pid)
        return runCatching {
            ProcessBuilder("taskkill", "/T", "/F", "/PID", pid.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        }.getOrDefault(false)
    }

}

const val ARG_POS_ATUALIZACAO = "--depois-da-atualizacao"

const val ARG_MINIMIZADO = "--minimizado"

private const val PRAZO_DA_IDENTIDADE_MS = 2_000L

private const val UMA_HORA_MS = 60 * 60 * 1000L

private const val HORA_DA_MANHA = 8

private val ESTADOS_NA_BANDEJA =
    listOf(UserStatus.ONLINE, UserStatus.IDLE, UserStatus.DND, UserStatus.INVISIBLE)

private fun iconeDaJanela(): androidx.compose.ui.graphics.painter.Painter? = runCatching {
    val fluxo = Instalacao::class.java.getResourceAsStream("/astra-icon.png") ?: return null
    fluxo.use { androidx.compose.ui.graphics.painter.BitmapPainter(loadImageBitmap(it)) }
}.getOrNull()

private fun proximaManha(): Long {
    val agora = LocalDateTime.now()
    val manha = agora.toLocalDate().atTime(HORA_DA_MANHA, 0)
    val alvo = if (agora.isBefore(manha)) manha else manha.plusDays(1)
    return alvo.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

fun main(args: Array<String>) {
    if (Trocador.executarSePedido(args)) return
    val voltandoDeAtualizacao = args.any { it == ARG_POS_ATUALIZACAO || it.startsWith("$ARG_POS_ATUALIZACAO=") }
    val nascerEscondido = args.any { it == ARG_MINIMIZADO }
    if (!SingleInstance.acquireOrSignal()) {
        Arranque.recuou()
        return
    }
    CrashLog.install()
    val trocaFalhou = !Multi.ligado && TentativasDeInstalar.aoAbrir(System.getProperty("astra.version") ?: "dev")
    Saida.capturar()
    Arranque.comecar(System.getProperty("astra.version") ?: "dev")
    if (Arranque.modoSeguro) {
        System.setProperty("skiko.renderApi", "SOFTWARE")
    }
    Vigia.vigiar(nascerEscondido)
    Arranque.marcar("vigia armado")
    val identidade = thread(isDaemon = true, name = "astra-identidade-windows") {
        WindowsAppId.aplicar()
        Arranque.marcar("identidade no Windows aplicada")
    }
    Arranque.marcar("instancia unica garantida")
    startKoin { modules(appModule) }
    Arranque.marcar("Koin de pe")
    thread(isDaemon = true, name = "astra-diagnostico") {
        writeDiagnostics()
        Arranque.marcar("diagnostico escrito")
    }
    thread(isDaemon = true, name = "astra-despedida") {
        GlobalContext.get().get<DesktopSocket>().registrarDespedida()
        Arranque.marcar("despedida registrada")
    }
    identidade.join(PRAZO_DA_IDENTIDADE_MS)
    Arranque.marcar("entrando na composicao")
    application {
        marcoDoArranque("composicao iniciada")
        var windowVisible by remember { mutableStateOf(!nascerEscondido) }
        var resgate by remember { mutableStateOf(0) }
        val state = rememberWindowState(width = 1280.dp, height = 820.dp)
        val transparentWindow = remember {
            val p = GlobalContext.get().get<DesktopPrefs>().state.value
            p.windowTransparent && !p.performanceMode && janelaAceitaTransparencia &&
                !Arranque.modoSeguro
        }
        val topPrefState by remember { GlobalContext.get().get<DesktopPrefs>() }.state.collectAsState()
        val exitOnClose = topPrefState.exitOnClose
        val sairDeVez = {
            SingleInstance.release()
            exitApplication()
        }
        val onCloseApp = { if (exitOnClose) sairDeVez() else { windowVisible = false } }
        val appIcon = remember { iconeDaJanela() }
        val bandeja = remember { Bandeja() }

        val trazerParaAFrente = { recentrar: Boolean ->
            windowVisible = true
            state.isMinimized = false
            if (recentrar) state.position = WindowPosition(Alignment.Center)
            resgate++
        }

        val activate by SingleInstance.activate.collectAsState()
        LaunchedEffect(activate) {
            if (activate > 0) {
                Arranque.marcar(Arranque.MARCO_CHAMADO)
                trazerParaAFrente(false)
                SingleInstance.chamadoAtendido()
            }
        }

        val updater = remember { GlobalContext.get().get<UpdateService>() }
        val bootPrefs = remember { GlobalContext.get().get<DesktopPrefs>().state.value }
        marcoDoArranque("preferencias e servicos lidos")
        LaunchedEffect(Unit) {
            Obsidian.aplicarContraste(bootPrefs.altoContraste)
            Obsidian.apply(bootPrefs.accentId, bootPrefs.bgId)
        }
        val podeMostrarPortao = updater.installed && !nascerEscondido && !trocaFalhou
        var portaoFechado by remember { mutableStateOf(false) }
        val portaoNaTela = podeMostrarPortao && !portaoFechado
        LaunchedEffect(Unit) { if (podeMostrarPortao || trocaFalhou) updater.check(mostrarFalha = false) }
        val escopoDaTela = rememberCoroutineScope()
        val escopoDaJanela = remember(escopoDaTela) { EscopoSupervisionado.sob(escopoDaTela) }
        LaunchedEffect(Unit) { updater.iniciarRonda(escopoDaJanela) }
        LaunchedEffect(Unit) { updater.agendarFaxina(escopoDaJanela) }
        LaunchedEffect(Unit) { Servidor.vigiar(escopoDaJanela) }
        LaunchedEffect(Unit) { ModoTransmissao.vigiar(escopoDaJanela, GlobalContext.get().get()) }
        LaunchedEffect(Unit) {
            AtividadePublicador(
                GlobalContext.get().get(),
                GlobalContext.get().get(),
            ).iniciar(escopoDaJanela)
        }

        BandejaComMenu(
            bandeja = bandeja,
            dica = "Astra",
            aoAtivar = { windowVisible = true; state.isMinimized = false },
            itens = {
                buildList {
                    add(ItemDaBandeja("Abrir o Astra") { trazerParaAFrente(true) })
                    EstadoNaBandeja.atual?.let { atual ->
                        if (isNotEmpty()) add(SeparadorDaBandeja)
                        ESTADOS_NA_BANDEJA.forEach { escolha ->
                            add(
                                ItemDaBandeja(
                                    rotulo = statusLabel(escolha),
                                    marcado = escolha == atual,
                                    estado = escolha,
                                ) { EstadoNaBandeja.escolher(escolha) },
                            )
                        }
                        add(SeparadorDaBandeja)
                        val prefs = GlobalContext.get().get<DesktopPrefs>()
                        if (topPrefState.silencioAte > System.currentTimeMillis()) {
                            add(ItemDaBandeja("Religar os avisos") { prefs.setSilencioAte(0L) })
                        } else {
                            add(
                                ItemDaBandeja("Silenciar por uma hora") {
                                    prefs.setSilencioAte(System.currentTimeMillis() + UMA_HORA_MS)
                                },
                            )
                            add(
                                ItemDaBandeja("Silenciar até de manhã") {
                                    prefs.setSilencioAte(proximaManha())
                                },
                            )
                        }
                    }
                    VozNaBandeja.sessao?.let { voz ->
                        add(SeparadorDaBandeja)
                        add(
                            ItemDaBandeja(
                                if (voz.mudo) "Reativar microfone" else "Silenciar microfone",
                            ) { voz.alternarMudo() },
                        )
                        add(
                            ItemDaBandeja(
                                if (voz.ensurdecido) "Voltar a ouvir" else "Ensurdecer",
                            ) { voz.alternarEnsurdecer() },
                        )
                    }
                    if (isNotEmpty()) add(SeparadorDaBandeja)
                    add(ItemDaBandeja("Sair", perigo = true) { sairDeVez() })
                }
            },
        )

        AvisosDeMensagem()

        if (portaoNaTela) {
            val gateState = rememberWindowState(
                width = 380.dp,
                height = 470.dp,
                position = WindowPosition(Alignment.Center),
            )
            Window(
                onCloseRequest = { portaoFechado = true },
                title = "Astra",
                icon = appIcon,
                state = gateState,
                undecorated = true,
                transparent = janelaAceitaTransparencia,
                resizable = false,
                alwaysOnTop = true,
            ) {
                marcoDoArranque("portao de atualizacao na tela")
                LaunchedEffect(Unit) {
                    try {
                        withFrameNanos { }
                        Vigia.portaoApareceu()
                        awaitCancellation()
                    } finally {
                        Vigia.portaoSaiu()
                    }
                }
                UpdaterGate(
                    updater,
                    bootPrefs.reduceMotionEff,
                    onDone = { portaoFechado = true; resgate++ },
                )
            }
            return@application
        }

        Window(
            onCloseRequest = onCloseApp,
            title = "Astra",
            icon = appIcon,
            state = state,
            visible = windowVisible,
            undecorated = true,
            transparent = transparentWindow,
        ) {
            marcoDoArranque(Arranque.MARCO_JANELA)
            remember { Vigia.janelaCriada() }
            LaunchedEffect(Unit) {
                if (nascerEscondido) Arranque.nasceuEscondido()
                Arranque.marcar("composicao da janela pronta")
                withFrameNanos { }
                Arranque.desenhou()
                Vigia.apareceu(window)
                SingleInstance.aJanelaRespondeu()
            }
            LaunchedEffect(resgate) {
                if (resgate > 0) runCatching { window.toFront(); window.requestFocus() }
            }
            if (voltandoDeAtualizacao || trocaFalhou) {
                LaunchedEffect(Unit) {
                    delay(400)
                    resgate++
                }
            }
            setSingletonImageLoaderFactory { ctx ->
                ImageLoader.Builder(ctx)
                    .components {
                        add(DataUriMapper())
                        add(RelativeUrlMapper(AstraShared.BASE_URL))
                        add(DecodificadorNitido.Fabrica())
                    }
                    .memoryCache {
                        coil3.memory.MemoryCache.Builder()
                            .maxSizeBytes(tetoDoCacheDeImagens(bootPrefs.performanceMode))
                            .build()
                    }
                    .diskCache {
                        val home = System.getProperty("user.home")
                        val os = System.getProperty("os.name").orEmpty()
                        val base = when {
                            os.startsWith("Windows", true) -> System.getenv("LOCALAPPDATA") ?: "$home\\AppData\\Local"
                            os.contains("Mac", true) -> "$home/Library/Caches"
                            else -> System.getenv("XDG_CACHE_HOME") ?: "$home/.cache"
                        }
                        val teto = if (bootPrefs.performanceMode) 120L else 300L
                        DiskCache.Builder()
                            .directory(java.io.File(base, "Astra/image-cache").absolutePath.toPath())
                            .maxSizeBytes(teto * 1024 * 1024)
                            .build()
                    }
                    .build()
            }

            LaunchedEffect(Unit) { DesktopShortcut.ensureWindows() }
            LaunchedEffect(Unit) { InicioComWindows.realinhar() }
            LaunchedEffect(Unit) { WindowsAppId.registrarIdentidade() }

            val koin = GlobalContext.get()
            val windowInfo = LocalWindowInfo.current
            val janelaComFoco = lembrarFocoDoApp()
            val store = remember { koin.get<SessionStore>() }
            val authRepo = remember { koin.get<AuthRepository>() }
            var session by remember { mutableStateOf(store.load()) }
            var needsOnboarding by remember { mutableStateOf(false) }
            var searchOpen by remember { mutableStateOf(false) }
            var notifOpen by remember { mutableStateOf(false) }
            var desejosOpen by remember { mutableStateOf(false) }
            var missoesOpen by remember { mutableStateOf(false) }
            var notifUnread by remember { mutableStateOf(0) }
            val painelDeMissoes by remember { GlobalContext.get().get<MissoesStore>() }.painel.collectAsState()
            val missoesProntas = painelDeMissoes?.quantasProntas() ?: 0

            EmblemaDaBarra(window, notifUnread)

            val prefs = remember { koin.get<DesktopPrefs>() }
            val prefState by prefs.state.collectAsState()
            LaunchedEffect(prefState.accentId, prefState.bgId, prefState.altoContraste) {
                Obsidian.aplicarContraste(prefState.altoContraste)
                Obsidian.apply(prefState.accentId, prefState.bgId)
            }

            val rounded = transparentWindow && state.placement == WindowPlacement.Floating
            val windowShape = if (rounded) RoundedCornerShape(10.dp) else RectangleShape

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
                        missoesProntas = missoesProntas,
                        onOpenDesejos = { desejosOpen = !desejosOpen },
                        atualizacao = updater,
                    )
                    ServidorAcordandoStrip()
                    val naTela = windowVisible && !state.isMinimized
                    LaunchedEffect(naTela) { JanelaVisivel.marcar(naTela) }
                    CompositionLocalProvider(
                        LocalWindowActive provides (naTela && janelaComFoco),
                        LocalJanelaNaTela provides naTela,
                    ) {
                    Box(Modifier.fillMaxSize().contandoQuadros()) {
                    var aquecido by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { Quadros.medirParaArquivo(this) }
                    val auroraPulse = remember { Animatable(0f) }
                    val pulseScope = rememberCoroutineScope()
                    val transmitindo by Transmitindo.ativo.collectAsState()
                    val alguemFala by QuemFala.alguem.collectAsState()
                    LaunchedEffect(alguemFala, prefState.reduceMotionEff) {
                        if (prefState.reduceMotionEff) return@LaunchedEffect
                        if (alguemFala) auroraPulse.animateTo(1f, tween(240, easing = EaseOutStd))
                        else auroraPulse.animateTo(0f, tween(900, easing = EaseOutSoft))
                    }
                    CompositionLocalProvider(
                        LocalRenderPrefs provides
                            RenderPrefs(prefState.auroraQuality.octaves, prefState.uiFps.cap),
                        LocalReduceMotion provides
                            (prefState.reduceMotionEff || !janelaComFoco || transmitindo),
                    ) {
                        if (prefState.auroraOn) {
                            Box(Modifier.fillMaxSize().graphicsLayer {}.auroraBackground { auroraPulse.value })
                        } else {
                            Box(Modifier.fillMaxSize().background(Obsidian.void))
                        }
                        if (prefState.starsOn) StarField(Modifier.fillMaxSize())
                    }

                    val reveal = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(150, easing = EaseOutStd)) }
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha = reveal.value
                            translationY = (1f - reveal.value) * 12.dp.toPx()
                            val sc = 0.99f + 0.01f * reveal.value
                            scaleX = sc
                            scaleY = sc
                        },
                    ) {
                        Crossfade(
                            targetState = session,
                            animationSpec = tween(120, easing = EaseOutStd),
                            label = "entrada",
                        ) { s ->
                            if (s == null) {
                                LoginScreen(repo = authRepo, onLoggedIn = { sess, isNew ->
                                    session = sess
                                    if (isNew) needsOnboarding = true
                                    pulseScope.launch {
                                        auroraPulse.snapTo(1f)
                                        auroraPulse.animateTo(0f, tween(900, easing = EaseOutSoft))
                                    }
                                })
                            } else {
                                Crossfade(
                                    targetState = needsOnboarding,
                                    animationSpec = tween(120, easing = EaseOutStd),
                                    label = "onboarding",
                                ) { onb ->
                                    if (onb) {
                                        OnboardingScreen(
                                            displayName = s.displayName,
                                            onTestarAviso = {
                                                bandeja.avisar("Astra", "Pronto — os avisos do Astra estão liberados.")
                                            },
                                            onDone = {
                                                store.setUiPref("onboarded:${s.userId}", "1")
                                                store.setUiPref("checklist:${s.userId}", "1")
                                                store.setUiPref("permsVistas", "1")
                                                needsOnboarding = false
                                            },
                                        )
                                    } else {
                                        ShellScreen(
                                            session = s,
                                            windowInactive = {
                                                !windowVisible || state.isMinimized || !windowInfo.isWindowFocused
                                            },
                                            notify = { title, body ->
                                                bandeja.avisar(title, body)
                                                if (ModoTransmissao.ativo.value) return@ShellScreen
                                                tocarAvisoDeMensagem()
                                            },
                                            aoPedirJanela = {
                                                windowVisible = true
                                                state.isMinimized = false
                                            },
                                            onLogout = {
                                                authRepo.logout(escopoDaJanela)
                                                session = null
                                                notifUnread = 0
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
                    }
                    if (!aquecido) {
                        TelaDeCarregamento(prefState.reduceMotionEff) { aquecido = true }
                    }
                    }
                    }
                }
            }
            }
        }
    }
}

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
