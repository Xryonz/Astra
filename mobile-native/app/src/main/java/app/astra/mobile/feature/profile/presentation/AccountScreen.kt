package app.astra.mobile.feature.profile.presentation

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.DmMono
import app.astra.mobile.ui.theme.astraColors

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    CosmicBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            EditorialTopBar(title = "Conta", marginalia = "sua identidade", onBack = onBack)

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
                EditorialField(
                    value = state.displayName,
                    onValue = viewModel::onDisplayName,
                    label = "nome de exibicao",
                    placeholder = "Como te chamam",
                    enabled = !state.saving,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(22.dp))
                EditorialField(
                    value = state.username,
                    onValue = viewModel::onUsername,
                    label = "username",
                    placeholder = "minusculas_e_numeros",
                    enabled = !state.saving,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                )

                if (state.error != null) {
                    Spacer(Modifier.height(14.dp))
                    AuthErrorBox(state.error!!)
                }

                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.dirty && !state.saving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = astraColors.accent,
                            contentColor = astraColors.textInv,
                            disabledContainerColor = astraColors.accent.copy(alpha = 0.4f),
                            disabledContentColor = astraColors.textInv.copy(alpha = 0.7f),
                        ),
                        modifier = Modifier.height(46.dp),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = astraColors.textInv)
                        } else {
                            Text("SALVAR", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.16.em)
                        }
                    }
                    if (state.saved) {
                        Spacer(Modifier.width(12.dp))
                        MarginaliaLabel("salvo ✓", color = astraColors.success)
                    }
                }

                Spacer(Modifier.height(26.dp))
                HairlineRule()
                Spacer(Modifier.height(22.dp))

                ReadOnlyField("e-mail", state.email)

                Spacer(Modifier.height(22.dp))

                MarginaliaLabel("senha")
                Spacer(Modifier.height(8.dp))
                when {
                    !state.hasPassword -> Text(
                        "Sua conta usa login Google e nao tem senha.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = astraColors.text3,
                    )
                    !state.pwOpen -> {
                        PillButton("Trocar senha", onClick = viewModel::togglePw)
                        if (state.pwDone) {
                            Spacer(Modifier.height(8.dp))
                            MarginaliaLabel("senha alterada ✓", color = astraColors.success)
                        }
                    }
                    else -> {
                        EditorialField(
                            value = state.curPw, onValue = viewModel::onCurPw,
                            label = "senha atual", placeholder = "••••••••",
                            enabled = !state.pwSaving, keyboardType = KeyboardType.Password, imeAction = ImeAction.Next,
                            password = true,
                        )
                        Spacer(Modifier.height(16.dp))
                        EditorialField(
                            value = state.newPw, onValue = viewModel::onNewPw,
                            label = "nova senha", placeholder = "8+ caracteres",
                            enabled = !state.pwSaving, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                            onIme = viewModel::changePassword, password = true,
                        )
                        if (state.pwError != null) {
                            Spacer(Modifier.height(12.dp))
                            AuthErrorBox(state.pwError!!)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(if (state.pwSaving) "Salvando..." else "Confirmar", onClick = viewModel::changePassword, accent = true)
                            PillButton("Cancelar", onClick = viewModel::togglePw)
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))
                ReadOnlyField("Coordenada Astra", state.userId, mono = true)

                Spacer(Modifier.height(26.dp))
                HairlineRule()
                Spacer(Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .border(1.dp, astraColors.danger.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable(onClick = viewModel::logout),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Sair da conta", color = astraColors.danger, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String, mono: Boolean = false) {
    Column {
        MarginaliaLabel(label)
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) DmMono else null,
            color = astraColors.text2,
        )
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit, accent: Boolean = false) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (accent) Modifier.background(astraColors.accent)
                else Modifier.border(1.dp, astraColors.borderMid, RoundedCornerShape(12.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (accent) astraColors.textInv else astraColors.text1,
        )
    }
}
