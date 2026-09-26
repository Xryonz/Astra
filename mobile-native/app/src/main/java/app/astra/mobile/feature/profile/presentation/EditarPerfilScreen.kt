package app.astra.mobile.feature.profile.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.ColorGradientPicker
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.DisplayFontOptions
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.displayFontFamily
import app.astra.mobile.ui.components.readImageBytes
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors

private const val ENCOLHE_MS = 220

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditarPerfilScreen(
    onBack: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    val semMovimento = LocalAppPrefs.current.reduceMotion

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        readImageBytes(ctx, uri)?.let { (bytes, mime, _) -> viewModel.uploadAvatar(bytes, mime) }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        readImageBytes(ctx, uri)?.let { (bytes, mime, _) -> viewModel.uploadBanner(bytes, mime) }
    }
    val imageRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Column(Modifier.fillMaxSize().imePadding()) {
        EditorialTopBar(title = "Editar perfil", onBack = onBack)

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
            return@Column
        }

        val previa = PerfilVisivel(
            nome = state.displayName.ifBlank { state.username },
            usuario = state.username,
            avatar = state.avatarUrl.ifBlank { null },
            banner = state.bannerUrl.ifBlank { null },
            corDoBanner = state.bannerColor.ifBlank { null },
            bannerY = state.bannerPositionY,
            bannerEscala = state.bannerScale,
            fonte = state.displayFont,
            pronomes = state.pronouns,
            recado = state.customStatus,
            tema = state.profileTheme.ifBlank { null },
        )
        val forma = RoundedCornerShape(12.dp)
        val temBanner = state.bannerUrl.isNotBlank()
        AnimatedContent(
            targetState = WindowInsets.isImeVisible,
            transitionSpec = {
                if (semMovimento) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else fadeIn(tween(ENCOLHE_MS, easing = EaseOutSoft)) togetherWith fadeOut(tween(ENCOLHE_MS / 2))
            },
            label = "previa",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) { tecladoAberto ->
            FundoDoTema(
                previa.tema,
                Modifier
                    .fillMaxWidth()
                    .clip(forma)
                    .background(astraColors.base)
                    .border(1.dp, astraColors.border, forma),
            ) {
                if (tecladoAberto) {
                    FaixaDoPerfil(previa)
                } else {
                    Column(Modifier.padding(bottom = 14.dp)) {
                        CabecaDoPerfil(
                            p = previa,
                            alturaDoBanner = 96.dp,
                            fundoDoAnel = astraColors.base,
                            textoSemRecado = "Defina um recado",
                            aoTocarNaFoto = { avatarPicker.launch(imageRequest) },
                            rotuloDoToqueNaFoto = "trocar a foto",
                            modificadorDoBanner = Modifier.pointerInput(temBanner) {
                                if (!temBanner) return@pointerInput
                                detectVerticalDragGestures { mudanca, dy ->
                                    mudanca.consume()
                                    val passo = -dy / size.height * 100f
                                    viewModel.onBannerPositionY((viewModel.state.value.bannerPositionY + passo).toInt())
                                }
                            },
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                if (temBanner) {
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
                MarginaliaLabel("Foto ou GIF · comprime sozinho · GIF anima")
            }

            EditorialField(
                value = state.customStatus, onValue = viewModel::onCustomStatus,
                label = "recado", placeholder = "No que você está pensando?",
                enabled = !state.saving, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next,
            )
            EditorialField(
                value = state.pronouns, onValue = viewModel::onPronouns,
                label = "pronomes", placeholder = "ele/dele",
                enabled = !state.saving, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next,
            )
            EditorialField(
                value = state.bio, onValue = viewModel::onBio,
                label = "bio", placeholder = "Fale de você",
                enabled = !state.saving, keyboardType = KeyboardType.Text, imeAction = ImeAction.Default,
                singleLine = false,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarginaliaLabel("fonte do nome")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DisplayFontOptions.forEach { (id, label) ->
                        val ativa = state.displayFont == id
                        val formaDaFonte = RoundedCornerShape(12.dp)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(formaDaFonte)
                                .background(if (ativa) astraColors.accentDim else astraColors.raised)
                                .border(1.dp, if (ativa) astraColors.accent else astraColors.border, formaDaFonte)
                                .clickable { viewModel.onDisplayFont(id) }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Aa",
                                fontFamily = displayFontFamily(id),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (ativa) astraColors.accent else astraColors.text1,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = astraColors.text3)
                        }
                    }
                }
            }

            EditorialField(
                value = state.bannerColor, onValue = viewModel::onBannerColor,
                label = "cor do banner (sem imagem)", placeholder = "#1a1a2e",
                enabled = !state.saving, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next,
            )

            if (temBanner) {
                Column {
                    MarginaliaLabel("zoom do banner")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = state.bannerScale.toFloat().coerceIn(50f, 200f),
                            onValueChange = { viewModel.onBannerScale(it.toInt()) },
                            valueRange = 50f..200f,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${state.bannerScale}%", style = MaterialTheme.typography.labelMedium, color = astraColors.text3)
                    }
                    MarginaliaLabel("arraste o banner na prévia para reposicionar")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarginaliaLabel("tema do perfil")
                val semTema = state.profileTheme.isBlank()
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (semTema) astraColors.accentDim else astraColors.raised)
                        .border(1.dp, if (semTema) astraColors.accent else astraColors.border, RoundedCornerShape(10.dp))
                        .clickable { viewModel.onProfileTheme("") }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        "nenhum",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (semTema) astraColors.accent else astraColors.text2,
                    )
                }
                ColorGradientPicker(initial = state.profileTheme, onChange = viewModel::onProfileTheme)
            }

            state.error?.let { AuthErrorBox(it) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AstraButton(
                    text = "Salvar",
                    onClick = viewModel::save,
                    enabled = state.dirty,
                    loading = state.saving,
                )
                if (state.saved) {
                    Spacer(Modifier.width(12.dp))
                    MarginaliaLabel("salvo ✓", color = astraColors.success)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

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
