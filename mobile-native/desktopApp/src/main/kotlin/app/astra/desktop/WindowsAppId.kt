package app.astra.desktop

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.win32.StdCallLibrary
import java.io.File
import kotlin.concurrent.thread

object WindowsAppId {
    const val AUMID = "Xryonz.Astra"

    private const val NOME_VISIVEL = "Astra"
    private const val CHAVE = "Software\\Classes\\AppUserModelId\\$AUMID"
    private const val ATALHO = "Microsoft\\Windows\\Start Menu\\Programs\\Astra.lnk"

    private interface Shell32 : StdCallLibrary {
        fun SetCurrentProcessExplicitAppUserModelID(appID: WString): Int
    }

    private fun noWindows() =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    fun aplicar() {
        if (!noWindows()) return
        runCatching {
            Native.load("shell32", Shell32::class.java)
                .SetCurrentProcessExplicitAppUserModelID(WString(AUMID))
        }
    }

    fun registrarIdentidade() {
        if (!noWindows()) return
        thread(isDaemon = true, name = "astra-identidade") {
            runCatching { gravarNoRegistro() }
            runCatching { garantirAtalhoNoIniciar() }
        }
    }

    private fun gravarNoRegistro() {
        val raiz = WinReg.HKEY_CURRENT_USER
        Advapi32Util.registryCreateKey(raiz, CHAVE)
        Advapi32Util.registrySetStringValue(raiz, CHAVE, "DisplayName", NOME_VISIVEL)
        icone()?.let { Advapi32Util.registrySetStringValue(raiz, CHAVE, "IconUri", it) }
    }

    private fun icone(): String? = Instalacao.icone()?.absolutePath

    private fun garantirAtalhoNoIniciar() {
        val menu = System.getenv("APPDATA")?.let { File(it, ATALHO) } ?: return
        val alvo = Instalacao.alvoDoAtalho() ?: return

        fun q(s: String) = s.replace("'", "''")
        val roteiro = buildString {
            append("\$p = '${q(menu.absolutePath)}'; ")
            append("\$w = New-Object -ComObject WScript.Shell; ")
            append("if (Test-Path \$p) { \$c = \$w.CreateShortcut(\$p); ")
            append("if (\$c.TargetPath -eq '${q(alvo.programa)}' -and \$c.Arguments -eq '${q(alvo.argumentos)}') { exit } }; ")
            append("New-Item -ItemType Directory -Force -Path (Split-Path \$p) | Out-Null; ")
            append("\$s = \$w.CreateShortcut(\$p); ")
            append("\$s.TargetPath = '${q(alvo.programa)}'; ")
            append("\$s.Arguments = '${q(alvo.argumentos)}'; ")
            append("\$s.WorkingDirectory = '${q(alvo.pasta)}'; ")
            append("\$s.IconLocation = '${q(alvo.simbolo)}'; ")
            append("\$s.Save()")
        }
        ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", roteiro)
            .redirectErrorStream(true)
            .start()
    }
}
