package app.astra.mobile.core.push

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.astra.mobile.BuildConfig
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
import java.net.URL
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

    // O backend manda DATA-ONLY: este metodo roda em foreground E background,
    // e o app monta a notificacao sozinho. DM vira conversa (MessagingStyle com
    // avatar + responder na bandeja); o resto e notificacao simples.
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body ?: ""
        PushChannels.ensure(this)

        val dmConvId = data["dmConvId"].orEmpty()
        if (dmConvId.isNotEmpty()) {
            val sender = data["sender"].orEmpty().ifBlank { title }
            // Roda na thread de background do FCM — pode baixar o avatar aqui.
            DmNotifier.show(this, dmConvId, body, sender, loadAvatar(data["icon"].orEmpty()))
            return
        }

        val channelId = data["notifChannel"]
            ?: message.notification?.channelId
            ?: PushChannels.GENERAL

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
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            val id = data["tag"]?.takeIf { it.isNotBlank() }?.hashCode()
                ?: message.messageId.hashCode()
            NotificationManagerCompat.from(this).notify(id, built)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS negada — nada a fazer.
        }
    }

    // Avatar pro Person da conversa (best-effort: falhou = sem icone).
    private fun loadAvatar(url: String): IconCompat? {
        if (url.isBlank()) return null
        val full = if (url.startsWith("/")) BuildConfig.BASE_URL.trimEnd('/') + url else url
        return try {
            val bytes = URL(full).readBytes()
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val scaled = Bitmap.createScaledBitmap(raw, 128, 128, true)
            IconCompat.createWithBitmap(circleCrop(scaled))
        } catch (_: Exception) {
            null
        }
    }

    private fun circleCrop(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = src.width / 2f
        canvas.drawCircle(r, r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
