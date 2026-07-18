package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import app.astra.desktop.ui.theme.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.auth.AuthRepository
import app.astra.desktop.auth.Session
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import kotlinx.coroutines.launch

// Login split editorial: aurora viva a esquerda, formulario obsidiana a direita.
@Composable
fun LoginScreen(
    repo: AuthRepository,
    onLoggedIn: (Session) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (loading || email.isBlank() || password.isBlank()) return
        loading = true; error = null
        scope.launch {
            repo.login(email, password)
                .onSuccess { loading = false; onLoggedIn(it) }
                .onFailure { e -> loading = false; error = e.message }
        }
    }

    Row(Modifier.fillMaxSize()) {
        // ---- Painel esquerdo: aurora + marca ----
        Box(
            modifier = Modifier.weight(0.45f).fillMaxHeight().auroraBackground(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Astra",
                    style = TextStyle(
                        color = Obsidian.text1,
                        fontSize = 64.sp,
                        fontFamily = DmSerif,
                        fontWeight = FontWeight.Light,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "sua constelacao te espera",
                    style = TextStyle(color = Obsidian.text2, fontSize = 15.sp),
                )
            }
        }

        // ---- Painel direito: formulario ----
        Box(
            modifier = Modifier.weight(0.55f).fillMaxHeight().background(Obsidian.base),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.width(360.dp)) {
                Text(
                    text = "entrar",
                    style = TextStyle(color = Obsidian.text3, fontSize = 13.sp, fontFamily = DmSerif),
                )
                Spacer(Modifier.height(18.dp))
                EditorialField(
                    value = email, onValue = { email = it },
                    label = "email", enabled = !loading,
                )
                Spacer(Modifier.height(14.dp))
                EditorialField(
                    value = password, onValue = { password = it },
                    label = "senha", enabled = !loading,
                    password = true, onSubmit = ::submit,
                )
                Spacer(Modifier.height(22.dp))
                SubmitButton(
                    text = if (loading) "Entrando…" else "ENTRAR",
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    onClick = ::submit,
                )
                if (error != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = error!!,
                        style = TextStyle(color = Obsidian.danger, fontSize = 13.sp),
                    )
                }
                Spacer(Modifier.height(26.dp))
                Text(
                    text = "ainda sem conta? crie pelo app ou pelo site",
                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                )
            }
        }
    }
}

@Composable
private fun EditorialField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    enabled: Boolean,
    password: Boolean = false,
    onSubmit: (() -> Unit)? = null,
) {
    Column {
        Text(
            text = label,
            style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
        )
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(10.dp)
        BasicTextField(
            value = value,
            onValueChange = { onValue(it.take(200)) },
            enabled = enabled,
            // Enter no teclado fisico submete (desktop-first).
            modifier = Modifier.onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && onSubmit != null) {
                    onSubmit(); true
                } else false
            },
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Email,
            ),
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 15.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Obsidian.raised)
                        .border(1.dp, Obsidian.borderMid, shape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) { inner() }
            },
        )
    }
}

@Composable
private fun SubmitButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    !enabled -> Obsidian.raised
                    hovered -> Obsidian.text1
                    else -> Obsidian.accent
                },
            )
            .border(1.dp, if (enabled) Obsidian.accent else Obsidian.borderMid, shape)
            .hoverable(interaction)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = if (enabled) Obsidian.void else Obsidian.text3,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
