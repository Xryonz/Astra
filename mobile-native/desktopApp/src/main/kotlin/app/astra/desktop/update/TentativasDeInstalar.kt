package app.astra.desktop.update

import app.astra.desktop.CrashLog
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object TentativasDeInstalar {

    private const val ARQUIVO = "tentativas-de-atualizar.txt"
    private const val LIMITE = 2
    private const val EM_CURSO = "em-curso"

    private data class Registro(val versao: String, val tentativas: Int, val emCurso: Boolean)

    private fun arquivo() = File(CrashLog.dataDir(), ARQUIVO)

    private fun ler(): Registro? = runCatching {
        val partes = arquivo().readText().trim().split(' ')
        Registro(partes[0], partes[1].toInt(), partes.getOrNull(2) == EM_CURSO)
    }.getOrNull()

    private fun gravar(registro: Registro): Boolean = runCatching {
        val texto = "${registro.versao} ${registro.tentativas}" + if (registro.emCurso) " $EM_CURSO" else ""
        val rascunho = File(CrashLog.dataDir(), "$ARQUIVO.tmp")
        rascunho.writeText(texto)
        Files.move(
            rascunho.toPath(),
            arquivo().toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
        arquivo().readText() == texto
    }.getOrDefault(false)

    fun registrarTentativa(versao: String): Boolean {
        val anterior = ler()
        if (anterior == null && arquivo().exists()) return false
        val tentativas = if (anterior?.versao == versao) anterior.tentativas + 1 else 1
        return gravar(Registro(versao, tentativas, emCurso = true))
    }

    fun aoAbrir(emUso: String): Boolean {
        val registro = ler() ?: return arquivo().exists()
        if (!registro.emCurso) return false
        if (!isNewer(registro.versao, emUso)) {
            esquecer()
            return false
        }
        gravar(registro.copy(emCurso = false))
        return true
    }

    fun desistiu(versao: String): Boolean {
        val registro = ler() ?: return arquivo().exists()
        return registro.versao == versao && registro.tentativas >= LIMITE
    }

    fun esquecer() {
        runCatching { arquivo().delete() }
    }
}
