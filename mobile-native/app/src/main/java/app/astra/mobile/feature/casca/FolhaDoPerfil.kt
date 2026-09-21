package app.astra.mobile.feature.casca

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.AstraButtonVariant
import app.astra.mobile.ui.components.BadgeChips
import app.astra.mobile.ui.components.BadgeUi
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.StatusDot
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage

@Composable
fun FolhaDoPerfil(
    nome: String,
    usuario: String,
    avatar: String?,
    banner: String?,
    corDoBanner: String?,
    status: UserStatus,
    recado: String?,
    pronomes: String?,
    bio: String?,
    emblemas: List<BadgeUi>,
    aoEditar: () -> Unit,
    aoTrocarStatus: () -> Unit,
    aoAbrirAjustes: () -> Unit,
    aoFechar: () -> Unit,
) {
    BackHandler(onBack = aoFechar)
    val forma = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(astraColors.void.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { aoFechar() } },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(forma)
                .background(astraColors.base)
                .border(1.dp, astraColors.border, forma)
                .pointerInput(Unit) { detectTapGestures {} }
                .pointerInput(Unit) {
                    var andado = 0f
                    detectVerticalDragGestures(
                        onDragStart = { andado = 0f },
                        onDragEnd = { if (andado > 120f) aoFechar() },
                    ) { mudanca, delta ->
                        andado += delta
                        mudanca.consume()
                    }
                }
                .navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().height(92.dp).background(corDoBanner.comoCor() ?: astraColors.raised)) {
                if (banner != null) {
                    AsyncImage(
                        model = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(astraColors.text3.copy(alpha = 0.6f)),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(top = 0.dp)) {
                    AstraAvatar(avatar, nome, size = 72)
                    StatusDot(
                        status = status,
                        size = 18.dp,
                        bordered = true,
                        borderColor = astraColors.base,
                        cutoutColor = astraColors.base,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = nome,
                        fontFamily = DmSerif,
                        style = MaterialTheme.typography.headlineSmall,
                        color = astraColors.text1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MarginaliaLabel("@$usuario" + (pronomes?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                recado?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
                }
                bio?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = astraColors.text3)
                }
                if (emblemas.isNotEmpty()) BadgeChips(emblemas)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AstraButton(text = "Editar perfil", onClick = aoEditar, modifier = Modifier.weight(1f))
                AstraButton(
                    text = "Status",
                    onClick = aoTrocarStatus,
                    variant = AstraButtonVariant.Ghost,
                )
                AstraButton(
                    text = "Ajustes",
                    onClick = aoAbrirAjustes,
                    variant = AstraButtonVariant.Ghost,
                )
            }
        }
    }
}

private fun String?.comoCor(): Color? {
    val limpo = this?.trim()?.removePrefix("#") ?: return null
    if (limpo.length != 6) return null
    return runCatching { Color("FF$limpo".toLong(16)) }.getOrNull()
}
