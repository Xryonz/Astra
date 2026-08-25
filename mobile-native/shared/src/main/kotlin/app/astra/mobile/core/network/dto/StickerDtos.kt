package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ServerStickerDto(
    val id: String = "",
    val serverId: String = "",
    val name: String = "",
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class StickersResponse(val stickers: List<ServerStickerDto> = emptyList())

@Serializable
data class CriarFigurinhaRequest(
    val name: String,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
)
