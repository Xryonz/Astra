package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class WishAuthorDto(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
)

@Serializable
data class WishDto(
    val id: String,
    val content: String = "",
    val createdAt: String? = null,
    val author: WishAuthorDto? = null,
)

@Serializable
data class WishPageDto(
    val items: List<WishDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class PostWishRequest(val content: String)
