package app.astra.desktop

import kotlin.concurrent.thread

object DesktopShortcut {
    fun ensureWindows() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        thread(isDaemon = true, name = "astra-shortcut") {
            runCatching {
                val alvo = Instalacao.alvoDoAtalho() ?: return@runCatching
                fun q(s: String) = s.replace("'", "''")
                val ps = buildString {
                    append("\$d = [Environment]::GetFolderPath('Desktop'); ")
                    append("if (-not \$d) { exit }; ")
                    append("\$lnk = Join-Path \$d 'Astra.lnk'; ")
                    append("\$w = New-Object -ComObject WScript.Shell; ")
                    append("if (Test-Path \$lnk) { \$c = \$w.CreateShortcut(\$lnk); ")
                    append("if (\$c.TargetPath -eq '${q(alvo.programa)}' -and \$c.Arguments -eq '${q(alvo.argumentos)}') { exit } }; ")
                    append("\$s = \$w.CreateShortcut(\$lnk); ")
                    append("\$s.TargetPath = '${q(alvo.programa)}'; ")
                    append("\$s.Arguments = '${q(alvo.argumentos)}'; ")
                    append("\$s.WorkingDirectory = '${q(alvo.pasta)}'; ")
                    append("\$s.IconLocation = '${q(alvo.simbolo)}'; ")
                    append("\$s.Save()")
                }
                ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", ps)
                    .redirectErrorStream(true)
                    .start()
            }
        }
    }
}
