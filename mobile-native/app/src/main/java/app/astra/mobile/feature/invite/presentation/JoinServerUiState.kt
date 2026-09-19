package app.astra.mobile.feature.invite.presentation

import app.astra.mobile.feature.invite.domain.model.InvitePreview

data class JoinServerUiState(
    val code: String = "",
    val loadingPreview: Boolean = false,
    val preview: InvitePreview? = null,
    val previewError: String? = null,
    val joining: Boolean = false,
    val joinError: String? = null,
)
