package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
fun lembrarTempoDeCall(inicio: Long?): State<String> {
    val texto = remember { mutableStateOf("") }
    LaunchedEffect(inicio) {
        if (inicio == null) {
            texto.value = ""
            return@LaunchedEffect
        }
        while (true) {
            texto.value = formatarDuracao(System.currentTimeMillis() - inicio)
            delay(1000L - (System.currentTimeMillis() - inicio) % 1000L)
        }
    }
    return texto
}

fun formatarDuracao(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val s = total % 60
    val m = (total / 60) % 60
    val h = total / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
