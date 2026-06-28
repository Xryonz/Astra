package app.astra.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import zed.rainxch.rikkaui.foundation.RikkaColors

/**
 * Paleta RikkaUI mapeada na identidade obsidian/prata do Astra (espelha
 * [AstraColors]). O [zed.rainxch.rikkaui.foundation.RikkaTheme] que envolve o app
 * provê isto — todo componente RikkaUI (Input, etc.) herda essas cores.
 *
 * Tokens sem par (borderSubtle, scrim) e estados de interação usam os defaults.
 */
val ObsidianRikkaColors = RikkaColors(
    background = Raised,            // fill de inputs/fields
    onBackground = Text1,
    surface = Overlay,             // cards, dialogs, popovers, sheets
    onSurface = Text1,
    primary = Accent,              // prata-estelar — ações principais
    onPrimary = TextInv,
    secondary = HoverBg,
    onSecondary = Text1,
    muted = Base,
    onMuted = Text3,               // placeholder/legenda
    destructive = Danger,
    onDestructive = Color.White,
    warning = Warning,
    onWarning = TextInv,
    success = Success,
    onSuccess = TextInv,
    border = BorderMid,
    ring = Accent,                 // anel de foco (Glow do Input) = prata
    inverseSurface = Text1,
    onInverseSurface = Void,
    primaryTinted = AccentDim,
    onPrimaryTinted = Accent,
    destructiveTinted = Color(0x26E07A7A),
    onDestructiveTinted = Danger,
)
