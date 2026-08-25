package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.StickerApi
import app.astra.mobile.core.network.dto.ServerStickerDto
import org.koin.core.context.GlobalContext

@Composable
internal fun StickerPanel(serverId: String, onPick: (ServerStickerDto) -> Unit) {
    val api = remember { GlobalContext.get().get<StickerApi>() }
    var itens by remember(serverId) { mutableStateOf<List<ServerStickerDto>>(emptyList()) }
    var carregando by remember(serverId) { mutableStateOf(true) }

    LaunchedEffect(serverId) {
        carregando = true
        itens = runCatching { api.listar(serverId).stickers }.getOrNull().orEmpty()
        carregando = false
    }

    Column(
        Modifier
            .width(300.dp)
            .height(340.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        when {
            carregando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TypingDots(Obsidian.text3, dotSize = 5.dp)
            }
            itens.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "nenhuma figurinha aqui ainda.\nquem cuida da constelação\npode subir nas configurações.",
                    style = TextStyle(
                        color = Obsidian.text3, fontSize = 11.sp,
                        fontFamily = DmMono, lineHeight = 17.sp, textAlign = TextAlign.Center,
                    ),
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(itens, key = { it.id }) { fig -> CelulaDeFigurinha(fig) { onPick(fig) } }
            }
        }
    }
}

@Composable
private fun CelulaDeFigurinha(fig: ServerStickerDto, onClick: () -> Unit) {
    val src = remember(fig.id) { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(if (hov) Obsidian.hover else Obsidian.base)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AstraImage(
            url = fig.url,
            contentDescription = fig.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
