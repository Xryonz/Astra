package app.astra.desktop.ui

import androidx.compose.ui.text.font.FontFamily
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSans
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.GreatVibes

data class ProfileFont(val id: String, val label: String, val family: FontFamily)

val PROFILE_FONTS = listOf(
    ProfileFont("serif", "Serif editorial", DmSerif),
    ProfileFont("sans", "Sans limpa", DmSans),
    ProfileFont("mono", "Mono tecnica", DmMono),
    ProfileFont("handwriting", "Manuscrita", GreatVibes),
    ProfileFont("gothic", "Gotica", FontFamily.Serif),
    ProfileFont("rounded", "Arredondada", FontFamily.SansSerif),
    ProfileFont("condensed", "Condensada", FontFamily.SansSerif),
    ProfileFont("modern", "Geometrica", FontFamily.SansSerif),
)

fun profileFontFamily(id: String?): FontFamily =
    PROFILE_FONTS.find { it.id == id }?.family ?: DmSerif
