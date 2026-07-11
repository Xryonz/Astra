package app.astra.desktop.ui

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

// Skeletons shimmer (RikkaUI, cores do RikkaTheme obsidiana do Main) no lugar
// dos textos de loading. Densidade DESKTOP (compacta) — nao os tamanhos touch
// do ListSkeleton do mobile.

private val Shimmer = SkeletonAnimation.Shimmer

@Composable
fun SidebarSkeleton(rows: Int = 9, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(top = 6.dp)) {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Skeleton(Modifier.size(30.dp), Shimmer, CircleShape)
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Skeleton(
                        Modifier.width(if (it % 2 == 0) 96.dp else 128.dp).height(11.dp),
                        Shimmer,
                        RoundedCornerShape(5.dp),
                    )
                    Skeleton(
                        Modifier.width(if (it % 3 == 0) 150.dp else 80.dp).height(9.dp),
                        Shimmer,
                        RoundedCornerShape(5.dp),
                    )
                }
            }
        }
    }
}

// Linhas estilo Discord (avatar + nome + texto), nao bolhas como o mobile —
// espelha o layout real do ChatView.
@Composable
fun ChatSkeleton(modifier: Modifier = Modifier) {
    val widths = listOf(320, 180, 420, 240, 140, 360, 200, 280)
    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        widths.forEachIndexed { i, w ->
            Row(Modifier.fillMaxWidth()) {
                Skeleton(Modifier.size(34.dp), Shimmer, CircleShape)
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Skeleton(
                        Modifier.width((70 + (i * 37) % 60).dp).height(11.dp),
                        Shimmer,
                        RoundedCornerShape(5.dp),
                    )
                    Skeleton(Modifier.width(w.dp).height(12.dp), Shimmer, RoundedCornerShape(5.dp))
                    if (i % 3 == 0) {
                        Skeleton(
                            Modifier.width((w * 2 / 3).dp).height(12.dp),
                            Shimmer,
                            RoundedCornerShape(5.dp),
                        )
                    }
                }
            }
        }
    }
}
