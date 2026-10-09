package app.astra.desktop

import app.astra.desktop.voice.SidecarDeVoz
import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import com.sun.jna.platform.win32.ShlObj
import java.io.File

object Desinstalador {

    private const val EXECUTAVEL = "Astra.exe"
    private const val ATALHO = "Astra.lnk"
    private const val ATALHO_DO_INICIAR = "Microsoft\\Windows\\Start Menu\\Programs\\Astra.lnk"
    private const val ATALHO_DA_BARRA = "Microsoft\\Internet Explorer\\Quick Launch\\User Pinned\\TaskBar\\Astra.lnk"
    private val NOME_DE_VERSAO = Regex("""^\d+\.\d+\.\d+$""")
    private val PASTA_DE_TESTE = Regex("""^Astra-teste\d+$""")

    class Etapa(val rotulo: String, val trabalho: () -> Unit)

    val disponivel: Boolean
        get() = noWindows() && Instalacao.exe != null && Multi.slot == null

    fun etapas(): List<Etapa> = listOf(
        Etapa("encerrando a voz") { SidecarDeVoz.encerrarTodos(prazoMs = 3_000L) },
        Etapa("removendo o arranque com o Windows") {
            InicioComWindows.aplicar(ligar = false, escondido = false)
        },
        Etapa("removendo a identidade no Windows") { WindowsAppId.esquecer() },
        Etapa("apagando versões guardadas e downloads") {
            descartaveis().filter(::podeApagar).forEach { it.deleteRecursively() }
        },
    )

    fun concluir(): Boolean = runCatching {
        val roteiro = escreverRoteiro()
        ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-WindowStyle", "Hidden", "-File", roteiro.absolutePath,
        )
            .directory(roteiro.parentFile)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        true
    }.getOrDefault(false)

    private fun raizConfirmada(): File? {
        val raiz = Instalacao.raiz ?: return null
        val imagem = Instalacao.imagem ?: return null
        val dentro = Instalacao.mesmaPasta(imagem, File(raiz, Instalacao.PASTA_FIXA)) ||
            Instalacao.mesmaPasta(imagem.parentFile, File(raiz, "versions")) ||
            (Instalacao.mesmaPasta(imagem.parentFile, raiz) && NOME_DE_VERSAO.matches(imagem.name))
        return raiz.takeIf { dentro }
    }

    private fun descartaveis(): List<File> =
        if (Instalacao.raiz != null && raizConfirmada() == null) emptyList() else Instalacao.descartaveis()

    private fun programa(): Pair<List<File>, List<File>> {
        val imagem = Instalacao.imagem ?: return emptyList<File>() to emptyList()
        if (!pareceImagemDoAstra(imagem)) return emptyList<File>() to emptyList()
        val raiz = raizConfirmada()
        if (raiz != null) {
            val itens = raiz.listFiles().orEmpty().filter { item ->
                val nome = item.name
                nome.equals(Instalacao.PASTA_FIXA, ignoreCase = true) ||
                    nome.startsWith(Instalacao.PASTA_FIXA + Instalacao.SUFIXO_DA_RESERVA, ignoreCase = true) ||
                    nome.equals(Instalacao.PASTA_FIXA + Instalacao.SUFIXO_DA_COPIA, ignoreCase = true) ||
                    nome.equals("versions", ignoreCase = true) ||
                    nome.equals("zips", ignoreCase = true) ||
                    nome.startsWith("launch.vbs", ignoreCase = true) ||
                    nome.equals("astra.ico", ignoreCase = true) ||
                    nome.equals(ATALHO, ignoreCase = true) ||
                    (item.isDirectory && NOME_DE_VERSAO.matches(nome))
            }
            return itens to listOf(raiz)
        }
        val itens = listOf(File(imagem, EXECUTAVEL), File(imagem, "app"), File(imagem, "runtime"))
        return itens to listOfNotNull(imagem, imagem.parentFile)
    }

    private fun pareceImagemDoAstra(pasta: File) =
        File(pasta, EXECUTAVEL).isFile && File(pasta, "app\\Astra.cfg").isFile && File(pasta, "runtime").isDirectory

    private fun dados(): List<File> {
        val lista = ArrayList<File>()
        System.getenv("LOCALAPPDATA")?.let { lista += File(it, "Astra") }
        System.getenv("APPDATA")?.let { base ->
            lista += File(base, "Astra")
            File(base).listFiles().orEmpty().filterTo(lista) { it.isDirectory && PASTA_DE_TESTE.matches(it.name) }
        }
        return lista.filter { it.exists() }
    }

    private val proibidas: Set<String> by lazy {
        val home = System.getProperty("user.home")
        val doWindows = listOf(
            ShlObj.CSIDL_PROFILE, ShlObj.CSIDL_DESKTOPDIRECTORY, ShlObj.CSIDL_PERSONAL, ShlObj.CSIDL_MYPICTURES,
            ShlObj.CSIDL_MYMUSIC, ShlObj.CSIDL_MYVIDEO, ShlObj.CSIDL_APPDATA, ShlObj.CSIDL_LOCAL_APPDATA,
            ShlObj.CSIDL_WINDOWS, ShlObj.CSIDL_PROGRAM_FILES,
        ).mapNotNull { runCatching { Shell32Util.getFolderPath(it) }.getOrNull() } +
            listOfNotNull(runCatching { Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Downloads) }.getOrNull())
        (
            doWindows + listOfNotNull(
                home,
                System.getenv("APPDATA"),
                System.getenv("LOCALAPPDATA"),
                System.getenv("TEMP"),
                System.getenv("SystemRoot"),
                System.getenv("ProgramFiles"),
                System.getenv("ProgramFiles(x86)"),
                System.getenv("OneDrive"),
                home?.let { "$it\\Desktop" },
                home?.let { "$it\\Downloads" },
                home?.let { "$it\\Documents" },
                home?.let { "$it\\OneDrive" },
            )
        ).mapNotNull { runCatching { File(it).canonicalPath.lowercase() }.getOrNull() }.toSet()
    }

    private fun podeApagar(alvo: File): Boolean {
        val canonico = runCatching { alvo.canonicalFile }.getOrNull() ?: return false
        if (canonico.parentFile == null) return false
        return canonico.path.lowercase() !in proibidas
    }

    private fun aspas(texto: String) = "'" + texto.replace("'", "''") + "'"

    private fun lista(arquivos: List<File>) =
        "@(" + arquivos.joinToString(", ") { aspas(it.absolutePath) } + ")"

    private fun escreverRoteiro(): File {
        val (doPrograma, pastasQueSaemSeVazias) = programa()
        val alvos = (doPrograma + descartaveis() + dados()).distinct().filter(::podeApagar)
        val vazias = pastasQueSaemSeVazias
            .filter { it.name.startsWith("Astra", ignoreCase = true) && podeApagar(it) }
        val pastasDoPrograma = listOfNotNull(raizConfirmada() ?: Instalacao.imagem)
        val atalhos = System.getenv("APPDATA")?.let { base ->
            listOf(File(base, ATALHO_DO_INICIAR), File(base, ATALHO_DA_BARRA))
        }.orEmpty()
        val pid = ProcessHandle.current().pid()
        val texto = buildString {
            appendLine("\$ErrorActionPreference = 'SilentlyContinue'")
            appendLine("try { Wait-Process -Id $pid -Timeout 60 } catch {}")
            appendLine("Start-Sleep -Milliseconds 500")
            appendLine("\$pastas = ${lista(pastasDoPrograma)}")
            appendLine("\$doPrograma = ${lista(alvos.filter { it.isDirectory })}")
            appendLine("Get-CimInstance Win32_Process | ForEach-Object {")
            appendLine("  \$c = \$_.ExecutablePath")
            appendLine("  if (\$c) { foreach (\$p in \$doPrograma) { if (\$c.StartsWith(\$p + '\\', [StringComparison]::OrdinalIgnoreCase)) { Stop-Process -Id \$_.ProcessId -Force } } }")
            appendLine("}")
            appendLine("\$alvos = ${lista(alvos)}")
            appendLine("for (\$i = 0; \$i -lt 20; \$i++) {")
            appendLine("  \$restam = @(\$alvos | Where-Object { Test-Path -LiteralPath \$_ })")
            appendLine("  if (\$restam.Count -eq 0) { break }")
            appendLine("  foreach (\$a in \$restam) { Remove-Item -LiteralPath \$a -Recurse -Force }")
            appendLine("  Start-Sleep -Milliseconds 500")
            appendLine("}")
            appendLine("foreach (\$v in ${lista(vazias)}) {")
            appendLine("  if ((Test-Path -LiteralPath \$v) -and -not (Get-ChildItem -LiteralPath \$v -Force | Select-Object -First 1)) { Remove-Item -LiteralPath \$v -Force }")
            appendLine("}")
            appendLine("\$w = New-Object -ComObject WScript.Shell")
            appendLine("\$atalhos = ${lista(atalhos)}")
            appendLine("\$mesa = [Environment]::GetFolderPath('Desktop')")
            appendLine("if (\$mesa) { \$atalhos += (Join-Path \$mesa '$ATALHO') }")
            appendLine("foreach (\$l in \$atalhos) {")
            appendLine("  if (Test-Path -LiteralPath \$l) {")
            appendLine("    \$s = \$w.CreateShortcut(\$l)")
            appendLine("    \$destino = \$s.TargetPath + ' ' + \$s.Arguments")
            appendLine("    foreach (\$p in \$pastas) { if (\$destino.IndexOf(\$p, [StringComparison]::OrdinalIgnoreCase) -ge 0) { Remove-Item -LiteralPath \$l -Force; break } }")
            appendLine("  }")
            appendLine("}")
            appendLine("Remove-Item -LiteralPath \$PSCommandPath -Force")
        }
        val pasta = File(System.getProperty("java.io.tmpdir") ?: ".")
        val roteiro = File(pasta, "astra-desinstalar-$pid.ps1")
        roteiro.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + texto.toByteArray(Charsets.UTF_8))
        return roteiro
    }

    private fun noWindows(): Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
}
