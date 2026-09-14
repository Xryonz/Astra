package app.astra.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Obsidian
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private val BRILHO = listOf(
    "....#....",
    "....#....",
    "...###...",
    "..#####..",
    "#########",
    "..#####..",
    "...###...",
    "....#....",
    "....#....",
)

private const val MARGEM = 1
private const val ALTURA_EM_CELULAS = 5f
private const val CELULAS_POR_BLOCO = 3f
private const val MINIMO_DE_BLOCOS = 4
private const val MAXIMO_DE_BLOCOS = 32

@Composable
fun BrilhoEmPixels(cor: Color, tamanho: Dp = 12.dp, rotulo: String? = null) {
    val contorno = Obsidian.void
    Box(
        Modifier
            .size(tamanho)
            .let { m -> if (rotulo == null) m.clearAndSetSemantics { } else m.semantics { contentDescription = rotulo } }
            .drawBehind { desenharSprite(BRILHO, cor, contorno) },
    )
}

@Composable
fun BarraDeXpEmPixels(fracao: Float, cor: Color, modifier: Modifier = Modifier) {
    val trilho = Obsidian.void
    val contorno = Obsidian.borderMid
    Box(modifier.drawBehind { desenharBarra(fracao.coerceIn(0f, 1f), cor, trilho, contorno) })
}

private fun DrawScope.desenharSprite(linhas: List<String>, preenchimento: Color, contorno: Color) {
    val colunas = linhas.first().length
    val emLargura = colunas + MARGEM * 2
    val emAltura = linhas.size + MARGEM * 2
    val celula = floor(minOf(size.width / emLargura, size.height / emAltura)).coerceAtLeast(1f)
    val sobraX = (size.width - celula * emLargura) / 2f
    val sobraY = (size.height - celula * emAltura) / 2f
    val quadrado = Size(celula, celula)

    fun cheio(x: Int, y: Int): Boolean =
        y in linhas.indices && x >= 0 && x < colunas && linhas[y][x] == '#'

    fun pintar(x: Int, y: Int, cor: Color) = drawRect(
        cor,
        Offset(sobraX + (x + MARGEM) * celula, sobraY + (y + MARGEM) * celula),
        quadrado,
    )

    for (y in -MARGEM..linhas.size) {
        for (x in -MARGEM..colunas) {
            if (cheio(x, y)) continue
            val encosta = (-1..1).any { dy -> (-1..1).any { dx -> cheio(x + dx, y + dy) } }
            if (encosta) pintar(x, y, contorno)
        }
    }
    for (y in linhas.indices) {
        for (x in 0 until colunas) {
            if (cheio(x, y)) pintar(x, y, preenchimento)
        }
    }
}

private fun DrawScope.desenharBarra(fracao: Float, aceso: Color, trilho: Color, contorno: Color) {
    if (size.width <= 0f || size.height <= 0f) return
    val celula = floor(size.height / ALTURA_EM_CELULAS).coerceAtLeast(1f)
    val alturaInterna = size.height - celula * 2f
    val interior = size.width - celula * 2f
    if (alturaInterna <= 0f || interior <= 0f) return

    drawRect(contorno, Offset.Zero, size)
    drawRect(trilho, Offset(celula, celula), Size(interior, alturaInterna))

    val passo = celula * (CELULAS_POR_BLOCO + 1f)
    val blocos = ((interior + celula) / passo).toInt().coerceIn(MINIMO_DE_BLOCOS, MAXIMO_DE_BLOCOS)
    val larguraDoBloco = (interior - celula * (blocos - 1)) / blocos
    if (larguraDoBloco <= 0f) return

    val acesos = if (fracao <= 0f) 0 else max(1, (fracao * blocos).roundToInt())
    var x = celula
    repeat(blocos) { i ->
        if (i < acesos) drawRect(aceso, Offset(x, celula), Size(larguraDoBloco, alturaInterna))
        x += larguraDoBloco + celula
    }
}
