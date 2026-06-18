package app.astra.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.realtime.ConnectionState
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.Reveal
import app.astra.mobile.ui.components.RomanNumeral
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors

@Composable
fun HomeScreen(
    onOpenDms: () -> Unit,
    onOpenServers: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val socket by viewModel.socketState.collectAsState()

    CosmicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 22.dp),
        ) {
            // Topo: wordmark + status do socket
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("✦", color = astraColors.accent, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text("Astra", fontFamily = DmSerif, fontSize = 19.sp, color = astraColors.text1)
                Spacer(Modifier.weight(1f))
                SocketChip(socket)
            }

            Spacer(Modifier.weight(1f))

            Reveal { MarginaliaLabel("inicio") }
            Spacer(Modifier.height(8.dp))
            Reveal(delayMillis = 70) {
                Text(
                    text = "Para onde?",
                    style = MaterialTheme.typography.headlineLarge,
                    color = astraColors.text1,
                )
            }
            Spacer(Modifier.height(6.dp))
            Reveal(delayMillis = 110) {
                Text(
                    text = "Suas conversas e comunidades, num lugar so.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = astraColors.text2,
                )
            }

            Spacer(Modifier.height(30.dp))

            Reveal(delayMillis = 170) {
                EntryCard("Mensagens", "conversas diretas", "I", onOpenDms)
            }
            Spacer(Modifier.height(14.dp))
            Reveal(delayMillis = 230) {
                EntryCard("Servidores", "comunidades", "II", onOpenServers)
            }

            Spacer(Modifier.weight(1.3f))

            Reveal(delayMillis = 300) {
                MarginaliaLabel(
                    text = "sair da conta",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(onClick = viewModel::logout)
                        .padding(10.dp),
                    color = astraColors.text3,
                )
            }
        }
    }
}

@Composable
private fun EntryCard(title: String, subtitle: String, numeral: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RomanNumeral("$numeral.", fontSize = 26.sp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = astraColors.text1)
            MarginaliaLabel(subtitle)
        }
        Text("›", fontFamily = DmSerif, fontSize = 24.sp, color = astraColors.text3)
    }
}

@Composable
private fun SocketChip(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.Connected -> "online" to astraColors.success
        ConnectionState.Connecting -> "conectando" to astraColors.text3
        ConnectionState.Disconnected -> "offline" to astraColors.danger
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        MarginaliaLabel(label, color = color)
    }
}
