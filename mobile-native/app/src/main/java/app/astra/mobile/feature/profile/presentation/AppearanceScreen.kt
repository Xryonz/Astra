package app.astra.mobile.feature.profile.presentation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.data.DensityPref
import app.astra.mobile.core.data.FontSizePref
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val prefs = LocalAppPrefs.current

    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Aparencia", marginalia = "fonte e densidade", onBack = onBack)

            Spacer(Modifier.height(8.dp))
            MarginaliaLabel("— previa", Modifier.padding(start = 22.dp, bottom = 8.dp))
            PreviewBubbles(prefs.fontSize, prefs.density)

            Spacer(Modifier.height(20.dp))
            MarginaliaLabel("— tamanho da fonte", Modifier.padding(start = 22.dp, bottom = 8.dp))
            SegmentedRow(
                options = FontSizePref.entries,
                selected = prefs.fontSize,
                label = { it.label },
                onSelect = viewModel::setFontSize,
            )

            Spacer(Modifier.height(20.dp))
            MarginaliaLabel("— densidade das mensagens", Modifier.padding(start = 22.dp, bottom = 8.dp))
            SegmentedRow(
                options = DensityPref.entries,
                selected = prefs.density,
                label = { it.label },
                onSelect = viewModel::setDensity,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PreviewBubbles(fontSize: FontSizePref, density: DensityPref) {
    val shape = RoundedCornerShape(14.dp)
    val samples = listOf("Bora marcar a call?", "fechou, 21h entao")
    Column(Modifier.padding(horizontal = 18.dp)) {
        samples.forEachIndexed { i, text ->
            if (i > 0) Spacer(Modifier.height(density.groupedTopDp.dp + 2.dp))
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(astraColors.raised)
                    .border(1.dp, astraColors.borderMid, shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = (17 * fontSize.scale).sp,
                    color = astraColors.text1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            val active = opt == selected
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (active) astraColors.accentDim else astraColors.raised)
                    .border(1.dp, if (active) astraColors.accent else astraColors.border, shape)
                    .clickable { onSelect(opt) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(opt),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) astraColors.accent else astraColors.text2,
                )
            }
        }
    }
}
