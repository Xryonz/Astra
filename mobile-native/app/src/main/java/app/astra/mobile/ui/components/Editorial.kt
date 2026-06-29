package app.astra.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.animation.core.tween
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.DmMono
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.EaseSpring
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.delay

@Composable
fun Reveal(
    delayMillis: Int = 0,
    distance: Float = 14f,
    durationMillis: Int = 500,
    content: @Composable () -> Unit,
) {
    if (LocalAppPrefs.current.reduceMotion) {
        androidx.compose.foundation.layout.Box { content() }
        return
    }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        anim.animateTo(1f, tween(durationMillis = durationMillis, easing = EaseSpring))
    }
    androidx.compose.foundation.layout.Box(
        Modifier.graphicsLayer {
            alpha = anim.value
            translationY = (1f - anim.value) * distance.dp.toPx()
        },
    ) { content() }
}

@Composable
fun MarginaliaLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = astraColors.text3,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = DmMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.22.em,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun RomanNumeral(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    Text(
        text = text,
        modifier = modifier,
        color = astraColors.accent,
        fontFamily = DmSerif,
        fontStyle = FontStyle.Italic,
        fontSize = fontSize,
    )
}

@Composable
fun HairlineRule(
    modifier: Modifier = Modifier,
    bright: Boolean = false,
    accent: Boolean = false,
) {
    val base = modifier.fillMaxWidth().height(1.dp)
    if (accent) {
        androidx.compose.foundation.layout.Box(
            base.background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, astraColors.accent, Color.Transparent),
                ),
            ),
        )
    } else {
        androidx.compose.foundation.layout.Box(
            base.background(if (bright) astraColors.borderBright else astraColors.border),
        )
    }
}
