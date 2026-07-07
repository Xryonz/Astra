package app.astra.mobile.feature.discover.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.DiscoverServerDto
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.edgeSwipeBack
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import zed.rainxch.rikkaui.components.ui.input.Input
import zed.rainxch.rikkaui.components.ui.input.InputAnimation

@Composable
fun DiscoverScreen(
    onBack: () -> Unit,
    onOpenServer: (String, String) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsState()

    LaunchedEffect(s.joined) {
        s.joined?.let { (id, name) -> onOpenServer(id, name); viewModel.consumeJoined() }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize().edgeSwipeBack(onBack)) {
            EditorialTopBar(title = "Descobrir", marginalia = "constelacoes publicas", onBack = onBack)

            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Input(
                    value = s.query,
                    onValueChange = viewModel::onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Buscar constelacao",
                    singleLine = true,
                    animation = InputAnimation.Glow,
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    s.loading && s.servers.isEmpty() -> Center("Varrendo o ceu…")
                    s.error != null -> Center(s.error!!)
                    s.servers.isEmpty() -> EmptyState(
                        line = "Nenhuma constelacao publica",
                        hint = "seja o primeiro a fundar uma",
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(s.servers, key = { it.id }) { srv ->
                            DiscoverCard(
                                server = srv,
                                joining = s.joiningId == srv.id,
                                joined = srv.id in s.joinedIds,
                                onJoin = { viewModel.join(srv) },
                                onOpen = { onOpenServer(srv.id, srv.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverCard(
    server: DiscoverServerDto,
    joining: Boolean,
    joined: Boolean,
    onJoin: () -> Unit,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .then(if (joined) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AstraAvatar(server.iconUrl, server.name, size = 48)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = server.name,
                fontFamily = DmSerif,
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // val local: server.description agora vem do :shared -> smart-cast
            // entre modulos nao rola direto no acesso a propriedade.
            val description = server.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = astraColors.text2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MarginaliaLabel("${server.members} estrelas")
        }
        Spacer(Modifier.width(10.dp))
        val pill = RoundedCornerShape(12.dp)
        if (joined) {
            // Ja e membro (o user pediu pra ver as proprias publicas): selo em vez
            // de botao entrar; tocar o card abre a constelacao.
            Box(
                modifier = Modifier
                    .clip(pill)
                    .background(astraColors.raised)
                    .border(1.dp, astraColors.accent.copy(alpha = 0.5f), pill)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "voce ja esta",
                    style = MaterialTheme.typography.labelMedium,
                    color = astraColors.accent,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(pill)
                    .background(if (joining) astraColors.raised else astraColors.accent)
                    .border(1.dp, if (joining) astraColors.borderMid else astraColors.accent, pill)
                    .clickable(enabled = !joining, onClick = onJoin)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (joining) "…" else "Entrar",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (joining) astraColors.text3 else astraColors.textInv,
                )
            }
        }
    }
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
    }
}
