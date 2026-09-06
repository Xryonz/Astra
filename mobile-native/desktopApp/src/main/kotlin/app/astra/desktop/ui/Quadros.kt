package app.astra.desktop.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import app.astra.desktop.CrashLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

private const val AMOSTRAS = 240
private const val TETO_DE_QUADRO_MS = 100.0
private const val PISO_DE_QUADRO_MS = 0.5
private const val MINIMO_PARA_OPINAR = 60
private const val RODADAS_DA_MEDICAO = 6

object Quadros {
    private val janela = DoubleArray(AMOSTRAS)
    private var escritas = 0
    private var cursor = 0
    private var anterior = 0L

    @Volatile var medianaMs: Double = 0.0
        private set

    @Volatile var p95Ms: Double = 0.0
        private set

    @Volatile var amostras: Int = 0
        private set

    private val custos = LinkedHashMap<String, DoubleArray>()
    private val contagens = LinkedHashMap<String, Int>()

    inline fun <T> cronometrar(rotulo: String, bloco: () -> T): T {
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

    fun marcar() {
        val agora = System.nanoTime()
        val ultimo = anterior
        anterior = agora
        if (ultimo == 0L) return
        val ms = (agora - ultimo) / 1_000_000.0
        if (ms > TETO_DE_QUADRO_MS || ms < PISO_DE_QUADRO_MS) return
        janela[cursor] = ms
        cursor = (cursor + 1) % AMOSTRAS
        if (escritas < AMOSTRAS) escritas++
        if (escritas >= MINIMO_PARA_OPINAR) recalcular()
    }

    fun esquecer() {
        escritas = 0
        cursor = 0
        anterior = 0L
        amostras = 0
        medianaMs = 0.0
        p95Ms = 0.0
    }

    private fun recalcular() {
        val copia = janela.copyOf(escritas)
        copia.sort()
        medianaMs = copia[copia.size / 2]
        p95Ms = copia[(copia.size * 95) / 100]
        amostras = copia.size
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

fun Modifier.contandoQuadros(): Modifier = drawWithContent {
    Quadros.marcar()
    drawContent()
}
