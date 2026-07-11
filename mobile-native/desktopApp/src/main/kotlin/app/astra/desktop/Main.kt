package app.astra.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import app.astra.desktop.auth.AuthRepository
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.di.appModule
import app.astra.desktop.net.DataUriMapper
import app.astra.desktop.net.RelativeUrlMapper
import app.astra.desktop.ui.AstraTitleBar
import app.astra.desktop.ui.LoginScreen
import app.astra.desktop.ui.ShellScreen
import app.astra.desktop.ui.theme.Obsidian
import app.astra.shared.AstraShared
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import zed.rainxch.rikkaui.foundation.RikkaColors
import zed.rainxch.rikkaui.foundation.RikkaTheme

fun main() {
    startKoin { modules(appModule) }
    application {
        // Fechar a janela NAO mata o app: minimiza pra bandeja (decisao do dono).
        var windowVisible by remember { mutableStateOf(true) }
        val state = rememberWindowState(width = 1280.dp, height = 820.dp)
        // Logo real do Astra (planeta) — mesma do PWA/favicon do site.
        val appIcon = painterResource("astra-icon.png")
        val trayState = rememberTrayState()

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

        Window(
            onCloseRequest = { windowVisible = false },
            title = "Astra",
            icon = appIcon,
            state = state,
            visible = windowVisible,
            undecorated = true, // frameless: a barra-titulo obsidiana e nossa
            // Fundo da janela transparente so pra dar cantos arredondados ao
            // conteudo (polish). Se custar fluidez no device, reverter os dois.
            transparent = true,
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

            val koin = GlobalContext.get()
            val store = remember { koin.get<SessionStore>() }
            val authRepo = remember { koin.get<AuthRepository>() }
            var session by remember { mutableStateOf(store.load()) }

            // Cantos arredondados so com a janela solta; maximizada volta ao
            // reto (senao sobra fresta transparente nos cantos da tela).
            val rounded = state.placement == WindowPlacement.Floating
            val windowShape = if (rounded) RoundedCornerShape(10.dp) else RectangleShape

            // RikkaUI e CMP (foundation-only): mesmo tema do mobile, tokens obsidiana.
            RikkaTheme(colors = rikkaObsidian) {
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
                    AstraTitleBar(state = state, onClose = { windowVisible = false })
                    Box(Modifier.fillMaxSize()) {
                        val s = session
                        if (s == null) {
                            LoginScreen(repo = authRepo, onLoggedIn = { session = it })
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
                            )
                        }
                    }
                }
            }
        }
    }
}

// Mapeamento RikkaColors identico ao AstraTheme do mobile (Theme.kt), com os
// tokens obsidiana do desktop.
private val rikkaObsidian = RikkaColors(
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
