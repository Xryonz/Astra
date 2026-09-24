package app.astra.mobile.feature.casca

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.astra.mobile.ui.components.ItemDeMenu
import app.astra.mobile.ui.components.Viagem
import app.astra.mobile.ui.components.viajante

@Composable
fun PainelDeSussurros(
    sussurros: List<Conversation>,
    naoLidos: Set<String>,
    silenciados: Set<String>,
    aoAbrir: (Conversation) -> Unit,
    aoBuscar: () -> Unit,
    aoAbrirAmigos: () -> Unit,
    aoNovoSussurro: () -> Unit,
    aoSilenciar: (Conversation, Boolean) -> Unit,
    aoFechar: (Conversation) -> Unit,
    pedidos: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sussurros",
                fontFamily = DmSerif,
                style = MaterialTheme.typography.headlineSmall,
                color = astraColors.text1,
                modifier = Modifier.weight(1f),
            )
            BotaoRedondo(icone = Lucide.Search, rotulo = "Buscar", aoTocar = aoBuscar)
            BotaoRedondo(icone = Lucide.UserPlus, rotulo = "Amigos", aoTocar = aoAbrirAmigos, marca = pedidos)
            BotaoRedondo(icone = Lucide.Plus, rotulo = "Novo sussurro", aoTocar = aoNovoSussurro)
        }

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
        ) {
            itemsIndexed(sussurros, key = { _, conversa -> conversa.id }) { onde, conversa ->
                val primeira = onde == 0
                val ultima = onde == sussurros.lastIndex
                val forma = RoundedCornerShape(
                    topStart = if (primeira) 16.dp else 0.dp,
                    topEnd = if (primeira) 16.dp else 0.dp,
                    bottomStart = if (ultima) 16.dp else 0.dp,
                    bottomEnd = if (ultima) 16.dp else 0.dp,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(forma)
                        .background(astraColors.raised)
                        .padding(top = if (primeira) 6.dp else 0.dp, bottom = if (ultima) 6.dp else 0.dp),
                ) {
                    LinhaDeSussurro(
                        conversa = conversa,
                        naoLido = conversa.id in naoLidos && conversa.id !in silenciados,
                        silenciada = conversa.id in silenciados,
                        aoTocar = { aoAbrir(conversa) },
                        aoSilenciar = { aoSilenciar(conversa, conversa.id !in silenciados) },
                        aoFechar = { aoFechar(conversa) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LinhaDeSussurro(
    conversa: Conversation,
    naoLido: Boolean,
    silenciada: Boolean,
    aoTocar: () -> Unit,
    aoSilenciar: () -> Unit,
    aoFechar: () -> Unit,
) {
    val forma = RoundedCornerShape(12.dp)
    var menu by remember { mutableStateOf(false) }
    Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(forma)
            .background(if (naoLido) astraColors.overlay else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = aoTocar, onLongClick = { menu = true })
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .semantics { contentDescription = "Sussurro com ${conversa.otherName}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AstraAvatar(
            conversa.otherAvatarUrl,
            conversa.otherName,
            modifier = Modifier.viajante(Viagem.fotoDoSussurro(conversa.id)),
            size = 42,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = conversa.otherName,
                style = MaterialTheme.typography.titleMedium,
                color = if (naoLido) astraColors.text1 else astraColors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.viajante(Viagem.nomeDoSussurro(conversa.id), ehTexto = true),
            )
            Text(
                text = (if (conversa.lastFromMe) "Você: " else "") + conversa.preview,
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (naoLido) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(astraColors.accent))
        }
    }
    DropdownMenu(
        expanded = menu,
        onDismissRequest = { menu = false },
        shape = RoundedCornerShape(16.dp),
        containerColor = astraColors.overlay,
        border = BorderStroke(1.dp, astraColors.border),
    ) {
        haQuantoTempo(conversa.lastMessageAt).takeIf { it.isNotEmpty() }?.let { quando ->
            MarginaliaLabel(
                if (quando == "agora") "última mensagem agora" else "última mensagem há $quando",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        ItemDeMenu(
            text = {
                Text(
                    if (silenciada) "Reativar avisos" else "Silenciar conversa",
                    color = astraColors.text1,
                )
            },
            onClick = { menu = false; aoSilenciar() },
        )
        ItemDeMenu(
            text = { Text("Fechar conversa", color = astraColors.danger) },
            onClick = { menu = false; aoFechar() },
        )
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
