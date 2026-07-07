package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.AstraCopy
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors

// Cabecalho de secao da config (ex "pessoal"): fonte maior e branca (text1) pra
// leitura facil, no lugar do marginalia pequeno/cinza.
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontFamily = DmSerif,
        fontSize = 19.sp,
        color = astraColors.text1,
        modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 10.dp),
    )
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenData: () -> Unit,
    onOpenWishing: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsState()

    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Configuracoes", marginalia = "conta e preferencias", onBack = onBack)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AstraAvatar(profile?.avatarUrl, profile?.displayName ?: "?", size = 56)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = profile?.displayName ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = astraColors.text1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MarginaliaLabel("@${profile?.username ?: "..."}")
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionHeader("pessoal")
            SettingsRow("Conta", "nome, username, senha", onOpenAccount)
            SettingsRow("Personalização", "perfil, cor do nome, tema e banner", onOpenPersonalization)

            Spacer(Modifier.height(20.dp))
            SectionHeader("app")
            SettingsRow("Acessibilidade", "movimento e vibracao", onOpenAccessibility)
            SettingsRow("Notificações", "menções, sussurros e horário silencioso", onOpenNotifications)

            Spacer(Modifier.height(20.dp))
            SectionHeader("conta e seguranca")
            SettingsRow("Sessoes", "dispositivos conectados", onOpenSessions)
            SettingsRow("Dados e privacidade", "exportar ou apagar conta", onOpenData)

            Spacer(Modifier.height(20.dp))
            SectionHeader("comunidade")
            SettingsRow("Estrela Cadente", "sugira ideias pro Astra", onOpenWishing)

            Spacer(Modifier.height(20.dp))
            val logoutShape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(logoutShape)
                    .background(astraColors.raised)
                    .border(1.dp, astraColors.danger.copy(alpha = 0.4f), logoutShape)
                    .clickable(onClick = viewModel::logout)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(AstraCopy.Action.logout, style = MaterialTheme.typography.titleMedium, color = astraColors.danger)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsRow(title: String, sub: String, onClick: (() -> Unit)?) {
    val enabled = onClick != null
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick!!) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) astraColors.text1 else astraColors.text3,
            )
            MarginaliaLabel(sub)
        }
        Text(
            text = if (enabled) "›" else "·",
            fontFamily = DmSerif,
            style = MaterialTheme.typography.titleLarge,
            color = astraColors.text3,
        )
    }
}
