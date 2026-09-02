package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.WishApi
import app.astra.mobile.core.network.dto.WishDto
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.X
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.core.context.GlobalContext
import java.time.Instant
import app.astra.desktop.ui.theme.Tipo

@Composable
fun DesejosPanel(onClose: () -> Unit) {
    val api = remember { GlobalContext.get().get<WishApi>() }
    var itens by remember { mutableStateOf<List<WishDto>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(true) }
    var acabou by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf(false) }
    val lista = rememberLazyListState()

    suspend fun buscar(proximo: String?) {
        val r = runCatching { api.listar(limit = 20, cursor = proximo).data }.getOrNull()
        if (r == null) {
            erro = true
        } else {
            erro = false
            itens = if (proximo == null) r.items else itens + r.items
            cursor = r.nextCursor
            acabou = r.nextCursor == null
        }
        carregando = false
    }

    LaunchedEffect(Unit) { buscar(null) }

    LaunchedEffect(lista) {
        snapshotFlow { lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { ultimo ->
                if (!acabou && !carregando && itens.isNotEmpty() && ultimo >= itens.size - 3) {
                    carregando = true
                    buscar(cursor)
                }
            }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Obsidian.void.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .semCursorDeClique(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .popupReveal(originX = 0.5f, originY = 0.5f)
                .width(520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("estrela dos desejos", style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif))
                Spacer(Modifier.weight(1f))
                BotaoIcone(Lucide.X, "fechar", onClick = onClose)
            }
            Box(Modifier.padding(horizontal = 14.dp)) {
                Text(
                    "o que as pessoas gostariam que o Astra tivesse. para deixar o seu, " +
                        "peça à Sparxie: /sparxie desejo …",
                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
                )
            }
            Spacer(Modifier.height(10.dp))

            when {
                carregando && itens.isEmpty() -> Vazio("lendo o céu…")
                erro && itens.isEmpty() -> Vazio("não deu para ler o céu agora.")
                itens.isEmpty() -> Vazio("nenhum desejo ainda. o primeiro pode ser o seu.")
                else -> LazyColumn(
                    state = lista,
                    modifier = Modifier.heightIn(max = 460.dp).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(itens, key = { _, d -> d.id }) { i, d ->
                        CascadeIn(index = i, listKey = itens.size) { LinhaDeDesejo(d) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun LinhaDeDesejo(d: WishDto) {
    val quem = d.author?.displayName ?: d.author?.username ?: "alguém"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.hover.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DesktopAvatar(d.author?.avatarUrl, quem, 22)
            Spacer(Modifier.width(8.dp))
            Text(quem, style = Tipo.rotulo)
            Spacer(Modifier.weight(1f))
            LIcon(Lucide.Sparkles, tint = Obsidian.accent.copy(alpha = 0.55f), size = 12.dp)
            Spacer(Modifier.width(6.dp))
            Text(quando(d.createdAt), style = Tipo.apoio)
        }
        Spacer(Modifier.height(6.dp))
        Text(d.content, style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, lineHeight = 19.sp))
    }
}

@Composable
private fun Vazio(texto: String) {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = Tipo.descricao)
    }
}

private fun quando(iso: String?): String {
    val t = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val min = (System.currentTimeMillis() - t.toEpochMilli()) / 60_000
    return when {
        min < 1L -> "agora"
        min < 60L -> "há ${min}min"
        min < 60L * 24 -> "há ${min / 60}h"
        min < 60L * 24 * 30 -> "há ${min / (60 * 24)}d"
        else -> "há muito tempo"
    }
}
