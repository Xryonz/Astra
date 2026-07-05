package app.astra.mobile.feature.namecolors.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.ColorGradientPicker
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@Composable
fun NameColorsSection(
    viewModel: NameColorsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when {
        state.loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CosmicSpinner() }
        state.servers.isEmpty() -> Text(
            "Voce ainda nao esta em nenhum servidor.",
            style = MaterialTheme.typography.bodyMedium,
            color = astraColors.text3,
            modifier = Modifier.padding(22.dp),
        )
        else -> Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
                    state.servers.forEach { s ->
                        ServerColorCard(
                            server = s,
                            expanded = state.expandedId == s.id,
                            applied = state.applied[s.id],
                            saving = state.savingId == s.id,
                            error = if (state.expandedId == s.id) state.error else null,
                            onToggle = { viewModel.toggleExpand(s.id) },
                            onCustom = viewModel::onCustom,
                            onApply = { viewModel.apply(s.id) },
                            onReset = { viewModel.reset(s.id) },
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
}

@Composable
private fun ServerColorCard(
    server: NameColorServer,
    expanded: Boolean,
    applied: String?,
    saving: Boolean,
    error: String?,
    onToggle: () -> Unit,
    onCustom: (String) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, if (expanded) astraColors.accent else astraColors.border, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(astraColors.base),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (server.isGroup) "👥" else server.name.take(2).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = astraColors.accent,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = astraColors.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MarginaliaLabel(if (server.isGroup) "grupo" else "servidor")
            }
            if (applied != null) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(hexColor(applied) ?: astraColors.border)
                        .border(2.dp, astraColors.borderMid, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (expanded) "▴" else "▾", color = astraColors.text3, style = MaterialTheme.typography.titleMedium)
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
                ColorGradientPicker(
                    initial = applied ?: "",
                    onChange = onCustom,
                )

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = astraColors.danger)
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Limpar", filled = false, modifier = Modifier.weight(1f), enabled = !saving, onClick = onReset)
                    PillButton(if (saving) "Salvando…" else "Aplicar", filled = true, modifier = Modifier.weight(1f), enabled = !saving, onClick = onApply)
                }
            }
        }
    }
}

@Composable
private fun PillButton(text: String, filled: Boolean, modifier: Modifier = Modifier, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) astraColors.accent else astraColors.base)
            .border(1.dp, if (filled) Color.Transparent else astraColors.borderMid, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (filled) astraColors.textInv else astraColors.text2)
    }
}

private fun hexColor(raw: String?): Color? {
    if (raw == null) return null
    val h = raw.trim().removePrefix("#")
    if (h.length != 6) return null
    return runCatching { Color("FF$h".toLong(16)) }.getOrNull()
}
