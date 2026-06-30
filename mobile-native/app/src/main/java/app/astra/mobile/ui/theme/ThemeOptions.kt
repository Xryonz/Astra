package app.astra.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Espelha apps/web/src/lib/theme.ts — accent/background/presets. */

data class AccentOption(val id: String, val label: String, val value: Color)
data class BgOption(val id: String, val label: String, val voidC: Color, val raisedC: Color)
data class ThemePreset(val id: String, val label: String, val hint: String, val accentId: String, val bgId: String)

val AccentOptions = listOf(
    AccentOption("white", "Branco", Color(0xFFD4D8E0)),
    AccentOption("gold", "Ambar", Color(0xFFC9A96E)),
    AccentOption("violet", "Violeta", Color(0xFF9B7AC4)),
    AccentOption("teal", "Ciano", Color(0xFF6AAECA)),
    AccentOption("rose", "Rosa", Color(0xFFCA7A9B)),
    AccentOption("emerald", "Esmeralda", Color(0xFF6EC99B)),
    AccentOption("orange", "Laranja", Color(0xFFCA9A6E)),
    AccentOption("crimson", "Carmim", Color(0xFFC46A6A)),
    AccentOption("indigo", "Indigo", Color(0xFF7A78C4)),
    AccentOption("sage", "Salva", Color(0xFF9EB98A)),
    AccentOption("copper", "Cobre", Color(0xFFC98660)),
    AccentOption("slate", "Ardosia", Color(0xFF7A8DA0)),
    AccentOption("lilac", "Lilas", Color(0xFFB48CC9)),
    AccentOption("red", "Vermelho", Color(0xFFEF4444)),
    AccentOption("yellow", "Amarelo", Color(0xFFFACC15)),
    AccentOption("blue", "Azul", Color(0xFF3B82F6)),
    AccentOption("green", "Verde", Color(0xFF22C55E)),
    AccentOption("black", "Preto", Color(0xFF18181B)),
)

val BgOptions = listOf(
    BgOption("void", "Obsidiana", Color(0xFF06060E), Color(0xFF0F0F24)),
    BgOption("dark", "Carvao", Color(0xFF0D0D0D), Color(0xFF161616)),
    BgOption("navy", "Marinho", Color(0xFF05080F), Color(0xFF0B1020)),
    BgOption("forest", "Floresta", Color(0xFF060E09), Color(0xFF0C1A10)),
    BgOption("wine", "Vinho", Color(0xFF0E0609), Color(0xFF1A0C10)),
    BgOption("pure-black", "Preto AMOLED", Color(0xFF000000), Color(0xFF0A0A0A)),
    BgOption("pure-red", "Vermelho", Color(0xFF1A0808), Color(0xFF2A1010)),
    BgOption("pure-yellow", "Amarelo", Color(0xFF1A1605), Color(0xFF2A2410)),
    BgOption("pure-blue", "Azul", Color(0xFF08081A), Color(0xFF10102A)),
    BgOption("pure-green", "Verde", Color(0xFF081A08), Color(0xFF102A10)),
)

val ThemePresets = listOf(
    ThemePreset("obsidian", "Obsidiana", "Prata fria + void", "white", "void"),
    ThemePreset("solar", "Solar", "Ambar editorial", "gold", "void"),
    ThemePreset("nebula", "Nebulosa", "Violeta cosmica", "violet", "navy"),
    ThemePreset("aurora", "Aurora", "Ciano + floresta", "teal", "forest"),
    ThemePreset("eclipse", "Eclipse", "Carmim sobre vinho", "crimson", "wine"),
    ThemePreset("meridian", "Meridiano", "Esmeralda + carvao", "emerald", "dark"),
    ThemePreset("amoled", "AMOLED", "Branco em preto puro", "white", "pure-black"),
)

fun accentOption(id: String?): AccentOption = AccentOptions.firstOrNull { it.id == id } ?: AccentOptions[0]
fun bgOption(id: String?): BgOption = BgOptions.firstOrNull { it.id == id } ?: BgOptions[0]

/** Deriva accent + void/base/raised do par escolhido; demais tokens ficam fixos. */
fun buildAstraColors(accentId: String?, bgId: String?): AstraColors {
    val a = accentOption(accentId).value
    val bg = bgOption(bgId)
    return AstraColorTokens.copy(
        void = bg.voidC,
        base = lerp(bg.voidC, bg.raisedC, 0.4f),
        raised = bg.raisedC,
        accent = a,
        accentH = lerp(a, Color.White, 0.18f),
        accentDim = a.copy(alpha = 0.10f),
        accentGlow = a.copy(alpha = 0.25f),
    )
}
