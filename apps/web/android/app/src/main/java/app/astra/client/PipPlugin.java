package app.astra.client;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Plugin mínimo pra Picture-in-Picture: o JS liga a flag quando há vídeo
 * na chamada (câmera/screen share); MainActivity.onUserLeaveHint entra em
 * PiP se a flag estiver ativa quando o user sai do app (home/recents).
 */
@CapacitorPlugin(name = "AstraPip")
public class PipPlugin extends Plugin {
    static volatile boolean pipWanted = false;

    @PluginMethod
    public void setEnabled(PluginCall call) {
        Boolean enabled = call.getBoolean("enabled", false);
        pipWanted = Boolean.TRUE.equals(enabled);
        call.resolve();
    }
}
