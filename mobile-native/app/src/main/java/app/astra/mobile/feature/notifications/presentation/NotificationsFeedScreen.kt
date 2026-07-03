package app.astra.mobile.feature.notifications.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun NotificationsFeedScreen(
    onBack: () -> Unit,
    onOpenChannel: (id: String, name: String) -> Unit,
    onOpenDm: (id: String, name: String) -> Unit,
    viewModel: NotificationsFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(title = "Notificações", marginalia = "o que aconteceu", onBack = onBack)

            if (state.items.any { it.unread }) {
                Text(
                    text = "marcar todas como lidas",
                    style = MaterialTheme.typography.labelMedium,
                    color = astraColors.accent,
                    modifier = Modifier
                        .padding(start = 18.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = viewModel::markAllRead)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                state.items.isEmpty() && state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        state.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = astraColors.text3,
                    )
                }
                state.items.isEmpty() -> EmptyState(
                    line = "Céu quieto por enquanto",
                    hint = "mencoes e novidades pousam aqui",
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { n ->
                        NotificationRowItem(n) {
                            viewModel.markRead(n.id)
                            when {
                                n.conversationId != null -> onOpenDm(n.conversationId, n.authorName)
                                n.channelId != null -> onOpenChannel(n.channelId, n.channelName ?: "")
                            }
                        }
                    }
                    if (state.nextCursor != null) {
                        item {
                            Text(
                                text = if (state.loadingMore) "carregando..." else "ver mais antigas",
                                style = MaterialTheme.typography.labelMedium,
                                color = astraColors.accent,
                                modifier = Modifier
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !state.loadingMore, onClick = viewModel::loadMore)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }
                    item { Spacer(Modifier.height(28.dp)) }
                }
            }
        }
    }
}

@Composable
private fun NotificationRowItem(n: NotificationRow, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AstraAvatar(n.authorAvatar, n.authorName, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = buildString {
                        append(n.authorName)
                        append(" ")
                        append(verb(n))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (n.unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = astraColors.text1,
                    maxLines = 2,
                )
                if (n.preview.isNotBlank()) {
                    Text(
                        text = n.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = astraColors.text3,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MarginaliaLabel(relative(n.createdAt))
                if (n.unread) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(astraColors.accent))
                }
            }
        }
        HairlineRule(Modifier.padding(start = 70.dp))
    }
}

private fun verb(n: NotificationRow): String = when (n.type) {
    "mention" -> "te mencionou em #${n.channelName ?: "?"}"
    "reply" -> "respondeu você em #${n.channelName ?: "?"}"
    "reaction" -> "reagiu ${n.emoji ?: ""} à sua mensagem"
    "dm" -> "te mandou um sussurro"
    else -> "fez algo"
}

private fun relative(iso: String): String = runCatching {
    val then = Instant.parse(iso)
    val now = Instant.now()
    val m = ChronoUnit.MINUTES.between(then, now)
    when {
        m < 1 -> "agora"
        m < 60 -> "${m}m"
        m < 60 * 24 -> "${m / 60}h"
        else -> "${m / (60 * 24)}d"
    }
}.getOrDefault("")
