package app.astra.mobile.feature.voice.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.voice.CallStatus

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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "🔊 ${viewModel.channelName}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = statusLabel(state.status, state.participants, state.error),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.status == CallStatus.Error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.status == CallStatus.Connected) {
                OutlinedButton(onClick = viewModel::toggleMic) {
                    Text(if (state.micEnabled) "Mutar mic" else "Ativar mic")
                }
            }
            Button(
                onClick = { exit() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Sair") }
        }
    }
}

private fun statusLabel(status: CallStatus, participants: Int, error: String?): String =
    when (status) {
        CallStatus.Connecting -> "Conectando…"
        CallStatus.Connected -> "Na chamada • $participants ${if (participants == 1) "pessoa" else "pessoas"}"
        CallStatus.Error -> error ?: "Erro na chamada"
        CallStatus.Idle -> "Saindo…"
    }
