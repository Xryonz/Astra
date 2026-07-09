package app.astra.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.astra.desktop.auth.AuthRepository
import app.astra.desktop.auth.Session
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.di.appModule
import app.astra.desktop.ui.AstraTitleBar
import app.astra.desktop.ui.LoginScreen
import app.astra.desktop.ui.theme.Obsidian
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext

fun main() {
    startKoin { modules(appModule) }
    application {
        val state = rememberWindowState(width = 1200.dp, height = 800.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title = "Astra",
            state = state,
            undecorated = true, // frameless: a barra-titulo obsidiana e nossa
        ) {
            val koin = GlobalContext.get()
            val store = remember { koin.get<SessionStore>() }
            val authRepo = remember { koin.get<AuthRepository>() }
            var session by remember { mutableStateOf(store.load()) }

            Column(Modifier.fillMaxSize().background(Obsidian.void)) {
                AstraTitleBar(state = state, onClose = ::exitApplication)
                Box(Modifier.fillMaxSize()) {
                    val s = session
                    if (s == null) {
                        LoginScreen(repo = authRepo, onLoggedIn = { session = it })
                    } else {
                        HomePlaceholder(s)
                    }
                }
            }
        }
    }
}

// Placeholder pos-login: proxima fatia = shell (rail + orbitas + palco).
@Composable
private fun HomePlaceholder(session: Session) {
    Box(Modifier.fillMaxSize().background(Obsidian.base), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicText(
                text = "ola, ${session.displayName}",
                style = TextStyle(
                    color = Obsidian.text1,
                    fontSize = 30.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Light,
                ),
            )
            BasicText(
                text = "logado — o shell (constelacoes e orbitas) e a proxima fatia",
                style = TextStyle(color = Obsidian.text3, fontSize = 13.sp),
            )
        }
    }
}
