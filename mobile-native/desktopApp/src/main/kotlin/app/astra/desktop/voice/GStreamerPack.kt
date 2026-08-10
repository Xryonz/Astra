package app.astra.desktop.voice

import com.sun.jna.platform.win32.Kernel32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipFile

// O pacote do GStreamer que o Astra baixa SOZINHO, uma vez, na primeira transmissao.
//
// POR QUE UM PACOTE A PARTE, e nao dentro do zip do app:
//
// O auto-update baixa o zip inteiro a cada versao. Engordar o app em ~23MB significaria
// cobrar esses 23MB de novo em toda correcao de bug — e quem tem internet ruim e
// exatamente o publico de PC fraco que este caminho existe pra atender. Aqui o pacote
// mora FORA do app, em %LOCALAPPDATA%, versionado pela versao do GStreamer: atualizar o
// Astra nao rebaixa nada, e o GStreamer so muda quando a gente decidir subir de versao.
//
// POR QUE ISSO E SEGURO E NAO UMA APOSTA:
//
// O caminho GStreamer so ajuda maquina com encoder de HARDWARE. Sem ele, o Astra
// continua no ffmpeg + webrtc-java de hoje — exatamente como o ddagrab ja cai pro GDI.
// Ou seja, o app TEM que funcionar sem este pacote de qualquer jeito. A ausencia dele
// nunca e falha: e um caminho a menos. Por isso baixar sob demanda nao adiciona um modo
// de quebrar que ja nao existisse.
//
// ESTA CLASSE AINDA NAO E CHAMADA POR NINGUEM. E a fatia de entrega, entregue antes do
// pipeline de proposito: ela nao toca em uma linha de voz, entao nao pode quebrar call.
object GStreamerPack {

    // Versao do GStreamer, nao do Astra. O pacote e publicado numa tag propria
    // (`gstreamer-<versao>`) e sobrevive as atualizacoes do app.
    const val VERSAO = "1.28.6"

    private const val REPO = "Xryonz/Astra"
    private val ZIP_URL =
        "https://github.com/$REPO/releases/download/gstreamer-$VERSAO/gstreamer-$VERSAO-win-x64.zip"

    sealed interface Estado {
        data object Ausente : Estado
        data class Baixando(val fracao: Float) : Estado
        data object Pronto : Estado
        data class Falhou(val motivo: String) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Ausente)
    val estado = _estado.asStateFlow()

    // Uma tentativa por vez. Sem isto, duas transmissoes abertas em sequencia rapida
    // baixariam o mesmo arquivo duas vezes por cima uma da outra.
    private val trava = Mutex()

    // %LOCALAPPDATA%\Astra\gstreamer\<versao>\  — fora do diretorio do app DE PROPOSITO:
    // a pasta do app pode ser somente-leitura, e e trocada inteira a cada atualizacao.
    private val raiz: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")
        File(File(base, "Astra"), "gstreamer/$VERSAO")
    }

    private val binDir get() = File(raiz, "bin")
    private val pluginDir get() = File(raiz, "lib/gstreamer-1.0")

    // Marca escrita SO no fim de uma instalacao inteira. Sem ela, um download
    // interrompido no meio deixaria uma pasta pela metade que parece pronta — e DLL
    // faltando so aparece quando o pipeline sobe, com a call ja comecando.
    private val marca get() = File(raiz, ".completo")

    val disponivel: Boolean
        get() = marca.isFile && binDir.isDirectory && pluginDir.isDirectory

    // Garante o pacote em disco. `false` = siga pelo caminho de hoje, sem drama.
    suspend fun garantir(http: OkHttpClient): Boolean = trava.withLock {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return false
        if (disponivel) {
            _estado.value = Estado.Pronto
            return true
        }
        withContext(Dispatchers.IO) {
            runCatching { instalar(http) }
                .onSuccess { _estado.value = Estado.Pronto }
                .onFailure { e ->
                    _estado.value = Estado.Falhou(motivoLegivel(e))
                    VoiceLog.nota("pacote do GStreamer nao instalou: ${motivoLegivel(e)}")
                }
                .isSuccess
        }
    }

    private fun instalar(http: OkHttpClient) {
        val tmp = File(raiz.parentFile, "$VERSAO.baixando")
        if (tmp.exists()) tmp.deleteRecursively()
        tmp.mkdirs()
        val zip = File(tmp, "pacote.zip")

        _estado.value = Estado.Baixando(0f)
        baixar(http, ZIP_URL, zip) { _estado.value = Estado.Baixando(it) }
        conferirHash(http, zip)
        descompactar(zip, tmp)
        zip.delete()

        // Troca atomica: a pasta final so passa a existir ja completa. Um travamento no
        // meio da descompactacao deixa lixo em `.baixando`, nunca meia instalacao.
        if (raiz.exists()) raiz.deleteRecursively()
        raiz.parentFile.mkdirs()
        if (!tmp.renameTo(raiz)) error("nao consegui mover o pacote pra $raiz")
        File(raiz, ".completo").writeText(VERSAO)
    }

    // Mesmo desenho do download do auto-update: retoma de onde parou (Range) e confere o
    // tamanho no fim, porque conexao que morre em silencio deixa o zip truncado sem
    // lancar erro de leitura.
    private fun baixar(http: OkHttpClient, url: String, dest: File, onProgresso: (Float) -> Unit) {
        val client = http.newBuilder()
            .callTimeout(Duration.ZERO)
            .readTimeout(Duration.ofSeconds(120))
            .build()
        var tentativa = 0
        while (true) {
            tentativa++
            val tem = if (dest.exists()) dest.length() else 0L
            val req = Request.Builder().url(url).header("User-Agent", "Astra-Desktop")
            if (tem > 0) req.header("Range", "bytes=$tem-")
            try {
                var esperado = -1L
                client.newCall(req.build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("sem corpo")
                    val retomando = resp.code == 206 && tem > 0
                    if (!retomando) dest.delete()
                    val base = if (retomando) tem else 0L
                    esperado = body.contentLength().let { if (it > 0) base + it else -1L }
                    body.byteStream().use { input ->
                        FileOutputStream(dest, retomando).buffered().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var lido = base
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                lido += n
                                if (esperado > 0) onProgresso((lido.toFloat() / esperado).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                if (esperado > 0 && dest.length() != esperado) {
                    throw IOException("download incompleto (${dest.length()}/$esperado)")
                }
                onProgresso(1f)
                return
            } catch (e: IOException) {
                if (tentativa >= 3) throw e
                Thread.sleep(1500)
            }
        }
    }

    // AQUI O HASH E OBRIGATORIO, ao contrario do auto-update do app.
    //
    // La, aceitar hash ausente foi decisao consciente: releases antigas nao tem o
    // arquivo, e recusar deixaria todo mundo preso na versao instalada. Aqui nao ha
    // legado nenhum — o primeiro pacote ja nasce com hash. E o que este zip contem sao
    // DLLs que vao ser carregadas DENTRO do processo do Astra, que e o pior lugar
    // possivel pra confiar num arquivo que veio pela rede sem conferencia.
    private fun conferirHash(http: OkHttpClient, zip: File) {
        val req = Request.Builder().url("$ZIP_URL.sha256")
            .header("User-Agent", "Astra-Desktop").build()
        val esperado = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} no hash")
            // Formato do sha256sum: "<hash>  <nome>".
            resp.body?.string()?.trim()?.split(Regex("\\s+"))?.firstOrNull()
        }?.lowercase() ?: error("hash vazio")
        if (esperado.length != 64) error("hash malformado")

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
        if (obtido != esperado) error("o pacote baixado nao confere com o publicado")
    }

    private fun descompactar(zip: File, destino: File) {
        ZipFile(zip).use { zf ->
            for (e in zf.entries()) {
                val alvo = File(destino, e.name)
                // Zip Slip: entrada com ".." escaparia da pasta e escreveria em qualquer
                // lugar que o usuario possa escrever. O pacote e nosso, mas a conferencia
                // custa uma linha e o estrago custaria o computador de quem baixou.
                if (!alvo.canonicalPath.startsWith(destino.canonicalPath + File.separator)) {
                    error("entrada suspeita no pacote: ${e.name}")
                }
                if (e.isDirectory) {
                    alvo.mkdirs()
                } else {
                    alvo.parentFile?.mkdirs()
                    zf.getInputStream(e).use { input ->
                        FileOutputStream(alvo).buffered().use { input.copyTo(it) }
                    }
                }
            }
        }
    }

    // Prepara o ambiente do PROCESSO pro GStreamer achar o que precisa. Roda uma vez,
    // antes do Gst.init().
    //
    // Tem que ser via Kernel32 e nao System.setProperty: quem le estas variaveis e
    // codigo nativo (GLib/GStreamer), e o mapa de ambiente da JVM e uma copia que o
    // lado nativo nao enxerga.
    private var ambientePronto = false

    fun prepararAmbiente(): Boolean {
        if (!disponivel) return false
        if (ambientePronto) return true
        val k = runCatching { Kernel32.INSTANCE }.getOrNull() ?: return false

        // O bin do pacote na FRENTE do PATH: e assim que o loader do Windows acha as
        // dependencias dos plugins. Sem isso, o plugin carrega e a DLL de que ele
        // depende nao — erro sem nome de arquivo, no meio da call.
        val path = System.getenv("PATH").orEmpty()
        k.SetEnvironmentVariable("PATH", "${binDir.absolutePath};$path")

        k.SetEnvironmentVariable("GST_PLUGIN_PATH", pluginDir.absolutePath)

        // VAZIO DE PROPOSITO, e esta e a linha mais importante do arquivo.
        //
        // Numa maquina que tenha GStreamer instalado (a de quem desenvolve, tipicamente),
        // o padrao faria o processo carregar plugins da instalacao do SISTEMA junto com
        // os nossos. Duas versoes do mesmo plugin no mesmo processo e crash nativo — e do
        // tipo que so acontece em quem tem GStreamer, ou seja, nunca em teste.
        k.SetEnvironmentVariable("GST_PLUGIN_SYSTEM_PATH", "")

        // O GStreamer escreve um cache do registro de plugins na primeira execucao. O
        // padrao cai em lugar que pode nao ser gravavel; aqui fica junto do pacote.
        k.SetEnvironmentVariable("GST_REGISTRY", File(raiz, "registry.bin").absolutePath)

        ambientePronto = true
        return true
    }

    private fun motivoLegivel(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("HTTP 404") -> "o pacote ainda nao foi publicado"
            m.startsWith("HTTP") -> "o GitHub recusou ($m)"
            m.contains("nao confere") -> m
            m.contains("incompleto") -> "o download veio incompleto"
            e is IOException -> "a conexao caiu no meio"
            else -> m.ifBlank { "falha desconhecida" }
        }
    }
}
