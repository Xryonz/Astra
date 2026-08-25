package app.astra.desktop.voice

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object Sfx {
    private const val RATE = 44100

    private data class Tone(val hz: Float, val ms: Int, val gain: Float = 0.26f, val sino: Boolean = false)

    fun callJoin()   = play(listOf(Tone(620f, 80), Tone(930f, 150)))
    fun callLeave()  = play(listOf(Tone(430f, 90), Tone(300f, 175)))
    fun shareStart() = play(listOf(Tone(500f, 95), Tone(680f, 95), Tone(920f, 155)))
    fun shareStop()  = play(listOf(Tone(920f, 95), Tone(680f, 95), Tone(500f, 155)))

    fun aviso() = play(
        listOf(
            Tone(880f, 110, gain = 0.20f, sino = true),
            Tone(1108.7f, 240, gain = 0.18f, sino = true),
        ),
    )

    fun carinho() = play(
        listOf(
            Tone(392f, 70, gain = 0.11f, sino = true),
            Tone(523.3f, 130, gain = 0.09f, sino = true),
        ),
    )

    @Volatile private var tocando = false

    private val TOQUE = listOf(
        Tone(880f, 150), Tone(0f, 90), Tone(1170f, 220), Tone(0f, 2400, gain = 0f),
    )
    private val CHAMANDO = listOf(
        Tone(392f, 300, gain = 0.10f), Tone(0f, 2200, gain = 0f),
    )

    fun ringStart(souEuQueLiguei: Boolean) {
        if (tocando) return
        tocando = true
        val seq = if (souEuQueLiguei) CHAMANDO else TOQUE
        thread(isDaemon = true, name = "astra-ring") {
            runCatching {
                val fmt = AudioFormat(RATE.toFloat(), 16, 1, true, false)
                val buf = render(seq)
                AudioSystem.getSourceDataLine(fmt).apply {
                    open(fmt)
                    start()
                    while (tocando) write(buf, 0, buf.size)
                    stop()
                    close()
                }
            }
            tocando = false
        }
    }

    fun ringStop() { tocando = false }

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

    private fun render(seq: List<Tone>): ByteArray {
        val total = seq.sumOf { it.ms * RATE / 1000 }
        val out = ByteArray(total * 2)
        var idx = 0
        for (t in seq) {
            val n = t.ms * RATE / 1000
            val fade = (n * 0.18f).toInt().coerceAtLeast(1)
            val ataqueN = (RATE * 0.004f).coerceAtLeast(1f)
            val soltaN = (RATE * 0.003f).coerceAtLeast(1f)
            for (i in 0 until n) {
                val env = if (t.sino) {
                    val ataque = (i / ataqueN).coerceAtMost(1f)
                    val queda = exp(-3.2f * i / n)
                    val solta = ((n - i) / soltaN).coerceAtMost(1f)
                    ataque * queda * solta
                } else when {
                    i < fade -> i.toFloat() / fade
                    i > n - fade -> (n - i).toFloat() / fade
                    else -> 1f
                }
                val fase = 2.0 * PI * t.hz * i / RATE
                val onda = if (t.sino) (sin(fase) + 0.22 * sin(4.0 * fase)) / 1.22 else sin(fase)
                val s = onda.toFloat() * t.gain * env
                val v = (s * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
                out[idx++] = (v and 0xFF).toByte()
                out[idx++] = ((v shr 8) and 0xFF).toByte()
            }
        }
        return out
    }
}
