package app.astra.mobile.feature.profile.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.AlcaDaFolha
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.BotaoDeTexto
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.DisplayFontOptions
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.FolhaQueSobe
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.displayFontFamily
import app.astra.mobile.ui.components.readImageBytes
import app.astra.mobile.ui.components.rememberEstadoDaFolha
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.delay

private const val ENCOLHE_MS = 220
private const val SALVO_VISIVEL_MS = 1_600L
private const val ESCURO_DA_FOLHA_DO_BANNER = 0.2f
private val ALTURA_DO_BANNER = 96.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditarPerfilScreen(
    onBack: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    val semMovimento = LocalAppPrefs.current.reduceMotion
    var folhaDoBanner by remember { mutableStateOf(false) }
    val focoDoRecado = remember { FocusRequester() }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        readImageBytes(ctx, uri)?.let { (bytes, mime, _) -> viewModel.uploadAvatar(bytes, mime) }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        readImageBytes(ctx, uri)?.let { (bytes, mime, _) -> viewModel.uploadBanner(bytes, mime) }
    }
    val imageRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
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
                            alturaDoBanner = ALTURA_DO_BANNER,
                            fundoDoAnel = astraColors.base,
                            textoSemRecado = "Defina um recado",
                            aoTocarNaFoto = { if (!state.uploadingAvatar) avatarPicker.launch(imageRequest) },
                            rotuloDoToqueNaFoto = "trocar a foto",
                            aoTocarNoRecado = { focoDoRecado.requestFocus() },
                            modificadorDoBanner = Modifier
                                .clickable(
                                    enabled = !state.uploadingBanner,
                                    onClickLabel = if (temBanner) "abrir as opções do banner" else "escolher a imagem do banner",
                                ) {
                                    if (temBanner) folhaDoBanner = true else bannerPicker.launch(imageRequest)
                                }
                                .semantics { contentDescription = "Banner" }
                                .pointerInput(temBanner) {
                                    if (!temBanner) return@pointerInput
                                    detectVerticalDragGestures { mudanca, dy ->
                                        mudanca.consume()
                                        val passo = -dy / size.height * 100f
                                        viewModel.onBannerPositionY((viewModel.state.value.bannerPositionY + passo).toInt())
                                    }
                                },
                            sobreOBanner = {
                                if (state.uploadingBanner) Enviando(Modifier.fillMaxWidth().height(ALTURA_DO_BANNER))
                                SeloDeLapis(Modifier.align(Alignment.TopEnd).padding(10.dp))
                            },
                            presoAFoto = {
                                if (state.uploadingAvatar) Enviando(Modifier.matchParentSize().clip(CircleShape))
                                else SeloDeLapis(Modifier.align(Alignment.BottomEnd).padding(4.dp))
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CartaoDeSecao("Sobre você") {
                Spacer(Modifier.height(8.dp))
                EditorialField(
                    value = state.customStatus, onValue = viewModel::onCustomStatus,
                    label = "recado", placeholder = "No que você está pensando?",
                    enabled = !state.saving, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next,
                    modificadorDoCampo = Modifier.focusRequester(focoDoRecado),
                )
                Spacer(Modifier.height(16.dp))
                EditorialField(
                    value = state.pronouns, onValue = viewModel::onPronouns,
                    label = "pronomes", placeholder = "ele/dele",
                    enabled = !state.saving, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(16.dp))
                EditorialField(
                    value = state.bio, onValue = viewModel::onBio,
                    label = "bio", placeholder = "Fale de você",
                    enabled = !state.saving, keyboardType = KeyboardType.Text, imeAction = ImeAction.Default,
                    singleLine = false,
                )
            }

            CartaoDeSecao("Aparência") {
                Spacer(Modifier.height(8.dp))
                EscolhaDaFonte(atual = state.displayFont, aoEscolher = viewModel::onDisplayFont)
                Spacer(Modifier.height(18.dp))
                EscolhaDaCorDoPerfil(atual = state.bannerColor, aoEscolher = viewModel::onCorDoPerfil)
            }
        }

        BarraDeSalvar(
            estado = state,
            aoSalvar = viewModel::save,
            aoDesfazer = viewModel::desfazer,
            aoFecharAviso = viewModel::limparErro,
            aoSumir = viewModel::esquecerSalvo,
        )
    }

    if (folhaDoBanner) {
        FolhaDoBanner(
            escala = state.bannerScale,
            aoTrocar = { bannerPicker.launch(imageRequest) },
            aoRemover = viewModel::removeBanner,
            aoMudarEscala = viewModel::onBannerScale,
            aoFechar = { folhaDoBanner = false },
        )
    }
}

@Composable
private fun SeloDeLapis(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(astraColors.void.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Pencil, contentDescription = null, tint = astraColors.text1, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun Enviando(modifier: Modifier) {
    Box(
        modifier.background(astraColors.void.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = astraColors.accent)
    }
}

@Composable
private fun EscolhaDaFonte(atual: String, aoEscolher: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MarginaliaLabel("fonte do nome")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DisplayFontOptions.forEach { (id, label) ->
                val ativa = atual == id
                val formaDaFonte = RoundedCornerShape(12.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(formaDaFonte)
                        .background(if (ativa) astraColors.accentDim else astraColors.overlay)
                        .border(1.dp, if (ativa) astraColors.accent else astraColors.border, formaDaFonte)
                        .clickable { aoEscolher(id) }
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
}

@Composable
private fun BarraDeSalvar(
    estado: ProfileEditUiState,
    aoSalvar: () -> Unit,
    aoDesfazer: () -> Unit,
    aoFecharAviso: () -> Unit,
    aoSumir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val visivel = estado.dirty || estado.saving || estado.saved || estado.error != null
    LaunchedEffect(estado.saved) {
        if (estado.saved) {
            delay(SALVO_VISIVEL_MS)
            aoSumir()
        }
    }
    AnimatedVisibility(
        visible = visivel,
        enter = if (semMovimento) fadeIn(tween(0))
        else expandVertically(tween(240, easing = EaseOutSoft), expandFrom = Alignment.Top) +
            slideInVertically(tween(240, easing = EaseOutSoft)) { it / 2 } + fadeIn(tween(200)),
        exit = if (semMovimento) fadeOut(tween(0))
        else shrinkVertically(tween(200, easing = EaseOutSoft), shrinkTowards = Alignment.Top) +
            slideOutVertically(tween(200, easing = EaseOutSoft)) { it / 2 } + fadeOut(tween(160)),
        modifier = modifier,
    ) {
        val forma = RoundedCornerShape(16.dp)
        val enviando = estado.uploadingAvatar || estado.uploadingBanner
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .shadow(10.dp, forma, clip = false)
                .clip(forma)
                .background(astraColors.overlay)
                .border(1.dp, astraColors.borderMid, forma)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val erro = estado.error
            Text(
                text = when {
                    erro != null -> erro
                    estado.saved -> "Salvo"
                    else -> "Alterações não salvas"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    erro != null -> astraColors.danger
                    estado.saved -> astraColors.success
                    else -> astraColors.text2
                },
                maxLines = 2,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            when {
                estado.dirty || estado.saving -> {
                    BotaoDeTexto(onClick = aoDesfazer, enabled = !estado.saving && !enviando) {
                        Text("Desfazer", color = astraColors.text2)
                    }
                    Spacer(Modifier.width(4.dp))
                    AstraButton(text = "Salvar", onClick = aoSalvar, enabled = !enviando, loading = estado.saving)
                }
                erro != null -> BotaoDeTexto(onClick = aoFecharAviso) { Text("Fechar", color = astraColors.text2) }
            }
        }
    }
}

@Composable
private fun FolhaDoBanner(
    escala: Int,
    aoTrocar: () -> Unit,
    aoRemover: () -> Unit,
    aoMudarEscala: (Int) -> Unit,
    aoFechar: () -> Unit,
) {
    Dialog(
        onDismissRequest = aoFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val folha = rememberEstadoDaFolha(aoFechar)
        FolhaQueSobe(folha, rotuloDoFundo = "Fechar as opções do banner", escuroMaximo = ESCURO_DA_FOLHA_DO_BANNER) {
            AlcaDaFolha(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp))
            Text(
                "Banner",
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text1,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            )
            AcaoDaFolha(Lucide.Image, "Trocar imagem", astraColors.text1) { folha.fechar { aoFechar(); aoTrocar() } }
            AcaoDaFolha(Lucide.Trash2, "Remover imagem", astraColors.danger) { folha.fechar { aoFechar(); aoRemover() } }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 18.dp)) {
                MarginaliaLabel("zoom")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = escala.toFloat().coerceIn(50f, 200f),
                        onValueChange = { aoMudarEscala(it.toInt()) },
                        valueRange = 50f..200f,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("$escala%", style = MaterialTheme.typography.labelMedium, color = astraColors.text3)
                }
                MarginaliaLabel("arraste o banner para reposicionar")
            }
        }
    }
}

@Composable
private fun AcaoDaFolha(icone: ImageVector, rotulo: String, cor: Color, aoTocar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = aoTocar)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(rotulo, style = MaterialTheme.typography.bodyLarge, color = cor)
    }
}
