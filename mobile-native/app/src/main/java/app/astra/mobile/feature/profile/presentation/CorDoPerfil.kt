package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.components.BotaoDeTexto
import app.astra.mobile.ui.components.ColorGradientPicker
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.ProfileGradients
import app.astra.mobile.ui.components.parseGradientBrush
import app.astra.mobile.ui.theme.astraColors

private const val AMOSTRAS_POR_LINHA = 6

private fun nomeDaCor(css: String): String = when {
    css.isBlank() -> "nenhuma"
    else -> ProfileGradients.firstOrNull { it.second == css }?.first?.lowercase() ?: "própria"
}

@Composable
fun EscolhaDaCorDoPerfil(atual: String, aoEscolher: (String) -> Unit) {
    var propriaAberta by remember {
        mutableStateOf(atual.isNotBlank() && ProfileGradients.none { it.second == atual })
    }
    var ultimaDoSeletor by remember { mutableStateOf(atual) }
    LaunchedEffect(atual) {
        if (atual != ultimaDoSeletor) propriaAberta = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MarginaliaLabel("cor do perfil · ${nomeDaCor(atual)}")
        ProfileGradients.chunked(AMOSTRAS_POR_LINHA).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                linha.forEach { (nome, css) ->
                    Amostra(nome, css, escolhida = css == atual, modifier = Modifier.weight(1f)) { aoEscolher(css) }
                }
                repeat(AMOSTRAS_POR_LINHA - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BotaoDeTexto(onClick = { propriaAberta = false; aoEscolher("") }) {
                Text("sem cor", color = if (atual.isBlank()) astraColors.accent else astraColors.text2)
            }
            BotaoDeTexto(onClick = { ultimaDoSeletor = atual; propriaAberta = !propriaAberta }) {
                Text("cor própria…", color = if (propriaAberta) astraColors.accent else astraColors.text2)
            }
        }
        if (propriaAberta) {
            ColorGradientPicker(
                initial = atual,
                onChange = { css -> ultimaDoSeletor = css; aoEscolher(css) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Amostra(nome: String, css: String, escolhida: Boolean, modifier: Modifier, aoTocar: () -> Unit) {
    val forma = RoundedCornerShape(7.dp)
    val pincel = remember(css) { parseGradientBrush(css) }
    Box(
        modifier
            .height(30.dp)
            .clip(forma)
            .then(if (pincel != null) Modifier.background(pincel) else Modifier.background(astraColors.base))
            .border(if (escolhida) 2.dp else 1.dp, if (escolhida) astraColors.accent else astraColors.border, forma)
            .clickable(onClick = aoTocar)
            .semantics {
                contentDescription = nome
                selected = escolhida
            },
    )
}
