package app.astra.mobile.feature.profile.presentation

data class AccountUiState(
    val loading: Boolean = true,
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val userId: String = "",
    val hasPassword: Boolean = true,
    val origDisplayName: String = "",
    val origUsername: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,

    val pwOpen: Boolean = false,
    val curPw: String = "",
    val newPw: String = "",
    val pwSaving: Boolean = false,
    val pwError: String? = null,
    val pwDone: Boolean = false,
) {
    val dirty: Boolean get() = displayName != origDisplayName || username != origUsername
}
