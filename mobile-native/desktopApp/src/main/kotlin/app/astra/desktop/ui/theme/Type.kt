package app.astra.desktop.ui.theme

import androidx.compose.foundation.text.BasicText
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
// corpo/UI, DM Serif Display pros titulos editoriais, DM Mono pra codigo e
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

// DM Sans e o texto padrao do app. BasicText nao le LocalTextStyle (isso e do
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
) {
    BasicText(text, modifier, BaseStyle.merge(style), onTextLayout, overflow, softWrap, maxLines)
}
