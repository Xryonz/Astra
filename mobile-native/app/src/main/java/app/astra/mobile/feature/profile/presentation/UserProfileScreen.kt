package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.feature.friends.domain.model.Presence
import app.astra.mobile.feature.profile.domain.model.MutualServer
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors
import coil.compose.AsyncImage

@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val title = state.view?.profile?.displayName ?: viewModel.initialName

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(title = title, marginalia = "perfil", onBack = onBack)

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
                        TextButton(onClick = viewModel::load) { Text("Tentar de novo", color = astraColors.accent) }
                    }
                }
                state.view != null -> {
                    val v = state.view!!
                    val p = v.profile
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        // ── Banner ──
                        val bannerColor = parseHexColor(p.bannerColor) ?: astraColors.overlay
                        Box(Modifier.fillMaxWidth().height(120.dp).background(bannerColor)) {
                            if (!p.bannerUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = p.bannerUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }

                        // ── Avatar (sobrepoe o banner) + infos ──
                        Column(
                            modifier = Modifier
                                .offset(y = (-42).dp)
                                .padding(horizontal = 22.dp),
                        ) {
                            Box {
                                AstraAvatar(p.avatarUrl, p.displayName, size = 84)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(astraColors.void)
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(presenceColor(v.presence)),
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = p.displayName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = astraColors.text1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = buildString {
                                    append("@${p.username}")
                                    if (!p.pronouns.isNullOrBlank()) append("  ·  ${p.pronouns}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = astraColors.text3,
                            )

                            if (!p.bio.isNullOrBlank()) {
                                Spacer(Modifier.height(16.dp))
                                MarginaliaLabel("sobre")
                                Spacer(Modifier.height(6.dp))
                                Text(p.bio, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
                            }

                            if (p.createdAt != null) {
                                Spacer(Modifier.height(16.dp))
                                MarginaliaLabel("estrela desde ${p.createdAt.take(10)}")
                            }

                            if (v.mutual.isNotEmpty()) {
                                Spacer(Modifier.height(20.dp))
                                HairlineRule()
                                Spacer(Modifier.height(14.dp))
                                MarginaliaLabel("constelacoes em comum")
                                Spacer(Modifier.height(10.dp))
                                v.mutual.forEach { MutualRow(it) }
                            }

                            Spacer(Modifier.height(60.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MutualRow(s: MutualServer) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(astraColors.raised),
            contentAlignment = Alignment.Center,
        ) {
            Text(s.name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = astraColors.accent)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = s.name,
            style = MaterialTheme.typography.bodyMedium,
            color = astraColors.text1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MarginaliaLabel(s.role.lowercase())
    }
}

@Composable
private fun presenceColor(p: Presence): Color = when (p) {
    Presence.ONLINE -> astraColors.success
    Presence.IDLE -> astraColors.warning
    Presence.DND -> astraColors.danger
    Presence.OFFLINE -> astraColors.text3
}

private fun parseHexColor(raw: String?): Color? {
    if (raw == null) return null
    val h = raw.trim().removePrefix("#")
    if (h.length != 6) return null
    return runCatching { Color("FF$h".toLong(16)) }.getOrNull()
}
