package app.astra.desktop.voice

import dev.onvoid.webrtc.media.audio.CustomAudioSource
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

// Captura o microfone por javax.sound.sampled (JDK puro) e empurra o PCM num
// CustomAudioSource do WebRTC. E o equivalente do ffmpeg pra tela: o Core Audio
// nativo do webrtc-java quebra a captura quando o ADM e anexado ao factory ("Start
// recording failed" em toda camada testada), entao trazemos a captura por um
// caminho independente que roda em qualquer maquina.
//
// onLevel recebe o RMS (0..1) de cada bloco de 10ms — usado pra "quem esta falando"
// (inchada do card), ja que a fonte custom nao reporta audioLevel no getStats.
class MicCapture(
    private val source: CustomAudioSource,
    private val onLevel: (Float) -> Unit,
) {
    private var line: TargetDataLine? = null
    @Volatile private var running = false

    fun start(): Boolean {
        val format = FORMATS.firstOrNull {
            runCatching { AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, it)) }.getOrDefault(false)
        } ?: return false
        val rate = format.sampleRate.toInt()
        val channels = format.channels
        val frames = rate / 100 // bloco de 10ms — formato que o WebRTC espera
        val chunkBytes = frames * channels * 2
        val l = runCatching {
            (AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, format)) as TargetDataLine).apply {
                open(format, chunkBytes * 4)
                start()
            }
        }.getOrNull() ?: return false
        line = l
        running = true
        Thread({
            val buf = ByteArray(chunkBytes)
            while (running) {
                val n = runCatching { l.read(buf, 0, buf.size) }.getOrDefault(-1)
                if (n <= 0) break
                if (n < buf.size) continue // bloco parcial (raro): descarta pra manter alinhamento
                runCatching { source.pushAudio(buf, 16, rate, channels, frames) }
                onLevel(rms(buf))
            }
        }, "mic-capture").apply { isDaemon = true; start() }
        return true
    }

    fun stop() {
        running = false
        runCatching { line?.stop() }
        runCatching { line?.close() }
        line = null
    }

    private fun rms(buf: ByteArray): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < buf.size) {
            val s = ((buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)).toShort().toInt()
            sum += s.toDouble() * s
            i += 2
        }
        val count = buf.size / 2
        if (count == 0) return 0f
        return (Math.sqrt(sum / count) / 32768.0).toFloat()
    }

    companion object {
        // 48k/44.1k, mono depois estereo — o pushAudio aceita a taxa capturada (o
        // WebRTC reamostra pro Opus internamente).
        private val FORMATS = listOf(
            AudioFormat(48000f, 16, 1, true, false),
            AudioFormat(44100f, 16, 1, true, false),
            AudioFormat(48000f, 16, 2, true, false),
            AudioFormat(44100f, 16, 2, true, false),
        )
    }
}
