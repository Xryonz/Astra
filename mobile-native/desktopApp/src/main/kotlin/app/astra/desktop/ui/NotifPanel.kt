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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.NotificationApi
import app.astra.mobile.core.network.dto.NotificationItemDto
import com.composables.icons.lucide.AtSign
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Reply
import com.composables.icons.lucide.Users
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.core.context.GlobalContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Notificacoes-A: painel dropdown do sino (titlebar). Lista agrupada por dia,
// clique navega + marca lida, "marcar tudo" no topo. Contra /api/notifications.

@Serializable
private data class NotifPayload(
    val channelId: String? = null,
    val channelName: String? = null,
    val serverId: String? = null,
    val serverName: String? = null,
    val conversationId: String? = null,
    val authorName: String? = null,
    val authorAvatar: String? = null,
    val preview: String? = null,
    val emoji: String? = null,
)

@Composable
fun NotifPanel(
    onClose: () -> Unit,
    onOpenChannel: (serverId: String, channelId: String, name: String) -> Unit,
    onOpenDm: (convId: String, title: String) -> Unit,
    onOpenServer: (serverId: String) -> Unit,
    onAfterRead: () -> Unit,
) {
    val json = remember { GlobalContext.get().get<Json>() }
    val api = remember { GlobalContext.get().get<NotificationApi>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var items by remember { mutableStateOf<List<NotificationItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        items = runCatching { api.list(limit = 40).data?.items }.getOrNull().orEmpty()
        loading = false
    }

    fun parse(it: NotificationItemDto): NotifPayload =
        it.payload?.let { p -> runCatching { json.decodeFromJsonElement<NotifPayload>(p) }.getOrNull() } ?: NotifPayload()

    fun open(item: NotificationItemDto) {
        val p = parse(item)
        when (item.type) {
            "mention", "reply", "reaction" ->
                if (p.serverId != null && p.channelId != null) onOpenChannel(p.serverId, p.channelId, p.channelName ?: "canal")
            "dm" -> if (p.conversationId != null) onOpenDm(p.conversationId, p.authorName ?: "sussurro")
            "server_invite" -> if (p.serverId != null) onOpenServer(p.serverId)
        }
        // Marca lida otimista + persiste.
        if (item.readAt == null) {
            items = items.map { if (it.id == item.id) it.copy(readAt = "read") else it }
            scope.launch { runCatching { api.markRead(item.id) }; onAfterRead() }
        }
        onClose()
    }

    fun readAll() {
        items = items.map { if (it.readAt == null) it.copy(readAt = "read") else it }
        scope.launch { runCatching { api.readAll() }; onAfterRead() }
    }

    // Scrim: clique fora fecha. Painel ancorado no topo-direita (sob o sino).
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 8.dp)
                .width(390.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            // Cabecalho.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("notificações", style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif))
                Spacer(Modifier.weight(1f))
                if (items.any { it.readAt == null }) {
                    val src = remember { MutableInteractionSource() }
                    val hov by src.collectIsHoveredAsState()
                    Text(
                        "marcar tudo",
                        style = TextStyle(color = if (hov) Obsidian.accent else Obsidian.text3, fontSize = 11.sp),
                        modifier = Modifier.hoverable(src).clickable(interactionSource = src, indication = null) { readAll() },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Obsidian.borderDim.copy(alpha = 0.6f)))

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    Text("carregando…", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
                }
                items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LIcon(Lucide.Bell, tint = Obsidian.text3, size = 26.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("tudo em dia", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
                    }
                }
                else -> {
                    // Agrupa por dia (HOJE / ONTEM / dd/mm), mantendo a ordem desc.
                    val grouped = remember(items) { groupByDay(items) }
                    LazyColumn(
                        Modifier.heightIn(max = 460.dp).padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        grouped.forEach { (day, dayItems) ->
                            item(key = "day-$day") { DayLabel(day) }
                            items(dayItems, key = { it.id }) { it ->
                                NotifRow(it, parse(it)) { open(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayLabel(t: String) {
    Text(
        t.uppercase(),
        style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun NotifRow(item: NotificationItemDto, p: NotifPayload, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val unread = item.readAt == null
    val bg by animateColorAsState(if (hov) Obsidian.hover else Color.Transparent, tween(100))
    val author = p.authorName ?: "alguém"
    val (title, sub) = when (item.type) {
        "mention"       -> "$author mencionou você" to channelSub(p)
        "reply"         -> "$author respondeu você" to channelSub(p)
        "reaction"      -> "$author reagiu ${p.emoji ?: ""}".trim() to channelSub(p)
        "dm"            -> author to (p.preview ?: "sussurro")
        "server_invite" -> "entrou em ${p.serverName ?: "uma constelação"}" to "convite"
        else            -> author to (p.preview ?: "")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Ponto de nao-lida (some quando lida).
        Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(if (unread) Obsidian.accent else Color.Transparent))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Obsidian.base), contentAlignment = Alignment.Center) {
            LIcon(iconFor(item.type), tint = Obsidian.text2, size = 15.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = TextStyle(color = if (unread) Obsidian.text1 else Obsidian.text2, fontSize = 12.sp, fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(relTime(item.createdAt), style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
            }
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(sub, style = TextStyle(color = Obsidian.text3, fontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (item.type != "dm" && !p.preview.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("“${p.preview}”", style = TextStyle(color = Obsidian.text2, fontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun channelSub(p: NotifPayload): String {
    val ch = p.channelName?.let { "#$it" }
    return listOfNotNull(ch, p.serverName).joinToString(" · ")
}

private fun iconFor(type: String) = when (type) {
    "mention"       -> Lucide.AtSign
    "reply"         -> Lucide.Reply
    "reaction"      -> Lucide.Heart
    "dm"            -> Lucide.MessageSquare
    "server_invite" -> Lucide.Users
    else            -> Lucide.Bell
}

// ---- tempo ----

private fun instantOf(iso: String): Instant? = runCatching { Instant.parse(iso) }.getOrNull()

private fun relTime(iso: String): String {
    val t = instantOf(iso) ?: return ""
    val now = Instant.now()
    val mins = ChronoUnit.MINUTES.between(t, now)
    return when {
        mins < 1 -> "agora"
        mins < 60 -> "${mins}m"
        mins < 60 * 24 -> "${mins / 60}h"
        else -> "${mins / (60 * 24)}d"
    }
}

private fun groupByDay(items: List<NotificationItemDto>): List<Pair<String, List<NotificationItemDto>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return items.groupBy { n ->
        val d = instantOf(n.createdAt)?.atZone(zone)?.toLocalDate() ?: today
        when (d) {
            today -> "hoje"
            today.minusDays(1) -> "ontem"
            else -> "%02d/%02d".format(d.dayOfMonth, d.monthValue)
        }
    }.toList()
}
