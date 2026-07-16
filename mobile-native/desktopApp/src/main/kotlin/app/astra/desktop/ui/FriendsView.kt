package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.FriendApi
import app.astra.mobile.core.network.dto.FriendDto
import app.astra.mobile.core.network.dto.FriendRequestDto
import app.astra.mobile.core.network.dto.SendFriendRequest
import com.composables.icons.lucide.AtSign
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.UserMinus
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import retrofit2.HttpException

// Amigos (paridade web/mobile). Palco central: cabecalho + abas
// (Amigos/Pendentes/Adicionar) + conteudo. Dados do FriendApi (/api/friends).
// onStartDm abre/cria o sussurro com o amigo (mesmo do card de perfil).
private enum class FriendsTab(val label: String) { FRIENDS("Amigos"), PENDING("Pendentes"), ADD("Adicionar") }

private fun presenceRank(p: String): Int = when (p.uppercase()) {
    "ONLINE" -> 0; "IDLE" -> 1; "DND" -> 2; else -> 3
}

private fun presenceLabel(p: String): String = when (p.uppercase()) {
    "ONLINE" -> "online"; "IDLE" -> "ausente"; "DND" -> "ocupado"; else -> "offline"
}

@Composable
fun FriendsView(onStartDm: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val api = remember { GlobalContext.get().get<FriendApi>() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(FriendsTab.FRIENDS) }
    var friends by remember { mutableStateOf<List<FriendDto>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<FriendRequestDto>>(emptyList()) }
    var outgoing by remember { mutableStateOf<List<FriendRequestDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun reload() {
        val f = runCatching { api.friends().data.orEmpty() }.getOrDefault(emptyList())
        friends = f.sortedWith(
            compareBy({ presenceRank(it.presence) }, { (it.user.displayName ?: it.user.username).lowercase() }),
        )
        incoming = runCatching { api.requests().data.orEmpty() }.getOrDefault(emptyList())
        outgoing = runCatching { api.outgoing().data.orEmpty() }.getOrDefault(emptyList())
    }
    LaunchedEffect(Unit) { reload(); loading = false }

    // Acao otimista-simples: roda e recarrega as tres listas.
    fun act(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }; reload() }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LIcon(Lucide.Users, tint = Obsidian.accent, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text("Amigos", style = TextStyle(color = Obsidian.text1, fontSize = 20.sp, fontFamily = DmSerif))
        }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TabPill("Amigos", friends.size, tab == FriendsTab.FRIENDS) { tab = FriendsTab.FRIENDS }
            TabPill("Pendentes", incoming.size, tab == FriendsTab.PENDING) { tab = FriendsTab.PENDING }
            TabPill("Adicionar", null, tab == FriendsTab.ADD) { tab = FriendsTab.ADD }
        }
        Spacer(Modifier.height(6.dp))
        HairRule()
        Spacer(Modifier.height(14.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                FriendsTab.FRIENDS -> FriendsList(
                    loading, friends,
                    onMessage = { u -> onStartDm(u.username, u.displayName ?: u.username) },
                    onRemove = { id -> act { api.remove(id) } },
                )
                FriendsTab.PENDING -> PendingLists(
                    incoming, outgoing,
                    onAccept = { id -> act { api.accept(id) } },
                    onDrop = { id -> act { api.remove(id) } },
                )
                FriendsTab.ADD -> AddFriend { req, done ->
                    scope.launch {
                        val r = runCatching { api.sendRequest(req) }
                        done(r.isSuccess, (r.exceptionOrNull() as? HttpException)?.code())
                        reload()
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPill(label: String, count: Int?, active: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier
            .clickScale(src)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Obsidian.active else Obsidian.raised.copy(alpha = 0.4f))
            .border(1.dp, if (active) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = TextStyle(color = if (active) Obsidian.text1 else Obsidian.text3, fontSize = 13.sp, fontFamily = DmSerif),
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text("$count", style = TextStyle(color = if (active) Obsidian.accent else Obsidian.text3, fontSize = 11.sp))
        }
    }
}

@Composable
private fun FriendsList(
    loading: Boolean,
    items: List<FriendDto>,
    onMessage: (app.astra.mobile.core.network.dto.FriendUserDto) -> Unit,
    onRemove: (String) -> Unit,
) {
    when {
        loading && items.isEmpty() -> Center("carregando amigos…")
        items.isEmpty() -> Center("nenhum amigo ainda — adicione pela aba Adicionar")
        else -> {
            val online = items.count { presenceRank(it.presence) < 3 }
            Column(Modifier.fillMaxSize()) {
                if (online > 0) {
                    Text(
                        "$online acesa${if (online > 1) "s" else ""}",
                        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(items, key = { it.friendshipId }) { f ->
                        FriendRow(f, onMessage = { onMessage(f.user) }, onRemove = { onRemove(f.friendshipId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(f: FriendDto, onMessage: () -> Unit, onRemove: () -> Unit) {
    val name = f.user.displayName ?: f.user.username
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            DesktopAvatar(f.user.avatarUrl, name, 40)
            StatusDot(
                userStatus(f.presence),
                bordered = true,
                size = 13.dp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = TextStyle(color = Obsidian.text1, fontSize = 14.sp, fontFamily = DmSerif),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(7.dp))
                Text("@${f.user.username}", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp), maxLines = 1)
            }
            Text(
                buildString {
                    append(presenceLabel(f.presence))
                    f.user.customStatus?.takeIf { it.isNotBlank() }?.let { append(" · "); append(it) }
                },
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        RowIconButton(Lucide.MessageCircle, tint = Obsidian.accent, onClick = onMessage)
        Spacer(Modifier.width(4.dp))
        RowIconButton(Lucide.UserMinus, tint = Obsidian.danger, onClick = onRemove)
    }
}

@Composable
private fun PendingLists(
    incoming: List<FriendRequestDto>,
    outgoing: List<FriendRequestDto>,
    onAccept: (String) -> Unit,
    onDrop: (String) -> Unit,
) {
    if (incoming.isEmpty() && outgoing.isEmpty()) {
        Center("nenhum pedido pendente")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            SectionHeader(Lucide.Inbox, Obsidian.accent, "recebidos", incoming.size)
        }
        if (incoming.isEmpty()) {
            item { Muted("nada por aqui") }
        } else {
            items(incoming, key = { it.friendshipId }) { p ->
                PendingRow(p, trailing = {
                    RowIconButton(Lucide.Check, tint = Obsidian.accent) { onAccept(p.friendshipId) }
                    Spacer(Modifier.width(4.dp))
                    RowIconButton(Lucide.X, tint = Obsidian.danger) { onDrop(p.friendshipId) }
                })
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            SectionHeader(Lucide.Send, Obsidian.text3, "enviados", outgoing.size)
        }
        if (outgoing.isEmpty()) {
            item { Muted("nenhum enviado") }
        } else {
            items(outgoing, key = { it.friendshipId }) { p ->
                PendingRow(p, trailing = {
                    val src = remember { MutableInteractionSource() }
                    Text(
                        "cancelar",
                        style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                        modifier = Modifier
                            .clickScale(src)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable(interactionSource = src, indication = null) { onDrop(p.friendshipId) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                })
            }
        }
    }
}

@Composable
private fun PendingRow(p: FriendRequestDto, trailing: @Composable () -> Unit) {
    val name = p.user?.displayName ?: p.user?.username ?: "usuario"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopAvatar(p.user?.avatarUrl, name, 36)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = TextStyle(color = Obsidian.text1, fontSize = 14.sp, fontFamily = DmSerif), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${p.user?.username ?: "?"}", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp), maxLines = 1)
        }
        trailing()
    }
}

@Composable
private fun AddFriend(onSend: (SendFriendRequest, (Boolean, Int?) -> Unit) -> Unit) {
    var value by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // texto + ok

    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
        Text(
            "adicione um amigo pelo nome de usuario.",
            style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Obsidian.base)
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) {
                    Text("nome de usuario", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
                }
                BasicTextField(
                    value = value,
                    onValueChange = { msg = null; value = it.take(64) },
                    singleLine = true,
                    textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                    cursorBrush = SolidColor(Obsidian.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            val canSend = !busy && value.trim().isNotEmpty()
            val src = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .clickScale(src)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, if (canSend) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(10.dp))
                    .clickable(interactionSource = src, indication = null, enabled = canSend) {
                        busy = true
                        msg = null
                        onSend(SendFriendRequest(username = value.trim())) { ok, code ->
                            busy = false
                            msg = when {
                                ok -> "pedido enviado" to true
                                code == 404 -> "usuario nao encontrado" to false
                                code == 400 -> "confira os dados" to false
                                else -> "nao deu — tente de novo" to false
                            }
                            if (ok) value = ""
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LIcon(Lucide.UserPlus, tint = if (canSend) Obsidian.accent else Obsidian.text3, size = 15.dp)
                Spacer(Modifier.width(7.dp))
                Text(if (busy) "enviando…" else "enviar", style = TextStyle(color = if (canSend) Obsidian.accent else Obsidian.text3, fontSize = 13.sp))
            }
        }
        msg?.let { (text, ok) ->
            Spacer(Modifier.height(10.dp))
            Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, label: String, count: Int) {
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LIcon(icon, tint = tint, size = 14.dp)
        Spacer(Modifier.width(7.dp))
        Text(label, style = TextStyle(color = Obsidian.text2, fontSize = 13.sp, fontFamily = DmSerif))
        Spacer(Modifier.width(6.dp))
        Text("· $count", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
    }
}

@Composable
private fun RowIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(32.dp)
            .clickScale(src)
            .clip(CircleShape)
            .border(1.dp, Obsidian.borderDim, CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, tint = tint, size = 15.dp)
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = TextStyle(color = Obsidian.text3, fontSize = 12.sp), modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp))
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
    }
}
