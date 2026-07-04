package app.astra.mobile.feature.dm.domain.model

data class Conversation(
    val id: String,
    val otherUserId: String,
    val otherName: String,
    val otherAvatarUrl: String?,
    val preview: String,
    val lastMessageAt: String? = null,
    val lastFromMe: Boolean = false,
    val muted: Boolean = false,
)
