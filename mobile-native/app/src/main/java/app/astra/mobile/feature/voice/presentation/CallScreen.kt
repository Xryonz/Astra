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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.voice.CallStatus
import coil.compose.AsyncImage
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room

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

    // Camera e opcional: pede permissao na hora de ligar.
    val camLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.toggleCamera() }

    val onCamera = {
        if (hasPermission(ctx, Manifest.permission.CAMERA)) viewModel.toggleCamera()
        else camLauncher.launch(Manifest.permission.CAMERA)
    }

    // Screenshare: o sistema pede a captura por um dialogo proprio (nao e uma
    // runtime permission). O resultData volta aqui e vai pro LiveKit.
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

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "🔊 ${state.channelName}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                text = subtitle(state),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.status == CallStatus.Error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.status == CallStatus.Connecting -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.participants.isEmpty() -> Text(
                    text = if (state.status == CallStatus.Error) "—" else "Ninguem na chamada",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> {
                    // Cada pessoa = 1 tile de camera/avatar; quem compartilha tela
                    // ganha um tile extra (span 2 colunas).
                    val tiles = state.participants.flatMap { p ->
                        buildList {
                            add(Tile("${p.identity}:cam", p, isScreen = false))
                            if (p.screenTrack != null) add(Tile("${p.identity}:scr", p, isScreen = true))
                        }
                    }
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
                            ParticipantTile(tile.participant, tile.isScreen, viewModel.room)
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
private fun ParticipantTile(p: CallParticipantUi, isScreen: Boolean, room: Room?) {
    val shape = RoundedCornerShape(16.dp)
    // Anel ambar quando a pessoa fala (so no tile de pessoa, nao no de tela).
    val ring = if (!isScreen && p.isSpeaking) MaterialTheme.colorScheme.primary else Color.Transparent
    val track = if (isScreen) p.screenTrack else p.videoTrack
    val showVideo = if (isScreen) track != null else (p.cameraEnabled && track != null)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (isScreen) 16f / 9f else 1f)
            .clip(shape)
            .border(2.dp, ring, shape)
            .background(if (isScreen) Color.Black else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (showVideo) {
            VideoTrackView(
                videoTrack = track,
                modifier = Modifier.fillMaxSize().clip(shape),
                passedRoom = room,
                mirror = !isScreen && p.isLocal, // camera frontal espelhada pra quem se ve
                scaleType = if (isScreen) ScaleType.FitInside else ScaleType.Fill, // tela inteira vs preenche
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Avatar(p.name, p.avatarUrl)
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
private fun Avatar(name: String, url: String?) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

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
            OutlinedButton(
                onClick = onToggleMic,
                enabled = live && !deafened,
                modifier = Modifier.weight(1f),
            ) { Text(if (micEnabled) "Mutar" else "Ativar") }

            OutlinedButton(
                onClick = onToggleDeafen,
                enabled = live,
                modifier = Modifier.weight(1f),
            ) { Text(if (deafened) "Ouvir" else "Surdo") }
        }
        Spacer(Modifier.padding(top = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onToggleCamera,
                enabled = live,
                modifier = Modifier.weight(1f),
            ) { Text(if (cameraOn) "Camera off" else "Camera") }

            OutlinedButton(
                onClick = onToggleScreen,
                enabled = live,
                modifier = Modifier.weight(1f),
            ) { Text(if (screenSharing) "Parar tela" else "Tela") }
        }
        Spacer(Modifier.padding(top = 10.dp))
        Button(
            onClick = onLeave,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sair") }
    }
}
