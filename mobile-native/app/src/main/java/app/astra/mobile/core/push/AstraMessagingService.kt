package app.astra.mobile.core.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.astra.mobile.MainActivity
import app.astra.mobile.R
import app.astra.mobile.core.network.NotificationsApi
import app.astra.mobile.core.network.dto.FcmTokenRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AstraMessagingService : FirebaseMessagingService() {

    @Inject lateinit var api: NotificationsApi

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Token novo/rotacionado -> registra no backend (que envia via firebase-admin).
    // Se o user nao estiver logado o POST da 401 e morre em silencio; o registro
    // certo acontece no login via PushRegistrar.
    override fun onNewToken(token: String) {
        scope.launch {
            try { api.registerFcmToken(FcmTokenRequest(token)) } catch (_: Exception) {}
        }
    }

    // So dispara com o app em FOREGROUND (em background o sistema exibe sozinho a
    // notification-block do backend, usando o canal que ele mandou). Aqui exibimos
    // manualmente pra nao engolir o aviso.
    override fun onMessageReceived(message: RemoteMessage) {
        val notif = message.notification ?: return
        PushChannels.ensure(this)

        val channelId = notif.channelId ?: when {
            message.data["dmConvId"].orEmpty().isNotEmpty() -> PushChannels.DMS
            else -> PushChannels.GENERAL
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val built = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_astra)
            .setColor(0xFFC9A96E.toInt())
            .setContentTitle(notif.title)
            .setContentText(notif.body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), built)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS negada — nada a fazer.
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
