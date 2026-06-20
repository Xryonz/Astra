package app.astra.mobile.feature.invite.presentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@Composable
fun JoinServerScreen(
    onBack: () -> Unit,
    onJoined: (serverId: String, name: String) -> Unit,
    viewModel: JoinServerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.joined.collect { (id, name) -> onJoined(id, name) }
    }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = "Entrar por convite",
                marginalia = "convite",
                onBack = onBack,
            )

            Column(Modifier.padding(horizontal = 18.dp)) {
                Text(
                    text = "Cole o link ou o código do convite.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = astraColors.text2,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = viewModel::setCode,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = !state.loadingPreview,
                        label = { Text("convite") },
                    )
                    TextButton(
                        onClick = viewModel::loadPreview,
                        enabled = state.code.isNotBlank() && !state.loadingPreview,
                    ) {
                        Text(if (state.loadingPreview) "..." else "Buscar", color = astraColors.accent)
                    }
                }

                state.previewError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = astraColors.danger)
                }

                if (state.loadingPreview) {
                    Spacer(Modifier.height(28.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                }

                state.preview?.let { preview ->
                    Spacer(Modifier.height(20.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, astraColors.borderMid, RoundedCornerShape(16.dp))
                            .background(astraColors.overlay, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AstraAvatar(preview.iconUrl, preview.name, size = 72)
                        Text(
                            text = preview.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = astraColors.text1,
                            textAlign = TextAlign.Center,
                        )
                        MarginaliaLabel("${preview.memberCount} membro${if (preview.memberCount == 1) "" else "s"}")

                        Spacer(Modifier.height(6.dp))
                        if (preview.isGroup) {
                            Text(
                                text = "Este é um grupo privado. Peça pra um admin te adicionar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = astraColors.text3,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            state.joinError?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = astraColors.danger, textAlign = TextAlign.Center)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(astraColors.accent)
                                    .clickable(enabled = !state.joining, onClick = viewModel::join)
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (state.joining) "Entrando..." else "Entrar em ${preview.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = astraColors.textInv,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
