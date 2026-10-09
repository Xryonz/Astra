package app.astra.mobile.ui.components

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as AreaNaTela
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.astra.mobile.core.upload.ImageEncoder
import app.astra.mobile.ui.theme.astraColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val ZOOM_MINIMO_DO_RECORTE = 100f
private const val ZOOM_MAXIMO_DO_RECORTE = 300f
private const val PROPORCAO_DO_PALCO = 324f / 210f
private const val LADO_MAXIMO_DA_ORIGEM = 2048
private const val ESCURO_FORA_DO_FURO = 0.68f
private const val PASSO_DA_ACAO = 0.1f
private val RESPIRO_DO_PALCO = 14.dp
private val ALTURA_MAXIMA_DO_PALCO = 240.dp
private val CANTO_DO_PALCO = RoundedCornerShape(10.dp)
private val CANTO_DO_FURO = 8.dp

@Composable
fun JanelaDeRecorte(
    bytes: ByteArray,
    proporcao: Float,
    titulo: String,
    aoAplicar: (Bitmap, Rect) -> Unit,
    aoFechar: () -> Unit,
    redondo: Boolean = false,
    aviso: String? = null,
) {
    var origem by remember(bytes) { mutableStateOf<Bitmap?>(null) }
    var falhou by remember(bytes) { mutableStateOf(false) }
    LaunchedEffect(bytes) {
        val lida = ImageEncoder.decodeForCrop(bytes, LADO_MAXIMO_DA_ORIGEM)
        if (lida == null) falhou = true else origem = lida
    }
    var zoom by remember(bytes) { mutableFloatStateOf(ZOOM_MINIMO_DO_RECORTE) }
    var deslocamento by remember(bytes) { mutableStateOf(Offset.Zero) }
    var palco by remember { mutableStateOf(IntSize.Zero) }

    val respiro = with(LocalDensity.current) { RESPIRO_DO_PALCO.toPx() }
    val larguraDoFuro = min(palco.width - respiro * 2, (palco.height - respiro * 2) * proporcao).coerceAtLeast(1f)
    val alturaDoFuro = larguraDoFuro / proporcao
    val imagem = origem
    val cobre = if (imagem == null) 1f else max(larguraDoFuro / imagem.width, alturaDoFuro / imagem.height)

    fun limitar(alvo: Offset, escala: Float): Offset {
        val i = imagem ?: return Offset.Zero
        val folgaX = abs(i.width * escala - larguraDoFuro) / 2f
        val folgaY = abs(i.height * escala - alturaDoFuro) / 2f
        return Offset(alvo.x.coerceIn(-folgaX, folgaX), alvo.y.coerceIn(-folgaY, folgaY))
    }

    fun mover(passo: Offset): Boolean {
        deslocamento = limitar(deslocamento + passo, cobre * zoom / 100f)
        return true
    }

    AstraDialog(
        open = true,
        onDismiss = aoFechar,
        title = titulo,
        confirmText = "Aplicar",
        confirmEnabled = imagem != null,
        onConfirm = {
            imagem?.let { i ->
                val escala = cobre * zoom / 100f
                val enquadrado = limitar(deslocamento, escala)
                val x = i.width / 2f - (larguraDoFuro / 2f + enquadrado.x) / escala
                val y = i.height / 2f - (alturaDoFuro / 2f + enquadrado.y) / escala
                val w = larguraDoFuro / escala
                val h = alturaDoFuro / escala
                aoAplicar(i, Rect(x.roundToInt(), y.roundToInt(), (x + w).roundToInt(), (y + h).roundToInt()))
            }
        },
    ) {
        Text(
            "Arraste para enquadrar; aproxime com dois dedos ou pela barra.",
            style = MaterialTheme.typography.bodySmall,
            color = astraColors.text3,
        )
        if (aviso != null) {
            Text(aviso, style = MaterialTheme.typography.bodySmall, color = astraColors.text3)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = ALTURA_MAXIMA_DO_PALCO)
                .aspectRatio(PROPORCAO_DO_PALCO)
                .clip(CANTO_DO_PALCO)
                .background(astraColors.void)
                .onSizeChanged { palco = it },
            contentAlignment = Alignment.Center,
        ) {
            val desenho = remember(imagem) { imagem?.asImageBitmap() }
            if (imagem == null || desenho == null) {
                Text(
                    if (falhou) "Não foi possível abrir esta imagem. Escolha outra." else "Carregando…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (falhou) astraColors.danger else astraColors.text3,
                )
            } else {
                val contorno = astraColors.accent.copy(alpha = 0.85f)
                val veu = astraColors.void.copy(alpha = ESCURO_FORA_DO_FURO)
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(imagem, palco) {
                            detectTransformGestures { _, arrasto, fator, _ ->
                                zoom = (zoom * fator).coerceIn(ZOOM_MINIMO_DO_RECORTE, ZOOM_MAXIMO_DO_RECORTE)
                                deslocamento = limitar(deslocamento + arrasto, cobre * zoom / 100f)
                            }
                        }
                        .semantics {
                            contentDescription = "Área do recorte"
                            customActions = listOf(
                                CustomAccessibilityAction("Mostrar mais acima") { mover(Offset(0f, alturaDoFuro * PASSO_DA_ACAO)) },
                                CustomAccessibilityAction("Mostrar mais abaixo") { mover(Offset(0f, -alturaDoFuro * PASSO_DA_ACAO)) },
                                CustomAccessibilityAction("Mostrar mais à esquerda") { mover(Offset(larguraDoFuro * PASSO_DA_ACAO, 0f)) },
                                CustomAccessibilityAction("Mostrar mais à direita") { mover(Offset(-larguraDoFuro * PASSO_DA_ACAO, 0f)) },
                            )
                        },
                ) {
                    val escala = cobre * zoom / 100f
                    val enquadrado = limitar(deslocamento, escala)
                    val larguraDesenhada = imagem.width * escala
                    val alturaDesenhada = imagem.height * escala
                    drawImage(
                        image = desenho,
                        dstOffset = IntOffset(
                            (size.width / 2f + enquadrado.x - larguraDesenhada / 2f).roundToInt(),
                            (size.height / 2f + enquadrado.y - alturaDesenhada / 2f).roundToInt(),
                        ),
                        dstSize = IntSize(
                            larguraDesenhada.roundToInt().coerceAtLeast(1),
                            alturaDesenhada.roundToInt().coerceAtLeast(1),
                        ),
                    )
                    val furo = AreaNaTela(
                        Offset((size.width - larguraDoFuro) / 2f, (size.height - alturaDoFuro) / 2f),
                        Size(larguraDoFuro, alturaDoFuro),
                    )
                    val caminhoDoFuro = Path().apply {
                        if (redondo) addOval(furo) else addRoundRect(RoundRect(furo, CornerRadius(CANTO_DO_FURO.toPx())))
                    }
                    val caminhoDoPalco = Path().apply { addRect(AreaNaTela(Offset.Zero, size)) }
                    drawPath(Path.combine(PathOperation.Difference, caminhoDoPalco, caminhoDoFuro), veu)
                    drawPath(caminhoDoFuro, contorno, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarginaliaLabel("zoom")
            Slider(
                value = zoom,
                onValueChange = {
                    zoom = it
                    deslocamento = limitar(deslocamento, cobre * it / 100f)
                },
                valueRange = ZOOM_MINIMO_DO_RECORTE..ZOOM_MAXIMO_DO_RECORTE,
                enabled = imagem != null,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .semantics {
                        contentDescription = "Zoom do recorte"
                        stateDescription = "${zoom.roundToInt()}%"
                    },
            )
            Text("${zoom.roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = astraColors.text3)
        }
    }
}
