package app.astra.mobile.feature.invite.domain.model

data class InvitePreview(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val isGroup: Boolean,
    val memberCount: Int,
)
