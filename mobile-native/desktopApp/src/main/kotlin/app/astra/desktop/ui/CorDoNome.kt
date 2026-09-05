package app.astra.desktop.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

sealed interface CorDoNome {
    val solida: Color
    val pincel: Brush

    data class Lisa(override val solida: Color) : CorDoNome {
        override val pincel: Brush get() = SolidColor(solida)
    }

    data class Degrade(
        override val solida: Color,
        val fim: Color,
        val graus: Int,
    ) : CorDoNome {
        override val pincel: Brush get() = Brush.linearGradient(listOf(solida, fim))
    }
}

fun lerCorDoNome(cru: String?): CorDoNome? {
    val texto = cru?.trim().orEmpty()
    if (texto.isEmpty()) return null
    if (!texto.startsWith("gradient:")) return lerHex6(texto)?.let { CorDoNome.Lisa(it) }

    val partes = texto.split(":")
    if (partes.size != 4) return null
    val inicio = lerHex6(partes[2]) ?: return null
    val fim = lerHex6(partes[3]) ?: return null
    return CorDoNome.Degrade(inicio, fim, partes[1].toIntOrNull() ?: 0)
}

fun escreverCorDoNome(cor: CorDoNome?): String? = when (cor) {
    null -> null
    is CorDoNome.Lisa -> emHex(cor.solida)
    is CorDoNome.Degrade -> "gradient:${cor.graus}:${emHex(cor.solida)}:${emHex(cor.fim)}"
}

fun emHex(cor: Color): String {
    val r = (cor.red * 255).toInt().coerceIn(0, 255)
    val g = (cor.green * 255).toInt().coerceIn(0, 255)
    val b = (cor.blue * 255).toInt().coerceIn(0, 255)
    return "#%02x%02x%02x".format(r, g, b)
}

private fun lerHex6(cru: String): Color? {
    val h = cru.trim().removePrefix("#")
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}
