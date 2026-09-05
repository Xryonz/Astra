package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.astra.desktop.ui.theme.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import app.astra.desktop.AvisosNaTela
import app.astra.desktop.auth.Session
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.xp.MissoesStore
import app.astra.desktop.xp.XpStore
import app.astra.desktop.prefs.AvisosDaConta
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.TemaDaConta
import app.astra.desktop.ModoTransmissao
import app.astra.desktop.VozNaBandeja
import app.astra.desktop.voice.Sfx
import app.astra.desktop.voice.CallNaSala
import app.astra.desktop.voice.LeituraDoCaminho
import app.astra.desktop.voice.VoiceSession
import app.astra.desktop.shell.CacheDeConversas
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.ChatVm
import app.astra.desktop.shell.Selection
import app.astra.desktop.shell.ShellVm
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.BotOff
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.UserCheck
import com.composables.icons.lucide.UserMinus
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.Volume2
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.InviteApi
import app.astra.mobile.core.network.NotificationApi
import app.astra.mobile.core.network.BlockApi
import app.astra.mobile.core.network.FriendApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ChannelActivityEventDto
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ChannelVisibilityDto
import app.astra.mobile.core.network.dto.RoleDto
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.MemberRoleDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.GlobalContext
import app.astra.desktop.ui.theme.Tipo

@Composable
fun ShellScreen(
    session: Session,
    windowInactive: () -> Boolean,
    notify: (String, String) -> Unit,
    aoPedirJanela: () -> Unit,
    onLogout: () -> Unit,
    searchOpen: Boolean = false,
    onCloseSearch: () -> Unit = {},
    notifOpen: Boolean = false,
    onCloseNotif: () -> Unit = {},
    desejosOpen: Boolean = false,
    onCloseDesejos: () -> Unit = {},
    missoesOpen: Boolean = false,
    onAbrirMissoes: () -> Unit = {},
    onCloseMissoes: () -> Unit = {},
    onNotifUnread: (Int) -> Unit = {},
) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    val socket = remember { koin.get<DesktopSocket>() }
    val prefs = remember { koin.get<DesktopPrefs>() }
    val prefState by prefs.state.collectAsState()
    val vm = remember {
        ShellVm(
            scope, koin.get<ServerApi>(), koin.get<ChannelApi>(), koin.get<UserApi>(), koin.get<DmApi>(), koin.get<VoiceApi>(),
            koin.get<NotificationApi>(), koin.get<InviteApi>(), koin.get<SessionStore>(), socket, koin.get<Json>(), session.userId,
        )
    }
    val state by vm.state.collectAsState()
    val voice = remember { VoiceSession(scope, koin) }
    DisposableEffect(voice) {
        VozNaBandeja.assumir(voice)
        onDispose { VozNaBandeja.largar(voice); voice.encerrar() }
    }
    remember(voice) { vm.voiceSession = voice }
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsTab by remember { mutableStateOf(SettingsTab.ACCOUNT) }
    var serverSettingsOpen by remember { mutableStateOf(false) }
    var convidarPelaFaixa by remember { mutableStateOf<ServerDto?>(null) }
    var paletteOpen by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    LaunchedEffect(Unit) { socket.connect() }

    LaunchedEffect(Unit) { koin.get<XpStore>().iniciar(scope) }

    LaunchedEffect(Unit) { koin.get<MissoesStore>().iniciar(scope) }

    val sessionStore = remember { koin.get<SessionStore>() }
    var permsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (sessionStore.uiPref("permsVistas") != "1") {
            kotlinx.coroutines.delay(900)
            permsOpen = true
        }
    }
    if (permsOpen) {
        PermissoesDialog(
            onTestarAviso = { notify("Astra", "Pronto — os avisos do Astra estão liberados.") },
            onClose = {
                permsOpen = false
                sessionStore.setUiPref("permsVistas", "1")
            },
        )
    }

    state.penalidade?.let { p ->
        val onde = p.constelacao?.let { " de $it" } ?: ""
        CenteredConfirmDialog(
            message = if (p.tipo == "banido") "você foi banido$onde" else "você foi removido$onde",
            detalhe = when {
                !p.motivo.isNullOrBlank() -> "motivo: ${p.motivo}"
                p.tipo == "banido" -> "não há como entrar de novo enquanto o banimento valer."
                else -> "você pode entrar de novo se receber um convite."
            },
            confirmLabel = "entendi",
            cancelLabel = null,
            perigo = false,
            onConfirm = { vm.dispensarPenalidade() },
            onDismiss = { vm.dispensarPenalidade() },
        )
    }

    var notifRefresh by remember { mutableStateOf(0) }
    var notifCount by remember { mutableStateOf(0) }
    LaunchedEffect(notifRefresh) {
        while (true) {
            val c = runCatching { koin.get<NotificationApi>().unread().data?.count }.getOrNull() ?: 0
            notifCount = c
            onNotifUnread(c)
            delay(120_000)
        }
    }
    val json = remember { koin.get<Json>() }
    LaunchedEffect(Unit) {
        socket.notification.collect { raw ->
            notifCount += 1
            onNotifUnread(notifCount)
            val silencioso = runCatching {
                json.parseToJsonElement(raw).jsonObject["silent"]?.jsonPrimitive?.boolean
            }.getOrNull() ?: false
            if (!silencioso && prefs.state.value.somDeAviso && !ModoTransmissao.ativo.value) {
                Sfx.aviso()
            }
            if (!silencioso) AvisosDoPet.mensagemNova()
        }
    }

    fun avisoDeTeste() {
        if (prefs.state.value.avisoDiscreto || ModoTransmissao.ativo.value) {
            notify("Astra", "Se você está lendo isto, os avisos funcionam.")
            return
        }
        val eu = state.me
        AvisosNaTela.mostrar(
            quem = eu?.displayName ?: eu?.username ?: "Astra",
            onde = "teste",
            trecho = "Se você está lendo isto, os avisos funcionam.",
            avatarUrl = eu?.avatarUrl,
        )
    }

    val avisosDaConta = remember { koin.get<AvisosDaConta>() }
    LaunchedEffect(Unit) { avisosDaConta.carregar() }

    val temaDaConta = remember { koin.get<TemaDaConta>() }
    LaunchedEffect(Unit) { temaDaConta.sincronizar() }
    LaunchedEffect(Unit) {
        launch {
            socket.newDm.collect { raw ->
                if (!windowInactive() || !prefs.state.value.notifyDms) return@collect
                if (avisosDaConta.devoCalar(vm.state.value.me?.effectiveStatus)) return@collect
                val msg = runCatching { json.decodeFromString<DmMessageDto>(raw) }.getOrNull() ?: return@collect
                if (msg.senderId == session.userId) return@collect
                if (vm.state.value.dms.any { it.id == msg.conversationId && it.muted }) return@collect
                if (prefs.state.value.avisoDiscreto || ModoTransmissao.ativo.value) {
                    notify("Astra", "sussurro novo")
                    return@collect
                }
                val name = msg.author?.displayName ?: msg.author?.username ?: "alguém"
                AvisosNaTela.mostrar(
                    quem = name,
                    onde = "",
                    trecho = msg.content.ifBlank { "enviou um anexo" }.take(140),
                    avatarUrl = msg.author?.avatarUrl,
                    abrir = {
                        aoPedirJanela()
                        vm.select(Selection.Dms)
                        vm.openChat(ChatTarget.Dm(msg.conversationId, name))
                    },
                )
            }
        }
        launch {
            socket.channelActivity.collect { raw ->
                if (!windowInactive() || !prefs.state.value.notifyChannels) return@collect
                if (avisosDaConta.devoCalar(vm.state.value.me?.effectiveStatus)) return@collect
                val ev = runCatching { json.decodeFromString<ChannelActivityEventDto>(raw) }.getOrNull() ?: return@collect
                val estado = vm.state.value
                val ch = estado.servers.flatMap { it.channels }.find { it.id == ev.channelId } ?: return@collect
                if (estado.orbitaSilenciada(ev.channelId)) return@collect
                if (prefs.state.value.avisoDiscreto || ModoTransmissao.ativo.value) {
                    notify("Astra", "mensagem numa constelação")
                    return@collect
                }
                val quem = ev.authorName
                if (quem == null) {
                    notify("#${ch.name}", "nova mensagem")
                    return@collect
                }
                AvisosNaTela.mostrar(
                    quem = quem,
                    onde = listOfNotNull("#${ev.channelName ?: ch.name}", ev.serverName).joinToString(" · "),
                    trecho = ev.preview.orEmpty().ifBlank { "enviou um anexo" },
                    avatarUrl = ev.authorAvatar,
                    abrir = {
                        aoPedirJanela()
                        estado.servers.firstOrNull { s -> s.channels.any { it.id == ev.channelId } }
                            ?.let { vm.select(Selection.Server(it.id)) }
                        vm.openChat(ChatTarget.Channel(ev.channelId, ch.name))
                    },
                )
            }
        }
    }

    val chat = state.chat
    val cacheDeConversas = remember { CacheDeConversas() }
    val createChatVm = remember {
        { target: ChatTarget ->
            ChatVm(
                scope, target,
                koin.get<ChannelApi>(), koin.get<DmApi>(), koin.get<UploadApi>(),
                socket, koin.get<Json>(), session.userId,
                cache = cacheDeConversas,
                myProfile = { vm.state.value.me },
            )
        }
    }

    val emSegundoPlano = !LocalWindowActive.current
    val cores = remember(state.members) { coresDeCargo(state.members) }
    CompositionLocalProvider(
        LocalReduceMotion provides (prefState.reduceMotionEff || emSegundoPlano),
        LocalRenderPrefs provides RenderPrefs(prefState.auroraQuality.octaves, prefState.uiFps.cap),
        LocalMinhaConta provides MinhaConta(session.userId, state.me?.username),
        LocalCoresDeCargo provides cores,
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.K) {
                    paletteOpen = true; true
                } else false
            },
    ) {
        val onbStore = remember { GlobalContext.get().get<SessionStore>() }
        var checklistActive by remember(session.userId) {
            mutableStateOf(onbStore.uiPref("checklist:${session.userId}") == "1")
        }
        LaunchedEffect(checklistActive, state.servers.size, state.dms.size) {
            if (checklistActive && state.servers.isNotEmpty() && state.dms.isNotEmpty()) {
                onbStore.setUiPref("checklist:${session.userId}", "0")
                checklistActive = false
            }
        }
        val avisoDePerf = prefs.state.value.perfAutomatico
        val temAlgoNaVaga = checklistActive || avisoDePerf.isNotBlank()
        val firstSteps: (@Composable () -> Unit)? = if (temAlgoNaVaga) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (avisoDePerf.isNotBlank()) {
                        AvisoDeMaquinaEconomica(avisoDePerf, prefs::dispensarAvisoDePerf)
                    }
                    if (checklistActive) {
                        FirstStepsCard(
                            hasServer = state.servers.isNotEmpty(),
                            hasDm = state.dms.isNotEmpty(),
                            hasAvatar = state.me?.avatarUrl != null,
                            onDismiss = {
                                onbStore.setUiPref("checklist:${session.userId}", "0")
                                checklistActive = false
                            },
                        )
                    }
                }
            }
        } else {
            null
        }

        val shellCoberto = settingsOpen || serverSettingsOpen
        val shellVisivel by animateFloatAsState(
            if (shellCoberto) 0f else 1f,
            tween(160),
            label = "shellVisivel",
        )
        CompositionLocalProvider(
            LocalReduceMotion provides (prefState.reduceMotionEff || emSegundoPlano || shellCoberto),
        ) {
        Box(
            Modifier
                .graphicsLayer { alpha = shellVisivel }
                .drawWithContent { if (shellVisivel > 0.001f) drawContent() },
        ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(RESPIRO_DA_JANELA)
                .clip(FORMA_DO_SHELL)
                .border(1.dp, Obsidian.borderMid.copy(alpha = 0.55f), FORMA_DO_SHELL),
        ) {
        Row(Modifier.fillMaxSize()) {
        val podeConfigurar: (String) -> Boolean = { id ->
            (state.selection as? Selection.Server)?.id == id &&
                state.myPerms?.let { it.isOwner || it.isAdmin || "MANAGE_SERVER" in it.permissions } == true
        }
        val podeGerenciarOrbitas: (String) -> Boolean = { id ->
            (state.selection as? Selection.Server)?.id == id &&
                state.myPerms?.let { it.isOwner || it.isAdmin || "MANAGE_CHANNELS" in it.permissions } == true
        }
        val abrirConfigDaConstelacao: (String) -> Unit = { id ->
            vm.select(Selection.Server(id))
            serverSettingsOpen = true
        }
        Column(Modifier.width(LARGURA_RAIL + LARGURA_SIDEBAR).fillMaxHeight()) {
        Row(Modifier.weight(1f)) {
        Rail(
            servers = state.servers,
            selection = state.selection,
            myId = session.userId,
            mutedServers = state.mutedServers,
            sussurroNaoLido = state.dms.any { it.id in state.unread },
            canManageSelected = podeConfigurar,
            onOpenServerSettings = abrirConfigDaConstelacao,
            onSelect = vm::select,
            onLeaveServer = vm::leaveServer,
            onDeleteServer = vm::deleteServer,
            onCreateServer = vm::createServer,
            onToggleServerMute = vm::toggleServerMute,
            onMarkServerRead = vm::markServerRead,
            onAddMember = vm::addMember,
            onJoinInvite = vm::joinByInvite,
        )
        Sidebar(
            selection = state.selection,
            servers = state.servers,
            dms = state.dms,
            activeChatId = chat?.id ?: state.voiceChannel?.id,
            unread = state.unread,
            unreadCounts = state.unreadCounts,
            dmTyping = state.dmTyping,
            dmPresence = state.dmPresence,
            loading = state.loading,
            members = state.members,
            voicePresence = state.voicePresence,
            memberPresence = state.memberPresence,
            myId = session.userId,
            onConvidar = { convidarPelaFaixa = it },
            myVoiceChannelId = voice.joined?.id,
            onOpenChat = vm::openChat,
            onOpenVoice = vm::openVoice,
            onToggleMute = vm::toggleDmMute,
            onMarkRead = vm::markDmRead,
            onCloseDm = vm::closeDm,
            friendsOpen = state.friendsOpen,
            onOpenFriends = vm::openFriends,
            onCreateChannel = vm::createChannel,
            onCreateCategory = vm::createCategory,
            onRenameCategory = vm::renameCategory,
            onDeleteCategory = vm::deleteCategory,
            onReorderChannels = vm::reorderChannel,
            onMoveChannelToCategory = vm::moveChannelToCategory,
            onReorderCategories = vm::reorderCategories,
            onRenameChannel = vm::renameChannel,
            onDeleteChannel = vm::deleteChannel,
            onMarkChannelRead = vm::markChannelRead,
            silenciada = state::orbitaSilenciada,
            onToggleChannelMute = vm::toggleChannelMute,
            onToggleChannelBot = vm::setChannelBot,
            onToggleChannelKeepBot = vm::setChannelKeepBot,
            onToggleCatBot = vm::setCategoryBot,
            membersOpen = state.membersOpen,
            onToggleMembers = vm::toggleMembers,
            canManageSelected = podeConfigurar,
            podeGerenciarOrbitas = podeGerenciarOrbitas,
            onOpenServerSettings = abrirConfigDaConstelacao,
            visibilidade = remember(vm) {
                QuemVeAOrbita(
                    ler = vm::carregarVisibilidade,
                    cargos = vm::loadRoles,
                    salvar = vm::salvarVisibilidade,
                )
            },
            firstSteps = firstSteps,
        )
        }
        Spacer(Modifier.height(ALTURA_DO_RODAPE))
        }
        Stage(
            state.selectedServer,
            chat = chat,
            voiceChannel = state.voiceChannel,
            call = voice.callFor(state.voiceChannel),
            mudo = voice.mudo,
            aoAlternarMudo = voice::alternarMudo,
            ensurdecido = voice.ensurdecido,
            aoAlternarEnsurdecer = voice::alternarEnsurdecer,
            voicePresence = state.voiceChannel?.let { state.voicePresence[it.id] }.orEmpty(),
            onJoinVoice = { state.voiceChannel?.let { voice.join(it); vm.announceVoiceJoin(it.id) } },
            onLeaveVoice = {
                if (voice.emSussurro) vm.desligarSussurro() else { voice.leave(); vm.leaveVoice() }
            },
            onLigarSussurro = { alvo, video ->
                val foto = state.dms.find { it.id == alvo.id }?.otherUser?.avatarUrl
                vm.ligarNoSussurro(alvo.id, alvo.title, foto, video)
            },
            botDoOutroLado = (chat as? ChatTarget.Dm)?.let { alvo ->
                state.dms.find { it.id == alvo.id }?.otherUser?.username == USUARIO_DA_BOT
            } == true,
            fonteDoSussurro = (chat as? ChatTarget.Dm)?.let { alvo ->
                state.dms.find { it.id == alvo.id }?.otherUser?.displayFont
            },
            createChatVm = createChatVm,
            members = state.members,
            me = state.me,
            loading = state.loading,
            error = state.error,
            onRetry = vm::load,
            onStartDm = vm::startDm,
            showDiscover = state.selection is Selection.Discover,
            onDiscoverJoined = vm::refreshServersAndSelect,
            joinedServerIds = remember(state.servers) { state.servers.map { it.id }.toSet() },
            showFriends = state.selection is Selection.Dms && state.friendsOpen,
            leiturasAoEntrar = state.leiturasAoEntrar,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = state.selection is Selection.Server && state.membersOpen,
            enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
            exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120)),
        ) {
            MembersPanel(
                members = state.members,
                presence = state.memberPresence,
                atividade = state.memberActivity,
                myId = session.userId,
                serverId = state.selectedServer?.id,
                isOwner = state.selectedServer?.ownerId == session.userId,
                onStartDm = vm::startDm,
                onKick = { uid -> state.selectedServer?.id?.let { vm.kickMember(it, uid) } },
                onBan = { uid -> state.selectedServer?.id?.let { vm.banMember(it, uid) } },
            )
        }
        }
        val chamadaViva = voice.call
        val caminhoDaChamada by produceState<LeituraDoCaminho?>(null, chamadaViva) {
            value = null
            chamadaViva?.caminhoDaVoz?.collect { value = it }
        }
        UserFooter(
            me = state.me,
            fallbackName = session.displayName,
            onEdited = vm::refreshMe,
            onOpenSettings = { t -> settingsTab = t; settingsOpen = true },
            onAbrirJornada = onAbrirMissoes,
            onLogout = onLogout,
            mudo = voice.mudo,
            ensurdecido = voice.ensurdecido,
            onAlternarMudo = voice::alternarMudo,
            onAlternarEnsurdecer = voice::alternarEnsurdecer,
            caminho = caminhoDaChamada,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(LARGURA_RAIL + LARGURA_SIDEBAR)
                .height(ALTURA_DO_RODAPE),
        )
        }
        }
        }

        PetDoAstra(
            ligado = prefs.state.value.petLigado,
            petId = prefs.state.value.petTipo,
            pelagem = prefs.state.value.petPelagem,
            nome = prefs.state.value.petNome,
        )

        val cfgServer = state.selectedServer
        AnimatedVisibility(
            visible = serverSettingsOpen && cfgServer != null,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.98f),
        ) {
            val shown = remember(cfgServer) { cfgServer }
            shown?.let { srv ->
                ServerSettingsScreen(
                    server = srv,
                    isOwner = srv.ownerId == session.userId,
                    members = state.members,
                    myPermissions = remember(state.myPerms) { state.myPerms?.permissions.orEmpty().toSet() },
                    onClose = { serverSettingsOpen = false },
                    onSave = { body, cb -> vm.updateServer(srv.id, body, cb) },
                    onRegenerateInvite = { cb -> vm.regenerateInvite(srv.id, cb) },
                    onDelete = { serverSettingsOpen = false; vm.deleteServer(srv.id) },
                    onLeave = { serverSettingsOpen = false; vm.leaveServer(srv.id) },
                    onLoadRoles = { cb -> vm.loadRoles(srv.id, cb) },
                    onSaveRole = { id, body, cb -> vm.saveRole(srv.id, id, body, cb) },
                    onDeleteRole = { id, cb -> vm.deleteRole(srv.id, id, cb) },
                    onToggleMemberRole = { mid, rid, give, cb -> vm.setMemberRole(srv.id, mid, rid, give, cb) },
                    onLoadBans = { cb -> vm.loadBans(srv.id, cb) },
                    onUnban = { uid, cb -> vm.unbanUser(srv.id, uid, cb) },
                )
            }
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.98f),
        ) {
            SettingsScreen(
                me = state.me,
                prefs = prefs,
                aparelhos = voice,
                initialTab = settingsTab,
                onClose = { settingsOpen = false },
                onProfileSaved = { vm.refreshMe() },
                aoSairDaConta = onLogout,
                onTestarNotificacao = { avisoDeTeste() },
            )
        }

        AnimatedVisibility(
            visible = paletteOpen,
            enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.97f, transformOrigin = TransformOrigin(0.5f, 0f)),
            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.97f, transformOrigin = TransformOrigin(0.5f, 0f)),
        ) {
            CommandPalette(
                servers = state.servers,
                dms = state.dms,
                onClose = { paletteOpen = false },
                onOpenChannel = { sid, cid, name ->
                    vm.select(Selection.Server(sid))
                    vm.openChat(ChatTarget.Channel(cid, name))
                },
                onOpenDm = { cid, title ->
                    vm.select(Selection.Dms)
                    vm.openChat(ChatTarget.Dm(cid, title))
                },
            )
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.97f, transformOrigin = TransformOrigin(0.5f, 0f)),
            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.97f, transformOrigin = TransformOrigin(0.5f, 0f)),
        ) {
            SearchOverlay(
                currentServerId = state.selectedServer?.id,
                onClose = onCloseSearch,
                onOpenChannel = { sid, cid, name ->
                    vm.select(Selection.Server(sid))
                    vm.openChat(ChatTarget.Channel(cid, name))
                },
                onWhisper = { username, title -> vm.startDm(username, title) },
                onOpenDm = { convId, title ->
                    vm.select(Selection.Dms)
                    vm.openChat(ChatTarget.Dm(convId, title))
                },
            )
        }

        AnimatedVisibility(
            visible = notifOpen,
            enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.98f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.98f, transformOrigin = TransformOrigin(1f, 0f)),
        ) {
            NotifPanel(
                onClose = onCloseNotif,
                onOpenChannel = { sid, cid, name ->
                    vm.select(Selection.Server(sid))
                    vm.openChat(ChatTarget.Channel(cid, name))
                },
                onOpenDm = { cid, title ->
                    vm.select(Selection.Dms)
                    vm.openChat(ChatTarget.Dm(cid, title))
                },
                onOpenServer = { sid -> vm.select(Selection.Server(sid)) },
                onAfterRead = { notifRefresh++ },
            )
        }

        AnimatedVisibility(
            visible = desejosOpen,
            enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.98f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.98f, transformOrigin = TransformOrigin(1f, 0f)),
        ) {
            DesejosPanel(onClose = onCloseDesejos)
        }

        AnimatedVisibility(
            visible = missoesOpen,
            enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.97f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.97f, transformOrigin = TransformOrigin(1f, 0f)),
        ) {
            MissoesOverlay(me = state.me, onClose = onCloseMissoes)
        }

        convidarPelaFaixa?.let { alvo ->
            InvitePeopleDialog(
                serverName = alvo.name,
                inviteCode = alvo.inviteCode,
                onAdd = { username, onResult -> vm.addMember(alvo.id, username, onResult) },
                onClose = { convidarPelaFaixa = null },
            )
        }

        MissaoToaster()

        state.chamada?.let { c ->
            ChamadaScreen(c, onAtender = vm::atenderChamada, onRecusar = vm::recusarChamada)
        }
    }
    }
}

private const val USUARIO_DA_BOT = "astra_bot"

private val LARGURA_RAIL = 72.dp
private val LARGURA_SIDEBAR = 260.dp
private val RESPIRO_DA_JANELA = 10.dp
private val FORMA_DO_SHELL = RoundedCornerShape(10.dp)
private val ALTURA_DO_RODAPE = 62.dp

private fun Modifier.panelSurface(bg: Color, alpha: Float): Modifier =
    this.background(bg.copy(alpha = alpha))

@Composable
fun ConfirmPopup(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = "cancelar",
    posicao: PopupPositionProvider? = null,
) {
    val corpo: @Composable () -> Unit = {
        Column(
            Modifier
                .popupReveal()
                .clip(RoundedCornerShape(10.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                .padding(14.dp),
        ) {
            Text(
                message,
                style = Tipo.corpo,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    cancelLabel,
                    style = Tipo.descricao,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    confirmLabel,
                    style = Tipo.erro,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .border(1.dp, Obsidian.danger, RoundedCornerShape(7.dp))
                        .clickable { onDismiss(); onConfirm() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
    val props = PopupProperties(focusable = true)
    if (posicao != null) {
        Popup(popupPositionProvider = posicao, onDismissRequest = onDismiss, properties = props) { corpo() }
    } else {
        Popup(onDismissRequest = onDismiss, properties = props) { corpo() }
    }
}

object AoLadoDoBotao : PopupPositionProvider {
    private const val FOLGA = 8

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val direita = anchorBounds.right + FOLGA
        val x = if (direita + popupContentSize.width <= windowSize.width) direita
        else (anchorBounds.left - FOLGA - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.center.y - popupContentSize.height / 2)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

@Composable
fun CenteredConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String? = "cancelar",
    detalhe: String? = null,
    perigo: Boolean = true,
) {
    val reduce = LocalReduceMotion.current
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium))
    }
    val scrimSrc = remember { MutableInteractionSource() }
    val cardSrc = remember { MutableInteractionSource() }
    val cancelSrc = remember { MutableInteractionSource() }
    val okSrc = remember { MutableInteractionSource() }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value }
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = scrimSrc, indication = null, onClick = onDismiss)
                .semCursorDeClique(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        val s = 0.92f + 0.08f * enter.value
                        scaleX = s; scaleY = s
                    }
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Obsidian.overlay)
                    .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = cardSrc, indication = null, onClick = {})
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    message,
                    style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif),
                )
                if (detalhe != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        detalhe,
                        style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                val corBotao = if (perigo) Obsidian.danger else Obsidian.accent
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (cancelLabel != null) {
                        Text(
                            cancelLabel,
                            style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
                            modifier = Modifier
                                .clickScale(cancelSrc)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                                .clickable(interactionSource = cancelSrc, indication = null, onClick = onDismiss)
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                    Text(
                        confirmLabel,
                        style = TextStyle(color = corBotao, fontSize = 13.sp),
                        modifier = Modifier
                            .clickScale(okSrc)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, corBotao, RoundedCornerShape(8.dp))
                            .clickable(interactionSource = okSrc, indication = null) { onDismiss(); onConfirm() }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

private data class QuickResult(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val voice: Boolean,
    val serverId: String?,
)

@Composable
private fun CommandPalette(
    servers: List<ServerDto>,
    dms: List<ConversationDto>,
    onClose: () -> Unit,
    onOpenChannel: (serverId: String, channelId: String, name: String) -> Unit,
    onOpenDm: (convId: String, title: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(0) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

    val all = remember(servers, dms) {
        buildList {
            servers.forEach { s ->
                s.channels.sortedBy { it.position }.forEach { c ->
                    add(QuickResult("channel", c.id, c.name, s.name, c.type == "VOICE", s.id))
                }
            }
            dms.forEach { d ->
                val t = d.otherUser?.displayName ?: d.otherUser?.username ?: "sussurro"
                add(QuickResult("dm", d.id, t, "sussurro", false, null))
            }
        }
    }
    val results = remember(all, query) {
        val q = query.trim()
        (if (q.isBlank()) all else all.filter { it.title.contains(q, true) || it.subtitle.contains(q, true) }).take(50)
    }
    LaunchedEffect(results.size) { if (sel >= results.size) sel = 0 }

    fun choose(r: QuickResult) {
        if (r.kind == "channel" && r.serverId != null) onOpenChannel(r.serverId, r.id, r.title)
        else onOpenDm(r.id, r.title)
        onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Obsidian.void.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .semCursorDeClique(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 96.dp)
                .width(520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Escape -> { onClose(); true }
                        Key.DirectionDown -> { if (results.isNotEmpty()) sel = (sel + 1) % results.size; true }
                        Key.DirectionUp -> { if (results.isNotEmpty()) sel = (sel - 1 + results.size) % results.size; true }
                        Key.Enter -> { results.getOrNull(sel)?.let { choose(it) }; true }
                        else -> false
                    }
                }
                .padding(12.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it; sel = 0 },
                singleLine = true,
                textStyle = TextStyle(color = Obsidian.text1, fontSize = 15.sp),
                cursorBrush = SolidColor(Obsidian.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                        if (query.isEmpty()) {
                            Text(
                                "pular para um canal ou sussurro…",
                                style = TextStyle(color = Obsidian.text3, fontSize = 15.sp),
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            if (results.isEmpty()) {
                Text(
                    "nada encontrado",
                    style = Tipo.descricao,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(results, key = { _, r -> r.kind + r.id }) { i, r ->
                        PaletteRow(r, i == sel) { choose(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(r: QuickResult, active: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Obsidian.active else if (hov) Obsidian.hover else Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (r.kind == "dm") {
            Text("@", style = TextStyle(color = if (active) Obsidian.accent else Obsidian.text3, fontSize = 14.sp))
        } else {
            LIcon(
                if (r.voice) Lucide.Volume2 else Lucide.Hash,
                tint = if (active) Obsidian.accent else Obsidian.text3,
                size = 15.dp,
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            r.title,
            style = Tipo.corpo,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(r.subtitle, style = Tipo.apoio, maxLines = 1)
    }
}

@Composable
private fun Rail(
    servers: List<ServerDto>,
    selection: Selection,
    myId: String?,
    mutedServers: Set<String>,
    sussurroNaoLido: Boolean,
    canManageSelected: (String) -> Boolean,
    onOpenServerSettings: (String) -> Unit,
    onSelect: (Selection) -> Unit,
    onLeaveServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onCreateServer: (name: String) -> Unit,
    onToggleServerMute: (String) -> Unit,
    onMarkServerRead: (String) -> Unit,
    onAddMember: (serverId: String, username: String, onResult: (String?) -> Unit) -> Unit,
    onJoinInvite: (raw: String, onResult: (String?) -> Unit) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var inviteFor by remember { mutableStateOf<ServerDto?>(null) }
    Column(
        modifier = Modifier.width(LARGURA_RAIL).fillMaxHeight().panelSurface(Obsidian.void, 0.72f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier.size(72.dp).drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            Obsidian.accent.copy(alpha = 0.16f),
                            Obsidian.accent.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * 0.52f,
                    ),
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(44.dp)) {
                RailItem(
                    active = selection is Selection.Dms,
                    onClick = { onSelect(Selection.Dms) },
                    rotulo = "sussurros",
                ) {
                    Image(
                        painter = painterResource("astra-glyph.png"),
                        contentDescription = "sussurros",
                        modifier = Modifier.size(26.dp),
                    )
                }
                if (sussurroNaoLido) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Obsidian.void),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Obsidian.accent))
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        DivisoriaDaRail()
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(servers, key = { it.id }) { srv ->
                var confirmLeave by remember(srv.id) { mutableStateOf(false) }
                var confirmDelete by remember(srv.id) { mutableStateOf(false) }
                val isOwner = srv.ownerId == myId
                EditorialContextMenu(entries = {
                    buildList {
                        add(MenuEntry.Item("convidar pessoas", icon = Lucide.Users) { inviteFor = srv })
                        srv.inviteCode?.let { code ->
                            add(MenuEntry.Item("copiar link do convite", icon = Lucide.Link) {
                                clipboard.setText(AnnotatedString(inviteLink(code)))
                            })
                        }
                        add(MenuEntry.Item(if (srv.id in mutedServers) "reativar constelação" else "silenciar constelação", icon = if (srv.id in mutedServers) Lucide.Bell else Lucide.BellOff) { onToggleServerMute(srv.id) })
                        add(MenuEntry.Item("marcar tudo como lido", icon = Lucide.CheckCheck) { onMarkServerRead(srv.id) })
                        add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(srv.id)) })
                        if (isOwner || canManageSelected(srv.id)) {
                            add(MenuEntry.Separator)
                            add(MenuEntry.Item("configurações", icon = Lucide.Settings) { onOpenServerSettings(srv.id) })
                        }
                        add(MenuEntry.Separator)
                        if (isOwner) add(MenuEntry.Item("excluir constelação", danger = true, icon = Lucide.Trash2) { confirmDelete = true })
                        else add(MenuEntry.Item("sair da constelação", danger = true, icon = Lucide.LogOut) { confirmLeave = true })
                    }
                }) {
                if (confirmLeave) {
                    Popup(
                        onDismissRequest = { confirmLeave = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Obsidian.overlay)
                                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                                .padding(14.dp),
                        ) {
                            Text(
                                "sair de ${srv.name}?",
                                style = Tipo.corpo,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "ficar",
                                    style = Tipo.descricao,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp))
                                        .clickable { confirmLeave = false }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                                Text(
                                    "sair",
                                    style = Tipo.erro,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Obsidian.danger, RoundedCornerShape(7.dp))
                                        .clickable {
                                            confirmLeave = false
                                            onLeaveServer(srv.id)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                if (confirmDelete) {
                    Popup(
                        onDismissRequest = { confirmDelete = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Obsidian.overlay)
                                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                                .padding(14.dp),
                        ) {
                            Text(
                                "excluir ${srv.name}? apaga a constelação para todos — não há como desfazer.",
                                style = Tipo.corpo,
                                modifier = Modifier.widthIn(max = 240.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "cancelar",
                                    style = Tipo.descricao,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp))
                                        .clickable { confirmDelete = false }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                                Text(
                                    "excluir",
                                    style = Tipo.erro,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Obsidian.danger, RoundedCornerShape(7.dp))
                                        .clickable {
                                            confirmDelete = false
                                            onDeleteServer(srv.id)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                RailItem(
                    active = (selection as? Selection.Server)?.id == srv.id,
                    onClick = { onSelect(Selection.Server(srv.id)) },
                    rotulo = srv.name,
                ) {
                    if (!srv.iconUrl.isNullOrBlank() && !imagemMorreu(srv.iconUrl)) {
                        AsyncImage(
                            model = srv.iconUrl,
                            contentDescription = srv.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onState = { lembrarQueMorreu(srv.iconUrl, it) },
                        )
                    } else {
                        Text(
                            text = srv.name.take(1).uppercase(),
                            style = TextStyle(color = Obsidian.accent, fontSize = 17.sp, fontFamily = DmSerif),
                        )
                    }
                }
                }
            }
            item(key = "create-server") { CreateServerButton(onCreateServer, onJoinInvite) }
            item(key = "descobrir") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(4.dp))
                    DivisoriaDaRail()
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier.size(72.dp).drawBehind {
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Obsidian.accent.copy(alpha = 0.16f),
                                        Obsidian.accent.copy(alpha = 0.05f),
                                        Color.Transparent,
                                    ),
                                    center = center,
                                    radius = size.minDimension * 0.52f,
                                ),
                            )
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        RailItem(
                            active = selection is Selection.Discover,
                            onClick = { onSelect(Selection.Discover) },
                            rotulo = "descobrir",
                        ) {
                            LIcon(Lucide.Compass, tint = Obsidian.accent, size = 20.dp, rotulo = "descobrir")
                        }
                    }
                }
            }
        }
    }
    inviteFor?.let { srv ->
        InvitePeopleDialog(
            serverName = srv.name,
            inviteCode = srv.inviteCode,
            onAdd = { username, onResult -> onAddMember(srv.id, username, onResult) },
            onClose = { inviteFor = null },
        )
    }
}

@Composable
private fun CreateServerButton(
    onCreateServer: (name: String) -> Unit,
    onJoinInvite: (raw: String, onResult: (String?) -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var joinOpen by remember { mutableStateOf(false) }
    var criando by remember { mutableStateOf(false) }
    Box {
        RailItem(
            active = false,
            onClick = { menuOpen = true },
            rotulo = if (menuOpen) null else "adicionar",
        ) {
            Text("+", style = TextStyle(color = Obsidian.accent, fontSize = 22.sp))
        }
        if (menuOpen) {
            Popup(
                popupPositionProvider = RailMenuBeside,
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .popupReveal()
                        .widthIn(min = 170.dp, max = 230.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                ) {
                    CreateMenuRow(glyph = "✦", label = "criar constelação") { menuOpen = false; criando = true }
                    CreateMenuRow(icon = Lucide.Link, label = "entrar com convite") { menuOpen = false; joinOpen = true }
                }
            }
        }
    }
    if (joinOpen) {
        JoinByInviteDialog(onJoin = onJoinInvite, onClose = { joinOpen = false })
    }
    if (criando) {
        EditorialInputDialog(
            title = "nova constelação",
            placeholder = "nome da constelação",
            initial = "",
            confirmLabel = "criar",
            channelType = false,
            onDismiss = { criando = false },
            onConfirm = { name, _ -> onCreateServer(name) },
        )
    }
}

@Composable
private fun CreateMenuRow(
    label: String,
    glyph: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) Obsidian.hover else Color.Transparent, tween(100))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            glyph != null -> Text(glyph, style = TextStyle(color = Obsidian.accent, fontSize = 14.sp))
            icon != null -> LIcon(icon, tint = Obsidian.accent, size = 14.dp)
        }
        Spacer(Modifier.width(9.dp))
        Text(label, style = TextStyle(color = if (hovered) Obsidian.text1 else Obsidian.text2, fontSize = 13.sp))
    }
}

private object RailMenuBeside : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = (anchorBounds.right + 8).coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0),
        y = anchorBounds.top.coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0),
    )
}

@Composable
private fun DivisoriaDaRail() {
    Box(Modifier.width(24.dp).height(1.dp).background(Obsidian.borderDim.copy(alpha = 0.6f)))
}

private const val ATRASO_DO_BALAO_MS = 90L
private const val ENTRADA_DO_BALAO_MS = 120

private class BalaoDaRail(private val margem: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = (anchorBounds.right + margem).coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0),
        y = (anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2)
            .coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0),
    )
}

@Composable
private fun BalaoDoNome(nome: String) {
    val reduzir = LocalReduceMotion.current
    val entrada = remember { Animatable(if (reduzir) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduzir) entrada.animateTo(1f, tween(ENTRADA_DO_BALAO_MS, easing = EaseOutStd))
    }
    val desliza = with(LocalDensity.current) { 6.dp.toPx() }
    val forma = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer {
            alpha = entrada.value
            translationX = -(1f - entrada.value) * desliza
        },
    ) {
        Canvas(Modifier.size(width = 5.dp, height = 10.dp)) {
            drawPath(
                Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(0f, size.height / 2f)
                    lineTo(size.width, size.height)
                    close()
                },
                Obsidian.overlay,
            )
        }
        Text(
            nome,
            style = TextStyle(color = Obsidian.text1, fontSize = 12.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .clip(forma)
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderDim, forma)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun RailItem(
    active: Boolean,
    onClick: () -> Unit,
    rotulo: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var balaoAberto by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        if (!hovered) {
            balaoAberto = false
        } else {
            delay(ATRASO_DO_BALAO_MS)
            balaoAberto = true
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val bg by animateColorAsState(
        when {
            active -> Obsidian.overlay
            hovered -> Obsidian.hover
            else -> Obsidian.raised
        },
        tween(140),
    )
    val borderColor by animateColorAsState(
        when {
            active -> Obsidian.accent.copy(alpha = 0.55f)
            hovered -> Obsidian.borderMid
            else -> Obsidian.borderDim
        },
        tween(140),
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickScale(interaction)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (rotulo != null && balaoAberto) {
            val margem = with(LocalDensity.current) { 10.dp.roundToPx() }
            Popup(popupPositionProvider = remember(margem) { BalaoDaRail(margem) }) {
                BalaoDoNome(rotulo)
            }
        }
    }
}

@Composable
private fun BotaoDaFaixa(
    icone: ImageVector,
    rotulo: String,
    aceso: Boolean = false,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val forma = RoundedCornerShape(8.dp)
    val fundo by animateColorAsState(
        when {
            aceso -> Obsidian.accent.copy(alpha = 0.14f)
            hov -> Obsidian.hover
            else -> Color.Transparent
        },
        tween(120),
    )
    val cor by animateColorAsState(
        when {
            aceso -> Obsidian.accent
            hov -> Obsidian.text1
            else -> Obsidian.text3
        },
        tween(120),
    )
    Box(
        Modifier
            .size(30.dp)
            .clickScale(src, pressedScale = 0.92f, formaDoFoco = forma)
            .clip(forma)
            .background(fundo)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icone, tint = cor, size = 15.dp, rotulo = rotulo)
    }
}

@Composable
private fun FaixaDaConstelacao(
    nome: String,
    membros: Int,
    online: Int,
    membrosAbertos: Boolean,
    onToggleMembros: () -> Unit,
    onConvidar: () -> Unit,
    podeConfigurar: Boolean,
    onAbrirConfig: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                nome,
                style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Obsidian.success))
                Spacer(Modifier.width(5.dp))
                Text(
                    "$online/$membros " + (if (membros == 1) "membro" else "membros") + " online",
                    style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        AcoesDaFaixa(
            membrosAbertos = membrosAbertos,
            podeConfigurar = podeConfigurar,
            onConvidar = onConvidar,
            onToggleMembros = onToggleMembros,
            onAbrirConfig = onAbrirConfig,
        )
    }
}

@Composable
private fun AcoesDaFaixa(
    membrosAbertos: Boolean,
    podeConfigurar: Boolean,
    onConvidar: () -> Unit,
    onToggleMembros: () -> Unit,
    onAbrirConfig: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BotaoDaFaixa(Lucide.UserPlus, rotulo = "convidar pessoas", onClick = onConvidar)
        Spacer(Modifier.width(2.dp))
        BotaoDaFaixa(
            Lucide.Users,
            rotulo = if (membrosAbertos) "ocultar membros" else "mostrar membros",
            aceso = membrosAbertos,
            onClick = onToggleMembros,
        )
        if (podeConfigurar) {
            Spacer(Modifier.width(2.dp))
            BotaoDaFaixa(Lucide.Settings, rotulo = "configurações da constelação", onClick = onAbrirConfig)
        }
    }
}

@Composable
private fun ServerHeaderBanner(srv: ServerDto) {
    val forma = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .aspectRatio(ServerBannerAspect)
            .clip(forma)
            .border(1.dp, Obsidian.borderMid.copy(alpha = 0.6f), forma),
    ) {
        if (!srv.bannerUrl.isNullOrBlank()) {
            ProfileBanner(
                css = null,
                imageUrl = srv.bannerUrl,
                positionY = srv.bannerPositionY,
                scale = srv.bannerScale,
                fallback = Obsidian.overlay,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Obsidian.overlay, Obsidian.raised))))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    1f to Obsidian.void.copy(alpha = 0.85f),
                ),
            ),
        )
    }
}

@Composable
private fun Sidebar(
    selection: Selection,
    servers: List<ServerDto>,
    dms: List<ConversationDto>,
    activeChatId: String?,
    unread: Set<String>,
    unreadCounts: Map<String, Int>,
    dmTyping: Set<String>,
    dmPresence: Map<String, String>,
    loading: Boolean,
    members: List<ServerMemberDto>,
    voicePresence: Map<String, List<String>>,
    memberPresence: Map<String, String>,
    myId: String?,
    myVoiceChannelId: String?,
    onConvidar: (ServerDto) -> Unit,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    onToggleMute: (ConversationDto) -> Unit,
    onMarkRead: (String) -> Unit,
    onCloseDm: (String) -> Unit,
    friendsOpen: Boolean,
    onOpenFriends: () -> Unit,
    onCreateChannel: (serverId: String, name: String, type: String, categoryId: String?) -> Unit,
    onCreateCategory: (serverId: String, name: String) -> Unit,
    onRenameCategory: (serverId: String, categoryId: String, name: String) -> Unit,
    onDeleteCategory: (serverId: String, categoryId: String) -> Unit,
    onReorderChannels: (serverId: String, orderedIds: List<String>) -> Unit,
    onMoveChannelToCategory: (serverId: String, channelId: String, categoryId: String) -> Unit,
    onReorderCategories: (serverId: String, orderedIds: List<String>) -> Unit,
    onRenameChannel: (serverId: String, channelId: String, name: String) -> Unit,
    onDeleteChannel: (serverId: String, channelId: String) -> Unit,
    onMarkChannelRead: (channelId: String) -> Unit,
    silenciada: (channelId: String) -> Boolean,
    onToggleChannelMute: (channelId: String) -> Unit,
    onToggleChannelBot: (serverId: String, channelId: String, ligar: Boolean) -> Unit,
    onToggleChannelKeepBot: (serverId: String, channelId: String, guardar: Boolean) -> Unit,
    onToggleCatBot: (serverId: String, categoryId: String, ligar: Boolean) -> Unit,
    membersOpen: Boolean,
    onToggleMembers: () -> Unit,
    canManageSelected: (String) -> Boolean,
    podeGerenciarOrbitas: (String) -> Boolean,
    onOpenServerSettings: (String) -> Unit,
    visibilidade: QuemVeAOrbita,
    firstSteps: (@Composable () -> Unit)? = null,
) {
    var chanDialog by remember { mutableStateOf<ChanDialog?>(null) }
    Column(Modifier.width(LARGURA_SIDEBAR).fillMaxHeight().panelSurface(Obsidian.base, 0.62f)) {
        AnimatedContent(
            targetState = selection,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { -it / 12 })
                    .togetherWith(fadeOut(tween(120)))
            },
            modifier = Modifier.weight(1f),
        ) { sel ->
            val srv = (sel as? Selection.Server)?.let { s -> servers.find { it.id == s.id } }
            Column(Modifier.fillMaxSize()) {
                val header = @Composable {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            text = when {
                                sel is Selection.Dms -> "Sussurros"
                                sel is Selection.Discover -> "Descobrir"
                                else -> srv?.name ?: ""
                            },
                            style = TextStyle(
                                color = Obsidian.text1, fontSize = 16.sp,
                                fontFamily = DmSerif,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (srv != null) {
                    val isOwnerHere = srv.ownerId == myId
                    val podeMexerNasOrbitas = isOwnerHere || podeGerenciarOrbitas(srv.id)
                    EditorialContextMenu(entries = {
                        buildList {
                            add(MenuEntry.Item("marcar tudo como lido", icon = Lucide.CheckCheck) {
                                srv.channels.forEach { if (it.id in unread) onMarkChannelRead(it.id) }
                            })
                            if (podeMexerNasOrbitas) {
                                add(MenuEntry.Separator)
                                add(MenuEntry.Item("criar órbita", icon = Lucide.Plus) { chanDialog = ChanDialog.NewChannel(srv.id, null) })
                                add(MenuEntry.Item("criar categoria", icon = Lucide.FolderPlus) { chanDialog = ChanDialog.NewCategory(srv.id) })
                            }
                        }
                    }) { ServerHeaderBanner(srv) }
                    FaixaDaConstelacao(
                        nome = srv.name,
                        membros = members.size,
                        online = members.count { it.userId == myId || memberPresence[it.userId]?.let { p -> p != "OFFLINE" } == true },
                        membrosAbertos = membersOpen,
                        onToggleMembros = onToggleMembers,
                        onConvidar = { onConvidar(srv) },
                        podeConfigurar = isOwnerHere || canManageSelected(srv.id),
                        onAbrirConfig = { onOpenServerSettings(srv.id) },
                    )
                } else {
                    header()
                }

                Box(Modifier.weight(1f)) {
                    when {
                        loading -> SidebarSkeleton()
                        sel is Selection.Dms -> Column(Modifier.fillMaxSize()) {
                            FriendsNavRow(active = friendsOpen, onClick = onOpenFriends)
                            DmList(dms, servers, onToggleMute, onMarkRead, onCloseDm, activeChatId, unread, dmTyping, dmPresence, onOpenChat)
                        }
                        sel is Selection.Discover -> DiscoverSidebarMap()
                        else -> {
                            val orbits: @Composable () -> Unit = {
                                OrbitList(
                                    srv, activeChatId, unread, unreadCounts, members, voicePresence, myId, myVoiceChannelId,
                                    onOpenChat, onOpenVoice,
                                    podeGerenciarOrbitas = podeGerenciarOrbitas,
                                    onNewChannelInCat = { catId -> srv?.let { chanDialog = ChanDialog.NewChannel(it.id, catId) } },
                                    onRenameCat = { catId, cur -> srv?.let { chanDialog = ChanDialog.RenameCategory(it.id, catId, cur) } },
                                    onDeleteCat = { catId -> srv?.let { onDeleteCategory(it.id, catId) } },
                                    onReorderChannels = { ids -> srv?.let { onReorderChannels(it.id, ids) } },
                                    onMoveToCategory = { cid, catId -> srv?.let { onMoveChannelToCategory(it.id, cid, catId) } },
                                    onReorderCategories = { ids -> srv?.let { onReorderCategories(it.id, ids) } },
                                    onOpenChannelRename = { cid, cur -> srv?.let { chanDialog = ChanDialog.RenameChannel(it.id, cid, cur) } },
                                    onOpenChannelVisibility = { cid, name -> srv?.let { chanDialog = ChanDialog.Visibilidade(it.id, cid, name) } },
                                    onExcluirCanal = { cid -> srv?.let { onDeleteChannel(it.id, cid) } },
                                    onMarkChannelRead = onMarkChannelRead,
                                    silenciada = silenciada,
                                    onToggleChannelMute = onToggleChannelMute,
                                    onToggleChannelBot = { cid, on -> srv?.let { onToggleChannelBot(it.id, cid, on) } },
                                    onToggleChannelKeepBot = { cid, on -> srv?.let { onToggleChannelKeepBot(it.id, cid, on) } },
                                    onToggleCatBot = { catId, on -> srv?.let { onToggleCatBot(it.id, catId, on) } },
                                )
                            }
                            if (srv != null) {
                                val podeMexerAqui = srv.ownerId == myId || podeGerenciarOrbitas(srv.id)
                                EditorialContextMenu(entries = {
                                    buildList {
                                        add(MenuEntry.Item("marcar tudo como lido", icon = Lucide.CheckCheck) {
                                            srv.channels.forEach { if (it.id in unread) onMarkChannelRead(it.id) }
                                        })
                                        if (podeMexerAqui) {
                                            add(MenuEntry.Separator)
                                            add(MenuEntry.Item("criar órbita", icon = Lucide.Plus) { chanDialog = ChanDialog.NewChannel(srv.id, null) })
                                            add(MenuEntry.Item("criar categoria", icon = Lucide.FolderPlus) { chanDialog = ChanDialog.NewCategory(srv.id) })
                                        }
                                    }
                                }) { orbits() }
                            } else orbits()
                        }
                    }
                }
            }
        }

        firstSteps?.let { fs ->
            fs()
            Spacer(Modifier.height(8.dp))
        }
    }

    when (val d = chanDialog) {
        is ChanDialog.NewChannel -> EditorialInputDialog(
            title = "nova órbita",
            placeholder = "nome-da-órbita",
            initial = "",
            confirmLabel = "criar",
            channelType = true,
            onDismiss = { chanDialog = null },
            onConfirm = { name, type -> onCreateChannel(d.serverId, name, type, d.categoryId) },
        )
        is ChanDialog.NewCategory -> EditorialInputDialog(
            title = "nova categoria",
            placeholder = "nome da categoria",
            initial = "",
            confirmLabel = "criar",
            channelType = false,
            onDismiss = { chanDialog = null },
            onConfirm = { name, _ -> onCreateCategory(d.serverId, name) },
        )
        is ChanDialog.RenameCategory -> EditorialInputDialog(
            title = "renomear categoria",
            placeholder = "nome da categoria",
            initial = d.current,
            confirmLabel = "salvar",
            channelType = false,
            onDismiss = { chanDialog = null },
            onConfirm = { name, _ -> onRenameCategory(d.serverId, d.categoryId, name) },
        )
        is ChanDialog.RenameChannel -> EditorialInputDialog(
            title = "renomear órbita",
            placeholder = "nome-da-órbita",
            initial = d.current,
            confirmLabel = "salvar",
            channelType = true,
            onDismiss = { chanDialog = null },
            onConfirm = { name, _ -> onRenameChannel(d.serverId, d.channelId, name) },
        )
        is ChanDialog.Visibilidade -> VisibilidadeDaOrbitaDialog(
            nomeDaOrbita = "#${d.name}",
            aoCentro = CenterInWindow,
            carregar = { pronto -> visibilidade.ler(d.serverId, d.channelId, pronto) },
            carregarCargos = { pronto -> visibilidade.cargos(d.serverId, pronto) },
            salvar = { privada, cargos, pronto ->
                visibilidade.salvar(d.serverId, d.channelId, privada, cargos, pronto)
            },
            onDismiss = { chanDialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun OrbitList(
    server: ServerDto?,
    activeChatId: String?,
    unread: Set<String>,
    unreadCounts: Map<String, Int>,
    members: List<ServerMemberDto>,
    voicePresence: Map<String, List<String>>,
    myId: String?,
    myVoiceChannelId: String?,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    podeGerenciarOrbitas: (String) -> Boolean,
    onNewChannelInCat: (categoryId: String) -> Unit,
    onRenameCat: (categoryId: String, current: String) -> Unit,
    onDeleteCat: (categoryId: String) -> Unit,
    onReorderChannels: (orderedIds: List<String>) -> Unit,
    onMoveToCategory: (channelId: String, categoryId: String) -> Unit,
    onReorderCategories: (orderedIds: List<String>) -> Unit,
    onOpenChannelRename: (channelId: String, current: String) -> Unit,
    onOpenChannelVisibility: (channelId: String, name: String) -> Unit,
    onExcluirCanal: (channelId: String) -> Unit,
    onMarkChannelRead: (channelId: String) -> Unit,
    silenciada: (channelId: String) -> Boolean,
    onToggleChannelMute: (channelId: String) -> Unit,
    onToggleChannelBot: (channelId: String, ligar: Boolean) -> Unit,
    onToggleChannelKeepBot: (channelId: String, guardar: Boolean) -> Unit,
    onToggleCatBot: (categoryId: String, ligar: Boolean) -> Unit,
) {
    if (server == null) return
    val podeGerenciar = server.ownerId == myId || podeGerenciarOrbitas(server.id)
    val clipboard = LocalClipboardManager.current
    var collapsedCats by remember(server.id) { mutableStateOf(setOf<String>()) }
    val pessoaPorId = remember(members) { members.associateBy { it.userId } }
    val catIds = remember(server.categories) { server.categories.map { it.id }.toSet() }
    val loose = remember(server.channels, catIds) {
        server.channels.filter { it.categoryId == null || it.categoryId !in catIds }.sortedBy { it.position }
    }
    val cats = remember(server.categories) { server.categories.sortedBy { it.position } }
    val byCat = remember(server.channels) { server.channels.groupBy { it.categoryId } }
    val looseIds = remember(loose) { loose.map { it.id } }
    val drag = remember(server.id) { ChannelDragState() }
    val chMenu = ChannelMenu(
        podeGerenciar, silenciada, onMarkChannelRead, onOpenChannelRename, onOpenChannelVisibility,
        onExcluirCanal, onToggleChannelMute,
        botAtende = { ch ->
            ch.botEnabled ?: server.categories.find { it.id == ch.categoryId }?.botEnabled ?: true
        },
        onToggleBot = onToggleChannelBot,
        onToggleKeepBot = onToggleChannelKeepBot,
    )

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
        itemsIndexed(loose, key = { _, ch -> ch.id }) { i, ch ->
            CascadeIn(i, server.id) {
                OrbitEntry(
                    ch, ch.id == activeChatId, ch.id in unread, unreadCounts[ch.id] ?: 0,
                    pessoaPorId, voicePresence, myId, myVoiceChannelId, onOpenChat, onOpenVoice,
                    dragCtx = if (podeGerenciar) ChannelDragCtx(drag, "loose", i, loose.size, looseIds, onReorderChannels, onMoveToCategory) else null,
                    menu = chMenu,
                )
            }
        }
        var offset = loose.size
        val orderedCatIds = cats.map { it.id }
        cats.forEachIndexed { catIndex, cat ->
            val channels = byCat[cat.id].orEmpty().sortedBy { it.position }
            val headerRow = offset
            val collapsed = cat.id in collapsedCats
            val channelIds = channels.map { it.id }
            val visible =
                if (collapsed) channels.filter { it.id == activeChatId || it.id in unread }
                else channels
            item(key = "cat-${cat.id}") {
                val highlight = drag.dragging && drag.hoverCat == cat.id && drag.section != "cat:${cat.id}"
                val hi by animateFloatAsState(if (highlight) 1f else 0f, tween(120), label = "catHitbox")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { drag.catBounds[cat.id] = it.boundsInWindow() }
                        .drawBehind {
                            if (hi > 0f) {
                                val inset = 6.dp.toPx()
                                val tl = Offset(inset, 3.dp.toPx())
                                val sz = Size(size.width - inset * 2f, size.height - 6.dp.toPx())
                                val rad = CornerRadius(10.dp.toPx())
                                drawRoundRect(Obsidian.accent.copy(alpha = 0.07f * hi), tl, sz, rad)
                                drawRoundRect(Obsidian.accent.copy(alpha = 0.55f * hi), tl, sz, rad, style = Stroke(1.5.dp.toPx()))
                            }
                        },
                ) {
                    CascadeIn(headerRow, server.id) {
                        val head = @Composable {
                            CategoryHeader(
                                name = cat.name,
                                collapsed = collapsed,
                                onToggle = {
                                    collapsedCats =
                                        if (cat.id in collapsedCats) collapsedCats - cat.id else collapsedCats + cat.id
                                },
                                dragCtx = if (podeGerenciar) CategoryDragCtx(drag, catIndex, orderedCatIds, onReorderCategories) else null,
                            )
                        }
                        val catUnread = channels.any { it.id in unread }
                        var confirmDelCat by remember(cat.id) { mutableStateOf(false) }
                        EditorialContextMenu(entries = {
                            buildList {
                                if (catUnread) add(MenuEntry.Item("marcar categoria como lida", icon = Lucide.CheckCheck) {
                                    channels.forEach { if (it.id in unread) onMarkChannelRead(it.id) }
                                })
                                add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(cat.id)) })
                                if (podeGerenciar) {
                                    add(MenuEntry.Separator)
                                    add(MenuEntry.Item("criar órbita aqui", icon = Lucide.Plus) { onNewChannelInCat(cat.id) })
                                    val botNaCat = cat.botEnabled ?: true
                                    add(
                                        MenuEntry.Item(
                                            if (botNaCat) "silenciar a bot na categoria" else "deixar a bot atender na categoria",
                                            icon = if (botNaCat) Lucide.BotOff else Lucide.Bot,
                                        ) { onToggleCatBot(cat.id, !botNaCat) },
                                    )
                                    add(MenuEntry.Item("renomear categoria", icon = Lucide.Pencil) { onRenameCat(cat.id, cat.name) })
                                    add(MenuEntry.Item("excluir categoria", danger = true, icon = Lucide.Trash2) { confirmDelCat = true })
                                }
                            }
                        }) {
                            head()
                            if (confirmDelCat) ConfirmPopup(
                                message = "excluir a categoria ${cat.name}? não há como desfazer.",
                                confirmLabel = "excluir",
                                onConfirm = { onDeleteCat(cat.id) },
                                onDismiss = { confirmDelCat = false },
                            )
                        }
                    }
                    visible.forEachIndexed { i, ch ->
                        key(ch.id) {
                        CascadeIn(
                            i,
                            "${server.id}:${cat.id}:$collapsed",
                            startDelayMs = minOf(headerRow, 6).toLong() * 26L,
                        ) {
                            OrbitEntry(
                                ch, ch.id == activeChatId, ch.id in unread, unreadCounts[ch.id] ?: 0,
                                pessoaPorId, voicePresence, myId, myVoiceChannelId, onOpenChat, onOpenVoice,
                                dragCtx = if (podeGerenciar && !collapsed)
                                    ChannelDragCtx(drag, "cat:${cat.id}", i, channels.size, channelIds, onReorderChannels, onMoveToCategory) else null,
                                menu = chMenu,
                            )
                        }
                        }
                    }
                }
            }
            offset = headerRow + 1 + visible.size
        }
    }
    ChannelDragBubble(drag)
    }
}

private class ChannelDragState {
    var id by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var isVoice by mutableStateOf(false)
    var section by mutableStateOf<String?>(null)
    var fromIndex by mutableStateOf(-1)
    var targetIndex by mutableStateOf(-1)
    var windowPos by mutableStateOf(Offset.Zero)
    var fadingOut by mutableStateOf(false)
    var hoverCat by mutableStateOf<String?>(null)
    val catBounds = mutableStateMapOf<String, Rect>()
    var isCategory by mutableStateOf(false)
    val dragging: Boolean get() = id != null && !fadingOut
    fun reset() {
        id = null; name = ""; isVoice = false; section = null
        fromIndex = -1; targetIndex = -1; fadingOut = false; hoverCat = null; isCategory = false
    }
}

private class ChannelDragCtx(
    val state: ChannelDragState,
    val section: String,
    val index: Int,
    val sectionSize: Int,
    val orderedIds: List<String>,
    val onReorder: (List<String>) -> Unit,
    val onMoveToCategory: (channelId: String, categoryId: String) -> Unit,
)

@Composable
private fun Modifier.channelDrag(ch: ChannelDto, ctx: ChannelDragCtx?): Modifier {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var itemH by remember { mutableStateOf(1f) }
    if (ctx == null) return this
    val d = ctx.state
    return this
        .onGloballyPositioned { coords = it; itemH = it.size.height.toFloat().coerceAtLeast(1f) }
        .pointerInput(ch.id, ctx.section, ctx.index, ctx.sectionSize) {
            var accY = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { pos ->
                    accY = 0f
                    d.reset()
                    d.id = ch.id
                    d.name = ch.name
                    d.isVoice = ch.type == "VOICE"
                    d.section = ctx.section
                    d.fromIndex = ctx.index
                    d.targetIndex = ctx.index
                    coords?.let { c -> d.windowPos = c.localToWindow(pos) }
                },
                onDrag = { change, delta ->
                    change.consume()
                    accY += delta.y
                    coords?.let { c -> d.windowPos = c.localToWindow(change.position) }
                    d.targetIndex = (ctx.index + (accY / itemH).roundToInt()).coerceIn(0, ctx.sectionSize - 1)
                    d.hoverCat = d.catBounds.entries.firstOrNull { it.value.contains(d.windowPos) }?.key
                },
                onDragEnd = {
                    if (d.id == ch.id) {
                        val srcCat = if (ctx.section.startsWith("cat:")) ctx.section.removePrefix("cat:") else null
                        val over = d.hoverCat
                        if (over != null && over != srcCat) {
                            ctx.onMoveToCategory(ch.id, over)
                        } else if (d.targetIndex in 0 until ctx.sectionSize && d.targetIndex != d.fromIndex) {
                            val list = ctx.orderedIds.toMutableList()
                            list.add(d.targetIndex, list.removeAt(d.fromIndex))
                            ctx.onReorder(list)
                        }
                        d.fadingOut = true
                    }
                },
                onDragCancel = { if (d.id == ch.id) d.reset() },
            )
        }
}

private class CategoryDragCtx(
    val state: ChannelDragState,
    val index: Int,
    val orderedIds: List<String>,
    val onReorder: (List<String>) -> Unit,
)

@Composable
private fun Modifier.categoryDrag(name: String, ctx: CategoryDragCtx?): Modifier {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    if (ctx == null) return this
    val catId = ctx.orderedIds.getOrNull(ctx.index) ?: return this
    val d = ctx.state
    return this
        .onGloballyPositioned { coords = it }
        .pointerInput(catId, ctx.index, ctx.orderedIds.size) {
            detectDragGesturesAfterLongPress(
                onDragStart = { pos ->
                    d.reset()
                    d.id = catId
                    d.name = name
                    d.isCategory = true
                    d.fromIndex = ctx.index
                    d.targetIndex = ctx.index
                    coords?.let { c -> d.windowPos = c.localToWindow(pos) }
                },
                onDrag = { change, _ ->
                    change.consume()
                    coords?.let { c -> d.windowPos = c.localToWindow(change.position) }
                    val overId = d.catBounds.entries.firstOrNull { it.value.contains(d.windowPos) }?.key
                    val idx = ctx.orderedIds.indexOf(overId)
                    if (idx >= 0) d.targetIndex = idx
                },
                onDragEnd = {
                    if (d.id == catId) {
                        if (d.targetIndex in ctx.orderedIds.indices && d.targetIndex != d.fromIndex) {
                            val list = ctx.orderedIds.toMutableList()
                            list.add(d.targetIndex, list.removeAt(d.fromIndex))
                            ctx.onReorder(list)
                        }
                        d.fadingOut = true
                    }
                },
                onDragCancel = { if (d.id == catId) d.reset() },
            )
        }
}

@Composable
private fun ChannelDragBubble(d: ChannelDragState) {
    if (d.id == null) return
    val reduce = LocalReduceMotion.current
    val enter = remember(d.id) { Animatable(0f) }
    val splat = remember(d.id) { Animatable(0f) }
    LaunchedEffect(d.id) {
        if (reduce) enter.snapTo(1f)
        else enter.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(d.fadingOut) {
        if (d.fadingOut) {
            if (reduce) splat.snapTo(1f) else splat.animateTo(1f, tween(170, easing = FastOutLinearInEasing))
            d.reset()
        }
    }
    val pos = d.windowPos
    val name = d.name
    val voice = d.isVoice
    Popup(
        popupPositionProvider = remember(pos) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = IntOffset(
                    (pos.x - popupContentSize.width / 2f).roundToInt(),
                    (pos.y - popupContentSize.height / 2f).roundToInt(),
                )
            }
        },
        properties = PopupProperties(focusable = false),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        val e = enter.value
                        val ec = e.coerceIn(0f, 1f)
                        val squash = (1f - ec) * 0.22f
                        val x = splat.value
                        scaleX = e * (1f - squash) * (1f + 0.55f * x)
                        scaleY = e * (1f + squash) * (1f - 0.5f * x)
                        alpha = ec * (1f - x)
                    }
                    .clip(CircleShape)
                    .background(Obsidian.overlay)
                    .border(1.dp, Obsidian.accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(if (d.isCategory) Lucide.Folder else if (voice) Lucide.Volume2 else Lucide.Hash, tint = Obsidian.accent, size = 20.dp)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                name,
                style = TextStyle(color = Obsidian.text1, fontSize = 11.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) * (1f - splat.value) }
                    .widthIn(max = 140.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Obsidian.raised)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun OrbitEntry(
    ch: ChannelDto,
    active: Boolean,
    unread: Boolean,
    unreadCount: Int,
    pessoaPorId: Map<String, ServerMemberDto>,
    voicePresence: Map<String, List<String>>,
    myId: String?,
    myVoiceChannelId: String?,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    dragCtx: ChannelDragCtx? = null,
    menu: ChannelMenu,
) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth()) {
        var confirmDelCh by remember(ch.id) { mutableStateOf(false) }
        EditorialContextMenu(entries = {
            buildList {
                if (unread) add(MenuEntry.Item("marcar como lido", icon = Lucide.Check) { menu.onMarkRead(ch.id) })
                val calada = menu.silenciada(ch.id)
                add(MenuEntry.Item(if (calada) "reativar órbita" else "silenciar órbita", icon = if (calada) Lucide.Bell else Lucide.BellOff) { menu.onToggleMute(ch.id) })
                add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(ch.id)) })
                if (menu.podeGerenciar) {
                    add(MenuEntry.Separator)
                    val temBot = menu.botAtende(ch)
                    add(
                        MenuEntry.Item(
                            if (temBot) "silenciar a bot aqui" else "deixar a bot atender aqui",
                            icon = if (temBot) Lucide.BotOff else Lucide.Bot,
                        ) { menu.onToggleBot(ch.id, !temBot) },
                    )
                    if (temBot) add(
                        MenuEntry.Item(
                            if (ch.botKeepReplies) "não guardar as respostas" else "guardar as respostas aqui",
                            icon = if (ch.botKeepReplies) Lucide.EyeOff else Lucide.Archive,
                        ) { menu.onToggleKeepBot(ch.id, !ch.botKeepReplies) },
                    )
                    add(MenuEntry.Item("renomear", icon = Lucide.Pencil) { menu.onRename(ch.id, ch.name) })
                    add(
                        MenuEntry.Item("quem vê esta órbita", icon = Lucide.Lock) {
                            menu.onOpenVisibility(ch.id, ch.name)
                        },
                    )
                    add(MenuEntry.Item("excluir órbita", danger = true, icon = Lucide.Trash2) { confirmDelCh = true })
                }
            }
        }) {
            OrbitItem(ch, active, unread, unreadCount, onOpenChat, onOpenVoice, dragCtx)
            if (confirmDelCh) ConfirmPopup(
                message = "excluir a órbita ${ch.name}? apaga as mensagens dela — não há como desfazer.",
                confirmLabel = "excluir",
                onConfirm = { menu.onDelete(ch.id) },
                onDismiss = { confirmDelCh = false },
            )
        }
        if (ch.type == "VOICE") {
            val ids = remember(voicePresence, ch.id, myVoiceChannelId, myId) {
                val base = voicePresence[ch.id].orEmpty()
                if (myVoiceChannelId == ch.id && myId != null && myId !in base) listOf(myId) + base else base
            }
            ids.forEach { uid ->
                val user = pessoaPorId[uid]?.user
                VoicePresenceRow(
                    avatarUrl = user?.avatarUrl,
                    name = user?.displayName ?: user?.username ?: "…",
                    isMe = uid == myId,
                )
            }
        }
    }
}

@Composable
private fun VoicePresenceRow(avatarUrl: String?, name: String, isMe: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 8.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopAvatar(avatarUrl, name, 20)
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = TextStyle(color = if (isMe) Obsidian.text2 else Obsidian.text3, fontSize = 12.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CategoryHeader(name: String, collapsed: Boolean, onToggle: () -> Unit, dragCtx: CategoryDragCtx? = null) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, tween(140))
    val tint = if (hovered) Obsidian.text2 else Obsidian.text3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(top = 10.dp, bottom = 2.dp)
            .categoryDrag(name, dragCtx)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(
            Lucide.ChevronDown,
            tint = tint,
            size = 13.dp,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = name.uppercase(),
            style = TextStyle(color = tint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

internal class QuemVeAOrbita(
    val ler: (String, String, (ChannelVisibilityDto?, String?) -> Unit) -> Unit,
    val cargos: (String, (List<RoleDto>?, String?) -> Unit) -> Unit,
    val salvar: (String, String, Boolean, List<String>, (String?) -> Unit) -> Unit,
)

private sealed interface ChanDialog {
    data class NewChannel(val serverId: String, val categoryId: String?) : ChanDialog
    data class NewCategory(val serverId: String) : ChanDialog
    data class RenameCategory(val serverId: String, val categoryId: String, val current: String) : ChanDialog
    data class RenameChannel(val serverId: String, val channelId: String, val current: String) : ChanDialog
    data class Visibilidade(val serverId: String, val channelId: String, val name: String) : ChanDialog
}

private class ChannelMenu(
    val podeGerenciar: Boolean,
    val silenciada: (channelId: String) -> Boolean,
    val onMarkRead: (channelId: String) -> Unit,
    val onRename: (channelId: String, current: String) -> Unit,
    val onOpenVisibility: (channelId: String, name: String) -> Unit,
    val onDelete: (channelId: String) -> Unit,
    val onToggleMute: (channelId: String) -> Unit,
    val botAtende: (ChannelDto) -> Boolean,
    val onToggleBot: (channelId: String, ligar: Boolean) -> Unit,
    val onToggleKeepBot: (channelId: String, guardar: Boolean) -> Unit,
)

private object CenterInWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
        y = ((windowSize.height - popupContentSize.height) / 2).coerceAtLeast(0),
    )
}

@Composable
private fun EditorialInputDialog(
    title: String,
    placeholder: String,
    initial: String,
    confirmLabel: String,
    channelType: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var type by remember { mutableStateOf("TEXT") }
    val valid = text.trim().isNotEmpty()
    Popup(
        popupPositionProvider = CenterInWindow,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val entered = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = entered,
            enter = fadeIn(tween(140)) + scaleIn(tween(160), initialScale = 0.96f),
        ) {
            Column(
                Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Obsidian.overlay)
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(14.dp))
                    .padding(18.dp),
            ) {
                Text(title, style = TextStyle(color = Obsidian.text1, fontSize = 16.sp, fontFamily = DmSerif))
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(50) },
                    singleLine = true,
                    textStyle = Tipo.corpo,
                    cursorBrush = SolidColor(Obsidian.accent),
                    decorationBox = { inner ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Obsidian.base)
                                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (channelType) {
                                LIcon(Lucide.Hash, tint = Obsidian.text3, size = 14.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Box(Modifier.weight(1f)) {
                                if (text.isEmpty()) {
                                    Text(placeholder, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
                                }
                                inner()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (channelType) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeChip("texto", Lucide.Hash, type == "TEXT") { type = "TEXT" }
                        TypeChip("voz", Lucide.Volume2, type == "VOICE") { type = "VOICE" }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    DialogButton(label = "cancelar", accent = false, enabled = true) { onDismiss() }
                    DialogButton(label = confirmLabel, accent = true, enabled = valid) {
                        onDismiss()
                        onConfirm(text.trim(), type)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeChip(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent, tween(120),
    )
    val border by animateColorAsState(
        if (active) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim, tween(120),
    )
    val fg = if (active) Obsidian.text1 else Obsidian.text3
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(icon, tint = fg, size = 14.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = TextStyle(color = fg, fontSize = 12.sp))
    }
}

@Composable
private fun DialogButton(label: String, accent: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fg = when {
        !enabled -> Obsidian.text3.copy(alpha = 0.5f)
        accent -> Obsidian.accent
        else -> Obsidian.text3
    }
    val border by animateColorAsState(
        when {
            !enabled -> Obsidian.borderDim.copy(alpha = 0.5f)
            accent -> Obsidian.accent.copy(alpha = if (hovered) 0.9f else 0.55f)
            hovered -> Obsidian.borderMid
            else -> Obsidian.borderDim
        },
        tween(120),
    )
    Text(
        label,
        style = TextStyle(color = fg, fontSize = 12.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .hoverable(interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun OrbitItem(
    ch: ChannelDto,
    active: Boolean,
    unread: Boolean,
    unreadCount: Int,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    dragCtx: ChannelDragCtx? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val isUnread = !active && unread
    val alvo = if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent
    val fundoAnimado by animateColorAsState(alvo, tween(120))
    val itemBg = if (hovered && !active) alvo else fundoAnimado
    val dSt = dragCtx?.state
    val lifted = dSt != null && dSt.dragging && dSt.id == ch.id
    Box(
        Modifier
            .fillMaxWidth()
            .channelDrag(ch, dragCtx)
            .graphicsLayer { alpha = if (lifted) 0.35f else 1f },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(itemBg)
                .hoverable(interaction)
                .clickable {
                    if (ch.type == "VOICE") onOpenVoice(ch)
                    else onOpenChat(ChatTarget.Channel(ch.id, ch.name))
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LIcon(
                if (ch.type == "VOICE") Lucide.Volume2 else Lucide.Hash,
                tint = if (ch.type == "VOICE") Obsidian.accent else Obsidian.text3,
                size = 15.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = ch.name,
                style = TextStyle(
                    color = if (active || hovered || isUnread) Obsidian.text1 else Obsidian.text2,
                    fontSize = 13.sp,
                    fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (ch.isPrivate) {
                Spacer(Modifier.width(6.dp))
                LIcon(Lucide.Lock, tint = Obsidian.text3, size = 11.dp, rotulo = "órbita privada")
            }
            if (!active && unreadCount > 0) {
                Spacer(Modifier.width(6.dp))
                UnreadCountBadge(unreadCount)
            }
        }
        if (isUnread) UnreadPill(Modifier.align(Alignment.CenterStart))
        if (dSt != null && dragCtx != null && dSt.dragging &&
            dSt.section == dragCtx.section && dSt.id != ch.id && dSt.targetIndex == dragCtx.index
        ) {
            Box(
                Modifier
                    .align(if (dSt.targetIndex > dSt.fromIndex) Alignment.BottomCenter else Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Obsidian.accent),
            )
        }
    }
}

@Composable
internal fun UnreadCountBadge(count: Int, destaque: Boolean = true) {
    Box(
        Modifier
            .height(18.dp)
            .widthIn(min = 18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (destaque) Color.White else Obsidian.raised)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = TextStyle(
                color = if (destaque) Color.Black else Obsidian.text2,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun UnreadPill(modifier: Modifier = Modifier) {
    val glow = if (LocalReduceMotion.current || !LocalWindowActive.current) null else {
        rememberInfiniteTransition(label = "unread").animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = EaseOutSoft),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }
    Box(
        modifier
            .width(3.dp)
            .height(16.dp)
            .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
            .graphicsLayer { alpha = glow?.value ?: 1f }
            .background(Obsidian.accent),
    )
}

@Composable
private fun DmList(
    dms: List<ConversationDto>,
    servers: List<ServerDto>,
    onToggleMute: (ConversationDto) -> Unit,
    onMarkRead: (String) -> Unit,
    onCloseDm: (String) -> Unit,
    activeChatId: String?,
    unread: Set<String>,
    dmTyping: Set<String>,
    dmPresence: Map<String, String>,
    onOpenChat: (ChatTarget) -> Unit,
) {
    if (dms.isEmpty()) {
        EmptyHint("nenhum sussurro ainda")
        return
    }
    val clipboard = LocalClipboardManager.current
    var profileFor by remember { mutableStateOf<String?>(null) }
    var inviteFor by remember { mutableStateOf<ConversationDto?>(null) }
    val friendApi = remember { GlobalContext.get().get<FriendApi>() }
    var friendships by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val blockApi = remember { GlobalContext.get().get<BlockApi>() }
    var blocked by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        friendships = runCatching { friendApi.friends().data.orEmpty() }
            .getOrDefault(emptyList())
            .associate { it.user.id to it.friendshipId }
        blocked = runCatching { blockApi.blocked().data.orEmpty() }
            .getOrDefault(emptyList()).map { it.id }.toSet()
    }
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) dms else dms.filter { c ->
        val n = c.otherUser?.displayName ?: c.otherUser?.username ?: ""
        n.contains(query.trim(), ignoreCase = true)
    }
    Column(Modifier.fillMaxSize()) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Obsidian.base)
                        .border(1.dp, Obsidian.borderMid.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LIcon(
                        Lucide.Search,
                        tint = if (query.isEmpty()) Obsidian.text3 else Obsidian.accent,
                        size = 13.dp,
                    )
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("encontrar conversa", style = Tipo.descricao)
                        }
                        inner()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        )
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("nada encontrado", style = Tipo.descricao)
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            itemsIndexed(filtered, key = { _, c -> c.id }) { cascadeRow, conv ->
            val u = conv.otherUser
            val name = u?.displayName ?: u?.username ?: "?"
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val active = conv.id == activeChatId
            val isUnread = !active && conv.id in unread
            val alvo = if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent
            val fundoAnimado by animateColorAsState(alvo, tween(120))
            val itemBg = if (hovered && !active) alvo else fundoAnimado
            CascadeIn(cascadeRow, Unit) {
            EditorialContextMenu(entries = {
                buildList {
                    u?.id?.let { uid ->
                        add(MenuEntry.Item("ver perfil", icon = Lucide.User) { profileFor = uid })
                    }
                    if (isUnread) add(MenuEntry.Item("marcar como lida", icon = Lucide.Check) { onMarkRead(conv.id) })
                    add(MenuEntry.Separator)
                    if (u?.username != null && servers.isNotEmpty()) {
                        add(MenuEntry.Item("convidar para constelação", icon = Lucide.Users) { inviteFor = conv })
                    }
                    add(
                        MenuEntry.Item(if (conv.muted) "desmutar sussurro" else "mutar sussurro", icon = if (conv.muted) Lucide.Bell else Lucide.BellOff) {
                            onToggleMute(conv)
                        },
                    )
                    u?.id?.let { uid -> add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(uid)) }) }
                    add(MenuEntry.Separator)
                    add(MenuEntry.Item("fechar sussurro", icon = Lucide.X) { onCloseDm(conv.id) })
                    friendships[u?.id]?.let { fid ->
                        add(MenuEntry.Separator)
                        add(MenuEntry.Item("desfazer amizade", danger = true, icon = Lucide.UserMinus) {
                            scope.launch {
                                runCatching { friendApi.remove(fid) }
                                friendships = friendships - (u?.id ?: "")
                            }
                        })
                    }
                    u?.id?.let { uid ->
                        val jaBloqueado = uid in blocked
                        if (!friendships.containsKey(uid)) add(MenuEntry.Separator)
                        add(
                            MenuEntry.Item(
                                if (jaBloqueado) "desbloquear" else "bloquear",
                                danger = !jaBloqueado,
                                icon = if (jaBloqueado) Lucide.UserCheck else Lucide.Ban,
                            ) {
                                scope.launch {
                                    if (jaBloqueado) {
                                        runCatching { blockApi.unblock(uid) }.onSuccess { blocked = blocked - uid }
                                    } else {
                                        runCatching { blockApi.block(uid) }.onSuccess {
                                            blocked = blocked + uid
                                            friendships = friendships - uid
                                            onCloseDm(conv.id)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(itemBg)
                        .hoverable(interaction)
                        .clickable { onOpenChat(ChatTarget.Dm(conv.id, name)) }
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        DesktopAvatar(u?.avatarUrl, name, 28)
                        StatusDot(
                            status = userStatus(dmPresence[u?.id]),
                            size = 10.dp,
                            bordered = true,
                            borderColor = Obsidian.base,
                            cutoutColor = Obsidian.base,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = TextStyle(
                                color = if (active || hovered || isUnread) Obsidian.text1 else Obsidian.text2,
                                fontSize = 13.sp,
                                fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                            ),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (conv.id in dmTyping) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TypingDots(Obsidian.accent, dotSize = 3.dp)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "digitando…",
                                    style = TextStyle(color = Obsidian.accent, fontSize = 11.sp),
                                )
                            }
                        } else {
                            val preview = conv.lastMessage?.content?.ifBlank { "anexo" }
                            if (preview != null) {
                                Text(
                                    text = preview,
                                    style = Tipo.apoio,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (isUnread) UnreadPill(Modifier.align(Alignment.CenterStart))
            }
            }
            }
            }
        }
    }

    profileFor?.let { uid ->
        ProfilePage(
            userId = uid,
            isMe = false,
            onStartDm = { _, _ -> profileFor = null },
            onClose = { profileFor = null },
        )
    }
    inviteFor?.let { conv ->
        val uname = conv.otherUser?.username
        if (uname == null) inviteFor = null
        else PickServerDialog(
            username = uname,
            servers = servers,
            onClose = { inviteFor = null },
        )
    }
}

@Composable
private fun PickServerDialog(username: String, servers: List<ServerDto>, onClose: () -> Unit) {
    val api = remember { GlobalContext.get().get<ServerApi>() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    DialogShell(onClose = onClose) {
        Text(
            "convidar @$username",
            style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "para qual constelação?",
            style = Tipo.apoio,
        )
        Spacer(Modifier.height(12.dp))
        servers.forEach { srv ->
            val loading = busy == srv.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = busy == null) {
                        busy = srv.id
                        msg = null
                        scope.launch {
                            val r = runCatching { api.addMember(srv.id, username) }
                            busy = null
                            msg = if (r.isSuccess) "entrou em ${srv.name}" to true
                            else "não deu — já é membro, ou você não tem permissão" to false
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopAvatar(srv.iconUrl, srv.name, 26)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (loading) "…" else srv.name,
                    style = Tipo.corpo,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        msg?.let { (text, ok) ->
            Spacer(Modifier.height(10.dp))
            Text(
                text,
                style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun BotaoDeLigar(icone: ImageVector, titulo: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        Modifier
            .size(28.dp)
            .clickScale(src, formaDoFoco = RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (hov) Obsidian.accentDim else Color.Transparent, RoundedCornerShape(8.dp))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icone, tint = if (hov) Obsidian.accent else Obsidian.text3, size = 15.dp, rotulo = titulo)
    }
}

@Composable
private fun Stage(
    server: ServerDto?,
    chat: ChatTarget?,
    voiceChannel: ChannelDto?,
    call: CallNaSala?,
    mudo: Boolean,
    aoAlternarMudo: () -> Unit,
    ensurdecido: Boolean,
    aoAlternarEnsurdecer: () -> Unit,
    voicePresence: List<String>,
    onJoinVoice: () -> Unit,
    onLeaveVoice: () -> Unit,
    onLigarSussurro: (ChatTarget.Dm, video: Boolean) -> Unit,
    botDoOutroLado: Boolean,
    fonteDoSussurro: String?,
    createChatVm: (ChatTarget) -> ChatVm,
    members: List<ServerMemberDto>,
    me: ProfileUserDto?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onStartDm: (String, String) -> Unit,
    showDiscover: Boolean,
    onDiscoverJoined: (String) -> Unit,
    joinedServerIds: Set<String> = emptySet(),
    showFriends: Boolean,
    leiturasAoEntrar: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val pulo = remember { PuloParaMensagem() }
    androidx.compose.runtime.CompositionLocalProvider(LocalPuloParaMensagem provides pulo) {
    Column(modifier.fillMaxHeight().panelSurface(Obsidian.raised, 0.52f)) {
        if (showFriends) {
            FriendsView(onStartDm, Modifier.fillMaxSize())
            return@Column
        }
        if (showDiscover) {
            DiscoverView(onDiscoverJoined, joinedIds = joinedServerIds, modifier = Modifier.fillMaxSize())
            return@Column
        }
        val bareLanding = chat == null && voiceChannel == null
        if (!bareLanding) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Obsidian.overlay.copy(alpha = 0.45f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val leadIcon = when {
                    voiceChannel != null -> Lucide.Volume2
                    chat is ChatTarget.Channel -> Lucide.Hash
                    else -> null
                }
                if (leadIcon != null) {
                    LIcon(leadIcon, tint = Obsidian.text1, size = 15.dp)
                    Spacer(Modifier.width(7.dp))
                }
                Box(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            voiceChannel != null -> AnnotatedString(voiceChannel.name)
                            chat is ChatTarget.Channel -> AnnotatedString(chat.title)
                            chat is ChatTarget.Dm -> buildAnnotatedString {
                                append("sussurro · ")
                                withStyle(SpanStyle(fontFamily = fonteDoSussurro?.let { profileFontFamily(it) })) {
                                    append(chat.title)
                                }
                            }
                            server != null -> AnnotatedString("constelação · ${server.name}")
                            else -> AnnotatedString("sussurros")
                        },
                        style = TextStyle(
                            color = if (chat != null || voiceChannel != null) Obsidian.text1 else Obsidian.text3,
                            fontSize = 13.sp,
                        ),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
                    )
                }
                if (chat is ChatTarget.Channel && voiceChannel == null) {
                    AlfineteDoCanal(chat.id)
                }
                if (chat is ChatTarget.Dm && voiceChannel == null && !botDoOutroLado) {
                    BotaoDeLigar(Lucide.Phone, "ligar") { onLigarSussurro(chat, false) }
                    Spacer(Modifier.width(4.dp))
                    BotaoDeLigar(Lucide.Video, "chamada de vídeo") { onLigarSussurro(chat, true) }
                }
            }
        }

        if (voiceChannel != null) {
            if (call != null) VoiceView(voiceChannel, members, me, call, mudo, aoAlternarMudo, ensurdecido, aoAlternarEnsurdecer, onLeaveVoice, server?.id)
            else VoiceLobby(voiceChannel, members, voicePresence, onJoinVoice)
            return@Column
        }

        TrocaDePagina(
            alvo = chat,
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            if (target != null) {
                key(target.id) {
                val chatVm = remember { createChatVm(target) }
                DisposableEffect(Unit) { onDispose { chatVm.dispose() } }
                val botAqui = remember(target.id, server) {
                    val ch = server?.channels?.find { it.id == target.id }
                    val cat = server?.categories?.find { it.id == ch?.categoryId }
                    ch?.botEnabled ?: cat?.botEnabled ?: true
                }
                ChatView(
                    target, chatVm, onStartDm,
                    botAqui = botAqui,
                    serverId = server?.id,
                    membros = if (target is ChatTarget.Channel) members else emptyList(),
                    lidoAte = leiturasAoEntrar[target.id],
                )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> ChatSkeleton()
                        error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error, style = TextStyle(color = Obsidian.danger, fontSize = 13.sp))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "tentar de novo",
                                style = TextStyle(color = Obsidian.accent, fontSize = 13.sp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                                    .clickable(onClick = onRetry)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        else -> EmptyStage(isServer = server != null)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun MembersPanel(
    members: List<ServerMemberDto>,
    presence: Map<String, String>,
    atividade: Map<String, String>,
    myId: String?,
    serverId: String?,
    isOwner: Boolean,
    onStartDm: (String, String) -> Unit,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
) {
    val rows = remember(members, presence, myId) { buildMemberRows(members, presence, myId) }
    val forma = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    Column(
        Modifier
            .width(240.dp)
            .fillMaxHeight()
            .clip(forma)
            .panelSurface(Obsidian.base, 0.62f)
            .border(1.dp, Obsidian.borderMid.copy(alpha = 0.5f), forma),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            items(rows, key = { row -> row.key }) { row ->
                when (row) {
                    is MemberPanelRow.Header -> MemberSectionHeader(row.label, row.count, row.iconUrl)
                    is MemberPanelRow.Person -> MemberRow(
                        m = row.m,
                        online = row.online,
                        atividade = atividade[row.m.userId],
                        cascadeIndex = row.cascadeIndex,
                        cascadeTotal = members.size,
                        isMe = row.m.userId == myId,
                        serverId = serverId,
                        isOwner = isOwner,
                        onStartDm = onStartDm,
                        onKick = onKick,
                        onBan = onBan,
                    )
                }
            }
        }
    }
}

private sealed interface MemberPanelRow {
    val key: String
    data class Header(val id: String, val label: String, val count: Int, val iconUrl: String?) : MemberPanelRow {
        override val key get() = "h:$id"
    }
    data class Person(val m: ServerMemberDto, val online: Boolean, val cascadeIndex: Int) : MemberPanelRow {
        override val key get() = "m:${m.userId}"
    }
}

private fun buildMemberRows(members: List<ServerMemberDto>, presence: Map<String, String>, myId: String?): List<MemberPanelRow> {
    fun online(uid: String) = uid == myId || presence[uid]?.let { it != "OFFLINE" } == true
    val chave = HashMap<String, String>(members.size)
    for (m in members) chave[m.userId] = (m.user.displayName ?: m.user.username).lowercase()
    fun nameOf(m: ServerMemberDto) = chave[m.userId].orEmpty()

    val roleById = HashMap<String, MemberRoleDto>()
    val buckets = LinkedHashMap<String, MutableList<ServerMemberDto>>()
    for (m in members) {
        val r = m.roles.filter { it.hoist }.maxByOrNull { it.position }
        val key = r?.id ?: ""
        if (r != null) roleById[key] = r
        buckets.getOrPut(key) { mutableListOf() }.add(m)
    }
    val order = buckets.keys.sortedByDescending { roleById[it]?.position ?: Int.MIN_VALUE }

    val out = ArrayList<MemberPanelRow>()
    var idx = 0
    for (key in order) {
        val role = roleById[key]
        val list = buckets[key] ?: continue
        val on = list.filter { online(it.userId) }.sortedBy { nameOf(it) }
        val off = list.filter { !online(it.userId) }.sortedBy { nameOf(it) }
        out.add(MemberPanelRow.Header(key.ifEmpty { "members" }, role?.name?.uppercase() ?: "MEMBROS", list.size, role?.iconUrl))
        for (m in on + off) out.add(MemberPanelRow.Person(m, online(m.userId), idx++))
    }
    return out
}

@Composable
private fun MemberSectionHeader(label: String, count: Int, iconUrl: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!iconUrl.isNullOrBlank()) {
            Box(Modifier.size(15.dp).clip(CircleShape).background(Obsidian.overlay)) {
                AstraImage(iconUrl, label, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = "$label — $count",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, letterSpacing = 0.6.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MemberRow(
    m: ServerMemberDto,
    online: Boolean,
    atividade: String?,
    cascadeIndex: Int,
    cascadeTotal: Int,
    isMe: Boolean,
    serverId: String?,
    isOwner: Boolean,
    onStartDm: (String, String) -> Unit,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val name = m.user.displayName ?: m.user.username
    val corDoNome = if (online) corDoMembro(m) else null
    val padraoDoNome = if (online) Obsidian.text2 else Obsidian.text3.copy(alpha = 0.65f)
    val avatarAlpha = if (online) 1f else 0.4f
    CascadeIn(cascadeIndex, cascadeTotal) {
        var confirmMember by remember(m.userId) { mutableStateOf<String?>(null) }
        EditorialContextMenu(entries = {
            buildList {
                if (!isMe) add(MenuEntry.Item("sussurro", icon = Lucide.MessageCircle) { onStartDm(m.user.username, name) })
                add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(m.userId)) })
                if (isOwner && !isMe && serverId != null) {
                    add(MenuEntry.Separator)
                    add(MenuEntry.Item("expulsar", danger = true, icon = Lucide.UserMinus) { confirmMember = "kick" })
                    add(MenuEntry.Item("banir", danger = true, icon = Lucide.Ban) { confirmMember = "ban" })
                }
            }
        }) {
            confirmMember?.let { act ->
                ConfirmPopup(
                    message = if (act == "ban") "banir ${name}? a pessoa não poderá voltar." else "expulsar ${name}?",
                    confirmLabel = if (act == "ban") "banir" else "expulsar",
                    onConfirm = { if (act == "ban") onBan(m.userId) else onKick(m.id) },
                    onDismiss = { confirmMember = null },
                )
            }
            ProfileAnchor(m.userId, isMe = isMe, onStartDm = onStartDm, cargos = m.roles) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.graphicsLayer { alpha = avatarAlpha }) {
                        DesktopAvatar(m.user.avatarUrl, name, 26)
                    }
                    Spacer(Modifier.width(9.dp))
                    Column {
                        NomeColorido(
                            texto = name,
                            cor = corDoNome,
                            padrao = padraoDoNome,
                            fontSize = 13.sp,
                            fontFamily = m.user.displayFont?.let { profileFontFamily(it) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (atividade != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(Obsidian.accent.copy(alpha = if (online) 0.85f else 0.4f)),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = atividade,
                                    style = Tipo.apoio,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun memberRoleColor(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}

internal fun corDoMembro(m: ServerMemberDto): CorDoNome? =
    lerCorDoNome(m.topColor) ?: lerCorDoNome(m.nameColor)

internal fun coresDeCargo(membros: List<ServerMemberDto>): Map<String, CorDoNome> =
    membros.mapNotNull { m -> corDoMembro(m)?.let { m.userId to it } }.toMap()

@Composable
private fun FriendsNavRow(active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent,
        tween(120),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(Lucide.Users, tint = if (active) Obsidian.accent else Obsidian.text3, size = 16.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            "Amigos",
            style = TextStyle(
                color = if (active || hovered) Obsidian.text1 else Obsidian.text2,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}
