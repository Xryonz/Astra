package app.astra.desktop.ui

// A ESCOLHA DE QUAL TELA COMPARTILHAR.
//
// Até aqui o botão mandava sempre o monitor principal e não perguntava. Numa máquina de
// uma tela isso acerta por acaso; em duas, é metade de chance de mostrar a errada — e
// quem erra descobre pelo "não é essa" de outra pessoa, com a tela errada já no ar.
//
// A MINIATURA É A INFORMAÇÃO, e é por isso que este seletor é uma janela e não um menu
// de texto. O Windows chama os monitores de `\\.\DISPLAY1` e `\\.\DISPLAY2`; dois
// monitores do mesmo modelo têm a mesma resolução e nomes que só diferem no dígito.
// Nenhum rótulo que eu escrevesse separaria os dois. O que está NA tela separa na hora.
//
// A JANELA ABRE ANTES DA RESPOSTA, com o aviso de que está procurando. Amostrar custa
// uma duplicação de tela por monitor — uns 100ms cada —, e segurar a abertura por isso
// faria o clique parecer que não pegou.

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.voice.MonitorDaTela
import org.jetbrains.skia.Image as SkiaImage
import java.util.Base64

// Largura de cada cartão. Duas por linha cabem em 480dp, que é o que a janela ocupa
// numa máquina de dois monitores — o caso que este seletor existe para resolver.
private val LarguraDoCartao = 224.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeletorDeTela(
    monitores: List<MonitorDaTela>?,
    aoEscolher: (Int) -> Unit,
) {
    Column(
        Modifier
            .popupReveal(originX = 0.5f, originY = 1f)
            .width(if ((monitores?.size ?: 1) > 1) 480.dp else 248.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(
            "Qual tela compartilhar",
            style = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
        )
        Spacer(Modifier.height(10.dp))

        when {
            // NULO E VAZIO SÃO COISAS DIFERENTES, e dizer a mesma frase nos dois casos
            // faria a espera parecer defeito. Nulo é "ainda não respondeu"; vazio é
            // "respondeu que não há tela", que é problema de verdade.
            monitores == null -> Text(
                "procurando as telas desta máquina…",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            )
            monitores.isEmpty() -> Text(
                "nenhuma tela disponível para compartilhar",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            )
            else -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                monitores.forEach { m ->
                    CartaoDeTela(m) { aoEscolher(m.indice) }
                }
            }
        }
    }
}

@Composable
private fun CartaoDeTela(monitor: MonitorDaTela, aoClicar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val sobre by interacao.collectIsHoveredAsState()

    // DECODIFICA UMA VEZ POR IMAGEM, e a chave é a própria imagem. Sem o `remember`, cada
    // recomposição criaria um objeto Skia nativo novo — e objeto nativo não é coletado
    // pelo Java. É a mesma família de vazamento que já custou a RAM da transmissão.
    val figura: ImageBitmap? = remember(monitor.miniatura) {
        monitor.miniatura?.takeIf { it.isNotBlank() }?.let { texto ->
            runCatching {
                SkiaImage.makeFromEncoded(Base64.getDecoder().decode(texto)).toComposeImageBitmap()
            }.getOrNull()
        }
    }

    // O CARTÃO DENTRO DO CARTÃO: o painel é `raised`, então cada tela é `overlay`, um
    // degrau mais claro. Hierarquia por elevação, não por linha divisória.
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
                    contentDescription = null, // o nome logo abaixo já diz qual tela é
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // SEM AMOSTRA TEM MOTIVO, e o motivo quase sempre é este: a duplicação
                // de tela é exclusiva por processo, então a tela que já está no ar não
                // pode ser amostrada de novo. Dizer isso é melhor que um retângulo mudo.
                Text(
                    "já está sendo transmitida",
                    style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                nomeDaTela(monitor),
                style = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${monitor.largura} × ${monitor.altura}",
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
            )
        }
    }
}

// O NOME DO WINDOWS NÃO VAI PARA A TELA. `\\.\DISPLAY1` é identificador de sistema, não
// texto de produto — e não diz nada a ninguém. "Tela principal" e "Tela 2" dizem tanto
// quanto ele e se leem.
private fun nomeDaTela(m: MonitorDaTela): String =
    if (m.principal) "Tela principal" else "Tela ${m.indice + 1}"
