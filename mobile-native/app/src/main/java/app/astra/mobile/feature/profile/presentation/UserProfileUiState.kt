package app.astra.mobile.feature.profile.presentation

import app.astra.mobile.feature.profile.domain.model.ProfileView
import app.astra.mobile.ui.components.BadgeUi

data class UserProfileUiState(
    val loading: Boolean = true,
    val view: ProfileView? = null,
    val badges: List<BadgeUi> = emptyList(),
    val error: String? = null,
)
