package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

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

@Serializable
data class AvisosDaContaDto(
    val mentions: Boolean = true,
    val dms: Boolean = true,
    val reactions: Boolean = true,
    val replies: Boolean = true,
    val desktop: Boolean = true,
    val quietStart: Int? = null,
    val quietEnd: Int? = null,
)

@Serializable
data class AvisosDaContaResposta(
    val prefs: AvisosDaContaDto = AvisosDaContaDto(),
)

@Serializable
data class AvisosDaContaRequest(
    val mentions: Boolean,
    val dms: Boolean,
    val reactions: Boolean,
    val replies: Boolean,
    val desktop: Boolean,
    val quietStart: Int,
    val quietEnd: Int,
) {
    companion object {
        const val SEM_HORA = -1

        fun de(p: AvisosDaContaDto) = AvisosDaContaRequest(
            mentions = p.mentions,
            dms = p.dms,
            reactions = p.reactions,
            replies = p.replies,
            desktop = p.desktop,
            quietStart = p.quietStart ?: SEM_HORA,
            quietEnd = p.quietEnd ?: SEM_HORA,
        )
    }
}
