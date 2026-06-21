package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// POST /api/upload -> { data: { attachments: [{ url, type, ... }] } }
@Serializable
data class UploadResponse(val attachments: List<UploadedAttachmentDto> = emptyList())

@Serializable
data class UploadedAttachmentDto(
    val url: String,
    val type: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)
