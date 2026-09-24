package app.astra.mobile.core.upload

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SomConvertido(val wav: ByteArray, val duracaoMs: Int)

object ConversorDeSom {
    private const val SEGUNDOS_MAXIMOS = 120
    private const val ESPERA_US = 10_000L
    private const val CABECALHO = 44

    fun paraWav(contexto: Context, uri: Uri): SomConvertido? {
        val leitor = MediaExtractor()
        return runCatching {
            leitor.setDataSource(contexto, uri, null)
            val faixa = (0 until leitor.trackCount).firstOrNull { i ->
                leitor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("arquivo sem áudio")

            leitor.selectTrack(faixa)
            val formato = leitor.getTrackFormat(faixa)
            val codec = MediaCodec.createDecoderByType(formato.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(formato, null, null, 0)
            codec.start()

            var taxa = formato.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var canais = formato.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val teto = taxa.toLong() * canais * 2 * SEGUNDOS_MAXIMOS
            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var acabou = false

            while (!acabou) {
                val entrada = codec.dequeueInputBuffer(ESPERA_US)
                if (entrada >= 0) {
                    val balde = codec.getInputBuffer(entrada)!!
                    val lido = leitor.readSampleData(balde, 0)
                    if (lido < 0) {
                        codec.queueInputBuffer(entrada, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        codec.queueInputBuffer(entrada, 0, lido, leitor.sampleTime, 0)
                        leitor.advance()
                    }
                }
                when (val saida = codec.dequeueOutputBuffer(info, ESPERA_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val novo = codec.outputFormat
                        taxa = novo.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        canais = novo.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (saida >= 0) {
                        val balde = codec.getOutputBuffer(saida)!!
                        if (info.size > 0) {
                            val pedaco = ByteArray(info.size)
                            balde.position(info.offset)
                            balde.get(pedaco)
                            pcm.write(pedaco)
                        }
                        codec.releaseOutputBuffer(saida, false)
                        if (pcm.size() > teto) error("som acima de ${SEGUNDOS_MAXIMOS / 60} minutos")
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) acabou = true
                    }
                }
            }

            codec.stop()
            codec.release()

            val bruto = pcm.toByteArray()
            if (bruto.isEmpty()) error("não consegui ler esse arquivo")
            val duracaoMs = (bruto.size.toLong() * 1_000 / (taxa.toLong() * canais * 2)).toInt()
            SomConvertido(comCabecalhoDeWav(bruto, taxa, canais), duracaoMs)
        }.also { leitor.release() }.getOrNull()
    }

    private fun comCabecalhoDeWav(pcm: ByteArray, taxa: Int, canais: Int): ByteArray {
        val bytesPorSegundo = taxa * canais * 2
        val cabecalho = ByteBuffer.allocate(CABECALHO).order(ByteOrder.LITTLE_ENDIAN)
        cabecalho.put("RIFF".toByteArray())
        cabecalho.putInt(36 + pcm.size)
        cabecalho.put("WAVE".toByteArray())
        cabecalho.put("fmt ".toByteArray())
        cabecalho.putInt(16)
        cabecalho.putShort(1)
        cabecalho.putShort(canais.toShort())
        cabecalho.putInt(taxa)
        cabecalho.putInt(bytesPorSegundo)
        cabecalho.putShort((canais * 2).toShort())
        cabecalho.putShort(16)
        cabecalho.put("data".toByteArray())
        cabecalho.putInt(pcm.size)
        return cabecalho.array() + pcm
    }
}
