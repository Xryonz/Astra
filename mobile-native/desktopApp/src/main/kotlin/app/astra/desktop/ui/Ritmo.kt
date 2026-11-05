package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

private const val NS_POR_MS = 1_000_000L
private const val NS_POR_S = 1_000_000_000L
private const val QUADROS_DO_ENFEITE = 30

private suspend fun esperarOCompasso(quadrosPorSegundo: Int) {
    val agora = System.nanoTime()
    val compasso = (agora * quadrosPorSegundo / NS_POR_S + 1) * NS_POR_S / quadrosPorSegundo
    delay((compasso - agora + NS_POR_MS - 1) / NS_POR_MS)
}

suspend fun esperarPeloTeto(cap: Int) {
    if (cap <= 0) return
    esperarOCompasso(cap)
}

suspend fun tocarNoRitmo(quadrosPorSegundo: Int, aCadaQuadro: () -> Unit) {
    if (quadrosPorSegundo <= 0) return
    while (true) {
        esperarOCompasso(quadrosPorSegundo)
        aCadaQuadro()
    }
}

internal fun idaEVolta(fase: Float): Float = if (fase < 0.5f) fase * 2f else 2f - fase * 2f

@Composable
internal fun faseDeEnfeite(voltaMs: Int, repouso: Float): State<Float> {
    val parado = LocalReduceMotion.current
    return produceState(repouso, parado, voltaMs) {
        if (parado) {
            value = repouso
            return@produceState
        }
        val inicio = System.nanoTime()
        tocarNoRitmo(QUADROS_DO_ENFEITE) {
            val decorrido = (System.nanoTime() - inicio) / NS_POR_MS % voltaMs
            value = (repouso + decorrido.toFloat() / voltaMs) % 1f
        }
    }
}
