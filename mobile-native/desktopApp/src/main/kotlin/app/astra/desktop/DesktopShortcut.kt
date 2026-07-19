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
                fun q(s: String) = s.replace("'", "''")
                val workDir = File(exe).parent ?: return@runCatching
                // A pasta e resolvida PELO POWERSHELL, nao por user.home + "Desktop":
                // com o OneDrive ligado (padrao no Windows 11) a area de trabalho vira
                // %USERPROFILE%\OneDrive\Desktop e a pasta antiga nem existe — o palpite
                // falhava calado e o atalho nunca era criado. GetFolderPath('Desktop')
                // devolve o caminho real, redirecionado ou nao.
                // WScript.Shell (COM) via PowerShell cria o .lnk — sem lib nativa extra.
                val ps = buildString {
                    append("\$d = [Environment]::GetFolderPath('Desktop'); ")
                    append("if (-not \$d) { exit }; ")
                    append("\$lnk = Join-Path \$d 'Astra.lnk'; ")
                    append("\$w = New-Object -ComObject WScript.Shell; ")
                    // REPARA em vez de desistir: antes era `if (Test-Path) { exit }`,
                    // entao um atalho errado (ou de uma instalacao antiga, noutra
                    // pasta) ficava pra sempre — a versao nova via "ja existe" e nao
                    // corrigia nada. Agora so sai cedo se o alvo ja for este exe.
                    append("if (Test-Path \$lnk) { ")
                    append("if (\$w.CreateShortcut(\$lnk).TargetPath -eq '${q(exe)}') { exit } ")
                    append("}; ")
                    append("\$s = \$w.CreateShortcut(\$lnk); ")
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
    // UNICA fonte confiavel.
    //
    // O fallback pro comando do processo foi removido: num run de desenvolvimento
    // (./gradlew :desktopApp:run) ele devolve o java.exe do JDK, que passa num teste
    // de ".exe" e criava no Desktop um atalho apontando pro java — e, por causa do
    // antigo "se ja existe, desiste", esse atalho quebrado sobrevivia a instalacao
    // seguinte. Sem app empacotado nao ha atalho pra criar; entao nao cria nenhum.
    private fun currentExePath(): String? =
        System.getProperty("jpackage.app-path")?.takeIf { it.endsWith(".exe", true) }
}
