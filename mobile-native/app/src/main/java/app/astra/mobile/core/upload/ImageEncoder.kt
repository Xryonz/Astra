package app.astra.mobile.core.upload

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Base64
import app.astra.mobile.core.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
            val src = decodeOriented(bytes)
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

    // Como toDataUri, mas devolve bytes crus (pra upload multipart, ex: emojis).
    // GIF passa direto se couber no limite (preserva animacao); estatico vira JPEG.
    suspend fun toUploadBytes(
        bytes: ByteArray,
        mime: String,
        maxDimension: Int,
        targetBytes: Int,
        gifRawLimit: Int,
    ): Result<Pair<ByteArray, String>> = withContext(Dispatchers.Default) {
        val base = mime.substringBefore(';').trim().lowercase()
        if (base == "image/gif") {
            return@withContext if (bytes.size <= gifRawLimit) Result.success(bytes to "image/gif")
            else Result.failure(ApiException("GIF muito grande — escolha um menor."))
        }
        try {
            val src = decodeOriented(bytes)
                ?: return@withContext Result.failure(ApiException("Imagem invalida."))
            val scaled = scaleDown(src, maxDimension)
            var quality = 85
            var out = compress(scaled, quality)
            while (out.size > targetBytes && quality > 40) {
                quality -= 15
                out = compress(scaled, quality)
            }
            Result.success(out to "image/jpeg")
        } catch (e: Exception) {
            Result.failure(ApiException("Nao foi possivel processar a imagem."))
        }
    }

    // Decodifica JA aplicando o EXIF orientation. BitmapFactory ignora esse
    // metadata -> fotos de retrato (camera grava orientation=6/8) saiam deitadas.
    private fun decodeOriented(bytes: ByteArray): Bitmap? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            else -> return bmp
        }
        return try {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                .also { if (it != bmp) bmp.recycle() }
        } catch (e: OutOfMemoryError) {
            bmp
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

    private const val TARGET_BYTES = 1_500_000
}
