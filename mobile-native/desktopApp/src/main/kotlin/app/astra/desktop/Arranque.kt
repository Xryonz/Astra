package app.astra.desktop

import java.io.File

object Arranque {
    private const val FILE = "arranque.txt"

    private val comeco = System.nanoTime()

    private val arquivo: File by lazy { File(CrashLog.dataDir(), FILE) }

    fun comecar(versao: String) = runCatching {
        arquivo.writeText("Astra $versao — por onde o arranque passou\n")
        marcar("main")
    }

    fun marcar(passo: String) {
        runCatching {
            val ms = (System.nanoTime() - comeco) / 1_000_000
            arquivo.appendText("%6d ms  %s%n".format(ms, passo))
        }
    }
}
