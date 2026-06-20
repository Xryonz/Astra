package app.astra.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.astra.mobile.ui.AstraApp
import app.astra.mobile.ui.theme.AstraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
    }
}
