package app.astra.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.mobile.core.network.dto.UserBadgesDto
import app.astra.mobile.ui.theme.astraColors

// Badge pronta pra UI: globais (Pioneiro/Bot) vem sem origem; as de servidor
// carregam o nome da constelacao que concedeu.
data class BadgeUi(
    val id: String,
    val name: String,
    val icon: String,
    val color: Color?,
    val description: String?,
    val origin: String?,
)

fun UserBadgesDto.toUi(): List<BadgeUi> =
    global.map { BadgeUi(it.id, it.name, it.icon, parseBadgeColor(it.color), it.description, origin = null) } +
        server.map { BadgeUi(it.badgeId, it.name, it.icon, parseBadgeColor(it.color), it.description, origin = it.serverName) }

private fun parseBadgeColor(raw: String?): Color? {
    val h = raw?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    return runCatching { Color("FF$h".toLong(16)) }.getOrNull()
}

// Badges so-icone (escolha do user): capsula circular com o emoji, borda na cor
// da badge. Tocar abre um mini-card ancorado LOGO ABAIXO da badge (popover, nao
// dialog central) com titulo + descricao + origem; toca fora, fecha.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeChips(badges: List<BadgeUi>, modifier: Modifier = Modifier) {
    var detail by remember { mutableStateOf<BadgeUi?>(null) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
        badges.forEach { b ->
            val tint = b.color ?: astraColors.accent
            val open = detail?.id == b.id
            Box {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (open) astraColors.hover else astraColors.raised)
                        .border(1.5.dp, tint.copy(alpha = 0.55f), CircleShape)
                        .clickable { detail = if (open) null else b },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(b.icon, fontSize = 18.sp)
                }
                if (open) {
                    Popup(
                        popupPositionProvider = BelowAnchor(gapPx),
                        onDismissRequest = { detail = null },
                        properties = PopupProperties(focusable = true),
                    ) {
                        BadgePopover(b)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgePopover(b: BadgeUi) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .widthIn(max = 248.dp)
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border.copy(alpha = 0.7f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${b.icon}  ${b.name}",
            style = MaterialTheme.typography.titleMedium,
            color = astraColors.text1,
        )
        if (!b.description.isNullOrBlank()) {
            Text(b.description, style = MaterialTheme.typography.bodySmall, color = astraColors.text2)
        }
        MarginaliaLabel(if (b.origin != null) "concedida em ${b.origin}" else "insignia da Astra")
    }
}

// Posiciona o popover colado abaixo da badge (left-alinhado), preso na janela;
// se estourar embaixo, vira pra cima.
private class BelowAnchor(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) below
                else (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
