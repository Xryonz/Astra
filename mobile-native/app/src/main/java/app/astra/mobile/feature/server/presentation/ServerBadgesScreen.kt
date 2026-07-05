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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun ServerBadgesScreen(
    onBack: () -> Unit,
    viewModel: ServerBadgesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var createOpen by remember { mutableStateOf(false) }
    var grantTargetId by remember { mutableStateOf<String?>(null) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(title = "Insignias", marginalia = "crie e conceda", onBack = onBack)

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp, vertical = 18.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MarginaliaLabel("— insignias da constelacao")
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "+ nova",
                        style = MaterialTheme.typography.titleSmall,
                        color = astraColors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { createOpen = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (state.badges.isEmpty()) {
                    MarginaliaLabel("nenhuma insignia ainda — crie a primeira")
                } else {
                    state.badges.forEach { b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { grantTargetId = b.id }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${b.icon} ${b.name}", style = MaterialTheme.typography.bodyLarge, color = astraColors.text1, modifier = Modifier.weight(1f))
                            MarginaliaLabel("${b.grantedUserIds.size} estrela${if (b.grantedUserIds.size == 1) "" else "s"}")
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "✕",
                                color = astraColors.text3,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { deleteTargetId = b.id }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    MarginaliaLabel("toque numa insignia pra conceder a membros")
                }
                if (state.badgeError != null) {
                    Spacer(Modifier.height(10.dp))
                    AuthErrorBox(state.badgeError!!)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    CreateBadgeDialog(
        open = createOpen,
        onCreate = { name, icon, color, desc -> viewModel.createBadge(name, icon, color, desc) },
        onDismiss = { createOpen = false },
    )

    val grantBadge = state.badges.firstOrNull { it.id == grantTargetId }
    AstraDialog(
        open = grantBadge != null,
        onDismiss = { grantTargetId = null },
        title = grantBadge?.let { "${it.icon} ${it.name}" } ?: "",
        confirmText = "Fechar",
        onConfirm = { grantTargetId = null },
        dismissText = null,
    ) {
        if (state.members.isEmpty()) {
            MarginaliaLabel("nenhum membro carregado")
        } else {
            Column(
                Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                state.members.forEach { m ->
                    val granted = grantBadge != null && m.userId in grantBadge.grantedUserIds
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(m.name, style = MaterialTheme.typography.bodyMedium, color = astraColors.text1, modifier = Modifier.weight(1f))
                        AstraSwitch(
                            checked = granted,
                            onCheckedChange = { on -> grantBadge?.let { viewModel.toggleGrant(it.id, m.userId, on) } },
                        )
                    }
                }
            }
        }
    }

    AstraDialog(
        open = deleteTargetId != null,
        onDismiss = { deleteTargetId = null },
        title = "Apagar insignia?",
        confirmText = "Apagar",
        onConfirm = { deleteTargetId?.let(viewModel::deleteBadge); deleteTargetId = null },
    ) {
        MarginaliaLabel("ela some do perfil de quem recebeu")
    }
}

// Dialogo de criacao: emoji + nome + descricao + cor (swatches predefinidos).
@Composable
private fun CreateBadgeDialog(
    open: Boolean,
    onCreate: (name: String, icon: String, color: String?, description: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var icon by remember(open) { mutableStateOf("") }
    var name by remember(open) { mutableStateOf("") }
    var desc by remember(open) { mutableStateOf("") }
    var color by remember(open) { mutableStateOf<String?>(null) }
    AstraDialog(
        open = open,
        onDismiss = onDismiss,
        title = "Nova insignia",
        confirmText = "Criar",
        confirmEnabled = icon.isNotBlank() && name.isNotBlank(),
        onConfirm = { onCreate(name, icon, color, desc); onDismiss() },
    ) {
        EditorialField(
            value = icon, onValue = { icon = it.take(16) },
            label = "emoji", placeholder = "🔥",
            enabled = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(12.dp))
        EditorialField(
            value = name, onValue = { name = it.take(40) },
            label = "nome", placeholder = "Lenda",
            enabled = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(12.dp))
        EditorialField(
            value = desc, onValue = { desc = it.take(120) },
            label = "descricao (opcional)", placeholder = "por que essa insignia existe",
            enabled = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
        )
        Spacer(Modifier.height(14.dp))
        MarginaliaLabel("cor da borda")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BADGE_COLORS.forEach { hex ->
                val c = hex?.let { Color("FF${it.removePrefix("#")}".toLong(16)) } ?: astraColors.accent
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (hex == null) astraColors.raised else c)
                        .border(
                            width = if (color == hex) 2.dp else 1.dp,
                            color = if (color == hex) astraColors.text1 else astraColors.borderMid,
                            shape = RoundedCornerShape(50),
                        )
                        .clickable { color = hex },
                    contentAlignment = Alignment.Center,
                ) {
                    if (hex == null) Text("—", color = astraColors.text3, fontSize = 12.sp)
                }
            }
        }
    }
}

// null = sem cor (usa o ambar padrao no chip).
private val BADGE_COLORS = listOf<String?>(null, "#c9a96e", "#8ab4f8", "#7bd88f", "#e0b25c", "#e08a8a", "#b78ae0")
