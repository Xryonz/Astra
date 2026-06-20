package app.astra.mobile.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.Reveal
import app.astra.mobile.ui.theme.EaseSpring
import app.astra.mobile.ui.theme.GreatVibes
import app.astra.mobile.ui.theme.astraColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.astra.mobile.feature.auth.presentation.LoginScreen
import app.astra.mobile.feature.auth.presentation.RegisterScreen
import app.astra.mobile.feature.channel.presentation.ChannelChatScreen
import app.astra.mobile.feature.dm.presentation.DmChatScreen
import app.astra.mobile.feature.dm.presentation.DmListScreen
import app.astra.mobile.feature.friends.presentation.FriendsScreen
import app.astra.mobile.feature.home.HomeScreen
import app.astra.mobile.feature.invite.presentation.JoinServerScreen
import app.astra.mobile.feature.profile.presentation.AccountScreen
import app.astra.mobile.feature.profile.presentation.ProfileEditScreen
import app.astra.mobile.feature.profile.presentation.SettingsScreen
import app.astra.mobile.feature.profile.presentation.UserProfileScreen
import app.astra.mobile.feature.server.presentation.ChannelListScreen
import app.astra.mobile.feature.server.presentation.ServerListScreen
import app.astra.mobile.feature.voice.presentation.CallScreen
import app.astra.mobile.session.SessionViewModel
import android.net.Uri

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ACCOUNT = "settings/account"
    const val PROFILE_EDIT = "settings/profile"
    const val FRIENDS = "friends"
    const val USER_PROFILE = "user/{userId}?name={name}"
    fun userProfile(id: String, name: String) = "user/$id?name=${Uri.encode(name)}"
    const val DMS = "dms"
    const val DM_CHAT = "dm/{conversationId}?name={name}"
    const val SERVERS = "servers"
    const val JOIN = "join"
    const val CHANNELS = "channels/{serverId}?name={name}"
    const val CHANNEL_CHAT = "channel/{channelId}?name={name}"
    const val CALL = "call/{channelId}?name={name}&serverId={serverId}"
    // name encodado na query (%20 etc.) pra suportar espacos/acentos no display name.
    fun dmChat(id: String, name: String) = "dm/$id?name=${Uri.encode(name)}"
    fun channels(id: String, name: String) = "channels/$id?name=${Uri.encode(name)}"
    fun channelChat(id: String, name: String) = "channel/$id?name=${Uri.encode(name)}"
    fun call(id: String, name: String, serverId: String) =
        "call/$id?name=${Uri.encode(name)}&serverId=$serverId"
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
                // Transicoes espelham o PageTransition do web (motion/react): fade +
                // slide-x curto na curva EaseSpring (0.16,1,0.3,1), exit rapido. Sem
                // scale (blur/scale animado repinta a tela inteira por frame = jank).
                // Avanca = entra da direita; volta = entra da esquerda, atual sai pra direita.
                enterTransition = { fadeIn(tween(200, easing = EaseSpring)) + slideInHorizontally(tween(200, easing = EaseSpring)) { it / 16 } },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(200, easing = EaseSpring)) + slideInHorizontally(tween(200, easing = EaseSpring)) { -it / 16 } },
                popExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(180, easing = EaseSpring)) { it / 16 } },
            ) {
                composable(Routes.LOGIN) {
                    LoginScreen(onGoToRegister = { nav.navigate(Routes.REGISTER) })
                }
                composable(Routes.REGISTER) {
                    RegisterScreen(onGoToLogin = { nav.popBackStack() })
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenServer = { id, name -> nav.navigate(Routes.channels(id, name)) },
                        onOpenServers = { nav.navigate(Routes.SERVERS) },
                        onOpenDm = { id, name -> nav.navigate(Routes.dmChat(id, name)) },
                        onOpenDms = { nav.navigate(Routes.DMS) },
                        onOpenFriends = { nav.navigate(Routes.FRIENDS) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenProfile = { nav.navigate(Routes.PROFILE_EDIT) },
                        onJoinVoice = { channelId, name, serverId -> nav.navigate(Routes.call(channelId, name, serverId)) },
                    )
                }
                composable(Routes.FRIENDS) {
                    FriendsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenProfile = { id, name -> nav.navigate(Routes.userProfile(id, name)) },
                    )
                }
                composable(
                    route = Routes.USER_PROFILE,
                    arguments = listOf(
                        navArgument("userId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    UserProfileScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenAccount = { nav.navigate(Routes.ACCOUNT) },
                        onOpenProfile = { nav.navigate(Routes.PROFILE_EDIT) },
                    )
                }
                composable(Routes.ACCOUNT) {
                    AccountScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.PROFILE_EDIT) {
                    ProfileEditScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.SERVERS) {
                    ServerListScreen(
                        onBack = { nav.popBackStack() },
                        onOpenServer = { id, name -> nav.navigate(Routes.channels(id, name)) },
                        onOpenJoin = { nav.navigate(Routes.JOIN) },
                    )
                }
                composable(Routes.JOIN) {
                    JoinServerScreen(
                        onBack = { nav.popBackStack() },
                        onJoined = { id, name ->
                            nav.navigate(Routes.channels(id, name)) {
                                popUpTo(Routes.JOIN) { inclusive = true }
                            }
                        },
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
                        onOpenChannel = { id, name -> nav.navigate(Routes.channelChat(id, name)) },
                        onOpenVoice = { serverId, id, name -> nav.navigate(Routes.call(id, name, serverId)) },
                    )
                }
                composable(
                    route = Routes.CHANNEL_CHAT,
                    arguments = listOf(
                        navArgument("channelId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    ChannelChatScreen(onBack = { nav.popBackStack() })
                }
                composable(
                    route = Routes.CALL,
                    arguments = listOf(
                        navArgument("channelId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                        navArgument("serverId") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    CallScreen(onLeave = { nav.popBackStack() })
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
    CosmicBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Reveal {
                // Wordmark em Great Vibes com glow prata atras (textShadow do web).
                Text(
                    text = "Astra",
                    style = TextStyle(
                        fontFamily = GreatVibes,
                        fontSize = 76.sp,
                        color = astraColors.accent,
                        shadow = Shadow(astraColors.accentGlow, Offset(0f, 6f), blurRadius = 48f),
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            Reveal(delayMillis = 220) {
                MarginaliaLabel("constelacoes . pessoas . conversas")
            }
        }
    }
}
