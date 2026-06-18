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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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

@Composable
fun CallScreen(
    onLeave: () -> Unit,
    viewModel: CallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.join() else viewModel.permissionDenied()
    }

    // Mic e obrigatorio ANTES de entrar (foreground service type=microphone exige).
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.join() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.participants, key = { it.identity }) { p -> ParticipantRow(p) }
                }
            }
        }

        ControlBar(
            status = state.status,
            micEnabled = state.micEnabled,
            deafened = state.deafened,
            onToggleMic = viewModel::toggleMic,
            onToggleDeafen = viewModel::toggleDeafen,
            onLeave = { exit() },
        )
    }
}

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
private fun ParticipantRow(p: CallParticipantUi) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.name, p.avatarUrl, p.isSpeaking)
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (p.isLocal) "${p.name} (voce)" else p.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!p.micEnabled) {
            Text(
                text = "🔇",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun Avatar(name: String, url: String?, speaking: Boolean) {
    // Anel ambar quando a pessoa esta falando (active speaker).
    val ring = if (speaking) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(2.dp, ring, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ControlBar(
    status: CallStatus,
    micEnabled: Boolean,
    deafened: Boolean,
    onToggleMic: () -> Unit,
    onToggleDeafen: () -> Unit,
    onLeave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val live = status == CallStatus.Connected
        OutlinedButton(
            onClick = onToggleMic,
            enabled = live && !deafened,
            modifier = Modifier.weight(1f),
        ) { Text(if (micEnabled) "Mutar" else "Ativar") }

        OutlinedButton(
            onClick = onToggleDeafen,
            enabled = live,
            modifier = Modifier.weight(1f),
        ) { Text(if (deafened) "Ouvir" else "Silenciar") }

        Button(
            onClick = onLeave,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.weight(1f),
        ) { Text("Sair") }
    }
}
