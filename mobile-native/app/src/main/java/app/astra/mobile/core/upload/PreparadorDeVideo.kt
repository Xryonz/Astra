package app.astra.mobile.core.upload

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import app.astra.mobile.core.model.Attachment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface PreparoDoVideo {
    data class Pronto(val arquivo: UploadFile, val largura: Int, val altura: Int, val segundos: Int) : PreparoDoVideo {
        fun completar(anexo: Attachment) = anexo.copy(width = largura, height = altura, duration = segundos)
    }
    data class Recusado(val motivo: String) : PreparoDoVideo
}

private data class FichaDoVideo(
    val nome: String,
    val mime: String,
    val bytes: Long,
    val duracaoMs: Long,
    val largura: Int,
    val altura: Int,
) {
    val segundos: Int get() = (duracaoMs / 1000.0).roundToInt().coerceIn(1, 3600)
    val podeIrComoEsta: Boolean get() = mime in TIPOS_QUE_O_SERVIDOR_ACEITA && bytes <= TETO
}

private val TIPOS_QUE_O_SERVIDOR_ACEITA = setOf(MimeTypes.VIDEO_MP4, MimeTypes.VIDEO_WEBM, "video/quicktime")

private const val LADO_MENOR = 720
private const val KBPS = 2500
private const val KBPS_DO_SOM = 128
private const val KBPS_DE_VIDEO_LEVE = 3000
private const val KBPS_MINIMO = 600
private const val TETO = 25L * 1024 * 1024
private const val INTERVALO_DO_ANDAMENTO_MS = 250L
private const val IDADE_DE_SOBRA_MS = 60L * 60 * 1000
private const val PREFIXO_DO_TEMPORARIO = "astra-video-"

internal fun tamanhoDeSaida(largura: Int, altura: Int): Pair<Int, Int> {
    if (largura <= 0 || altura <= 0) return largura to altura
    val menor = min(largura, altura)
    val maior = max(largura, altura)
    val ladoMaior = LADO_MENOR * 16 / 9
    var escala = 1.0
    if (menor > LADO_MENOR) escala = LADO_MENOR.toDouble() / menor
    if (maior * escala > ladoMaior) escala = ladoMaior.toDouble() / maior
    fun par(v: Int): Int {
        val r = (v * escala + 0.5).toInt()
        return max(r - r % 2, 2)
    }
    return par(largura) to par(altura)
}

@Singleton
class PreparadorDeVideo @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val umPorVez = Mutex()

    suspend fun preparar(uri: Uri, andamento: (Float) -> Unit): PreparoDoVideo =
        try {
            prepararArquivo(uri, andamento)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PreparoDoVideo.Recusado("O vídeo não pôde ser preparado. Confira se há espaço livre no aparelho e tente de novo.")
        }

    private suspend fun prepararArquivo(uri: Uri, andamento: (Float) -> Unit): PreparoDoVideo {
        val ficha = withContext(Dispatchers.IO) {
            varrerSobras()
            sondar(uri)
        } ?: return PreparoDoVideo.Recusado("Este vídeo não pôde ser lido. Escolha outro arquivo.")

        val segundos = ficha.duracaoMs / 1000.0
        val kbpsAtual = if (segundos > 0) ficha.bytes * 8 / 1000.0 / segundos else Double.MAX_VALUE
        val leve = min(ficha.largura, ficha.altura) <= LADO_MENOR && kbpsAtual <= KBPS_DE_VIDEO_LEVE
        if (leve && ficha.podeIrComoEsta) return original(uri, ficha)
        val kbps = min(KBPS.toDouble(), kbpsAtual).toInt().coerceAtLeast(KBPS_MINIMO)
        val estimado = (kbps + KBPS_DO_SOM) * 1000L / 8 * ficha.duracaoMs / 1000
        if (estimado > TETO) return longoDemais(ficha)

        val saida = withContext(Dispatchers.IO) { File.createTempFile(PREFIXO_DO_TEMPORARIO, ".mp4", context.cacheDir) }
        val (largura, altura) = tamanhoDeSaida(ficha.largura, ficha.altura)
        try {
            umPorVez.withLock { comprimir(uri, saida, ficha, largura, altura, kbps, andamento) }
        } catch (e: CancellationException) {
            saida.delete()
            throw e
        } catch (e: Exception) {
            saida.delete()
            return semCompressao(uri, ficha)
        }
        return withContext(Dispatchers.IO) {
            try {
                when {
                    saida.length() == 0L -> semCompressao(uri, ficha)
                    saida.length() >= ficha.bytes && ficha.podeIrComoEsta -> original(uri, ficha)
                    saida.length() > TETO -> longoDemais(ficha)
                    else -> PreparoDoVideo.Pronto(
                        UploadFile(saida.readBytes(), MimeTypes.VIDEO_MP4, ficha.nome.substringBeforeLast('.') + ".mp4"),
                        largura,
                        altura,
                        ficha.segundos,
                    )
                }
            } finally {
                saida.delete()
            }
        }
    }

    private suspend fun original(uri: Uri, ficha: FichaDoVideo): PreparoDoVideo = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext PreparoDoVideo.Recusado("Este vídeo não pôde ser lido. Escolha outro arquivo.")
        PreparoDoVideo.Pronto(UploadFile(bytes, ficha.mime, ficha.nome), ficha.largura, ficha.altura, ficha.segundos)
    }

    private suspend fun semCompressao(uri: Uri, ficha: FichaDoVideo): PreparoDoVideo = when {
        ficha.podeIrComoEsta -> original(uri, ficha)
        ficha.bytes <= TETO -> PreparoDoVideo.Recusado(
            "${ficha.nome} não pôde ser convertido. Envie o vídeo em MP4, WebM ou MOV.",
        )
        else -> naoComprimido(ficha)
    }

    private fun varrerSobras() {
        val limite = System.currentTimeMillis() - IDADE_DE_SOBRA_MS
        context.cacheDir.listFiles { f -> f.name.startsWith(PREFIXO_DO_TEMPORARIO) && f.lastModified() < limite }
            ?.forEach { it.delete() }
    }

    private fun longoDemais(ficha: FichaDoVideo) = PreparoDoVideo.Recusado(
        "${ficha.nome} passa de 25 MB mesmo comprimido. Corte para até 1 minuto e 20 segundos e envie de novo.",
    )

    private fun naoComprimido(ficha: FichaDoVideo) = PreparoDoVideo.Recusado(
        "${ficha.nome} não pôde ser comprimido. Envie um vídeo de até 25 MB.",
    )

    private fun sondar(uri: Uri): FichaDoVideo? {
        val (nome, bytes) = runCatching {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
            )?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "video.mp4") to c.getLong(1) else null }
        }.getOrNull() ?: return null
        val leitor = MediaMetadataRetriever()
        return try {
            leitor.setDataSource(context, uri)
            val duracao = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val largura = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val altura = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val deitado = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()?.let { it == 90 || it == 270 } == true
            FichaDoVideo(
                nome = nome,
                mime = context.contentResolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase().orEmpty(),
                bytes = bytes,
                duracaoMs = duracao,
                largura = if (deitado) altura else largura,
                altura = if (deitado) largura else altura,
            ).takeIf { it.bytes > 0 && it.duracaoMs > 0 && it.largura > 0 && it.altura > 0 }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { leitor.release() }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun comprimir(
        uri: Uri,
        saida: File,
        ficha: FichaDoVideo,
        largura: Int,
        altura: Int,
        kbps: Int,
        andamento: (Float) -> Unit,
    ) = withContext(Dispatchers.Main) {
        val efeitos: List<Effect> = if (largura != ficha.largura || altura != ficha.altura) {
            listOf(Presentation.createForWidthAndHeight(largura, altura, Presentation.LAYOUT_SCALE_TO_FIT))
        } else {
            emptyList()
        }
        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(emptyList(), efeitos))
            .build()
        @Suppress("DEPRECATION")
        val sequencia = EditedMediaItemSequence.Builder(listOf(item)).build()
        val composicao = Composition.Builder(sequencia)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
        val fim = CompletableDeferred<Unit>()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(kbps * 1000).build())
                    .build(),
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    fim.complete(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    fim.completeExceptionally(exportException)
                }
            })
            .build()
        transformer.start(composicao, saida.absolutePath)
        val vigia = launch {
            val marca = ProgressHolder()
            while (true) {
                delay(INTERVALO_DO_ANDAMENTO_MS)
                if (transformer.getProgress(marca) == Transformer.PROGRESS_STATE_AVAILABLE) andamento(marca.progress / 100f)
            }
        }
        try {
            fim.await()
        } catch (e: CancellationException) {
            transformer.cancel()
            throw e
        } finally {
            vigia.cancel()
        }
    }
}
