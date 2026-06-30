package app.astra.mobile.core.crash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tela de diagnostico: aparece no launch seguinte a um crash, com o stacktrace pra copiar. */
@Composable
fun CrashScreen(trace: String, onDismiss: () -> Unit) {
    val clip = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B14))
            .systemBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("O app travou aqui", color = Color(0xFFE8C98A), fontSize = 22.sp)
        Text(
            "Copia esse erro e me manda — e o que eu preciso pra corrigir de vez.",
            color = Color(0xFFAAB0C0),
            fontSize = 13.sp,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF15151F))
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(trace, color = Color(0xFFD0D0D8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { clip.setText(AnnotatedString(trace)) }) { Text("Copiar erro") }
            OutlinedButton(onClick = onDismiss) { Text("Continuar") }
        }
    }
}
