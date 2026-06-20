package app.astra.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.astraColors

// ── Skeletons shimmer — placeholders que pulsam enquanto a tela carrega
//    (espelham DMListSkeleton / MessageListSkeleton / SidebarSkeleton do web).
//    Um unico gradiente prata varre da esquerda pra direita, em loop. ──

/** Gradiente prata que desliza — base do efeito shimmer. Lembrar 1x por tela. */
@Composable
private fun rememberShimmer(): Brush {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x",
    )
    val span = 360f
    return Brush.linearGradient(
        colors = listOf(astraColors.raised, astraColors.hover, astraColors.raised),
        start = Offset(x * (2 * span) - span, 0f),
        end = Offset(x * (2 * span), 0f),
    )
}

@Composable
private fun SkelBar(brush: Brush, width: Modifier, height: Int, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(width.height(height.dp).clip(shape).background(brush))
}

/** Lista generica: linhas com (opcional) avatar + 2 barras de texto. */
@Composable
fun ListSkeleton(rows: Int = 8, avatar: Boolean = true, modifier: Modifier = Modifier) {
    val brush = rememberShimmer()
    Column(modifier.fillMaxSize().padding(top = 6.dp)) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (avatar) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(brush))
                    Spacer(Modifier.width(14.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SkelBar(brush, Modifier.width(if (it % 2 == 0) 150.dp else 190.dp), 13)
                    SkelBar(brush, Modifier.width(if (it % 3 == 0) 220.dp else 120.dp), 11)
                }
            }
        }
    }
}

/** Historico de chat: bolhas alternadas (esquerda/direita) de larguras variadas. */
@Composable
fun MessageListSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmer()
    val widths = listOf(180, 120, 240, 90, 160, 210, 130)
    Column(
        modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        widths.forEachIndexed { i, w ->
            val mine = i % 3 == 0
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            ) {
                Box(
                    Modifier
                        .width(w.dp)
                        .height((30 + (w % 40)).dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(brush),
                )
            }
        }
    }
}
