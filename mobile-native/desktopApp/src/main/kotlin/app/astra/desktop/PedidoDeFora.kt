package app.astra.desktop

import java.io.File

object PedidoDeFora {
    const val DESINSTALAR = "desinstalar"

    private const val ARQUIVO = "pedido.txt"
    private const val VALIDADE_MS = 60_000L

    private val arquivo: File get() = File(CrashLog.dataDir(), ARQUIVO)

    fun deixar(pedido: String) {
        runCatching {
            arquivo.parentFile?.mkdirs()
            arquivo.writeText("$pedido ${System.currentTimeMillis()}")
        }
    }

    fun pegar(): String? {
        val texto = runCatching { arquivo.readText() }.getOrNull() ?: return null
        runCatching { arquivo.delete() }
        val quando = texto.substringAfter(' ').trim().toLongOrNull() ?: return null
        return texto.substringBefore(' ').takeIf { System.currentTimeMillis() - quando in 0..VALIDADE_MS }
    }
}
