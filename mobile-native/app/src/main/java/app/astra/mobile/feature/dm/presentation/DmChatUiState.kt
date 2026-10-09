package app.astra.mobile.feature.dm.presentation

import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.upload.AndamentoDoVideo
import app.astra.mobile.feature.dm.domain.model.DmMessage
import app.astra.mobile.feature.friends.domain.model.Presence
import app.astra.mobile.ui.components.MensagemPendente
import app.astra.mobile.ui.components.Remetente

data class DmChatUiState(
    val loading: Boolean = true,
    val messages: List<DmMessage> = emptyList(),
    val cursorAnterior: String? = null,
    val carregandoAntigas: Boolean = false,
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,

    val replyToId: String? = null,
    val replyToAuthor: String? = null,
    val replyToPreview: String? = null,
    val typingUsers: List<String> = emptyList(),

    val pendingAttachments: List<Attachment> = emptyList(),
    val uploading: Boolean = false,
    val videoEmPreparo: AndamentoDoVideo? = null,

    val translations: Map<String, String> = emptyMap(),
    val translatingIds: Set<String> = emptySet(),

    val muted: Boolean = false,

    val outroAvatar: String? = null,
    val outroStatus: Presence? = null,

    val pendentes: List<MensagemPendente> = emptyList(),
    val eu: Remetente? = null,
) {
    fun comMensagens(msgs: List<DmMessage>, chegaram: Set<String>): DmChatUiState {
        val restantes = pendentes.filterNot { it.nonce in chegaram }
        if (restantes.size == pendentes.size) return copy(messages = msgs)
        val falhaResolvida = pendentes.any { it.falhou && it.nonce in chegaram }
        return copy(messages = msgs, pendentes = restantes, error = if (falhaResolvida) null else error)
    }
}
