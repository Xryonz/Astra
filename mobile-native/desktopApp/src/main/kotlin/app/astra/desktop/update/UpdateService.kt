package app.astra.desktop.update

import app.astra.desktop.SingleInstance
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
// todas as versões + sempre abrir a mais nova"). Sem lib: bate na API pública de
// releases do GitHub, compara semver com a versão embutida (-Dastra.version),
// baixa o .zip do app-image novo com progresso (retry + resume), arquiva o zip e
// extrai a versão nova numa subpasta propria. No "reiniciar" so abre o Astra.exe
// da versão nova e sai — sem swap in-place (nada de rename/.old/.bat, que era o
// que dava permissão/race). O launcher (launch.vbs) sempre reabre a MAIOR versão.
//
// Layout portatil (o app roda de versions/<v>/Astra.exe):
//   C:/Astra/
//     Astra.lnk            atalho fixo -> launch.vbs
//     launch.vbs           acha a maior versão e roda seu Astra.exe (sem console)
//     versions/<v>/Astra.exe ...   uma pasta por versão (historico completo)
//     zips/Astra-<v>-win-x64.zip   cada zip baixado fica arquivado aqui
//
// Convencao de release (o dono segue ao publicar):
//   tag  : desktop-v<versão>            ex: desktop-v0.1.7
//   asset: Astra-<versão>-win-x64.zip   (contem a pasta Astra/ do createDistributable)

// Checagem SEM a API do GitHub (api.github.com tem rate-limit anonimo de 60/h por
// IP: varios boots/checagens + amigos no mesmo IP/CGNAT estouravam -> 403 -> falso
// "sem conexão"). github.com/<repo>/releases/latest responde 302 pra .../tag/<tag>
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

// Resolvida so pela tag (via redirect). notes/size ficam de fora: sem a API não ha
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
    // pelo Gradle/IDE — nesse caso o updater fica desligado (appRoot não resolve).
    val currentVersion: String get() = System.getProperty("astra.version") ?: "dev"

    // So o app empacotado (Astra.exe) tem pasta pra versionar. Dev/IDE = nulo.
    val installed: Boolean get() = appRootDir() != null

    // Astra.exe da versão nova, já extraida em versions/<v>/. Setado no stage.
    private var stagedExe: File? = null

    // ---- Checagem ----

    suspend fun check(silent: Boolean) = withContext(Dispatchers.IO) {
        if (!installed) { _state.value = UpdateState.Idle; return@withContext }
        _state.value = UpdateState.Checking
        val release = try {
            fetchLatest()
        } catch (e: Exception) {
            // Rede caiu de verdade. No gate (silent) não assusta: assume "atualizado"
            // e segue. Manual (Settings) mostra a causa REAL — sem o falso "sem
            // conexão" que o rate-limit da API (60/h/IP) disparava antes.
            _state.value = if (silent) UpdateState.UpToDate
            else UpdateState.Failed(failureReason(e), LATEST_PAGE)
            return@withContext
        }
        // Sem release publicada, ou não ha nada mais novo que agora.
        if (release == null || !isNewer(release.version, currentVersion)) {
            _state.value = UpdateState.UpToDate
            return@withContext
        }
        _state.value = UpdateState.Available(
            version = release.version,
            notes = "", // checagem sem API -> sem release notes (não essencial)
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
        is IOException -> "sem conexão com o GitHub"
        else -> "não deu pra verificar agora"
    }

    // semver simples: a > b por campo (major.minor.patch). Campos não-numericos = 0.
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
            _state.value = UpdateState.Failed("não achei a pasta do app", av.releaseUrl)
            return@withContext
        }
        // appRoot = versions/<versão-atual>. Pai = versions/. Avo = C:/Astra (raiz
        // portatil, onde mora zips/). Fallback pro proprio versions se não houver avo.
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
                    ?: error("Astra.exe não encontrado no pacote")
            newVersionDir.deleteRecursively()
            // rename e quase-atomico no mesmo volume; copia so se ele falhar (ex.:
            // exeRoot == stagingDir e alvo em volume diferente — raro).
            if (!exeRoot.renameTo(newVersionDir)) {
                exeRoot.copyRecursively(newVersionDir, overwrite = true)
            }
            stagingDir.deleteRecursively()
            // Confere que a versão extraida e um app-image COMPLETO (não so o exe):
            // o jpackage sempre traz app/ e runtime/ junto. Faltando = pacote quebrado
            // (nunca deixa uma versão capenga virar a "mais nova" que o launcher abre).
            if (!File(newVersionDir, "app").isDirectory || !File(newVersionDir, "runtime").isDirectory) {
                error("pacote incompleto")
            }
            stagedExe = File(newVersionDir, "Astra.exe")
            _state.value = UpdateState.Ready(av.version)
        }.onFailure {
            // Não deixa lixo: zip parcial/corrompido e a versão meio-extraida saem,
            // pra a próxima tentativa comecar limpa e o launcher nunca ver meia-versão.
            stagingDir.deleteRecursively()
            zipFile.delete()
            newVersionDir.deleteRecursively()
            _state.value = UpdateState.Failed(stageFailReason(it), av.releaseUrl)
        }
    }

    // Motivo LEGIVEL da falha do download/extracao — o dono quer saber se baixou ou
    // não, e por que. Cada caso vira uma frase que bate com o que houve (a UI de
    // Settings mostra isto + o botao "abrir pagina do release").
    private fun stageFailReason(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            e is UnknownHostException -> "sem internet — tente pelo site"
            m.contains("HTTP 404") -> "essa versão ainda não está no GitHub"
            m.startsWith("HTTP") -> "o GitHub recusou ($m) — tente pelo site"
            m.contains("space", true) || m.contains("espaco", true) -> "sem espaco em disco pra atualizar"
            m.contains("incompleto") -> "o download veio incompleto — tente de novo"
            e is IOException -> "a conexão caiu no meio — tente de novo"
            else -> "falha ao baixar — tente pelo site"
        }
    }

    // Download grande (~140MB) resiliente: retry ate 3x e RESUME via Range quando o
    // servidor aceita (206) — um engasgo de rede >readTimeout não joga fora o que já
    // baixou. Sem callTimeout (a chamada inteira não tem teto), so readTimeout por
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
                // Corte silencioso (a conexão morre sem erro de leitura) deixa o zip
                // truncado -> IOException dispara o retry, que RETOMA do byte que faltou.
                if (expected > 0 && dest.length() != expected) {
                    throw IOException("download incompleto (${dest.length()}/$expected)")
                }
                onProgress(1f)
                return
            } catch (e: IOException) {
                if (attempt >= 3) throw e
                // Espera curta e tenta RETOMAR do byte onde parou (não recomeca).
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

    // Portatil: a versão nova já esta pronta em versions/<v>/. So abre o Astra.exe
    // dela e sai — sem swap, sem .bat, sem esperar. O launcher (e o atalho) sempre
    // reabrem a MAIOR versão, entao os proximos boots também caem na nova.
    //
    // POR QUE ISTO NAO ERA SO "start + exit":
    //
    // 1. A TRAVA DE INSTANCIA UNICA. O processo velho ainda segura a porta quando o
    //    novo abre. O novo entao conclui "já tem um Astra aberto", avisa o velho e
    //    SAI — e logo depois o velho sai também. Resultado visto de fora: a janela
    //    pisca, tudo fecha e nada reabre. Por isso a trava e solta ANTES de abrir o
    //    novo: quando ele chegar na checagem (~1s de JVM), a porta já esta livre.
    //
    // 2. FECHAR SEM TER ABERTO. Se o start falhasse, o exitProcess vinha do mesmo
    //    jeito e o app sumia sem substituto. Agora so sai se o novo de fato subiu.
    //
    // 3. stagedExe NULO. Ele so existe na sessão em que a versão foi baixada. Quem
    //    baixou, fechou e reabriu caia num `return` mudo — a tela dizia "reiniciando"
    //    e nada acontecia. O fallback acha a maior versão instalada no disco.
    fun restartToInstall() {
        val exe = stagedExe?.takeIf { it.isFile } ?: exeDaMaiorVersao() ?: return
        SingleInstance.release()
        val subiu = runCatching {
            ProcessBuilder(exe.absolutePath)
                .directory(exe.parentFile)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.isSuccess
        if (subiu) exitProcess(0)
    }

    // Maior versão presente em versions/. Ordena por semver, nao por texto: por
    // ordem alfabetica "0.1.9" viria depois de "0.1.38" e o app reabriria a VELHA.
    private fun exeDaMaiorVersao(): File? {
        val versionsDir = appRootDir()?.parentFile ?: return null
        return versionsDir.listFiles()
            ?.filter { it.isDirectory && File(it, "Astra.exe").isFile }
            ?.maxWithOrNull { a, b -> if (isNewer(a.name, b.name)) 1 else -1 }
            ?.let { File(it, "Astra.exe") }
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
