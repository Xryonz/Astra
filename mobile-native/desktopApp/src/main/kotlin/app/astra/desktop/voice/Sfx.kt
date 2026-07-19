package app.astra.desktop.voice

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

// Sons do app SINTETIZADOS em runtime (sem arquivos .wav): senoides curtas com
// envelope (fade in/out) pra nao estalar. Convencao do dono:
//   entrar na call  = agudo/fino  (sobe)
//   sair da call     = grave/grosso (desce)
//   transmitir tela  = 3 fases subindo (cada fase mais fina)
//   parar transmissao= as MESMAS 3 fases, invertidas (descendo)
// Toca numa thread daemon (nao trava a UI); so JDK (javax.sound), zero dependencia.
object Sfx {
    private const val RATE = 44100

    private data class Tone(val hz: Float, val ms: Int, val gain: Float = 0.26f)

    fun callJoin()   = play(listOf(Tone(620f, 80), Tone(930f, 150)))
    fun callLeave()  = play(listOf(Tone(430f, 90), Tone(300f, 175)))
    fun shareStart() = play(listOf(Tone(500f, 95), Tone(680f, 95), Tone(920f, 155)))
    fun shareStop()  = play(listOf(Tone(920f, 95), Tone(680f, 95), Tone(500f, 155)))

    private fun play(seq: List<Tone>) {
        thread(isDaemon = true, name = "astra-sfx") {
            runCatching {
                val fmt = AudioFormat(RATE.toFloat(), 16, 1, true, false)
                val buf = render(seq)
                AudioSystem.getSourceDataLine(fmt).apply {
                    open(fmt)
                    start()
                    write(buf, 0, buf.size)
                    drain()
                    stop()
                    close()
                }
            }
        }
    }

    // PCM 16-bit mono little-endian. Cada tom ganha attack/release (18% da duracao)
    // pra evitar o "clique" de ligar/desligar a senoide seca.
    private fun render(seq: List<Tone>): ByteArray {
        val total = seq.sumOf { it.ms * RATE / 1000 }
        val out = ByteArray(total * 2)
        var idx = 0
        for (t in seq) {
            val n = t.ms * RATE / 1000
            val fade = (n * 0.18f).toInt().coerceAtLeast(1)
            for (i in 0 until n) {
                val env = when {
                    i < fade -> i.toFloat() / fade
                    i > n - fade -> (n - i).toFloat() / fade
                    else -> 1f
                }
                val s = sin(2.0 * PI * t.hz * i / RATE).toFloat() * t.gain * env
                val v = (s * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
                out[idx++] = (v and 0xFF).toByte()
                out[idx++] = ((v shr 8) and 0xFF).toByte()
            }
        }
        return out
    }
}
