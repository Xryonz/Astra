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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    CosmicBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            EditorialTopBar(title = "Perfil", marginalia = "como te veem", onBack = onBack)

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp, vertical = 18.dp),
            ) {
                // ── Preview do cartao ──
                val banner = parseHexColor(state.bannerColor) ?: astraColors.overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(banner),
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AstraAvatar(state.avatarUrl.ifBlank { null }, state.displayName, size = 56)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = state.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildString {
                                    append("@${state.username}")
                                    if (state.pronouns.isNotBlank()) append("  ·  ${state.pronouns}")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                EditorialField(
                    value = state.avatarUrl, onValue = viewModel::onAvatarUrl,
                    label = "url do avatar", placeholder = "https://...",
                    enabled = !state.saving, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(20.dp))
                EditorialField(
                    value = state.bannerColor, onValue = viewModel::onBannerColor,
                    label = "cor do banner", placeholder = "#1a1a2e",
                    enabled = !state.saving, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(20.dp))
                EditorialField(
                    value = state.pronouns, onValue = viewModel::onPronouns,
                    label = "pronomes", placeholder = "ele/dele",
                    enabled = !state.saving, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(20.dp))
                EditorialField(
                    value = state.bio, onValue = viewModel::onBio,
                    label = "bio", placeholder = "fale de voce",
                    enabled = !state.saving, keyboardType = KeyboardType.Text, imeAction = ImeAction.Default,
                    singleLine = false,
                )

                if (state.error != null) {
                    Spacer(Modifier.height(14.dp))
                    AuthErrorBox(state.error!!)
                }

                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.dirty && !state.saving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = astraColors.accent,
                            contentColor = astraColors.textInv,
                            disabledContainerColor = astraColors.accent.copy(alpha = 0.4f),
                            disabledContentColor = astraColors.textInv.copy(alpha = 0.7f),
                        ),
                        modifier = Modifier.height(46.dp),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = astraColors.textInv)
                        } else {
                            Text("SALVAR", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.16.em)
                        }
                    }
                    if (state.saved) {
                        Spacer(Modifier.width(12.dp))
                        MarginaliaLabel("salvo ✓", color = astraColors.success)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** "#RRGGBB" -> Color. Invalido/vazio -> null (usa fallback). */
private fun parseHexColor(raw: String): Color? {
    val h = raw.trim().removePrefix("#")
    if (h.length != 6) return null
    return runCatching { Color("FF$h".toLong(16)) }.getOrNull()
}
