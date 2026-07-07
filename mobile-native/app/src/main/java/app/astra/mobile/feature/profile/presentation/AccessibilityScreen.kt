package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import app.astra.mobile.ui.components.AstraSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@Composable
fun AccessibilityScreen(
    onBack: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val prefs = LocalAppPrefs.current
    val haptic = LocalHapticFeedback.current

    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Acessibilidade", marginalia = "movimento e vibracao", onBack = onBack)

            Spacer(Modifier.height(8.dp))
            MarginaliaLabel("movimento", Modifier.padding(start = 22.dp, bottom = 8.dp))
            ToggleRow(
                title = "Reduzir animacoes",
                sub = "Desliga TODAS de uma vez (mestre)",
                checked = prefs.reduceMotion,
                onCheckedChange = viewModel::setReduceMotion,
            )

            // So aparecem quando o mestre esta off (senao ja esta tudo desligado).
            if (!prefs.reduceMotion) {
                Spacer(Modifier.height(16.dp))
                MarginaliaLabel("efeitos especificos", Modifier.padding(start = 22.dp, bottom = 8.dp))
                ToggleRow(
                    title = "Aurora do fundo",
                    sub = "Brilho GPU atras do app (o mais pesado)",
                    checked = prefs.animAurora,
                    onCheckedChange = viewModel::setAnimAurora,
                )
                ToggleRow(
                    title = "Estrelas e meteoros",
                    sub = "Fundo animado; off deixa estatico",
                    checked = prefs.animStars,
                    onCheckedChange = viewModel::setAnimStars,
                )
                if (prefs.animAurora) {
                    ToggleRow(
                        title = "Toque no ceu",
                        sub = "Brilho + anel ao tocar no fundo vazio",
                        checked = prefs.animSkyTouch,
                        onCheckedChange = viewModel::setAnimSkyTouch,
                    )
                }
                ToggleRow(
                    title = "Transicoes entre telas",
                    sub = "Deslizar ao navegar; off vira fade curto",
                    checked = prefs.animTransitions,
                    onCheckedChange = viewModel::setAnimTransitions,
                )
            }

            Spacer(Modifier.height(20.dp))
            MarginaliaLabel("toque", Modifier.padding(start = 22.dp, bottom = 8.dp))
            ToggleRow(
                title = "Vibracao",
                sub = "Resposta tatil em gestos e acoes",
                checked = prefs.haptics,
                onCheckedChange = {
                    if (it) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setHaptics(it)
                },
            )
            Spacer(Modifier.height(24.dp))
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
