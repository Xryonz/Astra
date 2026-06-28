package app.astra.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zed.rainxch.rikkaui.components.ui.skeleton.Skeleton
import zed.rainxch.rikkaui.components.ui.skeleton.SkeletonAnimation

// Skeletons via RikkaUI Skeleton (Shimmer): uma varredura de luz percorre cada
// placeholder. Tamanho/forma vêm do modifier; a animação, do componente.

private val Shimmer = SkeletonAnimation.Shimmer

/** Lista generica: linhas com (opcional) avatar + 2 barras de texto. */
@Composable
fun ListSkeleton(rows: Int = 8, avatar: Boolean = true, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(top = 6.dp)) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (avatar) {
                    Skeleton(Modifier.size(46.dp), Shimmer, CircleShape)
                    Spacer(Modifier.width(14.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Skeleton(
                        Modifier.width(if (it % 2 == 0) 150.dp else 190.dp).height(13.dp),
                        Shimmer,
                        RoundedCornerShape(6.dp),
                    )
                    Skeleton(
                        Modifier.width(if (it % 3 == 0) 220.dp else 120.dp).height(11.dp),
                        Shimmer,
                        RoundedCornerShape(6.dp),
                    )
                }
            }
        }
    }
}

/** Historico de chat: bolhas alternadas (esquerda/direita) de larguras variadas. */
@Composable
fun MessageListSkeleton(modifier: Modifier = Modifier) {
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
                Skeleton(
                    Modifier.width(w.dp).height((30 + (w % 40)).dp),
                    Shimmer,
                    RoundedCornerShape(16.dp),
                )
            }
        }
    }
}
