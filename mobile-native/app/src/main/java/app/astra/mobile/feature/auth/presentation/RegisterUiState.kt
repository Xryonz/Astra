package app.astra.mobile.feature.auth.presentation

data class RegisterUiState(
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)
