package app.astra.mobile.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlin.math.ceil
import kotlin.math.max

const val PROPORCAO_DO_BANNER_DA_ORBITA = 3f
const val PROPORCAO_DO_BANNER_DO_PERFIL = 3.5f
private const val ZOOM_MAXIMO_DO_BANNER = 300
private const val FOLGA_DO_ARREDONDAMENTO = 0.001f

@Composable
fun ImagemDoBanner(url: String, posicaoY: Int, escala: Int, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = BiasAlignment(0f, posicaoY.coerceIn(0, 100) / 50f - 1f),
        modifier = modifier.fillMaxSize().scale(escala.coerceIn(0, ZOOM_MAXIMO_DO_BANNER) / 100f),
    )
}

fun zoomQueCobre(proporcaoDaImagem: Float, proporcaoDaCaixa: Float): Int {
    val fator = max(proporcaoDaCaixa / proporcaoDaImagem, proporcaoDaImagem / proporcaoDaCaixa)
    return ceil(fator * 100 - FOLGA_DO_ARREDONDAMENTO).toInt().coerceIn(100, ZOOM_MAXIMO_DO_BANNER)
}
