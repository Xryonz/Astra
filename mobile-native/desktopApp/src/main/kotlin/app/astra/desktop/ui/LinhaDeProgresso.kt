package app.astra.desktop.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val ALTURA = 2.dp
private const val ESPERA_ATE_VALER_A_LINHA_MS = 160L

@Composable
private fun esperaLongaOBastante(carregando: Boolean): Boolean {
    var passouDoLimiar by remember { mutableStateOf(false) }
    LaunchedEffect(carregando) {
        if (!carregando) {
            passouDoLimiar = false
        } else {
            delay(ESPERA_ATE_VALER_A_LINHA_MS)
            passouDoLimiar = true
        }
    }
    return carregando && passouDoLimiar
}

@Composable
fun LinhaDeProgresso(modifier: Modifier = Modifier) {
    EsperaDoKit(ALTURA, modifier.fillMaxWidth(), trilho = Color.Transparent)
}

@Composable
fun EsperaNoTopo(carregando: Boolean, modifier: Modifier = Modifier) {
    if (esperaLongaOBastante(carregando)) LinhaDeProgresso(modifier)
}
