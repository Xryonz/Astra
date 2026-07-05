package app.astra.mobile.feature.server.presentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import zed.rainxch.rikkaui.components.ui.toast.LocalToastHostState
import zed.rainxch.rikkaui.components.ui.toast.ToastVariant
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@Composable
fun ServerMembersScreen(
    onBack: () -> Unit,
    onOpenProfile: (userId: String, name: String) -> Unit,
    viewModel: ServerMembersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var menuFor by remember { mutableStateOf<String?>(null) }
    var kickTarget by remember { mutableStateOf<MemberUi?>(null) }
    var banTarget by remember { mutableStateOf<MemberUi?>(null) }

    val toastState = LocalToastHostState.current
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            toastState.show(it, variant = ToastVariant.Destructive)
            viewModel.clearActionError()
        }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = "Membros",
                marginalia = "${state.members.size} estrela${if (state.members.size == 1) "" else "s"}",
                onBack = onBack,
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                return@Column
            }
            if (state.error != null) {
                Box(Modifier.padding(20.dp)) { AuthErrorBox(state.error!!) }
                return@Column
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.members, key = { it.memberId }) { m ->
                    val isSelf = m.userId == state.myUserId
                    // Espelha as regras do backend: dono intocavel; admin so cai pela mao do dono.
                    val canAdmin = state.isOwner && m.role != "OWNER" && !isSelf
                    val protected = m.role == "OWNER" || isSelf || (m.role == "ADMIN" && !state.isOwner)
                    val canKick = state.canKick && !protected
                    val canBan = state.canBan && !protected

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { menuFor = m.memberId }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AstraAvatar(m.avatarUrl, m.name, size = 42)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = m.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = astraColors.text1,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MarginaliaLabel("@${m.username}")
                            }
                            when (m.role) {
                                "OWNER" -> MarginaliaLabel("dono", color = astraColors.accent)
                                "ADMIN" -> MarginaliaLabel("admin")
                            }
                        }

                        DropdownMenu(
                            expanded = menuFor == m.memberId,
                            onDismissRequest = { menuFor = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ver perfil") },
                                onClick = { menuFor = null; onOpenProfile(m.userId, m.name) },
                            )
                            if (canAdmin) {
                                if (m.role == "ADMIN") {
                                    DropdownMenuItem(
                                        text = { Text("Remover admin") },
                                        onClick = { menuFor = null; viewModel.setAdmin(m.memberId, false) },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Tornar admin") },
                                        onClick = { menuFor = null; viewModel.setAdmin(m.memberId, true) },
                                    )
                                }
                            }
                            if (canKick) {
                                DropdownMenuItem(
                                    text = { Text("Expulsar", color = astraColors.danger) },
                                    onClick = { menuFor = null; kickTarget = m },
                                )
                            }
                            if (canBan) {
                                DropdownMenuItem(
                                    text = { Text("Banir", color = astraColors.danger) },
                                    onClick = { menuFor = null; banTarget = m },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    AstraDialog(
        open = kickTarget != null,
        onDismiss = { kickTarget = null },
        title = "Expulsar ${kickTarget?.name ?: ""}?",
        confirmText = "Expulsar",
        onConfirm = { kickTarget?.let { viewModel.kick(it.memberId) }; kickTarget = null },
    ) {
        MarginaliaLabel("pode voltar com um convite")
    }

    BanDialog(
        target = banTarget,
        onConfirm = { userId, reason -> viewModel.ban(userId, reason); banTarget = null },
        onDismiss = { banTarget = null },
    )
}

@Composable
private fun BanDialog(
    target: MemberUi?,
    onConfirm: (userId: String, reason: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember(target) { mutableStateOf("") }
    AstraDialog(
        open = target != null,
        onDismiss = onDismiss,
        title = "Banir ${target?.name ?: ""}?",
        confirmText = "Banir",
        onConfirm = { target?.let { onConfirm(it.userId, reason) } },
    ) {
        MarginaliaLabel("nao entra mais nem com convite; da pra revogar depois")
        Spacer(Modifier.height(12.dp))
        EditorialField(
            value = reason, onValue = { reason = it.take(500) },
            label = "motivo (opcional)", placeholder = "por que essa estrela foi banida",
            enabled = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
        )
    }
}
