package app.astra.mobile.feature.server.domain.model

data class Server(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val memberCount: Int,
    val channels: List<Channel>,
    val categories: List<Category> = emptyList(),
    val inviteCode: String? = null,
    val ownerId: String? = null,
    val isPublic: Boolean = false,
)

data class Channel(
    val id: String,
    val name: String,
    val isVoice: Boolean,
    val lastMessageAt: String? = null,
    val categoryId: String? = null,
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
