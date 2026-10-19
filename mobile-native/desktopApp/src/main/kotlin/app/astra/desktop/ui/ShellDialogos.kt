package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import kotlinx.coroutines.launch

enum class TipoDeBotao { PRINCIPAL, SECUNDARIO, PERIGO }

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
        val focoInicial = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focoInicial.requestFocus() } }
        if (consequencia != null) {
            Spacer(Modifier.height(8.dp))
            Text(consequencia, style = TextStyle(color = Obsidian.text2, fontSize = 13.sp, lineHeight = 19.sp))
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
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
    largura: Dp = LARGURA_DO_AVISO,
    conteudo: @Composable ColumnScope.(fechar: () -> Unit) -> Unit,
) {
    val reduzir = LocalReduceMotion.current
    val escopo = rememberCoroutineScope()
    val presenca = remember { Animatable(if (reduzir) 1f else 0f) }
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
        popupPositionProvider = JanelaDoDialogo,
        onDismissRequest = { fecharAgora() },
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = presenca.value }
                .background(Obsidian.void.copy(alpha = ESCURO_ATRAS_DO_DIALOGO))
                .pointerInput(Unit) { detectTapGestures { fecharAgora() } }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                        fecharAgora()
                        true
                    } else {
                        false
                    }
                },
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
                    .width(largura)
                    .clip(FormaDoDialogo)
                    .background(Obsidian.overlay)
                    .border(1.dp, Obsidian.borderDim, FormaDoDialogo)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = titulo }
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
internal fun BotaoDeDialogo(
    rotulo: String,
    tipo: TipoDeBotao,
    onClick: () -> Unit,
    ligado: Boolean = true,
    foco: FocusRequester? = null,
) {
    val toque = remember { MutableInteractionSource() }
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
    Box(
        Modifier
            .then(if (foco != null) Modifier.focusRequester(foco) else Modifier)
            .clickScale(toque)
            .defaultMinSize(minHeight = ALTURA_DO_BOTAO)
            .clip(FormaDeBotao)
            .background(fundo)
            .hoverable(toque, enabled = ligado)
            .clickable(interactionSource = toque, indication = null, enabled = ligado, role = Role.Button, onClick = onClick)
            .padding(horizontal = RESPIRO_DO_BOTAO),
        contentAlignment = Alignment.Center,
    ) {
        Text(rotulo, style = TextStyle(color = cor, fontSize = 13.sp))
    }
}

private object JanelaDoDialogo : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

private val FormaDoDialogo = RoundedCornerShape(8.dp)
private val LARGURA_DO_AVISO = 360.dp
private val RESPIRO_DO_DIALOGO = 20.dp
private val SUBIDA_DE_ENTRADA = 8.dp
private val ALTURA_DO_BOTAO = 32.dp
private val RESPIRO_DO_BOTAO = 14.dp
private const val ESCURO_ATRAS_DO_DIALOGO = 0.72f
private const val ESCALA_DE_ENTRADA = 0.98f
private const val ENTRADA_DO_DIALOGO_MS = 160
private const val SAIDA_DO_DIALOGO_MS = 120
private const val TINTA_DO_PERIGO = 0.08f
private const val TINTA_DO_PERIGO_ACESO = 0.12f
