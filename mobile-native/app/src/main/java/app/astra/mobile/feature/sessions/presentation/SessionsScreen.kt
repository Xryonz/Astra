package app.astra.mobile.feature.sessions.presentation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var confirmOthers by remember { mutableStateOf(false) }
    val others = state.sessions.count { !it.current }

    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Sessoes", marginalia = "dispositivos conectados", onBack = onBack)

            Spacer(Modifier.height(8.dp))
            MarginaliaLabel("sessoes ativas", Modifier.padding(start = 22.dp, bottom = 10.dp))

            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CosmicSpinner() }
                state.sessions.isEmpty() -> Text(
                    "Nenhuma sessao ativa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = astraColors.text3,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
                else -> Column(
                    Modifier.padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.sessions.forEach { s ->
                        SessionCard(
                            s = s,
                            revoking = state.revokingId == s.id,
                            onRevoke = { viewModel.revoke(s.id) },
                        )
                    }
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = astraColors.danger,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
            }

            if (others > 0) {
                Spacer(Modifier.height(22.dp))
                val shape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(shape)
                        .background(astraColors.raised)
                        .border(1.dp, astraColors.danger.copy(alpha = 0.4f), shape)
                        .clickable(enabled = !state.revokingOthers) { confirmOthers = true }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.revokingOthers) "Encerrando…" else "Encerrar outras sessoes ($others)",
                        style = MaterialTheme.typography.titleMedium,
                        color = astraColors.danger,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    AstraDialog(
        open = confirmOthers,
        onDismiss = { confirmOthers = false },
        title = "Encerrar outras sessoes?",
        confirmText = "Encerrar",
        onConfirm = { confirmOthers = false; viewModel.revokeOthers() },
        dismissText = "Cancelar",
    ) {
        Text(
            "Todos os outros dispositivos serao desconectados. Este aqui continua logado.",
            style = MaterialTheme.typography.bodyMedium,
            color = astraColors.text2,
        )
    }
}

@Composable
private fun SessionCard(s: SessionRow, revoking: Boolean, onRevoke: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, if (s.current) astraColors.accent.copy(alpha = 0.5f) else astraColors.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(astraColors.base)
                .border(1.dp, astraColors.border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (s.isMobile) "📱" else "🖥", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = s.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = astraColors.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (s.current) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "este",
                        style = MaterialTheme.typography.labelSmall,
                        color = astraColors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(astraColors.accentDim)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            MarginaliaLabel(
                buildString {
                    append(s.ip ?: "IP desconhecido")
                    val rel = relTime(s.lastUsed)
                    if (rel.isNotEmpty()) append(" · $rel")
                },
            )
        }
        if (!s.current) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (revoking) "…" else "encerrar",
                style = MaterialTheme.typography.labelMedium,
                color = astraColors.danger,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !revoking, onClick = onRevoke)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private fun relTime(iso: String?): String {
    if (iso == null) return ""
    return runCatching {
        val then = java.time.Instant.parse(iso)
        val sec = java.time.Duration.between(then, java.time.Instant.now()).seconds.coerceAtLeast(0)
        when {
            sec < 60 -> "agora"
            sec < 3600 -> "${sec / 60}m"
            sec < 86400 -> "${sec / 3600}h"
            sec < 2592000 -> "${sec / 86400}d"
            else -> "${sec / 2592000}mes"
        }
    }.getOrDefault("")
}
