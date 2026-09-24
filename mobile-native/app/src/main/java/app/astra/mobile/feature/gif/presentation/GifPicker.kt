package app.astra.mobile.feature.gif.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.GifResultDto
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import zed.rainxch.rikkaui.components.ui.input.Input
import zed.rainxch.rikkaui.components.ui.input.InputAnimation

@Composable
fun PainelDeGifs(
    aoEscolher: (GifResultDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GifViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsState()

    Column(modifier.fillMaxSize()) {
        Input(
            value = s.query,
            onValueChange = viewModel::onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Buscar GIF",
            singleLine = true,
            animation = InputAnimation.Glow,
        )
        Spacer(Modifier.height(10.dp))

        when {
            s.enabled == false -> AvisoNoMeio("GIFs indisponíveis nesta instância.")
            s.error != null -> AvisoNoMeio(s.error!!)
            s.results.isEmpty() && !s.loading -> AvisoNoMeio("Nada por aqui.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(s.results, key = { it.id }) { g ->
                    val forma = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(forma)
                            .background(astraColors.base)
                            .border(1.dp, astraColors.border, forma)
                            .clickable { aoEscolher(g) },
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

@Composable
private fun AvisoNoMeio(texto: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = astraColors.text3)
    }
}
