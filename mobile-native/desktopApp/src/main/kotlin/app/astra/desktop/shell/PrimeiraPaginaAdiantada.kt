package app.astra.desktop.shell

import kotlinx.coroutines.Deferred

class PrimeiraPaginaAdiantada {
    private var alvo: ChatTarget? = null
    private var pagina: Deferred<Pair<List<ChatMessage>, String?>>? = null

    @Synchronized
    fun guardar(alvo: ChatTarget, pagina: Deferred<Pair<List<ChatMessage>, String?>>) {
        this.pagina?.cancel()
        this.alvo = alvo
        this.pagina = pagina
    }

    @Synchronized
    fun tomar(alvo: ChatTarget): Deferred<Pair<List<ChatMessage>, String?>>? {
        val guardado = this.alvo ?: return null
        if (guardado::class != alvo::class || guardado.id != alvo.id) return null
        return pagina.also { esquecer() }
    }

    @Synchronized
    fun descartar() {
        pagina?.cancel()
        esquecer()
    }

    private fun esquecer() {
        alvo = null
        pagina = null
    }
}
