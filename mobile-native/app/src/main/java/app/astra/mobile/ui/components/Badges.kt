package app.astra.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

// Chips editoriais (escolha do user): capsula emoji + nome, borda na cor da
// badge; tocar abre mini-card com descricao e origem.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeChips(badges: List<BadgeUi>, modifier: Modifier = Modifier) {
    var detail by remember { mutableStateOf<BadgeUi?>(null) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        badges.forEach { b ->
            val tint = b.color ?: astraColors.accent
            val shape = RoundedCornerShape(50)
            Text(
                text = "${b.icon} ${b.name}",
                style = MaterialTheme.typography.labelLarge,
                color = astraColors.text1,
                modifier = Modifier
                    .clip(shape)
                    .background(astraColors.raised)
                    .border(1.dp, tint.copy(alpha = 0.45f), shape)
                    .clickable { detail = b }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    val d = detail
    AstraDialog(
        open = d != null,
        onDismiss = { detail = null },
        title = d?.let { "${it.icon} ${it.name}" } ?: "",
        confirmText = "Fechar",
        onConfirm = { detail = null },
        dismissText = null,
    ) {
        if (!d?.description.isNullOrBlank()) {
            Text(d!!.description!!, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
        }
        if (d?.origin != null) {
            Spacer(Modifier.height(8.dp))
            MarginaliaLabel("concedida em ${d.origin}")
        } else if (d != null) {
            Spacer(Modifier.height(8.dp))
            MarginaliaLabel("insignia da Astra")
        }
    }
}
