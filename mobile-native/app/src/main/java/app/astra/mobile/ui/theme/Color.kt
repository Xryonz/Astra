package app.astra.mobile.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Void = Color(0xFF06060E)
val Base = Color(0xFF09091A)
val Raised = Color(0xFF0F0F24)
val Overlay = Color(0xFF15152E)
val HoverBg = Color(0xFF1C1C38)
val ActiveBg = Color(0xFF22223F)

val Accent = Color(0xFFD4D8E0)
val AccentH = Color(0xFFE8EBF0)
val AccentDim = Color(0x1AD4D8E0)
val AccentGlow = Color(0x33D4D8E0)

val Text1 = Color(0xFFF0EDFF)
val Text2 = Color(0xFFB4B2D6)
val Text3 = Color(0xFF8E8BB0)
val TextInv = Color(0xFF09091A)

val BorderDim = Color(0xFF363741)
val BorderMid = Color(0xFF494A54)
val BorderBright = Color(0xFF646670)

val Danger = Color(0xFFE07A7A)
val Success = Color(0xFF6FCFA0)
val Warning = Color(0xFFE8B86D)

@Immutable
data class AstraColors(
    val void: Color,
    val base: Color,
    val raised: Color,
    val overlay: Color,
    val hover: Color,
    val active: Color,
    val accent: Color,
    val accentH: Color,
    val accentDim: Color,
    val accentGlow: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val textInv: Color,
    val border: Color,
    val borderMid: Color,
    val borderBright: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
)

val AstraColorTokens = AstraColors(
    void = Void,
    base = Base,
    raised = Raised,
    overlay = Overlay,
    hover = HoverBg,
    active = ActiveBg,
    accent = Accent,
    accentH = AccentH,
    accentDim = AccentDim,
    accentGlow = AccentGlow,
    text1 = Text1,
    text2 = Text2,
    text3 = Text3,
    textInv = TextInv,
    border = BorderDim,
    borderMid = BorderMid,
    borderBright = BorderBright,
    danger = Danger,
    success = Success,
    warning = Warning,
)

val LocalAstraColors = staticCompositionLocalOf { AstraColorTokens }
