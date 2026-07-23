package app.astra.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.awt.Toolkit
import java.awt.event.KeyEvent as AwtKeyEvent

// Login split editorial: ceu vivo a esquerda (o MESMO planeta do gate de boot, as
// estrelas do app e a constelacao que se forma ao digitar), formulario a direita.
//
// A aurora NAO e pintada aqui: ela vive na janela (Main.kt), atras do login e do
// shell. Foi o que tornou a entrada continua — antes a aurora do login ocupava 45%
// da largura e a do shell 100%, e como o uv do shader e normalizado pelo tamanho,
// as duas imagens eram completamente diferentes: a troca saltava.
@Composable
fun LoginScreen(
    repo: AuthRepository,
    onLoggedIn: (Session) -> Unit,
) {
    val store = remember { GlobalContext.get().get<SessionStore>() }
    // Lembra o ultimo email que ENTROU (nao o que foi digitado): reabrir o app cai
    // com o campo pronto e o foco ja na senha.
    var email by remember { mutableStateOf(store.uiPref(LAST_EMAIL_KEY).orEmpty()) }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var capsOn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val emailFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }
    // Abre digitando: com email lembrado, o foco pula direto pra senha.
    LaunchedEffect(Unit) {
        runCatching { if (email.isBlank()) emailFocus.requestFocus() else passFocus.requestFocus() }
    }

    fun submit() {
        if (loading || email.isBlank() || password.isBlank()) return
        loading = true; error = null
        scope.launch {
            repo.login(email, password)
                .onSuccess {
                    store.setUiPref(LAST_EMAIL_KEY, email.trim())
                    loading = false
                    onLoggedIn(it)
                }
                .onFailure { e -> loading = false; error = e.message }
        }
    }

    Row(Modifier.fillMaxSize()) {
        // ---- Painel esquerdo: ceu + marca + constelacao ----
        Box(
            modifier = Modifier.weight(0.45f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            // Estrelas por cima da aurora da janela — o mesmo ceu do resto do app.
            StarField(Modifier.fillMaxSize())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp),
            ) {
                // O MESMO planeta do gate de atualizacao: o objeto que abre o app e
                // o que recebe no login.
                RotatingStarsLogo(LocalReduceMotion.current, diameter = 132.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Astra",
                    style = TextStyle(
                        color = Obsidian.text1,
                        fontSize = 56.sp,
                        fontFamily = DmSerif,
                        fontWeight = FontWeight.Light,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "sua constelacao te espera",
                    style = TextStyle(color = Obsidian.text2, fontSize = 15.sp),
                )
                Spacer(Modifier.height(6.dp))
                // Forma conforme o formulario e preenchido; retrai ao apagar.
                LoginConstellation(
                    progress = loginProgress(email, password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- Painel direito: formulario ----
        Box(
            // Translucido (nao mais opaco): a aurora da janela passa por baixo, no
            // mesmo idioma dos paineis do shell.
            modifier = Modifier.weight(0.55f).fillMaxHeight().background(Obsidian.base.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.width(360.dp)) {
                Text(
                    text = "entrar",
                    style = TextStyle(color = Obsidian.text3, fontSize = 13.sp, fontFamily = DmSerif),
                )
                Spacer(Modifier.height(18.dp))
                EditorialField(
                    value = email, onValue = { email = it; error = null },
                    label = "email", enabled = !loading,
                    focusRequester = emailFocus,
                    onSubmit = { runCatching { passFocus.requestFocus() } },
                )
                Spacer(Modifier.height(14.dp))
                EditorialField(
                    value = password, onValue = { password = it; error = null },
                    label = "senha", enabled = !loading,
                    password = true, onSubmit = ::submit,
                    focusRequester = passFocus,
                    reveal = showPassword,
                    onToggleReveal = { showPassword = !showPassword },
                    // Caps Lock e a causa numero 1 de "a senha esta certa e nao
                    // entra". O estado da tecla vem do proprio SO (AWT Toolkit).
                    onKey = { capsOn = capsLockOn() },
                )
                if (capsOn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Caps Lock esta ligado",
                        style = TextStyle(color = Obsidian.warning, fontSize = 12.sp),
                    )
                }
                Spacer(Modifier.height(22.dp))
                SubmitButton(
                    text = if (loading) "entrando…" else "ENTRAR",
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    loading = loading,
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

private const val LAST_EMAIL_KEY = "lastEmail"

// Estado real da tecla, perguntado ao SO — nao da pra deduzir do caractere digitado
// (uma letra maiuscula pode ser Shift). Nem todo ambiente responde; ai assume-se
// desligado em vez de avisar errado.
private fun capsLockOn(): Boolean = runCatching {
    Toolkit.getDefaultToolkit().getLockingKeyState(AwtKeyEvent.VK_CAPS_LOCK)
}.getOrDefault(false)

@Composable
private fun EditorialField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    enabled: Boolean,
    password: Boolean = false,
    onSubmit: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    reveal: Boolean = false,
    onToggleReveal: (() -> Unit)? = null,
    onKey: (() -> Unit)? = null,
) {
    Column {
        Text(
            text = label,
            style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
        )
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(10.dp)
        var focused by remember { mutableStateOf(false) }
        BasicTextField(
            value = value,
            onValueChange = { onValue(it.take(200)) },
            enabled = enabled,
            // Enter no teclado fisico submete (desktop-first).
            modifier = Modifier
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    onKey?.invoke()
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && onSubmit != null) {
                        onSubmit(); true
                    } else false
                },
            singleLine = true,
            visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Email,
            ),
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 15.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Obsidian.raised)
                        // Foco acende a borda no accent: diz onde a digitacao esta
                        // caindo sem precisar de rotulo extra.
                        .border(1.dp, if (focused) Obsidian.accent.copy(alpha = 0.7f) else Obsidian.borderMid, shape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { inner() }
                    if (onToggleReveal != null) {
                        Spacer(Modifier.width(8.dp))
                        LIcon(
                            if (reveal) Lucide.EyeOff else Lucide.Eye,
                            tint = Obsidian.text3,
                            size = 16.dp,
                            modifier = Modifier.clickable(onClick = onToggleReveal),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SubmitButton(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    val reduce = LocalReduceMotion.current
    // Varredura enquanto envia: prova de que algo esta acontecendo. Antes o botao
    // so trocava o texto pra "Entrando…" e parecia travado numa rede lenta.
    val sweep = if (!loading || reduce) 0f else {
        rememberInfiniteTransition(label = "submit").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
            label = "submitSweep",
        ).value
    }
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
            .then(
                if (!loading) Modifier else Modifier.drawBehind {
                    // Faixa clara correndo por cima do fundo do botao.
                    val bandW = size.width * 0.32f
                    val x = -bandW + (size.width + bandW) * sweep
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Obsidian.text1.copy(alpha = 0.5f),
                                Color.Transparent,
                            ),
                            startX = x,
                            endX = x + bandW,
                        ),
                    )
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
