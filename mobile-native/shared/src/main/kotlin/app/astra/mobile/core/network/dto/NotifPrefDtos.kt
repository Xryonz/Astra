package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// Modos: "all" | "mentions" | "mute". Resolucao no backend: pref explicita do
// canal (mesmo "all") > pref do servidor > "all".
@Serializable
data class ChannelNotifPrefDto(
    val channelId: String,
    val mode: String,
)

@Serializable
data class ServerNotifPrefDto(
    val serverId: String,
    val mode: String,
)

@Serializable
data class NotifModeRequest(
    val mode: String,
)
