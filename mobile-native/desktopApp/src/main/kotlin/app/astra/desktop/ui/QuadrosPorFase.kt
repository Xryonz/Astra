package app.astra.desktop.ui

private const val ORCAMENTO_60_HZ_MS = 1_000.0 / 60
private const val ORCAMENTO_120_HZ_MS = 1_000.0 / 120
private const val MEDIANA = 0.50
private const val P95 = 0.95
private const val P99 = 0.99

internal data class ResumoDaFase(
    val fase: String,
    val quadros: Int,
    val medianaMs: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maiorMs: Double,
    val acimaDe60Hz: Int,
    val acimaDe120Hz: Int,
) {
    fun linha(): String = listOf(
        fase,
        "$quadros quadros",
        "mediana %.2f ms".format(medianaMs),
        "p95 %.2f ms".format(p95Ms),
        "p99 %.2f ms".format(p99Ms),
        "maior %.2f ms".format(maiorMs),
        "acima de 16,7 ms: $acimaDe60Hz",
        "acima de 8,3 ms: $acimaDe120Hz",
    ).joinToString("\t")
}

internal object QuadrosPorFase {
    private val tempos = ArrayList<Double>()
    private var fase: String? = null

    @Synchronized
    fun comecar(nome: String) {
        tempos.clear()
        fase = nome
    }

    @Synchronized
    fun anotar(ms: Double) {
        if (fase != null) tempos.add(ms)
    }

    @Synchronized
    fun encerrar(): ResumoDaFase {
        val nome = fase.orEmpty()
        fase = null
        val ordenados = tempos.sorted()
        return ResumoDaFase(
            fase = nome,
            quadros = ordenados.size,
            medianaMs = ordenados.percentil(MEDIANA),
            p95Ms = ordenados.percentil(P95),
            p99Ms = ordenados.percentil(P99),
            maiorMs = ordenados.lastOrNull() ?: 0.0,
            acimaDe60Hz = ordenados.count { it > ORCAMENTO_60_HZ_MS },
            acimaDe120Hz = ordenados.count { it > ORCAMENTO_120_HZ_MS },
        )
    }
}

private fun List<Double>.percentil(fracao: Double): Double =
    if (isEmpty()) 0.0 else this[((size - 1) * fracao).toInt()]
