package app.astra.mobile.feature.server.presentation

data class ServerEditUiState(
    val loading: Boolean = true,
    val name: String = "",
    val iconUrl: String = "",
    val origName: String = "",
    val origIcon: String = "",
    val uploadingIcon: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val dirty: Boolean
        get() = name.trim() != origName || iconUrl != origIcon
}
