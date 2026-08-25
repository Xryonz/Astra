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

@OptIn(ExperimentalTextApi::class)
private fun cormorant(peso: FontWeight, noEixo: Int, estilo: FontStyle = FontStyle.Normal) = Font(
    resource = if (estilo == FontStyle.Italic) "font/cormorant_italic.ttf" else "font/cormorant.ttf",
    weight = peso,
    style = estilo,
    variationSettings = FontVariation.Settings(FontVariation.weight(noEixo)),
)

val DmSerif = FontFamily(
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

val Cinzel = FontFamily(Font(resource = "font/cinzel.ttf"))

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
