package app.astra.mobile.core.model

import app.astra.mobile.core.network.dto.AttachmentDto
import app.astra.mobile.core.network.dto.ServerStickerDto

data class Attachment(
    val url: String,
    val type: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val blurhash: String? = null,
    val duration: Int? = null,
    val figurinha: Boolean = false,
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
    figurinha = sticker == true,
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
    sticker = figurinha.takeIf { it },
)

fun ServerStickerDto.comoAnexo(): Attachment {
    val extensao = url.substringAfterLast('.', "png").substringBefore('?').lowercase()
    return Attachment(
        url = url,
        type = "image/" + if (extensao.length in 2..4) extensao else "png",
        name = name,
        width = width.takeIf { it > 0 },
        height = height.takeIf { it > 0 },
        figurinha = true,
    )
}
