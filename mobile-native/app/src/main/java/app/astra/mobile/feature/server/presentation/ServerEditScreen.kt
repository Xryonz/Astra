package app.astra.mobile.feature.server.presentation

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.BuildConfig
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.AstraSwitch
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.readImageBytes
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import zed.rainxch.rikkaui.components.ui.toast.LocalToastHostState

@Composable
fun ServerEditScreen(
    onBack: () -> Unit,
    viewModel: ServerEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val toast = LocalToastHostState.current
    val scope = rememberCoroutineScope()
    var regenOpen by remember { mutableStateOf(false) }

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        readImageBytes(ctx, uri)?.let { (bytes, mime, _) -> viewModel.uploadIcon(bytes, mime) }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        readImageBytes(ctx, uri)?.let { (bytes, mime, _) -> viewModel.uploadBanner(bytes, mime) }
    }
    val imageRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    CosmicBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            EditorialTopBar(title = "Visao geral", marginalia = "identidade e convite", onBack = onBack)

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

                // ---- Icone ----
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

                // ---- Nome ----
                Spacer(Modifier.height(24.dp))
                EditorialField(
                    value = state.name, onValue = viewModel::onName,
                    label = "nome da constelacao", placeholder = "minha constelacao",
                    enabled = !state.saving, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next,
                )

                // ---- Descricao ----
                Spacer(Modifier.height(16.dp))
                EditorialField(
                    value = state.description, onValue = viewModel::onDescription,
                    label = "descricao (aparece no Descobrir)", placeholder = "sobre o que e essa constelacao",
                    enabled = !state.saving, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
                )

                // ---- Banner ----
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MarginaliaLabel("banner")
                    Spacer(Modifier.weight(1f))
                    if (state.bannerUrl.isNotBlank()) {
                        Text(
                            text = "remover",
                            style = MaterialTheme.typography.titleSmall,
                            color = astraColors.danger,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.removeBanner() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val bannerShape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f)
                        .clip(bannerShape)
                        .background(astraColors.raised)
                        .border(1.dp, astraColors.borderMid, bannerShape)
                        .clickable { bannerPicker.launch(imageRequest) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.bannerUrl.isNotBlank()) {
                        AsyncImage(
                            model = state.bannerUrl,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize().clip(bannerShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        MarginaliaLabel("toque pra escolher · 8MB · GIF anima")
                    }
                    if (state.uploadingBanner) {
                        Box(
                            Modifier.matchParentSize().clip(bannerShape).background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                        }
                    }
                }

                // ---- Publica ----
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Constelacao publica", style = MaterialTheme.typography.titleSmall, color = astraColors.text1)
                        MarginaliaLabel("aparece no Descobrir; qualquer um entra sem convite")
                    }
                    Spacer(Modifier.width(12.dp))
                    AstraSwitch(
                        checked = state.isPublic,
                        onCheckedChange = viewModel::onPublic,
                        enabled = !state.saving,
                    )
                }

                // ---- Retencao ----
                Spacer(Modifier.height(22.dp))
                MarginaliaLabel("apagar mensagens antigas", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RETENTION_PRESETS.forEach { (days, label) ->
                        val active = state.retentionDays == days
                        val rShape = RoundedCornerShape(10.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rShape)
                                .background(if (active) astraColors.accentDim else Color.Transparent)
                                .border(1.dp, if (active) astraColors.accent else astraColors.border, rShape)
                                .clickable { viewModel.onRetention(days) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (active) astraColors.accent else astraColors.text1,
                                modifier = Modifier.weight(1f),
                            )
                            if (active) Text("✓", color = astraColors.accent, fontSize = 15.sp)
                        }
                    }
                }

                if (state.error != null) {
                    Spacer(Modifier.height(14.dp))
                    AuthErrorBox(state.error!!)
                }

                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AstraButton(
                        text = "SALVAR",
                        onClick = viewModel::save,
                        enabled = state.dirty && state.name.isNotBlank(),
                        loading = state.saving,
                    )
                    if (state.saved) {
                        Spacer(Modifier.width(12.dp))
                        MarginaliaLabel("salvo ✓", color = astraColors.success)
                    }
                }

                // ---- Convite ----
                val code = state.inviteCode
                if (!code.isNullOrBlank()) {
                    Spacer(Modifier.height(28.dp))
                    HairlineRule()
                    Spacer(Modifier.height(16.dp))
                    MarginaliaLabel("convite", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    val link = BuildConfig.BASE_URL.trimEnd('/') + "/i/" + code
                    val fieldShape = RoundedCornerShape(12.dp)
                    Text(
                        text = link,
                        style = MaterialTheme.typography.bodySmall,
                        color = astraColors.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(fieldShape)
                            .background(astraColors.raised)
                            .border(1.dp, astraColors.border, fieldShape)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InviteAction("Copiar") {
                            clipboard.setText(AnnotatedString(link))
                            scope.launch { toast.show("Link copiado") }
                        }
                        InviteAction("Compartilhar") { shareInviteLink(ctx, link) }
                        InviteAction(if (state.regenerating) "..." else "Regenerar", danger = true) {
                            if (!state.regenerating) regenOpen = true
                        }
                    }
                    MarginaliaLabel("regenerar invalida o link antigo", Modifier.padding(top = 8.dp))
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    AstraDialog(
        open = regenOpen,
        onDismiss = { regenOpen = false },
        title = "Regenerar convite?",
        confirmText = "Regenerar",
        onConfirm = { regenOpen = false; viewModel.regenerateInvite() },
    ) {
        MarginaliaLabel("o link atual para de funcionar na hora")
    }
}

@Composable
private fun InviteAction(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (danger) astraColors.danger else astraColors.accent,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, astraColors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun shareInviteLink(context: Context, link: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Entra na minha constelacao no Astra: $link")
    }
    context.startActivity(Intent.createChooser(send, "Compartilhar convite"))
}
