package app.astra.mobile.feature.verifyemail.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors

// Tela-bloqueio pos-registro: confirma o codigo de 6 digitos mandado pro email.
// Back nao escapa; a saida honesta e "sair da conta" (email fake = conta presa).
@Composable
fun VerifyEmailScreen(
    onDone: () -> Unit,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    BackHandler {}
    LaunchedEffect(state.done) { if (state.done) onDone() }

    CosmicBackground {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 26.dp),
        ) {
            Text(
                text = "Confirme seu email",
                fontFamily = DmSerif,
                fontSize = 28.sp,
                color = astraColors.text1,
            )
            Spacer(Modifier.height(6.dp))
            MarginaliaLabel(
                if (state.email.isBlank()) "mandamos um codigo de 6 digitos pro seu email"
                else "mandamos um codigo de 6 digitos pra ${state.email}",
            )
            Spacer(Modifier.height(20.dp))
            EditorialField(
                value = state.code, onValue = viewModel::onCode,
                label = "codigo", placeholder = "000000",
                enabled = !state.checking, keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
                onIme = viewModel::verify,
            )
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                AuthErrorBox(state.error!!)
            }
            Spacer(Modifier.height(18.dp))
            val canConfirm = state.code.length == 6 && !state.checking
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (canConfirm) astraColors.accent else astraColors.raised)
                    .clickable(enabled = canConfirm, onClick = viewModel::verify),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.checking) "Verificando..." else "Confirmar",
                    color = if (canConfirm) astraColors.textInv else astraColors.text3,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "reenviar codigo",
                    style = MaterialTheme.typography.labelLarge,
                    color = astraColors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = viewModel::resend)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(10.dp))
                if (state.resent) MarginaliaLabel("enviado ✓", color = astraColors.success)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "sair da conta",
                    style = MaterialTheme.typography.labelLarge,
                    color = astraColors.text3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = viewModel::logout)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}
