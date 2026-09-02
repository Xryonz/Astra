package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.dto.TrocarEmailRequest
import app.astra.mobile.core.network.dto.TrocarUsuarioRequest
import app.astra.mobile.core.network.dto.VerifyEmailRequest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import retrofit2.HttpException

private const val DIGITOS_DO_CODIGO = 6

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

    DialogShell(onClose = onClose, largura = 400.dp) {
        Text(
            "Trocar de e-mail",
            style = TextStyle(color = Obsidian.text1, fontSize = 19.sp, fontFamily = DmSerif),
        )
        Spacer(Modifier.height(6.dp))

        val pendente = enviadoPara
        if (pendente == null) {
            Text(
                "O código vai para o endereço novo. A conta só muda quando ele voltar certo — " +
                    "até lá, ${emailAtual ?: "o e-mail atual"} continua valendo.",
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
            )
            Spacer(Modifier.height(16.dp))
            RotuloDoCampo("E-mail novo")
            CampoDeLinha("nome@exemplo.com", novo, foco, !ocupado) { novo = it; erro = null }
            Spacer(Modifier.height(14.dp))
            RotuloDoCampo("Senha atual")
            CampoDeLinha("senha atual", senha, null, !ocupado, segredo = true) { senha = it; erro = null }
            Spacer(Modifier.height(6.dp))
            Text(
                "A senha impede que alguém com o seu computador destrancado tome a conta.",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            )
        } else {
            Text(
                "Enviamos um código de seis dígitos para $pendente.",
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
            )
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = codigo,
                onValueChange = { bruto ->
                    codigo = bruto.filter { it.isDigit() }.take(DIGITOS_DO_CODIGO)
                    erro = null
                    if (codigo.length == DIGITOS_DO_CODIGO) confirmar()
                },
                singleLine = true,
                enabled = !ocupado,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    color = Obsidian.text1,
                    fontSize = 24.sp,
                    fontFamily = DmMono,
                    textAlign = TextAlign.Center,
                    letterSpacing = 8.sp,
                ),
                cursorBrush = SolidColor(Obsidian.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(foco)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Obsidian.base)
                    .border(1.dp, Obsidian.borderMid, RoundedCornerShape(9.dp))
                    .padding(vertical = 14.dp),
            )
        }

        erro?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = TextStyle(color = Obsidian.danger, fontSize = 11.sp))
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotaoDoDialogo("Cancelar", primario = false, ligado = !ocupado, onClick = onClose)
            Spacer(Modifier.width(10.dp))
            if (pendente == null) {
                BotaoDoDialogo(
                    if (ocupado) "Enviando…" else "Enviar código",
                    primario = true,
                    ligado = podeEnviar,
                    onClick = ::pedirCodigo,
                )
            } else {
                BotaoDoDialogo(
                    if (ocupado) "Conferindo…" else "Confirmar",
                    primario = true,
                    ligado = !ocupado && codigo.length == DIGITOS_DO_CODIGO,
                    onClick = ::confirmar,
                )
            }
        }
    }
}

@Composable
internal fun TrocarUsuarioDialog(atual: String?, onClose: () -> Unit, aoTrocar: (String) -> Unit) {
    val api = remember { GlobalContext.get().get<AuthApi>() }
    val escopo = rememberCoroutineScope()
    val foco = remember { FocusRequester() }

    var nome by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }

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

    DialogShell(onClose = onClose, largura = 400.dp) {
        Text(
            "Trocar de nome de usuário",
            style = TextStyle(color = Obsidian.text1, fontSize = 19.sp, fontFamily = DmSerif),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "É por ele que as pessoas te encontram na busca e te mencionam. " +
                "Hoje você é @${atual ?: "—"}.",
            style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
        )
        Spacer(Modifier.height(16.dp))
        RotuloDoCampo("Nome novo")
        CampoDeLinha("nome_de_usuario", nome, foco, !ocupado) { nome = it; erro = null }
        Spacer(Modifier.height(6.dp))
        Text(
            "De 3 a 32 caracteres, apenas minúsculas, números e underscore.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        )

        if (nome.isNotBlank() && !formatoOk) {
            Spacer(Modifier.height(6.dp))
            Text(
                "esse formato não serve",
                style = TextStyle(color = Obsidian.danger, fontSize = 11.sp),
            )
        }
        erro?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = TextStyle(color = Obsidian.danger, fontSize = 11.sp))
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotaoDoDialogo("Cancelar", primario = false, ligado = !ocupado, onClick = onClose)
            Spacer(Modifier.width(10.dp))
            BotaoDoDialogo(
                if (ocupado) "Salvando…" else "Pronto",
                primario = true,
                ligado = podeSalvar,
                onClick = ::salvar,
            )
        }
    }
}

@Composable
private fun RotuloDoCampo(texto: String) {
    Text(
        texto,
        style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun CampoDeLinha(
    dica: String,
    valor: String,
    foco: FocusRequester?,
    ligado: Boolean,
    segredo: Boolean = false,
    onChange: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.base)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (valor.isEmpty()) {
            Text(dica, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
        }
        BasicTextField(
            value = valor,
            onValueChange = onChange,
            singleLine = true,
            enabled = ligado,
            visualTransformation = if (segredo) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (foco != null) Modifier.focusRequester(foco) else Modifier),
        )
    }
}
