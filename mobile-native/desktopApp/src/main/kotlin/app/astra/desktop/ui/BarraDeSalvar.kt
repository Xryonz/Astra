package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import kotlin.math.PI
import kotlin.math.sin

private const val TREMOR_MS = 380
private const val TREMOR_VOLTAS = 3.0
private val TREMOR_ALCANCE = 8.dp

internal fun resumoDoRascunho(ajustes: Int, perfil: Boolean): String {
    val emAjustes = when (ajustes) {
        0 -> null
        1 -> "1 ajuste"
        else -> "$ajustes ajustes"
    }
    return when {
        emAjustes != null && perfil -> "$emAjustes e o perfil"
        emAjustes != null -> emAjustes
        else -> "o perfil"
    }
}

internal fun recadoDoSalvamento(ajustes: Int, perfil: Boolean): String {
    val emAjustes = if (ajustes == 1) "1 ajuste" else "$ajustes ajustes"
    return when {
        ajustes > 0 && perfil -> "perfil e $emAjustes salvos"
        perfil -> "perfil salvo"
        ajustes == 1 -> "1 ajuste salvo"
        else -> "$emAjustes salvos"
    }
}

@Composable
internal fun BarraDeSalvar(
    ajustes: Int,
    perfil: Boolean,
    salvando: Boolean,
    confirmacao: String?,
    erro: String?,
    insistencia: Int,
    aoSalvar: () -> Unit,
    aoDescartar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendente = ajustes > 0 || perfil
    val tremor = remember { Animatable(1f) }
    val paradinho = LocalReduceMotion.current
    val alcance = with(LocalDensity.current) { TREMOR_ALCANCE.toPx() }

    LaunchedEffect(insistencia) {
        if (insistencia == 0 || paradinho) return@LaunchedEffect
        tremor.snapTo(0f)
        tremor.animateTo(1f, tween(TREMOR_MS, easing = LinearEasing))
    }

    AnimatedVisibility(
        visible = pendente || confirmacao != null,
        enter = slideInVertically(tween(180, easing = EaseOutStd)) { it } + fadeIn(tween(140)),
        exit = slideOutVertically(tween(150, easing = EaseOutStd)) { it } + fadeOut(tween(110)),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .graphicsLayer {
                    translationX =
                        sin(tremor.value * PI * TREMOR_VOLTAS).toFloat() * (1f - tremor.value) * alcance
                }
                .widthIn(max = 620.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Obsidian.overlay)
                .border(
                    1.dp,
                    if (erro != null) Obsidian.danger else Obsidian.borderMid,
                    RoundedCornerShape(12.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .semCursorDeClique()
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pendente) {
                PendenciaDaBarra(ajustes, perfil, erro)
                Spacer(Modifier.width(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotaoDaBarra("descartar", principal = false, ativo = !salvando, onClick = aoDescartar)
                    BotaoDaBarra(
                        if (salvando) "salvando…" else "salvar",
                        principal = true,
                        ativo = !salvando,
                        onClick = aoSalvar,
                    )
                }
            } else {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(Obsidian.success),
                    contentAlignment = Alignment.Center,
                ) {
                    LIcon(Lucide.Check, tint = Obsidian.void, size = 13.dp)
                }
                Spacer(Modifier.width(11.dp))
                Text(confirmacao.orEmpty(), style = Tipo.corpo)
            }
        }
    }
}

@Composable
private fun PendenciaDaBarra(ajustes: Int, perfil: Boolean, erro: String?) {
    Column(Modifier.widthIn(max = 360.dp)) {
        Text(
            if (erro != null) "não deu para salvar tudo" else "alterações não salvas",
            style = Tipo.corpo,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            erro ?: resumoDoRascunho(ajustes, perfil),
            style = if (erro != null) {
                TextStyle(color = Obsidian.danger, fontSize = 11.sp, lineHeight = 15.sp)
            } else {
                Tipo.apoio
            },
        )
    }
}

@Composable
private fun BotaoDaBarra(rotulo: String, principal: Boolean, ativo: Boolean, onClick: () -> Unit) {
    val fonte = remember { MutableInteractionSource() }
    val sobre by fonte.collectIsHoveredAsState()
    val cor = when {
        !ativo -> Obsidian.text3
        principal -> Obsidian.accent
        else -> Obsidian.text2
    }
    val fundo by animateColorAsState(
        when {
            !ativo -> Color.Transparent
            sobre && principal -> Obsidian.accent.copy(alpha = 0.14f)
            sobre -> Obsidian.hover
            else -> Color.Transparent
        },
        tween(140),
    )
    Row(
        Modifier
            .clickScale(fonte)
            .clip(FormaDeBotao)
            .background(fundo)
            .border(1.dp, if (principal) Obsidian.accentDim else Obsidian.borderDim, FormaDeBotao)
            .hoverable(fonte)
            .clickable(interactionSource = fonte, indication = null, enabled = ativo, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (principal) {
            LIcon(Lucide.Check, tint = cor, size = 14.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(rotulo, style = TextStyle(color = cor, fontSize = 13.sp))
    }
}
