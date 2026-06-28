package app.astra.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import zed.rainxch.rikkaui.foundation.RikkaTheme

// Astra e dark-only por design. Mapeia os tokens cosmicos no ColorScheme do
// Material3 (pros componentes prontos) + expoe os extras via LocalAstraColors.
private val AstraDarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = TextInv,
    secondary = Raised,
    onSecondary = Text1,
    background = Void,
    onBackground = Text1,
    surface = Raised,
    onSurface = Text1,
    surfaceVariant = Overlay,
    onSurfaceVariant = Text2,
    outline = BorderMid,
    outlineVariant = BorderDim,
    error = Danger,
    onError = Color.White,
    scrim = Color(0xCC000000),
)

@Composable
fun AstraTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAstraColors provides AstraColorTokens) {
        MaterialTheme(
            colorScheme = AstraDarkColors,
            typography = AstraTypography,
        ) {
            // RikkaUI por cima: provê os tokens obsidian pros componentes RikkaUI
            // (Input, etc.). Material3 e RikkaUI coexistem durante a migração.
            RikkaTheme(colors = ObsidianRikkaColors, content = content)
        }
    }
}

// Acesso aos tokens editoriais: astraColors.void, .accentGlow, .text3, etc.
val astraColors: AstraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAstraColors.current
