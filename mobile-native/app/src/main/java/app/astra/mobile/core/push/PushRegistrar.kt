package app.astra.mobile.core.push

import android.content.Context
import app.astra.mobile.core.network.NotificationsApi
import app.astra.mobile.core.network.dto.FcmTokenRequest
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Registra o token FCM no backend quando o user esta logado (chamado da Home,
// depois da permissao POST_NOTIFICATIONS). No-op se o Firebase nao inicializou
// (= sem google-services.json no build) — o app segue normal, so sem push.
@Singleton
class PushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: NotificationsApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun register() {
        if (FirebaseApp.getApps(context).isEmpty()) return
        PushChannels.ensure(context)
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            scope.launch {
                try { api.registerFcmToken(FcmTokenRequest(token)) } catch (_: Exception) {}
            }
        }
    }
}
