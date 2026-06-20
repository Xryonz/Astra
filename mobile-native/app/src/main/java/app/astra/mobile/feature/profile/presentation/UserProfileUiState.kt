package app.astra.mobile.feature.profile.presentation

import app.astra.mobile.feature.profile.domain.model.ProfileView

data class UserProfileUiState(
    val loading: Boolean = true,
    val view: ProfileView? = null,
    val error: String? = null,
)
