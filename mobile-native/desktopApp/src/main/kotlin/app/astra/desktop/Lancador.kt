package app.astra.desktop

import java.io.File

object Lancador {

    private const val ARQUIVO = "launch.vbs"

    fun manterAtualizado(): Boolean {
        val raiz = Instalacao.raiz ?: return false
        val embutido = runCatching {
            Lancador::class.java.getResourceAsStream("/$ARQUIVO")?.readBytes()
        }.getOrNull() ?: return false
        val noDisco = File(raiz, ARQUIVO)
        if (noDisco.isFile && noDisco.readBytes().contentEquals(embutido)) return false
        return runCatching { noDisco.writeBytes(embutido); true }.getOrDefault(false)
    }
}
