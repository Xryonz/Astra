package app.astra.desktop.ui

import kotlinx.coroutines.delay

suspend fun esperarPeloTeto(cap: Int, inicioDoQuadroNanos: Long) {
    if (cap <= 0) return
    val periodo = 1_000_000_000L / cap
    val gasto = System.nanoTime() - inicioDoQuadroNanos
    val resta = periodo - gasto
    if (resta > 0) delay(resta / 1_000_000)
}
