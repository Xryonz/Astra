package app.astra.client;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground service de chamada: segura o processo vivo enquanto há call
 * ativa com o app em background — sem ele o Android congela o WebView e a
 * call de áudio cai em segundos. A notificação persistente ("Em chamada")
 * é exigência do sistema pra qualquer foreground service.
 *
 * Ligado/desligado pelo CallServicePlugin (web: lib/native.ts setCallActive,
 * chamado pelo voiceStore ao entrar/sair de call).
 */
public class CallService extends Service {
    public static final String CHANNEL_ID = "calls";
    private static final int NOTIF_ID = 7001;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Chamadas", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Chamada de voz em andamento");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Astra")
                .setContentText("Em chamada — toque para voltar")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_ID, n);
        }
        return START_NOT_STICKY;
    }
}
