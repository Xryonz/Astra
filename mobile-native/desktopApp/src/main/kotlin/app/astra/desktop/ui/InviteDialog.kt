package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.shell.codigoDoConvite
import app.astra.mobile.core.network.InviteApi
import app.astra.mobile.core.network.dto.InvitePreviewDto
import app.astra.shared.AstraShared
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import app.astra.desktop.ui.theme.Tipo

fun inviteLink(code: String): String = AstraShared.BASE_URL.trimEnd('/') + "/i/" + code

private object CenterOverlay : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@Composable
internal fun DialogShell(
    onClose: () -> Unit,
    largura: Dp = 400.dp,
    respiro: Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val entrada = remember { Animatable(if (reduce) 1f else 0f) }
    var fechando by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (entrada.value < 1f) {
            entrada.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    fun pedirFechar() {
        if (fechando) return
        fechando = true
        if (reduce) { onClose(); return }
        scope.launch {
            entrada.animateTo(0f, tween(160, easing = EaseOutStd))
            onClose()
        }
    }

    Popup(
        popupPositionProvider = CenterOverlay,
        onDismissRequest = { pedirFechar() },
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = entrada.value.coerceIn(0f, 1f) }
                .background(Obsidian.void.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { pedirFechar() }
                .semCursorDeClique(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        val v = entrada.value.coerceIn(0f, 1f)
                        alpha = v
                        val s = 0.94f + 0.06f * v
                        scaleX = s
                        scaleY = s
                        translationY = (1f - v) * 16.dp.toPx()
                    }
                    .width(largura)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Obsidian.raised)
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .padding(respiro),
            ) { content() }
        }
    }
}

@Composable
internal fun DialogField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = Tipo.corpo,
            cursorBrush = SolidColor(Obsidian.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun DialogButton(
    label: String,
    accent: Boolean,
    icone: ImageVector? = null,
    habilitado: Boolean = true,
    onClick: () -> Unit,
) {
    val cor = when {
        !habilitado -> Obsidian.text3.copy(alpha = 0.5f)
        accent -> Obsidian.accent
        else -> Obsidian.text2
    }
    val borda = when {
        !habilitado -> Obsidian.borderDim.copy(alpha = 0.5f)
        accent -> Obsidian.accentDim
        else -> Obsidian.borderDim
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borda, RoundedCornerShape(8.dp))
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icone?.let {
            LIcon(it, tint = cor, size = 14.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(label, style = TextStyle(color = cor, fontSize = 13.sp))
    }
}

@Composable
fun InvitePeopleDialog(
    serverName: String,
    inviteCode: String?,
    onAdd: (username: String, onResult: (String?) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val submit = {
        if (!busy && username.isNotBlank()) {
            busy = true
            msg = null
            onAdd(username) { err ->
                busy = false
                if (err == null) {
                    msg = "$username entrou na constelação" to true
                    username = ""
                } else {
                    msg = err to false
                }
            }
        }
    }

    DialogShell(onClose) {
        Text(
            "convidar pessoas",
            style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "para $serverName",
            style = Tipo.apoio,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        Text("adicionar pelo nome de usuário", style = Tipo.rotulo)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                DialogField(username, "@usuario", { username = it; msg = null }, submit)
            }
            Spacer(Modifier.width(8.dp))
            DialogButton(if (busy) "…" else "adicionar", accent = true) { submit() }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "ela entra na hora, sem precisar aceitar nada.",
            style = Tipo.apoio,
        )

        msg?.let { (text, ok) ->
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp),
            )
        }

        if (inviteCode != null) {
            Spacer(Modifier.height(16.dp))
            CartaoInterno(fundo = Obsidian.hover, padding = PaddingValues(12.dp)) {
                Text("ou mande este link", style = Tipo.rotulo)
                Spacer(Modifier.height(7.dp))
                val link = remember(inviteCode) { inviteLink(inviteCode) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Obsidian.overlay)
                            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            link,
                            style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, fontFamily = DmMono),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    DialogButton(if (copied) "copiado" else "copiar", accent = false) {
                        clipboard.setText(AnnotatedString(link))
                        copied = true
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton("fechar", accent = false) { onClose() }
        }
    }
}

private const val ESPERA_DA_PREVIA_MS = 350L
private const val CODIGO_PLAUSIVEL = 8

@Composable
fun JoinByInviteDialog(
    onJoin: (raw: String, onResult: (String?) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val api = remember { GlobalContext.get().get<InviteApi>() }
    var raw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var previa by remember { mutableStateOf<InvitePreviewDto?>(null) }
    var procurando by remember { mutableStateOf(false) }

    val codigo = remember(raw) { codigoDoConvite(raw) }

    LaunchedEffect(codigo) {
        previa = null
        procurando = false
        if (codigo.length < CODIGO_PLAUSIVEL) return@LaunchedEffect
        delay(ESPERA_DA_PREVIA_MS)
        procurando = true
        val achada = runCatching { api.preview(codigo).data }.getOrNull()
        procurando = false
        previa = achada
        if (achada == null) err = "Convite inválido ou expirado"
    }

    val alvo = previa
    val podeEntrar = alvo != null && !alvo.isGroup && !busy

    val submit = {
        if (podeEntrar) {
            busy = true
            err = null
            onJoin(raw) { e ->
                busy = false
                if (e == null) onClose() else err = e
            }
        }
    }

    DialogShell(onClose) {
        Text(
            "entrar com convite",
            style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "cole o link que te mandaram — ou so o código.",
            style = Tipo.apoio,
        )
        Spacer(Modifier.height(14.dp))
        DialogField(raw, inviteLink("codigo-do-convite"), { raw = it; err = null }, submit)

        if (procurando) {
            Spacer(Modifier.height(10.dp))
            Text("procurando a constelação…", style = Tipo.apoio)
        }

        val reduzir = LocalReduceMotion.current
        AnimatedVisibility(
            visible = alvo != null,
            enter = fadeIn(tween(if (reduzir) 0 else 140, easing = EaseOutStd)) +
                slideInVertically(tween(if (reduzir) 0 else 140, easing = EaseOutStd)) { it / 3 },
        ) {
            alvo?.let {
                Column {
                    Spacer(Modifier.height(12.dp))
                    CartaoDaConstelacao(it)
                }
            }
        }

        err?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Tipo.erro)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton("cancelar", accent = false) { onClose() }
            DialogButton(
                if (busy) "entrando…" else "entrar",
                accent = true,
                habilitado = podeEntrar,
            ) { submit() }
        }
    }
}

@Composable
private fun CartaoDaConstelacao(previa: InvitePreviewDto) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopAvatar(previa.iconUrl, previa.name, 34)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                previa.name,
                style = TextStyle(color = Obsidian.text1, fontSize = 14.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            if (previa.isGroup) {
                Text("grupo privado — peça para te adicionarem", style = Tipo.apoio)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LIcon(Lucide.Users, tint = Obsidian.text3, size = 12.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        pessoasNaConstelacao(previa.count?.members ?: 0),
                        style = TextStyle(color = Obsidian.text3, fontSize = 12.sp, fontFamily = DmMono),
                    )
                }
            }
        }
    }
}

private fun pessoasNaConstelacao(quantas: Int): String =
    if (quantas == 1) "1 pessoa" else "$quantas pessoas"
