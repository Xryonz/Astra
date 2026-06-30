package app.astra.mobile.feature.wishing.presentation

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.WishDto
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import zed.rainxch.rikkaui.components.ui.input.Input
import zed.rainxch.rikkaui.components.ui.input.InputAnimation

@Composable
fun WishingScreen(
    onBack: () -> Unit,
    viewModel: WishingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val canPost = state.input.trim().length >= WISH_MIN && !state.posting

    CosmicBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            EditorialTopBar(title = "Estrela dos desejos", marginalia = "pendure um pedido no ceu", onBack = onBack)

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(8.dp))

                Column(Modifier.padding(horizontal = 18.dp)) {
                    Input(
                        value = state.input,
                        onValueChange = viewModel::onInput,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "O que voce sonha pro Astra?",
                        singleLine = false,
                        animation = InputAnimation.Glow,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "minimo $WISH_MIN, maximo $WISH_MAX",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = astraColors.text3,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${state.input.length}/$WISH_MAX",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.input.length > WISH_MAX - 60) astraColors.accent else astraColors.text3,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(if (canPost) astraColors.accent else astraColors.base)
                            .border(1.dp, if (canPost) astraColors.accent else astraColors.borderMid, shape)
                            .clickable(enabled = canPost, onClick = viewModel::post)
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.posting) "Pendurando…" else "Pendurar no ceu",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (canPost) astraColors.textInv else astraColors.text3,
                        )
                    }
                    if (state.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = astraColors.danger)
                    }
                }

                Spacer(Modifier.height(24.dp))
                MarginaliaLabel("— o ceu agora · global", Modifier.padding(start = 22.dp, bottom = 10.dp))

                when {
                    state.loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CosmicSpinner() }
                    state.items.isEmpty() -> Text(
                        "O ceu esta vazio. Seja o primeiro a sonhar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = astraColors.text3,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                    else -> Column(
                        Modifier.padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.items.forEach { WishCard(it) }
                        if (state.nextCursor != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (state.loadingMore) "Carregando…" else "Ver mais antigos",
                                style = MaterialTheme.typography.labelLarge,
                                color = astraColors.accent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !state.loadingMore, onClick = viewModel::loadMore)
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun WishCard(w: WishDto) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .padding(14.dp),
    ) {
        AstraAvatar(w.author?.avatarUrl, w.author?.displayName ?: "?", size = 34)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = w.author?.displayName ?: "Alguem",
                    fontFamily = DmSerif,
                    style = MaterialTheme.typography.titleSmall,
                    color = astraColors.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = relTime(w.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = astraColors.text3,
                )
            }
            if (!w.author?.username.isNullOrBlank()) {
                Text(
                    text = "@${w.author?.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = astraColors.text3,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = w.content,
                style = MaterialTheme.typography.bodyMedium,
                color = astraColors.text2,
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
