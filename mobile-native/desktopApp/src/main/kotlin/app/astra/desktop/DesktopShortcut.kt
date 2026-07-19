package app.astra.desktop

import java.io.File
import kotlin.concurrent.thread

// Atalho na area de trabalho. A distribuicao do Astra e um app-image (zip
// descompactado, sem instalador), entao NAO ha etapa de "instalar" que crie o
// atalho — o proprio app garante um Astra.lnk no Desktop no 1o run (se faltar),
// apontando pro Astra.exe atual. So Windows; roda numa thread daemon (nao trava o
// boot) e e no-op se o atalho ja existe.
object DesktopShortcut {
    fun ensureWindows() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        thread(isDaemon = true, name = "astra-shortcut") {
            runCatching {
                val exe = currentExePath() ?: return@runCatching
                val desktop = File(System.getProperty("user.home"), "Desktop")
                if (!desktop.isDirectory) return@runCatching
                val lnk = File(desktop, "Astra.lnk")
                if (lnk.exists()) return@runCatching

                fun q(s: String) = s.replace("'", "''")
                val workDir = File(exe).parent ?: return@runCatching
                // WScript.Shell (COM) via PowerShell cria o .lnk — sem lib nativa extra.
                val ps = buildString {
                    append("\$s = (New-Object -ComObject WScript.Shell).CreateShortcut('${q(lnk.absolutePath)}'); ")
                    append("\$s.TargetPath = '${q(exe)}'; ")
                    append("\$s.WorkingDirectory = '${q(workDir)}'; ")
                    append("\$s.IconLocation = '${q(exe)}'; ")
                    append("\$s.Save()")
                }
                ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", ps)
                    .redirectErrorStream(true)
                    .start()
            }
        }
    }

    // jpackage seta "jpackage.app-path" com o caminho do launcher (Astra.exe). E a
    // fonte confiavel; o fallback (comando do processo) apontaria pro java do runtime.
    private fun currentExePath(): String? =
        System.getProperty("jpackage.app-path")
            ?: ProcessHandle.current().info().command().orElse(null)?.takeIf { it.endsWith(".exe", true) }
}
