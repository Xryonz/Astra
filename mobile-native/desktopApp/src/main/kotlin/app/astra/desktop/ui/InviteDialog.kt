package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.koin.core.context.GlobalContext
import app.astra.desktop.ui.theme.Tipo

fun inviteLink(code: String): String = AstraShared.BASE_URL.trimEnd('/') + "/i/" + code

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

    Dialogo(titulo = "convidar pessoas", aoFechar = onClose, largura = LarguraDoDialogo.MEDIA) { fechar ->
        val focoInicial = rememberFocoAoAbrir()
        Spacer(Modifier.height(8.dp))
        Text(
            "para $serverName",
            style = TextoDoDialogo,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        RotuloDeCampo("adicionar pelo nome de usuário")
        Row(verticalAlignment = Alignment.CenterVertically) {
            CampoDeDialogo(
                username,
                { username = it; msg = null },
                Modifier.weight(1f),
                dica = "@usuário",
                aoEnviar = submit,
                foco = focoInicial,
            )
            Spacer(Modifier.width(8.dp))
            BotaoDeDialogo(
                if (busy) "adicionando…" else "adicionar",
                TipoDeBotao.PRINCIPAL,
                onClick = submit,
                ligado = username.isNotBlank() && !busy,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "ela entra na hora, sem precisar aceitar nada.",
            style = Tipo.apoio,
        )

        msg?.let { (text, ok) ->
            Spacer(Modifier.height(8.dp))
            RecadoDoDialogo(text, sucesso = ok)
        }

        if (inviteCode != null) {
            Spacer(Modifier.height(20.dp))
            RotuloDeCampo("ou mande este link")
            val link = remember(inviteCode) { inviteLink(inviteCode) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Obsidian.base)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        link,
                        style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, fontFamily = DmMono),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                BotaoDeDialogo(
                    if (copied) "copiado" else "copiar",
                    TipoDeBotao.SECUNDARIO,
                    onClick = {
                        clipboard.setText(AnnotatedString(link))
                        copied = true
                    },
                )
            }
        }

        RodapeDoDialogo {
            BotaoDeDialogo("fechar", TipoDeBotao.SECUNDARIO, onClick = fechar)
        }
    }
}

private const val ESPERA_DA_PREVIA_MS = 350L

@Composable
fun JoinByInviteDialog(
    onJoin: (raw: String, onResult: (String?) -> Unit) -> Unit,
    onClose: () -> Unit,
    codigoInicial: String = "",
) {
    val api = remember { GlobalContext.get().get<InviteApi>() }
    var raw by remember { mutableStateOf(codigoInicial) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var previa by remember { mutableStateOf<InvitePreviewDto?>(null) }
    var procurando by remember { mutableStateOf(false) }

    val codigo = remember(raw) { codigoDoConvite(raw) }

    LaunchedEffect(codigo) {
        previa = null
        procurando = false
        if (codigo.isEmpty()) return@LaunchedEffect
        delay(ESPERA_DA_PREVIA_MS)
        procurando = true
        val achada = runCatching { api.preview(codigo).data }.getOrNull()
        procurando = false
        previa = achada
        if (achada == null) err = "convite inválido ou expirado."
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

    Dialogo(titulo = "entrar com convite", aoFechar = onClose, largura = LarguraDoDialogo.MEDIA) { fechar ->
        val focoInicial = rememberFocoAoAbrir()
        Spacer(Modifier.height(8.dp))
        Text("cole o link do convite ou apenas o código.", style = TextoDoDialogo)
        Spacer(Modifier.height(16.dp))
        CampoDeDialogo(
            raw,
            { raw = it; err = null },
            dica = inviteLink("codigo-do-convite"),
            aoEnviar = submit,
            foco = focoInicial,
        )

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
                    CartaoDaConstelacao(it, fundo = Obsidian.hover)
                }
            }
        }

        err?.let {
            Spacer(Modifier.height(8.dp))
            RecadoDoDialogo(it)
        }
        RodapeDoDialogo {
            BotaoDeDialogo("cancelar", TipoDeBotao.SECUNDARIO, onClick = fechar)
            BotaoDeDialogo(
                if (busy) "entrando…" else "entrar",
                TipoDeBotao.PRINCIPAL,
                onClick = submit,
                ligado = podeEntrar,
            )
        }
    }
}

@Composable
internal fun CartaoDaConstelacao(previa: InvitePreviewDto, fundo: Color = Obsidian.overlay) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(fundo)
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
                Text("grupo privado: peça a quem administra para adicionar você", style = Tipo.apoio)
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
