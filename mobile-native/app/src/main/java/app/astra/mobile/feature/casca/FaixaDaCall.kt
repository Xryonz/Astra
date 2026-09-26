package app.astra.mobile.feature.casca

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.astra.mobile.core.voice.CallStatus
import app.astra.mobile.core.voice.RostosDaCall
import app.astra.mobile.core.voice.VoiceManager
import app.astra.mobile.core.voice.VoiceState
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.MicOff
import com.composables.icons.lucide.PhoneOff
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FaixaDaCallViewModel @Inject constructor(
    private val voiceManager: VoiceManager,
    rostosDaCall: RostosDaCall,
) : ViewModel() {
    val estado = voiceManager.state
    val rostos = rostosDaCall.rostos

    fun alternarMudo() = voiceManager.alternarMudo()
    fun sair() = voiceManager.sair()
}

@Composable
fun FaixaDaCall(
    aoAbrir: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FaixaDaCallViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    val rostos by viewModel.rostos.collectAsState()
    val semMovimento = LocalAppPrefs.current.reduceMotion
    AnimatedVisibility(
        visible = estado.sala != null,
        modifier = modifier,
        enter = if (semMovimento) fadeIn(tween(0)) else expandVertically(tween(260, easing = EaseOutSoft)) + fadeIn(tween(200)),
        exit = if (semMovimento) fadeOut(tween(0)) else shrinkVertically(tween(220, easing = EaseOutSoft)) + fadeOut(tween(160)),
    ) {
        val quemFala = remember(estado.pessoas, rostos) {
            estado.pessoas.filter { it.falando && !it.souEu }.map { rostos[it.identity] ?: RostosDaCall.rostoPadrao(estado.sala) }
        }
        ConteudoDaFaixa(
            estado = estado,
            falando = quemFala.firstOrNull()?.let { it.nome to it.foto },
            maisFalando = (quemFala.size - 1).coerceAtLeast(0),
            aoAbrir = aoAbrir,
            aoMicrofone = viewModel::alternarMudo,
            aoSair = viewModel::sair,
        )
    }
}

@Composable
private fun ConteudoDaFaixa(
    estado: VoiceState,
    falando: Pair<String, String?>?,
    maisFalando: Int,
    aoAbrir: () -> Unit,
    aoMicrofone: () -> Unit,
    aoSair: () -> Unit,
) {
    val forma = RoundedCornerShape(18.dp)
    val sala = estado.sala?.nome.orEmpty()
    val linha = when {
        estado.status == CallStatus.Error -> "a call caiu · toque para ver"
        estado.status == CallStatus.Connecting -> "conectando…"
        falando != null && maisFalando > 0 -> "${falando.first} e mais $maisFalando falando"
        falando != null -> "${falando.first} falando"
        else -> "${estado.pessoas.size} ${if (estado.pessoas.size == 1) "pessoa" else "pessoas"} na call"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 8.dp)
            .clip(forma)
            .background(astraColors.overlay)
            .border(1.dp, astraColors.accent.copy(alpha = 0.35f), forma)
            .clickable(onClickLabel = "abrir a call", onClick = aoAbrir)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (falando != null) {
                AstraAvatar(falando.second, falando.first, size = 26)
            } else {
                Text("◉", color = astraColors.accent, fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics { })
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                sala,
                fontSize = 14.sp,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                linha,
                fontSize = 12.sp,
                color = if (falando != null) astraColors.accent else astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BotaoDaFaixa(
            icone = if (estado.mudo) Lucide.MicOff else Lucide.Mic,
            rotulo = if (estado.mudo) "Abrir o microfone" else "Fechar o microfone",
            perigo = estado.mudo,
            aoTocar = aoMicrofone,
        )
        BotaoDaFaixa(icone = Lucide.PhoneOff, rotulo = "Sair da call", perigo = true, aoTocar = aoSair)
    }
}

@Composable
private fun BotaoDaFaixa(icone: ImageVector, rotulo: String, perigo: Boolean, aoTocar: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = aoTocar)
            .semantics {
                contentDescription = rotulo
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = null, tint = if (perigo) astraColors.danger else astraColors.text1, modifier = Modifier.size(20.dp))
    }
}
