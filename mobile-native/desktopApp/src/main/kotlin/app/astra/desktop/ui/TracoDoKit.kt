package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composables.ui.components.HorizontalSeparator
import com.composables.ui.components.VerticalSeparator

@Composable
internal fun TracoDoKit(cor: Color, modifier: Modifier = Modifier) {
    HorizontalSeparator(modifier = modifier, color = cor)
}

@Composable
internal fun TracoEmPeDoKit(cor: Color, modifier: Modifier = Modifier) {
    VerticalSeparator(modifier = modifier, color = cor)
}
