package app.astra.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

// D0: hello obsidiana. Prova que o toolchain Compose Desktop builda e roda ao
// lado do :app Android sem quebrar nada. A cara real (rail/sidebar/palco, aurora
// SkSL, vidro) vem a partir do D4. So foundation (sem material3) pra manter enxuto.
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Astra",
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
    ) {
        AstraDesktopHello()
    }
}

@Composable
private fun AstraDesktopHello() {
    val void = Color(0xFF06060E)
    val raised = Color(0xFF0F0F24)
    val accent = Color(0xFFD4D8E0)
    val dim = Color(0xFF8C8C94)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(void, raised, void))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText("Astra", style = TextStyle(color = accent, fontSize = 44.sp, fontWeight = FontWeight.Light))
            BasicText("desktop · obsidiana", style = TextStyle(color = dim, fontSize = 15.sp))
        }
    }
}
