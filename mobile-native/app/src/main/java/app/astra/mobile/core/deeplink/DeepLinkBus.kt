package app.astra.mobile.core.deeplink

import kotlinx.coroutines.flow.MutableStateFlow

data class PendingShare(
    val conversationId: String?,
    val name: String?,
    val text: String?,
    val imageUri: String?,
)

object DeepLinkBus {
    val pendingInviteCode = MutableStateFlow<String?>(null)
    val pendingShare = MutableStateFlow<PendingShare?>(null)
}
