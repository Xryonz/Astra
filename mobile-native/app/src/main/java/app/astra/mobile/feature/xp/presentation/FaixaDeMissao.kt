package app.astra.mobile.feature.xp.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.astra.mobile.core.network.dto.MissaoConcluidaDto
import app.astra.mobile.core.xp.Missoes
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.DmMono
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

private const val VIDA_MS = 3_400L
private const val RESPIRO_MS = 420L

@HiltViewModel
class MissaoConcluidaViewModel @Inject constructor(missoes: Missoes) : ViewModel() {
    val concluidas = missoes.concluidas
}

@Composable
fun BoxScope.FaixaDeMissao(viewModel: MissaoConcluidaViewModel = hiltViewModel()) {
    var atual by remember { mutableStateOf<MissaoConcluidaDto?>(null) }
    val ultima = remember { mutableStateOf<MissaoConcluidaDto?>(null) }
    val semMovimento = LocalAppPrefs.current.reduceMotion

    LaunchedEffect(Unit) {
        viewModel.concluidas.collect { feita ->
            atual = feita
            delay(VIDA_MS)
            atual = null
            delay(RESPIRO_MS)
        }
    }
    atual?.let { ultima.value = it }

    AnimatedVisibility(
        visible = atual != null,
        enter = if (semMovimento) fadeIn(tween(0))
        else slideInVertically(tween(280, easing = EaseOutSoft)) { -it } + fadeIn(tween(200)),
        exit = if (semMovimento) fadeOut(tween(0))
        else slideOutVertically(tween(220, easing = EaseOutSoft)) { -it } + fadeOut(tween(180)),
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        ultima.value?.let { CartaoDaMissao(it) }
    }
}

@Composable
private fun CartaoDaMissao(m: MissaoConcluidaDto) {
    val forma = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(astraColors.overlay)
            .border(1.dp, astraColors.accentDim, forma)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "${rotuloDoTipo(m.tipo)}: ${m.titulo}, mais ${m.xp} de brilho para resgatar"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(astraColors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Check, contentDescription = null, tint = astraColors.textInv, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                rotuloDoTipo(m.tipo),
                fontSize = 10.sp,
                color = astraColors.text3,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                m.titulo,
                style = MaterialTheme.typography.bodyMedium,
                color = astraColors.text1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "+${m.xp} esperando resgate",
                fontFamily = DmMono,
                fontSize = 12.sp,
                color = astraColors.accent,
            )
        }
    }
}

private fun rotuloDoTipo(tipo: String): String = when (tipo) {
    "semanal" -> "MISSÃO DA SEMANA"
    "conquista" -> "CONQUISTA"
    else -> "MISSÃO COMPLETA"
}
