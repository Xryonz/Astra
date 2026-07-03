package app.astra.mobile.core.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.dto.SendDmRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

// "Responder" da notificacao: pega o texto do RemoteInput e manda pela API.
// Repostar a notificacao com a propria mensagem e obrigatorio — sem isso o
// Android deixa o spinner do reply girando pra sempre.
@AndroidEntryPoint
class ReplyReceiver : BroadcastReceiver() {

    @Inject lateinit var dmApi: DmApi

    override fun onReceive(context: Context, intent: Intent) {
        val convId = intent.getStringExtra(DmNotifier.EXTRA_CONV_ID) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(DmNotifier.KEY_REPLY)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dmApi.send(convId, SendDmRequest(content = text))
                DmNotifier.show(context, convId, text, sender = null, senderIcon = null)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Falhou (offline?): deixa como esta; o user abre o app e ve.
            } finally {
                pending.finish()
            }
        }
    }
}
