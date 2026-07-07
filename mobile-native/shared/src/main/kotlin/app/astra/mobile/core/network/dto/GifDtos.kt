package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class GifResultDto(
    val id: String,
    val title: String = "gif",
    val preview: String,
    val full: String,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long = 0,
)

@Serializable
data class GifPageDto(
    val results: List<GifResultDto> = emptyList(),
    val next: String? = null,
)

@Serializable
data class GifEnabledDto(val enabled: Boolean = false)
