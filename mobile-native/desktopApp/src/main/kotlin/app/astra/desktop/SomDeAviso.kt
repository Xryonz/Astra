package app.astra.desktop

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAXA = 44_100f
private const val NOTA_GRAVE = 660.0
private const val NOTA_AGUDA = 990.0
private const val MS_GRAVE = 75
private const val MS_AGUDA = 105

private const val PICO = 0.18

private const val TRAVA_MS = 1_200L

private val ultimaVez = AtomicLong(0L)

private val toca by lazy {
    Executors.newSingleThreadExecutor { r ->
        Thread(r, "astra-som").apply { isDaemon = true }
    }
}

fun tocarAvisoDeMensagem() {
    val agora = System.currentTimeMillis()
    val anterior = ultimaVez.get()
    if (agora - anterior < TRAVA_MS) return
    if (!ultimaVez.compareAndSet(anterior, agora)) return
    toca.execute { runCatching { emitir(construirOnda()) } }
}

private fun construirOnda(): ByteArray {
    val amostrasGrave = (TAXA * MS_GRAVE / 1000).toInt()
    val amostrasAguda = (TAXA * MS_AGUDA / 1000).toInt()
    val total = amostrasGrave + amostrasAguda
    val saida = ByteArray(total * 2)

    for (i in 0 until total) {
        val naSegunda = i >= amostrasGrave
        val freq = if (naSegunda) NOTA_AGUDA else NOTA_GRAVE
        val posicao = if (naSegunda) i - amostrasGrave else i
        val duracao = if (naSegunda) amostrasAguda else amostrasGrave

        val fase = posicao.toDouble() / duracao
        val envelope = 0.5 * (1.0 - cos(2.0 * PI * fase))

        val valor = sin(2.0 * PI * freq * posicao / TAXA) * envelope * PICO
        val amostra = (valor * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
        saida[i * 2] = (amostra and 0xFF).toByte()
        saida[i * 2 + 1] = ((amostra shr 8) and 0xFF).toByte()
    }
    return saida
}

private fun emitir(pcm: ByteArray) {
    val formato = AudioFormat(TAXA, 16, 1, true, false)
    val linha = AudioSystem.getLine(javax.sound.sampled.DataLine.Info(SourceDataLine::class.java, formato))
        as SourceDataLine
    linha.open(formato)
    linha.start()
    linha.write(pcm, 0, pcm.size)
    linha.drain()
    linha.stop()
    linha.close()
}
