package app.astra.mobile.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.astraColors

@Composable
fun AstraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalAppPrefs.current.haptics
    val haptic = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = {
            if (haptics) {
                haptic.performHapticFeedback(
                    if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
            }
            onCheckedChange(it)
        },
        enabled = enabled,
        modifier = modifier,
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
