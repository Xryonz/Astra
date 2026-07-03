package app.astra.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import app.astra.mobile.core.deeplink.DeepLinkBus
import app.astra.mobile.core.deeplink.PendingShare
import app.astra.mobile.core.share.DmShortcuts
import app.astra.mobile.core.crash.CrashReporter
import app.astra.mobile.core.crash.CrashScreen
import app.astra.mobile.core.data.AppPrefs
import app.astra.mobile.core.data.PreferencesStore
import app.astra.mobile.feature.auth.domain.AuthRepository
import app.astra.mobile.ui.AstraApp
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.AstraTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import zed.rainxch.rikkaui.components.ui.toast.LocalToastHostState
import zed.rainxch.rikkaui.components.ui.toast.ToastAnimation
import zed.rainxch.rikkaui.components.ui.toast.ToastHost
import zed.rainxch.rikkaui.components.ui.toast.ToastPosition
import zed.rainxch.rikkaui.components.ui.toast.rememberToastHostState
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var preferencesStore: PreferencesStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val prefs by preferencesStore.prefs.collectAsState(initial = AppPrefs())
            AstraTheme(accentId = prefs.accentId, bgId = prefs.bgId) {

                val toastState = rememberToastHostState()
                CompositionLocalProvider(
                    LocalToastHostState provides toastState,
                    LocalAppPrefs provides prefs,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        var crash by remember { mutableStateOf(CrashReporter.read(this@MainActivity)) }
                        if (crash != null) {
                            CrashScreen(
                                trace = crash!!,
                                onDismiss = { CrashReporter.clear(this@MainActivity); crash = null },
                            )
                        } else {
                            AstraApp()
                        }
                        ToastHost(
                            hostState = toastState,
                            modifier = Modifier.navigationBarsPadding().padding(bottom = 72.dp),
                            position = ToastPosition.BottomCenter,
                            animation = ToastAnimation.Scale,
                        )
                    }
                }
            }
        }
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent ?: return

        // Direct Share: sistema entrega ACTION_SEND + EXTRA_SHORTCUT_ID do alvo.
        if (intent.action == Intent.ACTION_SEND) {
            val convId = DmShortcuts.conversationIdFrom(intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID))
            if (convId != null) {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                DeepLinkBus.pendingShare.value = PendingShare(
                    conversationId = convId,
                    name = null,
                    text = intent.getStringExtra(Intent.EXTRA_TEXT),
                    imageUri = stream?.toString(),
                )
            }
            return
        }

        // Atalho do launcher (long-press no icone): intent explicito com extras.
        if (intent.action == Intent.ACTION_VIEW && intent.hasExtra(DmShortcuts.EXTRA_CONV_ID)) {
            DeepLinkBus.pendingShare.value = PendingShare(
                conversationId = intent.getStringExtra(DmShortcuts.EXTRA_CONV_ID),
                name = intent.getStringExtra(DmShortcuts.EXTRA_CONV_NAME),
                text = null,
                imageUri = null,
            )
            return
        }

        val data = intent.data ?: return

        // Convite: https://<api>/i/CODE (link do share). Entrega pro AstraApp via bus.
        if (data.pathSegments.firstOrNull() == "i") {
            data.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                DeepLinkBus.pendingInviteCode.value = it
            }
            return
        }

        if (data.scheme != "astra") return
        when (data.host) {
            "auth" -> {
                val refresh = data.fragment
                    ?.substringAfter("refresh=", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                val token = Uri.decode(refresh)
                lifecycleScope.launch {

                    authRepository.completeGoogleLogin(token).onFailure { e ->
                        Toast.makeText(this@MainActivity, e.message ?: "Falha no login com Google", Toast.LENGTH_LONG).show()
                    }
                }
            }
            "login" -> {
                val error = data.getQueryParameter("error") ?: return
                val msg = when (error) {
                    "google_email_unregistered" ->
                        "Esse Google ainda nao tem conta no Astra. Crie uma conta primeiro."
                    else -> "Nao foi possivel entrar com o Google."
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
