package app.astra.desktop

import java.io.File
import kotlin.concurrent.thread

object DesktopShortcut {
    fun ensureWindows() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        thread(isDaemon = true, name = "astra-shortcut") {
            runCatching {
                val exe = currentExePath() ?: return@runCatching
                fun q(s: String) = s.replace("'", "''")
                val exeFile = File(exe)
                val portableRoot = exeFile.parentFile?.parentFile?.parentFile
                val launchVbs = portableRoot?.let { File(it, "launch.vbs") }?.takeIf { it.isFile }

                val target: String
                val args: String
                val workDir: String
                val icon: String
                if (launchVbs != null && portableRoot != null) {
                    val winDir = System.getenv("SystemRoot") ?: "C:\\Windows"
                    target = "$winDir\\System32\\wscript.exe"
                    args = "\"" + launchVbs.absolutePath + "\""
                    workDir = portableRoot.absolutePath
                    icon = File(portableRoot, "astra.ico").takeIf { it.isFile }?.absolutePath ?: exe
                } else {
                    target = exe
                    args = ""
                    workDir = exeFile.parent ?: return@runCatching
                    icon = exe
                }

                val ps = buildString {
                    append("\$d = [Environment]::GetFolderPath('Desktop'); ")
                    append("if (-not \$d) { exit }; ")
                    append("\$lnk = Join-Path \$d 'Astra.lnk'; ")
                    append("\$w = New-Object -ComObject WScript.Shell; ")
                    append("if (Test-Path \$lnk) { \$c = \$w.CreateShortcut(\$lnk); ")
                    append("if (\$c.TargetPath -eq '${q(target)}' -and \$c.Arguments -eq '${q(args)}') { exit } }; ")
                    append("\$s = \$w.CreateShortcut(\$lnk); ")
                    append("\$s.TargetPath = '${q(target)}'; ")
                    append("\$s.Arguments = '${q(args)}'; ")
                    append("\$s.WorkingDirectory = '${q(workDir)}'; ")
                    append("\$s.IconLocation = '${q(icon)}'; ")
                    append("\$s.Save()")
                }
                ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", ps)
                    .redirectErrorStream(true)
                    .start()
            }
        }
    }

    private fun currentExePath(): String? =
        System.getProperty("jpackage.app-path")?.takeIf { it.endsWith(".exe", true) }
}
