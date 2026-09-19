package app.astra.mobile.feature.server.presentation

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun ServerChannelsScreen(
    onBack: () -> Unit,
    viewModel: ServerChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var deleteTarget by remember { mutableStateOf<ChannelMgmtUi?>(null) }

    val toast = LocalToastHostState.current
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            toast.show(it, variant = ToastVariant.Destructive)
            viewModel.clearActionError()
        }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(title = "Orbitas", marginalia = "renomear, privar e apagar", onBack = onBack)

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                state.error != null -> Box(Modifier.padding(20.dp)) { AuthErrorBox(state.error!!) }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(state.channels, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.open(c) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (c.isVoice) "🔊" else "#",
                                color = astraColors.text3,
                                fontSize = 16.sp,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                text = c.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = astraColors.text1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (c.isPrivate) MarginaliaLabel("privada", color = astraColors.accent)
                            Spacer(Modifier.width(8.dp))
                            Text("›", color = astraColors.text3, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }

    val editing = state.editing
    if (editing != null) {
        ChannelManageDialog(
            editing = editing,
            roles = state.roles,
            onTogglePrivate = viewModel::togglePrivate,
            onToggleRole = viewModel::toggleRole,
            onRename = { viewModel.rename(editing.channelId, it) },
            onSaveVisibility = viewModel::saveVisibility,
            onAskDelete = {
                deleteTarget = state.channels.firstOrNull { it.id == editing.channelId }
            },
            onDismiss = viewModel::close,
        )
    }

    AstraDialog(
        open = deleteTarget != null,
        onDismiss = { deleteTarget = null },
        title = "Apagar #${deleteTarget?.name ?: ""}?",
        confirmText = "Apagar",
        onConfirm = { deleteTarget?.let { viewModel.delete(it.id) }; deleteTarget = null },
    ) {
        MarginaliaLabel("todas as mensagens dessa orbita se perdem — sem volta")
    }
}

@Composable
private fun ChannelManageDialog(
    editing: ChannelEditState,
    roles: List<RoleMini>,
    onTogglePrivate: (Boolean) -> Unit,
    onToggleRole: (String) -> Unit,
    onRename: (String) -> Unit,
    onSaveVisibility: () -> Unit,
    onAskDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(editing.channelId) { mutableStateOf(editing.name) }
    AstraDialog(
        open = true,
        onDismiss = onDismiss,
        title = "Gerenciar orbita",
        confirmText = "Salvar visibilidade",
        confirmEnabled = !editing.loadingVisibility,
        onConfirm = onSaveVisibility,
    ) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            EditorialField(
                value = name, onValue = { name = it.take(50) },
                label = "nome da orbita", placeholder = "geral",
                enabled = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val shape = RoundedCornerShape(10.dp)
                Text(
                    text = "Salvar nome",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (name.trim() != editing.name && name.isNotBlank()) astraColors.accent else astraColors.text3,
                    modifier = Modifier
                        .clip(shape).border(1.dp, astraColors.border, shape)
                        .clickable(enabled = name.trim() != editing.name && name.isNotBlank()) { onRename(name) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    text = "Apagar orbita",
                    style = MaterialTheme.typography.labelLarge,
                    color = astraColors.danger,
                    modifier = Modifier
                        .clip(shape).border(1.dp, astraColors.border, shape)
                        .clickable(onClick = onAskDelete)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Orbita privada", style = MaterialTheme.typography.titleSmall, color = astraColors.text1)
                    MarginaliaLabel("so os cargos marcados veem (o dono sempre ve)")
                }
                Spacer(Modifier.width(12.dp))
                AstraSwitch(
                    checked = editing.isPrivate,
                    onCheckedChange = onTogglePrivate,
                    enabled = !editing.loadingVisibility,
                )
            }

            if (editing.isPrivate) {
                Spacer(Modifier.height(14.dp))
                MarginaliaLabel("cargos com acesso")
                Spacer(Modifier.height(8.dp))
                if (editing.loadingVisibility) {
                    MarginaliaLabel("carregando...")
                } else if (roles.isEmpty()) {
                    MarginaliaLabel("nenhum cargo criado — crie em Cargos")
                } else {
                    roles.forEach { r ->
                        val active = r.id in editing.roleIds
                        val shape = RoundedCornerShape(10.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(shape)
                                .background(if (active) astraColors.accentDim else Color.Transparent)
                                .border(1.dp, if (active) astraColors.accent else astraColors.border, shape)
                                .clickable { onToggleRole(r.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(parseRoleColor(r.color, astraColors.text3)),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = r.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (active) astraColors.accent else astraColors.text1,
                                modifier = Modifier.weight(1f),
                            )
                            if (active) Text("✓", color = astraColors.accent, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
