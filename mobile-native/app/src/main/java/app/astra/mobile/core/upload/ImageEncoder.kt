package app.astra.mobile.core.upload

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import app.astra.mobile.core.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Codifica uma imagem como data URI base64 — o mesmo formato que o app web
 * usa pra avatar/banner/icone (readAsDataURL). Passa na validacao do backend
 * (que so aceita data:image/ ou hosts allowlisted) e exibe sem depender de URL.
 *
 * Foto -> reescala + recomprime JPEG (fica pequeno, ~dezenas de KB).
 * GIF   -> manda inteiro (recomprimir mataria a animacao); rejeita se passar
 *          do limite. Roda fora da main thread.
 */
object ImageEncoder {
    suspend fun toDataUri(
        bytes: ByteArray,
        mime: String,
        maxDimension: Int,
        gifRawLimit: Int,
    ): Result<String> = withContext(Dispatchers.Default) {
        val base = mime.substringBefore(';').trim().lowercase()
        if (base == "image/gif") {
            return@withContext if (bytes.size <= gifRawLimit) {
                Result.success("data:image/gif;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
            } else {
                Result.failure(ApiException("GIF muito grande — escolha um menor."))
            }
        }
        try {
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Result.failure(ApiException("Imagem invalida."))
            val scaled = scaleDown(src, maxDimension)
            var quality = 85
            var out = compress(scaled, quality)
            while (out.size > TARGET_BYTES && quality > 40) {
                quality -= 15
                out = compress(scaled, quality)
            }
            Result.success("data:image/jpeg;base64," + Base64.encodeToString(out, Base64.NO_WRAP))
        } catch (e: Exception) {
            Result.failure(ApiException("Nao foi possivel processar a imagem."))
        }
    }

    private fun compress(bmp: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    private fun scaleDown(bmp: Bitmap, maxDim: Int): Bitmap {
        val largest = maxOf(bmp.width, bmp.height)
        if (largest <= maxDim) return bmp
        val ratio = maxDim.toFloat() / largest
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    private const val TARGET_BYTES = 1_500_000 // ~1.5MB de bytes brutos -> data URI bem abaixo dos limites
}
