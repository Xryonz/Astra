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
    // Tee do preview local: recebe (argb, w, h) a ~15fps. O webrtc-java NAO entrega
    // frames de CustomVideoSource pro sink da track local, entao o auto-preview
    // (Discord) sai daqui, direto da captura — nao do sink da track.
    private val onPreview: ((ByteArray, Int, Int) -> Unit)? = null,
) {
    private var process: Process? = null
    @Volatile private var running = false

    // Preview: throttle + 2 buffers ARGB reaproveitados (o UI copia no makeRaster,
    // entao 2 dao folga de sobra a 15fps sem alocar 8MB por frame). So a thread de
    // captura toca nestes campos.
    private var lastPreviewNs = 0L
    private var fullArgb = ByteArray(0)                       // scratch res cheia (cap thread)
    private val argbBufs = arrayOf(ByteArray(0), ByteArray(0)) // saida ja reduzida (2 buffers)
    private var argbIdx = 0

    fun start(outputIdx: Int, width: Int, height: Int, fps: Int): Boolean {
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
        Thread({
            val input = BufferedInputStream(proc.inputStream, frameSize)
            val buf = ByteArray(frameSize)
            while (running) {
                if (!readFully(input, buf)) break
                pushI420(buf, width, height)
                firstFrame.countDown()
            }
        }, "ffmpeg-cap").apply { isDaemon = true; start() }

        // Frames fluindo dentro do prazo = sucesso; senao (hardware nao aguenta o
        // DXGI, ddagrab falhou, etc) = falha -> fallback pro GDI.
        val ok = runCatching { firstFrame.await(2500, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        if (!ok || !proc.isAlive) {
            stop()
            return false
        }
        return true
    }

    fun stop() {
        running = false
        process?.let { runCatching { it.destroyForcibly() } }
        process = null
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
    // stride nativo) -> VideoFrame -> pushFrame. Ref-count no idioma do webrtc-java:
    // a VideoFrame assume o ref do buffer; release() dela solta tudo depois do push.
    private fun pushI420(src: ByteArray, w: Int, h: Int) {
        val buffer = NativeI420Buffer.allocate(w, h)
        val frame = try {
            val cW = w / 2
            val cH = h / 2
            val ySize = w * h
            val cSize = cW * cH
            copyPlane(src, 0, w, buffer.dataY, buffer.strideY, w, h)
            copyPlane(src, ySize, cW, buffer.dataU, buffer.strideU, cW, cH)
            copyPlane(src, ySize + cSize, cW, buffer.dataV, buffer.strideV, cW, cH)
            emitPreview(buffer, w, h)
            VideoFrame(buffer, System.nanoTime())
        } catch (t: Throwable) {
            runCatching { buffer.release() }
            return
        }
        try {
            source.pushFrame(frame)
        } finally {
            runCatching { frame.release() }
        }
    }

    // Converte o MESMO buffer I420 (antes de virar VideoFrame) pra ARGB e entrega
    // pra UI. Throttle ~15fps: preview nao precisa de 60, e a conversao/copia sai
    // do caminho quente da transmissao. Alterna 2 buffers pra nao alocar por frame.
    private fun emitPreview(buffer: NativeI420Buffer, w: Int, h: Int) {
        val cb = onPreview ?: return
        val now = System.nanoTime()
        if (now - lastPreviewNs < PREVIEW_INTERVAL_NS) return
        lastPreviewNs = now
        // Reduz pra no maximo PREVIEW_MAX_W de largura: o preview nao precisa da
        // resolucao cheia, e um ImageBitmap ~4x menor deixa makeRaster + upload de
        // textura muito mais leve (era isso que tirava a fluidez). Nearest basta.
        val scale = if (w > PREVIEW_MAX_W) PREVIEW_MAX_W.toDouble() / w else 1.0
        val pw = (w * scale).toInt() and 1.inv()
        val ph = (h * scale).toInt() and 1.inv()
        if (pw < 2 || ph < 2) return
        val fullNeed = w * h * 4
        if (fullArgb.size != fullNeed) fullArgb = ByteArray(fullNeed)
        val outNeed = pw * ph * 4
        val dst = argbBufs[argbIdx].let { if (it.size == outNeed) it else ByteArray(outNeed).also { b -> argbBufs[argbIdx] = b } }
        argbIdx = argbIdx xor 1
        runCatching {
            VideoBufferConverter.convertFromI420(buffer, fullArgb, FourCC.ABGR)
            if (pw == w && ph == h) System.arraycopy(fullArgb, 0, dst, 0, outNeed)
            else downscaleArgb(fullArgb, w, h, dst, pw, ph)
            cb(dst, pw, ph)
        }
    }

    // Nearest-neighbor ARGB — barato, roda na thread de captura fora do caminho da
    // transmissao. Pra preview a qualidade nearest e suficiente.
    private fun downscaleArgb(src: ByteArray, sw: Int, sh: Int, dst: ByteArray, dw: Int, dh: Int) {
        var di = 0
        for (y in 0 until dh) {
            val srow = (y * sh / dh) * sw * 4
            for (x in 0 until dw) {
                val si = srow + (x * sw / dw) * 4
                dst[di++] = src[si]
                dst[di++] = src[si + 1]
                dst[di++] = src[si + 2]
                dst[di++] = src[si + 3]
            }
        }
    }

    private fun copyPlane(src: ByteArray, srcOff: Int, srcStride: Int, dst: ByteBuffer, dstStride: Int, w: Int, h: Int) {
        var s = srcOff
        for (row in 0 until h) {
            dst.position(row * dstStride)
            dst.put(src, s, w)
            s += srcStride
        }
    }

    companion object {
        // Preview a ~24fps (42ms). A transmissao segue no framerate cheio do ddagrab.
        private const val PREVIEW_INTERVAL_NS = 42_000_000L
        // Largura maxima do preview (mantem aspecto). Preview -> nao precisa de 1080p.
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
