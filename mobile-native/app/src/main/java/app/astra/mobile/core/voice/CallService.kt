package app.astra.mobile.core.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.astra.mobile.MainActivity
import app.astra.mobile.R
import app.astra.mobile.core.update.CuidadorDeAtualizacao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CallService : Service() {

    @Inject lateinit var voiceManager: VoiceManager

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        emAndamento = true
        garantirCanal(this)
        ServiceCompat.startForeground(
            this,
            ID_DA_NOTIFICACAO,
            construirNotificacao(this, voiceManager.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
        )
        escopo.launch {
            voiceManager.state
                .map { ResumoDaNotificacao(it.sala?.nome, it.mudo, it.surdo, it.transmitindo) }
                .distinctUntilChanged()
                .collect { resumo ->
                    if (resumo.sala == null) return@collect
                    runCatching {
                        NotificationManagerCompat.from(this@CallService)
                            .notify(ID_DA_NOTIFICACAO, construirNotificacao(this@CallService, voiceManager.state.value))
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACAO_MICROFONE -> voiceManager.alternarMudo()
            ACAO_PARAR_TELA -> voiceManager.pararDeTransmitir()
            ACAO_SAIR -> voiceManager.sair()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        escopo.cancel()
        emAndamento = false
        CuidadorDeAtualizacao.aoTerminarChamada(this)
        super.onDestroy()
    }

    private data class ResumoDaNotificacao(
        val sala: String?,
        val mudo: Boolean,
        val surdo: Boolean,
        val transmitindo: Boolean,
    )

    companion object {
        const val ID_DA_NOTIFICACAO = 4201
        private const val CANAL = "astra_call"
        private const val ACAO_MICROFONE = "app.astra.mobile.call.MICROFONE"
        private const val ACAO_PARAR_TELA = "app.astra.mobile.call.PARAR_TELA"
        private const val ACAO_SAIR = "app.astra.mobile.call.SAIR"
        const val EXTRA_ABRIR_CALL = "abrirCall"

        @Volatile
        var emAndamento = false
            private set

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, CallService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CallService::class.java))
        }

        fun construirNotificacao(ctx: Context, estado: VoiceState): Notification {
            garantirCanal(ctx)
            val sala = estado.sala?.nome.orEmpty()
            val abrir = PendingIntent.getActivity(
                ctx,
                0,
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_ABRIR_CALL, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val titulo = if (estado.transmitindo) "Transmitindo · $sala" else "Em call · $sala"
            val texto = when {
                estado.surdo -> "Sem ouvir e com o microfone fechado"
                estado.mudo -> "Microfone fechado"
                else -> "Microfone aberto"
            }
            return NotificationCompat.Builder(ctx, CANAL)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setSmallIcon(R.drawable.ic_stat_astra)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(abrir)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(0, if (estado.mudo) "Abrir microfone" else "Fechar microfone", acao(ctx, ACAO_MICROFONE, 1))
                .apply {
                    if (estado.transmitindo) addAction(0, "Parar transmissão", acao(ctx, ACAO_PARAR_TELA, 2))
                }
                .addAction(0, "Sair", acao(ctx, ACAO_SAIR, 3))
                .build()
        }

        private fun acao(ctx: Context, acao: String, codigo: Int): PendingIntent =
            PendingIntent.getService(
                ctx,
                codigo,
                Intent(ctx, CallService::class.java).setAction(acao),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun garantirCanal(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val gerente = ctx.getSystemService(NotificationManager::class.java)
            if (gerente.getNotificationChannel(CANAL) != null) return
            gerente.createNotificationChannel(
                NotificationChannel(CANAL, "Chamadas de voz", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
    }
}
