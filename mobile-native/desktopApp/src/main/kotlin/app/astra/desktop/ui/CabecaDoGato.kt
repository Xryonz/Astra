package app.astra.desktop.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.roundToInt

private val DESENHO_DA_CABECA = listOf(
    "..oo........oo..",
    ".obbo......obbo.",
    ".obpbo....obpbo.",
    ".obbbboooobbbbo.",
    "obbbbbbbbbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obbwebbbbbbwebbo",
    "obbeebbbbbbeebbo",
    "oabbbbbnnbbbbbao",
    "oaaabbbbbbbbaaao",
    ".oaaaaaaaaaaaao.",
    "..ooaaaaaaaaoo..",
    "....oooooooo....",
)

internal const val LARGURA_DA_CABECA = 16
internal val ALTURA_DA_CABECA = DESENHO_DA_CABECA.size

private const val OLHO = 0xFF221C2B
private const val BRILHO = 0xFFF4F1EA
private const val ORELHA = 0xFFE9A3B4
private const val FOCINHO = 0xFFD9788A

internal fun coresDaCabeca(pelagem: Pelagem): Map<Char, Color> {
    fun daRampa(i: Int) = Color(0xFF000000.toInt() or pelagem.rampa[i])
    return mapOf(
        'a' to daRampa(0),
        'b' to daRampa(1),
        'o' to daRampa(3),
        'e' to Color(OLHO),
        'w' to Color(BRILHO),
        'p' to Color(ORELHA),
        'n' to Color(FOCINHO),
    )
}

internal fun DrawScope.desenharCabecaDoGato(cores: Map<Char, Color>, pixel: Float, centro: Offset) {
    val esquerda = (centro.x - LARGURA_DA_CABECA * pixel / 2f).roundToInt().toFloat()
    val topo = (centro.y - ALTURA_DA_CABECA * pixel / 2f).roundToInt().toFloat()
    val lado = Size(pixel, pixel)
    DESENHO_DA_CABECA.forEachIndexed { y, linha ->
        linha.forEachIndexed { x, marca ->
            cores[marca]?.let { cor -> drawRect(cor, Offset(esquerda + x * pixel, topo + y * pixel), lado) }
        }
    }
}
