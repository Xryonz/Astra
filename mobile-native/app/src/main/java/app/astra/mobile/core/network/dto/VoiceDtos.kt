package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// POST /api/voice/token { roomKind, roomId } -> { token, url, roomName, identity }
@Serializable
data class VoiceTokenRequest(
    val roomKind: String, // "channel" | "dm"
    val roomId: String,
)

@Serializable
data class VoiceTokenData(
    val token: String,
    val url: String,
    val roomName: String? = null,
    val identity: String? = null,
)
