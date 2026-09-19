package app.astra.mobile.feature.casca

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.UserPlus
import java.time.Duration
import java.time.OffsetDateTime

@Composable
fun PainelDeSussurros(
    sussurros: List<Conversation>,
    naoLidos: Set<String>,
    silenciados: Set<String>,
    aoAbrir: (Conversation) -> Unit,
    aoBuscar: () -> Unit,
    aoAbrirAmigos: () -> Unit,
    aoNovoSussurro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Text(
            text = "Sussurros",
            fontFamily = DmSerif,
            style = MaterialTheme.typography.headlineSmall,
            color = astraColors.text1,
            modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 2.dp),
        )
        MarginaliaLabel("conversas de duas pessoas", Modifier.padding(start = 18.dp, bottom = 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Acao(icone = Lucide.Search, rotulo = "Buscar", aoTocar = aoBuscar, modifier = Modifier.weight(1f))
            Acao(icone = Lucide.UserPlus, rotulo = "Amigos", aoTocar = aoAbrirAmigos, modifier = Modifier.weight(1f))
            Acao(icone = Lucide.Plus, rotulo = "Novo", aoTocar = aoNovoSussurro, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.padding(top = 6.dp))

        if (sussurros.isEmpty()) {
            EmptyState(line = "Nenhum sussurro ainda", hint = "chame alguém pelo nome de usuário")
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 10.dp,
                end = 10.dp,
                top = 6.dp,
                bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(sussurros, key = { it.id }) { conversa ->
                LinhaDeSussurro(
                    conversa = conversa,
                    naoLido = conversa.id in naoLidos && conversa.id !in silenciados,
                    aoTocar = { aoAbrir(conversa) },
                )
            }
        }
    }
}

@Composable
private fun Acao(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    rotulo: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forma = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(forma)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, forma)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icone, contentDescription = null, tint = astraColors.text2, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(rotulo, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
    }
}

@Composable
private fun LinhaDeSussurro(conversa: Conversation, naoLido: Boolean, aoTocar: () -> Unit) {
    val forma = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (naoLido) astraColors.raised else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .semantics { contentDescription = "Sussurro com ${conversa.otherName}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AstraAvatar(conversa.otherAvatarUrl, conversa.otherName, size = 42)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = conversa.otherName,
                style = MaterialTheme.typography.titleMedium,
                color = if (naoLido) astraColors.text1 else astraColors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = (if (conversa.lastFromMe) "Você: " else "") + conversa.preview,
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MarginaliaLabel(haQuantoTempo(conversa.lastMessageAt))
            if (naoLido) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(astraColors.accent))
            }
        }
    }
}

internal fun haQuantoTempo(instante: String?): String {
    val quando = instante?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return ""
    val minutos = Duration.between(quando, OffsetDateTime.now()).toMinutes()
    return when {
        minutos < 1 -> "agora"
        minutos < 60 -> "${minutos}min"
        minutos < 60 * 24 -> "${minutos / 60}h"
        else -> "${minutos / (60 * 24)}d"
    }
}
