package app.astra.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.delay

// Passo do stagger entre linhas na entrada em cascata. Publico pra grupos
// stacked encadearem a onda: o 2o grupo recebe delayStartMs = nLinhasDoAnterior * este.
const val SETTINGS_ROW_STAGGER_MS = 45

// Card suave agrupado: um marginalia opcional acima + uma superficie raised leve
// com fios finos entre os itens. Substitui o "empilhado de caixas com borda".
// O grupo intercala os hairlines sozinho -> o chamador so passa as linhas.
// Na abertura, cada linha faz fade + slide-up escalonado (entrada em cascata).
@Composable
fun SettingsGroup(
    label: String? = null,
    modifier: Modifier = Modifier,
    entrance: Boolean = true,
    delayStartMs: Int = 0,
    rows: List<@Composable () -> Unit>,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        if (label != null) {
            MarginaliaLabel("$label", Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(astraColors.raised.copy(alpha = 0.5f))
                .border(1.dp, astraColors.border.copy(alpha = 0.7f), shape),
        ) {
            rows.forEachIndexed { i, row ->
                val progress = remember { Animatable(if (entrance) 0f else 1f) }
                LaunchedEffect(entrance) {
                    if (entrance) {
                        delay((delayStartMs + i * SETTINGS_ROW_STAGGER_MS).toLong())
                        progress.animateTo(1f, tween(300, easing = EaseOutSoft))
                    }
                }
                val dy = with(LocalDensity.current) { 12.dp.toPx() }
                // Hairline + linha entram juntos: o fio nao aparece antes do item.
                Column(
                    Modifier.graphicsLayer {
                        alpha = progress.value
                        translationY = (1f - progress.value) * dy
                    },
                ) {
                    if (i > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp)
                                .height(1.dp)
                                .background(astraColors.border.copy(alpha = 0.5f)),
                        )
                    }
                    row()
                }
            }
        }
    }
}

// Uma linha do grupo: titulo + marginalia opcional + trailing (default = chevron).
// danger pinta o titulo de vermelho (acoes destrutivas).
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = { ChevronGlyph() },
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (pressed) astraColors.hover.copy(alpha = 0.5f) else Color.Transparent)
            .then(
                if (onClick != null && enabled)
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    danger -> astraColors.danger
                    enabled -> astraColors.text1
                    else -> astraColors.text3
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) MarginaliaLabel(subtitle)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun ChevronGlyph(color: Color = astraColors.text3) {
    Text(text = "›", fontFamily = DmSerif, style = MaterialTheme.typography.titleLarge, color = color)
}
