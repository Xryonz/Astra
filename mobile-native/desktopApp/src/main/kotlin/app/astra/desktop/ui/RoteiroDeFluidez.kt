package app.astra.desktop.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import app.astra.desktop.CrashLog
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.Selection
import app.astra.desktop.shell.ShellVm
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs
import kotlin.system.exitProcess

private const val CHAVE = "fluidez"
private const val TETO_DA_RODADA_MS = 180_000L
private const val ESPERA_DA_CONVERSA_MS = 20_000L
private const val ESPERA_DA_PAGINA_MS = 15_000L
private const val PASSO_DA_ESPERA_MS = 50L
private const val ASSENTAR_MS = 3_000L
private const val MS_POR_SEGUNDO = 1_000L
private const val NS_POR_MS = 1_000_000L
private const val NS_POR_SEGUNDO = 1_000_000_000f
private const val RAPIDA_PX_POR_S = 2_400f
private const val LEITURA_PX_POR_S = 600f
private const val SUBIDA_RAPIDA_MS = 20_000L
private const val DESCIDA_RAPIDA_MS = 10_000L
private const val SUBIDA_LENTA_MS = 10_000L

internal class ConversaEmprestada(val alvo: String, val lista: LazyListState, val aindaHaHistorico: () -> Boolean)

internal object RoteiroDeFluidez {
    private val valor: String? = System.getProperty("astra.medir")
    val ligado: Boolean = valor == CHAVE || valor?.startsWith("$CHAVE:") == true
    val conversa: String = valor?.substringAfter("$CHAVE:", "").orEmpty()

    @Volatile
    var emprestada: ConversaEmprestada? = null

    @Volatile
    var etapa: String = "esperando a casca"
}

@Composable
internal fun EmprestarListaAoRoteiro(alvo: String, lista: LazyListState, aindaHaHistorico: Boolean) {
    val haHistorico = rememberUpdatedState(aindaHaHistorico)
    DisposableEffect(alvo, lista) {
        val emprestada = ConversaEmprestada(alvo, lista) { haHistorico.value }
        RoteiroDeFluidez.emprestada = emprestada
        onDispose { if (RoteiroDeFluidez.emprestada === emprestada) RoteiroDeFluidez.emprestada = null }
    }
}

internal suspend fun vigiarRoteiroDeFluidez() {
    delay(TETO_DA_RODADA_MS)
    gravarMedicao("tempo esgotado durante: ${RoteiroDeFluidez.etapa}")
    exitProcess(1)
}

internal suspend fun rodarRoteiroDeFluidez(vm: ShellVm) {
    vm.esperarACasca()
    RoteiroDeFluidez.etapa = "abrindo a conversa"
    val conversa = abrirAConversa(vm)
    if (conversa == null) {
        gravarMedicao("sem conversa para rolar")
        exitProcess(1)
    }
    delay(ASSENTAR_MS)
    gravarMedicao(rolar(conversa, "subindo rápido", -RAPIDA_PX_POR_S, SUBIDA_RAPIDA_MS))
    gravarMedicao(rolar(conversa, "descendo rápido", RAPIDA_PX_POR_S, DESCIDA_RAPIDA_MS))
    gravarMedicao(rolar(conversa, "subindo devagar", -LEITURA_PX_POR_S, SUBIDA_LENTA_MS))
    exitProcess(0)
}

private suspend fun abrirAConversa(vm: ShellVm): ConversaEmprestada? {
    val dms = vm.state.value.dms
    val pedida = RoteiroDeFluidez.conversa
    val alvo = if (pedida.isBlank()) dms.firstOrNull() else dms.find { it.id == pedida }
    if (alvo == null) return null
    val titulo = alvo.otherUser?.displayName ?: alvo.otherUser?.username ?: "sussurro"
    vm.select(Selection.Dms)
    vm.openChat(ChatTarget.Dm(alvo.id, titulo))
    return esperarAConversa(alvo.id)
}

private suspend fun esperarAConversa(alvo: String): ConversaEmprestada? {
    val prazo = System.currentTimeMillis() + ESPERA_DA_CONVERSA_MS
    while (System.currentTimeMillis() < prazo) {
        RoteiroDeFluidez.emprestada
            ?.takeIf { it.alvo == alvo && it.lista.layoutInfo.totalItemsCount > 0 }
            ?.let { return it }
        delay(PASSO_DA_ESPERA_MS)
    }
    return null
}

private suspend fun rolar(conversa: ConversaEmprestada, fase: String, pxPorSegundo: Float, duracaoMs: Long): String {
    RoteiroDeFluidez.etapa = fase
    val paraCima = pxPorSegundo < 0
    QuadrosPorFase.comecar(fase)
    val inicio = System.nanoTime()
    val fim = inicio + duracaoMs * NS_POR_MS
    var despachados = 0f
    var esperas = 0
    var motivo: String? = null
    var anterior = withFrameNanos { it }
    while (motivo == null && System.nanoTime() < fim) {
        val agora = withFrameNanos { it }
        val andou = conversa.lista.dispatchRawDelta(pxPorSegundo * (agora - anterior) / NS_POR_SEGUNDO)
        despachados += abs(andou)
        anterior = agora
        if (andou == 0f) {
            motivo = motivoParaParar(conversa, paraCima)
            if (motivo == null) {
                esperas++
                anterior = withFrameNanos { it }
            }
        }
    }
    val segundos = (System.nanoTime() - inicio) / NS_POR_SEGUNDO
    val percurso = "%.0f px despachados em %.1f s, %d esperas por página".format(despachados, segundos, esperas)
    return "${QuadrosPorFase.encerrar().linha()}\t$percurso\t${motivo ?: "durou o tempo previsto"}"
}

private suspend fun motivoParaParar(conversa: ConversaEmprestada, paraCima: Boolean): String? = when {
    !paraCima -> "chegou ao fim da conversa"
    !conversa.aindaHaHistorico() -> "chegou ao começo da conversa"
    esperarAPaginaAnterior(conversa) -> null
    else -> "a página anterior não chegou em ${ESPERA_DA_PAGINA_MS / MS_POR_SEGUNDO} s"
}

private suspend fun esperarAPaginaAnterior(conversa: ConversaEmprestada): Boolean {
    val prazo = System.currentTimeMillis() + ESPERA_DA_PAGINA_MS
    while (!conversa.lista.canScrollBackward && System.currentTimeMillis() < prazo) delay(PASSO_DA_ESPERA_MS)
    return conversa.lista.canScrollBackward
}

private fun gravarMedicao(linha: String) {
    File(CrashLog.dataDir(), "medicao.txt").appendText("$CHAVE\t$linha\n")
}
