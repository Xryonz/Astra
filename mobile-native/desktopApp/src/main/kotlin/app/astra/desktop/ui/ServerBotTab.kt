package app.astra.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.BotApi
import app.astra.mobile.core.network.dto.BotComandoDto
import app.astra.mobile.core.network.dto.UpdateServerRequest
import org.koin.core.context.GlobalContext

// O QUE A BOT PODE FAZER NESTA CONSTELAÇÃO.
//
// A aparência dela não está aqui de propósito: a Sparkle e a Sparxie são uma conta
// só, compartilhada por todas as constelações, então trocar o rosto delas seria
// trocar pra todo mundo. O que é legitimamente desta constelação é o que ela pode
// FAZER aqui — e é só isso que esta aba oferece.
//
// A lista guardada no servidor é a dos DESLIGADOS, não a dos ligados: assim um
// comando novo nasce ligado em todas as constelações sem precisar de migração.
@Composable
fun ServerBotTab(
    desligadosAgora: String?,
    aoSalvar: (List<String>) -> Unit,
) {
    val api = remember { GlobalContext.get().get<BotApi>() }
    var catalogo by remember { mutableStateOf<List<BotComandoDto>>(emptyList()) }
    var erro by remember { mutableStateOf<String?>(null) }
    // Estado local pra o interruptor responder na hora. A gravação vai junto, mas
    // esperar a volta do servidor pra mexer a chavinha faria cada toque parecer
    // travado — e são vários seguidos quando alguém está desligando a zoeira.
    var desligados by remember(desligadosAgora) {
        mutableStateOf(desligadosAgora.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }

    LaunchedEffect(Unit) {
        runCatching { api.catalogo().data?.comandos.orEmpty() }
            .onSuccess { catalogo = it }
            .onFailure { erro = "não foi possível carregar a lista de comandos." }
    }

    Text(
        "o que a bot pode fazer aqui",
        style = TextStyle(color = Obsidian.text1, fontSize = 15.sp),
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "desligar um comando vale só nesta constelação. quem chamar recebe um aviso curto " +
            "em vez de silêncio — comando que não responde parece bot quebrada.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 620.dp),
    )
    Spacer(Modifier.height(16.dp))

    if (erro != null) {
        Text(erro!!, style = TextStyle(color = Obsidian.danger, fontSize = 12.sp))
        return
    }

    // Agrupado pela mesma categoria que a lista de ajuda usa. Vinte interruptores
    // seguidos viram parede; com o título de grupo o olho acha "Diversão" sem ler
    // os vinte nomes — mesma razão do agrupamento dos temas em Aparência.
    Column(Modifier.fillMaxWidth()) {
        catalogo.groupBy { it.categoria }.forEach { (categoria, itens) ->
            Text(
                categoria.uppercase(),
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
                modifier = Modifier.widthIn(max = 620.dp),
            )
            Spacer(Modifier.height(6.dp))
            itens.forEach { c ->
                ToggleRow(
                    title = c.rotulo,
                    sub = c.descricao,
                    on = c.chave !in desligados,
                ) { ligado ->
                    desligados = if (ligado) desligados - c.chave else desligados + c.chave
                    aoSalvar(desligados.toList())
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

// Atalho pra montar o pedido de gravação sem espalhar o formato pela tela.
fun pedidoDeComandos(desligados: List<String>) =
    UpdateServerRequest(botDisabledCommands = desligados)
