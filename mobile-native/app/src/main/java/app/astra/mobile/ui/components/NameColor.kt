package app.astra.mobile.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Cor de nome por servidor: hex solido (#RRGGBB) ou gradiente (gradient:ANGLE:#hex:#hex). */
sealed interface NameColor {
    data class Solid(val color: Color) : NameColor
    data class Gradient(val brush: Brush) : NameColor
}

fun parseNameColor(raw: String?): NameColor? {
    if (raw.isNullOrBlank()) return null
    if (raw.startsWith("gradient:")) {
        val parts = raw.split(":")
        if (parts.size != 4) return null
        val c1 = parseHex6(parts[2]) ?: return null
        val c2 = parseHex6(parts[3]) ?: return null
        return NameColor.Gradient(Brush.linearGradient(listOf(c1, c2)))
    }
    return parseHex6(raw)?.let { NameColor.Solid(it) }
}

private fun parseHex6(raw: String): Color? {
    val h = raw.trim().removePrefix("#")
    if (h.length != 6) return null
    return runCatching { Color("FF$h".toLong(16)) }.getOrNull()
}
