package app.astra.desktop.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.AuthApi
import app.astra.mobile.core.network.dto.VerifyEmailRequest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import app.astra.desktop.ui.theme.Tipo

private const val TAMANHO_DO_CODIGO = 6

@Composable
internal fun VerificarEmailDialog(email: String?, onClose: () -> Unit, aoConferir: () -> Unit) {
    val api = remember { GlobalContext.get().get<AuthApi>() }
    val escopo = rememberCoroutineScope()

    var codigo by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var recado by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    fun conferir() {
        if (ocupado || codigo.length < TAMANHO_DO_CODIGO) return
        ocupado = true
        recado = null
        escopo.launch {
            val r = runCatching { api.verifyEmail(VerifyEmailRequest(codigo)) }
            ocupado = false
            if (r.isSuccess) {
                aoConferir()
                onClose()
            } else {
                recado = "código incorreto ou vencido." to false
                codigo = ""
            }
        }
    }

    fun reenviar() {
        if (ocupado) return
        ocupado = true
        recado = null
        escopo.launch {
            val r = runCatching { api.resendEmailCode() }
            ocupado = false
            recado = if (r.isSuccess) "código novo enviado." to true
            else "não foi possível enviar agora. Tente de novo em alguns minutos." to false
        }
    }

    Dialogo(titulo = "confirme seu e-mail", aoFechar = onClose) { fechar ->
        val focoInicial = rememberFocoAoAbrir()
        Spacer(Modifier.height(8.dp))
        Text(
            if (email.isNullOrBlank()) "enviamos um código de seis dígitos para o seu e-mail."
            else "enviamos um código de seis dígitos para $email.",
            style = TextoDoDialogo,
        )
        Spacer(Modifier.height(16.dp))

        CampoDeDialogo(
            codigo,
            { bruto ->
                codigo = bruto.filter { it.isDigit() }.take(TAMANHO_DO_CODIGO)
                if (codigo.length == TAMANHO_DO_CODIGO) conferir()
            },
            foco = focoInicial,
            ligado = !ocupado,
            estilo = EstiloDoCodigo,
        )

        recado?.let { (texto, bom) ->
            Spacer(Modifier.height(10.dp))
            RecadoDoDialogo(texto, sucesso = bom)
        }

        RodapeDoDialogo {
            BotaoDeDialogo("reenviar", TipoDeBotao.SECUNDARIO, onClick = ::reenviar, ligado = !ocupado)
            Spacer(Modifier.weight(1f))
            BotaoDeDialogo("agora não", TipoDeBotao.SECUNDARIO, onClick = fechar, ligado = !ocupado)
            BotaoDeDialogo(
                "confirmar",
                TipoDeBotao.PRINCIPAL,
                onClick = ::conferir,
                ligado = !ocupado && codigo.length == TAMANHO_DO_CODIGO,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "o código vale por quinze minutos. Sem confirmar, você continua entrando e lendo, " +
                "mas não cria constelação nem aceita convite.",
            style = Tipo.nota,
        )
    }
}
