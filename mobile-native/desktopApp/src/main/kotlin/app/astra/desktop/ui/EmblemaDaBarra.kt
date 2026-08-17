package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.astra.desktop.ui.theme.Obsidian
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.Window
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

// O EMBLEMA COLADO NO ÍCONE DA BARRA DE TAREFAS — o círculo com o número.
//
// Desce pro `ITaskbarList3::SetOverlayIcon` do Win32, que é a mesma API que o
// Discord usa. O JDK expõe isso como `Taskbar.setWindowIconBadge`.
//
// O WINDOWS NÃO SABE DESENHAR O NÚMERO. Conferido nesta máquina:
//   ICON_BADGE_NUMBER       = false   ← "escreva 5 pra mim" não existe aqui
//   ICON_BADGE_IMAGE_WINDOW = true    ← "cole esta imagem" existe
// Em macOS seria só passar a string. No Windows a imagem é obrigação nossa — o
// que acaba sendo melhor, porque o emblema nasce com a cor do tema em vez de
// herdar um círculo vermelho de fábrica.
//
// A CONTAGEM É A DO SINO, e isso é decisão de produto, não preguiça: a tabela de
// notificações só ganha linha para o que é dirigido a você (menção, sussurro,
// reação, resposta, pedido de amizade). Mensagem de canal não entra. Se o emblema
// contasse toda mensagem de todo canal, ele viveria em três dígitos e viraria
// enfeite — número que nunca zera deixa de ser informação.

private const val LADO = 64          // desenha grande e deixa o Windows reduzir
private const val ANEL = 4f          // contorno escuro, em unidades do LADO

@Composable
fun EmblemaDaBarra(janela: Window?, quantidade: Int) {
    // Lidos na composição (e não dentro do efeito) pra que trocar o tema em
    // Aparência repinte o emblema: aqui eles são chave do LaunchedEffect.
    val fundo = Obsidian.accent
    val tinta = Obsidian.textInv
    val contorno = Obsidian.void

    LaunchedEffect(janela, quantidade, fundo, tinta, contorno) {
        if (janela == null) return@LaunchedEffect
        val imagem = if (quantidade <= 0) null else desenhar(
            texto = rotulo(quantidade),
            fundo = Color(fundo.red, fundo.green, fundo.blue),
            tinta = Color(tinta.red, tinta.green, tinta.blue),
            contorno = Color(contorno.red, contorno.green, contorno.blue),
        )
        // AWT quer a EDT. Um `setWindowIconBadge` fora dela funciona quase sempre
        // e falha exatamente quando a janela está sendo recriada — o tipo de bug
        // que só aparece na máquina de outra pessoa.
        SwingUtilities.invokeLater {
            runCatching {
                if (!Taskbar.isTaskbarSupported()) return@runCatching
                val barra = Taskbar.getTaskbar()
                if (!barra.isSupported(Taskbar.Feature.ICON_BADGE_IMAGE_WINDOW)) return@runCatching
                // Nulo LIMPA o emblema. É por isso que `quantidade <= 0` não sai
                // cedo lá em cima: zerar precisa chegar até aqui, senão o círculo
                // fica grudado com o último número depois de ler tudo.
                barra.setWindowIconBadge(janela, imagem)
            }
        }
    }
}

// 1–99 exatos; acima disso, só o "+". Duas casas já ficam apertadas quando o
// Windows reduz isto pra 16px, e três viram borrão — e "+" responde a única
// pergunta que sobra nessa faixa ("muita coisa?") sem fingir precisão ilegível.
private fun rotulo(n: Int): String = if (n > 99) "+" else n.toString()

private fun desenhar(texto: String, fundo: Color, tinta: Color, contorno: Color): BufferedImage {
    val img = BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    // O anel escuro é o que salva o emblema em barra de tarefas CLARA. O accent
    // padrão do Astra é quase branco; sem contorno ele sumiria no tema claro do
    // Windows, que é o padrão de fábrica do sistema.
    g.color = contorno
    g.fillOval(0, 0, LADO, LADO)
    g.color = fundo
    g.fillOval(ANEL.toInt(), ANEL.toInt(), LADO - 2 * ANEL.toInt(), LADO - 2 * ANEL.toInt())

    // Uma casa pode ser generosa; duas precisam caber. O "+" desenha como uma casa.
    val corpo = if (texto.length >= 2) 34f else 44f
    g.font = Font(Font.SANS_SERIF, Font.BOLD, corpo.toInt())
    g.color = tinta
    val fm = g.fontMetrics
    val x = (LADO - fm.stringWidth(texto)) / 2f
    // Centro ÓPTICO, não o da caixa: `ascent - descent` põe o miolo do dígito no
    // meio do círculo. Usar só a altura da fonte deixa o número visivelmente alto.
    val y = (LADO + (fm.ascent - fm.descent)) / 2f
    g.drawString(texto, x, y)
    g.dispose()
    return img
}
