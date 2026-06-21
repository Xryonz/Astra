package app.astra.mobile.feature.server.domain.model

data class Server(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val memberCount: Int,
    val channels: List<Channel>,
    val inviteCode: String? = null,
    val ownerId: String? = null,
)

data class Channel(
    val id: String,
    val name: String,
    val isVoice: Boolean,
    val lastMessageAt: String? = null,
)

data class ServerMember(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
)
