package app.astra.mobile.feature.casca

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.StatusDot
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Lucide
import kotlin.math.abs

@Composable
fun BarraPessoal(
    nome: String,
    avatar: String?,
    status: UserStatus,
    recado: String?,
    avisos: Int,
    aoTocar: () -> Unit,
    aoSegurar: () -> Unit,
    aoAbrirAvisos: () -> Unit,
    aoDeslizar: (paraDireita: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val forma = RoundedCornerShape(8.dp)
    val limite = with(LocalDensity.current) { 56.dp.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(forma)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, forma)
            .pointerInput(Unit) {
                var andado = 0f
                detectHorizontalDragGestures(
                    onDragStart = { andado = 0f },
                    onDragEnd = { if (abs(andado) > limite) aoDeslizar(andado > 0) },
                ) { mudanca, delta ->
                    andado += delta
                    mudanca.consume()
                }
            }
            .combinedClickable(onClick = aoTocar, onLongClick = aoSegurar)
            .semantics { contentDescription = "Seu perfil: $nome, ${rotuloDoStatus(status)}" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AstraAvatar(avatar, nome, size = 40)
            StatusDot(
                status = status,
                bordered = true,
                borderColor = astraColors.raised,
                cutoutColor = astraColors.raised,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = nome,
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            recado?.takeIf { it.isNotBlank() }?.let { MarginaliaLabel(it) }
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(astraColors.overlay)
                .clickable(onClick = aoAbrirAvisos)
                .semantics { contentDescription = "Avisos" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.Bell,
                contentDescription = null,
                tint = if (avisos > 0) astraColors.accent else astraColors.text2,
                modifier = Modifier.size(18.dp),
            )
            if (avisos > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(astraColors.danger),
                )
            }
        }
    }
}

private fun rotuloDoStatus(status: UserStatus): String = when (status) {
    UserStatus.ONLINE -> "disponível"
    UserStatus.IDLE -> "ausente"
    UserStatus.DND -> "não perturbe"
    UserStatus.INVISIBLE -> "invisível"
    UserStatus.OFFLINE -> "desconectado"
}
