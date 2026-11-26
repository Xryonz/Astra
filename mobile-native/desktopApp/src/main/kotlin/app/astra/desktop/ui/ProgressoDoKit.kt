package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.astra.desktop.ui.theme.FormaDoKit
import com.composables.ui.components.IndeterminateProgressIndicator
import com.composables.ui.components.ProgressIndicator
import com.composables.ui.theme.colors
import com.composables.ui.theme.controlColor
import com.composables.ui.theme.primaryColor
import com.composeunstyled.Indicator
import com.composeunstyled.UnstyledProgress
import com.composeunstyled.theme.Theme

@Composable
internal fun BarraDeProgressoDoKit(progresso: Float, altura: Dp, modifier: Modifier = Modifier) {
    if (!LocalReduceMotion.current) {
        ProgressIndicator(progress = progresso, modifier = modifier, shape = FormaDoKit, height = altura)
        return
    }
    UnstyledProgress(
        progress = progresso.coerceIn(0f, 1f),
        modifier = modifier.height(altura).clip(FormaDoKit).background(Theme[colors][controlColor], FormaDoKit),
    ) {
        Indicator(Modifier.clip(FormaDoKit).background(Theme[colors][primaryColor], FormaDoKit))
    }
}

@Composable
internal fun EsperaDoKit(altura: Dp, modifier: Modifier = Modifier, trilho: Color = Theme[colors][controlColor]) {
    if (!LocalReduceMotion.current) {
        IndeterminateProgressIndicator(modifier = modifier, shape = FormaDoKit, trackColor = trilho, height = altura)
        return
    }
    UnstyledProgress(modifier = modifier.height(altura).clip(FormaDoKit).background(trilho, FormaDoKit)) {
        Box(Modifier.fillMaxSize().background(Theme[colors][primaryColor].copy(alpha = ESPERA_PARADA)))
    }
}

private const val ESPERA_PARADA = 0.3f
