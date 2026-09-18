package app.astra.mobile.feature.gif.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.GifResultDto
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import zed.rainxch.rikkaui.components.ui.input.Input
import zed.rainxch.rikkaui.components.ui.input.InputAnimation

@Composable
fun GifPicker(
    onPick: (GifResultDto) -> Unit,
    onClose: () -> Unit,
    viewModel: GifViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsState()
    BackHandler(onBack = onClose)

    val panelShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .imePadding(),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .clip(panelShape)
                .background(astraColors.raised)
                .border(1.dp, astraColors.borderMid, panelShape)
                .pointerInput(Unit) { detectTapGestures {} }
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Input(
                    value = s.query,
                    onValueChange = viewModel::onQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = "Buscar GIF",
                    singleLine = true,
                    animation = InputAnimation.Glow,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleLarge,
                    color = astraColors.text2,
                    modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            when {
                s.enabled == false -> CenterMsg("GIF desabilitado no servidor (sem GIPHY_API_KEY).")
                s.error != null -> CenterMsg(s.error!!)
                s.results.isEmpty() && !s.loading -> CenterMsg("Nada por aqui.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(s.results, key = { it.id }) { g ->
                        val tile = RoundedCornerShape(10.dp)
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(tile)
                                .background(astraColors.base)
                                .border(1.dp, astraColors.border, tile)
                                .clickable { onPick(g); onClose() },
                        ) {
                            AsyncImage(
                                model = g.preview,
                                contentDescription = g.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMsg(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = astraColors.text3)
    }
}
