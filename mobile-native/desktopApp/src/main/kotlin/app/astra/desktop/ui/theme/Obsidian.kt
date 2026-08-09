package app.astra.desktop.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Tokens obsidiana do desktop — agora REATIVOS. Os campos de cor que dependem do
// tema (accent + rampa de fundo) são mutableStateOf, entao os ~300 usos
// `Obsidian.xxx` dentro de @Composable recompoem sozinhos quando o tema muda. Os
// call sites não mudam. apply() deriva a paleta do par (accentId, bgId) escolhido
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

    // AS BORDAS SEGUEM O TEMA. Eram fixas (#363741 e #494A54) e por isso a mesma
    // linha azul-acinzentada aparecia por cima de qualquer fundo: no tema Eclipse,
    // de vinho, o cartao ficava contornado por uma cor fria que nao existia em
    // lugar nenhum da tela — parecia recortado de outro app.
    //
    // Elas nao ganharam cor propria: sao a rampa de elevacao levada dois degraus
    // adiante, a partir do MESMO `raised` do tema. Assim o tom acompanha o fundo
    // de graca, e nao entra cor nova no sistema pra resolver hierarquia (que e o
    // que as normas do produto proibem).
    //
    // O passo foi calibrado pra Obsidiana continuar praticamente identica: 1,6:1
    // contra o `raised`, igual ao que era. Borda de 1dp entre duas superficies e
    // separador, nao componente — perseguir os 3:1 de UI aqui desenharia um risco
    // duro em volta de cada cartao, que e exatamente o que o app evita.
    var borderDim by mutableStateOf(Color(0xFF363741))
        private set
    var borderMid by mutableStateOf(Color(0xFF494A54))
        private set

    // Fixos (independentes do tema).
    //
    // text1 desceu de #F5F5F7 pra #E4E4EB, e o motivo NAO e contraste — e o
    // contrario dele. Sobre o void (#06060E), o valor antigo dava ~19:1, quase o
    // dobro do que a norma pede pra texto pequeno. Contraste ALTO DEMAIS em fundo
    // escuro produz halacao: a borda clara da letra parece vibrar, e o efeito
    // aparece justamente em quem passa horas no app a noite — que e o uso real
    // daqui. O valor novo continua em ~15:1, folgado acima do minimo de 4,5:1.
    //
    // Se algum dia isto parecer apagado demais, o caminho e subir ESTE numero, e
    // nao mexer no fundo: a rampa de elevacao inteira e calibrada a partir do void.
    val text1 = Color(0xFFE4E4EB)
    val text2 = Color(0xFFC0C0C6)
    val text3 = Color(0xFF8C8C94)
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
