package app.astra.mobile.feature.stickers.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.ServerStickerDto
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage

@Composable
fun PainelDeFigurinhas(
    orbitaId: String,
    aoEscolher: (ServerStickerDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FigurinhasViewModel = hiltViewModel(),
) {
    LaunchedEffect(orbitaId) { viewModel.carregar(orbitaId) }
    val figurinhas by viewModel.figurinhas.collectAsState()
    val carregando by viewModel.carregando.collectAsState()

    Box(modifier.fillMaxSize()) {
        when {
            carregando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
            figurinhas.isEmpty() -> Text(
                text = "Nenhuma figurinha nesta constelação ainda. Quem cuida dela pode subir nos ajustes.",
                style = MaterialTheme.typography.bodyMedium,
                color = astraColors.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(figurinhas, key = { it.id }) { fig ->
                    val forma = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(forma)
                            .background(astraColors.base)
                            .clickable { aoEscolher(fig) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = fig.url,
                            contentDescription = fig.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
