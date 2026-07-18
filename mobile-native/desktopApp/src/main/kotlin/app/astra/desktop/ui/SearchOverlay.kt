package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.SearchApi
import app.astra.mobile.core.network.dto.SearchChannelDto
import app.astra.mobile.core.network.dto.SearchMessageDto
import app.astra.mobile.core.network.dto.SearchResultsDto
import app.astra.mobile.core.network.dto.SearchUserDto
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Volume2
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext

// Busca-A: palette dedicada (lupa no titlebar). Abas Tudo/Mensagens/Canais/
// Pessoas contra GET /api/search. Escopo padrao = constelacao atual, com toggle
// "buscar em tudo" (mensagens/canais filtram por serverId; pessoas sao globais).

private enum class SearchTab(val label: String) {
    ALL("Tudo"), MESSAGES("Mensagens"), CHANNELS("Canais"), PEOPLE("Pessoas")
}

@Composable
fun SearchOverlay(
    currentServerId: String?,
    onClose: () -> Unit,
    onOpenChannel: (serverId: String, channelId: String, name: String) -> Unit,
    onWhisper: (username: String, title: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(SearchTab.ALL) }
    // Sem constelacao aberta (ex.: nos sussurros), busca sempre global.
    var onlyHere by remember { mutableStateOf(currentServerId != null) }
    var results by remember { mutableStateOf(SearchResultsDto()) }
    var loading by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Um request por query (scope=all); as abas so filtram o que aparece. Debounce
    // curto; < 2 chars o backend devolve vazio, entao nem chama.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = SearchResultsDto(); loading = false; return@LaunchedEffect }
        loading = true
        delay(300)
        results = runCatching { GlobalContext.get().get<SearchApi>().search(q, "all").data }
            .getOrNull() ?: SearchResultsDto()
        loading = false
    }

    val serverFilter = onlyHere && currentServerId != null
    val msgs = results.messages.let { if (serverFilter) it.filter { m -> m.serverId == currentServerId } else it }
    val chans = results.channels.let { if (serverFilter) it.filter { c -> c.serverId == currentServerId } else it }
    val people = results.users

    Box(
        Modifier
            .fillMaxSize()
            .background(Obsidian.void.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 84.dp)
                .width(560.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false
                }
                .padding(12.dp),
        ) {
            // Campo de busca com lupa.
            Row(verticalAlignment = Alignment.CenterVertically) {
                LIcon(Lucide.Search, tint = Obsidian.text3, size = 17.dp)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Obsidian.text1, fontSize = 15.sp),
                    cursorBrush = SolidColor(Obsidian.accent),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            if (query.isEmpty()) {
                                Text(
                                    "buscar mensagens, canais, pessoas…",
                                    style = TextStyle(color = Obsidian.text3, fontSize = 15.sp),
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            // Abas + toggle de escopo.
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchTab.entries.forEach { t ->
                    TabPill(t.label, active = tab == t) { tab = t }
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                if (currentServerId != null) {
                    ScopeToggle(onlyHere) { onlyHere = !onlyHere }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Obsidian.borderDim.copy(alpha = 0.6f)))
            Spacer(Modifier.height(8.dp))

            val empty = msgs.isEmpty() && chans.isEmpty() && people.isEmpty()
            when {
                query.trim().length < 2 -> Hint("digite ao menos 2 letras")
                loading && empty -> Hint("procurando…")
                empty -> Hint("nada encontrado")
                else -> LazyColumn(
                    Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val showMsgs = tab == SearchTab.ALL || tab == SearchTab.MESSAGES
                    val showChans = tab == SearchTab.ALL || tab == SearchTab.CHANNELS
                    val showPeople = tab == SearchTab.ALL || tab == SearchTab.PEOPLE

                    if (showChans && chans.isNotEmpty()) {
                        item { SectionLabel("canais") }
                        items(chans.size) { i ->
                            val c = chans[i]
                            ChannelResult(c) {
                                if (c.serverId.isNotBlank()) { onOpenChannel(c.serverId, c.id, c.name); onClose() }
                            }
                        }
                    }
                    if (showPeople && people.isNotEmpty()) {
                        item { SectionLabel("pessoas") }
                        items(people.size) { i ->
                            val u = people[i]
                            PersonResult(u) {
                                onWhisper(u.username, u.displayName ?: u.username); onClose()
                            }
                        }
                    }
                    if (showMsgs && msgs.isNotEmpty()) {
                        item { SectionLabel("mensagens") }
                        items(msgs.size) { i ->
                            val m = msgs[i]
                            MessageResult(m) {
                                if (m.serverId.isNotBlank()) { onOpenChannel(m.serverId, m.channelId, m.channelName); onClose() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(t: String) {
    Text(t, style = TextStyle(color = Obsidian.text3, fontSize = 12.sp), modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun SectionLabel(t: String) {
    Text(
        t.uppercase(),
        style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun TabPill(label: String, active: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (active) Obsidian.accentDim else if (hov) Obsidian.hover else Color.Transparent, tween(120))
    Text(
        label,
        style = TextStyle(color = if (active) Obsidian.accent else Obsidian.text2, fontSize = 12.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun ScopeToggle(onlyHere: Boolean, onToggle: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Text(
        if (onlyHere) "só esta constelação" else "buscar em tudo",
        style = TextStyle(color = if (hov) Obsidian.text1 else Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, if (onlyHere) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(7.dp))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onToggle)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun ResultRow(onClick: () -> Unit, content: @Composable RowScope.(hovered: Boolean) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hov) Obsidian.hover else Color.Transparent, tween(100))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content(hov) }
}

@Composable
private fun ChannelResult(c: SearchChannelDto, onClick: () -> Unit) = ResultRow(onClick) {
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Obsidian.base), contentAlignment = Alignment.Center) {
        LIcon(if (c.type == "VOICE") Lucide.Volume2 else Lucide.Hash, tint = Obsidian.text3, size = 15.dp)
    }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
        Text(c.name, style = TextStyle(color = Obsidian.text1, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (c.serverName.isNotBlank()) {
            Text(c.serverName, style = TextStyle(color = Obsidian.text3, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PersonResult(u: SearchUserDto, onClick: () -> Unit) = ResultRow(onClick) {
    DesktopAvatar(u.avatarUrl, u.displayName ?: u.username, 28)
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
        Text(u.displayName ?: u.username, style = TextStyle(color = Obsidian.text1, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("@${u.username}", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Text("sussurrar", style = TextStyle(color = Obsidian.accent, fontSize = 11.sp))
}

@Composable
private fun MessageResult(m: SearchMessageDto, onClick: () -> Unit) = ResultRow(onClick) {
    DesktopAvatar(m.author?.avatarUrl, m.author?.displayName ?: m.author?.username ?: "?", 28)
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.author?.displayName ?: m.author?.username ?: "alguém",
                style = TextStyle(color = Obsidian.text1, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            LIcon(Lucide.Hash, tint = Obsidian.text3, size = 10.dp)
            Text(
                m.channelName.ifBlank { "canal" },
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            m.content,
            style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}
