package app.astra.mobile.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.astra.mobile.BuildConfig
import app.astra.mobile.R

class DepoisDeAtualizar : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!NotificationManagerCompat.from(contexto).areNotificationsEnabled()) return
        garantirCanal(contexto)

        val itens = Novidades.ler(contexto).orEmpty()
        val abrir = contexto.packageManager.getLaunchIntentForPackage(contexto.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val aoTocar = abrir?.let {
            PendingIntent.getActivity(contexto, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val aviso = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_stat_astra)
            .setContentTitle("Astra atualizado para ${BuildConfig.VERSION_NAME}")
            .setContentText(itens.firstOrNull() ?: "Toque para ver o que mudou.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(itens.joinToString("\n") { "· $it" }.ifEmpty { null }))
            .setContentIntent(aoTocar)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(contexto).notify(Novidades.AVISO_ID, aviso) }
    }

    private fun garantirCanal(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gerente = contexto.getSystemService(NotificationManager::class.java) ?: return
        if (gerente.getNotificationChannel(CANAL) != null) return
        gerente.createNotificationChannel(
            NotificationChannel(CANAL, "Atualizações", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quando o Astra se atualiza sozinho"
            },
        )
    }

    private companion object {
        const val CANAL = "atualizacoes"
    }
}
