package app.astra.desktop.update

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

    fun registrarFalha(versao: String) {
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
