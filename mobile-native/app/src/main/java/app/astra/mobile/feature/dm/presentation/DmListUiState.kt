package app.astra.mobile.feature.dm.presentation

import app.astra.mobile.feature.dm.domain.model.Conversation

data class DmListUiState(
    val loading: Boolean = true,
    val conversations: List<Conversation> = emptyList(),
    val error: String? = null,
    val opening: Boolean = false,
    val openError: String? = null,
)
