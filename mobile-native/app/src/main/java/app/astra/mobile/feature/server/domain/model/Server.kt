package app.astra.mobile.feature.server.domain.model

data class Server(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val memberCount: Int,
    val channels: List<Channel>,
)

data class Channel(
    val id: String,
    val name: String,
    val isVoice: Boolean,
)
