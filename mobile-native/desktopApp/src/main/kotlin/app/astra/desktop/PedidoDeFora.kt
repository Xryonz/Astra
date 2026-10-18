package app.astra.desktop

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

object PedidoDeFora {
    const val DESINSTALAR = "desinstalar"

    private const val ARQUIVO = "pedido.txt"
    private const val VALIDADE_MS = 60_000L
    private const val PREFIXO_DO_CONVITE = "convite:"
    private val CODIGO_DE_CONVITE = Regex("^[A-Za-z0-9-]{1,64}$")
    private val LINK_DE_CONVITE = Regex("^astra://convite/([A-Za-z0-9-]{1,64})/?$", RegexOption.IGNORE_CASE)

    val convite = MutableStateFlow<String?>(null)

    private val arquivo: File get() = File(CrashLog.dataDir(), ARQUIVO)

    fun lerArgumentos(args: Array<String>) {
        if (args.any { it == ARG_DESINSTALAR }) {
            deixar(DESINSTALAR)
            return
        }
        args.firstNotNullOfOrNull { LINK_DE_CONVITE.matchEntire(it.trim())?.groupValues?.get(1) }
            ?.let { deixar(PREFIXO_DO_CONVITE + it) }
    }

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

    fun conviteDo(pedido: String): String? =
        pedido.takeIf { it.startsWith(PREFIXO_DO_CONVITE) }
            ?.removePrefix(PREFIXO_DO_CONVITE)
            ?.takeIf { CODIGO_DE_CONVITE.matches(it) }
}
