package app.astra.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.astra.mobile.ui.theme.astraColors

enum class AstraButtonVariant { Primary, Ghost }

@Composable
fun AstraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: AstraButtonVariant = AstraButtonVariant.Primary,
) {
    val shape = RoundedCornerShape(8.dp)
    val active = enabled && !loading
    val primary = variant == AstraButtonVariant.Primary
    val bg = when {
        !primary -> Color.Transparent
        active -> astraColors.accent
        else -> astraColors.accent.copy(alpha = 0.4f)
    }
    val fg = if (primary) astraColors.textInv else astraColors.text1
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(bg)
            .then(if (primary) Modifier else Modifier.border(1.dp, astraColors.borderMid, shape))
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
        } else {
            Text(
                text = text,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.12.em,
            )
        }
    }
}
