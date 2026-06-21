package app.astra.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.AstraCopy
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.ListSkeleton
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.Reveal
import app.astra.mobile.ui.components.StatusDot
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import coil.compose.AsyncImage

@Composable
fun HomeScreen(
    onOpenServer: (id: String, name: String) -> Unit,
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

    // DM aberta pelo FAB -> navega pro chat e fecha o dialog.
    LaunchedEffect(Unit) {
        viewModel.opened.collect { conv ->
            showDialog = false
            onOpenDm(conv.conversationId, conv.otherName)
        }
    }

    // Constelacao forjada -> fecha o dialog e entra nela.
    LaunchedEffect(Unit) {
        viewModel.serverCreated.collect { srv ->
            showForge = false
            onOpenServer(srv.id, srv.name)
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
                ServerRail(
                    servers = state.servers,
                    onOpenServer = onOpenServer,
                    onAddServer = { createChooser = true },
                )

            Column(Modifier.weight(1f).fillMaxHeight()) {
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
                onPickStatus = viewModel::setStatus,
                onProfile = onOpenProfile,
                onSettings = onOpenSettings,
                onBell = {},
            )
        }
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

// ── Popup: forjar constelacao / forjar aglomerado / orbitar com convite ──
@Composable
private fun CreateChooserDialog(
    onServer: () -> Unit,
    onGroup: () -> Unit,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = astraColors.overlay,
        title = { Text("Forjar ou orbitar", style = MaterialTheme.typography.titleLarge, color = astraColors.text1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ChooserRow(AstraCopy.Action.createServer, AstraCopy.Desc.constelacao, onServer)
                ChooserRow(AstraCopy.Action.createGroup, AstraCopy.Desc.aglomerado, onGroup)
                ChooserRow("${AstraCopy.Action.joinServer} com um convite", "entrar numa que ja existe", onJoin)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = astraColors.text2) } },
    )
}

@Composable
private fun ChooserRow(label: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = astraColors.accent)
            MarginaliaLabel(sub)
        }
        Text("›", fontFamily = DmSerif, color = astraColors.text3, style = MaterialTheme.typography.titleLarge)
    }
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
    onOpenServer: (String, String) -> Unit,
    onAddServer: () -> Unit,
) {
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
        RailTile(active = true, onClick = {}) {
            Icon(
                Lucide.Sparkles,
                contentDescription = "Mensagens diretas",
                tint = astraColors.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        HairlineRule(Modifier.width(28.dp))
        servers.forEach { srv ->
            RailServer(srv) { onOpenServer(srv.id, srv.name) }
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

@Composable
private fun RailServer(server: Server, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .clickable(onClick = onClick),
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
    onPickStatus: (UserStatus) -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onBell: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
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
            // Avatar (sem dot) -> abre o perfil
            Box(Modifier.clip(CircleShape).clickable(onClick = onProfile)) {
                AstraAvatar(url = avatar, name = name.ifBlank { "?" }, size = 42)
            }
            Spacer(Modifier.width(11.dp))
            // Nome + chevron + status -> abre o seletor de status
            Box(Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { menuOpen = true }
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
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
                StatusMenu(
                    expanded = menuOpen,
                    current = status,
                    onPick = { menuOpen = false; onPickStatus(it) },
                    onDismiss = { menuOpen = false },
                )
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

@Composable
private fun StatusMenu(
    expanded: Boolean,
    current: UserStatus,
    onPick: (UserStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf(UserStatus.ONLINE, UserStatus.IDLE, UserStatus.DND, UserStatus.INVISIBLE).forEach { s ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(s, size = 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = statusLabel(s),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (s == current) astraColors.accent else astraColors.text1,
                        )
                    }
                },
                onClick = { onPick(s) },
            )
        }
    }
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
