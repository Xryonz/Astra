package app.astra.mobile.feature.server.presentation

data class ServerBadgeUi(
    val id: String,
    val name: String,
    val icon: String,
    val color: String?,
    val description: String?,
    val grantedUserIds: Set<String>,
)

data class BadgeMemberUi(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
)

data class ServerEditUiState(
    val loading: Boolean = true,
    val name: String = "",
    val iconUrl: String = "",
    val isPublic: Boolean = false,
    val origName: String = "",
    val origIcon: String = "",
    val origPublic: Boolean = false,
    val uploadingIcon: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val dirty: Boolean
        get() = name.trim() != origName || iconUrl != origIcon || isPublic != origPublic
}
