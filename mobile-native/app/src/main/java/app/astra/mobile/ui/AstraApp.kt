package app.astra.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.astra.mobile.feature.auth.presentation.LoginScreen
import app.astra.mobile.feature.dm.presentation.DmChatScreen
import app.astra.mobile.feature.dm.presentation.DmListScreen
import app.astra.mobile.feature.home.HomeScreen
import app.astra.mobile.feature.server.presentation.ChannelListScreen
import app.astra.mobile.feature.server.presentation.ServerListScreen
import app.astra.mobile.session.SessionViewModel
import android.net.Uri

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val DMS = "dms"
    const val DM_CHAT = "dm/{conversationId}?name={name}"
    const val SERVERS = "servers"
    const val CHANNELS = "channels/{serverId}?name={name}"
    // name encodado na query (%20 etc.) pra suportar espacos/acentos no display name.
    fun dmChat(id: String, name: String) = "dm/$id?name=${Uri.encode(name)}"
    fun channels(id: String, name: String) = "channels/$id?name=${Uri.encode(name)}"
}

@Composable
fun AstraApp() {
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val loggedIn by sessionViewModel.isLoggedIn.collectAsState()

    when (loggedIn) {
        null -> SplashScreen() // DataStore ainda lendo
        else -> {
            val nav = rememberNavController()
            NavHost(
                navController = nav,
                startDestination = if (loggedIn == true) Routes.HOME else Routes.LOGIN,
            ) {
                composable(Routes.LOGIN) { LoginScreen() }
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenDms = { nav.navigate(Routes.DMS) },
                        onOpenServers = { nav.navigate(Routes.SERVERS) },
                    )
                }
                composable(Routes.SERVERS) {
                    ServerListScreen(
                        onBack = { nav.popBackStack() },
                        onOpenServer = { id, name -> nav.navigate(Routes.channels(id, name)) },
                    )
                }
                composable(
                    route = Routes.CHANNELS,
                    arguments = listOf(
                        navArgument("serverId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    ChannelListScreen(
                        onBack = { nav.popBackStack() },
                        // M5b: navega pro chat do canal. Por ora no-op.
                        onOpenChannel = { _, _ -> },
                    )
                }
                composable(Routes.DMS) {
                    DmListScreen(
                        onBack = { nav.popBackStack() },
                        onOpenConversation = { id, name -> nav.navigate(Routes.dmChat(id, name)) },
                    )
                }
                composable(
                    route = Routes.DM_CHAT,
                    arguments = listOf(
                        navArgument("conversationId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    DmChatScreen(onBack = { nav.popBackStack() })
                }
            }

            // Login salva token -> isLoggedIn vira true; logout -> false.
            // Aqui reagimos e trocamos de rota limpando a backstack (popUpTo(0)).
            LaunchedEffect(loggedIn) {
                val target = if (loggedIn == true) Routes.HOME else Routes.LOGIN
                if (nav.currentDestination?.route != target) {
                    nav.navigate(target) { popUpTo(0) { inclusive = true } }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Astra",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
