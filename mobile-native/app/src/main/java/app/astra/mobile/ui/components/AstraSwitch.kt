package app.astra.mobile.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.astraColors

@Composable
fun AstraSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        modifier = modifier.minimumInteractiveComponentSize(),
        colors = SwitchDefaults.colors(
            checkedThumbColor = astraColors.textInv,
            checkedTrackColor = astraColors.accent,
            checkedBorderColor = astraColors.accent,
            uncheckedThumbColor = astraColors.text3,
            uncheckedTrackColor = astraColors.raised,
            uncheckedBorderColor = astraColors.borderMid,
        ),
    )
}

@Composable
fun comVibracaoDeChave(aoMudar: (Boolean) -> Unit): (Boolean) -> Unit {
    val haptics = LocalAppPrefs.current.haptics
    val haptic = LocalHapticFeedback.current
    return { ligado ->
        if (haptics) {
            haptic.performHapticFeedback(if (ligado) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        }
        aoMudar(ligado)
    }
}
