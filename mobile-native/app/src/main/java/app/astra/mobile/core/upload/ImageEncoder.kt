package app.astra.mobile.core.upload

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.os.Build
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
                ?: return@withContext Result.failure(ApiException("Imagem inválida."))
            val out = compressUnder(scaleDown(src, maxDimension), TARGET_BYTES)
            Result.success("data:image/webp;base64," + Base64.encodeToString(out, Base64.NO_WRAP))
        } catch (e: Exception) {
            Result.failure(ApiException("Não foi possível processar a imagem."))
        }
    }

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
                ?: return@withContext Result.failure(ApiException("Imagem inválida."))
            Result.success(compressUnder(scaleDown(src, maxDimension), targetBytes) to "image/webp")
        } catch (e: Exception) {
            Result.failure(ApiException("Não foi possível processar a imagem."))
        }
    }

    suspend fun aspectRatio(bytes: ByteArray): Float? = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val ratio = bounds.outWidth.toFloat() / bounds.outHeight
        if (readOrientation(bytes) in SIDEWAYS) 1f / ratio else ratio
    }

    private fun readOrientation(bytes: ByteArray): Int = try {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    suspend fun larguraOriginal(bytes: ByteArray): Int? = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        bounds.outWidth.takeIf { it > 0 }
    }

    suspend fun decodeForCrop(bytes: ByteArray, maxDimension: Int): Bitmap? = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        runCatching {
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@runCatching null
            val smaller = scaleDown(decoded, maxDimension).also { if (it !== decoded) decoded.recycle() }
            orient(smaller, readOrientation(bytes))
        }.getOrNull()
    }

    suspend fun cropToDataUri(src: Bitmap, crop: Rect, maxDimension: Int): Result<String> =
        withContext(Dispatchers.Default) {
            try {
                val left = crop.left.coerceIn(0, src.width - 1)
                val top = crop.top.coerceIn(0, src.height - 1)
                val width = crop.width().coerceIn(1, src.width - left)
                val height = crop.height().coerceIn(1, src.height - top)
                val cut = scaleDown(Bitmap.createBitmap(src, left, top, width, height), maxDimension)
                Result.success("data:image/webp;base64," + Base64.encodeToString(compressUnder(cut, TARGET_BYTES), Base64.NO_WRAP))
            } catch (e: Exception) {
                Result.failure(ApiException("Não foi possível recortar a imagem. Tente de novo."))
            } catch (e: OutOfMemoryError) {
                Result.failure(ApiException("A imagem é grande demais para recortar. Escolha uma menor."))
            }
        }

    private fun decodeOriented(bytes: ByteArray): Bitmap? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return orient(bmp, readOrientation(bytes))
    }

    private fun orient(bmp: Bitmap, orientation: Int): Bitmap {
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

    private fun compressUnder(bmp: Bitmap, targetBytes: Int): ByteArray {
        var quality = 85
        var out = compress(bmp, quality)
        while (out.size > targetBytes && quality > 40) {
            quality -= 15
            out = compress(bmp, quality)
        }
        return out
    }

    private fun compress(bmp: Bitmap, quality: Int): ByteArray {
        val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
        return ByteArrayOutputStream().also { bmp.compress(fmt, quality, it) }.toByteArray()
    }

    private fun scaleDown(bmp: Bitmap, maxDim: Int): Bitmap {
        val largest = maxOf(bmp.width, bmp.height)
        if (largest <= maxDim) return bmp
        val ratio = maxDim.toFloat() / largest
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    private const val TARGET_BYTES = 1_500_000
    private val SIDEWAYS = setOf(
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE,
    )
}
