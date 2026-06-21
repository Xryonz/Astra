package app.astra.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.astraColors

/**
 * Abas segmentadas (pill) no estilo editorial — container arredondado com o
 * segmento ativo destacado em accent. Mesma ideia do Tabs do shadcn, em
 * Compose puro. counts opcional mostra "(N)" por aba.
 */
@Composable
fun AstraTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    counts: List<Int?> = emptyList(),
) {
    val outer = RoundedCornerShape(12.dp)
    val inner = RoundedCornerShape(9.dp)
    Row(
        modifier = modifier
            .clip(outer)
            .background(astraColors.raised.copy(alpha = 0.5f))
            .border(1.dp, astraColors.border, outer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val count = counts.getOrNull(i)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(inner)
                    .background(if (selected) astraColors.accentDim else Color.Transparent)
                    .then(
                        if (selected) Modifier.border(1.dp, astraColors.accent.copy(alpha = 0.5f), inner)
                        else Modifier,
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count != null && count > 0) "$label ($count)" else label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) astraColors.accent else astraColors.text2,
                )
            }
        }
    }
}
