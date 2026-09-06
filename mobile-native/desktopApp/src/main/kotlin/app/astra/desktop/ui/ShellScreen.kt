package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.astra.desktop.AvisosNaTela
import app.astra.desktop.ModoTransmissao
import app.astra.desktop.VozNaBandeja
import app.astra.desktop.auth.Session
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.prefs.AvisosDaConta
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.TemaDaConta
import app.astra.desktop.shell.CacheDeConversas
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.ChatVm
import app.astra.desktop.shell.Selection
import app.astra.desktop.shell.ShellVm
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.voice.LeituraDoCaminho
import app.astra.desktop.voice.Sfx
import app.astra.desktop.voice.VoiceSession
import app.astra.desktop.xp.MissoesStore
import app.astra.desktop.xp.XpStore
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.InviteApi
import app.astra.mobile.core.network.NotificationApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ChannelActivityEventDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.ServerDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.GlobalContext

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

    LaunchedEffect(Unit) {
        val missoes = koin.get<MissoesStore>()
        missoes.iniciar(scope)
        missoes.recarregar()
    }

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
        val shellVisivel = animateFloatAsState(
            if (shellCoberto) 0f else 1f,
            tween(160),
            label = "shellVisivel",
        )
        CompositionLocalProvider(
            LocalReduceMotion provides (prefState.reduceMotionEff || emSegundoPlano || shellCoberto),
        ) {
        Box(
            Modifier
                .graphicsLayer { alpha = shellVisivel.value }
                .drawWithContent { if (shellVisivel.value > 0.001f) drawContent() },
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

internal val LARGURA_RAIL = 72.dp
internal val LARGURA_SIDEBAR = 260.dp
private val RESPIRO_DA_JANELA = 10.dp
private val FORMA_DO_SHELL = RoundedCornerShape(10.dp)
private val ALTURA_DO_RODAPE = 62.dp

internal fun Modifier.panelSurface(bg: Color, alpha: Float): Modifier =
    this.background(bg.copy(alpha = alpha))

