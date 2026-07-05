package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchResultsDto(
    val messages: List<SearchMessageDto> = emptyList(),
    val channels: List<SearchChannelDto> = emptyList(),
    val users: List<SearchUserDto> = emptyList(),
    val servers: List<SearchServerDto> = emptyList(),
)

@Serializable
data class SearchMessageDto(
    val id: String,
    val content: String = "",
    val channelId: String,
    val channelName: String = "",
    val serverId: String = "",
    val serverName: String = "",
    val createdAt: String? = null,
    val author: SearchUserDto? = null,
)

@Serializable
data class SearchChannelDto(
    val id: String,
    val name: String,
    val type: String = "TEXT",
    val serverId: String = "",
    val serverName: String = "",
)

@Serializable
data class SearchUserDto(
    val id: String,
    val username: String = "",
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class SearchServerDto(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val isGroup: Boolean = false,
)
