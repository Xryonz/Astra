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

// Presets de retencao (0 = pra sempre). Espelha o web (forever/1/7/30/90/365).
val RETENTION_PRESETS: List<Pair<Int, String>> = listOf(
    0 to "Pra sempre",
    1 to "24 horas",
    7 to "7 dias",
    30 to "30 dias",
    90 to "90 dias",
    365 to "1 ano",
)

data class ServerEditUiState(
    val loading: Boolean = true,
    val name: String = "",
    val iconUrl: String = "",
    val bannerUrl: String = "",
    val description: String = "",
    val retentionDays: Int = 0,
    val isPublic: Boolean = false,
    val inviteCode: String? = null,
    val origName: String = "",
    val origIcon: String = "",
    val origBanner: String = "",
    val origDescription: String = "",
    val origRetention: Int = 0,
    val origPublic: Boolean = false,
    val uploadingIcon: Boolean = false,
    val uploadingBanner: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val regenerating: Boolean = false,
    val error: String? = null,
) {
    val dirty: Boolean
        get() = name.trim() != origName ||
            iconUrl != origIcon ||
            bannerUrl != origBanner ||
            description.trim() != origDescription ||
            retentionDays != origRetention ||
            isPublic != origPublic
}
