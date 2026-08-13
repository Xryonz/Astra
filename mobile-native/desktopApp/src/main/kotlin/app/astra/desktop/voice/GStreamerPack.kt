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
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.Version
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit
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

    // Versao do PACOTE, nao do Astra e nao exatamente a do GStreamer: o sufixo conta
    // quantas vezes o conteudo mudou sem o GStreamer mudar. Publicado numa tag propria
    // (`gstreamer-<versao>`), sobrevive as atualizacoes do app.
    //
    // O `-2` existe porque o `-1` saiu quebrado de um jeito silencioso: faltava
    // `gstd3d12-1.0-0.dll` (0,7 MB) e, sem ela, os plugins do NVENC e do Quick Sync
    // morriam na carga sem reclamar. Quem tem placa boa caia no encoder generico do
    // Windows sem nunca saber. Trocar de versao (em vez de republicar o mesmo arquivo) e
    // o que faz quem ja baixou o pacote velho baixar o novo — a marca `.completo` nao
    // olha pra dentro da pasta.
    const val VERSAO = "1.28.6-2"

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

    // %LOCALAPPDATA%\Astra\ — onde ja moram voz.txt, diagnostico.txt e falhas.txt.
    private val pastaDeDados: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")
        File(base, "Astra")
    }

    // %LOCALAPPDATA%\Astra\gstreamer\<versao>\  — fora do diretorio do app DE PROPOSITO:
    // a pasta do app pode ser somente-leitura, e e trocada inteira a cada atualizacao.
    private val raiz: File by lazy { File(pastaDeDados, "gstreamer/$VERSAO") }

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
        limparVersoesAntigas()
    }

    // Cada versao do pacote sao ~60 MB descompactados. Sem esta faxina, subir a versao
    // deixaria a anterior parada em disco pra sempre — e o publico deste caminho e
    // justamente quem tem computador apertado.
    //
    // So roda DEPOIS da troca atomica: apagar antes deixaria a pessoa sem caminho nenhum
    // se a instalacao nova falhasse no meio.
    private fun limparVersoesAntigas() {
        val pai = raiz.parentFile ?: return
        pai.listFiles()?.forEach { f ->
            if (f.isDirectory && f.name != VERSAO) runCatching { f.deleteRecursively() }
        }
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

        // A JNA nao le o PATH do processo: ela monta a lista de candidatos a partir de
        // `jna.library.path`. Os bindings do GStreamer carregam por ela, entao sem esta
        // linha o PATH acima serve pras DEPENDENCIAS (que o loader do Windows resolve) e
        // nao pra biblioteca principal — que e justamente a primeira a ser procurada.
        val jna = System.getProperty("jna.library.path")
        System.setProperty(
            "jna.library.path",
            if (jna.isNullOrBlank()) binDir.absolutePath else "${binDir.absolutePath}${File.pathSeparator}$jna",
        )

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

        // O LOG EM ARQUIVO, e esta e a licao mais cara da migracao.
        //
        // O app morreu tres vezes ao entrar na call sem deixar NADA: sem excecao Java, sem
        // hs_err, sem registro no Visualizador de Eventos do Windows. O motivo estava
        // sendo impresso o tempo todo -- na saida de erro, que num app de janela do
        // jpackage nao existe. Horas de investigacao pra ler algo que o proprio GStreamer
        // ja estava dizendo.
        //
        // Por variavel de ambiente, e nao por argumento do `Gst.init`: os argumentos NAO
        // chegam ao `gst_init` por este binding (testado -- o arquivo simplesmente nao
        // nascia). Estas o codigo nativo le direto.
        //
        // Nivel 2 = so ERRO e AVISO. Rotina fica de fora e o arquivo nao passa de alguns
        // kilobytes numa sessao inteira; o GStreamer o reabre do zero a cada boot.
        k.SetEnvironmentVariable("GST_DEBUG", "2")
        k.SetEnvironmentVariable("GST_DEBUG_FILE", File(pastaDeDados, "gst.txt").absolutePath)
        k.SetEnvironmentVariable("GST_DEBUG_NO_COLOR", "1")

        ambientePronto = true
        return true
    }

    // Carrega o GStreamer DENTRO do processo. Uma vez so: Gst.init duas vezes e
    // comportamento indefinido do lado nativo.
    //
    // Mora aqui, e nao em quem usa, porque ja sao dois donos (a medicao e o transporte) e
    // "uma vez por processo" nao e algo que se combine entre iguais — precisa de um lugar.
    //
    // O catch e de Throwable, nao Exception, e isso e deliberado: falha ao carregar
    // biblioteca nativa chega como UnsatisfiedLinkError/NoClassDefFoundError, que sao
    // Error. Um catch de Exception deixaria passar exatamente o caso que se quer conter.
    @Volatile private var gstPronto = false

    @Synchronized
    fun iniciarGst(): Boolean {
        if (gstPronto) return true
        if (!prepararAmbiente()) return false
        return try {
            // A VERSAO PEDIDA NAO E DETALHE, e custou uma sessao de banco de testes.
            //
            // `Gst.init("Astra")` sem versao pede a BASELINE (1.8), e o gst1-java-core
            // TRANCA por versao cada funcao nativa que nasceu depois: chamar uma delas
            // levanta "Not supported by requested GStreamer version" -- mesmo com o
            // GStreamer 1.28 carregado e a funcao ali, presente. Foi o que derrubou o
            // setLocalDescription no ensaio, e o erro nao aponta pra versao pedida: ele
            // parece defeito da biblioteca.
            //
            // 1.20 cobre a API de WebRTC/SDP inteira e fica bem abaixo do que o pacote
            // entrega (1.28.6), que e nosso e nao muda debaixo da gente -- o
            // GST_PLUGIN_SYSTEM_PATH vazio garante que nunca carregamos a do sistema.
            Gst.init(Version.of(1, 20), "Astra")
            gstPronto = true
            // Diz ONDE o log do GStreamer foi parar. Sem esta linha, "o gst.txt nao mudou"
            // tem duas leituras -- nao houve nada pra registrar, ou a variavel de ambiente
            // nao pegou -- e as duas pedem investigacoes opostas.
            VoiceLog.nota("GStreamer no ar; o log dele vai para ${File(pastaDeDados, "gst.txt").absolutePath}")
            true
        } catch (t: Throwable) {
            VoiceLog.nota("GStreamer nao carregou: ${t.javaClass.simpleName} ${t.message.orEmpty()}")
            false
        }
    }

    // ---- Sonda de aceleracao por hardware ----

    // Ordem = preferencia. So encoders de HARDWARE: x264/openh264 ficam de fora de
    // proposito, porque encontrar um deles seria o mesmo custo que ja temos hoje.
    //
    // `nvh264enc` NAO ESTA AQUI, e a ausencia dele e o achado que mais custou.
    //
    // A NVIDIA publica TRES elementos, e o nome nao diz qual serve: `nvh264enc` e o modo
    // CUDA, `nvd3d11h264enc` e o modo Direct3D 11, `nvautogpuh264enc` escolhe sozinho.
    // So o do meio aceita quadro que ja esta na placa. Com o `nvh264enc` o cano nem
    // liga -- e o GStreamer nao trata isso como erro: ele AVISA e monta o cano assim
    // mesmo, sem o ramo de video. O ensaio subiu, negociou audio, e a linha de video
    // saiu com porta zero. Uma transmissao que nao transmite, sem uma unica excecao.
    private val ENCODERS = listOf(
        "nvd3d11h264enc" to "NVIDIA NVENC",
        "qsvh264enc" to "Intel Quick Sync",
        "amfh264enc" to "AMD AMF",
        "mfh264enc" to "Media Foundation",
    )

    data class Aceleracao(val encoders: List<String>, val motivo: String?) {
        val temHardware get() = encoders.isNotEmpty()
    }

    // Descobre se ESTA maquina consegue codificar video por hardware.
    //
    // Existe pra responder a distancia uma pergunta que hoje so se responde sentado na
    // maquina: "o PC dele tem encoder de hardware?". Quem esta com o computador lento
    // aperta um botao e le a resposta — nao precisa instalar GStreamer nem rodar script.
    suspend fun detectarAceleracao(http: OkHttpClient): Aceleracao {
        if (!garantir(http)) {
            val motivo = (_estado.value as? Estado.Falhou)?.motivo ?: "pacote indisponivel"
            return Aceleracao(emptyList(), motivo)
        }
        return withContext(Dispatchers.IO) {
            val achados = ENCODERS.filter { temElemento(it.first) }.map { it.second }
            Aceleracao(achados, if (achados.isEmpty()) "nenhum encoder de video por hardware" else null)
        }
    }

    // Pergunta num PROCESSO FILHO, e essa e a decisao de desenho aqui.
    //
    // Carregar o GStreamer dentro do Astra (Gst.init) para descobrir isto seria trocar
    // uma pergunta por um risco: falha de carregamento nativo nao lanca excecao em
    // Kotlin, derruba o processo inteiro. Num filho, o pior caso e um codigo de saida
    // diferente de zero.
    private fun temElemento(nome: String): Boolean {
        val exe = File(binDir, "gst-inspect-1.0.exe")
        if (!exe.isFile) return false
        return runCatching {
            val pb = ProcessBuilder(exe.absolutePath, nome)
            pb.environment().apply {
                put("PATH", binDir.absolutePath)
                put("GST_PLUGIN_PATH", pluginDir.absolutePath)
                put("GST_PLUGIN_SYSTEM_PATH", "")
                put("GST_REGISTRY", File(raiz, "registry.bin").absolutePath)
            }
            pb.redirectErrorStream(true)
            val p = pb.start()
            // Drenar a saida ANTES do waitFor: um processo que enche o pipe e ninguem
            // le fica bloqueado escrevendo, e o waitFor espera pra sempre.
            p.inputStream.use { it.readBytes() }
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                false
            } else {
                p.exitValue() == 0
            }
        }.getOrDefault(false)
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
