package app.astra.desktop.ui

import androidx.compose.ui.text.font.FontFamily
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSans
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.GreatVibes

// displayFont = a fonte do TEU NOME (chat, sussurros e perfil). O web guarda um
// id ("serif", "mono", ...) e resolve numa stack CSS; aqui traduzimos pro que o
// desktop realmente tem. O app empacota 4 fontes (DmSans/DmSerif/DmMono/
// GreatVibes); as demais caem nas familias genericas do sistema.
// NOTA: "rounded", "condensed" e "modern" nao tem arquivo proprio e caem todas
// na sans do sistema -> ficam parecidas entre si no desktop. Renderizamos os 8
// mesmo assim porque o valor pode vir do web/mobile e nao pode "sumir".
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

// Fallback = serif (mesmo default do web).
fun profileFontFamily(id: String?): FontFamily =
    PROFILE_FONTS.find { it.id == id }?.family ?: DmSerif
