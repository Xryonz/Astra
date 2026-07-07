package app.astra.mobile.feature.notifications.presentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import app.astra.mobile.ui.components.AstraSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.UpdateNotificationPrefsRequest
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors
import zed.rainxch.rikkaui.components.ui.toast.LocalToastHostState

@Composable
fun NotificationsSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val toast = LocalToastHostState.current

    LaunchedEffect(state.testSent) {
        if (state.testSent) {
            toast.show("Push de teste enviado")
            viewModel.clearTestFlag()
        }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Notificações", marginalia = "o que te acorda", onBack = onBack)

            when {
                state.loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CosmicSpinner()
                }
                state.prefs == null -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error ?: "Falha ao carregar", style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tentar de novo",
                        style = MaterialTheme.typography.labelLarge,
                        color = astraColors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = viewModel::load)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                else -> {
                    val p = state.prefs!!

                    Spacer(Modifier.height(8.dp))
                    MarginaliaLabel("tipos", Modifier.padding(start = 22.dp, bottom = 8.dp))
                    ToggleRow("Menções", "quando te citam numa órbita", p.mentions) { v ->
                        viewModel.toggle(UpdateNotificationPrefsRequest(mentions = v)) { it.copy(mentions = v) }
                    }
                    ToggleRow("Sussurros", "mensagens diretas novas", p.dms) { v ->
                        viewModel.toggle(UpdateNotificationPrefsRequest(dms = v)) { it.copy(dms = v) }
                    }
                    ToggleRow("Respostas", "quando respondem sua mensagem", p.replies) { v ->
                        viewModel.toggle(UpdateNotificationPrefsRequest(replies = v)) { it.copy(replies = v) }
                    }
                    ToggleRow("Reações", "quando reagem à sua mensagem", p.reactions) { v ->
                        viewModel.toggle(UpdateNotificationPrefsRequest(reactions = v)) { it.copy(reactions = v) }
                    }

                    Spacer(Modifier.height(20.dp))
                    MarginaliaLabel("som", Modifier.padding(start = 22.dp, bottom = 8.dp))
                    ToggleRow("Sons de notificação", "toca ao receber", p.sounds) { v ->
                        viewModel.toggle(UpdateNotificationPrefsRequest(sounds = v)) { it.copy(sounds = v) }
                    }

                    Spacer(Modifier.height(20.dp))
                    MarginaliaLabel("horário silencioso", Modifier.padding(start = 22.dp, bottom = 8.dp))
                    QuietHoursCard(
                        start = p.quietStart,
                        end = p.quietEnd,
                        onChange = viewModel::setQuiet,
                    )

                    Spacer(Modifier.height(20.dp))
                    MarginaliaLabel("push", Modifier.padding(start = 22.dp, bottom = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(astraColors.raised)
                            .border(1.dp, astraColors.border, RoundedCornerShape(14.dp))
                            .clickable(onClick = viewModel::sendTestPush)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Enviar push de teste", style = MaterialTheme.typography.titleMedium, color = astraColors.text1)
                            MarginaliaLabel("chega no aparelho se o push estiver ativo")
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    sub: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = astraColors.text1)
            MarginaliaLabel(sub)
        }
        AstraSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun QuietHoursCard(
    start: Int?,
    end: Int?,
    onChange: (Int?, Int?) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        MarginaliaLabel("sem push entre esses horários")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            HourPicker("início", start) { onChange(it, end) }
            Spacer(Modifier.width(14.dp))
            HourPicker("fim", end) { onChange(start, it) }
            Spacer(Modifier.weight(1f))
            if (start != null && end != null) {
                Text(
                    "limpar",
                    style = MaterialTheme.typography.labelMedium,
                    color = astraColors.text3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onChange(null, null) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun HourPicker(label: String, value: Int?, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        MarginaliaLabel(label)
        Spacer(Modifier.height(4.dp))
        Box {
            Text(
                text = value?.let { String.format("%02d:00", it) } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = if (value != null) astraColors.accent else astraColors.text3,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, astraColors.borderMid, RoundedCornerShape(8.dp))
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("—") }, onClick = { open = false; onPick(null) })
                (0..23).forEach { h ->
                    DropdownMenuItem(
                        text = { Text(String.format("%02d:00", h)) },
                        onClick = { open = false; onPick(h) },
                    )
                }
            }
        }
    }
}
