package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ServerSoundDto(
    val id: String = "",
    val serverId: String = "",
    val name: String = "",
    val url: String = "",
    val durationMs: Int = 0,
)

@Serializable
data class SoundsResponse(val sounds: List<ServerSoundDto> = emptyList())

@Serializable
data class CriarSomRequest(
    val name: String,
    val url: String,
    val durationMs: Int = 0,
)

@Serializable
data class TocarSomRequest(val channelId: String)
