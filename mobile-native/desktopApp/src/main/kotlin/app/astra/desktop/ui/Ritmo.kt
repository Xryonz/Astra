package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

private const val NS_POR_MS = 1_000_000L
private const val QUADROS_DO_ENFEITE = 30

suspend fun esperarPeloTeto(cap: Int, inicioDoQuadroNanos: Long) {
    if (cap <= 0) return
    val periodo = 1_000_000_000L / cap
    val gasto = System.nanoTime() - inicioDoQuadroNanos
    val resta = periodo - gasto
    if (resta > 0) delay(resta / NS_POR_MS)
}

suspend fun tocarNoRitmo(quadrosPorSegundo: Int, aCadaQuadro: () -> Unit) {
    if (quadrosPorSegundo <= 0) return
    val periodo = 1_000_000_000L / quadrosPorSegundo
    var prazo = System.nanoTime()
    while (true) {
        prazo += periodo
        val agora = System.nanoTime()
        if (agora - prazo > periodo) prazo = agora
        delay((prazo - agora).coerceAtLeast(0) / NS_POR_MS)
        aCadaQuadro()
    }
}

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
