package app.astra.desktop.ui

import app.astra.desktop.CrashLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

private const val AMOSTRAS = 240
private const val MINIMO_PARA_OPINAR = 60
private const val RODADAS_DA_MEDICAO = 6

object Quadros {
    private val janela = DoubleArray(AMOSTRAS)
    private val ordenados = DoubleArray(AMOSTRAS)
    private var escritas = 0
    private var cursor = 0

    val medianaMs: Double get() = percentil(50)

    val p95Ms: Double get() = percentil(95)

    val amostras: Int
        @Synchronized get() = if (escritas >= MINIMO_PARA_OPINAR) escritas else 0

    private val custos = LinkedHashMap<String, DoubleArray>()
    private val contagens = LinkedHashMap<String, Int>()

    val medindo: Boolean = System.getProperty("astra.medir") != null

    inline fun <T> cronometrar(rotulo: String, bloco: () -> T): T {
        if (!medindo) return bloco()
        val inicio = System.nanoTime()
        try {
            return bloco()
        } finally {
            anotarCusto(rotulo, (System.nanoTime() - inicio) / 1_000_000.0)
        }
    }

    fun anotarCusto(rotulo: String, ms: Double) {
        synchronized(custos) {
            val alvo = custos.getOrPut(rotulo) { DoubleArray(AMOSTRAS) }
            val n = contagens.getOrDefault(rotulo, 0)
            alvo[n % AMOSTRAS] = ms
            contagens[rotulo] = n + 1
        }
    }

    fun custoResumido(): String = synchronized(custos) {
        if (custos.isEmpty()) return "sem custos medidos"
        custos.entries.joinToString(" | ") { (rotulo, valores) ->
            val n = minOf(contagens.getOrDefault(rotulo, 0), AMOSTRAS)
            if (n < 30) return@joinToString "$rotulo: poucas amostras"
            val copia = valores.copyOf(n).also { it.sort() }
            "%s %.3f ms (p95 %.3f)".format(rotulo, copia[n / 2], copia[(n * 95) / 100])
        }
    }

    @Synchronized
    fun anotarQuadro(ms: Double) {
        janela[cursor] = ms
        cursor = (cursor + 1) % AMOSTRAS
        if (escritas < AMOSTRAS) escritas++
    }

    @Synchronized
    fun esquecer() {
        escritas = 0
        cursor = 0
    }

    @Synchronized
    private fun percentil(p: Int): Double {
        if (escritas < MINIMO_PARA_OPINAR) return 0.0
        System.arraycopy(janela, 0, ordenados, 0, escritas)
        ordenados.sort(0, escritas)
        return ordenados[(escritas * p) / 100]
    }

    fun resumo(): String =
        if (amostras < MINIMO_PARA_OPINAR) "sem amostras suficientes"
        else "mediana %.2f ms · p95 %.2f ms · %d quadros".format(medianaMs, p95Ms, amostras)

    fun medirParaArquivo(escopo: CoroutineScope) {
        val alvo = System.getProperty("astra.medir") ?: return
        escopo.launch(Dispatchers.IO) {
            val arquivo = File(CrashLog.dataDir(), "medicao.txt")
            repeat(RODADAS_DA_MEDICAO) {
                delay(5_000)
                arquivo.appendText("$alvo\t${resumo()}\t${custoResumido()}\n")
            }
            exitProcess(0)
        }
    }
}
