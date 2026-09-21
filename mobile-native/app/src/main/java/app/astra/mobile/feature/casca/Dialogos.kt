package app.astra.mobile.feature.casca

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.mobile.ui.AstraCopy
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackdrop
import app.astra.mobile.ui.components.EditorialField
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors

@Composable
fun DialogoDeForjar(
    aberto: Boolean,
    grupo: Boolean,
    criando: Boolean,
    erro: String?,
    aoConfirmar: (String) -> Unit,
    aoFechar: () -> Unit,
) {
    var nome by remember(aberto) { mutableStateOf("") }
    AstraDialog(
        open = aberto,
        onDismiss = { if (!criando) aoFechar() },
        title = if (grupo) AstraCopy.Action.createGroup else AstraCopy.Action.createServer,
        confirmText = if (criando) "Forjando..." else "Forjar",
        onConfirm = { aoConfirmar(nome) },
        confirmEnabled = nome.isNotBlank() && !criando,
    ) {
        Text(
            text = if (grupo) AstraCopy.Desc.aglomerado else AstraCopy.Desc.constelacao,
            style = MaterialTheme.typography.bodySmall,
            color = astraColors.text3,
        )
        EditorialField(
            value = nome,
            onValue = { nome = it },
            label = if (grupo) "nome do aglomerado" else "nome da constelação",
            placeholder = if (grupo) "como vão chamar o grupo?" else "como vão chamar a constelação?",
            enabled = !criando,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
            onIme = { if (nome.isNotBlank() && !criando) aoConfirmar(nome) },
        )
        if (erro != null) {
            Text(text = erro, style = MaterialTheme.typography.bodySmall, color = astraColors.danger)
        }
    }
}

@Composable
fun DialogoDeNovoSussurro(
    aberto: Boolean,
    abrindo: Boolean,
    erro: String?,
    aoConfirmar: (String) -> Unit,
    aoFechar: () -> Unit,
) {
    var usuario by remember(aberto) { mutableStateOf("") }
    AstraDialog(
        open = aberto,
        onDismiss = { if (!abrindo) aoFechar() },
        title = AstraCopy.Action.startDM,
        confirmText = if (abrindo) "Abrindo..." else "Abrir",
        onConfirm = { aoConfirmar(usuario) },
        confirmEnabled = usuario.isNotBlank() && !abrindo,
    ) {
        Text(
            text = AstraCopy.Desc.sussurro,
            style = MaterialTheme.typography.bodySmall,
            color = astraColors.text3,
        )
        EditorialField(
            value = usuario,
            onValue = { usuario = it },
            label = "estrela",
            placeholder = "@username",
            enabled = !abrindo,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
            onIme = { if (usuario.isNotBlank() && !abrindo) aoConfirmar(usuario) },
        )
        if (erro != null) {
            Text(text = erro, style = MaterialTheme.typography.bodySmall, color = astraColors.danger)
        }
    }
}

@Composable
fun PortaoDeSenha(
    salvando: Boolean,
    erro: String?,
    aoEnviar: (String, String) -> Unit,
) {
    BackHandler {}
    var senha by remember { mutableStateOf("") }
    var confirmacao by remember { mutableStateOf("") }
    CosmicBackdrop {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 26.dp),
        ) {
            Text(
                text = "Crie uma senha",
                fontFamily = DmSerif,
                fontSize = 28.sp,
                color = astraColors.text1,
            )
            Spacer(Modifier.height(6.dp))
            MarginaliaLabel(
                "sua conta entrou com o Google e ainda não tem senha — crie uma para garantir o acesso",
            )
            Spacer(Modifier.height(20.dp))
            EditorialField(
                value = senha, onValue = { senha = it },
                label = "nova senha", placeholder = "8+ caracteres, 1 maiúscula, 1 número",
                enabled = !salvando, keyboardType = KeyboardType.Password, imeAction = ImeAction.Next,
                password = true,
            )
            Spacer(Modifier.height(14.dp))
            EditorialField(
                value = confirmacao, onValue = { confirmacao = it },
                label = "confirmar senha", placeholder = "••••••••",
                enabled = !salvando, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                onIme = { aoEnviar(senha, confirmacao) }, password = true,
            )
            if (erro != null) {
                Spacer(Modifier.height(12.dp))
                AuthErrorBox(erro)
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(astraColors.accent)
                    .clickable(enabled = !salvando) { aoEnviar(senha, confirmacao) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (salvando) "Salvando..." else "Criar senha",
                    color = astraColors.textInv,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}
