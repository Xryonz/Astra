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
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.astraColors
import zed.rainxch.rikkaui.components.ui.dialog.Dialog
import zed.rainxch.rikkaui.components.ui.dialog.DialogAnimation

/**
 * Dialog editorial do Astra sobre o RikkaUI Dialog (FadeScale): anima a entrada
 * E a saida (some com fade+scale ao tocar fora/Esc), ao contrario do AlertDialog
 * do Material que sumia seco. Fica composto sempre; `open` dirige a animacao.
 *
 * O card ja vem com bg (overlay), borda e padding do RikkaTheme. Aqui so empilho
 * titulo + conteudo (slot) + rodape de acoes. Pra um dialog so de fechar (ex:
 * lista de fixadas), passe confirmText="Fechar", onConfirm=onDismiss, dismissText=null.
 *
 * @param confirmEnabled desabilita o botao de confirmar (ex: enquanto carrega).
 */
@Composable
fun AstraDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    title: String,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    dismissText: String? = "Cancelar",
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(
        open = open,
        onDismiss = onDismiss,
        animation = DialogAnimation.FadeScale,
        // Margem lateral: o card nao encosta nas bordas da tela.
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
                    Text(confirmText, color = astraColors.accent)
                }
            }
        }
    }
}
