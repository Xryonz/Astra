package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.shared.AstraShared

// Convite nativo do Astra. Dois caminhos, porque resolvem coisas diferentes:
//   - por @usuario: entra na hora, a outra ponta não faz nada (backend checa
//     permissao, banimento e se já e membro).
//   - por LINK: pra quem você não sabe o @, ou pra mandar por fora. O link e o
//     atalho curto que o proprio backend serve (`/i/<codigo>`, index.ts).
// A ponta que faltava era ENTRAR: o desktop sabia gerar convite mas não sabia
// usar um, entao convite recebido morria.

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
internal fun DialogShell(onClose: () -> Unit, content: @Composable () -> Unit) {
    Popup(
        popupPositionProvider = CenterOverlay,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Obsidian.void.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(400.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Obsidian.raised)
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(14.dp))
                    // Clique no cartao NAO fecha (so o scrim atras).
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .padding(18.dp),
            ) { content() }
        }
    }
}

// Campo e botao dos dialogos. Nasceram aqui (convite) mas nao tem nada de
// convite dentro — o dialogo de enquete usa os mesmos. Copiar seria repetir o
// erro do cartao de perfil, que existia em dois arquivos e divergiu.
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
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun DialogButton(label: String, accent: Boolean, icone: ImageVector? = null, onClick: () -> Unit) {
    val cor = if (accent) Obsidian.accent else Obsidian.text2
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (accent) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
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

// "convidar pessoas" — quem já esta na constelação chama.
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
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        Text("adicionar pelo nome de usuário", style = TextStyle(color = Obsidian.text2, fontSize = 12.sp))
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
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
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
            // O segundo caminho (mandar o link) virou CARTAO. Sao duas maneiras
            // diferentes de convidar a mesma pessoa; o traco dizia "acabou uma
            // coisa, comecou outra", e o cartao mostra as duas lado a lado.
            CartaoInterno(fundo = Obsidian.hover, padding = PaddingValues(12.dp)) {
                Text("ou mande este link", style = TextStyle(color = Obsidian.text2, fontSize = 12.sp))
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

// "entrar com convite" — a ponta que faltava. Aceita o link inteiro ou so o
// codigo; quem separa os dois e o ShellVm.
@Composable
fun JoinByInviteDialog(
    onJoin: (raw: String, onResult: (String?) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    var raw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    val submit = {
        if (!busy && raw.isNotBlank()) {
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
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        )
        Spacer(Modifier.height(14.dp))
        DialogField(raw, inviteLink("codigo-do-convite"), { raw = it; err = null }, submit)
        err?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = TextStyle(color = Obsidian.danger, fontSize = 12.sp))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton("cancelar", accent = false) { onClose() }
            DialogButton(if (busy) "entrando…" else "entrar", accent = true) { submit() }
        }
    }
}
