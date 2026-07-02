package app.astra.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import app.astra.mobile.ui.components.ConstellationGraphic
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.EaseOutSoft
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
import app.astra.mobile.feature.discover.presentation.DiscoverScreen
import app.astra.mobile.feature.dm.presentation.DmChatScreen
import app.astra.mobile.feature.dm.presentation.DmListScreen
import app.astra.mobile.feature.friends.presentation.FriendsScreen
import app.astra.mobile.feature.home.HomeScreen
import app.astra.mobile.feature.invite.presentation.JoinServerScreen
import app.astra.mobile.feature.profile.presentation.AccessibilityScreen
import app.astra.mobile.feature.profile.presentation.AccountScreen
import app.astra.mobile.feature.profile.presentation.DataScreen
import app.astra.mobile.feature.profile.presentation.PersonalizationScreen
import app.astra.mobile.feature.profile.presentation.SettingsScreen
import app.astra.mobile.feature.profile.presentation.UserProfileScreen
import app.astra.mobile.feature.sessions.presentation.SessionsScreen
import app.astra.mobile.feature.wishing.presentation.WishingScreen
import app.astra.mobile.feature.server.presentation.ChannelListScreen
import app.astra.mobile.feature.server.presentation.ServerEditScreen
import app.astra.mobile.feature.voice.presentation.CallScreen
import app.astra.mobile.session.SessionViewModel
import android.net.Uri
import kotlinx.coroutines.delay

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ACCOUNT = "settings/account"
    const val PERSONALIZATION = "settings/personalization"
    const val ACCESSIBILITY = "settings/accessibility"
    const val SESSIONS = "settings/sessions"
    const val DATA = "settings/data"
    const val WISHING = "settings/wishing"
    const val FRIENDS = "friends"
    const val USER_PROFILE = "user/{userId}?name={name}"
    fun userProfile(id: String, name: String) = "user/$id?name=${Uri.encode(name)}"
    const val DMS = "dms"
    const val DM_CHAT = "dm/{conversationId}?name={name}"
    const val JOIN = "join"
    const val DISCOVER = "discover"
    const val SERVER_EDIT = "server/{serverId}/edit"
    fun serverEdit(id: String) = "server/$id/edit"
    const val CHANNELS = "channels/{serverId}?name={name}"
    const val CHANNEL_CHAT = "channel/{channelId}?name={name}"
    const val CALL = "call/{channelId}?name={name}&serverId={serverId}"

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
    val reduceMotion = LocalAppPrefs.current.reduceMotion // mestre (usado no splash)
    val transitionsOn = LocalAppPrefs.current.transitionsOn // toggle de transicoes de tela

    Box(Modifier.fillMaxSize()) {

        if (loggedIn != null) {
            val nav = rememberNavController()
            NavHost(
                navController = nav,
                startDestination = if (loggedIn == true) Routes.HOME else Routes.LOGIN,

                enterTransition = { if (!transitionsOn) fadeIn(tween(120, easing = EaseOutSoft)) else fadeIn(tween(360, easing = EaseOutSoft)) + slideInHorizontally(tween(380, easing = EaseSpring)) { it / 8 } },
                exitTransition = { fadeOut(tween(if (!transitionsOn) 90 else 240, easing = EaseOutSoft)) },
                popEnterTransition = { if (!transitionsOn) fadeIn(tween(120, easing = EaseOutSoft)) else fadeIn(tween(360, easing = EaseOutSoft)) + slideInHorizontally(tween(380, easing = EaseSpring)) { -it / 8 } },
                popExitTransition = { if (!transitionsOn) fadeOut(tween(90, easing = EaseOutSoft)) else fadeOut(tween(260, easing = EaseOutSoft)) + slideOutHorizontally(tween(340, easing = EaseSpring)) { it / 8 } },
            ) {
                composable(Routes.LOGIN) {
                    LoginScreen(onGoToRegister = { nav.navigate(Routes.REGISTER) })
                }
                composable(Routes.REGISTER) {
                    RegisterScreen(onGoToLogin = { nav.popBackStack() })
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenChannel = { id, name -> nav.navigate(Routes.channelChat(id, name)) },
                        onOpenServerEdit = { id -> nav.navigate(Routes.serverEdit(id)) },
                        onOpenJoin = { nav.navigate(Routes.JOIN) },
                        onOpenDiscover = { nav.navigate(Routes.DISCOVER) },
                        onOpenDm = { id, name -> nav.navigate(Routes.dmChat(id, name)) },
                        onOpenDms = { nav.navigate(Routes.DMS) },
                        onOpenFriends = { nav.navigate(Routes.FRIENDS) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenProfile = { nav.navigate(Routes.PERSONALIZATION) },
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
                    // Card de perfil "cobrindo a tela" vindo da direita (estilo Discord).
                    enterTransition = {
                        if (!transitionsOn) fadeIn(tween(120, easing = EaseOutSoft))
                        else slideInHorizontally(tween(380, easing = EaseSpring)) { it } + fadeIn(tween(260, easing = EaseOutSoft))
                    },
                    popExitTransition = {
                        if (!transitionsOn) fadeOut(tween(90, easing = EaseOutSoft))
                        else slideOutHorizontally(tween(340, easing = EaseSpring)) { it } + fadeOut(tween(240, easing = EaseOutSoft))
                    },
                ) {
                    UserProfileScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenAccount = { nav.navigate(Routes.ACCOUNT) },
                        onOpenPersonalization = { nav.navigate(Routes.PERSONALIZATION) },
                        onOpenAccessibility = { nav.navigate(Routes.ACCESSIBILITY) },
                        onOpenSessions = { nav.navigate(Routes.SESSIONS) },
                        onOpenData = { nav.navigate(Routes.DATA) },
                        onOpenWishing = { nav.navigate(Routes.WISHING) },
                    )
                }
                composable(Routes.ACCOUNT) {
                    AccountScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.SESSIONS) {
                    SessionsScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.DATA) {
                    DataScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.WISHING) {
                    WishingScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.PERSONALIZATION) {
                    PersonalizationScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.ACCESSIBILITY) {
                    AccessibilityScreen(onBack = { nav.popBackStack() })
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
                composable(Routes.DISCOVER) {
                    DiscoverScreen(
                        onBack = { nav.popBackStack() },
                        onOpenServer = { id, name ->
                            nav.navigate(Routes.channels(id, name)) {
                                popUpTo(Routes.DISCOVER) { inclusive = true }
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
                ) { entry ->
                    val serverId = entry.arguments?.getString("serverId").orEmpty()
                    ChannelListScreen(
                        onBack = { nav.popBackStack() },
                        onOpenChannel = { id, name -> nav.navigate(Routes.channelChat(id, name)) },
                        onOpenVoice = { sid, id, name -> nav.navigate(Routes.call(id, name, sid)) },
                        onOpenEdit = { nav.navigate(Routes.serverEdit(serverId)) },
                    )
                }
                composable(
                    route = Routes.SERVER_EDIT,
                    arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
                ) {
                    ServerEditScreen(onBack = { nav.popBackStack() })
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

            LaunchedEffect(loggedIn) {
                val target = if (loggedIn == true) Routes.HOME else Routes.LOGIN
                if (nav.currentDestination?.route != target) {
                    nav.navigate(target) { popUpTo(0) { inclusive = true } }
                }
            }
        }

        var splashVisible by remember { mutableStateOf(true) }
        val splashEnter = remember { Animatable(0f) }
        val splashExit = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(1000) // tela vazia 1s: deixa o device carregar o app antes de animar (sem travar)
            if (reduceMotion) {
                splashEnter.snapTo(1f)
                delay(700)
                splashExit.animateTo(1f, tween(220, easing = EaseOutSoft))
            } else {
                splashEnter.animateTo(1f, tween(640, easing = EaseOutSoft))
                delay(780)
                splashExit.animateTo(1f, tween(560, easing = EaseOutSoft))
            }
            splashVisible = false
        }
        if (splashVisible) {
            SplashScreen(
                textAlpha = splashEnter.value * (1f - splashExit.value),
                textScale = 0.92f + 0.08f * splashEnter.value + 0.06f * splashExit.value,
                overlayAlpha = 1f - splashExit.value,
            )
        }
    }
}

@Composable
private fun SplashScreen(textAlpha: Float, textScale: Float, overlayAlpha: Float) {

    val inf = rememberInfiniteTransition(label = "splash")
    val pulseAnim by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseOutSoft), RepeatMode.Reverse),
        label = "glow",
    )
    val pulse = if (LocalAppPrefs.current.reduceMotion) 0f else pulseAnim
    // Fundo liso (sem StarField animado) durante o cold-start: canvas a 60fps aqui
    // competia por frames e causava o travamento. O starfield segue nas telas reais.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(astraColors.void),
        contentAlignment = Alignment.Center,
    ) {
        // Constelacao se desenhando atras do nome (one-shot). So compoe depois do 1s
        // vazio (textAlpha > 0), preservando o anti-jank do cold-start.
        if (textAlpha > 0.01f) {
            ConstellationGraphic(
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha * 0.45f
                    scaleX = 1.85f
                    scaleY = 1.85f
                },
            )
        }
        Text(
            text = "Astra",
            style = TextStyle(
                fontFamily = GreatVibes,
                fontSize = 76.sp,
                color = astraColors.accent,
                shadow = Shadow(
                    astraColors.accentGlow.copy(alpha = 0.4f + 0.5f * pulse),
                    Offset(0f, 6f),
                    blurRadius = 34f + 30f * pulse,
                ),
            ),
            modifier = Modifier.graphicsLayer {
                alpha = textAlpha
                scaleX = textScale
                scaleY = textScale
            },
        )
    }
}
