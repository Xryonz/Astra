package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable as clicavelDoCompose

enum class TipoDeBotao { PRINCIPAL, SECUNDARIO, PERIGO }

enum class LarguraDoDialogo(val medida: Dp) { ESTREITA(360.dp), MEDIA(440.dp) }

val TextoDoDialogo: TextStyle
    get() = TextStyle(color = Obsidian.text2, fontSize = 13.sp, lineHeight = 19.sp)

@Composable
fun Confirmacao(
    titulo: String,
    consequencia: String?,
    acao: String,
    aoConfirmar: () -> Unit,
    aoFechar: () -> Unit,
    perigo: Boolean = true,
    cancelar: String? = "cancelar",
) {
    Dialogo(titulo = titulo, aoFechar = aoFechar) { fechar ->
        val focoInicial = rememberFocoAoAbrir()
        if (consequencia != null) {
            Spacer(Modifier.height(8.dp))
            Text(consequencia, style = TextoDoDialogo)
        }
        RodapeDoDialogo {
            if (cancelar != null) {
                BotaoDeDialogo(
                    rotulo = cancelar,
                    tipo = TipoDeBotao.SECUNDARIO,
                    foco = focoInicial.takeIf { perigo },
                    onClick = fechar,
                )
            }
            BotaoDeDialogo(
                rotulo = acao,
                tipo = if (perigo) TipoDeBotao.PERIGO else TipoDeBotao.PRINCIPAL,
                foco = focoInicial.takeIf { !perigo || cancelar == null },
                onClick = aoConfirmar,
            )
        }
    }
}

@Composable
internal fun Dialogo(
    titulo: String,
    aoFechar: () -> Unit,
    largura: LarguraDoDialogo = LarguraDoDialogo.ESTREITA,
    dispensavel: Boolean = true,
    animarEntrada: Boolean = true,
    conteudo: @Composable ColumnScope.(fechar: () -> Unit) -> Unit,
) {
    val reduzir = LocalReduceMotion.current
    val escopo = rememberCoroutineScope()
    val presenca = remember { Animatable(if (reduzir || !animarEntrada) 1f else 0f) }
    var fechando by remember { mutableStateOf(false) }
    var fechado by remember { mutableStateOf(false) }
    val aoFecharAgora by rememberUpdatedState(aoFechar)
    LaunchedEffect(Unit) {
        if (presenca.value < 1f) presenca.animateTo(1f, tween(ENTRADA_DO_DIALOGO_MS, easing = EaseOutStd))
    }
    val fechar: () -> Unit = {
        if (!fechando) {
            fechando = true
            if (reduzir) {
                fechado = true
                aoFecharAgora()
            } else {
                escopo.launch {
                    presenca.animateTo(0f, tween(SAIDA_DO_DIALOGO_MS, easing = EaseOutStd))
                    fechado = true
                    aoFecharAgora()
                }
            }
        }
    }
    val fecharAgora by rememberUpdatedState(fechar)
    if (fechado) return
    Popup(
        popupPositionProvider = CobrindoAJanela,
        onDismissRequest = { if (dispensavel) fecharAgora() },
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = presenca.value }
                .background(Obsidian.void.copy(alpha = ESCURO_ATRAS_DO_DIALOGO))
                .pointerInput(dispensavel) { detectTapGestures { if (dispensavel) fecharAgora() } }
                .onPreviewKeyEvent { e ->
                    if (dispensavel && e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                        fecharAgora()
                        true
                    } else {
                        false
                    }
                }
                .padding(MARGEM_DA_JANELA),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        val p = presenca.value
                        val escala = ESCALA_DE_ENTRADA + (1f - ESCALA_DE_ENTRADA) * p
                        scaleX = escala
                        scaleY = escala
                        translationY = (1f - p) * SUBIDA_DE_ENTRADA.toPx()
                    }
                    .width(largura.medida)
                    .clip(FormaDoDialogo)
                    .background(Obsidian.overlay)
                    .border(1.dp, Obsidian.borderDim, FormaDoDialogo)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = titulo }
                    .verticalScroll(rememberScrollState())
                    .padding(RESPIRO_DO_DIALOGO),
            ) {
                Text(
                    titulo,
                    style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, lineHeight = 22.sp, fontFamily = DmSerif),
                )
                conteudo(fechar)
            }
        }
    }
}

@Composable
internal fun rememberFocoAoAbrir(): FocusRequester {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    return foco
}

@Composable
internal fun RodapeDoDialogo(conteudo: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = conteudo,
    )
}

@Composable
internal fun BotaoDeDialogo(
    rotulo: String,
    tipo: TipoDeBotao,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ligado: Boolean = true,
    foco: FocusRequester? = null,
    icone: ImageVector? = null,
) {
    val toque = remember { MutableInteractionSource() }
    val parado = remember { MutableInteractionSource() }
    val sobre by toque.collectIsHoveredAsState()
    val aceso = sobre && ligado
    val fundo = when (tipo) {
        TipoDeBotao.PRINCIPAL -> if (aceso) Obsidian.active else Obsidian.hover
        TipoDeBotao.SECUNDARIO -> if (aceso) Obsidian.hover else Color.Transparent
        TipoDeBotao.PERIGO -> Obsidian.danger.copy(alpha = if (aceso) TINTA_DO_PERIGO_ACESO else TINTA_DO_PERIGO)
    }
    val cor = when {
        !ligado -> Obsidian.text3
        tipo == TipoDeBotao.PERIGO -> Obsidian.danger
        tipo == TipoDeBotao.PRINCIPAL || aceso -> Obsidian.text1
        else -> Obsidian.text2
    }
    Row(
        modifier
            .then(if (foco != null) Modifier.focusRequester(foco) else Modifier)
            .clickScale(if (ligado) toque else parado)
            .defaultMinSize(minHeight = ALTURA_DO_BOTAO)
            .clip(FormaDeBotao)
            .background(fundo)
            .hoverable(toque, enabled = ligado)
            .pointerHoverIcon(if (ligado) PointerIcon.Hand else PointerIcon.Default)
            .semantics { if (!ligado) disabled() }
            .clicavelDoCompose(interactionSource = toque, indication = null, role = Role.Button) {
                if (ligado) onClick()
            }
            .padding(horizontal = RESPIRO_DO_BOTAO),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) {
            LIcon(icone, tint = cor, size = 14.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(rotulo, style = TextStyle(color = cor, fontSize = 13.sp))
    }
}

@Composable
internal fun RotuloDeCampo(texto: String) {
    Text(texto, style = Tipo.rotulo, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
internal fun CampoDeDialogo(
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    dica: String = "",
    aoEnviar: () -> Unit = {},
    foco: FocusRequester? = null,
    ligado: Boolean = true,
    segredo: Boolean = false,
    umaLinha: Boolean = true,
    icone: ImageVector? = null,
    estilo: TextStyle = Tipo.corpo,
) {
    val toque = remember { MutableInteractionSource() }
    val focado by toque.collectIsFocusedAsState()
    BasicTextField(
        value = valor,
        onValueChange = aoMudar,
        modifier = modifier
            .fillMaxWidth()
            .then(if (foco != null) Modifier.focusRequester(foco) else Modifier),
        readOnly = !ligado,
        textStyle = estilo,
        keyboardOptions = KeyboardOptions(imeAction = if (umaLinha) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { aoEnviar() }),
        singleLine = umaLinha,
        maxLines = if (umaLinha) 1 else LINHAS_DO_CAMPO_LONGO,
        visualTransformation = if (segredo) PasswordVisualTransformation() else VisualTransformation.None,
        interactionSource = toque,
        cursorBrush = SolidColor(Obsidian.accent),
        decorationBox = { campo ->
            Row(
                Modifier
                    .clip(FormaDoCampo)
                    .background(Obsidian.base)
                    .border(
                        1.dp,
                        if (focado) Obsidian.accent.copy(alpha = BORDA_EM_FOCO) else Obsidian.borderDim,
                        FormaDoCampo,
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icone != null) {
                    LIcon(icone, tint = Obsidian.text3, size = 14.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Box(Modifier.weight(1f), propagateMinConstraints = true) {
                    if (valor.isEmpty() && dica.isNotEmpty()) {
                        Text(dica, style = estilo.copy(color = Obsidian.text3))
                    }
                    campo()
                }
            }
        },
    )
}

@Composable
internal fun RecadoDoDialogo(texto: String, sucesso: Boolean = false) {
    Text(
        texto,
        style = TextStyle(color = if (sucesso) Obsidian.success else Obsidian.danger, fontSize = 12.sp, lineHeight = 17.sp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
internal fun EscolhaDoDialogo(rotulo: String, ativa: Boolean, onClick: () -> Unit, icone: ImageVector? = null) {
    val toque = remember { MutableInteractionSource() }
    val sobre by toque.collectIsHoveredAsState()
    val cor = if (ativa) Obsidian.text1 else Obsidian.text2
    Row(
        Modifier
            .clickScale(toque)
            .defaultMinSize(minHeight = ALTURA_DA_ESCOLHA)
            .clip(FormaDeBotao)
            .background(if (ativa) Obsidian.active else if (sobre) Obsidian.hover else Color.Transparent)
            .border(
                1.dp,
                if (ativa) Obsidian.accent.copy(alpha = BORDA_DA_ESCOLHA_ATIVA) else Obsidian.borderDim,
                FormaDeBotao,
            )
            .hoverable(toque)
            .semantics { selected = ativa }
            .clickable(interactionSource = toque, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) {
            LIcon(icone, tint = cor, size = 14.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(rotulo, style = TextStyle(color = cor, fontSize = 12.sp))
    }
}

private val FormaDoDialogo = RoundedCornerShape(8.dp)
private val FormaDoCampo = RoundedCornerShape(8.dp)
private val RESPIRO_DO_DIALOGO = 20.dp
private val MARGEM_DA_JANELA = 24.dp
private val SUBIDA_DE_ENTRADA = 8.dp
private val ALTURA_DO_BOTAO = 32.dp
private val ALTURA_DA_ESCOLHA = 28.dp
private val RESPIRO_DO_BOTAO = 14.dp
private const val ESCURO_ATRAS_DO_DIALOGO = 0.72f
private const val ESCALA_DE_ENTRADA = 0.98f
private const val ENTRADA_DO_DIALOGO_MS = 160
private const val SAIDA_DO_DIALOGO_MS = 120
private const val TINTA_DO_PERIGO = 0.08f
private const val TINTA_DO_PERIGO_ACESO = 0.12f
private const val BORDA_EM_FOCO = 0.6f
private const val BORDA_DA_ESCOLHA_ATIVA = 0.55f
private const val LINHAS_DO_CAMPO_LONGO = 4
