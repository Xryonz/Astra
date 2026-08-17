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

// Tipografia migrada do mobile (mesmos .ttf do :app): DM Sans variavel pro
// corpo/UI, DM Serif Display pros titulos editoriais, DM Mono pra código e
// timestamps, Great Vibes pra assinaturas.

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

val DmSerif = FontFamily(
    Font(resource = "font/dm_serif_display.ttf"),
    Font(resource = "font/dm_serif_display_italic.ttf", style = FontStyle.Italic),
)

val DmMono = FontFamily(
    Font(resource = "font/dm_mono.ttf"),
    Font(resource = "font/dm_mono_medium.ttf", weight = FontWeight.Medium),
)

val GreatVibes = FontFamily(Font(resource = "font/great_vibes.ttf"))

// O LETREIRO "ASTRA" — e só ele.
//
// Babylonica é escrita à mão de traço fino e laçada larga. Medida nos tamanhos que
// o app usa: soberba a 56px, boa a 32px, fraca a 20px e um borrão ilegível a 13px,
// que é onde o nome aparece na barra de título. Por isso ela vale nas duas telas em
// que "Astra" é LOGOTIPO (entrada e atualização) e não na barra, onde a mesma
// palavra é só um rótulo — trocar lá compraria uma mancha em vez de assinatura.
val Babylonica = FontFamily(Font(resource = "font/babylonica.ttf"))

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
