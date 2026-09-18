package app.astra.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import zed.rainxch.rikkaui.foundation.RikkaColors
import zed.rainxch.rikkaui.foundation.RikkaTheme

@Composable
fun AstraTheme(
    accentId: String = "white",
    bgId: String = "void",
    content: @Composable () -> Unit,
) {
    val colors = remember(accentId, bgId) { buildAstraColors(accentId, bgId) }

    val materialScheme = remember(colors) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.textInv,
            secondary = colors.raised,
            onSecondary = colors.text1,
            background = colors.void,
            onBackground = colors.text1,
            surface = colors.raised,
            onSurface = colors.text1,
            surfaceVariant = colors.overlay,
            onSurfaceVariant = colors.text2,
            outline = colors.borderMid,
            outlineVariant = colors.border,
            error = colors.danger,
            onError = Color.White,
            scrim = Color(0xCC000000),
        )
    }

    val rikkaColors = remember(colors) {
        RikkaColors(
            background = colors.raised,
            onBackground = colors.text1,
            surface = colors.overlay,
            onSurface = colors.text1,
            primary = colors.accent,
            onPrimary = colors.textInv,
            secondary = colors.hover,
            onSecondary = colors.text1,
            muted = colors.base,
            onMuted = colors.text3,
            destructive = colors.danger,
            onDestructive = Color.White,
            warning = colors.warning,
            onWarning = colors.textInv,
            success = colors.success,
            onSuccess = colors.textInv,
            border = colors.borderMid,
            ring = colors.accent,
            inverseSurface = colors.text1,
            onInverseSurface = colors.void,
            primaryTinted = colors.accentDim,
            onPrimaryTinted = colors.accent,
            destructiveTinted = Color(0x26E07A7A),
            onDestructiveTinted = colors.danger,
        )
    }

    CompositionLocalProvider(LocalAstraColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AstraTypography,
        ) {
            RikkaTheme(colors = rikkaColors, content = content)
        }
    }
}

val astraColors: AstraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAstraColors.current
