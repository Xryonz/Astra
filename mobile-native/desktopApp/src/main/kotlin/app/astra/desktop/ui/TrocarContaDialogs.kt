package app.astra.desktop.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.dto.TrocarEmailRequest
import app.astra.mobile.core.network.dto.TrocarUsuarioRequest
import app.astra.mobile.core.network.dto.VerifyEmailRequest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import retrofit2.HttpException
import app.astra.desktop.ui.theme.Tipo

private const val DIGITOS_DO_CODIGO = 6

internal val EstiloDoCodigo: TextStyle
    get() = TextStyle(
        color = Obsidian.text1,
        fontSize = 24.sp,
        fontFamily = DmMono,
        textAlign = TextAlign.Center,
        letterSpacing = 8.sp,
    )

internal fun recadoDaApi(t: Throwable?, reserva: String): String {
    val http = t as? HttpException ?: return "sem conexão com o servidor"
    val corpo = runCatching { http.response()?.errorBody()?.string() }.getOrNull()
    val dito = corpo?.let {
        runCatching { Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }.getOrNull()
    }
    return dito?.takeIf { it.isNotBlank() } ?: "$reserva (erro ${http.code()})"
}

@Composable
internal fun TrocarEmailDialog(emailAtual: String?, onClose: () -> Unit, aoTrocar: (String) -> Unit) {
    val api = remember { GlobalContext.get().get<AuthApi>() }
    val escopo = rememberCoroutineScope()
    val foco = remember { FocusRequester() }

    var novo by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var enviadoPara by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(enviadoPara) { runCatching { foco.requestFocus() } }

    val podeEnviar = !ocupado && novo.contains('@') && novo.length >= 5 && senha.isNotBlank()

    fun pedirCodigo() {
        if (!podeEnviar) return
        ocupado = true
        erro = null
        escopo.launch {
            val r = runCatching { api.trocarEmail(TrocarEmailRequest(novo.trim(), senha)) }
            ocupado = false
            r.onSuccess {
                enviadoPara = it.data?.pendingEmail?.ifBlank { null } ?: novo.trim()
                senha = ""
            }.onFailure { erro = recadoDaApi(it, "não foi possível pedir o código") }
        }
    }

    fun confirmar() {
        if (ocupado || codigo.length < DIGITOS_DO_CODIGO) return
        ocupado = true
        erro = null
        escopo.launch {
            val r = runCatching { api.confirmarTrocaDeEmail(VerifyEmailRequest(codigo)) }
            ocupado = false
            r.onSuccess {
                aoTrocar(it.data?.email?.ifBlank { null } ?: enviadoPara.orEmpty())
                onClose()
            }.onFailure {
                erro = recadoDaApi(it, "não foi possível confirmar")
                codigo = ""
            }
        }
    }

    Dialogo(titulo = "trocar de e-mail", aoFechar = onClose, largura = LarguraDoDialogo.MEDIA) { fechar ->
        Spacer(Modifier.height(8.dp))

        val pendente = enviadoPara
        if (pendente == null) {
            Text(
                "o código vai para o endereço novo. A conta só muda depois que ele for confirmado; " +
                    "até lá, ${emailAtual ?: "o e-mail atual"} continua valendo.",
                style = TextoDoDialogo,
            )
            Spacer(Modifier.height(16.dp))
            RotuloDeCampo("e-mail novo")
            CampoDeDialogo(
                novo,
                { novo = it; erro = null },
                dica = "nome@exemplo.com",
                aoEnviar = ::pedirCodigo,
                foco = foco,
                ligado = !ocupado,
            )
            Spacer(Modifier.height(14.dp))
            RotuloDeCampo("senha atual")
            CampoDeDialogo(
                senha,
                { senha = it; erro = null },
                dica = "senha atual",
                aoEnviar = ::pedirCodigo,
                ligado = !ocupado,
                segredo = true,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "a senha impede que alguém com o seu computador destrancado tome a conta.",
                style = Tipo.apoio,
            )
        } else {
            Text(
                "enviamos um código de seis dígitos para $pendente.",
                style = TextoDoDialogo,
            )
            Spacer(Modifier.height(16.dp))
            CampoDeDialogo(
                codigo,
                { bruto ->
                    codigo = bruto.filter { it.isDigit() }.take(DIGITOS_DO_CODIGO)
                    erro = null
                    if (codigo.length == DIGITOS_DO_CODIGO) confirmar()
                },
                foco = foco,
                ligado = !ocupado,
                estilo = EstiloDoCodigo,
            )
        }

        erro?.let {
            Spacer(Modifier.height(10.dp))
            RecadoDoDialogo(it)
        }

        RodapeDoDialogo {
            BotaoDeDialogo("cancelar", TipoDeBotao.SECUNDARIO, onClick = fechar, ligado = !ocupado)
            if (pendente == null) {
                BotaoDeDialogo(
                    if (ocupado) "enviando…" else "enviar código",
                    TipoDeBotao.PRINCIPAL,
                    onClick = ::pedirCodigo,
                    ligado = podeEnviar,
                )
            } else {
                BotaoDeDialogo(
                    if (ocupado) "conferindo…" else "confirmar",
                    TipoDeBotao.PRINCIPAL,
                    onClick = ::confirmar,
                    ligado = !ocupado && codigo.length == DIGITOS_DO_CODIGO,
                )
            }
        }
    }
}

@Composable
internal fun TrocarUsuarioDialog(atual: String?, onClose: () -> Unit, aoTrocar: (String) -> Unit) {
    val api = remember { GlobalContext.get().get<AuthApi>() }
    val escopo = rememberCoroutineScope()

    var nome by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    val limpo = nome.trim().lowercase()
    val formatoOk = limpo.length in 3..32 && limpo.all { it.isDigit() || it in 'a'..'z' || it == '_' }
    val podeSalvar = !ocupado && formatoOk && limpo != atual

    fun salvar() {
        if (!podeSalvar) return
        ocupado = true
        erro = null
        escopo.launch {
            val r = runCatching { api.trocarUsuario(TrocarUsuarioRequest(limpo)) }
            ocupado = false
            r.onSuccess {
                aoTrocar(it.data?.username?.ifBlank { null } ?: limpo)
                onClose()
            }.onFailure { erro = recadoDaApi(it, "não foi possível trocar") }
        }
    }

    Dialogo(titulo = "trocar de nome de usuário", aoFechar = onClose) { fechar ->
        val focoInicial = rememberFocoAoAbrir()
        Spacer(Modifier.height(8.dp))
        Text(
            "é o nome usado na busca e nas menções. O atual é @${atual ?: "…"}.",
            style = TextoDoDialogo,
        )
        Spacer(Modifier.height(16.dp))
        RotuloDeCampo("nome novo")
        CampoDeDialogo(
            nome,
            { nome = it; erro = null },
            dica = "nome_de_usuario",
            aoEnviar = ::salvar,
            foco = focoInicial,
            ligado = !ocupado,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "de 3 a 32 caracteres, apenas minúsculas, números e underscore.",
            style = Tipo.apoio,
        )

        if (nome.isNotBlank() && !formatoOk) {
            Spacer(Modifier.height(6.dp))
            RecadoDoDialogo("este formato não é aceito.")
        }
        erro?.let {
            Spacer(Modifier.height(10.dp))
            RecadoDoDialogo(it)
        }

        RodapeDoDialogo {
            BotaoDeDialogo("cancelar", TipoDeBotao.SECUNDARIO, onClick = fechar, ligado = !ocupado)
            BotaoDeDialogo(
                if (ocupado) "salvando…" else "salvar",
                TipoDeBotao.PRINCIPAL,
                onClick = ::salvar,
                ligado = podeSalvar,
            )
        }
    }
}
