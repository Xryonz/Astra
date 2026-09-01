package app.astra.mobile.core.model

import app.astra.mobile.core.network.dto.AttachmentDto

data class Attachment(
    val url: String,
    val type: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val blurhash: String? = null,
    val duration: Int? = null,
)

private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "bmp", "heic", "heif", "svg")

val Attachment.isImage: Boolean
    get() = type?.startsWith("image/") == true ||
        url.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase() in IMAGE_EXT

val Attachment.isAudio: Boolean
    get() = type?.startsWith("audio/") == true

fun AttachmentDto.toModel() = Attachment(
    url = url,
    type = type,
    name = name,
    size = size,
    width = width,
    height = height,
    blurhash = blurhash,
    duration = duration,
)

fun Attachment.toDto() = AttachmentDto(
    url = url,
    type = type,
    name = name,
    size = size,
    width = width,
    height = height,
    blurhash = blurhash,
    duration = duration,
)
