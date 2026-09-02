package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.prefs.AvisosDaConta
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.AvisosDaContaDto
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import app.astra.desktop.ui.theme.Tipo

@Composable
internal fun BlocoDeAjustes(
    titulo: String,
    explicacao: String,
    conteudo: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised.copy(alpha = 0.22f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            titulo.uppercase(),
            style = TextStyle(
                color = Obsidian.text2, fontSize = 10.sp,
                letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            explicacao,
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 15.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(12.dp))
        conteudo()
    }
}

@Composable
internal fun AvisosDaContaBloco() {
    val avisos = remember { GlobalContext.get().get<AvisosDaConta>() }
    val estado by avisos.estado.collectAsState()
    val escopo = rememberCoroutineScope()
    var erro by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { avisos.carregar() }

    fun aplicar(novo: AvisosDaContaDto) {
        escopo.launch {
            erro = null
            avisos.salvar(novo).onFailure { erro = "não foi possível salvar — a mudança voltou atrás" }
        }
    }

    BlocoDeAjustes(
        "na sua conta, em todo lugar",
        "decidem se o aviso chega a existir. Desligar aqui apaga o aviso do sino, " +
            "do celular e do navegador junto — não é só nesta máquina.",
    ) {
        ToggleRow(
            "Menções", "quando alguém escreve o seu @",
            estado.mentions,
        ) { aplicar(estado.copy(mentions = it)) }
        ToggleRow(
            "Sussurros", "mensagem privada",
            estado.dms,
        ) { aplicar(estado.copy(dms = it)) }
        ToggleRow(
            "Reações", "quando reagem a algo que você escreveu",
            estado.reactions,
        ) { aplicar(estado.copy(reactions = it)) }
        ToggleRow(
            "Respostas", "quando respondem a sua mensagem",
            estado.replies,
        ) { aplicar(estado.copy(replies = it)) }
        ToggleRow(
            "Avisos no celular e no navegador",
            "o push. Não muda nada neste app, que recebe por conexão direta",
            estado.desktop,
        ) { aplicar(estado.copy(desktop = it)) }

        erro?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = Tipo.erro)
        }
    }
}
