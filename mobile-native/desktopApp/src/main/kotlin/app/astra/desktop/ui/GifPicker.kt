package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.GifApi
import app.astra.mobile.core.network.dto.GifResultDto
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext

// GIF picker (F5): botao no composer abre painel com busca (Tenor via backend,
// mesma GifApi do mobile — agora no :shared). Escolher envia na hora, como o
// Discord: o GIF vai como anexo de URL direta, sem upload.

private object AbovePanel : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = (anchorBounds.right - popupContentSize.width).coerceAtLeast(0),
        y = (anchorBounds.top - popupContentSize.height - 8).coerceAtLeast(0),
    )
}

@Composable
fun GifButton(onPick: (GifResultDto) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box {
        Text(
            "GIF",
            style = TextStyle(
                color = if (open || hovered) Obsidian.accent else Obsidian.text3,
                fontSize = 11.sp,
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .border(
                    1.dp,
                    if (open || hovered) Obsidian.accentDim else Obsidian.borderDim,
                    RoundedCornerShape(6.dp),
                )
                .hoverable(interaction)
                .clickable { open = !open }
                .padding(horizontal = 8.dp, vertical = 5.dp),
        )
        if (open) {
            Popup(
                popupPositionProvider = AbovePanel,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                GifPanel(onPick = { g ->
                    open = false
                    onPick(g)
                })
            }
        }
    }
}

@Composable
private fun GifPanel(onPick: (GifResultDto) -> Unit) {
    val koin = GlobalContext.get()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GifResultDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Busca com debounce; vazio = destaques.
    LaunchedEffect(query) {
        loading = true
        if (query.isNotBlank()) delay(400)
        results = runCatching {
            val api = koin.get<GifApi>()
            if (query.isBlank()) api.featured(limit = 24).data?.results
            else api.search(query.trim(), limit = 24).data?.results
        }.getOrNull().orEmpty()
        loading = false
    }

    Column(
        Modifier
            .width(340.dp)
            .height(400.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(Obsidian.base)
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                ) {
                    if (query.isEmpty()) {
                        Text("buscar gif…", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.height(8.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TypingDots(Obsidian.text3, dotSize = 5.dp)
            }
            results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("nada encontrado", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.id }) { g ->
                    AstraImage(
                        url = g.preview,
                        contentDescription = g.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Obsidian.base)
                            .clickable { onPick(g) },
                    )
                }
            }
        }
    }
}
