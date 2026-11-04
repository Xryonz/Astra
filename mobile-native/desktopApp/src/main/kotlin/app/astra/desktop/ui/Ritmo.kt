package app.astra.desktop.ui

import kotlinx.coroutines.delay

private const val NS_POR_MS = 1_000_000L

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
