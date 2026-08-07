package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// Figurinha da constelacao.
@Serializable
data class ServerStickerDto(
    val id: String = "",
    val serverId: String = "",
    val name: String = "",
    val url: String = "",
    // Gravados no cadastro pra conversa reservar o espaco ANTES de a imagem
    // chegar. Sem eles a linha nasce com altura zero e empurra o papo pra baixo
    // quando a figurinha carrega — quem estava lendo perde a linha.
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
