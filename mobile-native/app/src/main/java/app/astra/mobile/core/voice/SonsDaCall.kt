package app.astra.mobile.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

object SonsDaCall {
    private const val TAXA = 44_100

    private class Tom(val hz: Float, val ms: Int, val ganho: Float = 0.26f)

    private val ENTRAR = listOf(Tom(620f, 80), Tom(930f, 150))
    private val SAIR = listOf(Tom(430f, 90), Tom(300f, 175))
    private val COMECAR_A_TRANSMITIR = listOf(Tom(500f, 95), Tom(680f, 95), Tom(920f, 155))
    private val PARAR_DE_TRANSMITIR = listOf(Tom(920f, 95), Tom(680f, 95), Tom(500f, 155))
    private val TOQUE = listOf(Tom(880f, 150), Tom(0f, 90), Tom(1170f, 220), Tom(0f, 2400, 0f))
    private val CHAMANDO = listOf(Tom(392f, 300, 0.10f), Tom(0f, 2200, 0f))

    private val RITMO_DA_VIBRACAO = longArrayOf(0, 400, 300, 400, 1600)

    private val lock = Any()
    private var toqueNoAr: AudioTrack? = null
    private var vibradorNoAr: Vibrator? = null
    private var geracaoDoToque = 0

    fun entrar(ctx: Context) = tocar(ctx, ENTRAR)
    fun sair(ctx: Context) = tocar(ctx, SAIR)
    fun comecarATransmitir(ctx: Context) = tocar(ctx, COMECAR_A_TRANSMITIR)
    fun pararDeTransmitir(ctx: Context) = tocar(ctx, PARAR_DE_TRANSMITIR)

    fun comecarToque(ctx: Context, souEuQueLiguei: Boolean) {
        pararToque()
        val modo = modoDaCampainha(ctx)
        if (modo == AudioManager.RINGER_MODE_SILENT) return
        if (modo == AudioManager.RINGER_MODE_VIBRATE) {
            if (!souEuQueLiguei) vibrar(ctx)
            return
        }
        val uso = if (souEuQueLiguei) AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING
        else AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        val pcm = desenhar(if (souEuQueLiguei) CHAMANDO else TOQUE)
        val minha = synchronized(lock) { geracaoDoToque }
        thread(isDaemon = true, name = "astra-toque") {
            val faixa = runCatching { faixaEstatica(pcm, uso) }.getOrNull() ?: return@thread
            val valeAinda = synchronized(lock) {
                if (geracaoDoToque != minha) return@synchronized false
                runCatching {
                    faixa.setLoopPoints(0, pcm.size, -1)
                    faixa.play()
                }.isSuccess.also { if (it) toqueNoAr = faixa }
            }
            if (!valeAinda) runCatching { faixa.release() }
        }
    }

    fun pararToque() {
        synchronized(lock) {
            geracaoDoToque++
            toqueNoAr?.let { runCatching { it.stop() }; runCatching { it.release() } }
            toqueNoAr = null
            vibradorNoAr?.cancel()
            vibradorNoAr = null
        }
    }

    private fun tocar(ctx: Context, sequencia: List<Tom>) {
        if (modoDaCampainha(ctx) != AudioManager.RINGER_MODE_NORMAL) return
        val pcm = desenhar(sequencia)
        thread(isDaemon = true, name = "astra-som") {
            val faixa = runCatching {
                faixaEstatica(pcm, AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
            }.getOrNull() ?: return@thread
            runCatching {
                faixa.play()
                Thread.sleep(pcm.size * 1000L / TAXA + 60)
            }
            runCatching { faixa.release() }
        }
    }

    private fun faixaEstatica(pcm: ShortArray, uso: Int): AudioTrack {
        val faixa = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(uso)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(TAXA)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        faixa.write(pcm, 0, pcm.size)
        return faixa
    }

    private fun vibrar(ctx: Context) {
        val vibrador = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrador.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrador.vibrate(VibrationEffect.createWaveform(RITMO_DA_VIBRACAO, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrador.vibrate(RITMO_DA_VIBRACAO, 0)
        }
        synchronized(lock) { vibradorNoAr = vibrador }
    }

    private fun modoDaCampainha(ctx: Context): Int =
        ctx.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

    private fun desenhar(sequencia: List<Tom>): ShortArray {
        val total = sequencia.sumOf { it.ms * TAXA / 1000 }
        val saida = ShortArray(total)
        var i = 0
        for (tom in sequencia) {
            val n = tom.ms * TAXA / 1000
            val rampa = (n * 0.18f).toInt().coerceAtLeast(1)
            for (k in 0 until n) {
                val envelope = when {
                    k < rampa -> k.toFloat() / rampa
                    k > n - rampa -> (n - k).toFloat() / rampa
                    else -> 1f
                }
                val onda = sin(2.0 * PI * tom.hz * k / TAXA).toFloat()
                saida[i++] = (onda * tom.ganho * envelope * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return saida
    }
}
