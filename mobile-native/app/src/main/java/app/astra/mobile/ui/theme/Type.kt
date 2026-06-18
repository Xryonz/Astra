package app.astra.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.astra.mobile.R

// ── Fontes editoriais do Astra (.ttf em res/font) ──────────────
// DM Sans e variable: 1 arquivo, peso via FontVariation (API 26+; em 24/25
// cai pro peso default, degradacao aceitavel).
@OptIn(ExperimentalTextApi::class)
private fun dmSansWeight(weight: Int, fw: FontWeight) =
    Font(R.font.dm_sans, fw, variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val DmSans = FontFamily(
    dmSansWeight(300, FontWeight.Light),
    dmSansWeight(400, FontWeight.Normal),
    dmSansWeight(500, FontWeight.Medium),
    dmSansWeight(600, FontWeight.SemiBold),
)

// Serif display — titulos editoriais (regular + italic)
val DmSerif = FontFamily(
    Font(R.font.dm_serif_display, FontWeight.Normal),
    Font(R.font.dm_serif_display_italic, FontWeight.Normal, FontStyle.Italic),
)

// Mono — marginalia, labels small-caps, timestamps
val DmMono = FontFamily(
    Font(R.font.dm_mono, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

// Script — flourish "Astra" (wordmark)
val GreatVibes = FontFamily(Font(R.font.great_vibes, FontWeight.Normal))

// Material3 Typography: serif nos titulos/headlines, sans no corpo/labels.
// Telas que usam MaterialTheme.typography.* ja herdam a tipografia editorial.
val AstraTypography = Typography(
    displayLarge = TextStyle(fontFamily = DmSerif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = DmSerif, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 38.sp),
    displaySmall = TextStyle(fontFamily = DmSerif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = DmSerif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = DmSerif, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = DmSerif, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = DmSerif, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
)
