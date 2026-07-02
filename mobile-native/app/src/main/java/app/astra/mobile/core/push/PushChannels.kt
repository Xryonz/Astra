package app.astra.mobile.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

// Canais em paridade com o web/Capacitor (pushNative.ts) e com o backend
// (lib/fcm.ts escolhe channelId = mentions | dms | general).
object PushChannels {
    const val MENTIONS = "mentions"
    const val DMS = "dms"
    const val GENERAL = "general"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return

        fun channel(id: String, name: String, desc: String, importance: Int, vibrate: Boolean) {
            if (mgr.getNotificationChannel(id) != null) return
            mgr.createNotificationChannel(
                NotificationChannel(id, name, importance).apply {
                    description = desc
                    enableVibration(vibrate)
                },
            )
        }

        channel(MENTIONS, "Menções", "Quando te citam numa órbita", NotificationManager.IMPORTANCE_HIGH, true)
        channel(DMS, "Sussurros", "Mensagens diretas novas", NotificationManager.IMPORTANCE_HIGH, true)
        channel(GENERAL, "Geral", "Outras notificações do Astra", NotificationManager.IMPORTANCE_DEFAULT, false)
    }
}
