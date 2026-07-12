package app.astra.desktop.voice

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import dev.onvoid.webrtc.media.video.CustomVideoSource
import dev.onvoid.webrtc.media.video.NativeI420Buffer
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
) {
    private var process: Process? = null
    @Volatile private var running = false

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

    private fun copyPlane(src: ByteArray, srcOff: Int, srcStride: Int, dst: ByteBuffer, dstStride: Int, w: Int, h: Int) {
        var s = srcOff
        for (row in 0 until h) {
            dst.position(row * dstStride)
            dst.put(src, s, w)
            s += srcStride
        }
    }

    companion object {
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
