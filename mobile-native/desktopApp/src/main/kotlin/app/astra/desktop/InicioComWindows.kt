package app.astra.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

object InicioComWindows {

    private const val CHAVE = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NOME = "Astra"

    fun disponivel(): Boolean = caminhoDoExe() != null

    fun ligado(): Boolean = runCatching {
        Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, CHAVE, NOME)
    }.getOrDefault(false)

    fun escondido(): Boolean = runCatching {
        Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, CHAVE, NOME)
            ?.contains(ARG_MINIMIZADO) == true
    }.getOrDefault(false)

    fun aplicar(ligar: Boolean, escondido: Boolean): Boolean {
        val exe = caminhoDoExe() ?: return false
        return runCatching {
            if (ligar) {
                val comando = buildString {
                    append('"').append(exe).append('"')
                    if (escondido) append(' ').append(ARG_MINIMIZADO)
                }
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, CHAVE, NOME, comando)
            } else {
                if (ligado()) Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, CHAVE, NOME)
            }
            true
        }.getOrDefault(false)
    }

    private fun caminhoDoExe(): String? =
        System.getProperty("jpackage.app-path")?.takeIf { it.endsWith(".exe", true) }
}
