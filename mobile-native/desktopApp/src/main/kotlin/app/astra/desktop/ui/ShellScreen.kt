package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import app.astra.desktop.auth.Session
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.ChatVm
import app.astra.desktop.shell.Selection
import app.astra.desktop.shell.ShellVm
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.Obsidian
import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ChannelActivityEventDto
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    val socket = remember { koin.get<DesktopSocket>() }
    val vm = remember {
        ShellVm(
            scope, koin.get<ServerApi>(), koin.get<UserApi>(), koin.get<DmApi>(), koin.get<SessionStore>(),
            socket, koin.get<Json>(), session.userId,
        )
    }
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { socket.connect() }

    // Toast na bandeja quando chega mensagem com a janela fechada/minimizada.
    // DM tem autor+conteudo (salas todas joinadas); canal so tem o id do
    // channel_activity -> notificacao generica com o nome da orbita.
    val json = remember { koin.get<Json>() }
    LaunchedEffect(Unit) {
        launch {
            socket.newDm.collect { raw ->
                if (!windowHidden()) return@collect
                val msg = runCatching { json.decodeFromString<DmMessageDto>(raw) }.getOrNull() ?: return@collect
                if (msg.senderId == session.userId) return@collect
                if (vm.state.value.dms.any { it.id == msg.conversationId && it.muted }) return@collect
                val name = msg.author?.displayName ?: msg.author?.username ?: "alguem"
                notify(name, msg.content.ifBlank { "anexo" }.take(120))
            }
        }
        launch {
            socket.channelActivity.collect { raw ->
                if (!windowHidden()) return@collect
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
            )
        }
    }

    val hazeState = remember { HazeState() }
    Box(Modifier.fillMaxSize()) {
        // Aurora viva atras do shell inteiro (decisao do dono). Camada propria
        // (graphicsLayer): so ela invalida por frame — os paineis translucidos
        // por cima nao redesenham com o shader. hazeSource: sidebar/membros
        // sao vidro de verdade (blur backdrop) por cima dela.
        Box(
            Modifier
                .matchParentSize()
                .hazeSource(hazeState)
                .graphicsLayer {}
                .auroraBackground(),
        )
        Row(Modifier.fillMaxSize()) {
        Rail(
            servers = state.servers,
            selection = state.selection,
            onSelect = vm::select,
            onLeaveServer = vm::leaveServer,
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
            hazeState = hazeState,
            onOpenChat = vm::openChat,
            onOpenVoice = vm::openVoice,
            onToggleMute = vm::toggleDmMute,
            onMarkRead = vm::markDmRead,
            onEditedProfile = vm::refreshMe,
            onLogout = onLogout,
        )
        Stage(
            state.selectedServer,
            chat = chat,
            voiceChannel = state.voiceChannel,
            onLeaveVoice = vm::leaveVoice,
            createChatVm = createChatVm,
            membersOpen = state.membersOpen,
            onToggleMembers = vm::toggleMembers,
            loading = state.loading,
            error = state.error,
            onRetry = vm::load,
            onStartDm = vm::startDm,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = state.selection is Selection.Server && state.membersOpen,
            enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
            exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120)),
        ) {
            MembersPanel(state.members, session.userId, vm::startDm, hazeState)
        }
        }
    }
}

// Vidro obsidiana: blur do fundo (aurora) + tint do painel por cima.
private fun glassStyle(base: Color) = HazeStyle(
    backgroundColor = base,
    tint = HazeTint(base.copy(alpha = 0.78f)),
    blurRadius = 24.dp,
    noiseFactor = 0f,
)

// ---- Rail de constelacoes (72dp) ----

@Composable
private fun Rail(
    servers: List<ServerDto>,
    selection: Selection,
    onSelect: (Selection) -> Unit,
    onLeaveServer: (String) -> Unit,
) {
    Column(
        // Translucido: a aurora vaza sutil por baixo (0.85 mantem os icones legiveis).
        modifier = Modifier.width(72.dp).fillMaxHeight().background(Obsidian.void.copy(alpha = 0.85f)),
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(servers, key = { it.id }) { srv ->
                // Botao direito na constelacao: sair (com confirmacao) (F4).
                var confirmLeave by remember(srv.id) { mutableStateOf(false) }
                EditorialContextMenu(entries = {
                    listOf(MenuEntry.Item("sair da constelacao", danger = true) { confirmLeave = true })
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
        }
    }
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
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .hoverable(interaction)
            .clickable(onClick = onClick),
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
    hazeState: HazeState,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
    onToggleMute: (ConversationDto) -> Unit,
    onMarkRead: (String) -> Unit,
    onEditedProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(Modifier.width(260.dp).fillMaxHeight().hazeEffect(hazeState, glassStyle(Obsidian.raised))) {
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
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = if (sel is Selection.Dms) "Sussurros" else srv?.name ?: "",
                        style = TextStyle(
                            color = Obsidian.text1, fontSize = 16.sp,
                            fontFamily = DmSerif,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HairRule()

                Box(Modifier.weight(1f)) {
                    when {
                        loading -> SidebarSkeleton()
                        sel is Selection.Dms ->
                            DmList(dms, onToggleMute, onMarkRead, activeChatId, unread, dmTyping, onOpenChat)
                        else -> OrbitList(srv, activeChatId, unread, onOpenChat, onOpenVoice)
                    }
                }
            }
        }

        // Rodape do usuario (F2): avatar com anel + status, perfil no clique.
        HairRule()
        UserFooter(me = me, fallbackName = meFallback, onEdited = onEditedProfile, onLogout = onLogout)
    }
}

@Composable
private fun OrbitList(
    server: ServerDto?,
    activeChatId: String?,
    unread: Set<String>,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
) {
    if (server == null) return
    // Estrutura Discord: orbitas soltas primeiro, depois categorias colapsaveis.
    var collapsedCats by remember(server.id) { mutableStateOf(setOf<String>()) }
    val catIds = server.categories.map { it.id }.toSet()
    val loose = server.channels.filter { it.categoryId == null || it.categoryId !in catIds }.sortedBy { it.position }
    val cats = server.categories.sortedBy { it.position }
    val byCat = server.channels.groupBy { it.categoryId }

    // Cascata (F6): a posicao corrida na lista decide o atraso de entrada.
    // Os indices sao computados no escopo do DSL (sincrono e deterministico);
    // as lambdas dos itens so capturam constantes.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
        itemsIndexed(loose, key = { _, ch -> ch.id }) { i, ch ->
            CascadeIn(i, server.id) {
                OrbitItem(ch, ch.id == activeChatId, ch.id in unread, onOpenChat, onOpenVoice)
            }
        }
        var offset = loose.size
        cats.forEach { cat ->
            val channels = byCat[cat.id].orEmpty().sortedBy { it.position }
            val headerRow = offset
            item(key = "cat-${cat.id}") {
                CascadeIn(headerRow, server.id) {
                    CategoryHeader(
                        name = cat.name,
                        collapsed = cat.id in collapsedCats,
                        onToggle = {
                            collapsedCats =
                                if (cat.id in collapsedCats) collapsedCats - cat.id else collapsedCats + cat.id
                        },
                    )
                }
            }
            // Colapsada ainda mostra a ativa e as nao lidas (comportamento Discord).
            val visible =
                if (cat.id in collapsedCats) channels.filter { it.id == activeChatId || it.id in unread }
                else channels
            itemsIndexed(visible, key = { _, ch -> ch.id }) { i, ch ->
                CascadeIn(headerRow + 1 + i, server.id) {
                    OrbitItem(ch, ch.id == activeChatId, ch.id in unread, onOpenChat, onOpenVoice)
                }
            }
            offset = headerRow + 1 + visible.size
        }
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
        Text(
            text = "▾",
            style = TextStyle(color = tint, fontSize = 9.sp),
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

@Composable
private fun OrbitItem(
    ch: ChannelDto,
    active: Boolean,
    unread: Boolean,
    onOpenChat: (ChatTarget) -> Unit,
    onOpenVoice: (ChannelDto) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val isUnread = !active && unread
    val itemBg by animateColorAsState(
        if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent,
        tween(120),
    )
    Box(Modifier.fillMaxWidth()) {
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
            Text(
                text = if (ch.type == "VOICE") "◉" else "#",
                style = TextStyle(color = if (ch.type == "VOICE") Obsidian.accent else Obsidian.text3, fontSize = 13.sp),
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
    }
}

// Traco accent na borda esquerda do item — marca de nao-lida (estilo Discord,
// tokens obsidiana).
@Composable
private fun UnreadPill(modifier: Modifier = Modifier) {
    // Pulso sutil (F6): o marcador "respira" devagar pra puxar o olho sem gritar.
    val transition = rememberInfiniteTransition()
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseOutSoft),
            repeatMode = RepeatMode.Reverse,
        ),
    )
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
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("nenhum sussurro ainda", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
        }
        return
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
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Obsidian.base)
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
    onLeaveVoice: () -> Unit,
    createChatVm: (ChatTarget) -> ChatVm,
    membersOpen: Boolean,
    onToggleMembers: () -> Unit,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onStartDm: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 0.92: o palco e onde vive o texto — translucidez mais conservadora.
    Column(modifier.fillMaxHeight().background(Obsidian.base.copy(alpha = 0.92f))) {
        // Top bar do palco
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    voiceChannel != null -> "◉ ${voiceChannel.name}"
                    chat is ChatTarget.Channel -> "# ${chat.title}"
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
                    Text(
                        "☰",
                        style = TextStyle(color = if (membersOpen) Obsidian.accent else Obsidian.text3, fontSize = 13.sp),
                    )
                }
            }
        }
        HairRule()

        // Sala de voz ocupa o palco (V1); senao, chat/placeholder com fade.
        if (voiceChannel != null) {
            VoiceView(voiceChannel, onLeaveVoice)
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
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "✦",
                                style = TextStyle(color = Obsidian.borderMid, fontSize = 40.sp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (server != null) "escolha uma orbita — a conversa chega na proxima fatia"
                                else "escolha um sussurro — a conversa chega na proxima fatia",
                                style = TextStyle(color = Obsidian.text3, fontSize = 13.sp),
                            )
                        }
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
    onStartDm: (String, String) -> Unit,
    hazeState: HazeState,
) {
    Column(Modifier.width(240.dp).fillMaxHeight().hazeEffect(hazeState, glassStyle(Obsidian.raised))) {
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
                // Cascata ao abrir o painel (F6) + linha clicavel = card de perfil (F3).
                CascadeIn(i, members.size) {
                    ProfileAnchor(m.userId, isMe = m.userId == myId, onStartDm = onStartDm) {
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

// ---- Pecinhas ----

@Composable
fun HairRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Obsidian.borderDim.copy(alpha = 0.6f)))
}
