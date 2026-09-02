package app.astra.desktop.shell

class CacheDeConversas(quantasConversas: Int = CONVERSAS) {

    private val guardadas = object : LinkedHashMap<String, List<ChatMessage>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<ChatMessage>>) =
            size > quantasConversas
    }

    @Synchronized
    fun ler(alvo: ChatTarget): List<ChatMessage>? = guardadas[chave(alvo)]

    @Synchronized
    fun guardar(alvo: ChatTarget, mensagens: List<ChatMessage>) {
        val firmes = mensagens.filterNot { it.pending || it.failed || it.deleting }
        if (firmes.isEmpty()) {
            guardadas.remove(chave(alvo))
        } else {
            guardadas[chave(alvo)] = firmes.takeLast(MENSAGENS)
        }
    }

    @Synchronized
    fun esquecer(alvo: ChatTarget) {
        guardadas.remove(chave(alvo))
    }

    private fun chave(alvo: ChatTarget) = when (alvo) {
        is ChatTarget.Channel -> "orbita:${alvo.id}"
        is ChatTarget.Dm -> "sussurro:${alvo.id}"
    }

    private companion object {
        const val CONVERSAS = 20
        const val MENSAGENS = 50
    }
}
