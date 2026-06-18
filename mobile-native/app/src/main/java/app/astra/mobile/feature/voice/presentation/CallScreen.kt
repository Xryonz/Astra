package app.astra.mobile.feature.voice.presentation

import android.Manifest
import android.content.pm.PackageManager
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
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.participants, key = { it.identity }) { p ->
                        ParticipantTile(p, viewModel.room)
                    }
                }
            }
        }

        ControlBar(
            status = state.status,
            micEnabled = state.micEnabled,
            cameraOn = state.cameraOn,
            deafened = state.deafened,
            onToggleMic = viewModel::toggleMic,
            onToggleCamera = onCamera,
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
private fun ParticipantTile(p: CallParticipantUi, room: Room?) {
    val shape = RoundedCornerShape(16.dp)
    // Anel ambar quando a pessoa esta falando (active speaker).
    val ring = if (p.isSpeaking) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .border(2.dp, ring, shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (p.cameraEnabled && p.videoTrack != null) {
            VideoTrackView(
                videoTrack = p.videoTrack,
                modifier = Modifier.fillMaxSize().clip(shape),
                passedRoom = room,
                mirror = p.isLocal, // camera frontal espelhada pra quem se ve
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
            if (!p.micEnabled) {
                Text("🔇", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = if (p.isLocal) "${p.name} (voce)" else p.name,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

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
    deafened: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleDeafen: () -> Unit,
    onLeave: () -> Unit,
) {
    val live = status == CallStatus.Connected
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onToggleMic,
                enabled = live && !deafened,
                modifier = Modifier.weight(1f),
            ) { Text(if (micEnabled) "Mutar" else "Ativar") }

            OutlinedButton(
                onClick = onToggleCamera,
                enabled = live,
                modifier = Modifier.weight(1f),
            ) { Text(if (cameraOn) "Cam off" else "Cam on") }

            OutlinedButton(
                onClick = onToggleDeafen,
                enabled = live,
                modifier = Modifier.weight(1f),
            ) { Text(if (deafened) "Ouvir" else "Surdo") }
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
