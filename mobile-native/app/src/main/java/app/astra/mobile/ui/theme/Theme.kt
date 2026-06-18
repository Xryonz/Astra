package app.astra.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Astra e dark-only por design (light mode foi dropado).
private val AstraDarkColors = darkColorScheme(
    primary = AstraAmber,
    onPrimary = AstraVoid,
    background = AstraVoid,
    onBackground = AstraText1,
    surface = AstraRaised,
    onSurface = AstraText1,
    surfaceVariant = AstraRaised,
    outline = AstraBorder,
    error = AstraDanger,
)

@Composable
fun AstraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AstraDarkColors,
        typography = AstraTypography,
        content = content,
    )
}
