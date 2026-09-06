package app.astra.desktop

import java.io.File

object Arranque {
    private const val FILE = "arranque.txt"
    private const val ANTERIOR = "arranque-anterior.txt"
    private const val MARCA_SEGURO = "modo-seguro.txt"

    const val MARCO_JANELA = "janela principal criada"
    private const val MARCO_DESENHOU = "primeiro quadro desenhado"
    private const val MARCO_ESCONDIDO = "nasceu escondido na bandeja — sem quadro por decisao"

    private val comeco = System.nanoTime()

    private val arquivo: File by lazy { File(CrashLog.dataDir(), FILE) }
    private val anterior: File by lazy { File(CrashLog.dataDir(), ANTERIOR) }
    private val marcaSegura: File by lazy { File(CrashLog.dataDir(), MARCA_SEGURO) }

    var modoSeguro: Boolean = false
        private set

    var acabouDeCair: Boolean = false
        private set

    fun comecar(versao: String) = runCatching {
        val trilha = if (arquivo.exists()) arquivo.readText() else null
        if (trilha != null) {
            acabouDeCair = trilha.contains(MARCO_JANELA) &&
                !trilha.contains(MARCO_DESENHOU) &&
                !trilha.contains(MARCO_ESCONDIDO)
            anterior.writeText(trilha)
        }
        modoSeguro = acabouDeCair || marcaSegura.exists()
        arquivo.writeText("Astra $versao — por onde o arranque passou\n")
        if (acabouDeCair) marcar("a abertura anterior criou a janela e NAO desenhou")
        if (modoSeguro) marcar("MODO SEGURO ligado — desenho por CPU e janela opaca")
        marcar("main")
    }

    fun marcar(passo: String) {
        runCatching {
            val ms = (System.nanoTime() - comeco) / 1_000_000
            arquivo.appendText("%6d ms  %s%n".format(ms, passo))
        }
    }

    fun desenhou() {
        marcar(MARCO_DESENHOU)
        if (modoSeguro) runCatching { marcaSegura.writeText("desenhou em modo seguro\n") }
    }

    fun nasceuEscondido() = marcar(MARCO_ESCONDIDO)

    fun sairDoModoSeguro() = runCatching { marcaSegura.delete() }.getOrDefault(false)
}
