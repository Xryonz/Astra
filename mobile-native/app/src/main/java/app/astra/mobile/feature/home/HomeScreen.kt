package app.astra.mobile.feature.home

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquarePlus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.UserPlus
import app.astra.mobile.BuildConfig
import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.feature.server.domain.model.Channel
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.AstraCopy
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.ListSkeleton
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.OptionRow
import app.astra.mobile.ui.components.Reveal
import app.astra.mobile.ui.components.StatusDot
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.EaseSpring
import app.astra.mobile.ui.theme.astraColors
import coil.compose.AsyncImage

@Composable
fun HomeScreen(
    onOpenChannel: (id: String, name: String) -> Unit,
    onOpenServerEdit: (serverId: String) -> Unit,
    onOpenJoin: () -> Unit,
    onOpenDm: (id: String, name: String) -> Unit,
    onOpenDms: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onJoinVoice: (channelId: String, name: String, serverId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Popup de criar: chooser (servidor/grupo/convite) -> dialog de forjar.
    var createChooser by remember { mutableStateOf(false) }
    var showForge by remember { mutableStateOf(false) }
    var forgeAsGroup by remember { mutableStateOf(false) }
    // Sheet do perfil (sobe de baixo ao tocar no bottom bar).
    var profileSheet by remember { mutableStateOf(false) }

    // DM aberta pelo FAB -> navega pro chat e fecha o dialog.
    LaunchedEffect(Unit) {
        viewModel.opened.collect { conv ->
            showDialog = false
            onOpenDm(conv.conversationId, conv.otherName)
        }
    }

    // Constelacao forjada -> fecha o dialog e abre ela inline (painel de canais).
    LaunchedEffect(Unit) {
        viewModel.serverCreated.collect { srv ->
            showForge = false
            viewModel.selectServer(srv.id)
        }
    }

    // Voltou pra Home (ex: depois de editar o perfil) -> atualiza o bottom bar.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshProfile()
                viewModel.refreshServers()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val dms = remember(state.dms, query) {
        if (query.isBlank()) state.dms
        else state.dms.filter { it.otherName.contains(query.trim(), ignoreCase = true) }
    }

    CosmicBackground {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().statusBarsPadding()) {
                val selected = state.servers.find { it.id == state.selectedServerId }
                ServerRail(
                    servers = state.servers,
                    selectedServerId = state.selectedServerId,
                    myId = state.myId,
                    onSelectDms = { viewModel.selectServer(null) },
                    onSelectServer = { viewModel.selectServer(it) },
                    onEditServer = onOpenServerEdit,
                    onAddServer = { createChooser = true },
                )

            AnimatedContent(
                targetState = selected,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                transitionSpec = {
                    // Conteudo novo desliza da esquerda + fade; o antigo so esvanece.
                    (slideInHorizontally(tween(360, easing = EaseSpring)) { w -> -w / 5 } + fadeIn(tween(280)))
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "home-panel",
            ) { srv ->
              if (srv == null) {
                Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = 18.dp).padding(top = 14.dp)) {
                    Reveal {
                        Text(
                            text = "Sussurros",
                            fontFamily = DmSerif,
                            fontSize = 30.sp,
                            color = astraColors.text1,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Reveal(delayMillis = 70) {
                        SearchAddRow(
                            searchOpen = searchOpen,
                            query = query,
                            onToggleSearch = { searchOpen = !searchOpen; if (!searchOpen) query = "" },
                            onQuery = { query = it },
                            onAddFriends = onOpenFriends,
                        )
                    }
                }

                // Corpo: faixa "na voz" + lista de DMs.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize()) {
                        if (state.activeVoice.isNotEmpty() && query.isBlank()) {
                            Spacer(Modifier.height(16.dp))
                            MarginaliaLabel("na voz", Modifier.padding(horizontal = 18.dp))
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(state.activeVoice, key = { it.channelId }) { room ->
                                    VoiceRoomCard(room) {
                                        onJoinVoice(room.channelId, room.channelName, room.serverId)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MarginaliaLabel("sussurros")
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { showDialog = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Lucide.MessageSquarePlus,
                                    contentDescription = "Iniciar sussurro",
                                    tint = astraColors.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "ver todas",
                                style = MaterialTheme.typography.labelMedium,
                                color = astraColors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOpenDms)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))

                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                state.loading -> ListSkeleton(avatar = true)
                                dms.isEmpty() -> Text(
                                    text = if (query.isBlank())
                                        "${AstraCopy.Empties.noDMs.title} — toque em Adicionar estrelas."
                                    else "Nada encontrado.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = astraColors.text3,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                )
                                else -> LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        start = 18.dp,
                                        end = 18.dp,
                                        bottom = 92.dp,
                                    ),
                                ) {
                                    items(dms, key = { it.id }) { c ->
                                        DmRow(c, unread = c.id in state.unread) {
                                            viewModel.markSeen(c.id)
                                            onOpenDm(c.id, c.otherName)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
              } else {
                ServerChannelsPanel(
                    server = srv,
                    isOwner = srv.ownerId != null && srv.ownerId == state.myId,
                    channelUnread = state.channelUnread,
                    onOpenChannel = { id, name -> viewModel.markChannelSeen(id); onOpenChannel(id, name) },
                    onJoinVoice = { id, name -> viewModel.markChannelSeen(id); onJoinVoice(id, name, srv.id) },
                    onEdit = { onOpenServerEdit(srv.id) },
                )
              }
            }
            }

            // Sobrepoe TODO o rodape (inclusive parte do rail), cantos
            // arredondados em cima dando efeito de card sobreposto (Discord).
            BottomUserBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                name = state.myName,
                avatar = state.myAvatar,
                banner = state.myBanner,
                bannerColor = state.myBannerColor,
                status = state.myStatus,
                onOpenSheet = { profileSheet = true },
                onSettings = onOpenSettings,
                onBell = {},
            )
        }
    }

    if (profileSheet) {
        ProfileSheet(
            name = state.myName,
            username = state.myUsername,
            avatar = state.myAvatar,
            banner = state.myBanner,
            bannerColor = state.myBannerColor,
            bio = state.myBio,
            pronouns = state.myPronouns,
            createdAt = state.myCreatedAt,
            status = state.myStatus,
            onEditProfile = { profileSheet = false; onOpenProfile() },
            onDismiss = { profileSheet = false },
        )
    }

    if (showDialog) {
        NewConversationDialog(
            opening = state.opening,
            error = state.openError,
            onConfirm = viewModel::openConversation,
            onDismiss = { showDialog = false; viewModel.clearOpenError() },
        )
    }

    if (createChooser) {
        CreateChooserDialog(
            onServer = { createChooser = false; forgeAsGroup = false; showForge = true },
            onGroup = { createChooser = false; forgeAsGroup = true; showForge = true },
            onJoin = { createChooser = false; onOpenJoin() },
            onDismiss = { createChooser = false },
        )
    }
    if (showForge) {
        ForgeDialog(
            isGroup = forgeAsGroup,
            creating = state.creating,
            error = state.createError,
            onConfirm = { name -> viewModel.createServer(name, forgeAsGroup) },
            onDismiss = { showForge = false; viewModel.clearCreateError() },
        )
    }
}

// ── Bottom sheet: forjar constelacao / aglomerado / orbitar com convite ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateChooserDialog(
    onServer: () -> Unit,
    onGroup: () -> Unit,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = astraColors.overlay,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(astraColors.borderMid),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Forjar ou orbitar",
                style = MaterialTheme.typography.titleLarge,
                color = astraColors.text1,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            ChooserRow(AstraCopy.Action.createServer, AstraCopy.Desc.constelacao, onServer)
            ChooserRow(AstraCopy.Action.createGroup, AstraCopy.Desc.aglomerado, onGroup)
            ChooserRow("${AstraCopy.Action.joinServer} com um convite", "entrar numa que ja existe", onJoin)
        }
    }
}

@Composable
private fun ChooserRow(label: String, sub: String, onClick: () -> Unit) {
    OptionRow(
        title = label,
        sub = sub,
        onClick = onClick,
        titleColor = astraColors.accent,
        trailing = {
            Text("›", fontFamily = DmSerif, color = astraColors.text3, style = MaterialTheme.typography.titleLarge)
        },
    )
}

// ── Dialog de forjar (nome) — serve servidor e grupo ──
@Composable
private fun ForgeDialog(
    isGroup: Boolean,
    creating: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val title = if (isGroup) AstraCopy.Action.createGroup else AstraCopy.Action.createServer
    val desc = if (isGroup) AstraCopy.Desc.aglomerado else AstraCopy.Desc.constelacao
    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        containerColor = astraColors.overlay,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = astraColors.text1) },
        text = {
            Column {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = astraColors.text3,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    enabled = !creating,
                    label = { Text(if (isGroup) "Nome do aglomerado" else "Nome da constelacao") },
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = astraColors.danger,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank() && !creating) {
                Text(if (creating) "Forjando..." else "Forjar", color = astraColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) {
                Text("Cancelar", color = astraColors.text2)
            }
        },
    )
}

// ── Rail de servidores (esquerda) ───────────────────────────────
@Composable
private fun ServerRail(
    servers: List<Server>,
    selectedServerId: String?,
    myId: String?,
    onSelectDms: () -> Unit,
    onSelectServer: (String) -> Unit,
    onEditServer: (String) -> Unit,
    onAddServer: () -> Unit,
) {
    val context = LocalContext.current
    // Capturado fora do drawBehind (lambda nao e @Composable).
    val railBorder = astraColors.borderMid
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            // Transparente: o fundo cosmico/StarField vaza inteiro atras do rail.
            // So a borda direita e os tiles delimitam.
            // Borda direita destacada (paridade web: border-r border-(--border)).
            .drawBehind {
                val x = size.width
                drawLine(
                    color = railBorder,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .verticalScroll(rememberScrollState())
            // bottom grande: o bottom bar sobrepoe o rodape do rail.
            .padding(top = 14.dp, bottom = 92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Sparkles = Sussurros (DMs). Ativo quando nenhuma Constelacao selecionada.
        RailTile(active = selectedServerId == null, onClick = onSelectDms) {
            Icon(
                Lucide.Sparkles,
                contentDescription = "Mensagens diretas",
                tint = astraColors.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        HairlineRule(Modifier.width(28.dp))
        servers.forEach { srv ->
            RailServer(
                server = srv,
                active = selectedServerId == srv.id,
                isOwner = srv.ownerId != null && srv.ownerId == myId,
                onClick = { onSelectServer(srv.id) },
                onEdit = { onEditServer(srv.id) },
                onInvite = { srv.inviteCode?.let { shareServerInvite(context, it) } },
            )
        }
        RailTile(active = false, onClick = onAddServer) {
            Text("+", fontFamily = DmSerif, fontSize = 24.sp, color = astraColors.accent)
        }
    }
}

@Composable
private fun RailTile(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    // Box full-width pra ancorar o indicador na borda esquerda do rail (Discord).
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 3.dp, height = 24.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(astraColors.accent),
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(if (active) astraColors.accentDim else astraColors.raised)
                .border(1.dp, if (active) astraColors.accent.copy(alpha = 0.5f) else astraColors.border, shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailServer(
    server: Server,
    active: Boolean,
    isOwner: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onInvite: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    var menuOpen by remember { mutableStateOf(false) }
    // Box full-width pra ancorar o indicador ativo na borda esquerda (Discord).
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 3.dp, height = 24.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(astraColors.accent),
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(if (active) astraColors.accentDim else astraColors.raised)
                .border(1.dp, if (active) astraColors.accent.copy(alpha = 0.5f) else astraColors.border, shape)
                // Tap = seleciona (canais inline). Segurar = menu de opcoes.
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
            contentAlignment = Alignment.Center,
        ) {
            if (!server.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = server.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(shape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = server.name.take(1).uppercase(),
                    fontFamily = DmSerif,
                    style = MaterialTheme.typography.titleLarge,
                    color = astraColors.accent,
                )
            }
        }
        ServerRailMenu(
            expanded = menuOpen,
            serverName = server.name,
            isOwner = isOwner,
            hasInvite = !server.inviteCode.isNullOrBlank(),
            onEdit = { menuOpen = false; onEdit() },
            onInvite = { menuOpen = false; onInvite() },
            onDismiss = { menuOpen = false },
        )
    }
}

// Menu de long-press (estilo do print): card sem escurecer a tela, titulo +
// divisoria + linhas (rotulo a esquerda, icone a direita). Ciente de dono.
@Composable
private fun ServerRailMenu(
    expanded: Boolean,
    serverName: String,
    isOwner: Boolean,
    hasInvite: Boolean,
    onEdit: () -> Unit,
    onInvite: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(astraColors.overlay),
    ) {
        Column(Modifier.width(248.dp)) {
            Text(
                text = serverName,
                fontFamily = DmSerif,
                fontSize = 18.sp,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (isOwner || hasInvite) HairlineRule()
            if (isOwner) {
                MenuRow("Editar constelacao", Lucide.Settings, onEdit)
            }
            if (isOwner && hasInvite) HairlineRule()
            if (hasInvite) {
                MenuRow("Convidar", Lucide.UserPlus, onInvite)
            }
            if (!isOwner && !hasInvite) {
                Text(
                    text = "Sem acoes disponiveis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = astraColors.text3,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// Linha do menu: rotulo a esquerda, icone a direita (paridade com o print).
@Composable
private fun MenuRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = astraColors.text1, modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = astraColors.text2, modifier = Modifier.size(20.dp))
    }
}

// ── Painel de canais inline (substitui os Sussurros quando ha Constelacao) ──
// Layout do print: capa (icone full-color) -> nome + n. membros + convidar/editar
// -> orbitas em linhas flat. Canais entram em cascata (Reveal).
@Composable
private fun ServerChannelsPanel(
    server: Server,
    isOwner: Boolean,
    channelUnread: Set<String>,
    onOpenChannel: (String, String) -> Unit,
    onJoinVoice: (String, String) -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        ServerPanelHeader(
            server = server,
            isOwner = isOwner,
            onEdit = onEdit,
            onInvite = { server.inviteCode?.let { shareServerInvite(context, it) } },
        )
        if (server.channels.isEmpty()) {
            Text(
                text = "Nenhuma orbita visivel nesta constelacao.",
                style = MaterialTheme.typography.bodyMedium,
                color = astraColors.text3,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
            ) {
                itemsIndexed(server.channels, key = { _, ch -> ch.id }) { i, ch ->
                    // Cascata: cada orbita entra com leve atraso (cap em 12 pra nao arrastar).
                    Reveal(delayMillis = i.coerceAtMost(12) * 28) {
                        ChannelRowFlat(ch, unread = ch.id in channelUnread) {
                            if (ch.isVoice) onJoinVoice(ch.id, ch.name) else onOpenChannel(ch.id, ch.name)
                        }
                    }
                }
            }
        }
    }
}

// Capa (icone como banner full-color, sem escurecer) + nome + contagem + acoes.
@Composable
private fun ServerPanelHeader(
    server: Server,
    isOwner: Boolean,
    onEdit: () -> Unit,
    onInvite: () -> Unit,
) {
    val base = astraColors.base
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(132.dp)) {
            if (!server.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = server.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(astraColors.accentDim, base)),
                    ),
                )
            }
            // Scrim curtinho so na base pra encostar no fundo (sem escurecer a capa).
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(0.65f to Color.Transparent, 1f to base),
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = server.name,
                    fontFamily = DmSerif,
                    fontSize = 26.sp,
                    color = astraColors.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MarginaliaLabel("${server.memberCount} na constelacao")
            }
            if (!server.inviteCode.isNullOrBlank()) {
                CircleIconBtn(Lucide.UserPlus, "Convidar", onInvite)
            }
            if (isOwner) {
                Spacer(Modifier.width(8.dp))
                CircleIconBtn(Lucide.Settings, "Editar constelacao", onEdit)
            }
        }
        Spacer(Modifier.height(12.dp))
        HairlineRule(Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(2.dp))
    }
}

// Linha de orbita flat (estilo Discord): # / 🔊 + nome, sem borda. Nao-lido fica
// mais claro com dot; voz mostra rotulo.
@Composable
private fun ChannelRowFlat(channel: Channel, unread: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (channel.isVoice) "🔊" else "#",
            style = MaterialTheme.typography.titleMedium,
            color = if (channel.isVoice) astraColors.accent else astraColors.text3,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (unread) astraColors.text1 else astraColors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (unread) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(astraColors.accent))
        } else if (channel.isVoice) {
            MarginaliaLabel("voz", color = astraColors.text3)
        }
    }
}

// ── Busca + Adicionar amigos ────────────────────────────────────
@Composable
private fun SearchAddRow(
    searchOpen: Boolean,
    query: String,
    onToggleSearch: () -> Unit,
    onQuery: (String) -> Unit,
    onAddFriends: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Botao de busca circular
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(astraColors.raised)
                    .border(1.dp, if (searchOpen) astraColors.accent.copy(alpha = 0.5f) else astraColors.border, CircleShape)
                    .clickable(onClick = onToggleSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.Search,
                    contentDescription = "Buscar",
                    tint = if (searchOpen) astraColors.accent else astraColors.text2,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            // Pill "Adicionar amigos"
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(astraColors.raised)
                    .border(1.dp, astraColors.border, RoundedCornerShape(12.dp))
                    .clickable(onClick = onAddFriends)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Lucide.UserPlus,
                    contentDescription = null,
                    tint = astraColors.text1,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Adicionar estrelas",
                    style = MaterialTheme.typography.titleSmall,
                    color = astraColors.text1,
                )
            }
        }
        if (searchOpen) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar estrela", color = astraColors.text3) },
            )
        }
    }
}

// ── Card de canal de voz ativo (faixa horizontal) ───────────────
@Composable
private fun VoiceRoomCard(room: ActiveVoiceRoom, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(184.dp)
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.accent.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(astraColors.accent))
            Spacer(Modifier.width(8.dp))
            MarginaliaLabel("na voz", color = astraColors.accent)
        }
        Text(
            text = room.channelName,
            style = MaterialTheme.typography.titleMedium,
            color = astraColors.text1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MarginaliaLabel("${room.serverName} · ${room.count} na orbita")
    }
}

// ── Linha de conversa ───────────────────────────────────────────
@Composable
private fun DmRow(c: Conversation, unread: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AstraAvatar(c.otherAvatarUrl, c.otherName, size = 48)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = c.otherName,
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (c.lastFromMe) "Você: ${c.preview}" else c.preview,
                style = MaterialTheme.typography.bodySmall,
                color = if (unread) astraColors.text1 else astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val time = relativeShort(c.lastMessageAt)
            if (time.isNotEmpty()) MarginaliaLabel(time)
            if (unread) {
                Box(
                    Modifier.size(9.dp).clip(CircleShape).background(astraColors.accent),
                )
            }
        }
    }
}

// ── Barra de perfil (rodape) — modelo user-panel do Discord ──────
// Sobrepoe TODO o rodape (inclusive parte do rail), cantos arredondados
// em cima dando efeito de card sobreposto. Banner do user faiscando atras
// (scrim pra legibilidade). Sem dot no avatar, sem botao de sair (sair
// vive nas Configuracoes). Avatar -> perfil; nome -> status; sino + engrenagem.
@Composable
private fun BottomUserBar(
    modifier: Modifier = Modifier,
    name: String,
    avatar: String?,
    banner: String?,
    bannerColor: String?,
    status: UserStatus,
    onOpenSheet: () -> Unit,
    onSettings: () -> Unit,
    onBell: () -> Unit,
) {
    val base = astraColors.base
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, astraColors.borderMid, shape),
    ) {
        // Fundo: piso base + banner (faint) + scrim pra contraste do texto.
        Box(Modifier.matchParentSize().background(base))
        if (!banner.isNullOrBlank()) {
            AsyncImage(
                model = banner,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
            )
        } else {
            parseHexColor(bannerColor)?.let {
                Box(Modifier.matchParentSize().background(it.copy(alpha = 0.4f)))
            }
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(base.copy(alpha = 0.4f), base.copy(alpha = 0.78f))),
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar + nome + status = um toque -> abre o sheet de perfil.
            // (status nao e mais escolhido direto daqui; vive no sheet.)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenSheet)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AstraAvatar(url = avatar, name = name.ifBlank { "?" }, size = 42)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name.ifBlank { "Astra" },
                            style = MaterialTheme.typography.titleMedium,
                            color = astraColors.text1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Lucide.ChevronDown,
                            contentDescription = null,
                            tint = astraColors.text3,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(status, size = 8.dp)
                        Spacer(Modifier.width(6.dp))
                        MarginaliaLabel(statusLabel(status))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            CircleIconBtn(Lucide.Bell, "Notificacoes", onBell)
            Spacer(Modifier.width(8.dp))
            CircleIconBtn(Lucide.Settings, "Configuracoes", onSettings)
        }
    }
}

/** Botao de icone circular (sino/engrenagem) do bottom bar. */
@Composable
private fun CircleIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(astraColors.raised.copy(alpha = 0.85f))
            .border(1.dp, astraColors.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = astraColors.text2, modifier = Modifier.size(18.dp))
    }
}

// Hex "#rrggbb" -> Color. Invalido/nulo = null (cai pro fundo base).
private fun parseHexColor(raw: String?): Color? {
    if (raw.isNullOrBlank()) return null
    val h = raw.trim().removePrefix("#")
    if (h.length != 6) return null
    return runCatching { Color("FF$h".toLong(16)) }.getOrNull()
}

private fun statusLabel(s: UserStatus): String = AstraCopy.statusLabel(s.name)

// ── Sheet de perfil (sobe de baixo) — card + atalho Editar perfil ──
// Status fica como indicador read-only; configurar status vive no Editar perfil.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(
    name: String,
    username: String,
    avatar: String?,
    banner: String?,
    bannerColor: String?,
    bio: String?,
    pronouns: String?,
    createdAt: String?,
    status: UserStatus,
    onEditProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val base = astraColors.base
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = astraColors.overlay,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(astraColors.borderMid),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        ) {
            // Banner do card + scrim.
            Box(Modifier.fillMaxWidth().height(104.dp)) {
                if (!banner.isNullOrBlank()) {
                    AsyncImage(
                        model = banner,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.matchParentSize().background(parseHexColor(bannerColor) ?: astraColors.raised))
                }
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = 0.85f))),
                    ),
                )
            }
            // Identidade: avatar + nome + @username/pronomes + status (read-only).
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AstraAvatar(url = avatar, name = name.ifBlank { "?" }, size = 60)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = name.ifBlank { "Astra" },
                        fontFamily = DmSerif,
                        fontSize = 24.sp,
                        color = astraColors.text1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            if (username.isNotBlank()) append("@$username")
                            if (!pronouns.isNullOrBlank()) {
                                if (isNotEmpty()) append("  ·  ")
                                append(pronouns)
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = astraColors.text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(status, size = 8.dp)
                        Spacer(Modifier.width(6.dp))
                        MarginaliaLabel(statusLabel(status))
                    }
                }
            }
            if (!bio.isNullOrBlank()) {
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = astraColors.text2,
                    modifier = Modifier.padding(horizontal = 18.dp).padding(top = 12.dp),
                )
            }
            memberSince(createdAt)?.let {
                MarginaliaLabel(it, Modifier.padding(horizontal = 18.dp).padding(top = 10.dp))
            }

            Spacer(Modifier.height(20.dp))
            AstraButton(
                text = "Editar perfil",
                onClick = onEditProfile,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            )
        }
    }
}

// ISO -> "membro desde 8 de dez. de 2021" (pt-BR). Falha de parse = null.
private fun memberSince(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val date = java.time.OffsetDateTime.parse(iso).toLocalDate()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", java.util.Locale("pt", "BR"))
        "membro desde ${date.format(fmt)}"
    }.getOrNull()
}

// Share sheet do convite (/i/:code = pagina OG que redireciona pro /invite/:code).
private fun shareServerInvite(context: Context, code: String) {
    val link = BuildConfig.BASE_URL.trimEnd('/') + "/i/" + code
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Entra na minha constelacao no Astra: $link")
    }
    context.startActivity(Intent.createChooser(send, "Compartilhar convite"))
}


@Composable
private fun NewConversationDialog(
    opening: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!opening) onDismiss() },
        containerColor = astraColors.overlay,
        title = { Text(AstraCopy.Action.startDM, style = MaterialTheme.typography.titleLarge, color = astraColors.text1) },
        text = {
            Column {
                Text(
                    text = AstraCopy.Desc.sussurro,
                    style = MaterialTheme.typography.bodySmall,
                    color = astraColors.text3,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    enabled = !opening,
                    label = { Text("@username") },
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = astraColors.danger,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(username) }, enabled = username.isNotBlank() && !opening) {
                Text(if (opening) "Abrindo..." else "Abrir", color = astraColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !opening) {
                Text("Cancelar", color = astraColors.text2)
            }
        },
    )
}

// ISO -> "agora / 5m / 3h / 2d / 3 sem / 5 mês". Falha de parse = vazio.
private fun relativeShort(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val then = java.time.Instant.parse(iso)
        val sec = java.time.Duration.between(then, java.time.Instant.now()).seconds.coerceAtLeast(0)
        when {
            sec < 60 -> "agora"
            sec < 3600 -> "${sec / 60}m"
            sec < 86_400 -> "${sec / 3600}h"
            sec < 604_800 -> "${sec / 86_400}d"
            sec < 2_592_000 -> "${sec / 604_800} sem"
            else -> "${sec / 2_592_000} mês"
        }
    } catch (e: Exception) {
        ""
    }
}
