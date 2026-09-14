package app.astra.desktop

private const val TETO_PADRAO = 64

class CacheDeSucesso<K : Any, V : Any>(private val teto: Int = TETO_PADRAO) {

    private val guardados = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>) = size > teto
    }

    @Synchronized
    fun guardado(chave: K): V? = guardados[chave]

    @Synchronized
    fun esquecer(chave: K) {
        guardados.remove(chave)
    }

    @Synchronized
    fun esquecerTudo() {
        guardados.clear()
    }

    suspend fun obter(chave: K, buscar: suspend () -> V): V? {
        guardado(chave)?.let { return it }
        val veio = runCatching { buscar() }.getOrNull() ?: return null
        synchronized(this) { guardados[chave] = veio }
        return veio
    }
}
