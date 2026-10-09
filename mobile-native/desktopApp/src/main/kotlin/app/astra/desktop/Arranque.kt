package app.astra.desktop

import java.io.File

object Arranque {
    private const val FILE = "arranque.txt"
    private const val ANTERIOR = "arranque-anterior.txt"
    private const val MARCA_SEGURO = "modo-seguro.txt"
    private const val RECUO = "recuo.txt"

    const val MARCO_RECUOU = "já havia outro Astra aberto — este saiu"

    const val MARCO_CHAMADO = "um segundo Astra pediu a frente — trazendo a janela"

    const val MARCO_JANELA = "janela principal criada"
    private const val MARCO_DESENHOU = "primeiro quadro desenhado"
    private const val MARCO_ESCONDIDO = "nasceu escondido na bandeja — sem quadro por decisao"

    private val comeco = System.nanoTime()

    private val arquivo: File by lazy { File(CrashLog.dataDir(), FILE) }
    private val anterior: File by lazy { File(CrashLog.dataDir(), ANTERIOR) }
    private val marcaSegura: File by lazy { File(CrashLog.dataDir(), MARCA_SEGURO) }
    private val recuo: File by lazy { File(CrashLog.dataDir(), RECUO) }

    var modoSeguro: Boolean = false
        private set

    var acabouDeCair: Boolean = false
        private set

    var quedasSeguidas: Int = 0
        private set

    val desistiuDaPlaca: Boolean get() = modoSeguro && quedasSeguidas >= QUEDAS_PARA_DESISTIR

    private data class Marca(val quedas: Int, val seguro: Boolean)

    private fun lerMarca(): Marca? {
        if (!marcaSegura.exists()) return null
        val partes = marcaSegura.readText().trim().split(' ')
        val quedas = partes.getOrNull(0)?.toIntOrNull() ?: return Marca(1, seguro = true)
        return Marca(quedas, seguro = partes.getOrNull(1) != ESTADO_TENTAR)
    }

    private fun gravarMarca(marca: Marca) {
        marcaSegura.writeText("${marca.quedas} ${if (marca.seguro) ESTADO_SEGURO else ESTADO_TENTAR}\n")
    }

    fun comecar(versao: String) = runCatching {
        val trilha = if (arquivo.exists()) arquivo.readText() else null
        if (trilha != null) {
            acabouDeCair = trilha.contains(MARCO_JANELA) &&
                !trilha.contains(MARCO_DESENHOU) &&
                !trilha.contains(MARCO_ESCONDIDO)
            anterior.writeText(trilha)
        }
        var marca = lerMarca()
        if (acabouDeCair) {
            marca = Marca((marca?.quedas ?: 0) + 1, seguro = true)
            gravarMarca(marca)
        }
        modoSeguro = marca?.seguro == true
        quedasSeguidas = marca?.quedas ?: 0
        arquivo.writeText("Astra $versao — por onde o arranque passou\n")
        if (acabouDeCair) marcar("a abertura anterior criou a janela e NÃO desenhou")
        if (modoSeguro) marcar("MODO SEGURO ligado — desenho por CPU e janela opaca ($quedasSeguidas queda(s) seguida(s))")
        marcar("main")
    }

    @Synchronized
    fun marcar(passo: String) {
        runCatching {
            val ms = (System.nanoTime() - comeco) / 1_000_000
            arquivo.appendText("%6d ms  %s%n".format(ms, passo))
        }
    }

    fun desenhou() {
        marcar(MARCO_DESENHOU)
        runCatching {
            val marca = lerMarca() ?: return@runCatching
            when {
                !modoSeguro -> marcaSegura.delete()
                marca.quedas < QUEDAS_PARA_DESISTIR -> {
                    gravarMarca(marca.copy(seguro = false))
                    marcar("a próxima abertura tenta a placa de vídeo de novo")
                }
                else -> marcar("a placa falhou $QUEDAS_PARA_DESISTIR vezes seguidas — fica no modo seguro")
            }
        }
    }

    fun nasceuEscondido() = marcar(MARCO_ESCONDIDO)

    fun recuou() = runCatching {
        recuo.writeText("$MARCO_RECUOU\n${java.time.LocalDateTime.now()}\n")
    }

    fun recuouDeUmTravado(pid: Long) = runCatching {
        recuo.writeText(
            "o Astra de número $pid segurava a vaga e não respondia — encerrado para abrir outro\n" +
                "${java.time.LocalDateTime.now()}\n",
        )
    }

    fun armarModoSeguro() = runCatching {
        gravarMarca(Marca(lerMarca()?.quedas ?: 0, seguro = true))
    }

    fun sairDoModoSeguro() = runCatching {
        gravarMarca(Marca(lerMarca()?.quedas ?: 0, seguro = false))
        true
    }.getOrDefault(false)

    private const val QUEDAS_PARA_DESISTIR = 2
    private const val ESTADO_SEGURO = "seguro"
    private const val ESTADO_TENTAR = "tentar"
}
