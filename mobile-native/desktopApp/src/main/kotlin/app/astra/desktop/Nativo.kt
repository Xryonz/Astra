package app.astra.desktop

import java.util.concurrent.ConcurrentHashMap

object Nativo {

    private val recusados = ConcurrentHashMap.newKeySet<String>()

    val desligado: Boolean get() = Arranque.modoSeguro

    fun <T> tentar(oQue: String, bloco: () -> T): T? {
        if (desligado) return null
        return runCatching(bloco).getOrElse { falha ->
            if (recusados.add(oQue)) {
                Arranque.marcar("nativo recusado — $oQue: ${falha::class.simpleName}: ${falha.message.orEmpty().take(120)}")
            }
            null
        }
    }

    fun recusados(): List<String> = recusados.sorted()
}
