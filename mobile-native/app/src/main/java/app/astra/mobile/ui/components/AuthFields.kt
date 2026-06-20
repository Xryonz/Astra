package app.astra.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.EaseSpring
import app.astra.mobile.ui.theme.astraColors

/**
 * Input editorial compartilhado (Login + Register): label mono em cima, texto
 * flush, underline que acende no foco. Sem chrome — type-first.
 */
@Composable
fun EditorialField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onIme: () -> Unit = {},
    password: Boolean = false,
    singleLine: Boolean = true,
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
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 5,
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

/** Caixa de erro suave (fundo danger 10%), compartilhada nas telas de auth. */
@Composable
fun AuthErrorBox(message: String) {
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
