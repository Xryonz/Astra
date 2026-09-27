package app.astra.mobile.ui.components

import android.content.Context
import android.net.Uri

fun readImageBytes(ctx: Context, uri: Uri?): Triple<ByteArray, String, String>? {
    if (uri == null) return null
    val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
    val bytes = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return null
    return Triple(bytes, mime, "upload.${imageExt(mime)}")
}

fun ehGif(mime: String): Boolean = mime.substringBefore(';').trim().equals("image/gif", ignoreCase = true)

fun imageExt(mime: String): String = when (mime.substringBefore(';').trim().lowercase()) {
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/png" -> "png"
    "image/avif" -> "avif"
    else -> "jpg"
}
