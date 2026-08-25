package app.astra.desktop.update

import app.astra.desktop.ARG_POS_ATUALIZACAO
import app.astra.desktop.FocoDoSistema
import app.astra.desktop.Multi
import app.astra.desktop.SingleInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.net.UnknownHostException
import java.time.Duration
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

private const val REPO = "Xryonz/Astra"
private const val LATEST_PAGE = "https://github.com/$REPO/releases/latest"

private const val INTERVALO_RONDA_MS = 20L * 60_000L

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState

    data class UpToDate(val vista: String, val conferidoEm: Long = System.currentTimeMillis()) : UpdateState
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

private data class ResolvedRelease(
    val version: String,
    val downloadUrl: String,
    val releaseUrl: String,
)

class UpdateService(private val http: OkHttpClient) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()

    val currentVersion: String get() = System.getProperty("astra.version") ?: "dev"

    val installed: Boolean get() = !Multi.ligado && appRootDir() != null

    private var stagedExe: File? = null

    private var ronda: Job? = null

    fun iniciarRonda(scope: CoroutineScope) {
        if (!installed || ronda?.isActive == true) return
        ronda = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(INTERVALO_RONDA_MS)
                val agora = _state.value
                val ocupado = agora is UpdateState.Downloading ||
                    agora is UpdateState.Ready ||
                    agora is UpdateState.Available
                if (!ocupado) check(mostrarFalha = false)
            }
        }
    }

    private val ESPERA_FAXINA_MS = 20_000L

    fun agendarFaxina(scope: CoroutineScope) {
        if (!installed) return
        scope.launch(Dispatchers.IO) {
            delay(ESPERA_FAXINA_MS)
            runCatching { limparVersoesAntigas() }
        }
    }

    fun limparVersoesAntigas(): Long {
        val atual = appRootDir() ?: return 0L
        val versionsDir = atual.parentFile ?: return 0L
        val raiz = versionsDir.parentFile ?: return 0L
        val caminhoAtual = runCatching { atual.canonicalPath }.getOrNull() ?: return 0L
        var liberado = 0L

        fun apagar(alvo: File) {
            if (runCatching { alvo.canonicalPath }.getOrNull() == caminhoAtual) return
            val tamanho = alvo.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            if (alvo.deleteRecursively()) liberado += tamanho
        }

        versionsDir.listFiles()?.forEach { if (it.isDirectory) apagar(it) }

        File(raiz, "zips").listFiles()?.forEach { if (it.isFile) apagar(it) }

        val pastaDeVersao = Regex("""^\d+\.\d+\.\d+$""")
        raiz.listFiles()?.forEach { f ->
            if (f.isDirectory && pastaDeVersao.matches(f.name)) apagar(f)
        }
        return liberado
    }

    suspend fun check(mostrarFalha: Boolean = true) = withContext(Dispatchers.IO) {
        if (!installed) { _state.value = UpdateState.Idle; return@withContext }
        val antes = _state.value
        _state.value = UpdateState.Checking

        fun falhou(motivo: String) {
            _state.value = if (mostrarFalha) UpdateState.Failed(motivo, LATEST_PAGE) else antes
        }

        val release = try {
            fetchLatest()
        } catch (e: Exception) {
            falhou(failureReason(e))
            return@withContext
        }
        if (release == null) {
            falhou("não consegui ler a versão publicada")
            return@withContext
        }
        if (!isNewer(release.version, currentVersion)) {
            _state.value = UpdateState.UpToDate(vista = release.version)
            return@withContext
        }
        _state.value = UpdateState.Available(
            version = release.version,
            notes = "",
            downloadUrl = release.downloadUrl,
            size = 0L,
            releaseUrl = release.releaseUrl,
        )
    }

    private fun fetchLatest(): ResolvedRelease? {
        val noRedirect = http.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val req = Request.Builder()
            .url(LATEST_PAGE)
            .header("User-Agent", "Astra-Desktop")
            .build()
        val location = noRedirect.newCall(req).execute().use { it.header("Location") }
            ?: return null
        val tag = location.substringAfter("/releases/tag/", "").substringBefore('?').trim('/')
        if (tag.isBlank()) return null
        val version = tag.removePrefix("desktop-").removePrefix("v").trim()
        if (version.isBlank()) return null
        return ResolvedRelease(
            version = version,
            downloadUrl = "https://github.com/$REPO/releases/download/$tag/Astra-$version-win-x64.zip",
            releaseUrl = "https://github.com/$REPO/releases/tag/$tag",
        )
    }

    private fun failureReason(e: Throwable): String = when (e) {
        is UnknownHostException -> "sem internet"
        is IOException -> "sem conexão com o GitHub"
        else -> "não foi possível verificar agora"
    }

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

    suspend fun downloadAndStage(av: UpdateState.Available) = withContext(Dispatchers.IO) {
        val appRoot = appRootDir() ?: run {
            _state.value = UpdateState.Failed("não achei a pasta do app", av.releaseUrl)
            return@withContext
        }
        val versionsDir = appRoot.parentFile ?: run {
            _state.value = UpdateState.Failed("layout do app inesperado", av.releaseUrl)
            return@withContext
        }
        val portableRoot = versionsDir.parentFile ?: versionsDir
        val zipsDir = File(portableRoot, "zips").apply { mkdirs() }
        val zipFile = File(zipsDir, "Astra-${av.version}-win-x64.zip")
        val stagingDir = File(versionsDir, ".staging-${av.version}")
        val newVersionDir = File(versionsDir, av.version)
        runCatching {
            _state.value = UpdateState.Downloading(av.version, 0f)
            stagingDir.deleteRecursively()
            zipFile.delete()
            download(av.downloadUrl, zipFile) { p ->
                _state.value = UpdateState.Downloading(av.version, p)
            }
            conferirHash(av.downloadUrl, zipFile)
            unzip(zipFile, stagingDir)
            val exeRoot =
                if (File(stagingDir, "Astra.exe").exists()) stagingDir
                else stagingDir.listFiles()?.firstOrNull { File(it, "Astra.exe").exists() }
                    ?: error("Astra.exe não encontrado no pacote")
            newVersionDir.deleteRecursively()
            if (!exeRoot.renameTo(newVersionDir)) {
                exeRoot.copyRecursively(newVersionDir, overwrite = true)
            }
            stagingDir.deleteRecursively()
            if (!File(newVersionDir, "app").isDirectory || !File(newVersionDir, "runtime").isDirectory) {
                error("pacote incompleto")
            }
            stagedExe = File(newVersionDir, "Astra.exe")
            _state.value = UpdateState.Ready(av.version)
        }.onFailure {
            stagingDir.deleteRecursively()
            zipFile.delete()
            newVersionDir.deleteRecursively()
            _state.value = UpdateState.Failed(stageFailReason(it), av.releaseUrl)
        }
    }

    private fun stageFailReason(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            e is UnknownHostException -> "sem internet — tente pelo site"
            m.contains("HTTP 404") -> "essa versão ainda não está no GitHub"
            m.startsWith("HTTP") -> "o GitHub recusou ($m) — tente pelo site"
            m.contains("space", true) || m.contains("espaco", true) -> "sem espaco em disco para atualizar"
            m.contains("incompleto") -> "o download veio incompleto — tente de novo"
            e is IOException -> "a conexão caiu no meio — tente de novo"
            else -> "falha ao baixar — tente pelo site"
        }
    }

    private fun download(url: String, dest: File, onProgress: (Float) -> Unit) {
        val client = http.newBuilder()
            .callTimeout(Duration.ZERO)
            .readTimeout(Duration.ofSeconds(120))
            .build()
        var attempt = 0
        while (true) {
            attempt++
            val have = if (dest.exists()) dest.length() else 0L
            val reqB = Request.Builder().url(url).header("User-Agent", "Astra-Desktop")
            if (have > 0) reqB.header("Range", "bytes=$have-")
            try {
                var expected = -1L
                client.newCall(reqB.build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("sem corpo")
                    val resuming = resp.code == 206 && have > 0
                    if (!resuming) dest.delete()
                    val base = if (resuming) have else 0L
                    expected = body.contentLength().let { if (it > 0) base + it else -1L }
                    body.byteStream().use { input ->
                        FileOutputStream(dest, resuming).buffered().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var read = base
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                if (expected > 0) onProgress((read.toFloat() / expected).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                if (expected > 0 && dest.length() != expected) {
                    throw IOException("download incompleto (${dest.length()}/$expected)")
                }
                onProgress(1f)
                return
            } catch (e: IOException) {
                if (attempt >= 3) throw e
                Thread.sleep(1500)
            }
        }
    }

    private fun conferirHash(zipUrl: String, zip: File) {
        val esperado = runCatching {
            val req = Request.Builder().url("$zipUrl.sha256")
                .header("User-Agent", "Astra-Desktop").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()?.trim()?.split(Regex("\\s+"))?.firstOrNull()
            }
        }.getOrNull()?.lowercase()

        if (esperado.isNullOrBlank() || esperado.length != 64) return

        val md = MessageDigest.getInstance("SHA-256")
        zip.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val obtido = md.digest().joinToString("") { "%02x".format(it) }
        if (obtido != esperado) {
            zip.delete()
            error("o pacote baixado nao confere com o publicado")
        }
    }

    private fun unzip(zip: File, destDir: File) {
        destDir.mkdirs()
        val root = destDir.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val out = File(destDir, e.name)
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

    fun restartToInstall() {
        val exe = stagedExe?.takeIf { it.isFile } ?: exeDaMaiorVersao() ?: return
        SingleInstance.release()
        val novo = runCatching {
            ProcessBuilder(exe.absolutePath, ARG_POS_ATUALIZACAO)
                .directory(exe.parentFile)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrNull()
        if (novo != null) {
            FocoDoSistema.cederAFrenteA(novo.pid())
            exitProcess(0)
        }
    }

    private fun exeDaMaiorVersao(): File? {
        val versionsDir = appRootDir()?.parentFile ?: return null
        return versionsDir.listFiles()
            ?.filter { it.isDirectory && File(it, "Astra.exe").isFile }
            ?.maxWithOrNull { a, b -> if (isNewer(a.name, b.name)) 1 else -1 }
            ?.let { File(it, "Astra.exe") }
    }

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
