package app.astra.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.voice.JanelaDaTela
import app.astra.desktop.voice.MonitorDaTela
import org.jetbrains.skia.Image as SkiaImage
import java.util.Base64
import app.astra.desktop.ui.theme.Tipo

private val LarguraDoCartao = 224.dp

sealed interface FonteEscolhida {
    data class Monitor(val indice: Int) : FonteEscolhida
    data class Janela(val id: ULong, val largura: Int, val altura: Int) : FonteEscolhida
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeletorDeTela(
    monitores: List<MonitorDaTela>?,
    janelas: List<JanelaDaTela>?,
    aoPedirJanelas: () -> Unit,
    aoEscolher: (FonteEscolhida) -> Unit,
) {
    var emJanelas by remember { mutableStateOf(false) }

    Column(
        Modifier
            .popupReveal(originX = 0.5f, originY = 1f)
            .width(480.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(
            "O que compartilhar",
            style = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AbaDaFonte("telas inteiras", !emJanelas) { emJanelas = false }
            AbaDaFonte("uma janela", emJanelas) {
                emJanelas = true
                if (janelas == null) aoPedirJanelas()
            }
        }
        Spacer(Modifier.height(12.dp))

        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (emJanelas) {
                ListaDeFontes(
                    itens = janelas,
                    procurando = "procurando as janelas abertas…",
                    vazio = "nenhuma janela aberta para compartilhar",
                ) { j ->
                    CartaoDaFonte(j.miniatura, j.nome, "${j.largura} × ${j.altura}") {
                        aoEscolher(FonteEscolhida.Janela(j.id, j.largura, j.altura))
                    }
                }
            } else {
                ListaDeFontes(
                    itens = monitores,
                    procurando = "procurando as telas desta máquina…",
                    vazio = "nenhuma tela disponível para compartilhar",
                ) { m ->
                    CartaoDaFonte(m.miniatura, nomeDaTela(m), "${m.largura} × ${m.altura}") {
                        aoEscolher(FonteEscolhida.Monitor(m.indice))
                    }
                }
            }
        }

        if (emJanelas) {
            Spacer(Modifier.height(10.dp))
            Text(
                "só a janela escolhida é enviada — o que estiver por cima dela não aparece",
                style = Tipo.nota,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ListaDeFontes(
    itens: List<T>?,
    procurando: String,
    vazio: String,
    cartao: @Composable (T) -> Unit,
) {
    when {
        itens == null -> Text(procurando, style = Tipo.apoio)
        itens.isEmpty() -> Text(vazio, style = Tipo.apoio)
        else -> FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itens.forEach { cartao(it) }
        }
    }
}

@Composable
private fun AbaDaFonte(rotulo: String, ativa: Boolean, aoClicar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val sobre by interacao.collectIsHoveredAsState()
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    ativa -> Obsidian.overlay
                    sobre -> Obsidian.hover
                    else -> Obsidian.raised
                },
            )
            .hoverable(interacao)
            .clickScale(interacao, formaDoFoco = RoundedCornerShape(6.dp))
            .clickable(interactionSource = interacao, indication = null, onClick = aoClicar)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            rotulo,
            style = TextStyle(color = if (ativa) Obsidian.text1 else Obsidian.text3, fontSize = 11.sp),
        )
    }
}

@Composable
private fun CartaoDaFonte(
    miniatura: String?,
    titulo: String,
    tamanho: String,
    aoClicar: () -> Unit,
) {
    val interacao = remember { MutableInteractionSource() }
    val sobre by interacao.collectIsHoveredAsState()

    val figura: ImageBitmap? = remember(miniatura) {
        miniatura?.takeIf { it.isNotBlank() }?.let { texto ->
            runCatching {
                SkiaImage.makeFromEncoded(Base64.getDecoder().decode(texto)).toComposeImageBitmap()
            }.getOrNull()
        }
    }

    Column(
        Modifier
            .width(LarguraDoCartao)
            .clip(RoundedCornerShape(8.dp))
            .background(if (sobre) Obsidian.hover else Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .hoverable(interacao)
            .clickScale(interacao)
            .clickable(interactionSource = interacao, indication = null, onClick = aoClicar)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Obsidian.void),
            contentAlignment = Alignment.Center,
        ) {
            if (figura != null) {
                Image(
                    figura,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    "sem prévia",
                    style = Tipo.nota,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            titulo,
            style = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            tamanho,
            style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
        )
    }
}

private fun nomeDaTela(m: MonitorDaTela): String =
    if (m.principal) "Tela principal" else "Tela ${m.indice + 1}"
