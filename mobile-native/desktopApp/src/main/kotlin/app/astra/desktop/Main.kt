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
import androidx.compose.runtime.withFrameNanos
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
import app.astra.desktop.update.UpdateState
import app.astra.desktop.voice.Transmitindo
import app.astra.desktop.xp.MissoesStore
import app.astra.desktop.xp.quantasProntas
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import androidx.compose.foundation.LocalContextMenuRepresentation
import app.astra.desktop.ui.Aquecimento
import app.astra.desktop.ui.AstraTextContextMenu
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

private fun gcName(): String = runCatching {
    java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
        .joinToString("+") { it.name }
}.getOrDefault("?")

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

    fun cederAFrenteA(pid: Long) {
        runCatching { U32.I?.AllowSetForegroundWindow(pid.toInt()) }
    }

    private val meuPid = runCatching { ProcessHandle.current().pid().toInt() }.getOrDefault(-1)

    fun appNaFrente(): Boolean {
        val u = U32.I ?: return true
        val janela = u.GetForegroundWindow() ?: return false
        val dono = IntByReference()
        u.GetWindowThreadProcessId(janela, dono)
        return dono.value == meuPid
    }
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
        if (Arranque.arranqueAnteriorFalhou) {
            appendLine("MODO SEGURO  : ligado — o arranque anterior criou a janela e nao desenhou")
            appendLine("   ^ janela opaca + desenho por CPU. arranque-anterior.txt tem a trilha que falhou")
        }
        appendLine("transparencia: ${if (janelaAceitaTransparencia) "aceita" else "NAO aceita — janela opaca"}")
        appendLine("   ^ NAO aceita e janela transparente = janela invisivel, so o icone na barra")
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

object SingleInstance {
    private const val PORT = 47821
    val activate = MutableStateFlow(0)
    private var server: ServerSocket? = null

    val multi: Boolean get() = Multi.ligado

    fun release() {
        runCatching { server?.close() }
        server = null
    }

    fun acquireOrSignal(): Boolean = if (multi) true else try {
        server = ServerSocket().also { s ->
            s.reuseAddress = true
            s.bind(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 1)
            thread(isDaemon = true, name = "astra-single-instance") {
                while (!s.isClosed) runCatching { s.accept().close(); activate.value++ }
            }
        }
        true
    } catch (e: IOException) {
        val existe = runCatching {
            Socket().use { it.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 800) }
        }.isSuccess
        !existe
    }
}

const val ARG_POS_ATUALIZACAO = "--depois-da-atualizacao"

const val ARG_MINIMIZADO = "--minimizado"

private const val PRAZO_DO_PORTAO_MS = 8_000L

fun main(args: Array<String>) {
    val voltandoDeAtualizacao = args.any { it == ARG_POS_ATUALIZACAO }
    val nascerEscondido = args.any { it == ARG_MINIMIZADO }
    CrashLog.install()
    Saida.capturar()
    Arranque.comecar(System.getProperty("astra.version") ?: "dev")
    if (Arranque.arranqueAnteriorFalhou) {
        System.setProperty("skiko.renderApi", "SOFTWARE")
    }
    Vigia.vigiar(nascerEscondido)
    WindowsAppId.aplicar()
    if (!SingleInstance.acquireOrSignal()) {
        Arranque.marcar("ja havia outro Astra aberto — este saiu")
        return
    }
    Arranque.marcar("instancia unica garantida")
    startKoin { modules(appModule) }
    Arranque.marcar("Koin de pe")
    writeDiagnostics()
    Arranque.marcar("diagnostico escrito")
    GlobalContext.get().get<DesktopSocket>().registrarDespedida()
    Arranque.marcar("entrando na composicao")
    application {
        marcoDoArranque("composicao iniciada")
        var windowVisible by remember { mutableStateOf(!nascerEscondido) }
        val state = rememberWindowState(width = 1280.dp, height = 820.dp)
        val transparentWindow = remember {
            val p = GlobalContext.get().get<DesktopPrefs>().state.value
            p.windowTransparent && !p.performanceMode && janelaAceitaTransparencia &&
                !Arranque.arranqueAnteriorFalhou
        }
        val topPrefState by remember { GlobalContext.get().get<DesktopPrefs>() }.state.collectAsState()
        val exitOnClose = topPrefState.exitOnClose
        val onCloseApp = { if (exitOnClose) exitApplication() else { windowVisible = false } }
        val appIcon = painterResource("astra-icon.png")
        val bandeja = remember { Bandeja() }

        val activate by SingleInstance.activate.collectAsState()
        LaunchedEffect(activate) {
            if (activate > 0) {
                windowVisible = true
                state.isMinimized = false
            }
        }

        val updater = remember { GlobalContext.get().get<UpdateService>() }
        val bootPrefs = remember { GlobalContext.get().get<DesktopPrefs>().state.value }
        marcoDoArranque("preferencias e servicos lidos")
        LaunchedEffect(Unit) {
            Obsidian.aplicarContraste(bootPrefs.altoContraste)
            Obsidian.apply(bootPrefs.accentId, bootPrefs.bgId)
        }
        val podeMostrarPortao = updater.installed && !nascerEscondido
        val estadoDaAtualizacao by updater.state.collectAsState()
        var portaoConvocado by remember { mutableStateOf(false) }
        var portaoFechado by remember { mutableStateOf(false) }
        var prazoDoPortaoVenceu by remember { mutableStateOf(!podeMostrarPortao) }
        val portaoNaTela = portaoConvocado && !portaoFechado
        LaunchedEffect(Unit) { if (podeMostrarPortao) updater.check(mostrarFalha = false) }
        LaunchedEffect(Unit) {
            if (podeMostrarPortao) { delay(PRAZO_DO_PORTAO_MS); prazoDoPortaoVenceu = true }
        }
        LaunchedEffect(estadoDaAtualizacao, prazoDoPortaoVenceu) {
            val noticia = estadoDaAtualizacao.let {
                it is UpdateState.Available || it is UpdateState.Downloading || it is UpdateState.Ready
            }
            if (noticia && !prazoDoPortaoVenceu && !portaoFechado) portaoConvocado = true
        }
        val escopoDaJanela = rememberCoroutineScope()
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
            aoAtivar = { windowVisible = true },
            itens = {
                buildList {
                    if (!exitOnClose) add(ItemDaBandeja("Abrir o Astra") { windowVisible = true })
                    VozNaBandeja.sessao?.let { voz ->
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
                    add(ItemDaBandeja("Sair", perigo = true) { exitApplication() })
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
                UpdaterGate(updater, bootPrefs.reduceMotionEff, onDone = { portaoFechado = true })
            }
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
            LaunchedEffect(Unit) {
                if (nascerEscondido) Arranque.nasceuEscondido()
                withFrameNanos { }
                Arranque.desenhou()
                Vigia.apareceu()
            }
            if (voltandoDeAtualizacao) {
                LaunchedEffect(Unit) {
                    delay(400)
                    runCatching { window.toFront(); window.requestFocus() }
                }
            }
            setSingletonImageLoaderFactory { ctx ->
                ImageLoader.Builder(ctx)
                    .components {
                        add(DataUriMapper())
                        add(RelativeUrlMapper(AstraShared.BASE_URL))
                    }
                    .memoryCache {
                        val teto = if (bootPrefs.performanceMode) 16L else 48L
                        coil3.memory.MemoryCache.Builder().maxSizeBytes(teto * 1024 * 1024).build()
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
                    CompositionLocalProvider(
                        LocalWindowActive provides
                            (windowVisible && !state.isMinimized && janelaComFoco),
                        LocalJanelaNaTela provides (windowVisible && !state.isMinimized),
                    ) {
                    Box(Modifier.fillMaxSize()) {
                    Aquecimento()
                    val auroraPulse = remember { Animatable(0f) }
                    val pulseScope = rememberCoroutineScope()
                    val transmitindo by Transmitindo.ativo.collectAsState()
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
                    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(220, easing = EaseOutStd)) }
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
                            animationSpec = tween(180, easing = EaseOutStd),
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
                                    animationSpec = tween(180, easing = EaseOutStd),
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
