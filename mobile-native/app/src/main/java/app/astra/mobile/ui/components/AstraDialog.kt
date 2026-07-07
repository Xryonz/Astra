package app.astra.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.astraColors
import zed.rainxch.rikkaui.components.ui.dialog.Dialog
import zed.rainxch.rikkaui.components.ui.dialog.DialogAnimation

@Composable
fun AstraDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    title: String,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    confirmColor: Color = astraColors.accent,
    dismissText: String? = "Cancelar",
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(
        open = open,
        onDismiss = onDismiss,
        animation = DialogAnimation.FadeScale,

        modifier = Modifier.padding(horizontal = 20.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = astraColors.text1,
        )
        content()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dismissText != null) {
                TextButton(onClick = onDismiss) { Text(dismissText, color = astraColors.text2) }
            }
            if (confirmText != null) {
                TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                    Text(confirmText, color = confirmColor)
                }
            }
        }
    }
}
