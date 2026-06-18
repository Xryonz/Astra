package app.astra.mobile.feature.dm.domain.model

data class Conversation(
    val id: String,
    val otherUserId: String,
    val otherName: String,
    val otherAvatarUrl: String?,
    val preview: String,
)
