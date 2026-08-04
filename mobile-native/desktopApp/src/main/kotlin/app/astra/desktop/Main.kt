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
import androidx.compose.ui.platform.LocalWindowInfo
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
import androidx.compose.foundation.LocalContextMenuRepresentation
import app.astra.desktop.ui.AstraTextContextMenu
import app.astra.desktop.ui.AstraTitleBar
import app.astra.desktop.ui.LocalWindowActive
import app.astra.desktop.ui.LoginScreen
import app.astra.desktop.ui.OnboardingScreen
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
    }
    java.io.File(dir, "diagnostico.txt").writeText(txt)
    println(txt)
}

// Instancia única: lock por ServerSocket no loopback. Se já tem Astra rodando (a
// porta esta ocupada), sinaliza o processo existente pra aparecer e ESTE sai — sem
// dois apps na bandeja. O primeiro escuta e traz a janela pra frente ao ser tocado.
object SingleInstance {
    private const val PORT = 47821
    val activate = MutableStateFlow(0)
    private var server: ServerSocket? = null

    // Abre um SEGUNDO Astra na mesma maquina, com sessão propria:
    //   ./gradlew :desktopApp:run -Pastra.multi
    //   (ou o .exe com -Dastra.multi=1)
    //
    // Por que isto existe: a maioria dos bugs que aparecem aqui e do tipo
    // "funciona pra quem fez a ação, não funciona pro outro" — canal novo que não
    // aparecia, presenca congelada, status que não propagava, membro que não
    // surgia. Nenhum deles e azar: e consequencia de so dar pra testar com UMA
    // conta. Com duas janelas lado a lado, cada um desses aparece em segundos, na
    // hora de escrever o codigo, em vez de semanas depois pela boca de um amigo.
    val multi: Boolean = System.getProperty("astra.multi") != null

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

fun main() {
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
    // Retrato do boot (API grafica do Skia, GC, heap) num arquivo legivel.
    writeDiagnostics()
    startKoin { modules(appModule) }
    application {
        // Fechar a janela NAO mata o app: minimiza pra bandeja (decisao do dono).
        var windowVisible by remember { mutableStateOf(true) }
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
        val trayState = rememberTrayState()

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
        LaunchedEffect(Unit) { Obsidian.apply(bootPrefs.accentId, bootPrefs.bgId) }
        var gateDone by remember { mutableStateOf(!updater.installed) }
        // Ronda: o app deixa de depender de reiniciar pra saber que saiu versao
        // nova. Vive no escopo da janela — some junto com ela.
        val escopoDaJanela = rememberCoroutineScope()
        LaunchedEffect(Unit) { updater.iniciarRonda(escopoDaJanela) }
        // Faxina das versoes antigas, 20s depois de abrir: se este pacote estiver
        // quebrado e o app morrer antes disso, a versao anterior sobrevive e o
        // launcher volta pra ela.
        LaunchedEffect(Unit) { updater.agendarFaxina(escopoDaJanela) }

        // Bandeja so quando NAO e "fechar de vez": ligado o exitOnClose, o X ja encerra
        // o app, entao um icone de bandeja seria presenca inutil em segundo plano.
        if (!exitOnClose) {
            Tray(
                state = trayState,
                icon = appIcon,
                tooltip = "Astra",
                onAction = { windowVisible = true }, // duplo clique no ícone reabre
                menu = {
                    Item("Abrir o Astra", onClick = { windowVisible = true })
                    Separator()
                    Item("Sair", onClick = ::exitApplication)
                },
            )
        }

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
            var missoesOpen by remember { mutableStateOf(false) }
            var notifUnread by remember { mutableStateOf(0) }

            // Tema do usuário (Settings > Aparencia): aplica o par accent/fundo nos
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
                    )
                    // Ativa = visivel & não minimizada: aurora/estrelas so pedem
                    // frame quando ativa (poupam na bandeja) SEM congelar quando um
                    // popup rouba o foco (isso e visibilidade, não foco).
                    CompositionLocalProvider(
                        LocalWindowActive provides (windowVisible && !state.isMinimized),
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
                    if (prefState.auroraOn) {
                        // Camada propria (graphicsLayer): so ela invalida por frame —
                        // os paineis translucidos por cima não redesenham com o shader.
                        Box(Modifier.fillMaxSize().graphicsLayer {}.auroraBackground { auroraPulse.value })
                    } else {
                        Box(Modifier.fillMaxSize().background(Obsidian.void))
                    }
                    if (prefState.starsOn) StarField(Modifier.fillMaxSize())

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
                                                trayState.sendNotification(
                                                    Notification("Astra", "Pronto — os avisos do Astra estão liberados.", Notification.Type.None),
                                                )
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
                                            missoesOpen = missoesOpen,
                                            onCloseMissoes = { missoesOpen = false },
                                            onNotifUnread = { notifUnread = it },
                                        )
                                    }
                                }
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
