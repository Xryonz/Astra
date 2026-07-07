package app.astra.mobile.feature.server.domain.model

import androidx.compose.runtime.Immutable

// @Immutable: so era instavel pelas List<> internas (channels/categories); as
// instancias sao recriadas a cada update, nunca mutadas -> promessa verdadeira.
// Destrava skip nos composables que recebem Server (rail, chips do perfil).
@Immutable
data class Server(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val memberCount: Int,
    val onlineCount: Int = 0,
    val channels: List<Channel>,
    val categories: List<Category> = emptyList(),
    val inviteCode: String? = null,
    val ownerId: String? = null,
    val isPublic: Boolean = false,
    val isGroup: Boolean = false,
    val bannerUrl: String? = null,
    val description: String? = null,
    val messageRetentionDays: Int? = null,
)

data class Channel(
    val id: String,
    val name: String,
    val isVoice: Boolean,
    val lastMessageAt: String? = null,
    val categoryId: String? = null,
    val isPrivate: Boolean = false,
)

data class Category(
    val id: String,
    val name: String,
    val position: Int,
)

data class ServerMember(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
)
