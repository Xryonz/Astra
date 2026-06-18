package app.astra.mobile.feature.voice.presentation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.voice.CallStatus
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room

// Room e constante durante a call (mesmo EGL/renderer). Passa via local estavel
// pra NAO entrar como parametro instavel em ParticipantTile — assim o tile pode
// pular recomposicao quando so o "isSpeaking" de outro participante muda.
private val LocalCallRoom = staticCompositionLocalOf<Room?> { null }

@Composable
fun CallScreen(
    onLeave: () -> Unit,
    viewModel: CallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.join() else viewModel.permissionDenied()
    }

    // Mic e obrigatorio ANTES de entrar (foreground service type=microphone exige).
    LaunchedEffect(Unit) {
        if (hasPermission(ctx, Manifest.permission.RECORD_AUDIO)) viewModel.join()
        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val camLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.toggleCamera() }

    val onCamera = {
        if (hasPermission(ctx, Manifest.permission.CAMERA)) viewModel.toggleCamera()
        else camLauncher.launch(Manifest.permission.CAMERA)
    }

    // Screenshare: dialogo de captura do sistema -> resultData -> LiveKit.
    val screenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            viewModel.startScreenShare(data)
        }
    }
    val onScreen = {
        if (state.screenSharing) {
            viewModel.stopScreenShare()
        } else {
            val mpm = ctx.getSystemService(MediaProjectionManager::class.java)
            screenLauncher.launch(mpm.createScreenCaptureIntent())
        }
    }

    val exit = {
        viewModel.leave()
        onLeave()
    }
    BackHandler { exit() }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            // Cabecalho cosmico
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                MarginaliaLabel("chamada de voz")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.channelName,
                    style = MaterialTheme.typography.titleLarge,
                    color = astraColors.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.status == CallStatus.Error) astraColors.danger else astraColors.text2,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.status == CallStatus.Connecting ->
                        CosmicSpinner(Modifier.align(Alignment.Center))
                    state.participants.isEmpty() -> Text(
                        text = if (state.status == CallStatus.Error) "—" else "Ninguem na chamada",
                        style = MaterialTheme.typography.bodyMedium,
                        color = astraColors.text2,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> {
                        val tiles = state.participants.flatMap { p ->
                            buildList {
                                add(Tile("${p.identity}:cam", p, isScreen = false))
                                if (p.screenTrack != null) add(Tile("${p.identity}:scr", p, isScreen = true))
                            }
                        }
                        CompositionLocalProvider(LocalCallRoom provides viewModel.room) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(
                                    items = tiles,
                                    key = { it.key },
                                    span = { GridItemSpan(if (it.isScreen) 2 else 1) },
                                ) { tile ->
                                    ParticipantTile(tile.participant, tile.isScreen)
                                }
                            }
                        }
                    }
                }
            }

            ControlBar(
                status = state.status,
                micEnabled = state.micEnabled,
                cameraOn = state.cameraOn,
                screenSharing = state.screenSharing,
                deafened = state.deafened,
                onToggleMic = viewModel::toggleMic,
                onToggleCamera = onCamera,
                onToggleScreen = onScreen,
                onToggleDeafen = viewModel::toggleDeafen,
                onLeave = { exit() },
            )
        }
    }
}

private fun hasPermission(ctx: android.content.Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

private fun subtitle(state: CallUiState): String = when (state.status) {
    CallStatus.Connecting -> "Conectando…"
    CallStatus.Connected -> {
        val n = state.participants.size
        "$n ${if (n == 1) "pessoa" else "pessoas"} na chamada"
    }
    CallStatus.Error -> state.error ?: "Erro na chamada"
    CallStatus.Idle -> "Saindo…"
}

@Composable
private fun ParticipantTile(p: CallParticipantUi, isScreen: Boolean) {
    val room = LocalCallRoom.current
    val shape = RoundedCornerShape(16.dp)
    val ring = if (!isScreen && p.isSpeaking) astraColors.accent else Color.Transparent
    val track = if (isScreen) p.screenTrack else p.videoTrack
    val showVideo = if (isScreen) track != null else (p.cameraEnabled && track != null)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (isScreen) 16f / 9f else 1f)
            .clip(shape)
            .border(2.dp, ring, shape)
            .background(if (isScreen) Color.Black else astraColors.raised),
    ) {
        if (showVideo) {
            VideoTrackView(
                videoTrack = track,
                modifier = Modifier.fillMaxSize().clip(shape),
                passedRoom = room,
                mirror = !isScreen && p.isLocal,
                scaleType = if (isScreen) ScaleType.FitInside else ScaleType.Fill,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AstraAvatar(p.avatarUrl, p.name, size = 64)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isScreen && !p.micEnabled) {
                Text("🔇", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = when {
                    isScreen -> "${p.name} (tela)"
                    p.isLocal -> "${p.name} (voce)"
                    else -> p.name
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class Tile(
    val key: String,
    val participant: CallParticipantUi,
    val isScreen: Boolean,
)

@Composable
private fun ControlBar(
    status: CallStatus,
    micEnabled: Boolean,
    cameraOn: Boolean,
    screenSharing: Boolean,
    deafened: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleScreen: () -> Unit,
    onToggleDeafen: () -> Unit,
    onLeave: () -> Unit,
) {
    val live = status == CallStatus.Connected
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CallToggle("Microfone", active = micEnabled, enabled = live && !deafened, onClick = onToggleMic, modifier = Modifier.weight(1f))
            CallToggle("Surdo", active = deafened, enabled = live, onClick = onToggleDeafen, activeColor = astraColors.danger, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CallToggle("Camera", active = cameraOn, enabled = live, onClick = onToggleCamera, modifier = Modifier.weight(1f))
            CallToggle("Tela", active = screenSharing, enabled = live, onClick = onToggleScreen, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(astraColors.danger)
                .clickable(onClick = onLeave),
            contentAlignment = Alignment.Center,
        ) {
            Text("Sair", color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Pill toggle: preenchido (activeColor) quando ligado, contornado quando desligado. */
@Composable
private fun CallToggle(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = astraColors.accent,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (active) activeColor else astraColors.raised
    val fg = when {
        !enabled -> astraColors.text3
        active -> astraColors.textInv
        else -> astraColors.text2
    }
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(bg.copy(alpha = if (enabled) 1f else 0.4f))
            .border(1.dp, if (active) Color.Transparent else astraColors.borderMid, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 14.sp)
    }
}
