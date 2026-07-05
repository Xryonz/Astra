package app.astra.mobile.feature.server.presentation

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.AstraSwitch
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors
import zed.rainxch.rikkaui.components.ui.toast.LocalToastHostState
import zed.rainxch.rikkaui.components.ui.toast.ToastVariant

// Converte "#RRGGBB" -> Color; null/invalido cai no accent.
internal fun parseRoleColor(hex: String?, fallback: Color): Color =
    hex?.removePrefix("#")?.takeIf { it.length == 6 }
        ?.let { runCatching { Color("FF$it".toLong(16)) }.getOrNull() }
        ?: fallback

@Composable
fun ServerRolesScreen(
    onBack: () -> Unit,
    viewModel: ServerRolesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var editing by remember { mutableStateOf<RoleUi?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RoleUi?>(null) }

    val toastState = LocalToastHostState.current
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            toastState.show(it, variant = ToastVariant.Destructive)
            viewModel.clearActionError()
        }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(title = "Cargos", marginalia = "papeis e permissoes", onBack = onBack)

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                return@Column
            }
            if (state.error != null) {
                Box(Modifier.padding(20.dp)) { AuthErrorBox(state.error!!) }
                return@Column
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MarginaliaLabel("— cargos da constelacao")
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "+ novo",
                        style = MaterialTheme.typography.titleSmall,
                        color = astraColors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { creating = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (state.roles.isEmpty()) {
                    MarginaliaLabel("nenhum cargo ainda — crie o primeiro")
                } else {
                    state.roles.forEach { r ->
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(shape)
                                .border(1.dp, astraColors.border, shape)
                                .clickable { editing = r }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(parseRoleColor(r.color, astraColors.text3)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = r.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = parseRoleColor(r.color, astraColors.text1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            MarginaliaLabel(
                                if (r.permissions.isEmpty()) "—"
                                else "${r.permissions.size} perm${if (r.permissions.size == 1) "" else "s"}",
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "✕",
                                color = astraColors.text3,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { deleteTarget = r }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (creating) {
        RoleEditorDialog(
            role = null,
            onDismiss = { creating = false },
            onSave = { name, color, hoist, perms ->
                viewModel.createRole(name, color, hoist, perms); creating = false
            },
        )
    }
    editing?.let { r ->
        RoleEditorDialog(
            role = r,
            onDismiss = { editing = null },
            onSave = { name, color, hoist, perms ->
                viewModel.updateRole(r.id, name, color, hoist, perms); editing = null
            },
        )
    }

    AstraDialog(
        open = deleteTarget != null,
        onDismiss = { deleteTarget = null },
        title = "Apagar cargo?",
        confirmText = "Apagar",
        onConfirm = { deleteTarget?.let { viewModel.deleteRole(it.id) }; deleteTarget = null },
    ) {
        MarginaliaLabel("quem tinha ${deleteTarget?.name ?: "esse cargo"} perde as permissoes dele")
    }
}

@Composable
private fun RoleEditorDialog(
    role: RoleUi?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String?, hoist: Boolean, perms: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(role?.name ?: "") }
    var hasColor by remember { mutableStateOf(role?.color != null) }
    var color by remember { mutableStateOf(role?.color ?: "#c9a96e") }
    var hoist by remember { mutableStateOf(role?.hoist ?: false) }
    var perms by remember { mutableStateOf(role?.permissions?.toSet() ?: emptySet()) }

    AstraDialog(
        open = true,
        onDismiss = onDismiss,
        title = if (role == null) "Novo cargo" else "Editar ${role.name}",
        confirmText = if (role == null) "Criar" else "Salvar",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onSave(name, if (hasColor) color else null, hoist, perms.toList()) },
    ) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            EditorialField(
                value = name, onValue = { name = it.take(50) },
                label = "nome do cargo", placeholder = "Moderador",
                enabled = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next,
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarginaliaLabel("cor do cargo")
                Spacer(Modifier.weight(1f))
                AstraSwitch(checked = hasColor, onCheckedChange = { hasColor = it })
            }
            if (hasColor) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ROLE_SWATCHES.forEach { hex ->
                        val c = parseRoleColor(hex, astraColors.accent)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (color.equals(hex, true)) 2.dp else 1.dp,
                                    color = if (color.equals(hex, true)) astraColors.text1 else astraColors.borderMid,
                                    shape = CircleShape,
                                )
                                .clickable { color = hex },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                EditorialField(
                    value = color, onValue = { color = it.take(7) },
                    label = "hex", placeholder = "#RRGGBB",
                    enabled = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Separar na lista", style = MaterialTheme.typography.titleSmall, color = astraColors.text1)
                    MarginaliaLabel("mostra este cargo como grupo proprio (hoist)")
                }
                Spacer(Modifier.width(12.dp))
                AstraSwitch(checked = hoist, onCheckedChange = { hoist = it })
            }

            Spacer(Modifier.height(18.dp))
            MarginaliaLabel("— permissoes")
            Spacer(Modifier.height(8.dp))
            PERM_OPTIONS.forEach { (key, label, desc) ->
                val active = key in perms
                val shape = RoundedCornerShape(10.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(shape)
                        .background(if (active) astraColors.accentDim else Color.Transparent)
                        .border(1.dp, if (active) astraColors.accent else astraColors.border, shape)
                        .clickable { perms = if (active) perms - key else perms + key }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) astraColors.accent else astraColors.text1,
                            fontWeight = FontWeight.Medium,
                        )
                        MarginaliaLabel(desc)
                    }
                    if (active) Text("✓", color = astraColors.accent, fontSize = 15.sp)
                }
            }
        }
    }
}

private val ROLE_SWATCHES = listOf("#c9a96e", "#8ab4f8", "#7bd88f", "#e0b25c", "#e08a8a", "#b78ae0")
