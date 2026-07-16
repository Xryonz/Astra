package app.astra.desktop.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

// Auto-update DIY (zip-swap) do Astra desktop. Sem lib: bate na API publica de
// releases do GitHub, compara semver com a versao embutida (-Dastra.version),
// baixa o .zip do app-image novo com progresso, descompacta num staging ao lado
// e — no "reiniciar" — solta um .bat que espera o app fechar, troca a pasta
// (rename quase-atomico e reversivel) e reabre. So java.base (HttpURLConnection),
// pra nao depender de modulo jlink extra no app empacotado.
//
// Convencao de release (o dono segue ao publicar):
//   tag  : desktop-v<versao>        ex: desktop-v0.2.0
//   asset: Astra-<versao>-win-x64.zip  (contem a pasta Astra/ do createDistributable)

private const val RELEASES_URL = "https://api.github.com/repos/Xryonz/Astra/releases/latest"

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(
        val version: String,
        val notes: String,
        val downloadUrl: String,
        val size: Long,
        val releaseUrl: String,
    ) : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Ready(val version: String) : UpdateState
    data class Failed(val reason: String, val releaseUrl: String?) : UpdateState
}

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0,
)

private data class Staged(val appRoot: File, val newRoot: File, val stagingDir: File)

class UpdateService(private val json: Json) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()

    // Versao embutida no app-image (jvmArg do build.gradle). "dev" quando rodando
    // pelo Gradle/IDE — nesse caso o updater fica desligado (appRoot nao resolve).
    val currentVersion: String get() = System.getProperty("astra.version") ?: "dev"

    // So o app empacotado (Astra.exe) tem pasta pra trocar. Dev/IDE = nulo.
    val installed: Boolean get() = appRootDir() != null

    private var staged: Staged? = null

    // ---- Checagem ----

    suspend fun check(silent: Boolean) = withContext(Dispatchers.IO) {
        if (!installed) { _state.value = UpdateState.Idle; return@withContext }
        _state.value = UpdateState.Checking
        val release = runCatching { fetchLatest() }.getOrElse {
            _state.value = if (silent) UpdateState.Idle else UpdateState.Failed("sem conexao com o GitHub", null)
            return@withContext
        }
        // Sem releases publicadas ainda (404) = nada mais novo que agora.
        if (release == null) { _state.value = UpdateState.UpToDate; return@withContext }
        val latest = release.tagName.removePrefix("desktop-").removePrefix("v").trim()
        val asset = release.assets.firstOrNull { it.name.endsWith(".zip", true) && it.name.contains("win", true) }
            ?: release.assets.firstOrNull { it.name.endsWith(".zip", true) }
        if (asset == null || latest.isBlank() || !isNewer(latest, currentVersion)) {
            _state.value = UpdateState.UpToDate
            return@withContext
        }
        _state.value = UpdateState.Available(
            version = latest,
            notes = release.body?.trim().orEmpty(),
            downloadUrl = asset.browserDownloadUrl,
            size = asset.size,
            releaseUrl = release.htmlUrl,
        )
    }

    private fun fetchLatest(): GhRelease? {
        val conn = URI.create(RELEASES_URL).toURL().openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Astra-Desktop")
        // Curto: e a checagem do boot (gate). Offline -> falha rapido e o app segue.
        conn.connectTimeout = 6_000
        conn.readTimeout = 8_000
        if (conn.responseCode == 404) return null // repo sem releases ainda
        if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        return json.decodeFromString<GhRelease>(body)
    }

    // semver simples: a > b por campo (major.minor.patch). Campos nao-numericos = 0.
    private fun isNewer(a: String, b: String): Boolean {
        val pa = a.split(".", "-").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".", "-").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // ---- Download + staging ----

    suspend fun downloadAndStage(av: UpdateState.Available) = withContext(Dispatchers.IO) {
        val appRoot = appRootDir() ?: run {
            _state.value = UpdateState.Failed("nao achei a pasta do app", av.releaseUrl)
            return@withContext
        }
        val installDir = appRoot.parentFile
        val stagingDir = File(installDir, "Astra.new")
        val zipFile = File(installDir, "Astra-update.zip")
        runCatching {
            _state.value = UpdateState.Downloading(av.version, 0f)
            stagingDir.deleteRecursively()
            zipFile.delete()
            download(av.downloadUrl, zipFile, av.size) { p ->
                _state.value = UpdateState.Downloading(av.version, p)
            }
            unzip(zipFile, stagingDir)
            zipFile.delete()
            val newRoot =
                if (File(stagingDir, "Astra.exe").exists()) stagingDir
                else stagingDir.listFiles()?.firstOrNull { File(it, "Astra.exe").exists() }
                    ?: error("Astra.exe nao encontrado no pacote")
            staged = Staged(appRoot, newRoot, stagingDir)
            _state.value = UpdateState.Ready(av.version)
        }.onFailure {
            stagingDir.deleteRecursively()
            zipFile.delete()
            _state.value = UpdateState.Failed("falha ao baixar — tente pelo site", av.releaseUrl)
        }
    }

    private fun download(url: String, dest: File, expected: Long, onProgress: (Float) -> Unit) {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Astra-Desktop")
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true // asset do GitHub -> 302 pro storage
        val total = if (expected > 0) expected else conn.contentLengthLong
        conn.inputStream.use { input ->
            dest.outputStream().buffered().use { out ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        onProgress(1f)
    }

    private fun unzip(zip: File, destDir: File) {
        destDir.mkdirs()
        val root = destDir.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val out = File(destDir, e.name)
                // zip-slip: nunca escreve fora do staging.
                if (out.canonicalPath.startsWith(root)) {
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().buffered().use { zin.copyTo(it) }
                    }
                }
                e = zin.nextEntry
            }
        }
    }

    // ---- Troca + reinicio ----

    // Solta o .bat que espera este processo morrer, troca a pasta e reabre. O .bat
    // sobrevive ao nosso exit (Windows nao mata filho junto do pai).
    fun restartToInstall() {
        val s = staged ?: return
        val bat = writeSwapScript(s)
        ProcessBuilder("cmd.exe", "/c", bat.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        exitProcess(0)
    }

    private fun writeSwapScript(s: Staged): File {
        val pid = ProcessHandle.current().pid()
        val appRoot = s.appRoot.absolutePath
        val newRoot = s.newRoot.absolutePath
        val staging = s.stagingDir.absolutePath
        val exe = File(s.appRoot, "Astra.exe").absolutePath
        // Espera o PID sair -> renomeia Astra->Astra.old, novo->Astra, reabre. Se a
        // troca falhar (sem permissao/pasta protegida), rollback do .old e reabre.
        val script = buildString {
            appendLine("@echo off")
            appendLine("setlocal")
            appendLine("set \"PID=$pid\"")
            appendLine(":wait")
            appendLine("tasklist /FI \"PID eq %PID%\" 2>nul | find \"%PID%\" >nul")
            appendLine("if not errorlevel 1 (")
            appendLine("  timeout /t 1 /nobreak >nul")
            appendLine("  goto wait")
            appendLine(")")
            appendLine("if exist \"$appRoot.old\" rmdir /S /Q \"$appRoot.old\"")
            appendLine("move \"$appRoot\" \"$appRoot.old\" >nul 2>&1")
            appendLine("move \"$newRoot\" \"$appRoot\" >nul 2>&1")
            appendLine("if not exist \"$exe\" (")
            appendLine("  if exist \"$appRoot.old\" move \"$appRoot.old\" \"$appRoot\" >nul 2>&1")
            appendLine(") else (")
            appendLine("  if exist \"$appRoot.old\" rmdir /S /Q \"$appRoot.old\"")
            appendLine("  if exist \"$staging\" rmdir /S /Q \"$staging\"")
            appendLine(")")
            appendLine("start \"\" \"$exe\"")
            appendLine("del \"%~f0\"")
        }
        val bat = File(System.getProperty("java.io.tmpdir"), "astra-update.bat")
        bat.writeText(script.replace("\n", "\r\n"))
        return bat
    }

    // Astra.exe do jpackage: jpackage.app-path aponta pro launcher; a pasta dele e
    // o app-image (Astra.exe + app/ + runtime/). Fallback: comando do processo.
    private fun appRootDir(): File? {
        System.getProperty("jpackage.app-path")?.let { p ->
            val exe = File(p)
            if (exe.exists() && exe.name.equals("Astra.exe", true)) return exe.parentFile
        }
        val cmd = ProcessHandle.current().info().command().orElse(null) ?: return null
        val exe = File(cmd)
        return if (exe.name.equals("Astra.exe", true)) exe.parentFile else null
    }
}
