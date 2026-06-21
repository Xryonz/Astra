package app.astra.mobile.feature.server.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.readImageBytes
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import coil.compose.AsyncImage

@Composable
fun ServerEditScreen(
    onBack: () -> Unit,
    viewModel: ServerEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        readImageBytes(ctx, uri)?.let { (bytes, mime, _) -> viewModel.uploadIcon(bytes, mime) }
    }
    val imageRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    CosmicBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            EditorialTopBar(title = "Editar constelacao", marginalia = "icone e nome", onBack = onBack)

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Icone (tocavel) ──
                val shape = RoundedCornerShape(22.dp)
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(shape)
                        .background(astraColors.raised)
                        .border(1.dp, astraColors.borderMid, shape)
                        .clickable { iconPicker.launch(imageRequest) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.iconUrl.isNotBlank()) {
                        AsyncImage(
                            model = state.iconUrl,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize().clip(shape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = state.name.take(1).uppercase().ifBlank { "?" },
                            fontFamily = DmSerif,
                            fontSize = 40.sp,
                            color = astraColors.accent,
                        )
                    }
                    if (state.uploadingIcon) {
                        Box(
                            Modifier.matchParentSize().clip(shape).background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Trocar icone",
                    style = MaterialTheme.typography.titleSmall,
                    color = astraColors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { iconPicker.launch(imageRequest) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                MarginaliaLabel("PNG, JPG, GIF ou WebP · 5MB · GIF anima")

                Spacer(Modifier.height(24.dp))
                EditorialField(
                    value = state.name, onValue = viewModel::onName,
                    label = "nome da constelacao", placeholder = "minha constelacao",
                    enabled = !state.saving, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
                )

                if (state.error != null) {
                    Spacer(Modifier.height(14.dp))
                    AuthErrorBox(state.error!!)
                }

                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.dirty && state.name.isNotBlank() && !state.saving,
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
            }
        }
    }
}
