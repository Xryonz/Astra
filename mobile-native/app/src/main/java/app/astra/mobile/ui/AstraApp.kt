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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.astra.mobile.feature.auth.presentation.LoginScreen
import app.astra.mobile.feature.dm.presentation.DmListScreen
import app.astra.mobile.feature.home.HomeScreen
import app.astra.mobile.session.SessionViewModel

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val DMS = "dms"
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
                    HomeScreen(onOpenDms = { nav.navigate(Routes.DMS) })
                }
                composable(Routes.DMS) {
                    DmListScreen(
                        onBack = { nav.popBackStack() },
                        // M4c: navega pro chat da conversa. Por ora no-op.
                        onOpenConversation = { _, _ -> },
                    )
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
