package app.astra.client;

import android.content.Intent;
import android.os.Build;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Ponte JS → CallService. Web chama AstraCallService.setActive({active})
 * ao entrar/sair de call (lib/native.ts setCallActive).
 */
@CapacitorPlugin(name = "AstraCallService")
public class CallServicePlugin extends Plugin {

    @PluginMethod
    public void setActive(PluginCall call) {
        boolean active = Boolean.TRUE.equals(call.getBoolean("active", false));
        Intent i = new Intent(getContext(), CallService.class);
        try {
            if (active) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getContext().startForegroundService(i);
                } else {
                    getContext().startService(i);
                }
            } else {
                getContext().stopService(i);
            }
            call.resolve();
        } catch (Exception e) {
            // App em estado que não permite FGS (raro) — call segue sem o service
            call.reject(e.getMessage());
        }
    }
}
