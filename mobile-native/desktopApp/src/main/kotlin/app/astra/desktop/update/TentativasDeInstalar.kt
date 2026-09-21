package app.astra.desktop.update

import app.astra.desktop.ARG_POS_ATUALIZACAO
import app.astra.desktop.ARG_TROCA_FALHOU
import app.astra.desktop.CrashLog
import java.io.File

internal object TentativasDeInstalar {

    private const val ARQUIVO = "tentativas-de-atualizar.txt"
    private const val LIMITE = 2

    private fun arquivo() = File(CrashLog.dataDir(), ARQUIVO)

    private fun ler(): Pair<String, Int>? = runCatching {
        val (versao, falhas) = arquivo().readText().trim().split(' ')
        versao to falhas.toInt()
    }.getOrNull()

    fun trocaFalhou(args: Array<String>, emUso: String): Boolean {
        val versao = valorDe(args, ARG_TROCA_FALHOU)
            ?: valorDe(args, ARG_POS_ATUALIZACAO)?.takeIf { isNewer(it, emUso) }
            ?: return false
        registrarFalha(versao)
        return true
    }

    private fun valorDe(args: Array<String>, chave: String): String? =
        args.firstOrNull { it.startsWith("$chave=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }

    private fun registrarFalha(versao: String) {
        val anterior = ler()
        val falhas = if (anterior?.first == versao) anterior.second + 1 else 1
        runCatching { arquivo().writeText("$versao $falhas") }
    }

    fun desistiu(versao: String): Boolean {
        val anterior = ler() ?: return false
        return anterior.first == versao && anterior.second >= LIMITE
    }

    fun esquecer() {
        runCatching { arquivo().delete() }
    }
}
