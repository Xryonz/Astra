package app.astra.desktop.voice

import javax.sound.sampled.AudioFormat
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object Sfx {
    private const val RATE = 44100

    private data class Tone(val hz: Float, val ms: Int, val gain: Float = 0.26f, val sino: Boolean = false)

    private val FORMATO = AudioFormat(RATE.toFloat(), 16, 1, true, false)

    private val ENTRADA = som(Tone(620f, 80), Tone(930f, 150))
    private val SAIDA = som(Tone(430f, 90), Tone(300f, 175))
    private val TELA_LIGADA = som(Tone(500f, 95), Tone(680f, 95), Tone(920f, 155))
    private val TELA_DESLIGADA = som(Tone(920f, 95), Tone(680f, 95), Tone(500f, 155))
    private val AVISO = som(
        Tone(880f, 110, gain = 0.20f, sino = true),
        Tone(1108.7f, 240, gain = 0.18f, sino = true),
    )
    private val CARINHO = som(
        Tone(392f, 70, gain = 0.11f, sino = true),
        Tone(523.3f, 130, gain = 0.09f, sino = true),
    )
    private val TOQUE = som(Tone(880f, 150), Tone(0f, 90), Tone(1170f, 220), Tone(0f, 2400, gain = 0f))
    private val CHAMANDO = som(Tone(392f, 300, gain = 0.10f), Tone(0f, 2200, gain = 0f))

    fun callJoin() = ENTRADA.tocar()
    fun callLeave() = SAIDA.tocar()
    fun shareStart() = TELA_LIGADA.tocar()
    fun shareStop() = TELA_DESLIGADA.tocar()

    fun aviso() = AVISO.tocar()

    fun carinho() = CARINHO.tocar()

    @Volatile private var tocando = false

    fun ringStart(souEuQueLiguei: Boolean) {
        if (tocando) return
        tocando = true
        (if (souEuQueLiguei) CHAMANDO else TOQUE).repetir()
    }

    fun ringStop() {
        tocando = false
        TOQUE.parar()
        CHAMANDO.parar()
    }

    private fun som(vararg sequencia: Tone) = SomPronto(FORMATO) { render(sequencia.asList()) }

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
