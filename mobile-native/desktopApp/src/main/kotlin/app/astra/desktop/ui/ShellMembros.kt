package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.dto.MemberRoleDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.UserMinus
import com.composables.icons.lucide.Users

@Composable
internal fun MembersPanel(
    members: List<ServerMemberDto>,
    presence: Map<String, String>,
    atividade: Map<String, String>,
    myId: String?,
    serverId: String?,
    isOwner: Boolean,
    onStartDm: (String, String) -> Unit,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
) {
    val rows = remember(members, presence, myId) { buildMemberRows(members, presence, myId) }
    val forma = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    Column(
        Modifier
            .width(240.dp)
            .fillMaxHeight()
            .clip(forma)
            .panelSurface(Obsidian.base, 0.62f)
            .border(1.dp, Obsidian.borderMid.copy(alpha = 0.5f), forma),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            items(rows, key = { row -> row.key }) { row ->
                when (row) {
                    is MemberPanelRow.Header -> MemberSectionHeader(row.label, row.count, row.iconUrl)
                    is MemberPanelRow.Person -> MemberRow(
                        m = row.m,
                        online = row.online,
                        atividade = atividade[row.m.userId],
                        cascadeIndex = row.cascadeIndex,
                        cascadeTotal = members.size,
                        isMe = row.m.userId == myId,
                        serverId = serverId,
                        isOwner = isOwner,
                        onStartDm = onStartDm,
                        onKick = onKick,
                        onBan = onBan,
                    )
                }
            }
        }
    }
}

private sealed interface MemberPanelRow {
    val key: String
    data class Header(val id: String, val label: String, val count: Int, val iconUrl: String?) : MemberPanelRow {
        override val key get() = "h:$id"
    }
    data class Person(val m: ServerMemberDto, val online: Boolean, val cascadeIndex: Int) : MemberPanelRow {
        override val key get() = "m:${m.userId}"
    }
}

private fun buildMemberRows(members: List<ServerMemberDto>, presence: Map<String, String>, myId: String?): List<MemberPanelRow> {
    fun online(uid: String) = uid == myId || presence[uid]?.let { it != "OFFLINE" } == true
    val chave = HashMap<String, String>(members.size)
    for (m in members) chave[m.userId] = (m.user.displayName ?: m.user.username).lowercase()
    fun nameOf(m: ServerMemberDto) = chave[m.userId].orEmpty()

    val roleById = HashMap<String, MemberRoleDto>()
    val buckets = LinkedHashMap<String, MutableList<ServerMemberDto>>()
    for (m in members) {
        val r = m.roles.filter { it.hoist }.maxByOrNull { it.position }
        val key = r?.id ?: ""
        if (r != null) roleById[key] = r
        buckets.getOrPut(key) { mutableListOf() }.add(m)
    }
    val order = buckets.keys.sortedByDescending { roleById[it]?.position ?: Int.MIN_VALUE }

    val out = ArrayList<MemberPanelRow>()
    var idx = 0
    for (key in order) {
        val role = roleById[key]
        val list = buckets[key] ?: continue
        val on = list.filter { online(it.userId) }.sortedBy { nameOf(it) }
        val off = list.filter { !online(it.userId) }.sortedBy { nameOf(it) }
        out.add(MemberPanelRow.Header(key.ifEmpty { "members" }, role?.name?.uppercase() ?: "MEMBROS", list.size, role?.iconUrl))
        for (m in on + off) out.add(MemberPanelRow.Person(m, online(m.userId), idx++))
    }
    return out
}

@Composable
private fun MemberSectionHeader(label: String, count: Int, iconUrl: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!iconUrl.isNullOrBlank()) {
            Box(Modifier.size(15.dp).clip(CircleShape).background(Obsidian.overlay)) {
                AstraImage(iconUrl, label, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = "$label — $count",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, letterSpacing = 0.6.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MemberRow(
    m: ServerMemberDto,
    online: Boolean,
    atividade: String?,
    cascadeIndex: Int,
    cascadeTotal: Int,
    isMe: Boolean,
    serverId: String?,
    isOwner: Boolean,
    onStartDm: (String, String) -> Unit,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val name = m.user.displayName ?: m.user.username
    val corDoNome = if (online) corDoMembro(m) else null
    val padraoDoNome = if (online) Obsidian.text2 else Obsidian.text3.copy(alpha = 0.65f)
    val avatarAlpha = if (online) 1f else 0.4f
    CascadeIn(cascadeIndex, cascadeTotal) {
        var confirmMember by remember(m.userId) { mutableStateOf<String?>(null) }
        EditorialContextMenu(entries = {
            buildList {
                if (!isMe) add(MenuEntry.Item("sussurro", icon = Lucide.MessageCircle) { onStartDm(m.user.username, name) })
                add(MenuEntry.Item("copiar ID", icon = Lucide.Copy) { clipboard.setText(AnnotatedString(m.userId)) })
                if (isOwner && !isMe && serverId != null) {
                    add(MenuEntry.Separator)
                    add(MenuEntry.Item("expulsar", danger = true, icon = Lucide.UserMinus) { confirmMember = "kick" })
                    add(MenuEntry.Item("banir", danger = true, icon = Lucide.Ban) { confirmMember = "ban" })
                }
            }
        }) {
            confirmMember?.let { act ->
                ConfirmPopup(
                    message = if (act == "ban") "banir ${name}? a pessoa não poderá voltar." else "expulsar ${name}?",
                    confirmLabel = if (act == "ban") "banir" else "expulsar",
                    onConfirm = { if (act == "ban") onBan(m.userId) else onKick(m.id) },
                    onDismiss = { confirmMember = null },
                )
            }
            ProfileAnchor(m.userId, isMe = isMe, onStartDm = onStartDm, cargos = m.roles) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.graphicsLayer { alpha = avatarAlpha }) {
                        DesktopAvatar(m.user.avatarUrl, name, 26)
                    }
                    Spacer(Modifier.width(9.dp))
                    Column {
                        NomeColorido(
                            texto = name,
                            cor = corDoNome,
                            padrao = padraoDoNome,
                            fontSize = 13.sp,
                            fontFamily = m.user.displayFont?.let { profileFontFamily(it) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (atividade != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(Obsidian.accent.copy(alpha = if (online) 0.85f else 0.4f)),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = atividade,
                                    style = Tipo.apoio,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun memberRoleColor(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}

internal fun corDoMembro(m: ServerMemberDto): CorDoNome? =
    lerCorDoNome(m.topColor) ?: lerCorDoNome(m.nameColor)

internal fun coresDeCargo(membros: List<ServerMemberDto>): Map<String, CorDoNome> =
    membros.mapNotNull { m -> corDoMembro(m)?.let { m.userId to it } }.toMap()

@Composable
internal fun FriendsNavRow(active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (active) Obsidian.active else if (hovered) Obsidian.hover else Color.Transparent,
        tween(120),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(Lucide.Users, tint = if (active) Obsidian.accent else Obsidian.text3, size = 16.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            "Amigos",
            style = TextStyle(
                color = if (active || hovered) Obsidian.text1 else Obsidian.text2,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}
