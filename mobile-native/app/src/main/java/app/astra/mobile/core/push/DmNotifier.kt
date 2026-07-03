package app.astra.mobile.core.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import app.astra.mobile.MainActivity
import app.astra.mobile.R

// Notificacao conversacional de DM (MessagingStyle): avatar + historico da
// conversa + campo "Responder" direto na bandeja, como WhatsApp/Discord.
// Compartilhado entre o service FCM (mensagem recebida) e o ReplyReceiver
// (eco da propria resposta, que encerra o spinner do RemoteInput).
object DmNotifier {
    const val KEY_REPLY = "astra_reply"
    const val EXTRA_CONV_ID = "conversationId"

    fun notifId(conversationId: String) = "dm-$conversationId".hashCode()

    fun show(
        context: Context,
        conversationId: String,
        text: String,
        sender: String?, // null/blank = mensagem do proprio user (eco do reply)
        senderIcon: IconCompat?,
    ) {
        PushChannels.ensure(context)
        val id = notifId(conversationId)

        // Continua a conversa ja aberta na bandeja em vez de empilhar avisos.
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = mgr.activeNotifications.firstOrNull { it.id == id }?.notification
        val me = Person.Builder().setName("Você").build()
        val style = existing
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(me)

        val person = if (sender.isNullOrBlank()) {
            null // null = mensagem do proprio user
        } else {
            Person.Builder().setName(sender).setIcon(senderIcon).build()
        }
        style.addMessage(text, System.currentTimeMillis(), person)

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel("Responder").build()
        val replyPending = PendingIntent.getBroadcast(
            context, id,
            Intent(context, ReplyReceiver::class.java).putExtra(EXTRA_CONV_ID, conversationId),
            // MUTABLE: o sistema escreve o texto do RemoteInput dentro do intent.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyAction = NotificationCompat.Action.Builder(R.drawable.ic_stat_astra, "Responder", replyPending)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val built = NotificationCompat.Builder(context, PushChannels.DMS)
            .setSmallIcon(R.drawable.ic_stat_astra)
            .setColor(0xFFC9A96E.toInt())
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(replyAction)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, built)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS negada — nada a fazer.
        }
    }
}
