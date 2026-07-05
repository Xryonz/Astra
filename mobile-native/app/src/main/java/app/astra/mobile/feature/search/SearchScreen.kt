package app.astra.mobile.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.SearchChannelDto
import app.astra.mobile.core.network.dto.SearchMessageDto
import app.astra.mobile.core.network.dto.SearchServerDto
import app.astra.mobile.core.network.dto.SearchUserDto
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenServer: (serverId: String, name: String) -> Unit,
    onOpenChannel: (channelId: String, name: String) -> Unit,
    onOpenUser: (userId: String, name: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { focus.requestFocus() }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(title = "Buscar", marginalia = "em tudo", onBack = onBack)

            TextField(
                value = state.query,
                onValueChange = viewModel::onQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .focusRequester(focus),
                singleLine = true,
                placeholder = { Text("mensagens, orbitas, estrelas, constelacoes", color = astraColors.text3) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = astraColors.raised,
                    unfocusedContainerColor = astraColors.raised,
                    focusedIndicatorColor = astraColors.accent,
                    unfocusedIndicatorColor = astraColors.border,
                    cursorColor = astraColors.accent,
                    focusedTextColor = astraColors.text1,
                    unfocusedTextColor = astraColors.text1,
                ),
            )

            when {
                state.loading && state.empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                state.query.trim().length < 2 -> EmptyState(
                    line = "Busca em toda a Astra",
                    hint = "digite 2+ letras pra achar mensagens, orbitas, estrelas e constelacoes",
                )
                state.empty -> EmptyState(
                    line = "Nada encontrado",
                    hint = "tenta outro termo",
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    val r = state.results
                    if (r.servers.isNotEmpty()) {
                        item { SectionHeader("Constelacoes") }
                        r.servers.forEach { s ->
                            item(key = "srv-${s.id}") {
                                ServerResult(s) {
                                    focusManager.clearFocus(); onOpenServer(s.id, s.name)
                                }
                            }
                        }
                    }
                    if (r.channels.isNotEmpty()) {
                        item { SectionHeader("Orbitas") }
                        r.channels.forEach { c ->
                            item(key = "ch-${c.id}") {
                                ChannelResult(c) {
                                    focusManager.clearFocus(); onOpenChannel(c.id, c.name)
                                }
                            }
                        }
                    }
                    if (r.users.isNotEmpty()) {
                        item { SectionHeader("Estrelas") }
                        r.users.forEach { u ->
                            item(key = "usr-${u.id}") {
                                UserResult(u) {
                                    focusManager.clearFocus(); onOpenUser(u.id, u.displayName ?: u.username)
                                }
                            }
                        }
                    }
                    if (r.messages.isNotEmpty()) {
                        item { SectionHeader("Sussurros") }
                        r.messages.forEach { m ->
                            item(key = "msg-${m.id}") {
                                MessageResult(m) {
                                    focusManager.clearFocus(); onOpenChannel(m.channelId, m.channelName)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    MarginaliaLabel(
        "— ${text.lowercase()}",
        Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun RowCard(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ServerResult(s: SearchServerDto, onClick: () -> Unit) = RowCard(onClick) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(40.dp).clip(shape).background(astraColors.raised).border(1.dp, astraColors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!s.iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = s.iconUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(shape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(s.name.take(1).uppercase(), fontFamily = DmSerif, color = astraColors.accent, fontSize = 18.sp)
        }
    }
    Spacer(Modifier.width(12.dp))
    Text(
        s.name, style = MaterialTheme.typography.titleMedium, color = astraColors.text1,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
    )
    MarginaliaLabel(if (s.isGroup) "grupo" else "constelacao")
}

@Composable
private fun ChannelResult(c: SearchChannelDto, onClick: () -> Unit) = RowCard(onClick) {
    Text(if (c.type == "VOICE") "🔊" else "#", color = astraColors.text3, fontSize = 16.sp, modifier = Modifier.width(28.dp))
    Column(Modifier.weight(1f)) {
        Text(c.name, style = MaterialTheme.typography.titleMedium, color = astraColors.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        MarginaliaLabel(c.serverName)
    }
}

@Composable
private fun UserResult(u: SearchUserDto, onClick: () -> Unit) = RowCard(onClick) {
    AstraAvatar(u.avatarUrl, u.displayName ?: u.username, size = 40)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
        Text(u.displayName ?: u.username, style = MaterialTheme.typography.titleMedium, color = astraColors.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        MarginaliaLabel("@${u.username}")
    }
}

@Composable
private fun MessageResult(m: SearchMessageDto, onClick: () -> Unit) = RowCard(onClick) {
    AstraAvatar(m.author?.avatarUrl, m.author?.displayName ?: m.author?.username ?: "?", size = 36)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.author?.displayName ?: m.author?.username ?: "alguem",
                style = MaterialTheme.typography.titleSmall, color = astraColors.text1, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            MarginaliaLabel("#${m.channelName}")
        }
        Text(
            m.content, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}
