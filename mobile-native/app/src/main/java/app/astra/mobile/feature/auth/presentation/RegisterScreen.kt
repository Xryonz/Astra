package app.astra.mobile.feature.auth.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.Reveal
import app.astra.mobile.ui.components.RomanNumeral
import app.astra.mobile.ui.theme.AstraTheme
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors

@Composable
fun RegisterScreen(
    onGoToLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    RegisterContent(
        state = state,
        onDisplayName = viewModel::onDisplayName,
        onUsername = viewModel::onUsername,
        onEmail = viewModel::onEmail,
        onPassword = viewModel::onPassword,
        onSubmit = viewModel::submit,
        onGoToLogin = onGoToLogin,
    )
}

@Composable
private fun RegisterContent(
    state: RegisterUiState,
    onDisplayName: (String) -> Unit,
    onUsername: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoToLogin: () -> Unit,
) {
    CosmicBackground {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(440.dp)
                .background(
                    Brush.radialGradient(listOf(astraColors.accentDim, Color.Transparent)),
                    CircleShape,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 30.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Reveal {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "✦", color = astraColors.accent, fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(text = "Astra", fontFamily = DmSerif, fontSize = 20.sp, color = astraColors.text1)
                }
            }

            Spacer(Modifier.height(34.dp))

            Reveal(delayMillis = 90) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RomanNumeral("II.", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    MarginaliaLabel("criar conta", Modifier.padding(bottom = 4.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            Reveal(delayMillis = 140) {
                Text(
                    text = "Criar sua conta",
                    style = MaterialTheme.typography.headlineMedium,
                    color = astraColors.text1,
                )
            }

            Spacer(Modifier.height(8.dp))

            Reveal(delayMillis = 180) {
                Text(
                    text = "Junte-se a constelacao.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = astraColors.text2,
                )
            }

            Spacer(Modifier.height(18.dp))
            Reveal(delayMillis = 220) { HairlineRule() }
            Spacer(Modifier.height(26.dp))

            Reveal(delayMillis = 260) {
                EditorialField(
                    value = state.displayName,
                    onValue = onDisplayName,
                    label = "nome",
                    placeholder = "Como te chamam",
                    enabled = !state.loading,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                )
            }

            Spacer(Modifier.height(20.dp))

            Reveal(delayMillis = 300) {
                EditorialField(
                    value = state.username,
                    onValue = onUsername,
                    label = "username",
                    placeholder = "minusculas_e_numeros",
                    enabled = !state.loading,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                )
            }

            Spacer(Modifier.height(20.dp))

            Reveal(delayMillis = 340) {
                EditorialField(
                    value = state.email,
                    onValue = onEmail,
                    label = "e-mail",
                    placeholder = "voce@exemplo.com",
                    enabled = !state.loading,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                )
            }

            Spacer(Modifier.height(20.dp))

            Reveal(delayMillis = 380) {
                EditorialField(
                    value = state.password,
                    onValue = onPassword,
                    label = "senha",
                    placeholder = "8+ com maiuscula e numero",
                    enabled = !state.loading,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onIme = onSubmit,
                    password = true,
                )
            }

            if (state.error != null) {
                Spacer(Modifier.height(16.dp))
                AuthErrorBox(state.error)
            }

            Spacer(Modifier.height(28.dp))

            Reveal(delayMillis = 420) {
                Button(
                    onClick = onSubmit,
                    enabled = !state.loading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = astraColors.accent,
                        contentColor = astraColors.textInv,
                        disabledContainerColor = astraColors.accent.copy(alpha = 0.45f),
                        disabledContentColor = astraColors.textInv.copy(alpha = 0.7f),
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = astraColors.textInv,
                        )
                    } else {
                        Text(
                            text = "CRIAR CONTA",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = 0.18.em,
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            Reveal(delayMillis = 460) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MarginaliaLabel("ja tem conta?")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Entrar",
                        fontFamily = DmSerif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 18.sp,
                        color = astraColors.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable(enabled = !state.loading, onClick = onGoToLogin)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF06060E)
@Composable
private fun RegisterPreview() {
    AstraTheme {
        RegisterContent(
            state = RegisterUiState(displayName = "Theo", username = "theo", email = "theo@astra.app"),
            onDisplayName = {}, onUsername = {}, onEmail = {}, onPassword = {},
            onSubmit = {}, onGoToLogin = {},
        )
    }
}
