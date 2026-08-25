package app.astra.desktop.ui.theme

import androidx.compose.ui.graphics.Color

data class AccentOption(val id: String, val label: String, val value: Color)
data class BgOption(val id: String, val label: String, val voidC: Color, val raisedC: Color)
enum class FamiliaDeTema(val titulo: String) {
    NEUTRO("neutros"),
    QUENTE("quentes"),
    FRIO("frios"),
}

data class ThemePreset(
    val id: String,
    val label: String,
    val hint: String,
    val accentId: String,
    val bgId: String,
    val familia: FamiliaDeTema,
)

val AccentOptions = listOf(
    AccentOption("white", "Branco", Color(0xFFD4D8E0)),
    AccentOption("gold", "Âmbar", Color(0xFFC9A96E)),
    AccentOption("violet", "Violeta", Color(0xFF9B7AC4)),
    AccentOption("teal", "Ciano", Color(0xFF6AAECA)),
    AccentOption("rose", "Rosa", Color(0xFFCA7A9B)),
    AccentOption("emerald", "Esmeralda", Color(0xFF6EC99B)),
    AccentOption("orange", "Laranja", Color(0xFFCA9A6E)),
    AccentOption("crimson", "Carmim", Color(0xFFC46A6A)),
    AccentOption("indigo", "Índigo", Color(0xFF7A78C4)),
    AccentOption("sage", "Salva", Color(0xFF9EB98A)),
    AccentOption("copper", "Cobre", Color(0xFFC98660)),
    AccentOption("slate", "Ardósia", Color(0xFF7A8DA0)),
    AccentOption("lilac", "Lilás", Color(0xFFB48CC9)),
    AccentOption("cobalt", "Cobalto", Color(0xFF7B9BC7)),
    AccentOption("red", "Vermelho", Color(0xFFEF4444)),
    AccentOption("yellow", "Amarelo", Color(0xFFFACC15)),
    AccentOption("blue", "Azul", Color(0xFF3B82F6)),
    AccentOption("green", "Verde", Color(0xFF22C55E)),
    AccentOption("black", "Preto", Color(0xFF18181B)),
)

val BgOptions = listOf(
    BgOption("void", "Obsidiana", Color(0xFF06060E), Color(0xFF0F0F24)),
    BgOption("dark", "Carvão", Color(0xFF0D0D0D), Color(0xFF161616)),
    BgOption("navy", "Marinho", Color(0xFF05080F), Color(0xFF0B1020)),
    BgOption("forest", "Floresta", Color(0xFF060E09), Color(0xFF0C1A10)),
    BgOption("wine", "Vinho", Color(0xFF0E0609), Color(0xFF1A0C10)),
    BgOption("pure-black", "Preto AMOLED", Color(0xFF000000), Color(0xFF0A0A0A)),
    BgOption("pure-red", "Vermelho", Color(0xFF1A0808), Color(0xFF2A1010)),
    BgOption("pure-yellow", "Amarelo", Color(0xFF1A1605), Color(0xFF2A2410)),
    BgOption("pure-blue", "Azul", Color(0xFF08081A), Color(0xFF10102A)),
    BgOption("pure-green", "Verde", Color(0xFF081A08), Color(0xFF102A10)),
    BgOption("arctic", "Ártico", Color(0xFF070A10), Color(0xFF0F131E)),
    BgOption("clay", "Terra", Color(0xFF0C0805), Color(0xFF1C140D)),
    BgOption("indigo-night", "Índigo", Color(0xFF06070F), Color(0xFF0D1026)),
    BgOption("mauve", "Malva", Color(0xFF0A070D), Color(0xFF16121D)),
    BgOption("petrol", "Petróleo", Color(0xFF05090A), Color(0xFF0B171A)),
    BgOption("olive", "Oliva", Color(0xFF080D07), Color(0xFF131F10)),
    BgOption("haze", "Bruma", Color(0xFF08070B), Color(0xFF151220)),
    BgOption("lead", "Chumbo", Color(0xFF06070A), Color(0xFF0E1018)),
)

val ThemePresets = listOf(
    ThemePreset("obsidian", "Obsidiana", "Prata fria + void", "white", "void", FamiliaDeTema.NEUTRO),
    ThemePreset("solar", "Solar", "Âmbar editorial", "gold", "void", FamiliaDeTema.QUENTE),
    ThemePreset("nebula", "Nebulosa", "Violeta cósmica", "violet", "navy", FamiliaDeTema.FRIO),
    ThemePreset("aurora", "Aurora", "Ciano + floresta", "teal", "forest", FamiliaDeTema.FRIO),
    ThemePreset("eclipse", "Eclipse", "Carmim sobre vinho", "crimson", "wine", FamiliaDeTema.QUENTE),
    ThemePreset("meridian", "Meridiano", "Esmeralda + carvão", "emerald", "dark", FamiliaDeTema.FRIO),
    ThemePreset("amoled", "AMOLED", "Branco em preto puro", "white", "pure-black", FamiliaDeTema.NEUTRO),
    ThemePreset("nortada", "Nortada", "Ardósia no gelo", "slate", "arctic", FamiliaDeTema.NEUTRO),
    ThemePreset("terracota", "Terracota", "Cobre sobre terra", "copper", "clay", FamiliaDeTema.QUENTE),
    ThemePreset("equinocio", "Equinócio", "Índigo de cidade", "indigo", "indigo-night", FamiliaDeTema.FRIO),
    ThemePreset("penumbra", "Penumbra", "Lilás abafado", "lilac", "mauve", FamiliaDeTema.FRIO),
    ThemePreset("maresia", "Maresia", "Ciano + petróleo", "teal", "petrol", FamiliaDeTema.FRIO),
    ThemePreset("musgo", "Musgo", "Salva + oliva", "sage", "olive", FamiliaDeTema.FRIO),
    ThemePreset("poeira", "Poeira Estelar", "Violeta em bruma", "violet", "haze", FamiliaDeTema.FRIO),
    ThemePreset("vespera", "Véspera", "Cobalto discreto", "cobalt", "lead", FamiliaDeTema.NEUTRO),
)

fun accentOption(id: String?): AccentOption = AccentOptions.firstOrNull { it.id == id } ?: AccentOptions[0]
fun bgOption(id: String?): BgOption = BgOptions.firstOrNull { it.id == id } ?: BgOptions[0]
