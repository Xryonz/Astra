package app.astra.mobile.feature.auth.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.Reveal
import app.astra.mobile.ui.components.RomanNumeral
import app.astra.mobile.ui.theme.AstraTheme
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.EaseSpring
import app.astra.mobile.ui.theme.astraColors

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LoginContent(
        state = state,
        onEmail = viewModel::onEmail,
        onPassword = viewModel::onPassword,
        onSubmit = viewModel::submit,
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    CosmicBackground {
        // Glow prata suave atras do form (espelha o radial accent-dim do web)
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
                .padding(horizontal = 30.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            // Wordmark
            Reveal {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "✦",
                        color = astraColors.accent,
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Astra",
                        fontFamily = DmSerif,
                        fontSize = 20.sp,
                        color = astraColors.text1,
                    )
                }
            }

            Spacer(Modifier.height(44.dp))

            // Secao: numeral + marginalia
            Reveal(delayMillis = 90) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RomanNumeral("I.", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    MarginaliaLabel("entrar", Modifier.padding(bottom = 4.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            Reveal(delayMillis = 140) {
                Text(
                    text = "Bem-vindo de volta",
                    style = MaterialTheme.typography.headlineMedium,
                    color = astraColors.text1,
                )
            }

            Spacer(Modifier.height(8.dp))

            Reveal(delayMillis = 180) {
                Text(
                    text = "Entre pra continuar de onde parou.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = astraColors.text2,
                )
            }

            Spacer(Modifier.height(20.dp))
            Reveal(delayMillis = 220) { HairlineRule() }
            Spacer(Modifier.height(30.dp))

            Reveal(delayMillis = 280) {
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

            Spacer(Modifier.height(22.dp))

            Reveal(delayMillis = 330) {
                EditorialField(
                    value = state.password,
                    onValue = onPassword,
                    label = "senha",
                    placeholder = "••••••••",
                    enabled = !state.loading,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onIme = onSubmit,
                    password = true,
                )
            }

            if (state.error != null) {
                Spacer(Modifier.height(16.dp))
                ErrorBox(state.error)
            }

            Spacer(Modifier.height(30.dp))

            Reveal(delayMillis = 380) {
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
                            text = "ENTRAR",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = 0.18.em,
                        )
                    }
                }
            }
        }
    }
}

/** Input editorial: label mono em cima, texto flush, underline que acende no foco. */
@Composable
private fun EditorialField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onIme: () -> Unit = {},
    password: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val lineColor by animateColorAsState(
        targetValue = if (focused) astraColors.accent else astraColors.borderMid,
        animationSpec = tween(300, easing = EaseSpring),
        label = "underline",
    )
    Column(Modifier.fillMaxWidth()) {
        MarginaliaLabel(label)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = astraColors.text1),
            cursorBrush = SolidColor(astraColors.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { onIme() },
                onDone = { onIme() },
                onGo = { onIme() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic,
                            color = astraColors.text3,
                        )
                    }
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(lineColor))
    }
}

@Composable
private fun ErrorBox(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(astraColors.danger.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = astraColors.danger,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF06060E)
@Composable
private fun LoginPreview() {
    AstraTheme {
        LoginContent(
            state = LoginUiState(email = "theo@astra.app", error = "E-mail ou senha incorretos"),
            onEmail = {}, onPassword = {}, onSubmit = {},
        )
    }
}
