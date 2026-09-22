package app.astra.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.astraColors
import kotlin.math.abs

private val VAO = 4.dp

@Composable
fun AstraTabs(
    tabs: List<String>,
    posicao: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    counts: List<Int?> = emptyList(),
) {
    val outer = RoundedCornerShape(12.dp)
    val inner = RoundedCornerShape(9.dp)
    BoxWithConstraints(
        modifier = modifier
            .clip(outer)
            .background(astraColors.raised.copy(alpha = 0.5f))
            .border(1.dp, astraColors.border, outer)
            .padding(VAO),
    ) {
        val quantas = tabs.size.coerceAtLeast(1)
        val largura = (maxWidth - VAO * (quantas - 1)) / quantas
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .offset(x = (largura + VAO) * posicao.coerceIn(0f, (quantas - 1).toFloat()))
                    .width(largura)
                    .fillMaxHeight()
                    .clip(inner)
                    .background(astraColors.accentDim)
                    .border(1.dp, astraColors.accent.copy(alpha = 0.5f), inner),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VAO)) {
            tabs.forEachIndexed { i, label ->
                val perto = (1f - abs(posicao - i)).coerceIn(0f, 1f)
                val count = counts.getOrNull(i)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(inner)
                        .clickable { onSelect(i) }
                        .semantics {
                            role = Role.Tab
                            selected = perto > 0.5f
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (count != null && count > 0) "$label ($count)" else label,
                        style = MaterialTheme.typography.labelLarge,
                        color = lerp(astraColors.text2, astraColors.accent, perto),
                    )
                }
            }
        }
    }
}
