package app.astra.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.realtime.ConnectionState

// Placeholder do M3 + status do socket (M4a). Vira a tela real de DMs no M4b/c.
@Composable
fun HomeScreen(
    onOpenDms: () -> Unit,
    onOpenServers: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val socket by viewModel.socketState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SocketStatus(socket)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Sessao ativa",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Login + realtime nativos. Token no DataStore, socket conectado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenDms) {
            Text("Mensagens")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenServers) {
            Text("Servidores")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { viewModel.logout() }) {
            Text("Sair")
        }
    }
}

@Composable
private fun SocketStatus(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.Connected -> "Online" to MaterialTheme.colorScheme.primary
        ConnectionState.Connecting -> "Conectando..." to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionState.Disconnected -> "Offline" to MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
