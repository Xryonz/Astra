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
    private const val CHAVE_DA_LISTA = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Astra"
    private const val PUBLICADOR = "Xryonz"
    private const val CHAVE_DO_PROTOCOLO = "Software\\Classes\\astra"

    private interface Shell32 : StdCallLibrary {
        fun SetCurrentProcessExplicitAppUserModelID(appID: WString): Int
    }

    private fun noWindows() =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    fun aplicar() {
        if (!noWindows()) return
        Nativo.tentar("identidade na barra de tarefas") {
            Native.load("shell32", Shell32::class.java)
                .SetCurrentProcessExplicitAppUserModelID(WString(AUMID))
        }
    }

    fun registrarIdentidade() {
        if (!noWindows()) return
        thread(isDaemon = true, name = "astra-identidade") {
            runCatching { gravarNoRegistro() }
            runCatching { garantirAtalhoNoIniciar() }
            runCatching { entrarNaListaDeProgramas() }
            runCatching { registrarProtocolo() }
        }
    }

    fun esquecer() {
        if (!noWindows()) return
        for (chave in listOf(CHAVE, CHAVE_DA_LISTA, CHAVE_DO_PROTOCOLO)) runCatching { apagarComFilhas(chave) }
    }

    private fun apagarComFilhas(chave: String) {
        val raiz = WinReg.HKEY_CURRENT_USER
        if (!Advapi32Util.registryKeyExists(raiz, chave)) return
        Advapi32Util.registryGetKeys(raiz, chave).forEach { apagarComFilhas("$chave\\$it") }
        Advapi32Util.registryDeleteKey(raiz, chave)
    }

    private fun registrarProtocolo() {
        if (!Desinstalador.disponivel) return
        val exe = Instalacao.exe ?: return
        val raiz = WinReg.HKEY_CURRENT_USER
        val comando = "$CHAVE_DO_PROTOCOLO\\shell\\open\\command"
        val icone = "$CHAVE_DO_PROTOCOLO\\DefaultIcon"
        Advapi32Util.registryCreateKey(raiz, comando)
        Advapi32Util.registryCreateKey(raiz, icone)
        Advapi32Util.registrySetStringValue(raiz, CHAVE_DO_PROTOCOLO, "", "URL:$NOME_VISIVEL")
        Advapi32Util.registrySetStringValue(raiz, CHAVE_DO_PROTOCOLO, "URL Protocol", "")
        Advapi32Util.registrySetStringValue(raiz, icone, "", (Instalacao.icone() ?: exe).absolutePath)
        Advapi32Util.registrySetStringValue(raiz, comando, "", "\"${exe.absolutePath}\" \"%1\"")
    }

    private fun entrarNaListaDeProgramas() {
        if (!Desinstalador.disponivel) return
        val exe = Instalacao.exe ?: return
        val raiz = WinReg.HKEY_CURRENT_USER
        fun texto(nome: String, valor: String) = Advapi32Util.registrySetStringValue(raiz, CHAVE_DA_LISTA, nome, valor)
        Advapi32Util.registryCreateKey(raiz, CHAVE_DA_LISTA)
        texto("DisplayName", NOME_VISIVEL)
        texto("Publisher", PUBLICADOR)
        System.getProperty("astra.version")?.let { texto("DisplayVersion", it) }
        texto("DisplayIcon", (Instalacao.icone() ?: exe).absolutePath)
        texto("InstallLocation", (Instalacao.raiz ?: exe.parentFile).absolutePath)
        texto("UninstallString", "\"${exe.absolutePath}\" $ARG_DESINSTALAR")
        Advapi32Util.registrySetIntValue(raiz, CHAVE_DA_LISTA, "NoModify", 1)
        Advapi32Util.registrySetIntValue(raiz, CHAVE_DA_LISTA, "NoRepair", 1)
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
