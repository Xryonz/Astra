package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class UploadResponse(val attachments: List<AttachmentDto> = emptyList())

@Serializable
data class AttachmentDto(
    val url: String,
    val type: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val blurhash: String? = null,
    val duration: Int? = null,
)
