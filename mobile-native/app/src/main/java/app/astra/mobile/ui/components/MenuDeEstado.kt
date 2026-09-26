package app.astra.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

private val ESTADOS_ESCOLHIVEIS = listOf(
    UserStatus.ONLINE to "Disponível",
    UserStatus.IDLE to "Ausente",
    UserStatus.DND to "Não perturbe",
    UserStatus.INVISIBLE to "Invisível",
)

@Composable
fun MenuDeEstado(
    aberto: Boolean,
    atual: UserStatus?,
    aoEscolher: (UserStatus) -> Unit,
    aoFechar: () -> Unit,
    deslocamento: DpOffset = DpOffset.Zero,
) {
    DropdownMenu(
        expanded = aberto,
        onDismissRequest = aoFechar,
        offset = deslocamento,
        shape = RoundedCornerShape(16.dp),
        containerColor = astraColors.overlay,
        border = BorderStroke(1.dp, astraColors.border),
    ) {
        ESTADOS_ESCOLHIVEIS.forEach { (estado, rotulo) ->
            val escolhido = estado == atual
            ItemDeMenu(
                text = { Text(rotulo, color = if (escolhido) astraColors.accent else astraColors.text1) },
                leadingIcon = { StatusDot(status = estado, size = 12.dp, cutoutColor = astraColors.overlay) },
                trailingIcon = if (escolhido) {
                    { Icon(Lucide.Check, contentDescription = null, tint = astraColors.accent, modifier = Modifier.size(16.dp)) }
                } else null,
                onClick = { aoEscolher(estado) },
                modifier = Modifier.semantics { selected = escolhido },
            )
        }
    }
}
