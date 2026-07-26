package app.astra.desktop.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.UnknownHostException
import java.time.Duration
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

// Auto-update DIY do Astra desktop, modo PORTATIL (decisao do dono: "pasta com
// todas as versoes + sempre abrir a mais nova"). Sem lib: bate na API publica de
// releases do GitHub, compara semver com a versao embutida (-Dastra.version),
// baixa o .zip do app-image novo com progresso (retry + resume), arquiva o zip e
// extrai a versao nova numa subpasta propria. No "reiniciar" so abre o Astra.exe
// da versao nova e sai — sem swap in-place (nada de rename/.old/.bat, que era o
// que dava permissao/race). O launcher (launch.vbs) sempre reabre a MAIOR versao.
//
// Layout portatil (o app roda de versions/<v>/Astra.exe):
//   C:/Astra/
//     Astra.lnk            atalho fixo -> launch.vbs
//     launch.vbs           acha a maior versao e roda seu Astra.exe (sem console)
//     versions/<v>/Astra.exe ...   uma pasta por versao (historico completo)
//     zips/Astra-<v>-win-x64.zip   cada zip baixado fica arquivado aqui
//
// Convencao de release (o dono segue ao publicar):
//   tag  : desktop-v<versao>            ex: desktop-v0.1.7
//   asset: Astra-<versao>-win-x64.zip   (contem a pasta Astra/ do createDistributable)

// Checagem SEM a API do GitHub (api.github.com tem rate-limit anonimo de 60/h por
// IP: varios boots/checagens + amigos no mesmo IP/CGNAT estouravam -> 403 -> falso
// "sem conexao"). github.com/<repo>/releases/latest responde 302 pra .../tag/<tag>
// e a pagina web NAO tem esse limite; da tag monta-se o zip por convencao.
private const val REPO = "Xryonz/Astra"
private const val LATEST_PAGE = "https://github.com/$REPO/releases/latest"

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

// Resolvida so pela tag (via redirect). notes/size ficam de fora: sem a API nao ha
// como obte-los, e nenhum dos dois e essencial (o download usa o contentLength).
private data class ResolvedRelease(
    val version: String,
    val downloadUrl: String,
    val releaseUrl: String,
)

class UpdateService(private val http: OkHttpClient) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()

    // Versao embutida no app-image (jvmArg do build.gradle). "dev" quando rodando
    // pelo Gradle/IDE — nesse caso o updater fica desligado (appRoot nao resolve).
    val currentVersion: String get() = System.getProperty("astra.version") ?: "dev"

    // So o app empacotado (Astra.exe) tem pasta pra versionar. Dev/IDE = nulo.
    val installed: Boolean get() = appRootDir() != null

    // Astra.exe da versao nova, ja extraida em versions/<v>/. Setado no stage.
    private var stagedExe: File? = null

    // ---- Checagem ----

    suspend fun check(silent: Boolean) = withContext(Dispatchers.IO) {
        if (!installed) { _state.value = UpdateState.Idle; return@withContext }
        _state.value = UpdateState.Checking
        val release = try {
            fetchLatest()
        } catch (e: Exception) {
            // Rede caiu de verdade. No gate (silent) nao assusta: assume "atualizado"
            // e segue. Manual (Settings) mostra a causa REAL — sem o falso "sem
            // conexao" que o rate-limit da API (60/h/IP) disparava antes.
            _state.value = if (silent) UpdateState.UpToDate
            else UpdateState.Failed(failureReason(e), LATEST_PAGE)
            return@withContext
        }
        // Sem release publicada, ou nao ha nada mais novo que agora.
        if (release == null || !isNewer(release.version, currentVersion)) {
            _state.value = UpdateState.UpToDate
            return@withContext
        }
        _state.value = UpdateState.Available(
            version = release.version,
            notes = "", // checagem sem API -> sem release notes (nao essencial)
            downloadUrl = release.downloadUrl,
            size = 0L, // desconhecido sem API; o download usa o contentLength da resposta
            releaseUrl = release.releaseUrl,
        )
    }

    // Le so o Location do 302 de github.com/<repo>/releases/latest (sem seguir o
    // redirect) -> extrai a tag -> monta a URL do zip por convencao. Null = sem
    // release publicada (200/sem Location) ou tag inesperada.
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
        // .../releases/tag/desktop-v0.1.0 -> "desktop-v0.1.0"
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

    // Causa legivel da falha da checagem manual. Sem o rate-limit da API no meio,
    // sobra so o caso real (rede) — a mensagem finalmente bate com o que houve.
    private fun failureReason(e: Throwable): String = when (e) {
        is UnknownHostException -> "sem internet"
        is IOException -> "sem conexao com o GitHub"
        else -> "nao deu pra verificar agora"
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

    // ---- Download + extracao (portatil) ----

    suspend fun downloadAndStage(av: UpdateState.Available) = withContext(Dispatchers.IO) {
        val appRoot = appRootDir() ?: run {
            _state.value = UpdateState.Failed("nao achei a pasta do app", av.releaseUrl)
            return@withContext
        }
        // appRoot = versions/<versao-atual>. Pai = versions/. Avo = C:/Astra (raiz
        // portatil, onde mora zips/). Fallback pro proprio versions se nao houver avo.
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
            unzip(zipFile, stagingDir)
            // O zip NAO e apagado: fica arquivado em zips/ (historico pedido pelo dono).
            // O asset tem a pasta Astra/ na raiz; achar quem contem Astra.exe (flat
            // ou aninhado) e promover essa pasta a versions/<v>/.
            val exeRoot =
                if (File(stagingDir, "Astra.exe").exists()) stagingDir
                else stagingDir.listFiles()?.firstOrNull { File(it, "Astra.exe").exists() }
                    ?: error("Astra.exe nao encontrado no pacote")
            newVersionDir.deleteRecursively()
            // rename e quase-atomico no mesmo volume; copia so se ele falhar (ex.:
            // exeRoot == stagingDir e alvo em volume diferente — raro).
            if (!exeRoot.renameTo(newVersionDir)) {
                exeRoot.copyRecursively(newVersionDir, overwrite = true)
            }
            stagingDir.deleteRecursively()
            // Confere que a versao extraida e um app-image COMPLETO (nao so o exe):
            // o jpackage sempre traz app/ e runtime/ junto. Faltando = pacote quebrado
            // (nunca deixa uma versao capenga virar a "mais nova" que o launcher abre).
            if (!File(newVersionDir, "app").isDirectory || !File(newVersionDir, "runtime").isDirectory) {
                error("pacote incompleto")
            }
            stagedExe = File(newVersionDir, "Astra.exe")
            _state.value = UpdateState.Ready(av.version)
        }.onFailure {
            // Nao deixa lixo: zip parcial/corrompido e a versao meio-extraida saem,
            // pra a proxima tentativa comecar limpa e o launcher nunca ver meia-versao.
            stagingDir.deleteRecursively()
            zipFile.delete()
            newVersionDir.deleteRecursively()
            _state.value = UpdateState.Failed(stageFailReason(it), av.releaseUrl)
        }
    }

    // Motivo LEGIVEL da falha do download/extracao — o dono quer saber se baixou ou
    // nao, e por que. Cada caso vira uma frase que bate com o que houve (a UI de
    // Settings mostra isto + o botao "abrir pagina do release").
    private fun stageFailReason(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            e is UnknownHostException -> "sem internet — tente pelo site"
            m.contains("HTTP 404") -> "essa versao ainda nao esta no GitHub"
            m.startsWith("HTTP") -> "o GitHub recusou ($m) — tente pelo site"
            m.contains("space", true) || m.contains("espaco", true) -> "sem espaco em disco pra atualizar"
            m.contains("incompleto") -> "o download veio incompleto — tente de novo"
            e is IOException -> "a conexao caiu no meio — tente de novo"
            else -> "falha ao baixar — tente pelo site"
        }
    }

    // Download grande (~140MB) resiliente: retry ate 3x e RESUME via Range quando o
    // servidor aceita (206) — um engasgo de rede >readTimeout nao joga fora o que ja
    // baixou. Sem callTimeout (a chamada inteira nao tem teto), so readTimeout por
    // leitura. OkHttp segue o 302 do asset pro storage sozinho.
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
                    // 206 = retomou de onde parou; qualquer outro (200) = comeca do zero.
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
                // Integridade: se o GitHub informou o tamanho, o arquivo TEM que bater.
                // Corte silencioso (a conexao morre sem erro de leitura) deixa o zip
                // truncado -> IOException dispara o retry, que RETOMA do byte que faltou.
                if (expected > 0 && dest.length() != expected) {
                    throw IOException("download incompleto (${dest.length()}/$expected)")
                }
                onProgress(1f)
                return
            } catch (e: IOException) {
                if (attempt >= 3) throw e
                // Espera curta e tenta RETOMAR do byte onde parou (nao recomeca).
                Thread.sleep(1500)
            }
        }
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

    // ---- Reinicio ----

    // Portatil: a versao nova ja esta pronta em versions/<v>/. So abre o Astra.exe
    // dela e sai — sem swap, sem .bat, sem esperar. O launcher (e o atalho) sempre
    // reabrem a MAIOR versao, entao os proximos boots tambem caem na nova.
    fun restartToInstall() {
        val exe = stagedExe ?: return
        ProcessBuilder(exe.absolutePath)
            .directory(exe.parentFile)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        exitProcess(0)
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
