package app.astra.mobile.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import app.astra.mobile.ui.components.CosmicBackdrop
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import app.astra.mobile.ui.components.LocalCena
import app.astra.mobile.ui.components.LocalPalco
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.GreatVibes
import app.astra.mobile.ui.theme.astraColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.AnimatedContentScope
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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
import app.astra.mobile.feature.casca.Casca
import app.astra.mobile.feature.search.SearchScreen
import app.astra.mobile.feature.invite.presentation.JoinServerScreen
import app.astra.mobile.feature.notifications.presentation.NotificationsFeedScreen
import app.astra.mobile.feature.onboarding.presentation.OnboardingScreen
import app.astra.mobile.feature.verifyemail.presentation.VerifyEmailScreen
import app.astra.mobile.feature.notifications.presentation.NotificationsSettingsScreen
import app.astra.mobile.feature.profile.presentation.AccessibilityScreen
import app.astra.mobile.feature.profile.presentation.AccountScreen
import app.astra.mobile.feature.profile.presentation.DataScreen
import app.astra.mobile.feature.profile.presentation.AparenciaScreen
import app.astra.mobile.feature.profile.presentation.CoresDoNomeScreen
import app.astra.mobile.feature.profile.presentation.EditarPerfilScreen
import app.astra.mobile.feature.profile.presentation.FolhaDePerfil
import app.astra.mobile.feature.profile.presentation.MeuPerfilScreen
import app.astra.mobile.feature.profile.presentation.SettingsScreen
import app.astra.mobile.feature.profile.presentation.TelaSobre
import app.astra.mobile.feature.profile.presentation.VozScreen
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.dialog
import app.astra.mobile.feature.sessions.presentation.SessionsScreen
import app.astra.mobile.feature.wishing.presentation.WishingScreen
import app.astra.mobile.feature.server.presentation.ChannelListScreen
import app.astra.mobile.feature.server.presentation.ServerBadgesScreen
import app.astra.mobile.feature.server.presentation.ServerBansScreen
import app.astra.mobile.feature.server.presentation.ServerChannelsScreen
import app.astra.mobile.feature.server.presentation.ServerEditScreen
import app.astra.mobile.feature.server.presentation.ServerEmojisScreen
import app.astra.mobile.feature.server.presentation.ServerMembersScreen
import app.astra.mobile.feature.server.presentation.ServerRolesScreen
import app.astra.mobile.feature.server.presentation.ServerSettingsScreen
import app.astra.mobile.feature.server.presentation.SonsDaOrbitaScreen
import app.astra.mobile.core.deeplink.DeepLinkBus
import app.astra.mobile.feature.voice.presentation.CallScreen
import app.astra.mobile.feature.voice.presentation.ChamadaScreen
import app.astra.mobile.feature.voice.presentation.LigacaoViewModel
import app.astra.mobile.feature.voice.presentation.PreviaDaSala
import app.astra.mobile.feature.xp.presentation.FaixaDeMissao
import app.astra.mobile.feature.xp.presentation.JornadaScreen
import app.astra.mobile.session.SessionViewModel
import android.net.Uri
import kotlinx.coroutines.delay

private const val ORBITA_PEDIDA = "orbitaPedida"
private const val TELA_MS = 320
private const val RECUO_DA_ANTERIOR = 4
private const val ESCURO_DA_ANTERIOR = 0.55f

private fun NavGraphBuilder.tela(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(route = route, arguments = arguments) { entrada ->
    val cena = this
    Box(Modifier.fillMaxSize().background(astraColors.void)) { cena.content(entrada) }
}

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ACCOUNT = "settings/account"
    const val APARENCIA = "settings/appearance"
    const val CORES_DO_NOME = "settings/name-colors"
    const val EDITAR_PERFIL = "settings/profile"
    const val MEU_PERFIL = "me"
    const val ACCESSIBILITY = "settings/accessibility"
    const val NOTIFICATIONS = "settings/notifications"
    const val NOTIF_FEED = "notifications"
    const val ONBOARDING = "onboarding"
    const val SESSIONS = "settings/sessions"
    const val DATA = "settings/data"
    const val WISHING = "settings/wishing"
    const val SOBRE = "settings/sobre"
    const val FRIENDS = "friends"
    const val USER_PROFILE = "user/{userId}?name={name}"
    fun userProfile(id: String, name: String) = "user/$id?name=${Uri.encode(name)}"
    const val DMS = "dms"
    const val DM_CHAT = "dm/{conversationId}?name={name}&chamar={chamar}"
    const val JOIN = "join?code={code}"
    const val VERIFY_EMAIL = "verify-email"
    const val DISCOVER = "discover"
    const val SEARCH = "search"
    const val SERVER_EDIT = "server/{serverId}/edit"
    fun serverEdit(id: String) = "server/$id/edit"
    const val SERVER_OVERVIEW = "server/{serverId}/overview"
    fun serverOverview(id: String) = "server/$id/overview"
    const val SERVER_MEMBERS = "server/{serverId}/members"
    fun serverMembers(id: String) = "server/$id/members"
    const val SERVER_BADGES = "server/{serverId}/badges"
    fun serverBadges(id: String) = "server/$id/badges"
    const val SERVER_ROLES = "server/{serverId}/roles"
    fun serverRoles(id: String) = "server/$id/roles"
    const val SERVER_BANS = "server/{serverId}/bans"
    fun serverBans(id: String) = "server/$id/bans"
    const val SERVER_EMOJIS = "server/{serverId}/emojis"
    fun serverEmojis(id: String) = "server/$id/emojis"
    const val SERVER_SONS = "server/{serverId}/sons"
    fun serverSons(id: String) = "server/$id/sons"
    const val SERVER_CHANNELS_MGMT = "server/{serverId}/channels-manage"
    fun serverChannelsManage(id: String) = "server/$id/channels-manage"
    const val CHANNELS = "channels/{serverId}?name={name}"
    const val CHANNEL_CHAT = "channel/{channelId}?name={name}&serverId={serverId}"
    const val CALL = "call"
    const val SALA = "sala/{channelId}?name={name}&serverId={serverId}"
    const val VOZ = "settings/voice"
    const val JORNADA = "jornada"

    fun dmChat(id: String, name: String, chamar: Boolean = false) = "dm/$id?name=${Uri.encode(name)}&chamar=$chamar"
    fun channels(id: String, name: String) = "channels/$id?name=${Uri.encode(name)}"
    fun channelChat(id: String, name: String, serverId: String = "") =
        "channel/$id?name=${Uri.encode(name)}&serverId=${Uri.encode(serverId)}"
    fun sala(id: String, name: String, serverId: String) =
        "sala/$id?name=${Uri.encode(name)}&serverId=$serverId"
    fun join(code: String? = null) =
        if (code.isNullOrBlank()) "join" else "join?code=${Uri.encode(code)}"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AstraApp() {
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val loggedIn by sessionViewModel.isLoggedIn.collectAsState()
    val reduceMotion = LocalAppPrefs.current.reduceMotion
    val transitionsOn = LocalAppPrefs.current.transitionsOn
    val emCamadas = transitionsOn && !reduceMotion

    Box(Modifier.fillMaxSize().background(astraColors.void)) {

        if (loggedIn != null) {
            val nav = rememberNavController()
            SharedTransitionLayout {
            CompositionLocalProvider(LocalCena provides this) {
            NavHost(
                navController = nav,
                startDestination = if (loggedIn == true) Routes.HOME else Routes.LOGIN,

                enterTransition = {
                    if (!emCamadas) fadeIn(tween(120, easing = EaseOutSoft))
                    else slideInHorizontally(tween(TELA_MS, easing = EaseOutSoft)) { it }
                },
                exitTransition = {
                    if (!emCamadas) fadeOut(tween(90, easing = EaseOutSoft))
                    else slideOutHorizontally(tween(TELA_MS, easing = EaseOutSoft)) { -it / RECUO_DA_ANTERIOR } +
                        fadeOut(tween(TELA_MS, easing = EaseOutSoft), targetAlpha = ESCURO_DA_ANTERIOR)
                },
                popEnterTransition = {
                    if (!emCamadas) fadeIn(tween(120, easing = EaseOutSoft))
                    else slideInHorizontally(tween(TELA_MS, easing = EaseOutSoft)) { -it / RECUO_DA_ANTERIOR } +
                        fadeIn(tween(TELA_MS, easing = EaseOutSoft), initialAlpha = ESCURO_DA_ANTERIOR)
                },
                popExitTransition = {
                    if (!emCamadas) fadeOut(tween(90, easing = EaseOutSoft))
                    else slideOutHorizontally(tween(TELA_MS, easing = EaseOutSoft)) { it }
                },
            ) {
                tela(Routes.LOGIN) {
                    LoginScreen(onGoToRegister = { nav.navigate(Routes.REGISTER) })
                }
                tela(Routes.REGISTER) {
                    RegisterScreen(onGoToLogin = { nav.popBackStack() })
                }
                tela(Routes.HOME) { entrada ->
                    val orbitaPedida by entrada.savedStateHandle.getStateFlow<String?>(ORBITA_PEDIDA, null).collectAsState()
                    CompositionLocalProvider(LocalPalco provides this) {
                    Casca(
                        orbitaPedida = orbitaPedida,
                        aoAtenderPedido = { entrada.savedStateHandle[ORBITA_PEDIDA] = null },
                        aoAbrirCanal = { id, nome, orbitaId -> nav.navigate(Routes.channelChat(id, nome, orbitaId)) },
                        aoAbrirSussurro = { id, nome -> nav.navigate(Routes.dmChat(id, nome)) },
                        aoAbrirAjustesDaOrbita = { id -> nav.navigate(Routes.serverEdit(id)) },
                        aoEntrarNaVoz = { canalId, nome, orbitaId ->
                            nav.navigate(Routes.sala(canalId, nome, orbitaId))
                        },
                        aoAbrirCall = { nav.navigate(Routes.CALL) },
                        aoAbrirJornada = { nav.navigate(Routes.JORNADA) },
                        aoAbrirBusca = { nav.navigate(Routes.SEARCH) },
                        aoAbrirAmigos = { nav.navigate(Routes.FRIENDS) },
                        aoAbrirAvisos = { nav.navigate(Routes.NOTIF_FEED) },
                        aoAbrirPerfil = { nav.navigate(Routes.MEU_PERFIL) },
                        aoAbrirDescobrir = { nav.navigate(Routes.DISCOVER) },
                        aoAbrirConvite = { nav.navigate(Routes.join()) },
                        aoAbrirOnboarding = { nav.navigate(Routes.ONBOARDING) },
                        aoAbrirVerificacao = { nav.navigate(Routes.VERIFY_EMAIL) },
                    )
                    }
                }
                tela(Routes.ONBOARDING) {
                    OnboardingScreen(onDone = { nav.popBackStack() })
                }
                tela(Routes.VERIFY_EMAIL) {
                    VerifyEmailScreen(onDone = { nav.popBackStack() })
                }
                tela(Routes.NOTIF_FEED) {
                    NotificationsFeedScreen(
                        onBack = { nav.popBackStack() },
                        onOpenChannel = { id, name -> nav.navigate(Routes.channelChat(id, name)) },
                        onOpenDm = { id, name -> nav.navigate(Routes.dmChat(id, name)) },
                    )
                }
                tela(Routes.FRIENDS) {
                    FriendsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenProfile = { id, name -> nav.navigate(Routes.userProfile(id, name)) },
                    )
                }
                dialog(
                    route = Routes.USER_PROFILE,
                    arguments = listOf(
                        navArgument("userId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                    dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) {
                    FolhaDePerfil(
                        aoFechar = { nav.popBackStack() },
                        aoEditarPerfil = {
                            nav.popBackStack()
                            nav.navigate(Routes.EDITAR_PERFIL)
                        },
                        aoAbrirConversa = { c ->
                            nav.popBackStack()
                            val atual = nav.currentBackStackEntry
                            val jaEstaNela = atual?.destination?.route == Routes.DM_CHAT &&
                                atual.arguments?.getString("conversationId") == c.id
                            when {
                                !jaEstaNela -> nav.navigate(Routes.dmChat(c.id, c.nome, c.chamar))
                                c.chamar -> atual.savedStateHandle["chamar"] = true
                            }
                        },
                        aoAbrirOrbita = { id ->
                            runCatching { nav.getBackStackEntry(Routes.HOME).savedStateHandle[ORBITA_PEDIDA] = id }
                            nav.popBackStack(Routes.HOME, inclusive = false)
                        },
                    )
                }
                tela(Routes.MEU_PERFIL) {
                    MeuPerfilScreen(
                        aoFechar = { nav.popBackStack() },
                        aoEditar = { nav.navigate(Routes.EDITAR_PERFIL) },
                        aoAbrirAmigos = { nav.navigate(Routes.FRIENDS) },
                        aoAbrirConfiguracoes = { nav.navigate(Routes.SETTINGS) },
                    )
                }
                tela(Routes.EDITAR_PERFIL) {
                    EditarPerfilScreen(onBack = { nav.popBackStack() })
                }
                tela(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenProfile = { nav.navigate(Routes.EDITAR_PERFIL) },
                        onOpenAccount = { nav.navigate(Routes.ACCOUNT) },
                        onOpenNameColors = { nav.navigate(Routes.CORES_DO_NOME) },
                        onOpenAppearance = { nav.navigate(Routes.APARENCIA) },
                        onOpenAccessibility = { nav.navigate(Routes.ACCESSIBILITY) },
                        onOpenVoz = { nav.navigate(Routes.VOZ) },
                        onOpenNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                        onOpenSessions = { nav.navigate(Routes.SESSIONS) },
                        onOpenData = { nav.navigate(Routes.DATA) },
                        onOpenWishing = { nav.navigate(Routes.WISHING) },
                        onOpenAbout = { nav.navigate(Routes.SOBRE) },
                    )
                }
                tela(Routes.NOTIFICATIONS) {
                    NotificationsSettingsScreen(onBack = { nav.popBackStack() })
                }
                tela(Routes.ACCOUNT) {
                    AccountScreen(onBack = { nav.popBackStack() })
                }
                tela(Routes.SESSIONS) {
                    SessionsScreen(onBack = { nav.popBackStack() })
                }
                tela(Routes.DATA) {
                    DataScreen(onBack = { nav.popBackStack() })
                }
                tela(Routes.WISHING) {
                    WishingScreen(onBack = { nav.popBackStack() })
                }
                tela(Routes.SOBRE) {
                    TelaSobre(onBack = { nav.popBackStack() })
                }
                tela(Routes.APARENCIA) {
                    AparenciaScreen(onBack = { nav.popBackStack() })
                }
                tela(Routes.VOZ) {
                    VozScreen(onBack = { nav.popBackStack() })
                }
                tela(Routes.JORNADA) {
                    JornadaScreen(onBack = { nav.popBackStack() })
                }
                tela(Routes.CORES_DO_NOME) {
                    CoresDoNomeScreen(onBack = { nav.popBackStack() })
                }
                tela(Routes.ACCESSIBILITY) {
                    AccessibilityScreen(onBack = { nav.popBackStack() })
                }
                tela(
                    route = Routes.JOIN,
                    arguments = listOf(navArgument("code") { type = NavType.StringType; defaultValue = "" }),
                ) {
                    JoinServerScreen(
                        onBack = { nav.popBackStack() },
                        onJoined = { id, name ->
                            nav.navigate(Routes.channels(id, name)) {
                                popUpTo(Routes.JOIN) { inclusive = true }
                            }
                        },
                    )
                }
                tela(Routes.DISCOVER) {
                    DiscoverScreen(
                        onBack = { nav.popBackStack() },
                        onOpenServer = { id, name ->
                            nav.navigate(Routes.channels(id, name)) {
                                popUpTo(Routes.DISCOVER) { inclusive = true }
                            }
                        },
                    )
                }
                tela(Routes.SEARCH) {
                    SearchScreen(
                        onBack = { nav.popBackStack() },
                        onOpenServer = { id, name -> nav.navigate(Routes.channels(id, name)) },
                        onOpenChannel = { id, name -> nav.navigate(Routes.channelChat(id, name)) },
                        onOpenUser = { id, name -> nav.navigate(Routes.userProfile(id, name)) },
                    )
                }
                tela(
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
                        onOpenVoice = { sid, id, name -> nav.navigate(Routes.sala(id, name, sid)) },
                        onOpenEdit = { nav.navigate(Routes.serverEdit(serverId)) },
                    )
                }
                tela(
                    route = Routes.SERVER_EDIT,
                    arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
                ) { entry ->
                    val serverId = entry.arguments?.getString("serverId").orEmpty()
                    ServerSettingsScreen(
                        onBack = { nav.popBackStack() },
                        onClosed = { nav.popBackStack(Routes.HOME, false) },
                        onOpenOverview = { nav.navigate(Routes.serverOverview(serverId)) },
                        onOpenMembers = { nav.navigate(Routes.serverMembers(serverId)) },
                        onOpenBadges = { nav.navigate(Routes.serverBadges(serverId)) },
                        onOpenRoles = { nav.navigate(Routes.serverRoles(serverId)) },
                        onOpenBans = { nav.navigate(Routes.serverBans(serverId)) },
                        onOpenEmojis = { nav.navigate(Routes.serverEmojis(serverId)) },
                        onOpenSons = { nav.navigate(Routes.serverSons(serverId)) },
                        onOpenChannels = { nav.navigate(Routes.serverChannelsManage(serverId)) },
                    )
                }
                tela(
                    route = Routes.SERVER_OVERVIEW,
                    arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
                ) {
                    ServerEditScreen(onBack = { nav.popBackStack() })
                }
                tela(
                    route = Routes.SERVER_MEMBERS,
                    arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
                ) {
                    ServerMembersScreen(
                        onBack = { nav.popBackStack() },
                        onOpenProfile = { id, name -> nav.navigate(Routes.userProfile(id, name)) },
                    )
                }
                tela(
                    route = Routes.SERVER_BADGES,
                    arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
                ) {
                    ServerBadgesScreen(onBack = { nav.popBackStack() })
                }
                tela(
                    route = Routes.SERVER_ROLES,
                    arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
                ) {
                    ServerRolesScreen(onBack = { nav.popBackStack() })
                }
                tela(
                    route = Routes.SERVER_BANS,
                    arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
                ) {
                    ServerBansScreen(onBack = { nav.popBackStack() })
                }
                tela(
                    route = Routes.SERVER_EMOJIS,
                    arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
                ) {
                    ServerEmojisScreen(onBack = { nav.popBackStack() })
                }
                tela(
                    route = Routes.SERVER_SONS,
                    arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
                ) {
                    SonsDaOrbitaScreen(onBack = { nav.popBackStack() })
                }
                tela(
                    route = Routes.SERVER_CHANNELS_MGMT,
                    arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
                ) {
                    ServerChannelsScreen(onBack = { nav.popBackStack() })
                }
                tela(
                    route = Routes.CHANNEL_CHAT,
                    arguments = listOf(
                        navArgument("channelId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                        navArgument("serverId") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entrada ->
                    CompositionLocalProvider(LocalPalco provides this) {
                        ChannelChatScreen(
                            onBack = { nav.popBackStack() },
                            onOpenProfile = { id, name -> nav.navigate(Routes.userProfile(id, name)) },
                            temOrbita = !entrada.arguments?.getString("serverId").isNullOrBlank(),
                        )
                    }
                }
                dialog(
                    route = Routes.CALL,
                    dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) {
                    CallScreen(aoEncolher = { nav.popBackStack() })
                }
                dialog(
                    route = Routes.SALA,
                    arguments = listOf(
                        navArgument("channelId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                        navArgument("serverId") { type = NavType.StringType; defaultValue = "" },
                    ),
                    dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) {
                    PreviaDaSala(
                        aoFechar = { nav.popBackStack() },
                        aoEntrar = {
                            nav.popBackStack()
                            nav.navigate(Routes.CALL)
                        },
                    )
                }
                tela(Routes.DMS) {
                    DmListScreen(
                        onBack = { nav.popBackStack() },
                        onOpenConversation = { id, name -> nav.navigate(Routes.dmChat(id, name)) },
                    )
                }
                tela(
                    route = Routes.DM_CHAT,
                    arguments = listOf(
                        navArgument("conversationId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                        navArgument("chamar") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { entrada ->
                    val pedirChamada by entrada.savedStateHandle.getStateFlow("chamar", false).collectAsState()
                    CompositionLocalProvider(LocalPalco provides this) {
                        DmChatScreen(
                            onBack = { nav.popBackStack() },
                            onOpenProfile = { id, name -> nav.navigate(Routes.userProfile(id, name)) },
                            pedirChamada = pedirChamada,
                            aoAtenderPedido = { entrada.savedStateHandle["chamar"] = false },
                        )
                    }
                }
            }
            }
            }

            LaunchedEffect(loggedIn) {
                val target = if (loggedIn == true) Routes.HOME else Routes.LOGIN
                if (nav.currentDestination?.route != target) {
                    nav.navigate(target) { popUpTo(0) { inclusive = true } }
                }
            }

            val pendingInvite by DeepLinkBus.pendingInviteCode.collectAsState()
            LaunchedEffect(pendingInvite, loggedIn) {
                val code = pendingInvite ?: return@LaunchedEffect
                if (loggedIn == true) {
                    DeepLinkBus.pendingInviteCode.value = null
                    nav.navigate(Routes.join(code))
                }
            }

            val pendingShare by DeepLinkBus.pendingShare.collectAsState()
            LaunchedEffect(pendingShare, loggedIn) {
                val share = pendingShare ?: return@LaunchedEffect
                val convId = share.conversationId
                if (convId == null) {
                    DeepLinkBus.pendingShare.value = null
                    return@LaunchedEffect
                }
                if (loggedIn == true) {
                    nav.navigate(Routes.dmChat(convId, share.name ?: "Conversa")) { launchSingleTop = true }
                    if (share.text == null && share.imageUri == null) {
                        DeepLinkBus.pendingShare.value = null
                    }
                }
            }

            val ligacaoVm: LigacaoViewModel = hiltViewModel()
            val ligacao by ligacaoVm.ligacao.collectAsState()
            LaunchedEffect(Unit) {
                ligacaoVm.entrouNaCall.collect { nav.navigate(Routes.CALL) }
            }
            val abrirCall by DeepLinkBus.abrirCall.collectAsState()
            LaunchedEffect(abrirCall, loggedIn) {
                if (!abrirCall) return@LaunchedEffect
                DeepLinkBus.abrirCall.value = false
                if (loggedIn == true) nav.navigate(Routes.CALL)
            }
            val atenderPedido by DeepLinkBus.atenderLigacao.collectAsState()
            LaunchedEffect(ligacao) { if (ligacao == null) DeepLinkBus.atenderLigacao.value = null }
            if (loggedIn == true) FaixaDeMissao()
            ligacao?.let { chamada ->
                ChamadaScreen(
                    ligacao = chamada,
                    atenderJa = atenderPedido == chamada.conversationId,
                    aoAtender = {
                        DeepLinkBus.atenderLigacao.value = null
                        ligacaoVm.atender()
                    },
                    aoRecusar = {
                        DeepLinkBus.atenderLigacao.value = null
                        ligacaoVm.recusar()
                    },
                )
            }
        }

        var splashVisible by remember { mutableStateOf(true) }
        val splashEnter = remember { Animatable(0f) }
        val splashExit = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(1000)
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(astraColors.void),
        contentAlignment = Alignment.Center,
    ) {
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
