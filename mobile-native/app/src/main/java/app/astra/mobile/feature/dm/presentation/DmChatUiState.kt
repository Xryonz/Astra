package app.astra.mobile.feature.dm.presentation

import app.astra.mobile.feature.dm.domain.model.DmMessage

data class DmChatUiState(
    val loading: Boolean = true,
    val messages: List<DmMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
)
