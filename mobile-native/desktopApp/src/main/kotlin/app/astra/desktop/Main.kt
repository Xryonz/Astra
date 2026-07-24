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
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import app.astra.desktop.auth.AuthRepository
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.di.appModule
import app.astra.desktop.net.DataUriMapper
import app.astra.desktop.net.RelativeUrlMapper
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.update.UpdateService
import app.astra.desktop.ui.AstraTitleBar
import app.astra.desktop.ui.LocalWindowActive
import app.astra.desktop.ui.LoginScreen
import app.astra.desktop.ui.ShellScreen
import app.astra.desktop.ui.StarField
import app.astra.desktop.ui.auroraBackground
import app.astra.desktop.ui.UpdateBanner
import app.astra.desktop.ui.UpdaterGate
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.shared.AstraShared
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
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

// Instancia unica: lock por ServerSocket no loopback. Se ja tem Astra rodando (a
// porta esta ocupada), sinaliza o processo existente pra aparecer e ESTE sai — sem
// dois apps na bandeja. O primeiro escuta e traz a janela pra frente ao ser tocado.
object SingleInstance {
    private const val PORT = 47821
    val activate = MutableStateFlow(0)
    private var server: ServerSocket? = null

    // true = somos a instancia primaria; false = ja tem uma (sinalizamos, hora de sair).
    fun acquireOrSignal(): Boolean = try {
        server = ServerSocket(PORT, 1, InetAddress.getLoopbackAddress()).also { s ->
            thread(isDaemon = true, name = "astra-single-instance") {
                while (!s.isClosed) runCatching { s.accept().close(); activate.value++ }
            }
        }
        true
    } catch (e: IOException) {
        runCatching { Socket(InetAddress.getLoopbackAddress(), PORT).use { } }
        false
    }
}

fun main() {
    // Segundo processo: pede pro Astra aberto aparecer e encerra aqui mesmo.
    if (!SingleInstance.acquireOrSignal()) return
    startKoin { modules(appModule) }
    application {
        // Fechar a janela NAO mata o app: minimiza pra bandeja (decisao do dono).
        var windowVisible by remember { mutableStateOf(true) }
        val state = rememberWindowState(width = 1280.dp, height = 820.dp)
        // Transparencia e param de CRIACAO da janela -> le a pref UMA vez no boot
        // (Settings > Desempenho avisa "aplica ao reiniciar"). Opaca = mais leve.
        val transparentWindow = remember { GlobalContext.get().get<DesktopPrefs>().state.value.windowTransparent }
        // Logo real do Astra (planeta) — mesma do PWA/favicon do site.
        val appIcon = painterResource("astra-icon.png")
        val trayState = rememberTrayState()

        // Outro processo tentou abrir o Astra -> traz esta janela (a unica) pra frente.
        val activate by SingleInstance.activate.collectAsState()
        LaunchedEffect(activate) {
            if (activate > 0) {
                windowVisible = true
                state.isMinimized = false
            }
        }

        // Auto-update: gate de boot (janelinha estilo Discord) so no app instalado;
        // dev/IDE pula direto pro app. Tema aplicado ja aqui -> o gate (logo +
        // estrelas orbitando) sai no accent que o usuario escolheu.
        val updater = remember { GlobalContext.get().get<UpdateService>() }
        val bootPrefs = remember { GlobalContext.get().get<DesktopPrefs>().state.value }
        LaunchedEffect(Unit) { Obsidian.apply(bootPrefs.accentId, bootPrefs.bgId) }
        var gateDone by remember { mutableStateOf(!updater.installed) }

        Tray(
            state = trayState,
            icon = appIcon,
            tooltip = "Astra",
            onAction = { windowVisible = true }, // duplo clique no icone reabre
            menu = {
                Item("Abrir o Astra", onClick = { windowVisible = true })
                Separator()
                Item("Sair", onClick = ::exitApplication)
            },
        )

        // Gate de update primeiro: verifica a versao (logo + estrelas girando) e,
        // se houver nova, baixa com barra de progresso; senao segue pro app. So
        // enquanto nao terminou (gateDone) — depois some e o app abre normal.
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
            onCloseRequest = { windowVisible = false },
            title = "Astra",
            icon = appIcon,
            state = state,
            visible = windowVisible,
            undecorated = true, // frameless: a barra-titulo obsidiana e nossa
            // Fundo da janela transparente so pra dar cantos arredondados ao
            // conteudo (polish). Toggle em Settings > Desempenho (aplica ao reiniciar).
            transparent = transparentWindow,
        ) {
            // Coil global: data-URIs (avatares no banco) + URLs relativas /uploads.
            setSingletonImageLoaderFactory { ctx ->
                ImageLoader.Builder(ctx)
                    .components {
                        add(DataUriMapper())
                        add(RelativeUrlMapper(AstraShared.BASE_URL))
                    }
                    .build()
            }

            // 1o run: garante um atalho do Astra na area de trabalho (Windows).
            LaunchedEffect(Unit) { DesktopShortcut.ensureWindows() }

            val koin = GlobalContext.get()
            val store = remember { koin.get<SessionStore>() }
            val authRepo = remember { koin.get<AuthRepository>() }
            var session by remember { mutableStateOf(store.load()) }
            // Overlays disparados pelo titlebar (lupa/sino) mas renderizados no
            // shell (onde vive o vm de navegacao). Estado hasteado aqui no meio.
            var searchOpen by remember { mutableStateOf(false) }
            var notifOpen by remember { mutableStateOf(false) }
            var notifUnread by remember { mutableStateOf(0) }

            // Tema do usuario (Settings > Aparencia): aplica o par accent/fundo nos
            // tokens reativos do Obsidian -> o app inteiro recolore ao vivo.
            val prefs = remember { koin.get<DesktopPrefs>() }
            val prefState by prefs.state.collectAsState()
            LaunchedEffect(prefState.accentId, prefState.bgId) {
                Obsidian.apply(prefState.accentId, prefState.bgId)
            }

            // Cantos arredondados so com a janela solta E translucida; maximizada
            // ou opaca volta ao reto (senao sobra fresta/canto preto).
            val rounded = transparentWindow && state.placement == WindowPlacement.Floating
            val windowShape = if (rounded) RoundedCornerShape(10.dp) else RectangleShape

            // RikkaUI e CMP (foundation-only): mesmo tema do mobile, tokens obsidiana.
            // Reconstruido AQUI (nao top-level) pra ler os tokens reativos do Obsidian
            // -> os componentes RikkaUI recolorem junto quando o tema muda.
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
                        onClose = { windowVisible = false },
                        showActions = session != null,
                        notifUnread = notifUnread,
                        onOpenSearch = { searchOpen = true },
                        onOpenNotifications = { notifOpen = !notifOpen },
                    )
                    // Ativa = visivel & nao minimizada: aurora/estrelas so pedem
                    // frame quando ativa (poupam na bandeja) SEM congelar quando um
                    // popup rouba o foco (isso e visibilidade, nao foco).
                    CompositionLocalProvider(
                        LocalWindowActive provides (windowVisible && !state.isMinimized),
                    ) {
                    // O CEU DA JANELA: aurora + estrelas atras do login E do shell.
                    // Morava dentro do ShellScreen, e o login pintava a propria aurora
                    // num painel de 45% — como o uv do shader e normalizado pelo
                    // tamanho, eram imagens diferentes e a entrada saltava. Aqui em
                    // cima ela nao se mexe quando o conteudo troca: entra-se NO app,
                    // nao se troca de tela. E fica um shader so, nunca dois.
                    Box(Modifier.fillMaxSize()) {
                    // Pulso de login: o ceu "respira" uma vez quando voce entra. Lido
                    // no draw da aurora (nao recompoe); disparado no onLoggedIn abaixo.
                    val auroraPulse = remember { Animatable(0f) }
                    val pulseScope = rememberCoroutineScope()
                    if (prefState.auroraOn) {
                        // Camada propria (graphicsLayer): so ela invalida por frame —
                        // os paineis translucidos por cima nao redesenham com o shader.
                        Box(Modifier.fillMaxSize().graphicsLayer {}.auroraBackground { auroraPulse.value })
                    } else {
                        Box(Modifier.fillMaxSize().background(Obsidian.void))
                    }
                    if (prefState.starsOn) StarField(Modifier.fillMaxSize())

                    // Entrada do Astra: um reveal unico (uma vez por abertura) quando o
                    // conteudo aparece depois do gate — o app sobe com fade + escala
                    // sutil POR CIMA do ceu, que ja esta aceso. GPU-only, ~520ms.
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
                        // shell aparecerem SOBRE o mesmo ceu, que nao se mexe. Por
                        // isso Crossfade e nao slide: o ceu ancora as duas telas, e
                        // qualquer deslocamento denunciaria que sao telas diferentes.
                        Crossfade(
                            targetState = session,
                            animationSpec = tween(420, easing = EaseOutStd),
                            label = "entrada",
                        ) { s ->
                            if (s == null) {
                                LoginScreen(repo = authRepo, onLoggedIn = {
                                    session = it
                                    // O ceu respira: sobe na hora e decai em 900ms.
                                    pulseScope.launch {
                                        auroraPulse.snapTo(1f)
                                        auroraPulse.animateTo(0f, tween(900, easing = EaseOutSoft))
                                    }
                                })
                            } else {
                                ShellScreen(
                                    session = s,
                                    // Toast da bandeja so quando o app nao esta na frente.
                                    windowHidden = { !windowVisible || state.isMinimized },
                                    notify = { title, body ->
                                        trayState.sendNotification(Notification(title, body, Notification.Type.None))
                                    },
                                    onLogout = {
                                        authRepo.logout()
                                        session = null
                                    },
                                    searchOpen = searchOpen,
                                    onCloseSearch = { searchOpen = false },
                                    notifOpen = notifOpen,
                                    onCloseNotif = { notifOpen = false },
                                    onNotifUnread = { notifUnread = it },
                                )
                            }
                        }
                        // Banner de update (topo): lembrete quando adiado ("depois")
                        // ou achado na checagem manual — conduz o mesmo mini-fluxo.
                        UpdateBanner(updater)
                    }
                    }
                    }
                }
            }
        }
    }
}

// Mapeamento RikkaColors identico ao AstraTheme do mobile (Theme.kt), com os
// tokens obsidiana do desktop. Funcao (nao val) pra ler os tokens reativos DENTRO
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
