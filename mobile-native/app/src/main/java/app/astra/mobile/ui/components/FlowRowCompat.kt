package app.astra.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Substituto de androidx.compose.foundation.layout.FlowRow, feito com Layout (API core
 * estavel). A assinatura 1.7 do FlowRow (com FlowRowOverflow) nao existe na
 * foundation-layout empacotada em runtime (skew de versao arrastado por lib de terceiro),
 * causando NoSuchMethodError. Este wrap quebra os filhos em linhas conforme a largura.
 */
@Composable
fun FlowRowCompat(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val itemConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(itemConstraints) }

        val rows = mutableListOf<List<Placeable>>()
        val rowHeights = mutableListOf<Int>()
        var current = mutableListOf<Placeable>()
        var rowWidth = 0
        var rowHeight = 0
        for (p in placeables) {
            val projected = if (current.isEmpty()) p.width else rowWidth + hGap + p.width
            if (current.isNotEmpty() && projected > maxWidth) {
                rows.add(current)
                rowHeights.add(rowHeight)
                current = mutableListOf()
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth = if (current.isEmpty()) p.width else rowWidth + hGap + p.width
            rowHeight = maxOf(rowHeight, p.height)
            current.add(p)
        }
        if (current.isNotEmpty()) {
            rows.add(current)
            rowHeights.add(rowHeight)
        }

        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            (rows.maxOfOrNull { r -> r.sumOf { it.width } + hGap * (r.size - 1).coerceAtLeast(0) } ?: 0)
                .coerceAtLeast(constraints.minWidth)
        }
        val height = (rowHeights.sum() + vGap * (rows.size - 1).coerceAtLeast(0))
            .coerceAtLeast(constraints.minHeight)

        layout(width, height) {
            var y = 0
            rows.forEachIndexed { i, row ->
                var x = 0
                row.forEach { p ->
                    p.placeRelative(x, y)
                    x += p.width + hGap
                }
                y += rowHeights[i] + vGap
            }
        }
    }
}
