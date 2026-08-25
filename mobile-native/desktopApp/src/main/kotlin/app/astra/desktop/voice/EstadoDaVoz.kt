package app.astra.desktop.voice

sealed interface VoiceStatus {
    data object Connecting : VoiceStatus

    data class Connected(
        val others: List<VoiceParticipant>,
        val audioLive: Boolean = false,
        val mySpeaking: Boolean = false,
    ) : VoiceStatus

    data class Failed(val reason: String) : VoiceStatus
    data object Closed : VoiceStatus
}

data class VoiceParticipant(
    val identity: String,
    val label: String,
    val speaking: Boolean,
    val avatarUrl: String? = null,
)
