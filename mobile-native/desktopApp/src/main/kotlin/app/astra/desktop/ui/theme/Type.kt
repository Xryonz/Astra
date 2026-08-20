package app.astra.desktop.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextOverflow

// Tipografia: DM Sans variavel pro corpo/UI, Cormorant pros titulos editoriais,
// DM Mono pra código e timestamps, Great Vibes pra assinaturas, Babylonica pro
// letreiro. Todas OFL — as licenças viajam creditadas no README.

@OptIn(ExperimentalTextApi::class)
private fun dmSans(weight: FontWeight) = Font(
    resource = "font/dm_sans.ttf",
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val DmSans = FontFamily(
    dmSans(FontWeight.Light),
    dmSans(FontWeight.Normal),
    dmSans(FontWeight.Medium),
    dmSans(FontWeight.SemiBold),
)

// OS TÍTULOS SÃO CORMORANT, e o nome da variável continua `DmSerif` de propósito —
// ela é usada em umas quarenta telas, e renomear tudo seria um diff enorme sobre uma
// troca que é de arquivo, não de papel. O papel é o mesmo: a serifa editorial dos
// títulos. (Se um dia isto incomodar, é um renomear mecânico e seguro; hoje não paga.)
//
// POR QUE TROCAR. O DM Serif Display é uma boa serifa de display e é genérica: ela
// serve a qualquer produto editorial. A Cormorant tem contraste grosso/fino extremo,
// terminais em gota e ascendentes muito altos — em corpo grande o fio fino é
// literalmente a estética da gravura em cobre dos atlas estelares, que é o vocabulário
// do produto (constelação, órbita) aparecendo na forma da letra e não só na palavra.
//
// O PESO PADRÃO É 600, E ISSO NÃO É GOSTO. A instância padrão da fonte variável é
// LIGHT (300) — o próprio arquivo se chama "Cormorant Light". A 300, num fundo
// #06060E, o traço fino da Cormorant desaparece: contraste alto de desenho encontra
// contraste alto de fundo e a haste some. Foi medido em título de 15sp, que é o
// tamanho real de "notificações" no painel do sino.
//
// Mapear `Normal` para o eixo em 600 resolve os dois lados de uma vez: mantém a cor
// tipográfica parecida com a do DM Serif Display (que só tem um peso, e ele é cheio),
// então nenhuma das telas existentes precisou ser tocada; e deixa os pesos reais
// disponíveis para quem quiser um título mais leve num tamanho grande, onde o fio fino
// vira qualidade em vez de defeito.
@OptIn(ExperimentalTextApi::class)
private fun cormorant(peso: FontWeight, noEixo: Int, estilo: FontStyle = FontStyle.Normal) = Font(
    resource = if (estilo == FontStyle.Italic) "font/cormorant_italic.ttf" else "font/cormorant.ttf",
    weight = peso,
    style = estilo,
    variationSettings = FontVariation.Settings(FontVariation.weight(noEixo)),
)

val DmSerif = FontFamily(
    // O rótulo à esquerda é o que o call site pede; o número à direita é onde o eixo
    // realmente vai. Os dois só coincidem no Bold — ver o parágrafo do peso acima.
    cormorant(FontWeight.Light, 300),
    cormorant(FontWeight.Normal, 600),
    cormorant(FontWeight.Medium, 600),
    cormorant(FontWeight.SemiBold, 600),
    cormorant(FontWeight.Bold, 700),
    cormorant(FontWeight.Normal, 600, FontStyle.Italic),
)

val DmMono = FontFamily(
    Font(resource = "font/dm_mono.ttf"),
    Font(resource = "font/dm_mono_medium.ttf", weight = FontWeight.Medium),
)

val GreatVibes = FontFamily(Font(resource = "font/great_vibes.ttf"))

// O LETREIRO "ASTRA" — e só ele.
//
// Cinzel é desenhada sobre as capitais de INSCRIÇÃO romanas, as talhadas em pedra:
// só caixa-alta, serifa fina e afiada, proporção clássica. Com tracking largo ela lê
// como letreiro na fachada de um observatório, que é exatamente o registro do
// produto — frio, cerimonial, adulto.
//
// SAIU A BABYLONICA, que é escrita à mão de laçada larga. Ela assinava bonito, mas
// assinava — e assinatura à mão diz "feito por uma pessoa", enquanto o Astra quer
// dizer "instrumento". Eram duas vozes brigando: o resto do app é editorial e frio,
// e o letreiro era caloroso e manuscrito.
//
// OS CORPOS FORAM MEDIDOS, não estimados, porque as duas ocupam o corpo de formas
// muito diferentes: a Babylonica gasta metade da altura em laçada e a Cinzel é toda
// letra. Pela LARGURA da palavra, que é o que o olho compara num letreiro:
//
//	Babylonica 72sp -> 138px de largura     (era o da entrada)
//	Cinzel     44sp -> 140px                 (o equivalente)
//	Babylonica 32sp ->  61px                (era o da atualização)
//	Cinzel     20sp ->  64px
//
// Os corpos que entraram são um pouco maiores que esses porque o tracking largo, que
// é metade do efeito, precisa de onde caber.
val Cinzel = FontFamily(Font(resource = "font/cinzel.ttf"))

// DM Sans e o texto padrao do app. BasicText não le LocalTextStyle (isso e do
// material), entao este Text aplica a familia por baixo — os call sites so
// declaram cor/tamanho e herdam a fonte certa.
private val BaseStyle = TextStyle(fontFamily = DmSans)

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(text, modifier, BaseStyle.merge(style), onTextLayout, overflow, softWrap, maxLines)
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    // Imagem NO MEIO do texto (emoji da constelacao). Vazio por padrao: quem não
    // usa não paga nada, e nenhum call site existente muda.
    inlineContent: Map<String, InlineTextContent> = mapOf(),
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = BaseStyle.merge(style),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        inlineContent = inlineContent,
    )
}
