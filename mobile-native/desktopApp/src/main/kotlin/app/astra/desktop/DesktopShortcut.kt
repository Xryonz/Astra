package app.astra.desktop

import java.io.File
import kotlin.concurrent.thread

// Atalho na area de trabalho. A distribuicao do Astra e um app-image (zip
// descompactado, sem instalador), entao NAO ha etapa de "instalar" que crie o
// atalho — o proprio app garante um Astra.lnk no Desktop no 1o run (se faltar).
// APONTA PRO LAUNCHER (launch.vbs), não pro exe de uma versão: a instalacao e
// portatil (varias versões em versions\<v>\) e o launch.vbs sempre abre a MAIOR.
// Cravar o exe de uma versão travava o atalho nela — quando chegava versão nova, o
// atalho seguia abrindo a velha ("não leva pra mais atual"). So Windows; thread
// daemon; repara se o atalho existente estiver errado.
object DesktopShortcut {
    fun ensureWindows() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        thread(isDaemon = true, name = "astra-shortcut") {
            runCatching {
                val exe = currentExePath() ?: return@runCatching
                fun q(s: String) = s.replace("'", "''")
                val exeFile = File(exe)
                // exe = <raiz>\versions\<v>\Astra.exe -> raiz portatil (onde vive o
                // launch.vbs) = 3 pais acima. Se o launcher existir, o atalho aponta
                // pra ele; senao (layout inesperado) cai no exe atual, como antes.
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

                // A pasta e resolvida PELO POWERSHELL, não por user.home + "Desktop":
                // com o OneDrive ligado (padrao no Windows 11) a area de trabalho vira
                // %USERPROFILE%\OneDrive\Desktop e a pasta antiga nem existe — o palpite
                // falhava calado e o atalho nunca era criado. GetFolderPath('Desktop')
                // devolve o caminho real, redirecionado ou não.
                // WScript.Shell (COM) via PowerShell cria o .lnk — sem lib nativa extra.
                val ps = buildString {
                    append("\$d = [Environment]::GetFolderPath('Desktop'); ")
                    append("if (-not \$d) { exit }; ")
                    append("\$lnk = Join-Path \$d 'Astra.lnk'; ")
                    append("\$w = New-Object -ComObject WScript.Shell; ")
                    // REPARA em vez de desistir: so sai cedo se alvo E args já batem
                    // (inclui consertar um atalho antigo cravado no exe de uma versão).
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

    // jpackage seta "jpackage.app-path" com o caminho do launcher (Astra.exe). E a
    // UNICA fonte confiavel.
    //
    // O fallback pro comando do processo foi removido: num run de desenvolvimento
    // (./gradlew :desktopApp:run) ele devolve o java.exe do JDK, que passa num teste
    // de ".exe" e criava no Desktop um atalho apontando pro java — e, por causa do
    // antigo "se já existe, desiste", esse atalho quebrado sobrevivia a instalacao
    // seguinte. Sem app empacotado não ha atalho pra criar; entao não cria nenhum.
    private fun currentExePath(): String? =
        System.getProperty("jpackage.app-path")?.takeIf { it.endsWith(".exe", true) }
}
