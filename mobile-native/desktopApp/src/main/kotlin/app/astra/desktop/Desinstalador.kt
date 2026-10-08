package app.astra.desktop

import app.astra.desktop.voice.SidecarDeVoz
import java.io.File

object Desinstalador {

    private const val EXECUTAVEL = "Astra.exe"
    private const val ATALHO = "Astra.lnk"
    private const val ATALHO_DO_INICIAR = "Microsoft\\Windows\\Start Menu\\Programs\\Astra.lnk"
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
            Instalacao.descartaveis().filter(::podeApagar).forEach { it.deleteRecursively() }
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

    private fun programa(): Pair<List<File>, List<File>> {
        val imagem = Instalacao.imagem ?: return emptyList<File>() to emptyList()
        val raiz = Instalacao.raiz
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
        if (!pareceImagemDoAstra(imagem)) return emptyList<File>() to emptyList()
        return listOf(imagem) to listOfNotNull(imagem.parentFile)
    }

    private fun pareceImagemDoAstra(pasta: File) =
        File(pasta, EXECUTAVEL).isFile && File(pasta, "app").isDirectory && File(pasta, "runtime").isDirectory

    private fun dados(): List<File> {
        val lista = ArrayList<File>()
        System.getenv("LOCALAPPDATA")?.let { lista += File(it, "Astra") }
        System.getenv("APPDATA")?.let { base ->
            lista += File(base, "Astra")
            File(base).listFiles().orEmpty().filterTo(lista) { it.isDirectory && PASTA_DE_TESTE.matches(it.name) }
        }
        return lista.filter { it.exists() }
    }

    private fun proibidas(): Set<String> {
        val home = System.getProperty("user.home")
        return listOfNotNull(
            home,
            System.getenv("APPDATA"),
            System.getenv("LOCALAPPDATA"),
            System.getenv("TEMP"),
            System.getenv("SystemRoot"),
            System.getenv("ProgramFiles"),
            System.getenv("ProgramFiles(x86)"),
            home?.let { "$it\\Desktop" },
            home?.let { "$it\\Downloads" },
            home?.let { "$it\\Documents" },
            home?.let { "$it\\OneDrive" },
        ).mapNotNull { runCatching { File(it).canonicalPath.lowercase() }.getOrNull() }.toSet()
    }

    private fun podeApagar(alvo: File): Boolean {
        val canonico = runCatching { alvo.canonicalFile }.getOrNull() ?: return false
        if (canonico.parentFile == null) return false
        return canonico.path.lowercase() !in proibidas()
    }

    private fun aspas(texto: String) = "'" + texto.replace("'", "''") + "'"

    private fun lista(arquivos: List<File>) =
        "@(" + arquivos.joinToString(", ") { aspas(it.absolutePath) } + ")"

    private fun escreverRoteiro(): File {
        val (doPrograma, pastasQueSaemSeVazias) = programa()
        val alvos = (doPrograma + Instalacao.descartaveis() + dados()).distinct().filter(::podeApagar)
        val vazias = pastasQueSaemSeVazias
            .filter { it.name.startsWith("Astra", ignoreCase = true) && podeApagar(it) }
        val pastasDoPrograma = listOfNotNull(Instalacao.raiz ?: Instalacao.imagem)
        val atalhos = listOfNotNull(System.getenv("APPDATA")?.let { File(it, ATALHO_DO_INICIAR) })
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
