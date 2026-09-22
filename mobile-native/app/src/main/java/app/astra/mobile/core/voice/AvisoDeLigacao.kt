package app.astra.mobile.core.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.astra.mobile.MainActivity
import app.astra.mobile.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

object AvisoDeLigacao {
    private const val ID = 4203
    private const val CANAL = "astra_ligacoes"
    const val EXTRA_ATENDER = "atenderLigacao"

    fun mostrar(contexto: Context, ligacao: LigacaoNaTela) {
        garantirCanal(contexto)
        val abrir = PendingIntent.getActivity(
            contexto,
            10,
            Intent(contexto, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val atender = PendingIntent.getActivity(
            contexto,
            11,
            Intent(contexto, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ATENDER, ligacao.conversationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val recusar = PendingIntent.getBroadcast(
            contexto,
            12,
            Intent(contexto, RecusarLigacao::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val aviso = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_stat_astra)
            .setContentTitle(ligacao.nome)
            .setContentText("Está te chamando para uma conversa de voz")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(abrir)
            .addAction(0, "Recusar", recusar)
            .addAction(0, "Atender", atender)
            .build()
        runCatching { NotificationManagerCompat.from(contexto).notify(ID, aviso) }
    }

    fun esconder(contexto: Context) {
        NotificationManagerCompat.from(contexto).cancel(ID)
    }

    private fun garantirCanal(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gerente = contexto.getSystemService(NotificationManager::class.java)
        if (gerente.getNotificationChannel(CANAL) != null) return
        gerente.createNotificationChannel(
            NotificationChannel(CANAL, "Ligações", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }
}

@AndroidEntryPoint
class RecusarLigacao : BroadcastReceiver() {
    @Inject lateinit var ligacao: LigacaoDeSussurro

    override fun onReceive(contexto: Context, intent: Intent) {
        ligacao.recusar()
    }
}
