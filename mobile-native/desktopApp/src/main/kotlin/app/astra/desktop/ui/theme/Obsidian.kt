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
    // Elas nao ganharam cor propria: saem do MESMO `raised` do tema. Assim o tom
    // acompanha o fundo de graca, e nao entra cor nova no sistema pra resolver
    // hierarquia (que e o que as normas do produto proibem).
    //
    // NAO E `lift`, E `clarear`, E A DIFERENCA E O BUG QUE ISTO CONSERTA. A primeira
    // versao somava a mesma quantidade nos tres canais. Somar preserva a diferenca
    // ABSOLUTA entre eles e destroi a RELATIVA: o raised da Aurora (#0C1A10) mais
    // 0,145 vira #313F35 — verde na conta, cinza no olho, porque 14/255 de vantagem
    // do verde sobre um nivel alto nao se enxerga. Multiplicar ANTES de somar
    // preserva a proporcao, e a cor sobrevive ao clareamento.
    //
    // O passo foi calibrado pra Obsidiana continuar em ~1,6:1 contra o `raised`,
    // igual ao que era. Borda de 1dp entre duas superficies e separador, nao
    // componente — perseguir os 3:1 de UI aqui desenharia um risco duro em volta de
    // cada cartao, que e exatamente o que o app evita.
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
    // VIRARAM `var` POR CAUSA DO ALTO CONTRASTE, e o parágrafo acima continua
    // valendo inteiro: ele descreve o PADRÃO, que não mudou. A halação é real e é
    // por isso que ninguém é empurrado pra cima dela.
    //
    // Mas "o padrão é calibrado pra sessão longa à noite" e "existe gente que não
    // enxerga esse padrão" são duas verdades ao mesmo tempo, e a segunda não tem
    // escapatória sem isto. Quem liga o alto contraste está dizendo que troca o
    // conforto pela legibilidade — e essa troca é dela, não minha.
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
        ultimoRaised = bg.raisedC
        // As bordas saem daqui E do contraste, então quem manda nelas é uma função
        // só. Sem isto, trocar de tema com alto contraste ligado devolveria as
        // bordas fracas em silêncio — e ninguém liga o alto contraste de novo pra
        // testar se ele sobreviveu à troca de cor.
        aplicarContraste(altoContraste)
    }

    // ALTO CONTRASTE. Sobe texto e borda; NÃO mexe no fundo, e isso é regra: a
    // rampa de elevação inteira (void → active) é calibrada a partir do void, e
    // clarear o fundo pra ganhar contraste destruiria a hierarquia que ela existe
    // pra criar — o remédio apagaria a estrutura da tela.
    //
    // text3 é o que mais sobe. Ele é o terciário, o primeiro a sumir pra quem tem
    // baixa visão, e no padrão ele vive perto do piso de propósito.
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

// Clareia MANTENDO a cor. `lift` soma o mesmo valor nos tres canais, o que preserva
// a diferenca absoluta entre eles e afunda a relativa: sobre um nivel alto, os
// 14/255 de vantagem do verde da Aurora deixam de ser enxergados e a borda le como
// cinza. Multiplicar antes de somar mantem a proporcao entre os canais — o ganho
// e o mesmo, a cor sobrevive.
//
// Serve pras BORDAS. A rampa de superficie continua no `lift`, e de proposito: ali
// o que se quer e justamente subir um degrau sem mexer no tom do fundo.
private fun clarear(c: Color, ganho: Float, piso: Float): Color = Color(
    red = (c.red * ganho + piso).coerceIn(0f, 1f),
    green = (c.green * ganho + piso).coerceIn(0f, 1f),
    blue = (c.blue * ganho + piso).coerceIn(0f, 1f),
    alpha = c.alpha,
)
