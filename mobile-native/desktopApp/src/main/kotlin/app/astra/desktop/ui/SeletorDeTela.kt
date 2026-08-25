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

    val figura: ImageBitmap? = remember(monitor.miniatura) {
        monitor.miniatura?.takeIf { it.isNotBlank() }?.let { texto ->
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

private fun nomeDaTela(m: MonitorDaTela): String =
    if (m.principal) "Tela principal" else "Tela ${m.indice + 1}"
