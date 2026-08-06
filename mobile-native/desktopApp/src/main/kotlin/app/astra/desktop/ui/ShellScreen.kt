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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.AnnotatedString
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
import app.astra.desktop.auth.Session
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.xp.MissoesStore
import app.astra.desktop.xp.XpStore
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.voice.VoiceEngine
import app.astra.desktop.voice.VoiceSession
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.ChatVm
import app.astra.desktop.shell.Selection
import app.astra.desktop.shell.ShellVm
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.Obsidian
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.Bot
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
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.MemberRoleDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext

// Shell desktop (fatia 2): rail 72 | sidebar 260 | palco | membros 240.
// Compacto (decisao do dono); chat de verdade e a próxima fatia.
@Composable
fun ShellScreen(
    session: Session,
    windowInactive: () -> Boolean,
    notify: (String, String) -> Unit,
    onLogout: () -> Unit,
    searchOpen: Boolean = false,
    onCloseSearch: () -> Unit = {},
    notifOpen: Boolean = false,
    onCloseNotif: () -> Unit = {},
    missoesOpen: Boolean = false,
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
    // Call VIVA acima da navegacao: o engine mora aqui, não dentro da VoiceView.
    // Trocar de órbita não desconecta mais — so desligar (ou entrar noutra sala).
    val voice = remember { VoiceSession(scope, koin) }
    DisposableEffect(Unit) { onDispose { voice.leave() } }
    var settingsOpen by remember { mutableStateOf(false) }
    // Aba em que o takeover abre: a engrenagem cai em Conta, o avatar do rodape
    // cai em Perfil.
    var settingsTab by remember { mutableStateOf(SettingsTab.ACCOUNT) }
    // Configuracoes da CONSTELACAO (outro takeover): sempre a selecionada.
    var serverSettingsOpen by remember { mutableStateOf(false) }
    // Convite aberto pelo botao da faixa do banner. Declarado aqui em cima porque
    // quem SETA (a faixa, dentro do Sidebar) e quem RENDERIZA (o Box de fora) estao
    // em ramos diferentes da arvore.
    var convidarPelaFaixa by remember { mutableStateOf<ServerDto?>(null) }
    // Ctrl+K = quick-switcher (pular pra canal/sussurro). Foco na raiz garante que o
    // atalho dispara mesmo sem nada clicado; onPreviewKeyEvent na raiz ve a tecla
    // antes de qualquer campo de texto filho.
    var paletteOpen by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    LaunchedEffect(Unit) { socket.connect() }

    // Progressao: le o XP uma vez e depois fica escutando o `xp_gain`. Vive no
    // escopo do shell — some junto com a sessao, sem coletor orfao depois do logout.
    LaunchedEffect(Unit) { koin.get<XpStore>().iniciar(scope) }

    // Missoes: so o coletor do socket sobe no boot (barato, e o que faz o aviso
    // aparecer). O painel em si so e buscado quando alguem abre a tela — carregar no
    // boot seria uma requisicao a mais no pior momento, pra desenhar nada.
    LaunchedEffect(Unit) { koin.get<MissoesStore>().iniciar(scope) }

    // Permissões do Windows na PRIMEIRA abertura, pra quem JÁ TINHA conta — quem
    // cria conta agora vê a mesma lista dentro das boas-vindas (e o onDone de lá
    // marca esta pref, pra não mostrar duas vezes seguidas). Depois fica em
    // Configurações > Permissões. Vem antes da primeira call de propósito:
    // descobrir que o mic está bloqueado no meio da conversa é o pior momento.
    val sessionStore = remember { koin.get<SessionStore>() }
    var permsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (sessionStore.uiPref("permsVistas") != "1") {
            // Um respiro pra a janela desenhar antes: a checagem abre o microfone
            // e o diálogo caindo em cima do carregamento parece travamento.
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

    // Fui expulso/banido: o VM ja me tirou da call e da constelacao; aqui so
    // explico o que houve. Vem DEPOIS do dialogo de permissoes de proposito — se
    // os dois abrissem juntos, um Popup focavel roubaria o foco do outro.
    state.penalidade?.let { p ->
        val onde = p.constelacao?.let { " de $it" } ?: ""
        CenteredConfirmDialog(
            message = if (p.tipo == "banido") "você foi banido$onde" else "você foi removido$onde",
            detalhe = when {
                !p.motivo.isNullOrBlank() -> "motivo: ${p.motivo}"
                p.tipo == "banido" -> "não dá pra entrar de novo enquanto o banimento valer."
                else -> "você pode entrar de novo se receber um convite."
            },
            confirmLabel = "entendi",
            cancelLabel = null,
            perigo = false,
            onConfirm = { vm.dispensarPenalidade() },
            onDismiss = { vm.dispensarPenalidade() },
        )
    }

    // Janela escondida/minimizada = ninguem olha o auto-preview da transmissão. Ele
    // custa conversao + upload de textura a 60fps, entao desliga enquanto não da pra
    // ver e volta ao reaparecer. O que os OUTROS recebem não muda (encoder e outro
    // caminho) — economia pura.
    val windowActive = LocalWindowActive.current
    LaunchedEffect(windowActive) { voice.engine?.setPreviewEnabled(windowActive) }

    // Badge do sino: o servidor AVISA (evento 'notification' na sala user:<id>), então
    // o badge sobe na hora em vez de esperar o próximo poll. O poll continua, mas
    // lento (2min) e so como rede de seguranca — ele e a fonte AUTORITATIVA da
    // contagem (corrige o palpite local e conta o que chegou com o app fechado).
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
    LaunchedEffect(Unit) {
        socket.notification.collect {
            notifCount += 1
            onNotifUnread(notifCount)
        }
    }

    // Toast na bandeja quando chega mensagem com a janela fechada/minimizada.
    // DM tem autor+conteudo (salas todas joinadas); canal so tem o id do
    // channel_activity -> notificação generica com o nome da órbita.
    val json = remember { koin.get<Json>() }
    LaunchedEffect(Unit) {
        launch {
            socket.newDm.collect { raw ->
                if (!windowInactive() || !prefs.state.value.notifyDms) return@collect
                val msg = runCatching { json.decodeFromString<DmMessageDto>(raw) }.getOrNull() ?: return@collect
                if (msg.senderId == session.userId) return@collect
                if (vm.state.value.dms.any { it.id == msg.conversationId && it.muted }) return@collect
                val name = msg.author?.displayName ?: msg.author?.username ?: "alguem"
                notify(name, msg.content.ifBlank { "anexo" }.take(120))
            }
        }
        launch {
            socket.channelActivity.collect { raw ->
                if (!windowInactive() || !prefs.state.value.notifyChannels) return@collect
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
    // CompositionLocal. auroraOn/starsOn/reduceMotionEff já aplicam o modo
    // desempenho (kill-switch) por cima dos toggles individuais.
    CompositionLocalProvider(
        LocalReduceMotion provides prefState.reduceMotionEff,
        LocalRenderPrefs provides RenderPrefs(prefState.auroraQuality.octaves, prefState.uiFps.cap),
        LocalMinhaConta provides MinhaConta(session.userId, state.me?.username),
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
        // Aurora e estrelas NAO moram mais aqui: subiram pra janela (Main.kt), atrás
        // do login e do shell ao mesmo tempo. Sem isso a entrada saltava — a aurora
        // do login ocupava 45% da largura e a do shell 100%, e o uv do shader e
        // normalizado pelo tamanho, entao eram duas imagens diferentes. Uma so
        // instancia também significa um shader em vez de dois durante a transicao.
        // Paineis = cartoes flutuantes (estilo mobile): gap entre eles + cantos
        // arredondados deixam a aurora respirar nas juntas (impressao de
        // sobreposicao). Margem externa de 8dp separa do titulo/bordas da janela.
        // Escondidos enquanto o Settings (takeover) esta aberto: assim a UNICA aurora
        // do shell (montada acima) fica continua por baixo do Settings — sem aurora
        // nova, sem salto de posição ao trocar de aba. Crossfade rapido.
        // Checklist de 1o acesso (metade "checklist" do onboarding): so pra quem
        // acabou de passar pelo takeover (pref "checklist:<id>"=1). Risca sozinho
        // conforme cria constelação / manda sussurro; some ao completar os dois ou
        // no "pular".
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
        val firstSteps: (@Composable () -> Unit)? = if (checklistActive) {
            {
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
        } else {
            null
        }

        AnimatedVisibility(
            visible = !settingsOpen && !serverSettingsOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
        Row(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        // Quem pode abrir as configurações da constelação, e como abrir. Sao os
        // MESMOS dois pra rail e pra engrenagem abaixo do banner — duas rotas pra
        // mesma tela, uma regra so.
        val podeConfigurar: (String) -> Boolean = { id ->
            (state.selection as? Selection.Server)?.id == id &&
                state.myPerms?.let { it.isOwner || it.isAdmin || "MANAGE_SERVER" in it.permissions } == true
        }
        val abrirConfigDaConstelacao: (String) -> Unit = { id ->
            // Selecionar antes de abrir: a tela le a constelação selecionada, e
            // assim também chega o myPerms dela.
            vm.select(Selection.Server(id))
            serverSettingsOpen = true
        }
        Rail(
            servers = state.servers,
            selection = state.selection,
            myId = session.userId,
            mutedServers = state.mutedServers,
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
            // Ids são unicos: o "ativo" da sidebar cobre chat de texto OU sala de voz.
            activeChatId = chat?.id ?: state.voiceChannel?.id,
            unread = state.unread,
            unreadCounts = state.unreadCounts,
            dmTyping = state.dmTyping,
            me = state.me,
            meFallback = session.displayName,
            loading = state.loading,
            members = state.members,
            voicePresence = state.voicePresence,
            memberPresence = state.memberPresence,
            myId = session.userId,
            onConvidar = { convidarPelaFaixa = it },
            // Eu-otimista na sidebar so quando ESTOU CONECTADO (voice.joined), não
            // quando so abri a antessala (state.voiceChannel). Antes o meu ícone
            // aparecia sob o canal no instante em que eu clicava nele, sem entrar.
            myVoiceChannelId = voice.joined?.id,
            onOpenChat = vm::openChat,
            onOpenVoice = vm::openVoice,
            onToggleMute = vm::toggleDmMute,
            onMarkRead = vm::markDmRead,
            onCloseDm = vm::closeDm,
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
            onMoveChannelToCategory = vm::moveChannelToCategory,
            onReorderCategories = vm::reorderCategories,
            onRenameChannel = vm::renameChannel,
            onDeleteChannel = vm::deleteChannel,
            onMarkChannelRead = vm::markChannelRead,
            mutedChannels = state.mutedChannels,
            onToggleChannelMute = vm::toggleChannelMute,
            onToggleChannelBot = vm::setChannelBot,
            onToggleCatBot = vm::setCategoryBot,
            membersOpen = state.membersOpen,
            onToggleMembers = vm::toggleMembers,
            canManageSelected = podeConfigurar,
            onOpenServerSettings = abrirConfigDaConstelacao,
            firstSteps = firstSteps,
        )
        Stage(
            state.selectedServer,
            chat = chat,
            voiceChannel = state.voiceChannel,
            voiceEngine = voice.engineFor(state.voiceChannel),
            voicePresence = state.voiceChannel?.let { state.voicePresence[it.id] }.orEmpty(),
            // Entrar de verdade: conecta E anuncia. O anuncio mora aqui (e nao no
            // openVoice) porque abrir a antessala nao e entrar na call.
            onJoinVoice = { state.voiceChannel?.let { voice.join(it); vm.announceVoiceJoin(it.id) } },
            // Desligar = sair de verdade e limpar o palco.
            onLeaveVoice = { voice.leave(); vm.leaveVoice() },
            createChatVm = createChatVm,
            members = state.members,
            me = state.me,
            loading = state.loading,
            error = state.error,
            onRetry = vm::load,
            onStartDm = vm::startDm,
            showDiscover = state.selection is Selection.Discover,
            onDiscoverJoined = vm::refreshServersAndSelect,
            // COLECAO MONTADA NA HORA E VENENO PRA RECOMPOSICAO.
            //
            // `Set` e instavel pro Compose, entao ele so consegue pular quando
            // recebe A MESMA INSTANCIA (comparacao por identidade, que e como o
            // strong skipping trata instavel). Montado aqui dentro, o Set nasce
            // NOVO a cada recomposicao deste shell — e o shell recompoe a cada
            // mensagem, presenca, alguem digitando. Resultado: o palco inteiro
            // nunca pulava, por causa de um argumento que quase nunca muda.
            //
            // Com o remember, a instancia so troca quando a lista de constelacoes
            // troca de verdade.
            joinedServerIds = remember(state.servers) { state.servers.map { it.id }.toSet() },
            showFriends = state.selection is Selection.Dms && state.friendsOpen,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            // Volta a ter interruptor (embaixo do banner): entrar numa constelação
            // ainda mostra os membros — o padrão continua ABERTO —, mas dá pra
            // fechar e ganhar a largura pro chat.
            visible = state.selection is Selection.Server && state.membersOpen,
            enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
            exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120)),
        ) {
            MembersPanel(
                members = state.members,
                presence = state.memberPresence,
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

        // Card flutuante da call: so quando você esta conectado E saiu da sala no
        // palco. Fica por cima de tudo (inclusive das configurações) — e o único
        // lugar com o botao de desligar depois que navegar deixou de desconectar.
        val joined = voice.joined
        val joinedEngine = voice.engine
        if (joined != null && joinedEngine != null && state.voiceChannel?.id != joined.id) {
            CallDock(
                channel = joined,
                engine = joinedEngine,
                meName = state.me?.displayName ?: state.me?.username ?: "você",
                meAvatar = state.me?.avatarUrl,
                onExpand = { vm.openVoice(joined) },
                onLeave = { voice.leave(); vm.leaveVoice() },
            )
        }

        // Configuracoes da constelação: mesma entrada do Settings (decisao do dono),
        // pra as duas telas de configuração se comportarem igual. Sai sozinha se a
        // constelação deixar de existir (excluir/sair fecham pelo proprio estado).
        val cfgServer = state.selectedServer
        AnimatedVisibility(
            visible = serverSettingsOpen && cfgServer != null,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.98f),
        ) {
            // Guarda o último servidor valido: durante o fade de saida o selectedServer
            // já pode ser null (ex.: acabou de excluir) e a tela não pode piscar vazia.
            val shown = remember(cfgServer) { cfgServer }
            shown?.let { srv ->
                ServerSettingsScreen(
                    server = srv,
                    isOwner = srv.ownerId == session.userId,
                    members = state.members,
                    // isAdmin (cargo legado) concede o conjunto que o backend trata
                    // como de admin; senao, so as permissões granulares dos cargos.
                    // Mesmo caso do joinedServerIds acima: Set novo a cada
                    // recomposicao impedia a tela de configuracoes de pular.
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
                // O teste usa o MESMO caminho do aviso de verdade (bandeja do SO).
                // Um "toast falso" desenhado dentro do app provaria nada — o que
                // costuma falhar e justamente o SO: foco de notificacao desligado,
                // modo nao perturbe, icone escondido na bandeja.
                onTestarNotificacao = {
                    notify("Astra", "Se você está lendo isto, os avisos funcionam.")
                },
            )
        }

        // Ctrl+K: quick-switcher em takeover (fade + leve zoom, como o settings).
        // Nasce do topo-centro (onde o card fica), não do centro da tela.
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

        // Busca-A (lupa no titlebar): palette dedicada com abas. Fade+zoom sutil,
        // nascendo do topo-centro (posição do card).
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
                // Sussurro do historico ja tem id de conversa: abre direto, sem
                // passar pelo startDm (que resolve @usuario -> conversa).
                onOpenDm = { convId, title ->
                    vm.select(Selection.Dms)
                    vm.openChat(ChatTarget.Dm(convId, title))
                },
            )
        }

        // Notificacoes-A (sino no titlebar): dropdown topo-direita. Nasce do canto
        // do sino (topo-direita), não do centro da tela.
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

        // Missoes (alvo no titlebar). Nasce do topo-direita como o sino — os dois
        // saem da mesma regiao da barra.
        AnimatedVisibility(
            visible = missoesOpen,
            enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.97f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.97f, transformOrigin = TransformOrigin(1f, 0f)),
        ) {
            MissoesOverlay(onClose = onCloseMissoes)
        }

        // Convite aberto pela faixa do banner. Mora AQUI, no Box de fora, e nao
        // junto do Sidebar: um Popup escrito dentro daquele Row conta como filho
        // pro Arrangement.spacedBy, entao o layout inteiro ganhava 8dp de
        // deslocamento no instante em que o dialogo abria. Popup nao ocupa espaco,
        // mas ocupa VAGA na contagem de filhos — e essa e a pegadinha.
        //
        // Estado proprio (a rail tem o dela): sao dois pontos de entrada distantes
        // pro mesmo dialogo, e hoistar o da rail pra ca mexeria numa assinatura
        // que ja esta grande demais.
        convidarPelaFaixa?.let { alvo ->
            InvitePeopleDialog(
                serverName = alvo.name,
                inviteCode = alvo.inviteCode,
                onAdd = { username, onResult -> vm.addMember(alvo.id, username, onResult) },
                onClose = { convidarPelaFaixa = null },
            )
        }

        // O aviso fica FORA do AnimatedVisibility das telas: missao pode fechar com
        // qualquer coisa aberta (ou nada), e o aviso tem que aparecer do mesmo jeito.
        MissaoToaster()
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

// Confirmacao "Tem certeza?" reusavel: popup obsidiana no ponto, ação em danger.
// Usada por todo delete/sair (canal, categoria, constelação, expulsar, banir,
// logout). O chamador guarda um Boolean e renderiza isto quando true.
//
// ATENCAO ao usar: sem `posicao`, o Popup ancora no CONTAINER onde ele foi
// escrito, nao no botao que o abriu. Dentro de uma tela de configuracoes inteira
// isso joga a caixinha no topo da pagina, longe do botao — foi o que aconteceu
// com o regenerar convite. Se o chamador nao estiver colado no botao, passe
// `posicao = AoLadoDoBotao` e escreva o ConfirmPopup DENTRO de um Box que
// embrulhe so o botao (o Box e quem vira a ancora).
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
    val props = PopupProperties(focusable = true)
    if (posicao != null) {
        Popup(popupPositionProvider = posicao, onDismissRequest = onDismiss, properties = props) { corpo() }
    } else {
        Popup(onDismissRequest = onDismiss, properties = props) { corpo() }
    }
}

// Cola o popup na DIREITA da ancora, centralizado na altura dela. Se nao couber
// ate a borda da janela, vai pra esquerda; se nem la couber, encosta na borda.
// Cortar a caixa pela metade seria pior que desalinhar.
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

// Confirmacao CENTRAL (logout): scrim escurecido em tela cheia + card no centro,
// entrada em escala+fade. Diferente do ConfirmPopup ancorado — aqui a decisao e
// modal (sair da conta merece uma pausa). Clique no escurecido (fora do card)
// cancela; o card engole o proprio clique pra não vazar.
@Composable
fun CenteredConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    // null = um botao so. Aviso nao e pergunta: quando nao ha o que escolher, um
    // "cancelar" ao lado do "entendi" so faz a pessoa procurar a diferenca.
    cancelLabel: String? = "cancelar",
    // Detalhe opcional embaixo da frase principal (motivo do banimento, etc.).
    detalhe: String? = null,
    // Vermelho = acao destrutiva. Aviso que a pessoa so le nao e destrutivo.
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
                .clickable(interactionSource = scrimSrc, indication = null, onClick = onDismiss),
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
                    // Engole o clique dentro do card pra não vazar pro scrim (cancela).
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

// ---- Ctrl+K quick-switcher: pular pra qualquer canal/sussurro pelo teclado ----
private data class QuickResult(
    val kind: String, // "channel" | "dm"
    val id: String,
    val title: String,
    val subtitle: String, // nome da constelação (canal) ou "sussurro" (dm)
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
                // Clique no painel não fecha (so o scrim fecha).
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

// ---- Rail de constelações (72dp) ----

@Composable
private fun Rail(
    servers: List<ServerDto>,
    selection: Selection,
    myId: String?,
    mutedServers: Set<String>,
    // "posso gerenciar esta constelação?" — so responde true pra SELECIONADA (ver
    // o comentario no menu abaixo).
    canManageSelected: (String) -> Boolean,
    onOpenServerSettings: (String) -> Unit,
    onSelect: (Selection) -> Unit,
    onLeaveServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onCreateServer: (name: String, isGroup: Boolean) -> Unit,
    onToggleServerMute: (String) -> Unit,
    onMarkServerRead: (String) -> Unit,
    onAddMember: (serverId: String, username: String, onResult: (String?) -> Unit) -> Unit,
    onJoinInvite: (raw: String, onResult: (String?) -> Unit) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    // Constelação com o dialogo de convite aberto (null = fechado).
    var inviteFor by remember { mutableStateOf<ServerDto?>(null) }
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
            // Marca do Astra = a logo TRANSPARENTE (astra-glyph.png: so o planeta
            // branco, sem quadrado de fundo), a mesma da tela de procura por update.
            Image(
                painter = painterResource("astra-glyph.png"),
                contentDescription = "sussurros",
                modifier = Modifier.size(26.dp),
            )
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
                // Botao direito na constelação: dono exclui (apaga pra todos); membro
                // sai. Ambos com confirmação (F4).
                var confirmLeave by remember(srv.id) { mutableStateOf(false) }
                var confirmDelete by remember(srv.id) { mutableStateOf(false) }
                val isOwner = srv.ownerId == myId
                EditorialContextMenu(entries = {
                    buildList {
                        // Convidar abre o dialogo (adicionar por @ + link pronto). O
                        // "copiar convite" antigo copiava o CODIGO cru, que ninguem
                        // sabia o que fazer com — agora o link vai copiado inteiro.
                        add(MenuEntry.Item("convidar pessoas", icon = Lucide.Users) { inviteFor = srv })
                        srv.inviteCode?.let { code ->
                            add(MenuEntry.Item("copiar link do convite", icon = Lucide.Link) {
                                clipboard.setText(AnnotatedString(inviteLink(code)))
                            })
                        }
                        add(MenuEntry.Item(if (srv.id in mutedServers) "reativar constelação" else "silenciar constelação", icon = if (srv.id in mutedServers) Lucide.Bell else Lucide.BellOff) { onToggleServerMute(srv.id) })
                        add(MenuEntry.Item("marcar tudo como lido", icon = Lucide.CheckCheck) { onMarkServerRead(srv.id) })
                        add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(srv.id)) })
                        // "configurações" so pra quem manda: dono (o app já sabe pelo
                        // ownerId) ou MANAGE_SERVER — este último so e conhecido na
                        // constelação SELECIONADA, porque as permissões são buscadas
                        // uma vez por selecao (buscar de todas seria N requisicoes no
                        // boot). Clicar seleciona antes de abrir, entao a tela sempre
                        // abre com as permissões certas em mao.
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
                                "excluir ${srv.name}? apaga a constelação pra todos — não da pra desfazer.",
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
            // "+" colado logo abaixo do último servidor (rola junto com a lista,
            // não fica preso no rodape da rail).
            item(key = "create-server") { CreateServerButton(onCreateServer, onJoinInvite) }
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
    inviteFor?.let { srv ->
        InvitePeopleDialog(
            serverName = srv.name,
            inviteCode = srv.inviteCode,
            onAdd = { username, onResult -> onAddMember(srv.id, username, onResult) },
            onClose = { inviteFor = null },
        )
    }
}

// "+" da rail: abre um mini-menu (constelação / grupo / entrar com convite) e, ao
// escolher, um dialogo. Reaproveita o EditorialInputDialog (mesmo do criar canal).
@Composable
private fun CreateServerButton(
    onCreateServer: (name: String, isGroup: Boolean) -> Unit,
    onJoinInvite: (raw: String, onResult: (String?) -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var joinOpen by remember { mutableStateOf(false) }
    // null = fechado; false = constelação; true = grupo.
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
                        .popupReveal()
                        .widthIn(min = 170.dp, max = 230.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                ) {
                    CreateMenuRow(glyph = "✦", label = "criar constelação") { menuOpen = false; kind = false }
                    CreateMenuRow(icon = Lucide.Users, label = "criar grupo") { menuOpen = false; kind = true }
                    CreateMenuRow(icon = Lucide.Link, label = "entrar com convite") { menuOpen = false; joinOpen = true }
                }
            }
        }
    }
    if (joinOpen) {
        JoinByInviteDialog(onJoin = onJoinInvite, onClose = { joinOpen = false })
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

// ---- Sidebar (260dp): órbitas da constelação OU sussurros + painel do user ----

// #13: cabecalho da constelação = faixa de banner (imagem, animavel via AstraImage)
// com o nome em serifa por cima, legivel gracas a um scrim de baixo pra cima. Sem
// banner: um degrade sobrio do tema no lugar da imagem. So constelação usa isto;
// sussurros/descobrir seguem no header de texto.
//
// MESMO desenho da previa das configuracoes: mesmo ProfileBanner, mesma proporcao
// (ServerBannerAspect) e o mesmo enquadramento (positionY/scale) que o dono ajustou.
// Antes era um AstraImage cru com ContentScale.Crop numa altura fixa de 104dp — ou
// seja, outra proporcao E sem enquadramento nenhum, entao o que se via aqui nunca
// batia com o que a previa prometia.
// Interruptor do painel de membros, logo abaixo do banner.
//
// A borda ACESA quando o painel está aberto (pedido do dono) resolve o problema
// clássico do botão que alterna: sem estado visível, cada clique é um palpite.
// Com o painel aberto ela pulsa de leve — o brilho fica vivo em vez de ser só
// uma cor diferente —, e fechado volta pra borda apagada.
// Botao de icone da faixa abaixo do banner. Quadrado, so borda, sem fundo — o
// mesmo vocabulario dos botoes do compositor.
//
// `aceso` faz a borda PULSAR no accent. E o que resolve o problema classico do
// botao que alterna: sem estado visivel, cada clique vira palpite. Pulsar (e nao
// so trocar de cor) deixa o estado vivo em vez de ser mais um tom no escuro.
@Composable
private fun BotaoDaFaixa(
    icone: ImageVector,

    aceso: Boolean = false,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val reduzir = LocalReduceMotion.current
    // Respiro do brilho: 1.6s de ida e volta. Com "reduzir movimento" fica fixo
    // no meio — o estado continua legivel, sem nada se mexendo.
    val respiro = if (reduzir || !aceso) 0.5f else {
        val t = rememberInfiniteTransition(label = "faixaGlow")
        t.animateFloat(
            initialValue = 0.35f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = EaseOutSoft), RepeatMode.Reverse),
            label = "faixaGlowV",
        ).value
    }
    val corBorda = when {
        aceso -> Obsidian.accent.copy(alpha = 0.35f + 0.45f * respiro)
        hov -> Obsidian.borderMid
        else -> Obsidian.borderDim
    }
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, corBorda, RoundedCornerShape(9.dp))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(
            icone,
            tint = if (aceso || hov) Obsidian.accent else Obsidian.text3,
            size = 15.dp,
        )
    }
}

// A faixa logo abaixo do banner: nome da constelação, quantas pessoas ha, e as
// acoes (convidar, membros, configuracoes).
//
// O NOME MUDOU DE LUGAR. Vivia por cima da imagem, com um scrim escuro embaixo
// tentando salvar a leitura — e banner claro ganhava do scrim. Aqui ele fica sobre
// a obsidiana, sempre legivel, e de quebra a faixa deixa de ser dois botoes
// flutuando num vazio.
//
// A engrenagem so aparece pra quem manda. Ela existia so no menu de botao
// direito da rail — atalho invisivel pra quem nao sabe que ele existe.
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
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
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
                // O ponto e o unico sinal de vida da faixa: sem ele, "4 online" le
                // como numero de relatorio em vez de gente do outro lado.
                Box(Modifier.size(5.dp).clip(CircleShape).background(Obsidian.success))
                Spacer(Modifier.width(5.dp))
                Text(
                    "$online online · $membros " + if (membros == 1) "membro" else "membros",
                    style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        BotaoDaFaixa(Lucide.UserPlus, onClick = onConvidar)
        Spacer(Modifier.width(6.dp))
        BotaoDaFaixa(Lucide.Users, aceso = membrosAbertos, onClick = onToggleMembros)
        if (podeConfigurar) {
            Spacer(Modifier.width(6.dp))
            BotaoDaFaixa(Lucide.Settings, onClick = onAbrirConfig)
        }
    }
}

@Composable
private fun ServerHeaderBanner(srv: ServerDto) {
    Box(Modifier.fillMaxWidth().aspectRatio(ServerBannerAspect)) {
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
        // Scrim: transparente em cima, escurece pro void embaixo -> nome sempre le.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    1f to Obsidian.void.copy(alpha = 0.85f),
                ),
            ),
        )
        // O nome saiu daqui pra FaixaDaConstelacao logo abaixo (banner claro comia
        // o texto mesmo com scrim). O scrim FICA: ele ainda casa a base da imagem
        // com a obsidiana da faixa, sem o corte reto que havia antes.
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
    me: ProfileUserDto?,
    meFallback: String,
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
    onMoveChannelToCategory: (serverId: String, channelId: String, categoryId: String) -> Unit,
    onReorderCategories: (serverId: String, orderedIds: List<String>) -> Unit,
    onRenameChannel: (serverId: String, channelId: String, name: String) -> Unit,
    onDeleteChannel: (serverId: String, channelId: String) -> Unit,
    onMarkChannelRead: (channelId: String) -> Unit,
    mutedChannels: Set<String>,
    onToggleChannelMute: (channelId: String) -> Unit,
    onToggleChannelBot: (serverId: String, channelId: String, ligar: Boolean) -> Unit,
    onToggleCatBot: (serverId: String, categoryId: String, ligar: Boolean) -> Unit,
    membersOpen: Boolean,
    onToggleMembers: () -> Unit,
    canManageSelected: (String) -> Boolean,
    onOpenServerSettings: (String) -> Unit,
    // Checklist de 1o acesso, quando ativo — vive acima do rodape do usuário.
    firstSteps: (@Composable () -> Unit)? = null,
) {
    // Dialogo de nome (nova órbita / nova categoria / renomear) — centralizado na
    // janela. So o dono da constelação dispara pelos menus de botao direito.
    var chanDialog by remember { mutableStateOf<ChanDialog?>(null) }
    Column(Modifier.width(260.dp).fillMaxHeight().panelCard(Obsidian.raised, 0.20f)) {
        // Transicao ao trocar na rail (sussurros <-> constelação): header + lista
        // viram uma "pagina" que desliza de leve e faz fade. A pagina que sai
        // resolve o servidor pela PROPRIA selecao antiga (por isso a lista
        // inteira de servers entra aqui, não so o selecionado).
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
                // Botao direito no cabecalho da constelação (so o dono): criar órbita
                // solta ou uma categoria nova.
                if (srv != null) {
                    val isOwnerHere = srv.ownerId == myId
                    EditorialContextMenu(entries = {
                        buildList {
                            add(MenuEntry.Item("marcar tudo como lido", icon = Lucide.CheckCheck) {
                                srv.channels.forEach { if (it.id in unread) onMarkChannelRead(it.id) }
                            })
                            if (isOwnerHere) {
                                add(MenuEntry.Separator)
                                add(MenuEntry.Item("criar órbita", icon = Lucide.Plus) { chanDialog = ChanDialog.NewChannel(srv.id, null) })
                                add(MenuEntry.Item("criar categoria", icon = Lucide.FolderPlus) { chanDialog = ChanDialog.NewCategory(srv.id) })
                            }
                        }
                        // #13: faixa de banner no topo da constelação com o nome por cima.
                        // Substitui o header de texto simples (que segue nos sussurros/descobrir).
                    }) { ServerHeaderBanner(srv) }
                    FaixaDaConstelacao(
                        nome = srv.name,
                        membros = members.size,
                        // Eu conto sempre: estou olhando o app agora. Mesma regra do
                        // painel de membros — duas contagens divergentes na mesma
                        // tela seriam pior que qualquer imprecisao.
                        online = members.count { it.userId == myId || memberPresence[it.userId]?.let { p -> p != "OFFLINE" } == true },
                        membrosAbertos = membersOpen,
                        onToggleMembros = onToggleMembers,
                        onConvidar = { onConvidar(srv) },
                        podeConfigurar = isOwnerHere || canManageSelected(srv.id),
                        onAbrirConfig = { onOpenServerSettings(srv.id) },
                    )
                } else {
                    header()
                    HairRule()
                }

                Box(Modifier.weight(1f)) {
                    when {
                        loading -> SidebarSkeleton()
                        sel is Selection.Dms -> Column(Modifier.fillMaxSize()) {
                            FriendsNavRow(active = friendsOpen, onClick = onOpenFriends)
                            DmList(dms, servers, onToggleMute, onMarkRead, onCloseDm, activeChatId, unread, dmTyping, onOpenChat)
                        }
                        sel is Selection.Discover -> DiscoverSidebarMap()
                        else -> {
                            val orbits: @Composable () -> Unit = {
                                OrbitList(
                                    srv, activeChatId, unread, unreadCounts, members, voicePresence, myId, myVoiceChannelId,
                                    onOpenChat, onOpenVoice,
                                    onNewChannelInCat = { catId -> srv?.let { chanDialog = ChanDialog.NewChannel(it.id, catId) } },
                                    onRenameCat = { catId, cur -> srv?.let { chanDialog = ChanDialog.RenameCategory(it.id, catId, cur) } },
                                    onDeleteCat = { catId -> srv?.let { onDeleteCategory(it.id, catId) } },
                                    onReorderChannels = { ids -> srv?.let { onReorderChannels(it.id, ids) } },
                                    onMoveToCategory = { cid, catId -> srv?.let { onMoveChannelToCategory(it.id, cid, catId) } },
                                    onReorderCategories = { ids -> srv?.let { onReorderCategories(it.id, ids) } },
                                    onOpenChannelRename = { cid, cur -> srv?.let { chanDialog = ChanDialog.RenameChannel(it.id, cid, cur) } },
                                    onOpenChannelDelete = { cid, name -> srv?.let { chanDialog = ChanDialog.DeleteChannel(it.id, cid, name) } },
                                    onMarkChannelRead = onMarkChannelRead,
                                    mutedChannels = mutedChannels,
                                    onToggleChannelMute = onToggleChannelMute,
                                    onToggleChannelBot = { cid, on -> srv?.let { onToggleChannelBot(it.id, cid, on) } },
                                    onToggleCatBot = { catId, on -> srv?.let { onToggleCatBot(it.id, catId, on) } },
                                )
                            }
                            // #5: o mesmo menu do cabecalho também na AREA VAZIA da lista.
                            // Órbita/categoria são mais internos e consomem o clique, entao
                            // este aqui so dispara no vazio abaixo dos canais.
                            if (srv != null) {
                                val ownerHere = srv.ownerId == myId
                                EditorialContextMenu(entries = {
                                    buildList {
                                        add(MenuEntry.Item("marcar tudo como lido", icon = Lucide.CheckCheck) {
                                            srv.channels.forEach { if (it.id in unread) onMarkChannelRead(it.id) }
                                        })
                                        if (ownerHere) {
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

        // Checklist de 1o acesso, LOGO ACIMA do rodape. Ficava sobre o palco vazio,
        // onde tapava a arte e — pior — SUMIA assim que o usuário abria qualquer
        // órbita, justo enquanto ele cumpria os passos. Aqui ele acompanha o
        // caminho todo, e numa conta nova esta coluna esta vazia mesmo.
        firstSteps?.let { fs ->
            fs()
            Spacer(Modifier.height(8.dp))
        }

        // Rodape do usuário: cartao flutuante estilo Discord (bordas arredondadas
        // sobre a aurora). A propria borda do cartao já separa da lista — sem HairRule.
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
        is ChanDialog.DeleteChannel -> ConfirmDialog(
            text = "excluir #${d.name}? apaga as mensagens dela — não da pra desfazer.",
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
    unreadCounts: Map<String, Int>,
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
    onMoveToCategory: (channelId: String, categoryId: String) -> Unit,
    onReorderCategories: (orderedIds: List<String>) -> Unit,
    onOpenChannelRename: (channelId: String, current: String) -> Unit,
    onOpenChannelDelete: (channelId: String, name: String) -> Unit,
    onMarkChannelRead: (channelId: String) -> Unit,
    mutedChannels: Set<String>,
    onToggleChannelMute: (channelId: String) -> Unit,
    onToggleChannelBot: (channelId: String, ligar: Boolean) -> Unit,
    onToggleCatBot: (categoryId: String, ligar: Boolean) -> Unit,
) {
    if (server == null) return
    // Estrutura Discord: órbitas soltas primeiro, depois categorias colapsaveis.
    // Todos veem copiar ID / marcar lida na categoria; so o dono ganha gestao.
    val isOwner = server.ownerId == myId
    val clipboard = LocalClipboardManager.current
    var collapsedCats by remember(server.id) { mutableStateOf(setOf<String>()) }
    // Deriva a estrutura da sidebar (filtro/sort/groupBy) SO quando canais/categorias
    // mudam — não a cada recomposição. Sem isto, o poll de presença de voz (5s) e cada
    // mensagem em qualquer canal (state novo) refaziam tudo do zero. (Perf P0-2.)
    val catIds = remember(server.categories) { server.categories.map { it.id }.toSet() }
    val loose = remember(server.channels, catIds) {
        server.channels.filter { it.categoryId == null || it.categoryId !in catIds }.sortedBy { it.position }
    }
    val cats = remember(server.categories) { server.categories.sortedBy { it.position } }
    val byCat = remember(server.channels) { server.channels.groupBy { it.categoryId } }
    val looseIds = remember(loose) { loose.map { it.id } }
    // Estado do drag de reordenacao (uma instancia por constelação aberta).
    val drag = remember(server.id) { ChannelDragState() }
    // Acoes do botao-direito da órbita (serverId já embutido nas lambdas de cima).
    val chMenu = ChannelMenu(
        isOwner, mutedChannels, onMarkChannelRead, onOpenChannelRename, onOpenChannelDelete, onToggleChannelMute,
        // Herança resolvida aqui, uma vez: órbita decide; se não decidiu, a
        // categoria; se nem ela, a bot atende.
        botAtende = { ch ->
            ch.botEnabled ?: server.categories.find { it.id == ch.categoryId }?.botEnabled ?: true
        },
        onToggleBot = onToggleChannelBot,
    )

    // Cascata (F6): a posição corrida na lista decide o atraso de entrada.
    // Os indices são computados no escopo do DSL (sincrono e deterministico);
    // as lambdas dos itens so capturam constantes.
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
        itemsIndexed(loose, key = { _, ch -> ch.id }) { i, ch ->
            CascadeIn(i, server.id) {
                OrbitEntry(
                    ch, ch.id == activeChatId, ch.id in unread, unreadCounts[ch.id] ?: 0,
                    members, voicePresence, myId, myVoiceChannelId, onOpenChat, onOpenVoice,
                    dragCtx = if (isOwner) ChannelDragCtx(drag, "loose", i, loose.size, looseIds, onReorderChannels, onMoveToCategory) else null,
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
            // Colapsada ainda mostra a ativa e as não lidas (comportamento Discord).
            val visible =
                if (collapsed) channels.filter { it.id == activeChatId || it.id in unread }
                else channels
            // A categoria vira UM item medido: header + órbitas na mesma Column, pra (1)
            // registrar os bounds da categoria em coords de janela (hit-test do drag) e (2)
            // desenhar a MOLDURA da hitbox quando uma órbita de FORA paira por cima.
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
                                dragCtx = if (isOwner) CategoryDragCtx(drag, catIndex, orderedCatIds, onReorderCategories) else null,
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
                                if (isOwner) {
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
                                message = "excluir a categoria ${cat.name}? não dá pra desfazer.",
                                confirmLabel = "excluir",
                                onConfirm = { onDeleteCat(cat.id) },
                                onDismiss = { confirmDelCat = false },
                            )
                        }
                    }
                    visible.forEachIndexed { i, ch ->
                    // POR QUE o indice aqui e LOCAL da categoria, e nao a linha da
                    // lista inteira: a cascata so anima os primeiros CASCADE_MAX
                    // itens (senao o ultimo de uma lista longa entraria segundos
                    // depois). Com o indice global, toda orbita a partir da 15a
                    // linha caia fora do limite e aparecia SECA — e era exatamente
                    // o que se via ao abrir uma categoria mais pra baixo.
                    // Local: uma categoria raramente passa de 14 orbitas, entao
                    // todas animam. O lugar da categoria na lista vira um atraso de
                    // largada, limitado a ~150ms pra que abrir no clique continue
                    // parecendo resposta, e nao espera.
                    // A chave inclui o colapso: reabrir toca a entrada de novo.
                    // E o key(ch.id) faz o estado da animacao seguir a ORBITA, nao a
                    // posicao — sem ele, colapsada->aberta reaproveitava o estado da
                    // vizinha e uma entrava pronta enquanto a outra animava.
                        key(ch.id) {
                        CascadeIn(
                            i,
                            "${server.id}:${cat.id}:$collapsed",
                            startDelayMs = minOf(headerRow, 6).toLong() * 26L,
                        ) {
                            OrbitEntry(
                                ch, ch.id == activeChatId, ch.id in unread, unreadCounts[ch.id] ?: 0,
                                members, voicePresence, myId, myVoiceChannelId, onOpenChat, onOpenVoice,
                                // Reordena so quando aberta (indice do visivel == indice real).
                                dragCtx = if (isOwner && !collapsed)
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

// ---- Drag pra reordenar canais (so o dono). Estilo "bolha leve": a órbita vira um
// circulo flutuante que segue o cursor; ao soltar, faz fade e o canal reaparece na
// nova posição. Reorder dentro da MESMA secao (soltos, ou de uma categoria). ----

private class ChannelDragState {
    var id by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var isVoice by mutableStateOf(false)
    var section by mutableStateOf<String?>(null)
    var fromIndex by mutableStateOf(-1)
    var targetIndex by mutableStateOf(-1)
    var windowPos by mutableStateOf(Offset.Zero)
    var fadingOut by mutableStateOf(false)
    // Hitbox: categoria sob o cursor durante o arrasto (pro realce + drop cross-categoria).
    // catBounds = bounds de cada categoria em coords de JANELA, alimentado no layout.
    var hoverCat by mutableStateOf<String?>(null)
    val catBounds = mutableStateMapOf<String, Rect>()
    // Arrastando uma CATEGORIA (cabecalho) em vez de uma órbita — muda o ícone da bolha
    // e a logica de drop (reordena categorias).
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

// Long-press pega a órbita; o arrasto move a bolha (windowPos) e calcula o slot alvo
// pela distancia percorrida / altura do item. Soltar reordena a secao. Chamado SEMPRE
// (ctx nulo = no-op) pra não variar a contagem de composables entre recomposicoes.
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
                    // Categoria sob o cursor: realca a hitbox e decide drop cross-categoria.
                    d.hoverCat = d.catBounds.entries.firstOrNull { it.value.contains(d.windowPos) }?.key
                },
                onDragEnd = {
                    if (d.id == ch.id) {
                        val srcCat = if (ctx.section.startsWith("cat:")) ctx.section.removePrefix("cat:") else null
                        val over = d.hoverCat
                        if (over != null && over != srcCat) {
                            // Soltou POR CIMA de outra categoria -> move pra dentro dela.
                            ctx.onMoveToCategory(ch.id, over)
                        } else if (d.targetIndex in 0 until ctx.sectionSize && d.targetIndex != d.fromIndex) {
                            // Reordena dentro da mesma secao.
                            val list = ctx.orderedIds.toMutableList()
                            list.add(d.targetIndex, list.removeAt(d.fromIndex))
                            ctx.onReorder(list)
                        }
                        d.fadingOut = true // dispara o fade da bolha
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

// Long-press no cabecalho pega a CATEGORIA; arrastar reordena entre as outras (hit-test
// pela drag.catBounds já registrada). Soltar reordena. Chamado SEMPRE (ctx nulo = no-op)
// pra não variar a contagem de composables. Convive com o clique (tap = colapsar).
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
                    // Categoria sob o cursor -> indice alvo (usa os bounds já medidos).
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
                        // Leitura diferida (draw-phase): não recompoe, so re-desenha a camada.
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
                LIcon(if (d.isCategory) Lucide.Folder else if (voice) Lucide.Volume2 else Lucide.Hash, tint = Obsidian.accent, size = 20.dp)
            }
            Spacer(Modifier.height(5.dp))
            // O nome so faz fade junto (não esparrama — texto esticado fica estranho).
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

// Órbita + (se for de voz) a lista de quem está na sala logo abaixo, indentada
// — estilo Discord, pra quem esta de fora saber que tem gente na call.
@Composable
private fun OrbitEntry(
    ch: ChannelDto,
    active: Boolean,
    unread: Boolean,
    unreadCount: Int,
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
        // Botao direito na órbita: marcar lido / copiar ID (todos) + renomear/excluir (dono).
        EditorialContextMenu(entries = {
            buildList {
                if (unread) add(MenuEntry.Item("marcar como lido", icon = Lucide.Check) { menu.onMarkRead(ch.id) })
                add(MenuEntry.Item(if (ch.id in menu.mutedChannels) "reativar órbita" else "silenciar órbita", icon = if (ch.id in menu.mutedChannels) Lucide.Bell else Lucide.BellOff) { menu.onToggleMute(ch.id) })
                add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(ch.id)) })
                if (menu.isOwner) {
                    add(MenuEntry.Separator)
                    val temBot = menu.botAtende(ch)
                    add(
                        MenuEntry.Item(
                            if (temBot) "silenciar a bot aqui" else "deixar a bot atender aqui",
                            icon = if (temBot) Lucide.BotOff else Lucide.Bot,
                        ) { menu.onToggleBot(ch.id, !temBot) },
                    )
                    add(MenuEntry.Item("renomear", icon = Lucide.Pencil) { menu.onRename(ch.id, ch.name) })
                    add(MenuEntry.Item("excluir órbita", danger = true, icon = Lucide.Trash2) { confirmDelCh = true })
                }
            }
        }) {
            OrbitItem(ch, active, unread, unreadCount, onOpenChat, onOpenVoice, dragCtx)
            if (confirmDelCh) ConfirmPopup(
                message = "excluir a órbita ${ch.name}? apaga as mensagens dela — não dá pra desfazer.",
                confirmLabel = "excluir",
                onConfirm = { menu.onDelete(ch.id, ch.name) },
                onDismiss = { confirmDelCh = false },
            )
        }
        if (ch.type == "VOICE") {
            // Presenca do poll + eu otimista (aparece na hora que entro, sem
            // esperar o próximo ciclo de ~5s do backend).
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
private fun CategoryHeader(name: String, collapsed: Boolean, onToggle: () -> Unit, dragCtx: CategoryDragCtx? = null) {
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
            // Long-press + arrastar reordena as categorias (so o dono; ctx nulo = no-op).
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

// ---- Criacao de órbita/categoria (dono) ----

private sealed interface ChanDialog {
    data class NewChannel(val serverId: String, val categoryId: String?) : ChanDialog
    data class NewCategory(val serverId: String) : ChanDialog
    data class RenameCategory(val serverId: String, val categoryId: String, val current: String) : ChanDialog
    data class RenameChannel(val serverId: String, val channelId: String, val current: String) : ChanDialog
    data class DeleteChannel(val serverId: String, val channelId: String, val name: String) : ChanDialog
}

// Botao direito que agrupa as ações de uma órbita, montado no OrbitList (já sabe
// se e dono) e passado adiante pro OrbitEntry. serverId já fica embutido nas lambdas.
private class ChannelMenu(
    val isOwner: Boolean,
    val mutedChannels: Set<String>,
    val onMarkRead: (channelId: String) -> Unit,
    val onRename: (channelId: String, current: String) -> Unit,
    val onDelete: (channelId: String, name: String) -> Unit,
    val onToggleMute: (channelId: String) -> Unit,
    // A bot atende nesta órbita? (já vem com a herança da categoria resolvida)
    val botAtende: (ChannelDto) -> Boolean,
    val onToggleBot: (channelId: String, ligar: Boolean) -> Unit,
)

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
                    // Nome livre (decisao do dono): maiuscula/acento/simbolo permitidos,
                    // so limita o tamanho. Vale pra órbita e categoria.
                    onValueChange = { text = it.take(50) },
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
    unreadCount: Int,
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
    // A órbita arrastada fica esmaecida no lugar (a bolha e a copia "levantada").
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
                // Órbita de voz abre a sala (sonda V1); texto abre o chat.
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
            // Badge de não-lidas (so quando NAO esta aberto): circulo ambar 99+.
            if (!active && unreadCount > 0) {
                Spacer(Modifier.width(6.dp))
                UnreadCountBadge(unreadCount)
            }
        }
        if (isUnread) UnreadPill(Modifier.align(Alignment.CenterStart))
        // Marca de insercao do drag: linha accent no topo (subindo) ou na base
        // (descendo) da órbita que esta no slot alvo — nunca na propria arrastada.
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

// Badge de não-lidas: circulo ambar com o numero (cap 99+). Numero escuro
// (Obsidian.base) pra contraste no ambar — marca da constelação, não vermelho.
@Composable
private fun UnreadCountBadge(count: Int) {
    Box(
        Modifier
            .height(18.dp)
            .widthIn(min = 18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Obsidian.accent)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = TextStyle(color = Obsidian.base, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

// Traco accent na borda esquerda do item — marca de não-lida (estilo Discord,
// tokens obsidiana).
@Composable
private fun UnreadPill(modifier: Modifier = Modifier) {
    // Pulso sutil (F6): o marcador "respira" devagar pra puxar o olho sem gritar.
    // Movimento reduzido / janela em segundo plano: fica aceso e parado. O valor e
    // lido DENTRO do graphicsLayer — antes o .value saia no corpo e recompunha o
    // item a cada frame, um clock por não-lida. (Auditoria de movimento, achado #2.)
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
    onOpenChat: (ChatTarget) -> Unit,
) {
    if (dms.isEmpty()) {
        EmptyHint("nenhum sussurro ainda")
        return
    }
    val clipboard = LocalClipboardManager.current
    // Alvos dos itens de menu que abrem tela (null = fechado).
    var profileFor by remember { mutableStateOf<String?>(null) }
    var inviteFor by remember { mutableStateOf<ConversationDto?>(null) }
    // Amizades carregadas UMA vez: o menu precisa do id da amizade pra desfazer, e
    // a conversa so carrega o id do usuário. Sem amizade com a pessoa, o item nem
    // aparece — melhor do que oferecer uma ação que vai falhar.
    val friendApi = remember { GlobalContext.get().get<FriendApi>() }
    var friendships by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Quem eu bloqueei: o menu precisa saber pra oferecer "desbloquear" em vez de
    // "bloquear" de novo (bloquear duas vezes nao faz nada e confunde).
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Obsidian.base)
                        .border(1.dp, Obsidian.borderMid.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A lupa acende quando o campo tem texto: parada, ela e so um
                    // rotulo; acesa, confirma que o filtro esta valendo.
                    LIcon(
                        Lucide.Search,
                        tint = if (query.isEmpty()) Obsidian.text3 else Obsidian.accent,
                        size = 13.dp,
                    )
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("encontrar conversa", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
                        }
                        inner()
                    }
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
                    u?.id?.let { uid ->
                        add(MenuEntry.Item("ver perfil", icon = Lucide.User) { profileFor = uid })
                    }
                    if (isUnread) add(MenuEntry.Item("marcar como lida", icon = Lucide.Check) { onMarkRead(conv.id) })
                    add(MenuEntry.Separator)
                    // So faz sentido convidar se eu tenho pra onde convidar.
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
                    // "desfazer amizade" so aparece se existe amizade — oferecer uma
                    // ação que vai dar erro e pior do que não oferecer.
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
                                            // O backend ja desfez a amizade e escondeu a
                                            // conversa; a tela acompanha na hora em vez de
                                            // esperar o proximo carregamento.
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
                        // 9dp e nao 6dp: sem a linha, o respiro e o unico separador,
                        // e 6dp deixava os cards colados demais pra isso funcionar.
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Hover na LINHA inteira dispara o anel 360 em volta da foto
                    // (não so o hover direto na foto) -> a hitbox toda fica viva.
                    DesktopAvatar(u?.avatarUrl, name, 28, externalHover = hovered)
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
                // Sem divisoria. Ela era um traco de borda a borda no rodape de cada
                // card — padrao de TABELA, e o olho passa a ler a coluna como grade
                // em vez de gente. Quem separa agora e o respiro (o vertical padding
                // do Row dobrou) e o hover, que acende o fundo e desenha o limite do
                // card exatamente quando ele importa: na hora de clicar.
            }
            }
            }
            }
        }
    }

    // Telas abertas pelo menu do sussurro. Ficam FORA da LazyColumn de proposito:
    // a linha some da composicao ao rolar, e a tela iria junto no meio do uso.
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

// Escolher PRA ONDE convidar. O menu de contexto não tem submenu generico, e um
// dialogo aqui e mais honesto que uma lista escondida: da pra ver o ícone e o nome
// de cada constelação antes de decidir. Adiciona pelo @usuario (a pessoa entra na
// hora) — a mesma rota do "convidar pessoas" da rail.
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
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
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
                    style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
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

// ---- Palco central ----

@Composable
private fun Stage(
    server: ServerDto?,
    chat: ChatTarget?,
    voiceChannel: ChannelDto?,
    // Engine so quando a sala do palco E a que você entrou; null = antessala.
    voiceEngine: VoiceEngine?,
    voicePresence: List<String>,
    onJoinVoice: () -> Unit,
    onLeaveVoice: () -> Unit,
    createChatVm: (ChatTarget) -> ChatVm,
    members: List<ServerMemberDto>,
    me: ProfileUserDto?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onStartDm: (String, String) -> Unit,
    showDiscover: Boolean,
    onDiscoverJoined: (String) -> Unit,
    // IDs dos servidores que já sou membro — o Discover troca "entrar" por "você já
    // está aqui" nesses cards.
    joinedServerIds: Set<String> = emptySet(),
    showFriends: Boolean,
    modifier: Modifier = Modifier,
) {
    // Cartao do palco: onde vive o texto do chat, entao alpha um tico maior que
    // os outros paineis pra leitura (aurora aparece, mas não briga com a mensagem).
    Column(modifier.fillMaxHeight().panelCard(Obsidian.base, 0.32f)) {
        // Amigos ocupa o palco inteiro (cabecalho + abas proprios).
        if (showFriends) {
            FriendsView(onStartDm, Modifier.fillMaxSize())
            return@Column
        }
        // Descobrir ocupa o palco inteiro (tem cabecalho + busca proprios).
        if (showDiscover) {
            DiscoverView(onDiscoverJoined, joinedIds = joinedServerIds, modifier = Modifier.fillMaxSize())
            return@Column
        }
        // Top bar do palco. ESCONDIDO em QUALQUER tela vazia (nada aberto) — constelação OU
        // sussurros: ali o palco vira um componente so, com a animação central de fato no
        // centro (e, em constelação, os membros já na lateral). Estados com nome (órbita ou
        // sussurro aberto, voz) mantem o top bar. O botao de membros saiu.
        val bareLanding = chat == null && voiceChannel == null
        if (!bareLanding) {
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
                Box(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            voiceChannel != null -> voiceChannel.name
                            chat is ChatTarget.Channel -> chat.title
                            chat is ChatTarget.Dm -> "sussurro · ${chat.title}"
                            server != null -> "constelação · ${server.name}"
                            else -> "sussurros"
                        },
                        style = TextStyle(
                            color = if (chat != null || voiceChannel != null) Obsidian.text1 else Obsidian.text3,
                            fontSize = 13.sp,
                        ),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
                    )
                }
            }
            HairRule()
        }

        // Sala de voz ocupa o palco. Sem engine = você abriu a sala mas ainda não
        // entrou -> antessala com quem esta la e o botao verde.
        if (voiceChannel != null) {
            if (voiceEngine != null) VoiceView(voiceChannel, members, me, voiceEngine, onLeaveVoice)
            else VoiceLobby(voiceChannel, members, voicePresence, onJoinVoice)
            return@Column
        }

        // Troca de conversa em DOIS TEMPOS (ver TrocaDePagina.kt). Antes era um
        // AnimatedContent com fade cruzado, e as duas conversas — cada uma com seu
        // ChatVm, sua lista e suas imagens — desenhavam no mesmo frame. Era esse o
        // engasgo. Agora a antiga apaga primeiro e a nova so e composta depois.
        TrocaDePagina(
            alvo = chat,
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            if (target != null) {
                val chatVm = remember { createChatVm(target) }
                DisposableEffect(Unit) { onDispose { chatVm.dispose() } }
                // Heranca: orbita decide; se nao decidiu, a categoria; se nem ela,
                // fica ligado. Resolvido aqui porque o cliente ja tem as duas listas
                // na mao — pedir de novo ao servidor seria round-trip por nada.
                val botAqui = remember(target.id, server) {
                    val ch = server?.channels?.find { it.id == target.id }
                    val cat = server?.categories?.find { it.id == ch?.categoryId }
                    // Sussurro não tem órbita nem categoria: a bot atende sempre.
                    ch?.botEnabled ?: cat?.botEnabled ?: true
                }
                ChatView(
                    target, chatVm, onStartDm,
                    botAqui = botAqui,
                    serverId = server?.id,
                    membros = if (target is ChatTarget.Channel) members else emptyList(),
                )
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
    presence: Map<String, String>,
    myId: String?,
    serverId: String?,
    isOwner: Boolean,
    onStartDm: (String, String) -> Unit,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
) {
    // Agrupado por cargo hoist (membro no cargo mais alto que "separa"; resto em
    // MEMBROS). Dentro de cada secao: online antes de offline. Recalcula so quando
    // a lista ou a presenca muda — não a cada recomposicao.
    val rows = remember(members, presence, myId) { buildMemberRows(members, presence, myId) }
    Column(Modifier.width(240.dp).fillMaxHeight().panelCard(Obsidian.raised, 0.20f)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            items(rows, key = { row -> row.key }) { row ->
                when (row) {
                    is MemberPanelRow.Header -> MemberSectionHeader(row.label, row.count, row.iconUrl)
                    is MemberPanelRow.Person -> MemberRow(
                        m = row.m,
                        online = row.online,
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

// Linha achatada do painel: cabecalho de secao (cargo/MEMBROS) ou pessoa. Pre-
// computado fora da composicao pra o LazyColumn so re-emitir quando muda.
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
    // Eu SEMPRE conto como online (estou olhando o app agora): a presenca do proprio
    // usuário nunca chega via socket (o broadcast do connect vai so pros OUTROS) e o
    // snapshot inicial pode ter pego antes do socket subir. Sem isso meu nome ficava
    // apagado mesmo online. Os demais vem da presenca real (o heartbeat a mantem viva).
    fun online(uid: String) = uid == myId || presence[uid]?.let { it != "OFFLINE" } == true
    fun nameOf(m: ServerMemberDto) = (m.user.displayName ?: m.user.username).lowercase()

    // membro -> cargo hoist mais alto (position maior). Sem cargo hoist = "" (MEMBROS).
    val roleById = HashMap<String, MemberRoleDto>()
    val buckets = LinkedHashMap<String, MutableList<ServerMemberDto>>()
    for (m in members) {
        val r = m.roles.filter { it.hoist }.maxByOrNull { it.position }
        val key = r?.id ?: ""
        if (r != null) roleById[key] = r
        buckets.getOrPut(key) { mutableListOf() }.add(m)
    }
    // Secoes: cargos hoist por position desc; MEMBROS ("") sempre por último.
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
    // Online = nome na cor do cargo (topColor). Offline = esmaecido, sem cor.
    val nameColor = if (online) (memberRoleColor(m.topColor) ?: Obsidian.text2) else Obsidian.text3.copy(alpha = 0.65f)
    val avatarAlpha = if (online) 1f else 0.4f
    // Cascata ao abrir o painel (F6) + linha clicavel = card de perfil (F3)
    // + botao direito = menu (sussurro/copiar ID; expulsar/banir do dono).
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
                    // kick usa o ID da MEMBERSHIP (serverMembers.id = m.id);
                    // ban usa o userId no corpo. Mandar userId no kick dava
                    // 404 (rota casa por serverMembers.id) — bug do "não expulsa".
                    onConfirm = { if (act == "ban") onBan(m.userId) else onKick(m.id) },
                    onDismiss = { confirmMember = null },
                )
            }
            ProfileAnchor(m.userId, isMe = isMe, onStartDm = onStartDm) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.graphicsLayer { alpha = avatarAlpha }) {
                        DesktopAvatar(m.user.avatarUrl, name, 26)
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = name,
                        style = TextStyle(color = nameColor, fontSize = 13.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// "#rrggbb" -> Color; invalido/nulo = null (a UI cai no cinza).
private fun memberRoleColor(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
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
