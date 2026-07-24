package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import app.astra.desktop.auth.Session
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.voice.VoiceEngine
import app.astra.desktop.voice.VoiceSession
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.ChatVm
import app.astra.desktop.shell.Selection
import app.astra.desktop.shell.ShellVm
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.Obsidian
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.Volume2
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.NotificationApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ChannelActivityEventDto
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext

// Shell desktop (fatia 2): rail 72 | sidebar 260 | palco | membros 240.
// Compacto (decisao do dono); chat de verdade e a proxima fatia.
@Composable
fun ShellScreen(
    session: Session,
    windowHidden: () -> Boolean,
    notify: (String, String) -> Unit,
    onLogout: () -> Unit,
    searchOpen: Boolean = false,
    onCloseSearch: () -> Unit = {},
    notifOpen: Boolean = false,
    onCloseNotif: () -> Unit = {},
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
            koin.get<NotificationApi>(), koin.get<SessionStore>(), socket, koin.get<Json>(), session.userId,
        )
    }
    val state by vm.state.collectAsState()
    // Call VIVA acima da navegacao: o engine mora aqui, nao dentro da VoiceView.
    // Trocar de orbita nao desconecta mais — so desligar (ou entrar noutra sala).
    val voice = remember { VoiceSession(scope, koin) }
    DisposableEffect(Unit) { onDispose { voice.leave() } }
    var settingsOpen by remember { mutableStateOf(false) }
    // Aba em que o takeover abre: a engrenagem cai em Conta, o avatar do rodape
    // cai em Perfil.
    var settingsTab by remember { mutableStateOf(SettingsTab.ACCOUNT) }
    // Configuracoes da CONSTELACAO (outro takeover): sempre a selecionada.
    var serverSettingsOpen by remember { mutableStateOf(false) }
    // Ctrl+K = quick-switcher (pular pra canal/sussurro). Foco na raiz garante que o
    // atalho dispara mesmo sem nada clicado; onPreviewKeyEvent na raiz ve a tecla
    // antes de qualquer campo de texto filho.
    var paletteOpen by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    LaunchedEffect(Unit) { socket.connect() }

    // Badge do sino: poll da contagem de nao-lidas a cada 30s; qualquer leitura no
    // painel bumpa o tick -> refetch imediato. Barato (uma linha no backend).
    var notifRefresh by remember { mutableStateOf(0) }
    LaunchedEffect(notifRefresh) {
        while (true) {
            val c = runCatching { koin.get<NotificationApi>().unread().data?.count }.getOrNull() ?: 0
            onNotifUnread(c)
            delay(30_000)
        }
    }

    // Toast na bandeja quando chega mensagem com a janela fechada/minimizada.
    // DM tem autor+conteudo (salas todas joinadas); canal so tem o id do
    // channel_activity -> notificacao generica com o nome da orbita.
    val json = remember { koin.get<Json>() }
    LaunchedEffect(Unit) {
        launch {
            socket.newDm.collect { raw ->
                if (!windowHidden() || !prefs.state.value.notifyDms) return@collect
                val msg = runCatching { json.decodeFromString<DmMessageDto>(raw) }.getOrNull() ?: return@collect
                if (msg.senderId == session.userId) return@collect
                if (vm.state.value.dms.any { it.id == msg.conversationId && it.muted }) return@collect
                val name = msg.author?.displayName ?: msg.author?.username ?: "alguem"
                notify(name, msg.content.ifBlank { "anexo" }.take(120))
            }
        }
        launch {
            socket.channelActivity.collect { raw ->
                if (!windowHidden() || !prefs.state.value.notifyChannels) return@collect
                val ev = runCatching { json.decodeFromString<ChannelActivityEventDto>(raw) }.getOrNull() ?: return@collect
                val ch = vm.state.value.servers.flatMap { it.channels }.find { it.id == ev.channelId } ?: return@collect
                notify("#${ch.name}", "nova mensagem")
            }
        }
    }

    // ChatVm nasce DENTRO da pagina do AnimatedContent do palco: a conversa
    // antiga continua renderizando durante o fade e o dispose so roda quando a
    // pagina sai da composicao de vez.
    val chat = state.chat
    val createChatVm = remember {
        { target: ChatTarget ->
            ChatVm(
                scope, target,
                koin.get<ChannelApi>(), koin.get<DmApi>(), koin.get<UploadApi>(),
                socket, koin.get<Json>(), session.userId,
                myProfile = { vm.state.value.me },
            )
        }
    }

    // Desempenho (Settings): reduzir movimento + prefs de render descem por
    // CompositionLocal. auroraOn/starsOn/reduceMotionEff ja aplicam o modo
    // desempenho (kill-switch) por cima dos toggles individuais.
    CompositionLocalProvider(
        LocalReduceMotion provides prefState.reduceMotionEff,
        LocalRenderPrefs provides RenderPrefs(prefState.auroraQuality.octaves, prefState.uiFps.cap),
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
        // Aurora e estrelas NAO moram mais aqui: subiram pra janela (Main.kt), atras
        // do login e do shell ao mesmo tempo. Sem isso a entrada saltava — a aurora
        // do login ocupava 45% da largura e a do shell 100%, e o uv do shader e
        // normalizado pelo tamanho, entao eram duas imagens diferentes. Uma so
        // instancia tambem significa um shader em vez de dois durante a transicao.
        // Paineis = cartoes flutuantes (estilo mobile): gap entre eles + cantos
        // arredondados deixam a aurora respirar nas juntas (impressao de
        // sobreposicao). Margem externa de 8dp separa do titulo/bordas da janela.
        // Escondidos enquanto o Settings (takeover) esta aberto: assim a UNICA aurora
        // do shell (montada acima) fica continua por baixo do Settings — sem aurora
        // nova, sem salto de posicao ao trocar de aba. Crossfade rapido.
        AnimatedVisibility(
            visible = !settingsOpen && !serverSettingsOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
        Row(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        Rail(
            servers = state.servers,
            selection = state.selection,
            myId = session.userId,
            mutedServers = state.mutedServers,
            canManageSelected = { id ->
                (state.selection as? Selection.Server)?.id == id &&
                    state.myPerms?.let { it.isOwner || it.isAdmin || "MANAGE_SERVER" in it.permissions } == true
            },
            onOpenServerSettings = { id ->
                // Selecionar antes de abrir: a tela le a constelacao selecionada, e
                // assim tambem chega o myPerms dela.
                vm.select(Selection.Server(id))
                serverSettingsOpen = true
            },
            onSelect = vm::select,
            onLeaveServer = vm::leaveServer,
            onDeleteServer = vm::deleteServer,
            onCreateServer = vm::createServer,
            onToggleServerMute = vm::toggleServerMute,
            onMarkServerRead = vm::markServerRead,
        )
        Sidebar(
            selection = state.selection,
            servers = state.servers,
            dms = state.dms,
            // Ids sao unicos: o "ativo" da sidebar cobre chat de texto OU sala de voz.
            activeChatId = chat?.id ?: state.voiceChannel?.id,
            unread = state.unread,
            dmTyping = state.dmTyping,
            me = state.me,
            meFallback = session.displayName,
            loading = state.loading,
            members = state.members,
            voicePresence = state.voicePresence,
            myId = session.userId,
            myVoiceChannelId = state.voiceChannel?.id,
            onOpenChat = vm::openChat,
            onOpenVoice = vm::openVoice,
            onToggleMute = vm::toggleDmMute,
            onMarkRead = vm::markDmRead,
            onEditedProfile = vm::refreshMe,
            onOpenSettings = { t -> settingsTab = t; settingsOpen = true },
            onLogout = onLogout,
            friendsOpen = state.friendsOpen,
            onOpenFriends = vm::openFriends,
            onCreateChannel = vm::createChannel,
            onCreateCategory = vm::createCategory,
            onRenameCategory = vm::renameCategory,
            onDeleteCategory = vm::deleteCategory,
            onReorderChannels = vm::reorderChannel,
            onRenameChannel = vm::renameChannel,
            onDeleteChannel = vm::deleteChannel,
            onMarkChannelRead = vm::markChannelRead,
            mutedChannels = state.mutedChannels,
            onToggleChannelMute = vm::toggleChannelMute,
        )
        Stage(
            state.selectedServer,
            chat = chat,
            voiceChannel = state.voiceChannel,
            voiceEngine = voice.engineFor(state.voiceChannel),
            voicePresence = state.voiceChannel?.let { state.voicePresence[it.id] }.orEmpty(),
            onJoinVoice = { state.voiceChannel?.let(voice::join) },
            // Desligar = sair de verdade e limpar o palco.
            onLeaveVoice = { voice.leave(); vm.leaveVoice() },
            createChatVm = createChatVm,
            members = state.members,
            me = state.me,
            membersOpen = state.membersOpen,
            onToggleMembers = vm::toggleMembers,
            loading = state.loading,
            error = state.error,
            onRetry = vm::load,
            onStartDm = vm::startDm,
            showDiscover = state.selection is Selection.Discover,
            onDiscoverJoined = vm::refreshServersAndSelect,
            showFriends = state.selection is Selection.Dms && state.friendsOpen,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = state.selection is Selection.Server && state.membersOpen,
            enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
            exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120)),
        ) {
            MembersPanel(
                members = state.members,
                myId = session.userId,
                serverId = state.selectedServer?.id,
                isOwner = state.selectedServer?.ownerId == session.userId,
                onStartDm = vm::startDm,
                onKick = { uid -> state.selectedServer?.id?.let { vm.kickMember(it, uid) } },
                onBan = { uid -> state.selectedServer?.id?.let { vm.banMember(it, uid) } },
            )
        }
        }
        }

        // Card flutuante da call: so quando voce esta conectado E saiu da sala no
        // palco. Fica por cima de tudo (inclusive das configuracoes) — e o unico
        // lugar com o botao de desligar depois que navegar deixou de desconectar.
        val joined = voice.joined
        val joinedEngine = voice.engine
        if (joined != null && joinedEngine != null && state.voiceChannel?.id != joined.id) {
            CallDock(
                channel = joined,
                engine = joinedEngine,
                onExpand = { vm.openVoice(joined) },
                onLeave = { voice.leave(); vm.leaveVoice() },
            )
        }

        // Configuracoes da constelacao: mesma entrada do Settings (decisao do dono),
        // pra as duas telas de configuracao se comportarem igual. Sai sozinha se a
        // constelacao deixar de existir (excluir/sair fecham pelo proprio estado).
        val cfgServer = state.selectedServer
        AnimatedVisibility(
            visible = serverSettingsOpen && cfgServer != null,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.98f),
        ) {
            // Guarda o ultimo servidor valido: durante o fade de saida o selectedServer
            // ja pode ser null (ex.: acabou de excluir) e a tela nao pode piscar vazia.
            val shown = remember(cfgServer) { cfgServer }
            shown?.let { srv ->
                ServerSettingsScreen(
                    server = srv,
                    isOwner = srv.ownerId == session.userId,
                    members = state.members,
                    // isAdmin (cargo legado) concede o conjunto que o backend trata
                    // como de admin; senao, so as permissoes granulares dos cargos.
                    myPermissions = state.myPerms?.permissions.orEmpty().toSet(),
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

        // Settings em takeover (Discord): a MESMA aurora do shell segue viva por baixo
        // (o conteudo acima esconde-se no crossfade). Entra/sai com fade + leve zoom.
        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.98f),
        ) {
            SettingsScreen(
                me = state.me,
                prefs = prefs,
                initialTab = settingsTab,
                onClose = { settingsOpen = false },
                // Salvou o perfil -> re-hidrata o `me` do shell (rodape, chat e a
                // propria previa passam a ler o valor novo).
                onProfileSaved = { vm.refreshMe() },
            )
        }

        // Ctrl+K: quick-switcher em takeover (fade + leve zoom, como o settings).
        AnimatedVisibility(
            visible = paletteOpen,
            enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.97f),
            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.97f),
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

        // Busca-A (lupa no titlebar): palette dedicada com abas. Fade+zoom sutil.
        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.97f),
            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.97f),
        ) {
            SearchOverlay(
                currentServerId = state.selectedServer?.id,
                onClose = onCloseSearch,
                onOpenChannel = { sid, cid, name ->
                    vm.select(Selection.Server(sid))
                    vm.openChat(ChatTarget.Channel(cid, name))
                },
                onWhisper = { username, title -> vm.startDm(username, title) },
            )
        }

        // Notificacoes-A (sino no titlebar): dropdown topo-direita. Fade+zoom sutil.
        AnimatedVisibility(
            visible = notifOpen,
            enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.98f),
            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.98f),
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
    }
    }
}

// Cartao translucido do shell (estilo mobile): cantos arredondados + fundo baixo
// (a aurora vaza por baixo) + borda fina. O gap entre cartoes na Row vira a
// "linha arredondada" que da a sensacao de sobreposicao.
private fun Modifier.panelCard(bg: Color, alpha: Float): Modifier {
    val shape = RoundedCornerShape(14.dp)
    return this
        .clip(shape)
        .background(bg.copy(alpha = alpha))
        .border(1.dp, Obsidian.borderMid.copy(alpha = 0.5f), shape)
}

// Confirmacao "Tem certeza?" reusavel: popup obsidiana no ponto, acao em danger.
// Usada por todo delete/sair (canal, categoria, constelacao, expulsar, banir,
// logout). O chamador guarda um Boolean e renderiza isto quando true.
@Composable
fun ConfirmPopup(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = "cancelar",
) {
    Popup(
        onDismissRequest = onDismiss,
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
                message,
                style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                modifier = Modifier.widthIn(max = 240.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    cancelLabel,
                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    confirmLabel,
                    style = TextStyle(color = Obsidian.danger, fontSize = 12.sp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .border(1.dp, Obsidian.danger, RoundedCornerShape(7.dp))
                        .clickable { onDismiss(); onConfirm() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ---- Ctrl+K quick-switcher: pular pra qualquer canal/sussurro pelo teclado ----
private data class QuickResult(
    val kind: String, // "channel" | "dm"
    val id: String,
    val title: String,
    val subtitle: String, // nome da constelacao (canal) ou "sussurro" (dm)
    val voice: Boolean,
    val serverId: String?, // navegacao do canal
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
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 96.dp)
                .width(520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
                // Clique no painel nao fecha (so o scrim fecha).
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                // Setas/Enter/Esc: o preview do painel ve a tecla antes do campo (que
                // fica focado), entao navega a lista; letras caem no campo (retorna false).
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
                                "pular pra um canal ou sussurro…",
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
                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
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
            style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(r.subtitle, style = TextStyle(color = Obsidian.text3, fontSize = 11.sp), maxLines = 1)
    }
}

// ---- Rail de constelacoes (72dp) ----

@Composable
private fun Rail(
    servers: List<ServerDto>,
    selection: Selection,
    myId: String?,
    mutedServers: Set<String>,
    // "posso gerenciar esta constelacao?" — so responde true pra SELECIONADA (ver
    // o comentario no menu abaixo).
    canManageSelected: (String) -> Boolean,
    onOpenServerSettings: (String) -> Unit,
    onSelect: (Selection) -> Unit,
    onLeaveServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onCreateServer: (name: String, isGroup: Boolean) -> Unit,
    onToggleServerMute: (String) -> Unit,
    onMarkServerRead: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(
        // Cartao translucido: a aurora vaza por baixo (estilo mobile).
        modifier = Modifier.width(72.dp).fillMaxHeight().panelCard(Obsidian.void, 0.34f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        RailItem(
            active = selection is Selection.Dms,
            onClick = { onSelect(Selection.Dms) },
        ) {
            Text("✦", style = TextStyle(color = Obsidian.accent, fontSize = 20.sp))
        }
        Spacer(Modifier.height(8.dp))
        HairRule()
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(servers, key = { it.id }) { srv ->
                // Botao direito na constelacao: dono exclui (apaga pra todos); membro
                // sai. Ambos com confirmacao (F4).
                var confirmLeave by remember(srv.id) { mutableStateOf(false) }
                var confirmDelete by remember(srv.id) { mutableStateOf(false) }
                val isOwner = srv.ownerId == myId
                EditorialContextMenu(entries = {
                    buildList {
                        srv.inviteCode?.let { code ->
                            add(MenuEntry.Item("copiar convite") { clipboard.setText(AnnotatedString(code)) })
                        }
                        add(MenuEntry.Item(if (srv.id in mutedServers) "reativar constelacao" else "silenciar constelacao") { onToggleServerMute(srv.id) })
                        add(MenuEntry.Item("marcar tudo como lido") { onMarkServerRead(srv.id) })
                        add(MenuEntry.Item("copiar ID") { clipboard.setText(AnnotatedString(srv.id)) })
                        // "configuracoes" so pra quem manda: dono (o app ja sabe pelo
                        // ownerId) ou MANAGE_SERVER — este ultimo so e conhecido na
                        // constelacao SELECIONADA, porque as permissoes sao buscadas
                        // uma vez por selecao (buscar de todas seria N requisicoes no
                        // boot). Clicar seleciona antes de abrir, entao a tela sempre
                        // abre com as permissoes certas em mao.
                        if (isOwner || canManageSelected(srv.id)) {
                            add(MenuEntry.Separator)
                            add(MenuEntry.Item("configuracoes") { onOpenServerSettings(srv.id) })
                        }
                        add(MenuEntry.Separator)
                        if (isOwner) add(MenuEntry.Item("excluir constelacao", danger = true) { confirmDelete = true })
                        else add(MenuEntry.Item("sair da constelacao", danger = true) { confirmLeave = true })
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
                                style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "ficar",
                                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp))
                                        .clickable { confirmLeave = false }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                                Text(
                                    "sair",
                                    style = TextStyle(color = Obsidian.danger, fontSize = 12.sp),
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
                                "excluir ${srv.name}? apaga a constelacao pra todos — nao da pra desfazer.",
                                style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                                modifier = Modifier.widthIn(max = 240.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "cancelar",
                                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(7.dp))
                                        .clickable { confirmDelete = false }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                                Text(
                                    "excluir",
                                    style = TextStyle(color = Obsidian.danger, fontSize = 12.sp),
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
                ) {
                    if (!srv.iconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = srv.iconUrl,
                            contentDescription = srv.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
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
            // "+" colado logo abaixo do ultimo servidor (rola junto com a lista,
            // nao fica preso no rodape da rail).
            item(key = "create-server") { CreateServerButton(onCreateServer) }
        }
        // Bussola (Descobrir) fixada no rodape da rail — padrao Discord.
        Spacer(Modifier.height(8.dp))
        HairRule()
        Spacer(Modifier.height(8.dp))
        RailItem(
            active = selection is Selection.Discover,
            onClick = { onSelect(Selection.Discover) },
        ) {
            LIcon(Lucide.Compass, tint = Obsidian.accent, size = 20.dp)
        }
        Spacer(Modifier.height(10.dp))
    }
}

// "+" da rail: abre um mini-menu (constelacao / grupo) e, ao escolher, um dialogo
// de nome. Reaproveita o EditorialInputDialog (mesmo do criar canal).
@Composable
private fun CreateServerButton(onCreateServer: (name: String, isGroup: Boolean) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    // null = fechado; false = constelacao; true = grupo.
    var kind by remember { mutableStateOf<Boolean?>(null) }
    Box {
        RailItem(active = false, onClick = { menuOpen = true }) {
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
                        // max obrigatorio: sem ele o fillMaxWidth das linhas estica o
                        // card pra largura da janela inteira (o Popup da constraint cheia).
                        .widthIn(min = 170.dp, max = 230.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                ) {
                    CreateMenuRow(glyph = "✦", label = "criar constelação") { menuOpen = false; kind = false }
                    CreateMenuRow(icon = Lucide.Users, label = "criar grupo") { menuOpen = false; kind = true }
                }
            }
        }
    }
    kind?.let { g ->
        EditorialInputDialog(
            title = if (g) "novo grupo" else "nova constelação",
            placeholder = if (g) "nome do grupo" else "nome da constelação",
            initial = "",
            confirmLabel = "criar",
            channelType = false,
            onDismiss = { kind = null },
            onConfirm = { name, _ -> onCreateServer(name, g) },
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

// Menu do "+" aparece a DIREITA do botao da rail (a rail e estreita, na borda esq).
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
private fun RailItem(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Pill morph: circulo -> quadrado arredondado no hover/ativo (assinatura da
    // rail). Canto, fundo e borda transicionam (polish).
    val corner by animateDpAsState(if (active || hovered) 14.dp else 22.dp, tween(140))
    val shape = RoundedCornerShape(corner)
    val bg by animateColorAsState(if (active) Obsidian.overlay else Obsidian.raised, tween(140))
    val borderColor by animateColorAsState(
        if (active) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim,
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
    ) { content() }
}

// ---- Sidebar (260dp): orbitas da constelacao OU sussurros + painel do user ----

@Composable
private fun Sidebar(
    selection: Selection,
    servers: List<ServerDto>,
    dms: List<ConversationDto>,
    activeChatId: String?,
    unread: Set<String>,
    dmTyping: Set<String>,
    me: ProfileUserDto?,
    meFallback: String,
    loading: Boolean,
    members: List<ServerMemberDto>,
    voicePresence: Map<String, List<String>>,
    myId: String?,
    myVoiceChannelId: String?,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    onToggleMute: (ConversationDto) -> Unit,
    onMarkRead: (String) -> Unit,
    onEditedProfile: () -> Unit,
    onOpenSettings: (SettingsTab) -> Unit,
    onLogout: () -> Unit,
    friendsOpen: Boolean,
    onOpenFriends: () -> Unit,
    onCreateChannel: (serverId: String, name: String, type: String, categoryId: String?) -> Unit,
    onCreateCategory: (serverId: String, name: String) -> Unit,
    onRenameCategory: (serverId: String, categoryId: String, name: String) -> Unit,
    onDeleteCategory: (serverId: String, categoryId: String) -> Unit,
    onReorderChannels: (serverId: String, orderedIds: List<String>) -> Unit,
    onRenameChannel: (serverId: String, channelId: String, name: String) -> Unit,
    onDeleteChannel: (serverId: String, channelId: String) -> Unit,
    onMarkChannelRead: (channelId: String) -> Unit,
    mutedChannels: Set<String>,
    onToggleChannelMute: (channelId: String) -> Unit,
) {
    // Dialogo de nome (nova orbita / nova categoria / renomear) — centralizado na
    // janela. So o dono da constelacao dispara pelos menus de botao direito.
    var chanDialog by remember { mutableStateOf<ChanDialog?>(null) }
    Column(Modifier.width(260.dp).fillMaxHeight().panelCard(Obsidian.raised, 0.20f)) {
        // Transicao ao trocar na rail (sussurros <-> constelacao): header + lista
        // viram uma "pagina" que desliza de leve e faz fade. A pagina que sai
        // resolve o servidor pela PROPRIA selecao antiga (por isso a lista
        // inteira de servers entra aqui, nao so o selecionado).
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
                // Header
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
                // Botao direito no cabecalho da constelacao (so o dono): criar orbita
                // solta ou uma categoria nova.
                if (srv != null) {
                    val isOwnerHere = srv.ownerId == myId
                    EditorialContextMenu(entries = {
                        buildList {
                            add(MenuEntry.Item("marcar tudo como lido") {
                                srv.channels.forEach { if (it.id in unread) onMarkChannelRead(it.id) }
                            })
                            if (isOwnerHere) {
                                add(MenuEntry.Separator)
                                add(MenuEntry.Item("criar órbita") { chanDialog = ChanDialog.NewChannel(srv.id, null) })
                                add(MenuEntry.Item("criar categoria") { chanDialog = ChanDialog.NewCategory(srv.id) })
                            }
                        }
                    }) { header() }
                } else {
                    header()
                }
                HairRule()

                Box(Modifier.weight(1f)) {
                    when {
                        loading -> SidebarSkeleton()
                        sel is Selection.Dms -> Column(Modifier.fillMaxSize()) {
                            FriendsNavRow(active = friendsOpen, onClick = onOpenFriends)
                            DmList(dms, onToggleMute, onMarkRead, activeChatId, unread, dmTyping, onOpenChat)
                        }
                        sel is Selection.Discover ->
                            Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "busque e entre em constelacoes publicas no palco ao lado.",
                                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp, lineHeight = 17.sp),
                                )
                            }
                        else -> OrbitList(
                            srv, activeChatId, unread, members, voicePresence, myId, myVoiceChannelId,
                            onOpenChat, onOpenVoice,
                            onNewChannelInCat = { catId -> srv?.let { chanDialog = ChanDialog.NewChannel(it.id, catId) } },
                            onRenameCat = { catId, cur -> srv?.let { chanDialog = ChanDialog.RenameCategory(it.id, catId, cur) } },
                            onDeleteCat = { catId -> srv?.let { onDeleteCategory(it.id, catId) } },
                            onReorderChannels = { ids -> srv?.let { onReorderChannels(it.id, ids) } },
                            onOpenChannelRename = { cid, cur -> srv?.let { chanDialog = ChanDialog.RenameChannel(it.id, cid, cur) } },
                            onOpenChannelDelete = { cid, name -> srv?.let { chanDialog = ChanDialog.DeleteChannel(it.id, cid, name) } },
                            onMarkChannelRead = onMarkChannelRead,
                            mutedChannels = mutedChannels,
                            onToggleChannelMute = onToggleChannelMute,
                        )
                    }
                }
            }
        }

        // Rodape do usuario: cartao flutuante estilo Discord (bordas arredondadas
        // sobre a aurora). A propria borda do cartao ja separa da lista — sem HairRule.
        UserFooter(
            me = me,
            fallbackName = meFallback,
            onEdited = onEditedProfile,
            onOpenSettings = onOpenSettings,
            onLogout = onLogout,
        )
    }

    when (val d = chanDialog) {
        is ChanDialog.NewChannel -> EditorialInputDialog(
            title = "nova órbita",
            placeholder = "nome-da-orbita",
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
            placeholder = "nome-da-orbita",
            initial = d.current,
            confirmLabel = "salvar",
            channelType = true,
            onDismiss = { chanDialog = null },
            onConfirm = { name, _ -> onRenameChannel(d.serverId, d.channelId, name) },
        )
        is ChanDialog.DeleteChannel -> ConfirmDialog(
            text = "excluir #${d.name}? apaga as mensagens dela — nao da pra desfazer.",
            confirmLabel = "excluir",
            onDismiss = { chanDialog = null },
            onConfirm = { onDeleteChannel(d.serverId, d.channelId) },
        )
        null -> Unit
    }
}

// Confirmacao destrutiva centralizada (mesma casca do EditorialInputDialog).
@Composable
private fun ConfirmDialog(
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
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
                Text(text, style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, lineHeight = 18.sp))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "cancelar",
                        style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        confirmLabel,
                        style = TextStyle(color = Obsidian.danger, fontSize = 12.sp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Obsidian.danger, RoundedCornerShape(8.dp))
                            .clickable { onDismiss(); onConfirm() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OrbitList(
    server: ServerDto?,
    activeChatId: String?,
    unread: Set<String>,
    members: List<ServerMemberDto>,
    voicePresence: Map<String, List<String>>,
    myId: String?,
    myVoiceChannelId: String?,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    onNewChannelInCat: (categoryId: String) -> Unit,
    onRenameCat: (categoryId: String, current: String) -> Unit,
    onDeleteCat: (categoryId: String) -> Unit,
    onReorderChannels: (orderedIds: List<String>) -> Unit,
    onOpenChannelRename: (channelId: String, current: String) -> Unit,
    onOpenChannelDelete: (channelId: String, name: String) -> Unit,
    onMarkChannelRead: (channelId: String) -> Unit,
    mutedChannels: Set<String>,
    onToggleChannelMute: (channelId: String) -> Unit,
) {
    if (server == null) return
    // Estrutura Discord: orbitas soltas primeiro, depois categorias colapsaveis.
    // Todos veem copiar ID / marcar lida na categoria; so o dono ganha gestao.
    val isOwner = server.ownerId == myId
    val clipboard = LocalClipboardManager.current
    var collapsedCats by remember(server.id) { mutableStateOf(setOf<String>()) }
    val catIds = server.categories.map { it.id }.toSet()
    val loose = server.channels.filter { it.categoryId == null || it.categoryId !in catIds }.sortedBy { it.position }
    val cats = server.categories.sortedBy { it.position }
    val byCat = server.channels.groupBy { it.categoryId }
    val looseIds = loose.map { it.id }
    // Estado do drag de reordenacao (uma instancia por constelacao aberta).
    val drag = remember(server.id) { ChannelDragState() }
    // Acoes do botao-direito da orbita (serverId ja embutido nas lambdas de cima).
    val chMenu = ChannelMenu(isOwner, mutedChannels, onMarkChannelRead, onOpenChannelRename, onOpenChannelDelete, onToggleChannelMute)

    // Cascata (F6): a posicao corrida na lista decide o atraso de entrada.
    // Os indices sao computados no escopo do DSL (sincrono e deterministico);
    // as lambdas dos itens so capturam constantes.
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
        itemsIndexed(loose, key = { _, ch -> ch.id }) { i, ch ->
            CascadeIn(i, server.id) {
                OrbitEntry(
                    ch, ch.id == activeChatId, ch.id in unread,
                    members, voicePresence, myId, myVoiceChannelId, onOpenChat, onOpenVoice,
                    dragCtx = if (isOwner) ChannelDragCtx(drag, "loose", i, loose.size, looseIds, onReorderChannels) else null,
                    menu = chMenu,
                )
            }
        }
        var offset = loose.size
        cats.forEach { cat ->
            val channels = byCat[cat.id].orEmpty().sortedBy { it.position }
            val headerRow = offset
            item(key = "cat-${cat.id}") {
                CascadeIn(headerRow, server.id) {
                    val head = @Composable {
                        CategoryHeader(
                            name = cat.name,
                            collapsed = cat.id in collapsedCats,
                            onToggle = {
                                collapsedCats =
                                    if (cat.id in collapsedCats) collapsedCats - cat.id else collapsedCats + cat.id
                            },
                        )
                    }
                    val catUnread = channels.any { it.id in unread }
                    var confirmDelCat by remember(cat.id) { mutableStateOf(false) }
                    EditorialContextMenu(entries = {
                        buildList {
                            if (catUnread) add(MenuEntry.Item("marcar categoria como lida") {
                                channels.forEach { if (it.id in unread) onMarkChannelRead(it.id) }
                            })
                            add(MenuEntry.Item("copiar ID") { clipboard.setText(AnnotatedString(cat.id)) })
                            if (isOwner) {
                                add(MenuEntry.Separator)
                                add(MenuEntry.Item("criar órbita aqui") { onNewChannelInCat(cat.id) })
                                add(MenuEntry.Item("renomear categoria") { onRenameCat(cat.id, cat.name) })
                                add(MenuEntry.Item("excluir categoria", danger = true) { confirmDelCat = true })
                            }
                        }
                    }) {
                        head()
                        if (confirmDelCat) ConfirmPopup(
                            message = "excluir a categoria ${cat.name}? não dá pra desfazer.",
                            confirmLabel = "excluir",
                            onConfirm = { onDeleteCat(cat.id) },
                            onDismiss = { confirmDelCat = false },
                        )
                    }
                }
            }
            // Colapsada ainda mostra a ativa e as nao lidas (comportamento Discord).
            val collapsed = cat.id in collapsedCats
            val channelIds = channels.map { it.id }
            val visible =
                if (collapsed) channels.filter { it.id == activeChatId || it.id in unread }
                else channels
            itemsIndexed(visible, key = { _, ch -> ch.id }) { i, ch ->
                CascadeIn(headerRow + 1 + i, server.id) {
                    OrbitEntry(
                        ch, ch.id == activeChatId, ch.id in unread,
                        members, voicePresence, myId, myVoiceChannelId, onOpenChat, onOpenVoice,
                        // Reordena so quando aberta (indice do visivel == indice real).
                        dragCtx = if (isOwner && !collapsed)
                            ChannelDragCtx(drag, "cat:${cat.id}", i, channels.size, channelIds, onReorderChannels) else null,
                        menu = chMenu,
                    )
                }
            }
            offset = headerRow + 1 + visible.size
        }
    }
    ChannelDragBubble(drag)
    }
}

// ---- Drag pra reordenar canais (so o dono). Estilo "bolha leve": a orbita vira um
// circulo flutuante que segue o cursor; ao soltar, faz fade e o canal reaparece na
// nova posicao. Reorder dentro da MESMA secao (soltos, ou de uma categoria). ----

private class ChannelDragState {
    var id by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var isVoice by mutableStateOf(false)
    var section by mutableStateOf<String?>(null)
    var fromIndex by mutableStateOf(-1)
    var targetIndex by mutableStateOf(-1)
    var windowPos by mutableStateOf(Offset.Zero)
    var fadingOut by mutableStateOf(false)
    val dragging: Boolean get() = id != null && !fadingOut
    fun reset() {
        id = null; name = ""; isVoice = false; section = null
        fromIndex = -1; targetIndex = -1; fadingOut = false
    }
}

private class ChannelDragCtx(
    val state: ChannelDragState,
    val section: String,
    val index: Int,
    val sectionSize: Int,
    val orderedIds: List<String>,
    val onReorder: (List<String>) -> Unit,
)

// Long-press pega a orbita; o arrasto move a bolha (windowPos) e calcula o slot alvo
// pela distancia percorrida / altura do item. Soltar reordena a secao. Chamado SEMPRE
// (ctx nulo = no-op) pra nao variar a contagem de composables entre recomposicoes.
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
                },
                onDragEnd = {
                    if (d.id == ch.id && d.targetIndex in 0 until ctx.sectionSize && d.targetIndex != d.fromIndex) {
                        val list = ctx.orderedIds.toMutableList()
                        list.add(d.targetIndex, list.removeAt(d.fromIndex))
                        ctx.onReorder(list)
                    }
                    if (d.id == ch.id) d.fadingOut = true // dispara o fade da bolha
                },
                onDragCancel = { if (d.id == ch.id) d.reset() },
            )
        }
}

// A bolha flutuante (Popup em coords de janela, segue o cursor 1:1 — sem inercia).
// Entrada = "gota" que coalesce (comeca alongada na vertical e assenta redonda, com
// leve overshoot da mola); saida = "esparrama" (achata na horizontal e some), e so
// entao reseta. Tudo em graphicsLayer com leitura DIFERIDA (scaleX/scaleY/alpha lidos
// dentro do lambda de draw) -> so a camada re-renderiza por frame, sem recompor: leve.
@Composable
private fun ChannelDragBubble(d: ChannelDragState) {
    if (d.id == null) return
    val reduce = LocalReduceMotion.current
    val enter = remember(d.id) { Animatable(0f) } // 0=sem bolha ..1=formada
    val splat = remember(d.id) { Animatable(0f) } // 0=inteira ..1=esparramada
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
                        // Leitura diferida (draw-phase): nao recompoe, so re-desenha a camada.
                        val e = enter.value
                        val ec = e.coerceIn(0f, 1f)
                        val squash = (1f - ec) * 0.22f // gota: alongada vertical no comeco
                        val x = splat.value
                        scaleX = e * (1f - squash) * (1f + 0.55f * x) // esparrama = achata p/ fora
                        scaleY = e * (1f + squash) * (1f - 0.5f * x)
                        alpha = ec * (1f - x)
                    }
                    .clip(CircleShape)
                    .background(Obsidian.overlay)
                    .border(1.dp, Obsidian.accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(if (voice) Lucide.Volume2 else Lucide.Hash, tint = Obsidian.accent, size = 20.dp)
            }
            Spacer(Modifier.height(5.dp))
            // O nome so faz fade junto (nao esparrama — texto esticado fica estranho).
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

// Orbita + (se for de voz) a lista de quem esta na sala logo abaixo, indentada
// — estilo Discord, pra quem esta de fora saber que tem gente na call.
@Composable
private fun OrbitEntry(
    ch: ChannelDto,
    active: Boolean,
    unread: Boolean,
    members: List<ServerMemberDto>,
    voicePresence: Map<String, List<String>>,
    myId: String?,
    myVoiceChannelId: String?,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    dragCtx: ChannelDragCtx? = null,
    menu: ChannelMenu,
) {
    val clipboard = LocalClipboardManager.current
    // Column: o CascadeIn envolve isto num Box (empilha) — sem a Column, a lista
    // de presenca ficaria SOBRE o canal em vez de abaixo. Empilha na vertical.
    Column(Modifier.fillMaxWidth()) {
        var confirmDelCh by remember(ch.id) { mutableStateOf(false) }
        // Botao direito na orbita: marcar lido / copiar ID (todos) + renomear/excluir (dono).
        EditorialContextMenu(entries = {
            buildList {
                if (unread) add(MenuEntry.Item("marcar como lido") { menu.onMarkRead(ch.id) })
                add(MenuEntry.Item(if (ch.id in menu.mutedChannels) "reativar órbita" else "silenciar órbita") { menu.onToggleMute(ch.id) })
                add(MenuEntry.Item("copiar ID") { clipboard.setText(AnnotatedString(ch.id)) })
                if (menu.isOwner) {
                    add(MenuEntry.Separator)
                    add(MenuEntry.Item("renomear") { menu.onRename(ch.id, ch.name) })
                    add(MenuEntry.Item("excluir órbita", danger = true) { confirmDelCh = true })
                }
            }
        }) {
            OrbitItem(ch, active, unread, onOpenChat, onOpenVoice, dragCtx)
            if (confirmDelCh) ConfirmPopup(
                message = "excluir a órbita ${ch.name}? apaga as mensagens dela — não dá pra desfazer.",
                confirmLabel = "excluir",
                onConfirm = { menu.onDelete(ch.id, ch.name) },
                onDismiss = { confirmDelCh = false },
            )
        }
        if (ch.type == "VOICE") {
            // Presenca do poll + eu otimista (aparece na hora que entro, sem
            // esperar o proximo ciclo de ~5s do backend).
            val ids = remember(voicePresence, ch.id, myVoiceChannelId, myId) {
                val base = voicePresence[ch.id].orEmpty()
                if (myVoiceChannelId == ch.id && myId != null && myId !in base) listOf(myId) + base else base
            }
            ids.forEach { uid ->
                val user = members.find { it.userId == uid }?.user
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
        // Indentado sob o nome do canal (alinha o avatar ~onde fica o glifo ◉).
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
            // weight = ellipsiza no espaco que sobra (nome grande virava "..." vazando
            // e a linha ficava cortada na borda da sidebar).
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CategoryHeader(name: String, collapsed: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Chevron gira ao colapsar (▾ -> ▸).
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, tween(140))
    val tint = if (hovered) Obsidian.text2 else Obsidian.text3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(top = 10.dp, bottom = 2.dp)
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

// ---- Criacao de orbita/categoria (dono) ----

private sealed interface ChanDialog {
    data class NewChannel(val serverId: String, val categoryId: String?) : ChanDialog
    data class NewCategory(val serverId: String) : ChanDialog
    data class RenameCategory(val serverId: String, val categoryId: String, val current: String) : ChanDialog
    data class RenameChannel(val serverId: String, val channelId: String, val current: String) : ChanDialog
    data class DeleteChannel(val serverId: String, val channelId: String, val name: String) : ChanDialog
}

// Botao direito que agrupa as acoes de uma orbita, montado no OrbitList (ja sabe
// se e dono) e passado adiante pro OrbitEntry. serverId ja fica embutido nas lambdas.
private class ChannelMenu(
    val isOwner: Boolean,
    val mutedChannels: Set<String>,
    val onMarkRead: (channelId: String) -> Unit,
    val onRename: (channelId: String, current: String) -> Unit,
    val onDelete: (channelId: String, name: String) -> Unit,
    val onToggleMute: (channelId: String) -> Unit,
)

// Nome de orbita segue a regra do backend (^[a-z0-9-]+$): sanitiza ao vivo pra
// dar o mesmo feedback do Discord (espaco vira hifen, acento/simbolo some).
private fun sanitizeChannel(s: String): String =
    s.lowercase().replace(Regex("\\s+"), "-").replace(Regex("[^a-z0-9-]"), "").take(50)

// Centraliza o dialogo na JANELA (ignora a ancora) — modal flutuante estilo Discord.
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
                    onValueChange = { text = if (channelType) sanitizeChannel(it) else it.take(50) },
                    singleLine = true,
                    textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
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
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    dragCtx: ChannelDragCtx? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val isUnread = !active && unread
    val itemBg by animateColorAsState(
        if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent,
        tween(120),
    )
    val dSt = dragCtx?.state
    // A orbita arrastada fica esmaecida no lugar (a bolha e a copia "levantada").
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
                // Orbita de voz abre a sala (sonda V1); texto abre o chat.
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
            )
        }
        if (isUnread) UnreadPill(Modifier.align(Alignment.CenterStart))
        // Marca de insercao do drag: linha accent no topo (subindo) ou na base
        // (descendo) da orbita que esta no slot alvo — nunca na propria arrastada.
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

// Traco accent na borda esquerda do item — marca de nao-lida (estilo Discord,
// tokens obsidiana).
@Composable
private fun UnreadPill(modifier: Modifier = Modifier) {
    // Pulso sutil (F6): o marcador "respira" devagar pra puxar o olho sem gritar.
    // Reduzir movimento: fica aceso e parado.
    val glow = if (LocalReduceMotion.current) 1f else {
        val transition = rememberInfiniteTransition()
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = EaseOutSoft),
                repeatMode = RepeatMode.Reverse,
            ),
        ).value
    }
    Box(
        modifier
            .width(3.dp)
            .height(16.dp)
            .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
            .graphicsLayer { alpha = glow }
            .background(Obsidian.accent),
    )
}

@Composable
private fun DmList(
    dms: List<ConversationDto>,
    onToggleMute: (ConversationDto) -> Unit,
    onMarkRead: (String) -> Unit,
    activeChatId: String?,
    unread: Set<String>,
    dmTyping: Set<String>,
    onOpenChat: (ChatTarget) -> Unit,
) {
    if (dms.isEmpty()) {
        EmptyHint("nenhum sussurro ainda")
        return
    }
    val clipboard = LocalClipboardManager.current
    // Estrutura Discord: busca no topo dos sussurros (filtro local por nome).
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
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Obsidian.base)
                        .border(1.dp, Obsidian.borderMid.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    if (query.isEmpty()) {
                        Text("encontrar conversa", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        )
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("nada encontrado", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
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
            val itemBg by animateColorAsState(
                if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent,
                tween(120),
            )
            // Cascata no boot (F6) + botao direito: mutar/desmutar + marcar lida (F4).
            CascadeIn(cascadeRow, Unit) {
            EditorialContextMenu(entries = {
                buildList {
                    add(
                        MenuEntry.Item(if (conv.muted) "desmutar sussurro" else "mutar sussurro") {
                            onToggleMute(conv)
                        },
                    )
                    if (isUnread) add(MenuEntry.Item("marcar como lida") { onMarkRead(conv.id) })
                    u?.id?.let { uid -> add(MenuEntry.Item("copiar ID") { clipboard.setText(AnnotatedString(uid)) }) }
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
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopAvatar(u?.avatarUrl, name, 28)
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
                                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (isUnread) UnreadPill(Modifier.align(Alignment.CenterStart))
                // Divisoria arredondada no rodape de cada card (destaca cada sussurro).
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(1.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Obsidian.borderDim.copy(alpha = 0.5f)),
                )
            }
            }
            }
            }
        }
    }
}

// ---- Palco central ----

@Composable
private fun Stage(
    server: ServerDto?,
    chat: ChatTarget?,
    voiceChannel: ChannelDto?,
    // Engine so quando a sala do palco E a que voce entrou; null = antessala.
    voiceEngine: VoiceEngine?,
    voicePresence: List<String>,
    onJoinVoice: () -> Unit,
    onLeaveVoice: () -> Unit,
    createChatVm: (ChatTarget) -> ChatVm,
    members: List<ServerMemberDto>,
    me: ProfileUserDto?,
    membersOpen: Boolean,
    onToggleMembers: () -> Unit,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onStartDm: (String, String) -> Unit,
    showDiscover: Boolean,
    onDiscoverJoined: (String) -> Unit,
    showFriends: Boolean,
    modifier: Modifier = Modifier,
) {
    // Cartao do palco: onde vive o texto do chat, entao alpha um tico maior que
    // os outros paineis pra leitura (aurora aparece, mas nao briga com a mensagem).
    Column(modifier.fillMaxHeight().panelCard(Obsidian.base, 0.32f)) {
        // Amigos ocupa o palco inteiro (cabecalho + abas proprios).
        if (showFriends) {
            FriendsView(onStartDm, Modifier.fillMaxSize())
            return@Column
        }
        // Descobrir ocupa o palco inteiro (tem cabecalho + busca proprios).
        if (showDiscover) {
            DiscoverView(onDiscoverJoined, Modifier.fillMaxSize())
            return@Column
        }
        // Top bar do palco
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
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
            Text(
                text = when {
                    voiceChannel != null -> voiceChannel.name
                    chat is ChatTarget.Channel -> chat.title
                    chat is ChatTarget.Dm -> "sussurro · ${chat.title}"
                    server != null -> "constelacao · ${server.name}"
                    else -> "sussurros"
                },
                style = TextStyle(
                    color = if (chat != null || voiceChannel != null) Obsidian.text1 else Obsidian.text3,
                    fontSize = 13.sp,
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (server != null) {
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (hovered) Obsidian.hover else Color.Transparent)
                        .border(1.dp, Obsidian.borderMid, CircleShape)
                        .hoverable(interaction)
                        .clickable(onClick = onToggleMembers),
                    contentAlignment = Alignment.Center,
                ) {
                    LIcon(
                        Lucide.Users,
                        tint = if (membersOpen) Obsidian.accent else Obsidian.text3,
                        size = 15.dp,
                    )
                }
            }
        }
        HairRule()

        // Sala de voz ocupa o palco. Sem engine = voce abriu a sala mas ainda nao
        // entrou -> antessala com quem esta la e o botao verde.
        if (voiceChannel != null) {
            if (voiceEngine != null) VoiceView(voiceChannel, members, me, voiceEngine, onLeaveVoice)
            else VoiceLobby(voiceChannel, members, voicePresence, onJoinVoice)
            return@Column
        }

        // Fade ao abrir/trocar conversa: a pagina antiga (com seu ChatVm) segue
        // renderizando ate o fade acabar; o dispose roda no fim.
        AnimatedContent(
            targetState = chat,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            if (target != null) {
                val chatVm = remember { createChatVm(target) }
                DisposableEffect(Unit) { onDispose { chatVm.dispose() } }
                ChatView(target, chatVm, onStartDm)
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

// ---- Painel de membros (240dp) ----

@Composable
private fun MembersPanel(
    members: List<ServerMemberDto>,
    myId: String?,
    serverId: String?,
    isOwner: Boolean,
    onStartDm: (String, String) -> Unit,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.width(240.dp).fillMaxHeight().panelCard(Obsidian.raised, 0.20f)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = "membros — ${members.size}",
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
            )
        }
        HairRule()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            itemsIndexed(members, key = { _, m -> m.userId }) { i, m ->
                val name = m.user.displayName ?: m.user.username
                val isMe = m.userId == myId
                // Cascata ao abrir o painel (F6) + linha clicavel = card de perfil (F3)
                // + botao direito = menu (sussurro/copiar ID; expulsar/banir do dono).
                CascadeIn(i, members.size) {
                    var confirmMember by remember(m.userId) { mutableStateOf<String?>(null) }
                    EditorialContextMenu(entries = {
                        buildList {
                            if (!isMe) add(MenuEntry.Item("sussurro") { onStartDm(m.user.username, name) })
                            add(MenuEntry.Item("copiar ID") { clipboard.setText(AnnotatedString(m.userId)) })
                            if (isOwner && !isMe && serverId != null) {
                                add(MenuEntry.Separator)
                                add(MenuEntry.Item("expulsar", danger = true) { confirmMember = "kick" })
                                add(MenuEntry.Item("banir", danger = true) { confirmMember = "ban" })
                            }
                        }
                    }) {
                        confirmMember?.let { act ->
                            ConfirmPopup(
                                message = if (act == "ban") "banir ${name}? a pessoa não poderá voltar." else "expulsar ${name}?",
                                confirmLabel = if (act == "ban") "banir" else "expulsar",
                                onConfirm = { if (act == "ban") onBan(m.userId) else onKick(m.userId) },
                                onDismiss = { confirmMember = null },
                            )
                        }
                        ProfileAnchor(m.userId, isMe = isMe, onStartDm = onStartDm) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DesktopAvatar(m.user.avatarUrl, name, 26)
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    text = name,
                                    style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
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

// ---- Pecinhas ----

@Composable
fun HairRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Obsidian.borderDim.copy(alpha = 0.6f)))
}

// Botao "Amigos" no topo dos sussurros (padrao Discord) — abre a tela de amigos
// no palco. Ativo = destaque ambar.
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
