package app.astra.mobile.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.astra.mobile.ui.theme.astraColors

@Composable
fun AstraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
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
