package app.astra.mobile.core.deeplink

import kotlinx.coroutines.flow.MutableStateFlow

// Conteudo compartilhado pra um Sussurro (Direct Share ou atalho do launcher).
// text/imageUri nulos = so abrir a conversa.
data class PendingShare(
    val conversationId: String?,
    val name: String?,
    val text: String?,
    val imageUri: String?,
)

// Ponte MainActivity -> AstraApp: a Activity recebe o intent do link e o NavHost
// (que vive no composable) consome quando o user estiver logado.
object DeepLinkBus {
    val pendingInviteCode = MutableStateFlow<String?>(null)
    val pendingShare = MutableStateFlow<PendingShare?>(null)
}
