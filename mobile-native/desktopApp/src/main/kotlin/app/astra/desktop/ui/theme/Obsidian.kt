package app.astra.desktop.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Tokens obsidiana do desktop — agora REATIVOS. Os campos de cor que dependem do
// tema (accent + rampa de fundo) sao mutableStateOf, entao os ~300 usos
// `Obsidian.xxx` dentro de @Composable recompoem sozinhos quando o tema muda. Os
// call sites nao mudam. apply() deriva a paleta do par (accentId, bgId) escolhido
// em Settings > Aparencia (mesma logica do buildAstraColors do mobile). text/border
// /status ficam fixos (funcionam em qualquer fundo escuro).
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

    // Fixos (independentes do tema).
    val text1 = Color(0xFFF5F5F7)
    val text2 = Color(0xFFC0C0C6)
    val text3 = Color(0xFF8C8C94)
    val borderDim = Color(0xFF363741)
    val borderMid = Color(0xFF494A54)
    val danger = Color(0xFFE07A7A)
    val success = Color(0xFF6FCFA0)
    val warning = Color(0xFFE8B86D)
    val textInv = Color(0xFF09091A)

    // Deriva accent + a rampa de fundo (void..active) do par escolhido. Rampa
    // elevada = passo grayscale sobre o raised do tema (mantem o tom em qualquer
    // fundo). accentDim em 0.2 pra bater com o token anterior do desktop.
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
    }
}

private fun lift(c: Color, amount: Float): Color = Color(
    red = (c.red + amount).coerceAtMost(1f),
    green = (c.green + amount).coerceAtMost(1f),
    blue = (c.blue + amount).coerceAtMost(1f),
    alpha = c.alpha,
)
