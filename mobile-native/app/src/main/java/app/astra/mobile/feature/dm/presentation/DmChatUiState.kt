package app.astra.mobile.feature.dm.presentation

import app.astra.mobile.core.model.Attachment
import app.astra.mobile.feature.dm.domain.model.DmMessage

data class DmChatUiState(
    val loading: Boolean = true,
    val messages: List<DmMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,

    val replyToId: String? = null,
    val replyToAuthor: String? = null,
    val replyToPreview: String? = null,
    val typingUsers: List<String> = emptyList(),

    val pendingAttachments: List<Attachment> = emptyList(),
    val uploading: Boolean = false,

    val translations: Map<String, String> = emptyMap(),
    val translatingIds: Set<String> = emptySet(),

    // Ligacao em sussurro: true enquanto o convite toca no outro lado (ate 30s).
    val ringing: Boolean = false,

    // Conversa silenciada pelo MEU lado (sem push/badge; mensagens continuam).
    val muted: Boolean = false,
)
