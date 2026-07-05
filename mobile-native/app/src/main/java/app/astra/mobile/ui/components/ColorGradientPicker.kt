package app.astra.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.astraColors

// Construtor de cor: toggle Solido | Gradiente. Emite uma string CSS que o
// backend/parseNameColor/parseGradientBrush ja entendem:
//   solido   -> "#rrggbb"
//   gradiente-> "linear-gradient(Ndeg,#a,#b)"
// Substitui os presets (escolha do user). Seed uma vez do valor inicial; depois
// vive do proprio estado e avisa o pai a cada mudanca.

private enum class PickMode { SOLID, GRADIENT }

private val HEX6 = Regex("#[0-9a-fA-F]{6}")

private fun composeCss(mode: PickMode, a: String, b: String, angle: Int): String =
    if (mode == PickMode.SOLID) a.trim()
    else "linear-gradient(${angle}deg,${a.trim()},${b.trim()})"

@Composable
fun ColorGradientPicker(
    initial: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seed = remember {
        val hexes = HEX6.findAll(initial).map { it.value }.toList()
        val angle = Regex("(-?[0-9]{1,3})deg").find(initial)?.groupValues?.get(1)?.toIntOrNull() ?: 135
        when {
            initial.contains("gradient") && hexes.size >= 2 -> SeedState(PickMode.GRADIENT, hexes.first(), hexes.last(), angle)
            hexes.size == 1 -> SeedState(PickMode.SOLID, hexes[0], "#6aaeca", angle)
            else -> SeedState(PickMode.SOLID, "#c9a96e", "#6aaeca", 135)
        }
    }
    var mode by remember { mutableStateOf(seed.mode) }
    var hexA by remember { mutableStateOf(seed.a) }
    var hexB by remember { mutableStateOf(seed.b) }
    var angle by remember { mutableIntStateOf(seed.angle) }

    fun emit() = onChange(composeCss(mode, hexA, hexB, angle))

    Column(modifier.fillMaxWidth()) {
        // Toggle Solido | Gradiente
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, astraColors.border, RoundedCornerShape(12.dp)),
        ) {
            SegButton("Sólido", mode == PickMode.SOLID, Modifier.weight(1f)) {
                if (mode != PickMode.SOLID) { mode = PickMode.SOLID; emit() }
            }
            SegButton("Gradiente", mode == PickMode.GRADIENT, Modifier.weight(1f)) {
                if (mode != PickMode.GRADIENT) { mode = PickMode.GRADIENT; emit() }
            }
        }

        // Preview ao vivo (branco se hex incompleto).
        val brush = parseGradientBrush(composeCss(mode, hexA, hexB, angle))
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .then(if (brush != null) Modifier.background(brush) else Modifier.background(astraColors.base))
                .border(1.dp, astraColors.border, RoundedCornerShape(9.dp)),
        )

        Spacer(Modifier.height(10.dp))
        EditorialField(
            value = hexA, onValue = { hexA = it; emit() },
            label = if (mode == PickMode.SOLID) "cor (hex)" else "início (hex)",
            placeholder = "#c9a96e",
            enabled = true, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next,
        )

        if (mode == PickMode.GRADIENT) {
            Spacer(Modifier.height(10.dp))
            EditorialField(
                value = hexB, onValue = { hexB = it; emit() },
                label = "fim (hex)", placeholder = "#6aaeca",
                enabled = true, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarginaliaLabel("ângulo")
                Slider(
                    value = angle.toFloat(),
                    onValueChange = { angle = it.toInt(); emit() },
                    valueRange = 0f..360f,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Text("${angle}°", style = MaterialTheme.typography.labelMedium, color = astraColors.text3)
            }
        }
    }
}

private data class SeedState(val mode: PickMode, val a: String, val b: String, val angle: Int)

@Composable
private fun SegButton(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (active) astraColors.accentDim else astraColors.raised)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) astraColors.accent else astraColors.text2,
        )
    }
}
