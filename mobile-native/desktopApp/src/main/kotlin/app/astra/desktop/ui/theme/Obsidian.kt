package app.astra.desktop.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

object Obsidian {
    var void by mutableStateOf(Color(0xFF06060E))
        private set
    var base by mutableStateOf(Color(0xFF09091A))
        private set
    var raised by mutableStateOf(Color(0xFF0F0F24))
        private set
    var overlay by mutableStateOf(Color(0xFF15152E))
        private set
    var hover by mutableStateOf(Color(0xFF1C1C38))
        private set
    var active by mutableStateOf(Color(0xFF22223F))
        private set
    var accent by mutableStateOf(Color(0xFFD4D8E0))
        private set
    var accentDim by mutableStateOf(Color(0x33D4D8E0))
        private set

    var borderDim by mutableStateOf(Color(0xFF363741))
        private set
    var borderMid by mutableStateOf(Color(0xFF494A54))
        private set

    var text1 by mutableStateOf(TEXT1_PADRAO)
        private set
    var text2 by mutableStateOf(TEXT2_PADRAO)
        private set
    var text3 by mutableStateOf(TEXT3_PADRAO)
        private set
    val danger = Color(0xFFE07A7A)
    val success = Color(0xFF6FCFA0)
    val warning = Color(0xFFE8B86D)
    val textInv = Color(0xFF09091A)

    fun apply(accentId: String?, bgId: String?) {
        val a = accentOption(accentId).value
        val bg = bgOption(bgId)
        void = bg.voidC
        base = lerp(bg.voidC, bg.raisedC, 0.4f)
        raised = bg.raisedC
        overlay = lift(bg.raisedC, 0.028f)
        hover = lift(bg.raisedC, 0.055f)
        active = lift(bg.raisedC, 0.085f)
        accent = a
        accentDim = a.copy(alpha = 0.2f)
        ultimoRaised = bg.raisedC
        aplicarContraste(altoContraste)
    }

    fun aplicarContraste(alto: Boolean) {
        altoContraste = alto
        text1 = if (alto) Color(0xFFF7F7FA) else TEXT1_PADRAO
        text2 = if (alto) Color(0xFFE0E0E6) else TEXT2_PADRAO
        text3 = if (alto) Color(0xFFB8B8C0) else TEXT3_PADRAO
        val r = ultimoRaised
        borderDim = if (alto) clarear(r, 2.30f, 0.235f) else clarear(r, 1.55f, 0.115f)
        borderMid = if (alto) clarear(r, 2.70f, 0.310f) else clarear(r, 1.85f, 0.17f)
    }

    private var altoContraste = false
    private var ultimoRaised = Color(0xFF0F0F24)
}

private val TEXT1_PADRAO = Color(0xFFE4E4EB)
private val TEXT2_PADRAO = Color(0xFFC0C0C6)
private val TEXT3_PADRAO = Color(0xFF8C8C94)

private fun lift(c: Color, amount: Float): Color = Color(
    red = (c.red + amount).coerceAtMost(1f),
    green = (c.green + amount).coerceAtMost(1f),
    blue = (c.blue + amount).coerceAtMost(1f),
    alpha = c.alpha,
)

private fun clarear(c: Color, ganho: Float, piso: Float): Color = Color(
    red = (c.red * ganho + piso).coerceIn(0f, 1f),
    green = (c.green * ganho + piso).coerceIn(0f, 1f),
    blue = (c.blue * ganho + piso).coerceIn(0f, 1f),
    alpha = c.alpha,
)
