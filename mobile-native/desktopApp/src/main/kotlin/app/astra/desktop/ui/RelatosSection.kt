package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.RelatosApi
import app.astra.mobile.core.network.dto.RelatoDto
import org.koin.core.context.GlobalContext

private const val QUANTOS_RELATOS = 50

@Composable
fun RelatosSection() {
    val api = remember { GlobalContext.get().get<RelatosApi>() }
    var relatos by remember { mutableStateOf<List<RelatoDto>>(emptyList()) }
    var erro by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching { api.relatos(QUANTOS_RELATOS).data.orEmpty() }
            .onSuccess { relatos = it; erro = null }
            .onFailure { erro = "não foi possível carregar os relatos." }
        carregando = false
    }

    FieldLabel("relatos")
    Text(
        "falhas que os apps enviaram sozinhos: travamentos e atualizações que não se completaram. " +
            "nomes de usuário do Windows chegam mascarados. clique num relato para ver o diário.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 520.dp),
    )
    Spacer(Modifier.height(16.dp))

    when {
        carregando -> Text("carregando…", style = Tipo.descricao)
        erro != null -> Text(erro!!, style = Tipo.erro)
        relatos.isEmpty() -> Text("nenhuma falha relatada nos últimos 60 dias.", style = Tipo.descricao)
        else -> relatos.forEach { r ->
            CartaoDoRelato(r)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CartaoDoRelato(r: RelatoDto) {
    var aberto by remember(r.id) { mutableStateOf(false) }
    val fonte = remember { MutableInteractionSource() }
    val forma = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .clickScale(fonte, pressedScale = 0.99f)
            .clip(forma)
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, forma)
            .clickable(interactionSource = fonte, indication = null) { aberto = !aberto }
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(
            listOfNotNull(
                if (r.tipo == "atualizacao") "atualização" else "travamento",
                r.versao.takeIf { it.isNotBlank() },
                r.createdAt?.take(16)?.replace('T', ' '),
            ).joinToString(" · "),
            style = Tipo.apoio,
        )
        Spacer(Modifier.height(4.dp))
        Text(r.mensagem?.takeIf { it.isNotBlank() } ?: r.tipo, style = Tipo.corpo)
        if (aberto) {
            Spacer(Modifier.height(8.dp))
            Text(
                r.rastro,
                style = TextStyle(
                    color = Obsidian.text2,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            )
        }
    }
}
