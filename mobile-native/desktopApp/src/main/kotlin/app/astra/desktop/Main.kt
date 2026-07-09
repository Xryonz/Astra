package app.astra.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
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

fun main() {
    startKoin { modules(appModule) }
    application {
        // Fechar a janela NAO mata o app: minimiza pra bandeja (decisao do dono).
        var windowVisible by remember { mutableStateOf(true) }
        val state = rememberWindowState(width = 1280.dp, height = 820.dp)

        Tray(
            state = rememberTrayState(),
            icon = TrayIcon,
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
            state = state,
            visible = windowVisible,
            undecorated = true, // frameless: a barra-titulo obsidiana e nossa
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

            Column(Modifier.fillMaxSize().background(Obsidian.void)) {
                AstraTitleBar(state = state, onClose = { windowVisible = false })
                Box(Modifier.fillMaxSize()) {
                    val s = session
                    if (s == null) {
                        LoginScreen(repo = authRepo, onLoggedIn = { session = it })
                    } else {
                        ShellScreen(s)
                    }
                }
            }
        }
    }
}

// Icone da bandeja desenhado na mao (sem asset): quadrado void + estrela accent.
private object TrayIcon : Painter() {
    override val intrinsicSize = Size(64f, 64f)
    override fun DrawScope.onDraw() {
        drawRoundRect(color = Color(0xFF06060E), cornerRadius = CornerRadius(16f, 16f))
        drawCircle(color = Color(0xFFD4D8E0), radius = size.minDimension * 0.26f)
    }
}
