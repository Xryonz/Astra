package app.astra.desktop.voice

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import dev.onvoid.webrtc.media.FourCC
import dev.onvoid.webrtc.media.video.CustomVideoSource
import dev.onvoid.webrtc.media.video.NativeI420Buffer
import dev.onvoid.webrtc.media.video.VideoBufferConverter
import dev.onvoid.webrtc.media.video.VideoFrame
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Captura de tela RAPIDA (DXGI Desktop Duplication) via ffmpeg ddagrab, empurrada
// como frames I420 num CustomVideoSource do WebRTC. E o caminho de 60fps; o
// VoiceEngine cai pro capturador GDI (VideoDesktopSource, ~20-30fps) se isto
// falhar — entao roda em TODA maquina, so muda o fps.
//
// ddagrab so existe no Windows e e sensivel a hardware (Optimus, HDR, multi-GPU).
// Por isso: (1) forca a GPU integrada pro ffmpeg (resolve o beco do Optimus, onde
// o monitor "pertence" a dGPU mas a duplicacao so vale na iGPU); (2) start()
// confirma que frames REALMENTE fluem antes de declarar sucesso — senao o engine
// usa o fallback.
class ScreenCaptureFfmpeg(
    private val ffmpegPath: String,
    private val source: CustomVideoSource,
    // Tee do preview local: recebe (argb, w, h) no MESMO fps da transmissão (segue o
    // fps de captura). O webrtc-java NAO entrega frames de CustomVideoSource pro sink
    // da track local, entao o auto-preview (Discord) sai daqui, direto da captura.
    private val onPreview: ((ByteArray, Int, Int) -> Unit)? = null,
) {
    private var process: Process? = null
    @Volatile private var running = false

    // A thread da captura, guardada pra `stop()` conseguir ESPERAR por ela.
    //
    // E o mesmo buraco que o MicCapture ja tapou (ver la o comentario do "o Astra
    // fecha sozinho"): `stop()` baixava a bandeira e voltava na hora, quem chamava
    // seguia adiante e dava dispose() na CustomVideoSource — que e memoria nativa.
    // Se a thread estivesse a um passo do pushFrame (e a 60fps ela esta a 16ms de
    // distancia), o push caia num objeto ja descartado. O webrtc-java confere o
    // ponteiro antes de usar, entao aqui sai "NullPointerException: Object handle is
    // null" em vez de heap corrompido — sorte nossa, mas o app morre igual, porque
    // excecao nao tratada numa thread solta derruba tudo.
    private var capThread: Thread? = null
    // Preview LIGADO/DESLIGADO em runtime. Com a janela escondida (bandeja/minimizada)
    // ninguem esta olhando o auto-preview, mas ele continuava convertendo e enviando
    // frame a 60fps — CPU jogada fora. Desligando, a transmissão pros OUTROS continua
    // intacta (o encoder e um caminho separado); so o quadradinho local para.
    @Volatile var previewEnabled = true

    // Preview DESACOPLADO: a thread de captura NAO converte — so faz um arraycopy
    // barato do I420 pra um buffer compartilhado e acorda a thread de preview
    // (ffmpeg-preview), que faz a conversao ARGB + downscale FORA do caminho quente
    // captura->encoder. Antes a conversao rodava no pushI420 e (a) roubava CPU do
    // encoder e (b) segurava a drenagem do pipe -> a transmissão E o preview travavam.
    private var lastPreviewNs = 0L                 // so a thread de captura toca
    // Intervalo do preview = 1 frame no fps de captura, teto de PREVIEW_MAX_FPS (30).
    // Definido no start(). O corte usa 90% do intervalo (tolerancia): frames que chegam
    // de raspao antes do prazo não são descartados -> cadencia PAR, sem judder (era o
    // que fazia 30fps parecer travado).
    @Volatile private var previewIntervalNs = PREVIEW_INTERVAL_NS
    // Handoff capture -> worker: buffer I420 compartilhado, guardado pelo lock.
    private val previewLock = Object()
    private var previewShared = ByteArray(0)
    private var previewSharedReady = false
    private var previewSharedW = 0
    private var previewSharedH = 0
    private var previewThread: Thread? = null
    // Daqui pra baixo, SO a thread de preview toca:
    private var previewPrivate = ByteArray(0)                 // copia de trabalho do I420
    private var previewNative: NativeI420Buffer? = null       // reusado por resolucao
    private var previewNativeW = 0
    private var previewNativeH = 0
    // Anel do caminho da TRANSMISSAO (ver pushI420). So a thread de captura toca.
    private val anel = arrayOfNulls<NativeI420Buffer>(3)
    private var anelIdx = 0
    private var anelW = 0
    private var anelH = 0
    private var smallI420 = ByteArray(0)                      // I420 ja reduzido (Metodo B)
    private val argbBufs = arrayOf(ByteArray(0), ByteArray(0)) // saida já reduzida (2 buffers)
    private var argbIdx = 0

    fun start(outputIdx: Int, width: Int, height: Int, fps: Int): Boolean {
        // Preview acompanha o fps de captura ATE 60 (teto). O pipeline já e DESACOPLADO do
        // encoder (handoffPreview so faz memcpy pra thread 'ffmpeg-preview'; o pushFrame pro
        // encoder roda a parte, no fps de captura). Tradeoff: em HW fraco o dobro de
        // memcpy/conversao pode disputar CPU e antecipar o auto-downgrade do STREAM.
        previewIntervalNs = 1_000_000_000L / fps.coerceIn(1, PREVIEW_MAX_FPS)
        forceIntegratedGpu(ffmpegPath) // Optimus: sem isso, ddagrab da "output not supported"
        val filter = "ddagrab=output_idx=$outputIdx:framerate=$fps," +
            "hwdownload,format=bgra,scale=$width:$height,format=yuv420p"
        val cmd = listOf(
            ffmpegPath, "-hide_banner", "-loglevel", "error", "-nostdin",
            "-filter_complex", filter,
            "-f", "rawvideo", "-pix_fmt", "yuv420p", "pipe:1",
        )
        val proc = runCatching { ProcessBuilder(cmd).start() }.getOrNull() ?: return false
        process = proc
        running = true

        // Drena stderr num daemon (senao o buffer do pipe enche e trava o ffmpeg).
        Thread({ runCatching { proc.errorStream.use { it.readBytes() } } }, "ffmpeg-err")
            .apply { isDaemon = true; start() }

        val frameSize = width * height * 3 / 2
        val firstFrame = CountDownLatch(1)
        val cap = Thread({
            val input = BufferedInputStream(proc.inputStream, frameSize)
            val buf = ByteArray(frameSize)
            try {
                while (running) {
                    if (!readFully(input, buf)) break
                    // Conta o primeiro quadro ANTES de empurrar: o que o start() quer
                    // saber e se o ffmpeg esta PRODUZINDO. Se o push falhar, isso e
                    // outro problema — declarar "a captura nao subiu" mandaria a
                    // maquina inteira pro fallback GDI por engano.
                    firstFrame.countDown()
                    pushI420(buf, width, height)
                }
            } finally {
                // O anel e liberado AQUI, pela thread que o criou e a unica que o usa.
                // Fazer isso no stop() seria soltar memoria nativa que este laco ainda
                // pode estar preenchendo — e ai nao e quadro rasgado, e fechar do nada.
                // No `finally` porque saida por excecao tambem tem que devolver os tres
                // blocos: senao some ~4MB de memoria nativa por transmissao interrompida.
                liberarAnel()
            }
        }, "ffmpeg-cap")
        capThread = cap
        cap.isDaemon = true
        cap.start()

        // Thread de preview: converte o I420 mais recente em ARGB fora do caminho
        // quente. So existe quando ha um consumidor de preview.
        if (onPreview != null) {
            previewThread = Thread({ previewLoop() }, "ffmpeg-preview").apply { isDaemon = true; start() }
        }

        // Frames fluindo dentro do prazo = sucesso; senao (hardware não aguenta o
        // DXGI, ddagrab falhou, etc) = falha -> fallback pro GDI.
        val ok = runCatching { firstFrame.await(2500, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!ok || !proc.isAlive) {
            stop()
            return false
        }
        return true
    }

    // Devolve `true` quando a thread de captura morreu DE FATO.
    //
    // Quem chama so pode dar dispose() na CustomVideoSource depois de um `true`. Com
    // `false`, o certo e deixar a fonte vazar: alguns KB ate a proxima call custam
    // menos que derrubar o app no meio de uma. E o mesmo contrato do MicCapture.stop().
    fun stop(): Boolean {
        // Ordem obrigatoria: bandeira primeiro, processo depois. Ao contrario, o
        // read() volta com erro enquanto o laco ainda acha que deve rodar.
        running = false
        synchronized(previewLock) { previewLock.notifyAll() } // acorda o worker para sair do wait
        // Matar o ffmpeg e o que DESBLOQUEIA o read(): sem isso a thread ficaria
        // parada esperando o proximo quadro de um processo que ninguem mais alimenta.
        process?.let { runCatching { it.destroyForcibly() } }
        process = null

        val t = capThread
        capThread = null
        if (t == null || t === Thread.currentThread()) return true
        return runCatching { t.join(FIM_MS); !t.isAlive }.getOrDefault(false)
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = runCatching { input.read(buf, off, buf.size - off) }.getOrDefault(-1)
            if (n < 0) return false
            off += n
        }
        return true
    }

    // yuv420p do ffmpeg (Y | U | V empacotados) -> NativeI420Buffer (respeitando o
    // stride nativo) -> VideoFrame -> pushFrame.
    //
    // ANEL DE TRES BUFFERS, no lugar de um NativeI420Buffer.allocate() por quadro.
    // A 60fps em 720p aquilo era um malloc nativo de 1,4MB sessenta vezes por
    // segundo — 83MB/s de aloca-e-libera que o alocador nativo nunca devolve todo
    // ao sistema. Agora sao tres blocos vivos e nada mais.
    //
    // POR QUE TRES, e nao dois nem dez: o webrtc segura no maximo DOIS quadros de
    // cada vez (um esperando na fila do encoder, um sendo codificado — o
    // VideoStreamEncoder descarta o pendente velho quando chega outro). Tres da
    // exatamente um de folga. Reusar o buffer que o encoder ainda le produziria um
    // quadro rasgado, com dois instantes misturados.
    //
    // E o dimensionamento TEM que vir desse raciocinio porque a lib nao deixa
    // perguntar: RefCountedObject expoe retain() e release(), e nada que diga
    // "quantos ainda seguram isto" (o pool do proprio libwebrtc usa HasOneRef, que
    // o binding Java nao publica).
    //
    // Ref-count: o buffer nasce com uma referencia NOSSA, que o anel guarda ate o
    // stop(). O retain() antes de cada VideoFrame paga a referencia que ela vai
    // consumir no release() depois do push. Sem ele, o primeiro quadro liberaria a
    // memoria embaixo do anel — e ai nao seria quadro rasgado, seria fechar do nada.
    private fun pushI420(src: ByteArray, w: Int, h: Int) {
        val buffer = bufferDoAnel(w, h) ?: return
        val frame = try {
            val cW = w / 2
            val cH = h / 2
            val ySize = w * h
            val cSize = cW * cH
            copyPlane(src, 0, w, buffer.dataY, buffer.strideY, w, h)
            copyPlane(src, ySize, cW, buffer.dataU, buffer.strideU, cW, cH)
            copyPlane(src, ySize + cSize, cW, buffer.dataV, buffer.strideV, cW, cH)
            handoffPreview(src, w, h)
            buffer.retain()
            VideoFrame(buffer, System.nanoTime())
        } catch (t: Throwable) {
            return
        }
        try {
            // A bandeira e conferida DE NOVO aqui. Ela nao fecha a corrida sozinha
            // (nao ha trava entre esta linha e o dispose la fora) — quem fecha e o
            // join do stop(). Isto so encolhe a janela pro menor tamanho possivel.
            if (running) source.pushFrame(frame)
        } catch (t: Throwable) {
            // Ultima rede, e ela existe por um motivo especifico: esta thread e solta,
            // entao excecao aqui nao "falha o quadro", derruba o processo inteiro.
            // Parar de empurrar e a unica resposta certa — repetir 60 vezes por
            // segundo num objeto morto nao conserta nada.
            running = false
            VoiceLog.nota("a captura de tela parou: a fonte de video recusou o quadro (${t.javaClass.simpleName})")
        } finally {
            runCatching { frame.release() }
        }
    }

    // Proximo slot do anel. Troca de resolucao (o dono mudou o preset no meio da
    // transmissão) joga o anel inteiro fora: buffer de tamanho errado nao se
    // reaproveita, e manter os antigos vivos seria segurar memoria que nao serve
    // mais pra nada.
    private fun bufferDoAnel(w: Int, h: Int): NativeI420Buffer? {
        if (anelW != w || anelH != h) {
            liberarAnel()
            anelW = w; anelH = h
        }
        val i = anelIdx
        anelIdx = (anelIdx + 1) % anel.size
        anel[i]?.let { return it }
        val novo = runCatching { NativeI420Buffer.allocate(w, h) }.getOrNull() ?: return null
        anel[i] = novo
        return novo
    }

    private fun liberarAnel() {
        for (i in anel.indices) {
            anel[i]?.let { runCatching { it.release() } }
            anel[i] = null
        }
        anelIdx = 0
    }

    // NA thread de captura: barato. Throttle com tolerancia (90% do intervalo), copia
    // o I420 pro buffer compartilhado e acorda o worker. Se o worker estiver atrás, o
    // próximo handoff sobrescreve = sempre o frame mais novo (drop natural). NAO
    // converte nada aqui — a conversao e no previewLoop, fora do caminho do encoder.
    private fun handoffPreview(src: ByteArray, w: Int, h: Int) {
        if (onPreview == null || !previewEnabled) return
        val now = System.nanoTime()
        if (now - lastPreviewNs < previewIntervalNs * 9 / 10) return
        lastPreviewNs = now
        val need = w * h * 3 / 2
        synchronized(previewLock) {
            if (previewShared.size != need) previewShared = ByteArray(need)
            System.arraycopy(src, 0, previewShared, 0, need)
            previewSharedW = w; previewSharedH = h
            previewSharedReady = true
            previewLock.notifyAll()
        }
    }

    // Thread de preview: espera o próximo I420, copia pra um buffer privado (sob o
    // lock, so um arraycopy) e converte FORA do lock -> convert+downscale ficam longe
    // da thread de captura e do encoder. So o frame MAIS NOVO importa (drop natural).
    private fun previewLoop() {
        val cb = onPreview ?: return
        while (running) {
            var w = 0; var h = 0; var got = false
            synchronized(previewLock) {
                while (running && !previewSharedReady) {
                    try { previewLock.wait(200) } catch (_: InterruptedException) {}
                }
                if (previewSharedReady) {
                    val need = previewShared.size
                    if (previewPrivate.size != need) previewPrivate = ByteArray(need)
                    System.arraycopy(previewShared, 0, previewPrivate, 0, need)
                    w = previewSharedW; h = previewSharedH
                    previewSharedReady = false
                    got = true
                }
            }
            if (!running) break
            if (got) runCatching { convertAndEmit(previewPrivate, w, h, cb) }
        }
        runCatching { previewNative?.release() }
        previewNative = null
    }

    // I420 cru -> (reduz JA no I420) -> NativeI420Buffer -> ARGB (ABGR) -> callback.
    // So a thread de preview toca aqui.
    //
    // Metodo B (pesquisa de FPS): reduzir ANTES de converter. Antes convertia o frame
    // INTEIRO pra ARGB (buffer de w*h*4 — 3.7MB em 720p) e so depois reduzia; agora a
    // reducao acontece no I420 (1.5 byte/px em vez de 4) e a conversao roda ja no
    // tamanho final. Em 720p->960 isso e ~45% menos pixel convertido por frame e um
    // buffer de 3.7MB a menos vivo — direto no consumo de CPU/RAM da transmissão.
    private fun convertAndEmit(i420: ByteArray, w: Int, h: Int, cb: (ByteArray, Int, Int) -> Unit) {
        val cW = w / 2; val cH = h / 2
        val ySize = w * h; val cSize = cW * cH
        if (i420.size < ySize + 2 * cSize) return
        // Tamanho do preview (par, mantendo proporcao).
        val scale = if (w > PREVIEW_MAX_W) PREVIEW_MAX_W.toDouble() / w else 1.0
        val pw = (w * scale).toInt() and 1.inv()
        val ph = (h * scale).toInt() and 1.inv()
        if (pw < 2 || ph < 2) return

        // Fonte da conversao: o proprio frame (sem reducao) ou a copia reduzida.
        val src: ByteArray; val srcW: Int; val srcH: Int; val srcOff: Int
        if (pw == w && ph == h) {
            src = i420; srcW = w; srcH = h; srcOff = 0
        } else {
            val pcW = pw / 2; val pcH = ph / 2
            val need = pw * ph + 2 * (pcW * pcH)
            if (smallI420.size != need) smallI420 = ByteArray(need)
            scalePlane(i420, 0, w, h, smallI420, 0, pw, ph)
            scalePlane(i420, ySize, cW, cH, smallI420, pw * ph, pcW, pcH)
            scalePlane(i420, ySize + cSize, cW, cH, smallI420, pw * ph + pcW * pcH, pcW, pcH)
            src = smallI420; srcW = pw; srcH = ph; srcOff = 0
        }

        val sCW = srcW / 2; val sCH = srcH / 2
        val sY = srcW * srcH; val sC = sCW * sCH
        var native = previewNative
        if (native == null || previewNativeW != srcW || previewNativeH != srcH) {
            runCatching { native?.release() }
            native = runCatching { NativeI420Buffer.allocate(srcW, srcH) }.getOrNull() ?: return
            previewNative = native; previewNativeW = srcW; previewNativeH = srcH
        }
        copyPlane(src, srcOff, srcW, native.dataY, native.strideY, srcW, srcH)
        copyPlane(src, srcOff + sY, sCW, native.dataU, native.strideU, sCW, sCH)
        copyPlane(src, srcOff + sY + sC, sCW, native.dataV, native.strideV, sCW, sCH)

        val outNeed = pw * ph * 4
        val dst = argbBufs[argbIdx].let { if (it.size == outNeed) it else ByteArray(outNeed).also { b -> argbBufs[argbIdx] = b } }
        argbIdx = argbIdx xor 1
        VideoBufferConverter.convertFromI420(native, dst, FourCC.ABGR)
        cb(dst, pw, ph)
    }

    // Nearest-neighbor de UM plano (8 bits/px) — usado pra reduzir o I420 antes da
    // conversao. Roda na thread de preview, fora do caminho do encoder.
    private fun scalePlane(
        src: ByteArray, srcOff: Int, sw: Int, sh: Int,
        dst: ByteArray, dstOff: Int, dw: Int, dh: Int,
    ) {
        var di = dstOff
        for (y in 0 until dh) {
            val srow = srcOff + (y * sh / dh) * sw
            for (x in 0 until dw) dst[di++] = src[srow + (x * sw / dw)]
        }
    }

    // Copia um plano respeitando o stride do destino.
    //
    // Caminho rapido quando os dois strides sao a propria largura (o caso comum: o
    // ffmpeg entrega empacotado e o webrtc costuma alinhar em w): o plano inteiro vai
    // num put so, em vez de 720 chamadas de put + position por quadro. Sao ~2200
    // travessias de ByteBuffer a menos por quadro em 720p, 130 mil por segundo a
    // 60fps. Mesmos bytes no destino — nao ha diferenca de imagem, so de caminho.
    private fun copyPlane(src: ByteArray, srcOff: Int, srcStride: Int, dst: ByteBuffer, dstStride: Int, w: Int, h: Int) {
        if (srcStride == w && dstStride == w) {
            dst.position(0)
            dst.put(src, srcOff, w * h)
            return
        }
        var s = srcOff
        for (row in 0 until h) {
            dst.position(row * dstStride)
            dst.put(src, s, w)
            s += srcStride
        }
    }

    companion object {
        // Quanto o stop() espera a thread de captura morrer. Meio segundo e o mesmo
        // teto do MicCapture: o read() ja esta desbloqueado quando chegamos aqui, so
        // falta a thread terminar o quadro que tem na mao.
        private const val FIM_MS = 500L

        // Teto de fps da PREVIA LOCAL — de propósito METADE da transmissão.
        //
        // A transmissão pros outros continua nos 60fps do preset: ela e o produto.
        // A previa e so a conferencia de "estou mostrando a tela certa", e cada
        // quadro dela custa um memcpy + conversao de cor + reducao na CPU. Como o
        // encoder e SOFTWARE (o webrtc-java não usa NVENC), CPU gasta na previa e
        // CPU tirada de quem esta assistindo — e o primeiro sintoma disso e a
        // transmissão dos OUTROS engasgar, não a sua janelinha.
        // 30fps ja e fluido pra conferir enquadramento e devolve metade desse custo.
        private const val PREVIEW_MAX_FPS = 30
        // Fallback do intervalo do preview (~24fps) ate o start() amarrar ao fps real.
        private const val PREVIEW_INTERVAL_NS = 42_000_000L
        // Largura maxima do preview (mantem aspecto). Preview -> não precisa de 1080p.
        private const val PREVIEW_MAX_W = 960

        // Preferencia de GPU por-exe (HKCU) = "power saving" (integrada). No Optimus
        // isso faz o ffmpeg rodar na iGPU que DE FATO scaneia o monitor -> a
        // duplicacao funciona. Em PC de 1 GPU e inofensivo. Best-effort (nunca
        // derruba a captura se falhar).
        private fun forceIntegratedGpu(exePath: String) {
            runCatching {
                val key = "Software\\Microsoft\\DirectX\\UserGpuPreferences"
                if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key)) {
                    Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key)
                }
                Advapi32Util.registrySetStringValue(
                    WinReg.HKEY_CURRENT_USER, key, exePath, "GpuPreference=1;",
                )
            }
        }
    }
}

// Acha o ffmpeg.exe empacotado. Compose copia appResources/windows/* pro dir de
// recursos do app; em dev (gradle) cai no appResources do modulo. So Windows tem
// ddagrab -> fora do Windows retorna null (o engine usa o GDI direto).
object FfmpegLocator {
    val path: String? by lazy { resolve() }

    private fun resolve(): String? {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return null
        val candidates = buildList {
            System.getProperty("compose.application.resources.dir")?.let { add(File(it, "ffmpeg.exe")) }
            // Dev (gradle run): o cwd e a raiz do projeto Gradle (mobile-native).
            add(File("desktopApp/appResources/windows/ffmpeg.exe"))
        }
        return candidates.firstOrNull { it.isFile }?.absolutePath
    }
}
