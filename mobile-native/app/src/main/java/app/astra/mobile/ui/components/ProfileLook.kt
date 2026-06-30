package app.astra.mobile.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Personalizacao de perfil: gradientes (profileTheme), fonte de exibicao. Espelha web. */

/** Strings no formato aceito pelo backend: linear-gradient(135deg,#a,#b[,#c]). */
val ProfileGradients: List<Pair<String, String>> = listOf(
    "Galaxia" to "linear-gradient(135deg,#0f0c29,#302b63,#24243e)",
    "Obsidiana" to "linear-gradient(135deg,#000000,#1a4d2e)",
    "Tinta" to "linear-gradient(135deg,#000000,#0f3460)",
    "Crepusculo" to "linear-gradient(135deg,#3a1c71,#4a00e0)",
    "Veludo" to "linear-gradient(135deg,#41295a,#2f0743)",
    "Aurora" to "linear-gradient(135deg,#3a1c71,#d76d77,#ffaf7b)",
    "Oceano" to "linear-gradient(135deg,#2193b0,#6dd5ed)",
    "Lagoa" to "linear-gradient(135deg,#43cea2,#185a9d)",
    "Floresta" to "linear-gradient(135deg,#134e5e,#71b280)",
    "Brasa" to "linear-gradient(135deg,#ff5722,#ff9800,#ffc107)",
    "Magma" to "linear-gradient(135deg,#f12711,#f5af19)",
    "Vinho" to "linear-gradient(135deg,#6e0d25,#bd5734)",
)

private val HEX_RE = Regex("#[0-9a-fA-F]{6}")

/** Parseia hex solido ou linear-gradient(...) num Brush. */
fun parseGradientBrush(css: String?): Brush? {
    if (css.isNullOrBlank()) return null
    val colors = HEX_RE.findAll(css).mapNotNull { m ->
        runCatching { Color("FF${m.value.removePrefix("#")}".toLong(16)) }.getOrNull()
    }.toList()
    return when {
        colors.isEmpty() -> null
        colors.size == 1 -> Brush.linearGradient(listOf(colors[0], colors[0]))
        else -> Brush.linearGradient(colors)
    }
}

/** Fontes de exibicao oferecidas no app (as que o Android tem nativo). */
val DisplayFontOptions: List<Pair<String, String>> = listOf(
    "serif" to "Serif",
    "sans" to "Sans",
    "mono" to "Mono",
    "handwriting" to "Manuscrita",
)

/** Mapeia o id de fonte (8 do web) pra FontFamily nativa, com fallback. */
fun displayFontFamily(id: String?): FontFamily = when (id) {
    "sans", "rounded", "modern", "condensed" -> FontFamily.SansSerif
    "mono" -> FontFamily.Monospace
    "handwriting" -> FontFamily.Cursive
    else -> FontFamily.Serif
}
