package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class VoiceTokenRequest(
    val roomKind: String,
    val roomId: String,
)

@Serializable
data class VoiceTokenData(
    val token: String,
    val url: String,
    val roomName: String? = null,
    val identity: String? = null,
)
