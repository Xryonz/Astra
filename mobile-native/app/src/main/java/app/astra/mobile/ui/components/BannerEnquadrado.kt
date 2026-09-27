package app.astra.mobile.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import app.astra.mobile.core.upload.RodadaDasImagens
import coil3.compose.AsyncImage
import kotlin.math.ceil
import kotlin.math.max

const val PROPORCAO_DO_BANNER_DA_ORBITA = 3f
const val PROPORCAO_DO_BANNER_DO_PERFIL = 3.5f
private const val ZOOM_MAXIMO_DO_BANNER = 300
private const val FOLGA_DO_ARREDONDAMENTO = 0.001f

@Composable
fun ImagemDoBanner(url: String, posicaoY: Int, escala: Int, modifier: Modifier = Modifier) {
    var falhou by remember(url, RodadaDasImagens.valor) { mutableStateOf(false) }
    if (falhou) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = BiasAlignment(0f, posicaoY.coerceIn(0, 100) / 50f - 1f),
        modifier = modifier.fillMaxSize().scale(escala.coerceIn(0, ZOOM_MAXIMO_DO_BANNER) / 100f),
        onError = { falhou = true },
    )
}

fun zoomQueCobre(proporcaoDaImagem: Float, proporcaoDaCaixa: Float): Int {
    val fator = max(proporcaoDaCaixa / proporcaoDaImagem, proporcaoDaImagem / proporcaoDaCaixa)
    return ceil(fator * 100 - FOLGA_DO_ARREDONDAMENTO).toInt().coerceIn(100, ZOOM_MAXIMO_DO_BANNER)
}
