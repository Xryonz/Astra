package app.astra.mobile.core.deeplink

import kotlinx.coroutines.flow.MutableStateFlow

// Ponte MainActivity -> AstraApp: a Activity recebe o intent do link e o NavHost
// (que vive no composable) consome quando o user estiver logado.
object DeepLinkBus {
    val pendingInviteCode = MutableStateFlow<String?>(null)
}
