package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.data.DensityPref
import app.astra.mobile.core.data.FontSizePref
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.AccentOption
import app.astra.mobile.ui.theme.AccentOptions
import app.astra.mobile.ui.theme.BgOption
import app.astra.mobile.ui.theme.BgOptions
import app.astra.mobile.ui.theme.ThemePreset
import app.astra.mobile.ui.theme.ThemePresets
import app.astra.mobile.ui.theme.accentOption
import app.astra.mobile.ui.theme.astraColors
import app.astra.mobile.ui.theme.bgOption

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val prefs = LocalAppPrefs.current

    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Aparencia", marginalia = "tema, fonte e densidade", onBack = onBack)

            Spacer(Modifier.height(8.dp))
            MarginaliaLabel("— previa", Modifier.padding(start = 22.dp, bottom = 8.dp))
            PreviewBubbles(prefs.fontSize, prefs.density)

            Spacer(Modifier.height(22.dp))
            MarginaliaLabel("— tema rapido", Modifier.padding(start = 22.dp, bottom = 8.dp))
            PresetGrid(
                selectedAccent = prefs.accentId,
                selectedBg = prefs.bgId,
                onPick = { p -> viewModel.setTheme(p.accentId, p.bgId) },
            )

            Spacer(Modifier.height(22.dp))
            MarginaliaLabel("— cor de destaque", Modifier.padding(start = 22.dp, bottom = 8.dp))
            AccentRow(selected = prefs.accentId, onSelect = viewModel::setAccent)

            Spacer(Modifier.height(22.dp))
            MarginaliaLabel("— fundo", Modifier.padding(start = 22.dp, bottom = 8.dp))
            BgList(selected = prefs.bgId, onSelect = viewModel::setBg)

            Spacer(Modifier.height(22.dp))
            MarginaliaLabel("— tamanho da fonte", Modifier.padding(start = 22.dp, bottom = 8.dp))
            SegmentedRow(
                options = FontSizePref.entries,
                selected = prefs.fontSize,
                label = { it.label },
                onSelect = viewModel::setFontSize,
            )

            Spacer(Modifier.height(22.dp))
            MarginaliaLabel("— densidade das mensagens", Modifier.padding(start = 22.dp, bottom = 8.dp))
            SegmentedRow(
                options = DensityPref.entries,
                selected = prefs.density,
                label = { it.label },
                onSelect = viewModel::setDensity,
            )
            Spacer(Modifier.height(28.dp))
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
private fun PresetGrid(
    selectedAccent: String,
    selectedBg: String,
    onPick: (ThemePreset) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemePresets.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { p ->
                    PresetCard(
                        preset = p,
                        active = selectedAccent == p.accentId && selectedBg == p.bgId,
                        onClick = { onPick(p) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: ThemePreset,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = bgOption(preset.bgId)
    val accent = accentOption(preset.accentId).value
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (active) astraColors.accentDim else astraColors.raised)
            .border(1.dp, if (active) astraColors.accent else astraColors.border, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bg.voidC)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) astraColors.accent else astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preset.hint,
                style = MaterialTheme.typography.labelSmall,
                color = astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentRow(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccentOptions.forEach { opt -> AccentSwatch(opt, opt.id == selected) { onSelect(opt.id) } }
    }
}

@Composable
private fun AccentSwatch(opt: AccentOption, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    val checkColor = if (opt.value.luminance() > 0.5f) Color(0xFF09091A) else Color.White
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(opt.value)
            .border(
                width = if (active) 2.5.dp else 1.dp,
                color = if (active) astraColors.text1 else Color.White.copy(alpha = 0.12f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (active) Text("✓", color = checkColor, fontSize = 15.sp)
    }
}

@Composable
private fun BgList(selected: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BgOptions.forEach { opt -> BgRow(opt, opt.id == selected) { onSelect(opt.id) } }
    }
}

@Composable
private fun BgRow(opt: BgOption, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) astraColors.accentDim else astraColors.raised)
            .border(1.dp, if (active) astraColors.accent else astraColors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(opt.voidC)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = opt.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) astraColors.accent else astraColors.text2,
            modifier = Modifier.weight(1f),
        )
        if (active) Text("✓", color = astraColors.accent, fontSize = 15.sp)
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
