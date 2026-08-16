package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class WishDto(
    val id: String,
    val content: String,
    val createdAt: String? = null,
    val author: WishAuthorDto? = null,
)

@Serializable
data class WishAuthorDto(
    val id: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

// `nextCursor` nulo = acabou o ceu. O cursor e "<iso>__<id>" montado pelo servidor;
// o cliente so devolve o que recebeu, sem tentar entender o formato.
@Serializable
data class WishPageDto(
    val items: List<WishDto> = emptyList(),
    val nextCursor: String? = null,
)
