package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.xp.MissoesStore
import app.astra.mobile.core.network.dto.MissaoConcluidaDto
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext

private const val VIDA_MS      = 3_400L
private const val RESPIRO_MS   = 420L

@Composable
fun BoxScope.MissaoToaster() {
    val store = remember { GlobalContext.get().get<MissoesStore>() }
    var atual by remember { mutableStateOf<MissaoConcluidaDto?>(null) }

    LaunchedEffect(store) {
        store.concluidas.collect { m ->
            atual = m
            delay(VIDA_MS)
            atual = null
            delay(RESPIRO_MS)
        }
    }

    val ultima = remember { mutableStateOf<MissaoConcluidaDto?>(null) }
    atual?.let { ultima.value = it }

    AnimatedVisibility(
        visible = atual != null,
        enter = slideInHorizontally(tween(280, easing = EaseOutStd)) { it / 2 } + fadeIn(tween(200)),
        exit = slideOutHorizontally(tween(220, easing = EaseOutStd)) { it / 2 } + fadeOut(tween(180)),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
    ) {
        ultima.value?.let { CartaoDeMissao(it) }
    }
}

@Composable
private fun CartaoDeMissao(m: MissaoConcluidaDto) {
    Row(
        Modifier
            .widthIn(min = 210.dp, max = 300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.accentDim, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Obsidian.accent),
            contentAlignment = Alignment.Center,
        ) {
            LIcon(Lucide.Check, tint = Obsidian.textInv, size = 14.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                rotuloDoTipo(m.tipo),
                style = TextStyle(
                    color = Obsidian.text3, fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 1.1.sp,
                ),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                m.titulo,
                style = TextStyle(color = Obsidian.text1, fontSize = 12.5.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "+${m.xp} de brilho esperando resgate",
                style = TextStyle(color = Obsidian.accent, fontSize = 11.sp, fontFamily = DmMono),
            )
        }
    }
}

private fun rotuloDoTipo(tipo: String): String = when (tipo) {
    "semanal"   -> "MISSÃO DA SEMANA"
    "conquista" -> "CONQUISTA"
    else        -> "MISSÃO COMPLETA"
}
