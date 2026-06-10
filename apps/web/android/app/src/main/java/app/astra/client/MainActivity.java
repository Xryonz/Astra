package app.astra.client;

import android.app.PictureInPictureParams;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PipPlugin.class);
        super.onCreate(savedInstanceState);
    }

    /**
     * User saiu do app (home/recents) durante chamada com vídeo →
     * encolhe pra Picture-in-Picture em vez de sumir. Flag controlada
     * pelo JS via PipPlugin.setEnabled (voiceStore liga quando há
     * câmera/screen share na call).
     */
    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (PipPlugin.pipWanted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9))
                        .build());
            } catch (IllegalStateException ignored) {
                // Activity não suporta PiP neste estado — segue normal
            }
        }
    }
}
