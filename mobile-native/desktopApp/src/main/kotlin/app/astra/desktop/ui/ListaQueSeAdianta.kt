package app.astra.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.PrefetchRequest
import androidx.compose.foundation.lazy.layout.PrefetchRequestScope
import androidx.compose.foundation.lazy.layout.PrefetchScheduler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import java.awt.GraphicsEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val FOLGA_ANTES_DO_QUADRO_NS = 1_000_000L

private const val TRABALHO_MINIMO_NS = 100_000L

private const val PASSADAS_SEM_AVANCO = 3

private const val TAXA_PADRAO_HZ = 60

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun lembrarListaQueSeAdianta(): LazyListState {
    val escopo = rememberCoroutineScope()
    val naTela = rememberUpdatedState(LocalJanelaNaTela.current)
    val estrategia = remember(escopo) {
        EstrategiaQueSeAdianta(PreparoEntreQuadros(escopo, intervaloDaTelaMaisRapidaNs()) { naTela.value })
    }
    return rememberLazyListState(0, 0, estrategia)
}

@OptIn(ExperimentalFoundationApi::class)
private class EstrategiaQueSeAdianta(
    private val agenda: PrefetchScheduler,
    base: LazyListPrefetchStrategy = LazyListPrefetchStrategy(),
) : LazyListPrefetchStrategy by base {
    override val prefetchScheduler: PrefetchScheduler
        get() = agenda
}

@OptIn(ExperimentalFoundationApi::class)
private class PreparoEntreQuadros(
    private val escopo: CoroutineScope,
    private val intervaloNs: Long,
    private val naTela: () -> Boolean,
) : PrefetchScheduler {
    private val pedidos = ArrayDeque<PrefetchRequest>()
    private var agendado = false

    override fun schedulePrefetch(prefetchRequest: PrefetchRequest) {
        pedidos.addLast(prefetchRequest)
        if (agendado) return
        agendado = true
        escopo.launch {
            try {
                var passadasSemAvanco = 0
                while (pedidos.isNotEmpty() && naTela() && passadasSemAvanco < PASSADAS_SEM_AVANCO) {
                    val inicioDoQuadro = withFrameNanos { System.nanoTime() }
                    val janela = JanelaEntreQuadros(inicioDoQuadro + intervaloNs - FOLGA_ANTES_DO_QUADRO_NS)
                    passadasSemAvanco = if (janela.adiantar(pedidos)) 0 else passadasSemAvanco + 1
                }
            } finally {
                agendado = false
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private class JanelaEntreQuadros(private val prazoNs: Long) : PrefetchRequestScope {
    override fun availableTimeNanos(): Long = (prazoNs - System.nanoTime()).coerceAtLeast(0)

    fun adiantar(pedidos: ArrayDeque<PrefetchRequest>): Boolean {
        var avancou = false
        while (pedidos.isNotEmpty() && availableTimeNanos() > 0) {
            val antes = System.nanoTime()
            val faltouTempo = with(pedidos.first()) { execute() }
            if (System.nanoTime() - antes >= TRABALHO_MINIMO_NS) avancou = true
            if (faltouTempo) break
            pedidos.removeFirst()
            avancou = true
        }
        return avancou
    }
}

private fun intervaloDaTelaMaisRapidaNs(): Long {
    val hz = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.maxOf { it.displayMode.refreshRate }
    }
        .getOrNull()
        ?.takeIf { it > 0 }
        ?: TAXA_PADRAO_HZ
    return 1_000_000_000L / hz
}
