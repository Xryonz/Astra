package app.astra.desktop.voice

import dev.onvoid.webrtc.media.audio.AudioProcessing
import dev.onvoid.webrtc.media.audio.AudioProcessingConfig
import dev.onvoid.webrtc.media.audio.AudioProcessingStreamConfig
import dev.onvoid.webrtc.media.audio.CustomAudioSource
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

// Captura o microfone por javax.sound.sampled (JDK puro) e passa CADA bloco de
// 10ms pelo APM do WebRTC (AudioProcessing) antes de empurrar no CustomAudioSource:
// supressao de ruido + high-pass + ganho automatico, ja convertendo pra 48kHz mono
// (o caminho feliz do WebRTC/Opus). Antes o PCM ia CRU -> Opus produzia "voz de
// robo com ruido" (mic cru + reamostragem 44.1k/estereo). AEC (eco) exige o sinal
// reverso (processReverseStream) e fica pra outra fase.
//
// O Core Audio nativo do webrtc-java quebra a captura quando o ADM e anexado ao
// factory ("Start recording failed"), entao a captura vem por este caminho
// independente que roda em qualquer maquina; o APM roda por fora, na mao.
//
// onLevel recebe o RMS (0..1) de cada bloco — usado pra "quem esta falando".
class MicCapture(
    private val source: CustomAudioSource,
    private val noiseSuppress: Boolean,
    private val autoGain: Boolean,
    private val echoCancel: Boolean,
    // Dispositivo de entrada (nome do Mixer; null = padrao do sistema).
    private val inputDeviceName: String? = null,
    private val onLevel: (Float) -> Unit,
) {
    private var line: TargetDataLine? = null
    private var apm: AudioProcessing? = null
    @Volatile private var running = false

    fun start(): Boolean {
        val format = FORMATS.firstOrNull {
            runCatching { AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, it)) }.getOrDefault(false)
        } ?: return false
        val rate = format.sampleRate.toInt()
        val channels = format.channels
        val inFrames = rate / 100 // bloco de 10ms na taxa capturada
        val inBytes = inFrames * channels * 2
        println("[MicCapture] formato do mic: ${rate}Hz ${channels}ch -> APM -> 48000Hz 1ch")

        // Buffer folgado (~200ms): pausa de GC nao derruba amostra (o picote que soa
        // robotico). Tenta o device escolhido; se falhar, cai no padrao do sistema.
        val l = acquireLine(inputDeviceName, format, inBytes * 20)
            ?: (if (inputDeviceName != null) acquireLine(null, format, inBytes * 20) else null)
            ?: return false
        line = l

        // APM: NS (alto) + high-pass sempre + AGC conforme a pref. AEC so tem efeito
        // real com o reverso (fase propria); deixamos o flag conforme a pref.
        apm = runCatching {
            AudioProcessing().apply {
                applyConfig(
                    AudioProcessingConfig().apply {
                        noiseSuppression.enabled = noiseSuppress
                        noiseSuppression.level = AudioProcessingConfig.NoiseSuppression.Level.HIGH
                        highPassFilter.enabled = true
                        gainControl.enabled = autoGain
                        echoCanceller.enabled = echoCancel
                    },
                )
            }
        }.getOrNull()

        val inConfig = AudioProcessingStreamConfig(rate, channels)
        val outConfig = AudioProcessingStreamConfig(48000, 1)
        val outFrames = 480 // 10ms @ 48kHz mono
        val outBytes = outFrames * 2

        running = true
        Thread({
            val inBuf = ByteArray(inBytes)
            val outBuf = ByteArray(outBytes)
            while (running) {
                val n = runCatching { l.read(inBuf, 0, inBuf.size) }.getOrDefault(-1)
                if (n <= 0) break
                if (n < inBuf.size) continue // bloco parcial (shutdown): mantem alinhamento
                val proc = apm
                if (proc != null) {
                    // Limpa (NS/HPF/AGC) + converte pra 48k mono no mesmo passo.
                    val ok = runCatching { proc.processStream(inBuf, inConfig, outConfig, outBuf) }.isSuccess
                    if (ok) {
                        runCatching { source.pushAudio(outBuf, 16, 48000, 1, outFrames) }
                        onLevel(rms(outBuf))
                        continue
                    }
                }
                // Sem APM (ou processStream falhou): cai pro cru pra nao ficar mudo.
                runCatching { source.pushAudio(inBuf, 16, rate, channels, inFrames) }
                onLevel(rms(inBuf))
            }
        }, "mic-capture").apply { isDaemon = true; start() }
        return true
    }

    // Abre a TargetDataLine no Mixer escolhido (ou padrao) e ja liga. Null = falhou.
    private fun acquireLine(deviceName: String?, format: AudioFormat, bufBytes: Int): TargetDataLine? = runCatching {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val mi = deviceName?.let { n -> AudioSystem.getMixerInfo().firstOrNull { it.name == n } }
        val l = if (mi != null) AudioSystem.getMixer(mi).getLine(info) as TargetDataLine
        else AudioSystem.getLine(info) as TargetDataLine
        l.apply { open(format, bufBytes); start() }
    }.getOrNull()

    fun stop() {
        running = false
        runCatching { line?.stop() }
        runCatching { line?.close() }
        line = null
        runCatching { apm?.dispose() }
        apm = null
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
        // 48k/44.1k, mono depois estereo. O APM converte qualquer um pra 48k mono.
        private val FORMATS = listOf(
            AudioFormat(48000f, 16, 1, true, false),
            AudioFormat(44100f, 16, 1, true, false),
            AudioFormat(48000f, 16, 2, true, false),
            AudioFormat(44100f, 16, 2, true, false),
        )
    }
}

// Enumera dispositivos de ENTRADA (mic) via Java Sound — nomes pro seletor da call.
// A SAIDA (alto-falante) vem do ADM do WebRTC (VoiceEngine.outputDevices()).
object AudioDevices {
    fun inputs(): List<String> = runCatching {
        AudioSystem.getMixerInfo().filter { mi ->
            runCatching {
                AudioSystem.getMixer(mi).targetLineInfo.any { it.lineClass == TargetDataLine::class.java }
            }.getOrDefault(false)
        }.map { it.name }.distinct()
    }.getOrDefault(emptyList())
}
