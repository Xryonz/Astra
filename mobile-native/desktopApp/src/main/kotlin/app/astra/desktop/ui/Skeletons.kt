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

private val FATIAS_DA_FALA = listOf(0.74f, 0.41f, 0.93f, 0.58f, 0.32f, 0.86f, 0.47f, 0.67f)
private val FATIAS_DO_NOME = listOf(0.20f, 0.14f, 0.26f, 0.17f, 0.23f, 0.15f, 0.28f, 0.19f)
private const val FATIA_DA_SOBRA = 0.62f

@Composable
fun ChatSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FATIAS_DA_FALA.forEachIndexed { i, fatia ->
            Row(Modifier.fillMaxWidth()) {
                Skeleton(Modifier.size(34.dp), Shimmer, CircleShape)
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Skeleton(
                        Modifier.fillMaxWidth(FATIAS_DO_NOME[i]).height(11.dp),
                        Shimmer,
                        RoundedCornerShape(5.dp),
                    )
                    Skeleton(
                        Modifier.fillMaxWidth(fatia).height(12.dp),
                        Shimmer,
                        RoundedCornerShape(5.dp),
                    )
                    if (i % 3 == 0) {
                        Skeleton(
                            Modifier.fillMaxWidth(fatia * FATIA_DA_SOBRA).height(12.dp),
                            Shimmer,
                            RoundedCornerShape(5.dp),
                        )
                    }
                }
            }
        }
    }
}
