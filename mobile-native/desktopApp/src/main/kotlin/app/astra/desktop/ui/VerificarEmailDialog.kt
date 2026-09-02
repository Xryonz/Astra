package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
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
    val foco = remember { FocusRequester() }

    var codigo by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var recado by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }

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
                recado = "código incorreto ou vencido" to false
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
            recado = if (r.isSuccess) "código novo enviado" to true
            else "não foi possível enviar agora" to false
        }
    }

    DialogShell(onClose = onClose, largura = 380.dp) {
        Text(
            "Confirme seu e-mail",
            style = TextStyle(color = Obsidian.text1, fontSize = 19.sp, fontFamily = DmSerif),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (email.isNullOrBlank()) "Enviamos um código de seis dígitos para o seu e-mail."
            else "Enviamos um código de seis dígitos para $email.",
            style = Tipo.descricao,
        )
        Spacer(Modifier.height(16.dp))

        BasicTextField(
            value = codigo,
            onValueChange = { bruto ->
                codigo = bruto.filter { it.isDigit() }.take(TAMANHO_DO_CODIGO)
                if (codigo.length == TAMANHO_DO_CODIGO) conferir()
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

        recado?.let { (texto, bom) ->
            Spacer(Modifier.height(10.dp))
            Text(
                texto,
                style = TextStyle(
                    color = if (bom) Obsidian.success else Obsidian.danger,
                    fontSize = 11.sp,
                ),
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotaoDoDialogo("Reenviar", primario = false, ligado = !ocupado, onClick = ::reenviar)
            Spacer(Modifier.weight(1f))
            BotaoDoDialogo("Agora não", primario = false, ligado = !ocupado, onClick = onClose)
            BotaoDoDialogo(
                "Confirmar",
                primario = true,
                ligado = !ocupado && codigo.length == TAMANHO_DO_CODIGO,
                onClick = ::conferir,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "O código vale por quinze minutos. Sem confirmar, você continua entrando e lendo, " +
                "mas não cria constelação nem aceita convite.",
            style = Tipo.nota,
        )
    }
}
