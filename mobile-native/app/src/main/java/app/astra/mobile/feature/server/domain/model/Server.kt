package app.astra.mobile.feature.server.domain.model

import androidx.compose.runtime.Immutable

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
    val bannerPositionY: Int = 50,
    val bannerScale: Int = 100,
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
    val position: Int = 0,
    val botAtende: Boolean? = null,
    val guardaAsRespostas: Boolean = true,
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
