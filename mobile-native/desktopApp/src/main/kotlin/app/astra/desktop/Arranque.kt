package app.astra.desktop

import java.io.File

object Arranque {
    private const val FILE = "arranque.txt"
    private const val ANTERIOR = "arranque-anterior.txt"

    const val MARCO_JANELA = "janela principal criada"
    private const val MARCO_DESENHOU = "primeiro quadro desenhado"
    private const val MARCO_ESCONDIDO = "nasceu escondido na bandeja — sem quadro por decisao"

    private val comeco = System.nanoTime()

    private val arquivo: File by lazy { File(CrashLog.dataDir(), FILE) }
    private val anterior: File by lazy { File(CrashLog.dataDir(), ANTERIOR) }

    var arranqueAnteriorFalhou: Boolean = false
        private set

    fun comecar(versao: String) = runCatching {
        val trilha = if (arquivo.exists()) arquivo.readText() else null
        if (trilha != null) {
            arranqueAnteriorFalhou = trilha.contains(MARCO_JANELA) &&
                !trilha.contains(MARCO_DESENHOU) &&
                !trilha.contains(MARCO_ESCONDIDO)
            anterior.writeText(trilha)
        }
        arquivo.writeText("Astra $versao — por onde o arranque passou\n")
        if (arranqueAnteriorFalhou) {
            marcar("o arranque anterior criou a janela e NAO desenhou — este vai em modo seguro")
        }
        marcar("main")
    }

    fun marcar(passo: String) {
        runCatching {
            val ms = (System.nanoTime() - comeco) / 1_000_000
            arquivo.appendText("%6d ms  %s%n".format(ms, passo))
        }
    }

    fun desenhou() = marcar(MARCO_DESENHOU)

    fun nasceuEscondido() = marcar(MARCO_ESCONDIDO)
}
