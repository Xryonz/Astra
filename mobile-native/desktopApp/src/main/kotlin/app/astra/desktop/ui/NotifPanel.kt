package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.composables.icons.lucide.UserPlus
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
    // Rede de seguranca do nome: displayName pode faltar em conta antiga, o @ nunca.
    val authorUsername: String? = null,
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
    val socket = remember { GlobalContext.get().get<app.astra.desktop.net.DesktopSocket>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var items by remember { mutableStateOf<List<NotificationItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        items = runCatching { api.list(limit = 40).data?.items }.getOrNull().orEmpty()
        loading = false
    }

    // O runCatching aqui e uma fabrica de bug invisivel: qualquer problema no
    // payload virava um NotifPayload vazio, e a linha aparecia como "alguém" sem
    // pista nenhuma do motivo. Continua não quebrando a tela — mas agora deixa
    // rastro no Diagnostico, que e o que separa "chegou torto" de "chegou vazio".
    fun parse(it: NotificationItemDto): NotifPayload {
        val p = it.payload ?: return NotifPayload()
        return runCatching { json.decodeFromJsonElement<NotifPayload>(p) }
            .onFailure { e -> socket.noteLocal("· notificação ilegível: ${e.message?.take(50)}") }
            .getOrNull() ?: NotifPayload()
    }

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

    // LIMPAR e diferente de MARCAR TUDO, e por isso sao dois botoes: marcar zera o
    // sino e mantem o historico; limpar apaga. A lista some na hora (otimista) e
    // VOLTA se o servidor recusar — sumir e reaparecer e chato, mas fingir que
    // apagou o que continua la e pior.
    var limpando by remember { mutableStateOf(false) }
    fun limparTudo() {
        if (limpando) return
        val antes = items
        limpando = true
        items = emptyList()
        scope.launch {
            runCatching { api.clearAll() }
                .onSuccess { onAfterRead() }
                .onFailure { items = antes }
            limpando = false
        }
    }

    // NO MEIO DA TELA (pedido do dono). Era um dropdown colado sob o sino, e o
    // dropdown tem um defeito que so aparece com a lista cheia: ele desce a partir do
    // canto superior direito, ou seja, obriga o olho a ler a coisa mais importante no
    // ponto mais distante de onde ele estava. Centralizado, a lista nasce onde o olho
    // ja esta — e a largura pode crescer sem espremer o conteudo contra a borda.
    //
    // O veu escuro e o mesmo da Busca de proposito: as duas sao "isto toma a tela ate
    // voce resolver", e duas coisas com a mesma funcao devem falar a mesma lingua.
    Box(
        Modifier
            .fillMaxSize()
            .background(Obsidian.void.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .semCursorDeClique(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                // Cresce do proprio centro: o painel nao vem mais "de" lugar nenhum,
                // entao a origem da escala e ele mesmo.
                .popupReveal(originX = 0.5f, originY = 0.5f)
                .width(520.dp)
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
                if (items.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    val srcLimpar = remember { MutableInteractionSource() }
                    val hovLimpar by srcLimpar.collectIsHoveredAsState()
                    Text(
                        if (limpando) "limpando…" else "limpar",
                        style = TextStyle(
                            color = if (hovLimpar) Obsidian.danger else Obsidian.text3,
                            fontSize = 11.sp,
                        ),
                        modifier = Modifier
                            .hoverable(srcLimpar)
                            .clickable(interactionSource = srcLimpar, indication = null) { limparTudo() },
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
    val author = p.authorName ?: p.authorUsername?.let { "@$it" } ?: "alguém"
    val (title, sub) = when (item.type) {
        "mention"       -> "$author mencionou você" to channelSub(p)
        "reply"         -> "$author respondeu você" to channelSub(p)
        "reaction"      -> "$author reagiu ${p.emoji ?: ""}".trim() to channelSub(p)
        "dm"            -> author to (p.preview ?: "sussurro")
        "server_invite" -> "entrou em ${p.serverName ?: "uma constelação"}" to "convite"
        // Sem destino próprio: clicar não navega (o `open` não trata este tipo), e
        // isso é deliberado — a ação mora na tela de Amigos, e mandar a pessoa pra
        // lá com um clique atravessaria o que ela estava fazendo. A notificação
        // avisa; aceitar continua sendo uma ida consciente.
        "friend_request" -> "$author quer te adicionar" to "pedido de amizade"
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
        // Ponto de não-lida (some quando lida).
        Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(if (unread) Obsidian.accent else Color.Transparent))
        Spacer(Modifier.width(8.dp))
        // O ROSTO RESPONDE "QUEM", que é a primeira pergunta de quem abre o sino.
        //
        // Aqui havia o ícone do TIPO, e ele era informação repetida: o título já diz
        // "mencionou você", "respondeu você", "reagiu ❤" com todas as letras. Um
        // balãozinho igual em cada linha fazia a lista inteira parecer a mesma coisa
        // — o painel ficava sendo uma coluna de nomes, e reconhecer quem escreveu
        // exigia LER, em vez de bater o olho.
        //
        // Sem autor (convite de constelação) o ícone continua: não há rosto pra
        // mostrar, e um espaço vazio seria pior que o símbolo.
        val quem = p.authorName ?: p.authorUsername
        if (quem != null || p.authorAvatar != null) {
            DesktopAvatar(p.authorAvatar, quem ?: "?", 30)
        } else {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Obsidian.base), contentAlignment = Alignment.Center) {
                LIcon(iconFor(item.type), tint = Obsidian.text2, size = 15.dp)
            }
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
    "friend_request" -> Lucide.UserPlus
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
