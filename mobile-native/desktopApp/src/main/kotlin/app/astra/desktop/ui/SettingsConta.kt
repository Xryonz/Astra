package app.astra.desktop.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ModoTransmissao
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.SessionApi
import app.astra.mobile.core.network.dto.ApagarContaRequest
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.RecusaDeApagar
import app.astra.mobile.core.network.dto.RevokeOthersRequest
import app.astra.mobile.core.network.dto.SessionDto
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext

@Composable
internal fun AccountSection(me: ProfileUserDto?, aoSairDaConta: () -> Unit) {
    val emTransmissao by ModoTransmissao.ativo.collectAsState()
    val semSenha = me?.hasPassword == false
    var trocandoSenha by remember { mutableStateOf(false) }
    var conferindoEmail by remember { mutableStateOf(false) }
    var conferidoAgora by remember { mutableStateOf(false) }
    var trocandoEmail by remember { mutableStateOf(false) }
    var trocandoUsuario by remember { mutableStateOf(false) }
    var usuarioAgora by remember { mutableStateOf<String?>(null) }
    var emailAgora by remember { mutableStateOf<String?>(null) }

    var sessoes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(me?.id) {
        sessoes = runCatching { GlobalContext.get().get<SessionApi>().list().data?.sessions?.size }
            .getOrNull()
    }

    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        val usuario = usuarioAgora ?: me?.username
        val email = emailAgora ?: me?.email
        LinhaDaConta(
            rotulo = "Nome de usuário",
            valor = usuario?.let { "@$it" } ?: "—",
            acao = if (usuario == null) null else "Editar",
            aoAgir = { trocandoUsuario = true },
        )
        LinhaDaConta(
            rotulo = "E-mail",
            valor = email?.let { if (emTransmissao) mascarar(it) else it } ?: "—",
            acao = if (email == null || semSenha) null else "Editar",
            aoAgir = { trocandoEmail = true },
        )
        LinhaDaConta(
            rotulo = "Senha",
            valor = if (semSenha) "não definida" else "••••••••",
            acao = if (semSenha) "Definir" else "Editar",
            aoAgir = { trocandoSenha = true },
        )
        val conferido = me?.emailVerifiedAt != null || conferidoAgora
        LinhaDaConta(
            rotulo = "Verificação",
            valor = if (conferido) "e-mail conferido" else "e-mail não conferido",
            acao = if (conferido) null else "Conferir",
            aoAgir = { conferindoEmail = true },
        )
        LinhaDaConta("Sessões", sessoes?.let { if (it == 1) "1 aberta" else "$it abertas" } ?: "…")
        LinhaDaConta("Membro desde", mesEAno(me?.createdAt))
    }

    if (semSenha) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Conta Google sem senha. Defina uma para entrar também por e-mail.",
            style = Tipo.apoio,
        )
    }

    if (trocandoSenha) {
        DialogoDeSenha(hasPassword = !semSenha, onClose = { trocandoSenha = false })
    }

    if (conferindoEmail) {
        VerificarEmailDialog(
            email = emailAgora ?: me?.email,
            onClose = { conferindoEmail = false },
            aoConferir = { conferidoAgora = true },
        )
    }

    if (trocandoEmail) {
        TrocarEmailDialog(
            emailAtual = emailAgora ?: me?.email,
            onClose = { trocandoEmail = false },
            aoTrocar = { emailAgora = it; conferidoAgora = true },
        )
    }

    if (trocandoUsuario) {
        TrocarUsuarioDialog(
            atual = usuarioAgora ?: me?.username,
            onClose = { trocandoUsuario = false },
            aoTrocar = { usuarioAgora = it },
        )
    }

    SettingsDivider()
    ApagarConta(me, aoSairDaConta)
}

@Composable
private fun LinhaDaConta(
    rotulo: String,
    valor: String,
    acao: String? = null,
    aoAgir: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rotulo,
            style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
            modifier = Modifier.weight(1f),
        )
        Text(
            valor,
            style = Tipo.corpo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 240.dp),
        )
        if (acao != null) {
            Spacer(Modifier.width(12.dp))
            val toque = remember { MutableInteractionSource() }
            val sobre by toque.collectIsHoveredAsState()
            Box(
                Modifier
                    .clickScale(toque)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sobre) Obsidian.hover else Obsidian.overlay)
                    .hoverable(toque)
                    .clickable(interactionSource = toque, indication = null, onClick = aoAgir)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(acao, style = TextStyle(color = Obsidian.text1, fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun ApagarConta(me: ProfileUserDto?, aoSairDaConta: () -> Unit) {
    val koin = GlobalContext.get()
    val escopo = rememberCoroutineScope()
    var aberto by remember { mutableStateOf(false) }
    var confirmacao by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var indo by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var presas by remember { mutableStateOf<List<String>>(emptyList()) }
    val temSenha = me?.hasPassword != false
    val arroba = me?.username.orEmpty()

    Text("Apagar conta", style = TextStyle(color = Obsidian.danger, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "acaba na hora e não tem volta.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(12.dp))

    if (!aberto) {
        BotaoDePerigo("apagar minha conta", Lucide.Trash2) {
            aberto = true; confirmacao = ""; senha = ""; erro = null; presas = emptyList()
        }
        return
    }
    Text(
        "o que você escreveu fica, assinado “conta apagada” — a conversa é de duas " +
            "pessoas, e sua saída não deveria abrir buracos no que a outra leu.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(12.dp))

    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
        ProfileField("digite @$arroba para confirmar", confirmacao, "@$arroba", max = 40) { confirmacao = it }
        if (temSenha) {
            Spacer(Modifier.height(10.dp))
            FieldLabel("sua senha")
            PasswordField("senha atual", senha) { senha = it }
        }
        if (presas.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "você ainda é dono de: ${presas.joinToString(", ")}. transfira ou exclua " +
                    "antes — constelação com gente dentro não some junto com a sua conta.",
                style = TextStyle(color = Obsidian.danger, fontSize = 11.sp, lineHeight = 16.sp),
            )
        } else erro?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = TextStyle(color = Obsidian.danger, fontSize = 11.sp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AboutButton(if (indo) "apagando…" else "apagar para sempre", accent = false, icone = Lucide.Trash2) {
                if (indo || confirmacao.trim().lowercase().removePrefix("@") != arroba.lowercase()) {
                    erro = "digite exatamente @$arroba."
                    return@AboutButton
                }
                indo = true; erro = null; presas = emptyList()
                escopo.launch {
                    val r = runCatching {
                        koin.get<AuthApi>().apagarConta(
                            ApagarContaRequest(confirmacao.trim().removePrefix("@"), senha.ifBlank { null }),
                        )
                    }.getOrNull()
                    indo = false
                    when {
                        r?.isSuccessful == true -> {
                            aoSairDaConta()
                        }
                        r?.code() == 409 -> {
                            val corpo = runCatching {
                                koin.get<Json>().decodeFromString<RecusaDeApagar>(
                                    r.errorBody()?.string().orEmpty(),
                                )
                            }.getOrNull()
                            presas = corpo?.constelacoes?.map { it.name }.orEmpty()
                            if (presas.isEmpty()) erro = corpo?.error ?: "não foi possível apagar."
                        }
                        r?.code() == 401 -> erro = "senha incorreta."
                        else -> erro = "não foi possível apagar. verifique a conexão."
                    }
                }
            }
            AboutButton("cancelar", accent = false) { aberto = false }
        }
    }
}

@Composable
internal fun SessionsSection() {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionDto>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        sessions = runCatching { koin.get<SessionApi>().list().data?.sessions }.getOrNull() ?: emptyList()
    }

    Text(
        "cada linha é um acesso ativo na sua conta. não reconhece algum? derrube.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(14.dp))

    msg?.let { (text, ok) ->
        Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
        Spacer(Modifier.height(10.dp))
    }

    val list = sessions
    when {
        list == null -> Text("carregando…", style = Tipo.descricao)
        list.isEmpty() -> Text("nenhuma sessão ativa.", style = Tipo.descricao)
        else -> Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { s ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Obsidian.raised.copy(alpha = 0.5f))
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            prettyAgent(s.userAgent),
                            style = Tipo.corpo,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(s.ip, prettyDate(s.lastUsedAt)?.let { "visto $it" })
                                .joinToString("  ·  "),
                            style = Tipo.apoio,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    BotaoIcone(Lucide.LogOut, "derrubar esta sessão", danger = true, ocupado = busy) {
                        busy = true; msg = null
                        scope.launch {
                            val r = runCatching { koin.get<SessionApi>().revoke(s.id) }
                            busy = false
                            msg = if (r.isSuccess) "sessão derrubada" to true
                            else "não foi possível derrubar" to false
                            reload++
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    AboutButton(if (busy) "…" else "derrubar todas as outras", accent = false, icone = Lucide.LogOut) {
        if (busy) return@AboutButton
        busy = true; msg = null
        scope.launch {
            val token = koin.get<SessionStore>().load()?.refreshToken
            if (token.isNullOrBlank()) {
                busy = false
                msg = "não achei o token desta sessão" to false
                return@launch
            }
            val r = runCatching { koin.get<SessionApi>().revokeOthers(RevokeOthersRequest(token)) }
            busy = false
            msg = r.map { "derrubadas: ${it.data?.revokedCount ?: 0}" to true }
                .getOrElse { "não foi possível derrubar as outras" to false }
            reload++
        }
    }
    Spacer(Modifier.height(20.dp))
}

private fun prettyAgent(ua: String?): String {
    val s = ua?.trim().orEmpty()
    if (s.isEmpty()) return "dispositivo desconhecido"
    if (s.contains("Astra", true)) return s.take(48)
    val os = when {
        s.contains("Windows", true) -> "Windows"
        s.contains("Android", true) -> "Android"
        s.contains("iPhone", true) || s.contains("iPad", true) -> "iOS"
        s.contains("Mac", true) -> "macOS"
        s.contains("Linux", true) -> "Linux"
        else -> null
    }
    val app = when {
        s.contains("Edg", true) -> "Edge"
        s.contains("Chrome", true) -> "Chrome"
        s.contains("Firefox", true) -> "Firefox"
        s.contains("Safari", true) -> "Safari"
        else -> "navegador"
    }
    return listOfNotNull(app, os).joinToString(" · ")
}

private fun prettyDate(iso: String?): String? {
    val s = iso?.trim().orEmpty()
    if (s.length < 16) return null
    val d = s.substring(8, 10)
    val m = s.substring(5, 7)
    val hm = s.substring(11, 16)
    return "$d/$m $hm"
}
