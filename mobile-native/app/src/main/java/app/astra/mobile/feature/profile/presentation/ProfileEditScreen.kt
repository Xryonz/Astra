package app.astra.mobile.feature.profile.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.readImageBytes
import app.astra.mobile.ui.theme.astraColors
import coil.compose.AsyncImage

@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    // Photo Picker do Android (sem permissao). Le os bytes e manda pro VM.
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        readImageBytes(ctx, uri)?.let { (bytes, mime, _) -> viewModel.uploadAvatar(bytes, mime) }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        readImageBytes(ctx, uri)?.let { (bytes, mime, _) -> viewModel.uploadBanner(bytes, mime) }
    }
    val imageRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

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
                // ── Preview do cartao: banner (imagem ou cor) + avatar tocavel ──
                val bannerColor = parseHexColor(state.bannerColor) ?: astraColors.overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bannerColor),
                ) {
                    if (state.bannerUrl.isNotBlank()) {
                        AsyncImage(
                            model = state.bannerUrl,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    // Scrim pra legibilidade do nome.
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.28f)),
                    )
                    if (state.uploadingBanner) {
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = Color.White)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { avatarPicker.launch(imageRequest) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AstraAvatar(state.avatarUrl.ifBlank { null }, state.displayName, size = 60)
                            if (state.uploadingAvatar) {
                                Box(
                                    Modifier.size(60.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                }
                            }
                        }
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

                Spacer(Modifier.height(14.dp))
                // ── Acoes de imagem ──
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UploadChip(
                        label = "Trocar foto",
                        busy = state.uploadingAvatar,
                        modifier = Modifier.weight(1f),
                        onClick = { avatarPicker.launch(imageRequest) },
                    )
                    UploadChip(
                        label = "Trocar banner",
                        busy = state.uploadingBanner,
                        modifier = Modifier.weight(1f),
                        onClick = { bannerPicker.launch(imageRequest) },
                    )
                }
                if (state.bannerUrl.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "remover banner",
                        style = MaterialTheme.typography.labelMedium,
                        color = astraColors.text3,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = viewModel::removeBanner)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                MarginaliaLabel(
                    "Foto ou GIF · comprime sozinho · GIF anima",
                    Modifier.padding(top = 8.dp),
                )

                Spacer(Modifier.height(20.dp))
                EditorialField(
                    value = state.bannerColor, onValue = viewModel::onBannerColor,
                    label = "cor do banner (sem imagem)", placeholder = "#1a1a2e",
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
                    AstraButton(
                        text = "SALVAR",
                        onClick = viewModel::save,
                        enabled = state.dirty,
                        loading = state.saving,
                    )
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

/** Botao-pilula editorial pra disparar o picker; vira spinner enquanto sobe. */
@Composable
private fun UploadChip(
    label: String,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = astraColors.accent)
        } else {
            Text(label, style = MaterialTheme.typography.titleSmall, color = astraColors.text1)
        }
    }
}

/** "#RRGGBB" -> Color. Invalido/vazio -> null (usa fallback). */
private fun parseHexColor(raw: String): Color? {
    val h = raw.trim().removePrefix("#")
    if (h.length != 6) return null
    return runCatching { Color("FF$h".toLong(16)) }.getOrNull()
}
