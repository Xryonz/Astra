package app.astra.desktop.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import app.astra.desktop.CrashLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private const val AMOSTRAS = 240
private const val TETO_DE_QUADRO_MS = 100.0
private const val MINIMO_PARA_OPINAR = 60

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

    fun marcar() {
        val agora = System.nanoTime()
        val ultimo = anterior
        anterior = agora
        if (ultimo == 0L) return
        val ms = (agora - ultimo) / 1_000_000.0
        if (ms > TETO_DE_QUADRO_MS) return
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
            arquivo.writeText("Astra — medicao de quadro ($alvo)\n")
            while (isActive) {
                delay(5_000)
                arquivo.appendText("$alvo\t${resumo()}\n")
            }
        }
    }
}

fun Modifier.contandoQuadros(): Modifier = drawWithContent {
    Quadros.marcar()
    drawContent()
}
