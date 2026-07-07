package app.astra.mobile.feature.home

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.X
import app.astra.mobile.BuildConfig
import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.feature.server.domain.model.Channel
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.AstraCopy
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AstraDialog
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.BadgeChips
import app.astra.mobile.ui.components.BadgeUi
import app.astra.mobile.ui.components.CosmicBackdrop
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.displayFontFamily
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.ListSkeleton
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.OptionRow
import app.astra.mobile.ui.components.ProfileHero
import app.astra.mobile.ui.components.Reveal
import app.astra.mobile.ui.components.StatusDot
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.EaseSpring
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import zed.rainxch.rikkaui.components.ui.toast.LocalToastHostState
import zed.rainxch.rikkaui.components.ui.toast.ToastVariant

@Composable
fun HomeScreen(
    onOpenChannel: (id: String, name: String) -> Unit,
    onOpenServerEdit: (serverId: String) -> Unit,
    onOpenJoin: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenDm: (id: String, name: String) -> Unit,
    onOpenDms: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenVerifyEmail: () -> Unit,
    onJoinVoice: (channelId: String, name: String, serverId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    var showForge by remember { mutableStateOf(false) }
    var forgeAsGroup by remember { mutableStateOf(false) }

    var profileSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.opened.collect { conv ->
            showDialog = false
            onOpenDm(conv.conversationId, conv.otherName)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.serverCreated.collect { srv ->
            showForge = false
            viewModel.selectServer(srv.id)
        }
    }

    // Conta que nunca viu o onboarding cosmico (gate compartilhado com o web).
    LaunchedEffect(state.needsOnboarding) {
        if (state.needsOnboarding) {
            viewModel.consumeOnboarding()
            onOpenOnboarding()
        }
    }

    // Email ainda nao confirmado (registro novo) -> tela de codigo por cima.
    LaunchedEffect(state.needsEmailVerify) {
        if (state.needsEmailVerify) {
            viewModel.consumeEmailVerify()
            onOpenVerifyEmail()
        }
    }

    // Push: pede POST_NOTIFICATIONS (13+) uma vez na Home logada e registra o token
    // FCM. Sem google-services.json o registerPush e no-op (nada quebra).
    val context = LocalContext.current
    val pushLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.registerPush() }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val ok = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (ok) viewModel.registerPush() else pushLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.registerPush()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshProfile()
                viewModel.refreshServers()
                viewModel.refreshNotifications()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val toastState = LocalToastHostState.current
    LaunchedEffect(state.manageError) {
        state.manageError?.let {
            toastState.show(it, variant = ToastVariant.Destructive)
            viewModel.clearManageError()
        }
    }

    val dms = state.dms

    val transitionsOn = LocalAppPrefs.current.transitionsOn

    CosmicBackground {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().statusBarsPadding()) {
                val selected = state.servers.find { it.id == state.selectedServerId }
                ServerRail(
                    servers = state.servers,
                    selectedServerId = state.selectedServerId,
                    myId = state.myId,
                    mutedServers = state.mutedServers,
                    onSelectDms = { viewModel.selectServer(null) },
                    onSelectServer = { viewModel.selectServer(it) },
                    onToggleMuteServer = { id, muted -> viewModel.setServerMuted(id, muted) },
                    onEditServer = onOpenServerEdit,
                    onCreateServer = { forgeAsGroup = false; showForge = true },
                    onCreateGroup = { forgeAsGroup = true; showForge = true },
                    onJoinInvite = onOpenJoin,
                    onDiscover = onOpenDiscover,
                    onLeaveServer = { viewModel.leaveServer(it) },
                )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 24.dp))
                    .background(astraColors.base.copy(alpha = 0.16f))
                    .border(1.dp, astraColors.borderMid, RoundedCornerShape(topStart = 24.dp)),
            ) {
            AnimatedContent(
                targetState = selected,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {

                    (slideInHorizontally(tween(520, easing = EaseSpring)) { w -> -w / 4 } + fadeIn(tween(420)))
                        .togetherWith(fadeOut(tween(260)))
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
                            onSearch = onOpenSearch,
                            onAddFriends = onOpenFriends,
                            onNew = { showDialog = true },
                        )
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize()) {
                        if (state.activeVoice.isNotEmpty()) {
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
                                dms.isEmpty() -> EmptyState(
                                    line = AstraCopy.Empties.noDMs.title,
                                    hint = "toque em Adicionar estrelas",
                                )
                                else -> LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        start = 18.dp,
                                        end = 18.dp,
                                        bottom = 92.dp,
                                    ),
                                ) {
                                    itemsIndexed(dms, key = { _, c -> c.id }) { i, c ->
                                        DmRow(
                                            c,
                                            unread = c.id in state.unread,
                                            showDivider = i < dms.lastIndex,
                                        ) {
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
                    mutedChannels = state.mutedChannels,
                    onOpenChannel = { id, name -> viewModel.markChannelSeen(id); onOpenChannel(id, name) },
                    onJoinVoice = { id, name -> viewModel.markChannelSeen(id); onJoinVoice(id, name, srv.id) },
                    onEdit = { onOpenServerEdit(srv.id) },
                    onCreateChannel = { name, isVoice -> viewModel.createChannel(srv.id, name, isVoice) },
                )
              }
            }
            }
            }

            BottomUserBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                name = state.myName,
                avatar = state.myAvatar,
                status = state.myStatus,
                onOpenSheet = { profileSheet = true },
                onSettings = onOpenSettings,
                onBell = onOpenNotifications,
                notifDot = state.unreadNotifs > 0,
            )
        }
    }

    AnimatedVisibility(
        visible = profileSheet,
        enter = if (!transitionsOn) fadeIn(tween(120))
                else slideInVertically(tween(400, easing = EaseSpring)) { it } + fadeIn(tween(240)),
        exit = if (!transitionsOn) fadeOut(tween(90))
               else slideOutVertically(tween(300, easing = EaseSpring)) { it } + fadeOut(tween(200)),
    ) {
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
            customStatus = state.myCustomStatus,
            font = state.myFont,
            badges = state.myBadges,
            servers = state.servers,
            onEditProfile = { profileSheet = false; onOpenProfile() },
            onDismiss = { profileSheet = false },
        )
    }

    // Conta Google sem senha: overlay OBRIGATORIO (back nao fecha) ate criar uma.
    if (state.needsPassword) {
        CreatePasswordGate(
            saving = state.pwSaving,
            error = state.pwError,
            onSubmit = viewModel::setPassword,
        )
    }

    NewConversationDialog(
        open = showDialog,
        opening = state.opening,
        error = state.openError,
        onConfirm = viewModel::openConversation,
        onDismiss = { showDialog = false; viewModel.clearOpenError() },
    )

    ForgeDialog(
        open = showForge,
        isGroup = forgeAsGroup,
        creating = state.creating,
        error = state.createError,
        onConfirm = { name -> viewModel.createServer(name, forgeAsGroup) },
        onDismiss = { showForge = false; viewModel.clearCreateError() },
    )
}

// Tela-bloqueio de criar senha (conta Google). Sem escape: e requisito da conta.
@Composable
private fun CreatePasswordGate(
    saving: Boolean,
    error: String?,
    onSubmit: (String, String) -> Unit,
) {
    BackHandler {}
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    CosmicBackdrop {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 26.dp),
        ) {
            Text(
                text = "Crie uma senha",
                fontFamily = DmSerif,
                fontSize = 28.sp,
                color = astraColors.text1,
            )
            Spacer(Modifier.height(6.dp))
            MarginaliaLabel("sua conta entrou com o Google e ainda nao tem senha — crie uma pra garantir o acesso")
            Spacer(Modifier.height(20.dp))
            EditorialField(
                value = pw, onValue = { pw = it },
                label = "nova senha", placeholder = "8+ caracteres, 1 maiuscula, 1 numero",
                enabled = !saving, keyboardType = KeyboardType.Password, imeAction = ImeAction.Next,
                password = true,
            )
            Spacer(Modifier.height(14.dp))
            EditorialField(
                value = confirm, onValue = { confirm = it },
                label = "confirmar senha", placeholder = "••••••••",
                enabled = !saving, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                onIme = { onSubmit(pw, confirm) }, password = true,
            )
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                AuthErrorBox(error)
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(astraColors.accent)
                    .clickable(enabled = !saving) { onSubmit(pw, confirm) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (saving) "Salvando..." else "Criar senha",
                    color = astraColors.textInv,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun ForgeDialog(
    open: Boolean,
    isGroup: Boolean,
    creating: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(open) { mutableStateOf("") }
    val title = if (isGroup) AstraCopy.Action.createGroup else AstraCopy.Action.createServer
    val desc = if (isGroup) AstraCopy.Desc.aglomerado else AstraCopy.Desc.constelacao
    AstraDialog(
        open = open,
        onDismiss = { if (!creating) onDismiss() },
        title = title,
        confirmText = if (creating) "Forjando..." else "Forjar",
        onConfirm = { onConfirm(name) },
        confirmEnabled = name.isNotBlank() && !creating,
    ) {
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = astraColors.text3,
        )
        EditorialField(
            value = name,
            onValue = { name = it },
            label = if (isGroup) "nome do aglomerado" else "nome da constelacao",
            placeholder = if (isGroup) "como vao chamar o grupo?" else "como vao chamar a constelacao?",
            enabled = !creating,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
            onIme = { if (name.isNotBlank() && !creating) onConfirm(name) },
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.danger,
            )
        }
    }
}

@Composable
private fun ServerRail(
    servers: List<Server>,
    selectedServerId: String?,
    myId: String?,
    mutedServers: Set<String>,
    onSelectDms: () -> Unit,
    onSelectServer: (String) -> Unit,
    onToggleMuteServer: (String, Boolean) -> Unit,
    onEditServer: (String) -> Unit,
    onCreateServer: () -> Unit,
    onCreateGroup: () -> Unit,
    onJoinInvite: () -> Unit,
    onDiscover: () -> Unit,
    onLeaveServer: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()

            .verticalScroll(rememberScrollState())

            .padding(top = 14.dp, bottom = 92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

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
                muted = srv.id in mutedServers,
                onClick = { onSelectServer(srv.id) },
                onToggleMute = { onToggleMuteServer(srv.id, srv.id !in mutedServers) },
                onEdit = { onEditServer(srv.id) },
                onInvite = { srv.inviteCode?.let { shareServerInvite(context, it) } },
                onLeave = { onLeaveServer(srv.id) },
            )
        }
        RailAddMenu(
            onCreateServer = onCreateServer,
            onCreateGroup = onCreateGroup,
            onJoinInvite = onJoinInvite,
        )
        // Descobrir tem tile proprio na base do rail (saiu do menu +): separa
        // "orbitar algo que existe" de "forjar algo novo".
        RailTile(active = false, onClick = onDiscover) {
            Icon(
                Lucide.Compass,
                contentDescription = "Descobrir constelacoes",
                tint = astraColors.accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun RailAddMenu(
    onCreateServer: () -> Unit,
    onCreateGroup: () -> Unit,
    onJoinInvite: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(astraColors.raised)
                .border(1.dp, astraColors.border, shape)
                .clickable { menuOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontFamily = DmSerif, fontSize = 24.sp, color = astraColors.accent)
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                offset = DpOffset(x = 56.dp, y = (-48).dp),
                modifier = Modifier.background(astraColors.raised),
            ) {
                Column(Modifier.width(248.dp)) {
                    Text(
                        text = "Forjar ou orbitar",
                        fontFamily = DmSerif,
                        fontSize = 18.sp,
                        color = astraColors.text1,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    HairlineRule()
                    MenuRow("Criar servidor", Lucide.Sparkles, onClick = { menuOpen = false; onCreateServer() })
                    HairlineRule()
                    MenuRow("Criar grupo", Lucide.Users, onClick = { menuOpen = false; onCreateGroup() })
                    HairlineRule()
                    MenuRow("Entrar com convite", Lucide.Link, onClick = { menuOpen = false; onJoinInvite() })
                }
            }
        }
    }
}

@Composable
private fun RailTile(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val hapticsOn = LocalAppPrefs.current.haptics
    val haptic = LocalHapticFeedback.current

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
                .clickable {
                    if (hapticsOn && !active) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
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
    muted: Boolean,
    onClick: () -> Unit,
    onToggleMute: () -> Unit,
    onEdit: () -> Unit,
    onInvite: () -> Unit,
    onLeave: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    var menuOpen by remember { mutableStateOf(false) }
    var leaveConfirm by remember { mutableStateOf(false) }
    val hapticsOn = LocalAppPrefs.current.haptics
    val haptic = LocalHapticFeedback.current

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

                .combinedClickable(
                    onClick = {
                        if (hapticsOn && !active) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onClick()
                    },
                    onLongClick = {
                        if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                ),
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

            ServerRailMenu(
                expanded = menuOpen,
                serverName = server.name,
                isOwner = isOwner,
                hasInvite = !server.inviteCode.isNullOrBlank(),
                muted = muted,
                onEdit = { menuOpen = false; onEdit() },
                onInvite = { menuOpen = false; onInvite() },
                onToggleMute = { menuOpen = false; onToggleMute() },
                onLeave = { menuOpen = false; leaveConfirm = true },
                onDismiss = { menuOpen = false },
            )
        }
    }

    AstraDialog(
        open = leaveConfirm,
        onDismiss = { leaveConfirm = false },
        title = "Sair da constelacao?",
        confirmText = "Sair",
        confirmColor = astraColors.danger,
        onConfirm = { leaveConfirm = false; onLeave() },
    ) {
        Text(
            text = "Voce vai deixar \"${server.name}\". Pra voltar, vai precisar de um novo convite.",
            style = MaterialTheme.typography.bodyMedium,
            color = astraColors.text2,
        )
    }
}

@Composable
private fun ServerRailMenu(
    expanded: Boolean,
    serverName: String,
    isOwner: Boolean,
    hasInvite: Boolean,
    muted: Boolean,
    onEdit: () -> Unit,
    onInvite: () -> Unit,
    onToggleMute: () -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,

        offset = DpOffset(x = 56.dp, y = (-48).dp),
        modifier = Modifier.background(astraColors.raised),
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
            HairlineRule()
            MenuRow(
                if (muted) "Reativar notificacoes" else "Silenciar constelacao",
                if (muted) Lucide.Bell else Lucide.BellOff,
                onToggleMute,
            )
            if (isOwner || hasInvite) HairlineRule()
            if (isOwner) {
                MenuRow("Editar constelacao", Lucide.Settings, onEdit)
            }
            if (isOwner && hasInvite) HairlineRule()
            if (hasInvite) {
                MenuRow("Convidar", Lucide.UserPlus, onInvite)
            }
            // Sair so pra quem NAO e dono (o dono precisa excluir/transferir).
            if (!isOwner) {
                HairlineRule()
                MenuRow("Sair da constelacao", Lucide.LogOut, onLeave, color = astraColors.danger)
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    color: Color = astraColors.text1,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) astraColors.hover.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        Icon(
            icon,
            contentDescription = null,
            tint = if (color == astraColors.text1) astraColors.text2 else color,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ServerChannelsPanel(
    server: Server,
    isOwner: Boolean,
    channelUnread: Set<String>,
    mutedChannels: Set<String>,
    onOpenChannel: (String, String) -> Unit,
    onJoinVoice: (String, String) -> Unit,
    onEdit: () -> Unit,
    onCreateChannel: (name: String, isVoice: Boolean) -> Unit,
) {
    val context = LocalContext.current

    var showCreateChannel by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ServerPanelHeader(
            server = server,
            isOwner = isOwner,
            onEdit = onEdit,
            onInvite = { server.inviteCode?.let { shareServerInvite(context, it) } },
            onAddChannel = if (isOwner) ({ showCreateChannel = true }) else null,
        )
        if (server.channels.isEmpty()) {
            EmptyState(
                line = "Nenhuma orbita visivel",
                hint = "as orbitas desta constelacao pousam aqui",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
            ) {
                itemsIndexed(
                    server.channels,
                    key = { _, ch -> ch.id },
                ) { i, ch ->

                    Reveal(delayMillis = i.coerceAtMost(12) * 42, durationMillis = 620, distance = 18f) {
                        Column {
                            ChannelRowFlat(
                                channel = ch,
                                unread = ch.id in channelUnread,
                                muted = ch.id in mutedChannels,
                            ) {
                                if (ch.isVoice) onJoinVoice(ch.id, ch.name) else onOpenChannel(ch.id, ch.name)
                            }
                            // Divisor entre orbitas: separa uma da outra ao mirar o toque.
                            if (i < server.channels.lastIndex) {
                                HairlineRule(Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    CreateChannelDialog(
        open = showCreateChannel,
        onConfirm = { name, isVoice -> onCreateChannel(name, isVoice); showCreateChannel = false },
        onDismiss = { showCreateChannel = false },
    )
}

@Composable
private fun ServerPanelHeader(
    server: Server,
    isOwner: Boolean,
    onEdit: () -> Unit,
    onInvite: () -> Unit,
    onAddChannel: (() -> Unit)? = null,
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
            onAddChannel?.let {
                Spacer(Modifier.width(8.dp))
                CircleIconBtn(Lucide.Plus, "Novo canal", it)
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

@Composable
private fun ChannelRowFlat(
    channel: Channel,
    unread: Boolean,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .alpha(if (muted) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel.isVoice) {
            Icon(
                Lucide.Volume2,
                contentDescription = null,
                tint = astraColors.accent,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = "#",
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text3,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (unread) astraColors.text1 else astraColors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (muted) {
            Icon(Lucide.BellOff, contentDescription = "Silenciado", tint = astraColors.text3, modifier = Modifier.size(13.dp))
        } else if (unread) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(astraColors.accent))
        } else if (channel.isVoice) {
            MarginaliaLabel("voz", color = astraColors.text3)
        }
    }
}

@Composable
private fun CreateChannelDialog(
    open: Boolean,
    onConfirm: (name: String, isVoice: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(open) { mutableStateOf("") }
    var isVoice by remember(open) { mutableStateOf(false) }
    AstraDialog(
        open = open,
        onDismiss = onDismiss,
        title = "Novo canal",
        confirmText = "Criar",
        onConfirm = { onConfirm(name.trim(), isVoice) },
        confirmEnabled = name.isNotBlank(),
    ) {
        EditorialField(
            value = name,
            onValue = { name = it },
            label = "nome do canal",
            placeholder = "geral",
            enabled = true,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
        )
        OptionRow(title = "Texto", selected = !isVoice, onClick = { isVoice = false })
        OptionRow(title = "Voz", selected = isVoice, onClick = { isVoice = true })
    }
}

@Composable
private fun SearchAddRow(
    onSearch: () -> Unit,
    onAddFriends: () -> Unit,
    onNew: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(astraColors.raised)
                    .border(1.dp, astraColors.border, CircleShape)
                    .clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.Search,
                    contentDescription = "Buscar",
                    tint = astraColors.text2,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
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
            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(astraColors.accentDim)
                    .border(1.dp, astraColors.accent.copy(alpha = 0.5f), CircleShape)
                    .clickable(onClick = onNew),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.Plus,
                    contentDescription = "Novo sussurro",
                    tint = astraColors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

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

@Composable
private fun DmRow(c: Conversation, unread: Boolean, showDivider: Boolean, onClick: () -> Unit) {

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (pressed) astraColors.raised.copy(alpha = 0.6f) else Color.Transparent)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(vertical = 9.dp, horizontal = 6.dp),
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

        if (showDivider) HairlineRule(Modifier.padding(start = 66.dp, top = 2.dp))
    }
}

@Composable
private fun BottomUserBar(
    modifier: Modifier = Modifier,
    name: String,
    avatar: String?,
    status: UserStatus,
    onOpenSheet: () -> Unit,
    onSettings: () -> Unit,
    onBell: () -> Unit,
    notifDot: Boolean = false,
) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(astraColors.base)
            .border(1.dp, astraColors.borderMid, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenSheet)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AstraAvatar(url = avatar, name = name.ifBlank { "?" }, size = 48)
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
            Box {
                CircleIconBtn(Lucide.Bell, "Notificacoes", onBell)
                if (notifDot) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(astraColors.accent),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            CircleIconBtn(Lucide.Settings, "Configuracoes", onSettings)
        }
    }
}

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

private fun parseHexColor(raw: String?): Color? {
    if (raw.isNullOrBlank()) return null
    val h = raw.trim().removePrefix("#")
    if (h.length != 6) return null
    return runCatching { Color("FF$h".toLong(16)) }.getOrNull()
}

private fun statusLabel(s: UserStatus): String = AstraCopy.statusLabel(s.name)

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
    customStatus: String?,
    font: String,
    badges: List<BadgeUi>,
    servers: List<Server>,
    onEditProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val member = memberSince(createdAt)
    // Tela cheia opaca estilo Discord (cobre a Home). Botao voltar fecha.
    BackHandler(onBack = onDismiss)
    // Backdrop proprio (opaco): este sheet COBRE a Home; o shim transparente
    // do CosmicBackground deixaria a Home aparecer atras.
    CosmicBackdrop(interactive = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
            ProfileHero(
                bannerUrl = banner,
                bannerColor = parseHexColor(bannerColor) ?: astraColors.raised,
                bannerPositionY = 50,
                bannerScale = 100,
                avatarUrl = avatar,
                displayName = name.ifBlank { "Astra" },
                displayFont = displayFontFamily(font),
                subtitle = buildString {
                    if (username.isNotBlank()) append("@$username")
                    if (!pronouns.isNullOrBlank()) {
                        if (isNotEmpty()) append("  ·  ")
                        append(pronouns)
                    }
                },
                statusColor = when (status) {
                    UserStatus.ONLINE -> astraColors.success
                    UserStatus.IDLE -> astraColors.warning
                    UserStatus.DND -> astraColors.danger
                    else -> astraColors.text3
                },
            )
                // X pra fechar, sobre o banner (Discord).
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 12.dp, top = 8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.32f))
                        .border(1.dp, astraColors.borderMid, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.X, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // Recado (custom status): so leitura aqui — editar e na Personalizacao.
            if (!customStatus.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "“$customStatus”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = astraColors.text1,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }

            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                BadgeChips(badges, Modifier.padding(horizontal = 18.dp))
            }

            Spacer(Modifier.height(18.dp))
            // Botao Editar estilo Discord: pilula larga + lapis, na cor ambar (pele Umbra).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(astraColors.accent)
                    .clickable(onClick = onEditProfile),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Pencil, contentDescription = null, tint = astraColors.textInv, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Editar perfil",
                    color = astraColors.textInv,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }

            if (!bio.isNullOrBlank() || member != null) {
                Spacer(Modifier.height(16.dp))
                ProfileCard(Modifier.padding(horizontal = 18.dp)) {
                    if (!bio.isNullOrBlank()) {
                        MarginaliaLabel("bio")
                        Spacer(Modifier.height(6.dp))
                        Text(bio, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
                    }
                    member?.let {
                        if (!bio.isNullOrBlank()) Spacer(Modifier.height(14.dp))
                        MarginaliaLabel("membro desde")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it.removePrefix("membro desde "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = astraColors.text2,
                        )
                    }
                }
            }

            if (servers.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ProfileCard(Modifier.padding(horizontal = 18.dp)) {
                    MarginaliaLabel("constelacoes")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        servers.forEach { ConstellationChip(it) }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun ConstellationChip(server: Server) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(astraColors.overlay)
                .border(1.dp, astraColors.border, shape),
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
                    style = MaterialTheme.typography.titleMedium,
                    color = astraColors.accent,
                )
            }
        }
        Text(
            text = server.name,
            style = MaterialTheme.typography.labelSmall,
            color = astraColors.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun memberSince(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val date = java.time.OffsetDateTime.parse(iso).toLocalDate()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", java.util.Locale("pt", "BR"))
        "membro desde ${date.format(fmt)}"
    }.getOrNull()
}

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
    open: Boolean,
    opening: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember(open) { mutableStateOf("") }
    AstraDialog(
        open = open,
        onDismiss = { if (!opening) onDismiss() },
        title = AstraCopy.Action.startDM,
        confirmText = if (opening) "Abrindo..." else "Abrir",
        onConfirm = { onConfirm(username) },
        confirmEnabled = username.isNotBlank() && !opening,
    ) {
        Text(
            text = AstraCopy.Desc.sussurro,
            style = MaterialTheme.typography.bodySmall,
            color = astraColors.text3,
        )
        EditorialField(
            value = username,
            onValue = { username = it },
            label = "estrela",
            placeholder = "@username",
            enabled = !opening,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
            onIme = { if (username.isNotBlank() && !opening) onConfirm(username) },
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.danger,
            )
        }
    }
}

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
