package app.astra.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.astra.mobile.feature.auth.domain.AuthRepository
import app.astra.mobile.ui.AstraApp
import app.astra.mobile.ui.theme.AstraTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Sem Scaffold: o CosmicBackground de cada tela pinta edge-to-edge
        // (atras da status/nav bar). As telas aplicam statusBarsPadding/
        // navigationBarsPadding no conteudo (Home e EditorialTopBar).
        setContent {
            AstraTheme {
                AstraApp()
            }
        }
        handleDeepLink(intent)
    }

    // launchMode=singleTask -> o deep link com o app aberto cai aqui.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    // OAuth Google: o backend redireciona pro deep link
    //   sucesso -> astra://auth/callback#refresh=<token>
    //   falha   -> astra://login?error=<code>
    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "astra") return
        when (data.host) {
            "auth" -> {
                val refresh = data.fragment
                    ?.substringAfter("refresh=", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                val token = Uri.decode(refresh)
                lifecycleScope.launch {
                    // Sucesso: isLoggedIn vira true -> AstraApp navega pra Home sozinho.
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
