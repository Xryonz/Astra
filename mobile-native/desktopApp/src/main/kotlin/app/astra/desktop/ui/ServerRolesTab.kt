package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.RoleDto
import app.astra.mobile.core.network.dto.RoleRequest
import app.astra.mobile.core.network.dto.ServerMemberDto
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

// Aba CARGOS. Lista -> clicar abre o editor NO LUGAR da lista (decisao do dono):
// os 7 interruptores de permissao cabem sem apertar e cada tela respira.
//
// Regra que molda esta tela: o backend passa toda permissao por grantableSubset,
// que DESCARTA em silencio o que o ator nao possui (menos o dono) e ainda assim
// responde 200. Se a UI deixasse marcar o que voce nao tem, ela diria "salvo" e a
// permissao nao estaria la. Por isso os interruptores fora do teu alcance ficam
// desligados e explicados, em vez de mentir.

// Rotulo e explicacao de cada permissao. A ordem e do mais forte pro mais fraco.
private val PERMISSIONS = listOf(
    Triple("MANAGE_SERVER", "Gerenciar a constelacao", "mudar nome, imagens, convite e visibilidade"),
    Triple("MANAGE_ROLES", "Gerenciar cargos", "criar, editar e dar cargos a outras pessoas"),
    Triple("MANAGE_CHANNELS", "Gerenciar orbitas", "criar, renomear, mover e apagar canais"),
    Triple("KICK_MEMBERS", "Expulsar membros", "remove da constelacao; da pra voltar com convite"),
    Triple("BAN_MEMBERS", "Banir membros", "remove e impede de voltar"),
    Triple("MANAGE_MESSAGES", "Gerenciar mensagens", "apagar mensagem de outros e fixar"),
    Triple("MENTION_EVERYONE", "Mencionar todos", "usar @everyone pra avisar a constelacao inteira"),
)

// Paleta de cargos: tons que se leem sobre o obsidiana sem virar neon. Quem quer
// exata digita o hex no mesmo popup.
private val ROLE_COLORS = listOf(
    "#c9a96e", "#e0b877", "#d99a6c", "#c97c6e", "#c96e8a",
    "#a87cc4", "#7c6fc4", "#6f8fc4", "#6fa8c9", "#6fc4b8",
    "#6ec98a", "#a3c96e", "#c9c46e", "#9aa0aa",
)

@Composable
internal fun RolesSection(
    roles: List<RoleDto>?,
    members: List<ServerMemberDto>,
    // Permissoes de QUEM ESTA MEXENDO: define o que da pra marcar.
    myPermissions: Set<String>,
    amOwner: Boolean,
    error: String?,
    onSave: (roleId: String?, RoleRequest, (String?) -> Unit) -> Unit,
    onDelete: (roleId: String, (String?) -> Unit) -> Unit,
    onToggleMember: (memberId: String, roleId: String, give: Boolean, (String?) -> Unit) -> Unit,
) {
    // null = lista; "" = criando um cargo novo; id = editando aquele cargo.
    var editing by remember(roles) { mutableStateOf<String?>(null) }

    if (roles == null) {
        Text(
            error ?: "carregando cargos…",
            style = TextStyle(color = if (error != null) Obsidian.danger else Obsidian.text3, fontSize = 12.sp),
        )
        return
    }

    AnimatedContent(
        targetState = editing,
        transitionSpec = {
            // Entrar no editor desliza pra esquerda; voltar, pra direita — o gesto
            // diz de que lado voce veio.
            val forward = targetState != null
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(200)) { it / 8 * dir } + fadeIn(tween(200)))
                .togetherWith(slideOutHorizontally(tween(160)) { -it / 8 * dir } + fadeOut(tween(120)))
        },
        label = "rolesPane",
    ) { current ->
        Column(Modifier.fillMaxWidth()) {
            if (current == null) {
                RoleList(roles, members, onPick = { editing = it }, onNew = { editing = "" })
            } else {
                val role = roles.find { it.id == current }
                RoleEditor(
                    role = role,
                    members = members,
                    myPermissions = myPermissions,
                    amOwner = amOwner,
                    onBack = { editing = null },
                    onSave = { body, cb -> onSave(role?.id, body) { err -> if (err == null) editing = null; cb(err) } },
                    onDelete = { cb -> role?.let { r -> onDelete(r.id) { err -> if (err == null) editing = null; cb(err) } } },
                    onToggleMember = onToggleMember,
                )
            }
        }
    }
}

@Composable
private fun RoleList(
    roles: List<RoleDto>,
    members: List<ServerMemberDto>,
    onPick: (String) -> Unit,
    onNew: () -> Unit,
) {
    Text(
        "cargos dao permissoes e cor ao nome. Quem tem varios fica com a cor do mais alto.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(14.dp))

    if (roles.isEmpty()) {
        Text(
            "nenhum cargo ainda.",
            style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
        )
        Spacer(Modifier.height(12.dp))
    }
    // Mais alto primeiro: a posicao e a hierarquia.
    roles.sortedByDescending { it.position }.forEach { role ->
        val count = members.count { m -> m.roles.any { it.id == role.id } }
        RoleRow(role, count) { onPick(role.id) }
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Obsidian.accentDim, RoundedCornerShape(8.dp))
            .clickable(onClick = onNew)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(Lucide.Plus, tint = Obsidian.accent, size = 14.dp)
        Spacer(Modifier.width(8.dp))
        Text("novo cargo", style = TextStyle(color = Obsidian.accent, fontSize = 13.sp))
    }
}

@Composable
private fun RoleRow(role: RoleDto, memberCount: Int, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (hov) Obsidian.hover else Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(roleColor(role.color) ?: Obsidian.text3),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                role.name,
                style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(if (memberCount == 1) "1 membro" else "$memberCount membros")
                    if (role.hoist) append(" · separado na lista")
                    if (role.permissions.isNotEmpty()) append(" · ${role.permissions.size} permissoes")
                },
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        LIcon(Lucide.ChevronRight, tint = Obsidian.text3, size = 14.dp)
    }
}

@Composable
private fun RoleEditor(
    role: RoleDto?,
    members: List<ServerMemberDto>,
    myPermissions: Set<String>,
    amOwner: Boolean,
    onBack: () -> Unit,
    onSave: (RoleRequest, (String?) -> Unit) -> Unit,
    onDelete: ((String?) -> Unit) -> Unit,
    onToggleMember: (memberId: String, roleId: String, give: Boolean, (String?) -> Unit) -> Unit,
) {
    var name by remember(role) { mutableStateOf(role?.name.orEmpty()) }
    var color by remember(role) { mutableStateOf(role?.color) }
    var hoist by remember(role) { mutableStateOf(role?.hoist ?: false) }
    var perms by remember(role) { mutableStateOf(role?.permissions.orEmpty().toSet()) }
    var saving by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        val src = remember { MutableInteractionSource() }
        val hov by src.collectIsHoveredAsState()
        Text(
            "‹ voltar",
            style = TextStyle(color = if (hov) Obsidian.text1 else Obsidian.text3, fontSize = 12.sp),
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .hoverable(src)
                .clickable(interactionSource = src, indication = null, onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
    Spacer(Modifier.height(14.dp))

    FieldLabel("nome do cargo")
    Box(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (name.isEmpty()) {
            Text("moderador, veterano…", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
        }
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            singleLine = true,
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(14.dp))
    FieldLabel("cor do nome")
    RoleColorPicker(color) { color = it }

    Spacer(Modifier.height(6.dp))
    ToggleRow(
        "Separar na lista de membros",
        "quem tem este cargo aparece num grupo proprio, acima dos demais",
        hoist,
    ) { hoist = it }

    SettingsDivider()
    FieldLabel("permissoes")
    if (!amOwner) {
        Text(
            "voce so pode conceder permissoes que voce mesmo tem.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            modifier = Modifier.widthIn(max = 460.dp).padding(bottom = 8.dp),
        )
    }
    PERMISSIONS.forEach { (key, label, desc) ->
        // Dono concede tudo; o resto so o que possui (espelha o grantableSubset do
        // backend, que descartaria em silencio).
        val canGrant = amOwner || key in myPermissions
        PermissionRow(
            label = label,
            desc = if (canGrant) desc else "$desc — voce nao tem esta permissao",
            checked = key in perms,
            enabled = canGrant,
        ) { on ->
            perms = if (on) perms + key else perms - key
        }
    }

    // ---- Membros com este cargo (so pra cargo que ja existe) ----
    if (role != null) {
        SettingsDivider()
        FieldLabel("quem tem este cargo")
        RoleMembers(role, members, onToggleMember) { text, ok -> msg = text to ok }
    }

    Spacer(Modifier.height(20.dp))
    msg?.let { (text, ok) ->
        Text(
            text,
            style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val canSave = name.isNotBlank() && !saving
        Text(
            if (saving) "salvando…" else if (role == null) "criar cargo" else "salvar",
            style = TextStyle(color = if (canSave) Obsidian.accent else Obsidian.text3, fontSize = 13.sp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, if (canSave) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
                .clickable(enabled = canSave) {
                    saving = true
                    msg = null
                    onSave(RoleRequest(name.trim(), color, perms.toList(), hoist)) { err ->
                        saving = false
                        if (err != null) msg = err to false
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (role != null) {
            Text(
                "excluir cargo",
                style = TextStyle(color = Obsidian.danger, fontSize = 13.sp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Obsidian.danger.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .clickable { confirmDelete = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    if (confirmDelete && role != null) {
        ConfirmPopup(
            message = "excluir o cargo ${role.name}? quem o tem perde as permissoes dele.",
            confirmLabel = "excluir",
            onConfirm = {
                confirmDelete = false
                onDelete { err -> if (err != null) msg = err to false }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    Spacer(Modifier.height(24.dp))
}

// Membros com o cargo + quem falta. Lista rolavel e capada: uma constelacao
// grande nao pode empurrar os botoes de salvar pra fora da tela.
@Composable
private fun RoleMembers(
    role: RoleDto,
    members: List<ServerMemberDto>,
    onToggleMember: (memberId: String, roleId: String, give: Boolean, (String?) -> Unit) -> Unit,
    onMsg: (String, Boolean) -> Unit,
) {
    var busy by remember { mutableStateOf<String?>(null) }
    val (withRole, without) = remember(members, role.id) {
        members.partition { m -> m.roles.any { it.id == role.id } }
    }

    if (withRole.isEmpty()) {
        Text("ninguem ainda.", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
    }
    Column(
        Modifier.widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        withRole.forEach { m ->
            MemberRoleRow(m, hasRole = true, busy = busy == m.id) {
                busy = m.id
                onToggleMember(m.id, role.id, false) { err ->
                    busy = null
                    if (err != null) onMsg(err, false)
                }
            }
        }
        if (without.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "ADICIONAR",
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp),
            )
            without.forEach { m ->
                MemberRoleRow(m, hasRole = false, busy = busy == m.id) {
                    busy = m.id
                    onToggleMember(m.id, role.id, true) { err ->
                        busy = null
                        if (err != null) onMsg(err, false)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRoleRow(m: ServerMemberDto, hasRole: Boolean, busy: Boolean, onToggle: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hov) Obsidian.hover else Color.Transparent)
            .hoverable(src)
            .clickable(enabled = !busy, interactionSource = src, indication = null, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopAvatar(m.user.avatarUrl, m.user.displayName ?: m.user.username, 24)
        Spacer(Modifier.width(9.dp))
        Text(
            m.user.displayName ?: m.user.username,
            style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (busy) "…" else if (hasRole) "remover" else "adicionar",
            style = TextStyle(
                color = if (hasRole) Obsidian.danger else Obsidian.accent,
                fontSize = 11.sp,
            ),
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = TextStyle(
                    color = if (enabled) Obsidian.text1 else Obsidian.text3,
                    fontSize = 13.sp,
                ),
            )
            Text(desc, style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
        }
        Spacer(Modifier.width(12.dp))
        // Interruptor simples (o ToggleRow do Settings ja tem rotulo proprio; aqui
        // o rotulo e a coluna acima, entao so a chave).
        Box(
            Modifier
                .size(38.dp, 21.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    when {
                        !enabled -> Obsidian.raised
                        checked -> Obsidian.accent.copy(alpha = 0.75f)
                        else -> Obsidian.raised
                    },
                )
                .border(
                    1.dp,
                    if (checked && enabled) Obsidian.accent else Obsidian.borderDim,
                    RoundedCornerShape(11.dp),
                ),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Obsidian.text1 else Obsidian.text3),
            )
        }
    }
}

@Composable
private fun RoleColorPicker(selected: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(18.dp).clip(CircleShape)
                    .background(roleColor(selected) ?: Obsidian.text3)
                    .border(1.dp, Obsidian.borderDim, CircleShape),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                selected ?: "sem cor",
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, fontFamily = DmMono),
            )
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(290.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    FieldLabel("paleta")
                    ROLE_COLORS.chunked(7).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { hex ->
                                val active = selected.equals(hex, ignoreCase = true)
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(roleColor(hex) ?: Obsidian.text3)
                                        .border(
                                            if (active) 2.dp else 1.dp,
                                            if (active) Obsidian.text1 else Obsidian.borderDim,
                                            CircleShape,
                                        )
                                        .clickable { onPick(hex); open = false },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    HairRule()
                    Spacer(Modifier.height(10.dp))
                    FieldLabel("codigo hex")
                    RoleHexField(selected) { onPick(it) }
                }
            }
        }
    }
}

@Composable
private fun RoleHexField(selected: String?, onPick: (String) -> Unit) {
    var text by remember(selected) { mutableStateOf(selected?.removePrefix("#").orEmpty()) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp, fontFamily = DmMono))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text("c9a96e", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp, fontFamily = DmMono))
            }
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val clean = raw.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.lowercase().take(6)
                    text = clean
                    // So aplica com os 6 digitos: teclar no meio nao deve pintar um
                    // valor pela metade.
                    if (clean.length == 6) onPick("#$clean")
                },
                singleLine = true,
                textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmMono),
                cursorBrush = SolidColor(Obsidian.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// "#rrggbb" -> Color. Invalido/nulo = null (a UI cai no cinza).
private fun roleColor(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}
