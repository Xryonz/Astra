package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import app.astra.desktop.ui.theme.Cinzel
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.awt.Toolkit
import java.awt.event.KeyEvent as AwtKeyEvent
import app.astra.desktop.ui.theme.Tipo

private enum class AuthMode { LOGIN, SIGNUP }

private data class AuthRule(val label: String, val ok: Boolean)

private fun isEmailValid(email: String): Boolean {
    val e = email.trim()
    if (e.length < 3 || e.contains(' ')) return false
    val at = e.indexOf('@')
    if (at <= 0 || at != e.lastIndexOf('@')) return false
    val domain = e.substring(at + 1)
    return domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')
}

private fun isUsernameValid(u: String): Boolean =
    u.length in 3..32 && u.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }

private fun loginRules(email: String, password: String) = listOf(
    AuthRule("email valido", isEmailValid(email)),
    AuthRule("senha preenchida", password.isNotEmpty()),
)

private fun signupRules(email: String, username: String, displayName: String, password: String) = listOf(
    AuthRule("email valido", isEmailValid(email)),
    AuthRule("usuário: 3+ (a-z, 0-9, _)", isUsernameValid(username)),
    AuthRule("nome de exibicao preenchido", displayName.isNotBlank()),
    AuthRule("senha: 8+ caracteres", password.length >= 8),
    AuthRule("senha com 1 maiuscula e 1 numero", password.any { it.isUpperCase() } && password.any { it.isDigit() }),
)

@Composable
fun LoginScreen(
    repo: AuthRepository,
    onLoggedIn: (Session, isNew: Boolean) -> Unit,
) {
    val store = remember { GlobalContext.get().get<SessionStore>() }
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var email by remember { mutableStateOf(store.uiPref(LAST_EMAIL_KEY).orEmpty()) }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var capsOn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val emailFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { if (email.isBlank()) emailFocus.requestFocus() else passFocus.requestFocus() }
    }

    val rules = when (mode) {
        AuthMode.LOGIN -> loginRules(email, password)
        AuthMode.SIGNUP -> signupRules(email, username, displayName, password)
    }
    val allOk = rules.all { it.ok }
    val progress = rules.count { it.ok }.toFloat() / rules.size
    val authDur = if (LocalReduceMotion.current) 0 else 240

    fun submit() {
        if (loading || !allOk) return
        loading = true; error = null
        scope.launch {
            val result = when (mode) {
                AuthMode.LOGIN -> repo.login(email, password)
                AuthMode.SIGNUP -> repo.register(email, username, displayName, password)
            }
            result
                .onSuccess {
                    store.setUiPref(LAST_EMAIL_KEY, email.trim())
                    loading = false
                    onLoggedIn(it, mode == AuthMode.SIGNUP)
                }
                .onFailure { e -> loading = false; error = e.message }
        }
    }

    fun googleSubmit() {
        if (loading || googleLoading) return
        googleLoading = true; error = null
        scope.launch {
            repo.loginWithGoogle()
                .onSuccess { googleLoading = false; onLoggedIn(it, false) }
                .onFailure { e -> googleLoading = false; error = e.message }
        }
    }

    Row(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(0.45f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            StarField(Modifier.fillMaxSize())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp),
            ) {
                RotatingStarsLogo(LocalReduceMotion.current, diameter = 132.dp, planetRes = "astra-glyph.png")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "ASTRA",
                    style = TextStyle(
                        color = Obsidian.text1,
                        fontSize = 46.sp,
                        fontFamily = Cinzel,
                        letterSpacing = 7.sp,
                    ),
                    modifier = Modifier.semantics { contentDescription = "Astra" },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "sua constelação te espera",
                    style = TextStyle(color = Obsidian.text2, fontSize = 15.sp),
                )
                Spacer(Modifier.height(6.dp))
                LoginConstellation(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Box(
            modifier = Modifier.weight(0.55f).fillMaxHeight().background(
                Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.10f to Obsidian.void.copy(alpha = 0.45f),
                    0.26f to Obsidian.base.copy(alpha = 0.82f),
                    1.0f to Obsidian.base.copy(alpha = 0.82f),
                ),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.width(360.dp).animateContentSize(tween(authDur))) {
                Crossfade(targetState = mode, animationSpec = tween(authDur), label = "authTitle") { m ->
                    Text(
                        text = if (m == AuthMode.LOGIN) "entrar" else "criar conta",
                        style = TextStyle(color = Obsidian.text3, fontSize = 13.sp, fontFamily = DmSerif),
                    )
                }
                Spacer(Modifier.height(18.dp))
                EditorialField(
                    value = email, onValue = { email = it; error = null },
                    label = "email", enabled = !loading,
                    focusRequester = emailFocus,
                    onSubmit = { runCatching { passFocus.requestFocus() } },
                )
                AnimatedVisibility(
                    visible = mode == AuthMode.SIGNUP,
                    enter = fadeIn(tween(authDur)) + expandVertically(tween(authDur)),
                    exit = fadeOut(tween(authDur)) + shrinkVertically(tween(authDur)),
                ) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        EditorialField(
                            value = username, onValue = { username = it.lowercase(); error = null },
                            label = "usuário", enabled = !loading,
                        )
                        Spacer(Modifier.height(14.dp))
                        EditorialField(
                            value = displayName, onValue = { displayName = it; error = null },
                            label = "nome de exibicao", enabled = !loading,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                EditorialField(
                    value = password, onValue = { password = it; error = null },
                    label = "senha", enabled = !loading,
                    password = true, onSubmit = ::submit,
                    focusRequester = passFocus,
                    reveal = showPassword,
                    onToggleReveal = { showPassword = !showPassword },
                    onKey = { capsOn = capsLockOn() },
                )
                if (capsOn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Caps Lock está ligado",
                        style = TextStyle(color = Obsidian.warning, fontSize = 12.sp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.animateContentSize(tween(authDur))) {
                    RulesChecklist(rules)
                }
                Spacer(Modifier.height(18.dp))
                SubmitButton(
                    text = when {
                        loading && mode == AuthMode.LOGIN -> "entrando…"
                        loading -> "criando…"
                        mode == AuthMode.LOGIN -> "ENTRAR"
                        else -> "CRIAR CONTA"
                    },
                    enabled = !loading && allOk,
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
                AnimatedVisibility(
                    visible = mode == AuthMode.LOGIN,
                    enter = fadeIn(tween(authDur)) + expandVertically(tween(authDur)),
                    exit = fadeOut(tween(authDur)) + shrinkVertically(tween(authDur)),
                ) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        OrDivider()
                        Spacer(Modifier.height(16.dp))
                        GoogleButton(loading = googleLoading, enabled = !loading, onClick = ::googleSubmit)
                    }
                }
                Spacer(Modifier.height(20.dp))
                AuthModeToggle(mode) {
                    mode = if (mode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN
                    error = null
                }
            }
        }
    }
}

@Composable
private fun RulesChecklist(rules: List<AuthRule>) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rules.forEach { rule ->
            val fill by animateFloatAsState(
                targetValue = if (rule.ok) 1f else 0f,
                animationSpec = tween(if (LocalReduceMotion.current) 0 else 260),
                label = "ruleFill",
            )
            val txt by animateColorAsState(
                targetValue = if (rule.ok) Obsidian.success else Obsidian.text3,
                animationSpec = tween(260),
                label = "ruleTxt",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(11.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(color = Obsidian.borderMid, radius = r, style = Stroke(1.2.dp.toPx()))
                    if (fill > 0f) drawCircle(color = Obsidian.success.copy(alpha = fill), radius = r * fill)
                }
                Spacer(Modifier.width(9.dp))
                Text(rule.label, style = TextStyle(color = txt, fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun AuthModeToggle(mode: AuthMode, onToggle: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val dur = if (LocalReduceMotion.current) 0 else 220
    Crossfade(targetState = mode, animationSpec = tween(dur), label = "authToggle") { m ->
        val prompt = if (m == AuthMode.LOGIN) "ainda não tem conta?" else "já tem conta?"
        val action = if (m == AuthMode.LOGIN) "criar conta" else "entrar"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(prompt, style = Tipo.descricao)
            Spacer(Modifier.width(6.dp))
            Text(
                action,
                style = TextStyle(color = Obsidian.accent, fontSize = 12.sp, fontFamily = DmSerif),
                modifier = Modifier.clickable(interactionSource = src, indication = null, onClick = onToggle),
            )
        }
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Obsidian.borderDim))
        Text(
            "ou",
            style = Tipo.apoio,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(Obsidian.borderDim))
    }
}

@Composable
private fun GoogleButton(loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    val active = enabled && !loading
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickScale(src)
            .clip(shape)
            .background(if (hovered && active) Obsidian.raised else Color.Transparent)
            .border(1.dp, Obsidian.borderMid, shape)
            .clickable(enabled = active, interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "G",
            style = TextStyle(color = Obsidian.accent, fontSize = 15.sp, fontFamily = DmSerif, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (loading) "abrindo o navegador…" else "entrar com Google",
            style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
        )
    }
}

private const val LAST_EMAIL_KEY = "lastEmail"

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
            style = Tipo.descricao,
        )
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(10.dp)
        var focused by remember { mutableStateOf(false) }
        BasicTextField(
            value = value,
            onValueChange = { onValue(it.take(200)) },
            enabled = enabled,
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
                        .border(1.dp, if (focused) Obsidian.accent.copy(alpha = 0.7f) else Obsidian.borderMid, shape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { inner() }
                    if (onToggleReveal != null) {
                        Spacer(Modifier.width(8.dp))
                        val olho = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .size(24.dp)
                                .clickScale(olho, formaDoFoco = RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(
                                    interactionSource = olho,
                                    indication = null,
                                    onClick = onToggleReveal,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            LIcon(
                                if (reveal) Lucide.EyeOff else Lucide.Eye,
                                tint = Obsidian.text3,
                                size = 16.dp,
                                rotulo = if (reveal) "ocultar a senha" else "mostrar a senha",
                            )
                        }
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
            .clickScale(interaction)
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
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = text, animationSpec = tween(if (reduce) 0 else 220), label = "submitLabel") { t ->
            Text(
                text = t,
                style = TextStyle(
                    color = if (enabled) Obsidian.void else Obsidian.text3,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
