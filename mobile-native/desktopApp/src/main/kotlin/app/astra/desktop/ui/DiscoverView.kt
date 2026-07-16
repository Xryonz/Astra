package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.DiscoverApi
import app.astra.mobile.core.network.dto.DiscoverServerDto
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Users
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import retrofit2.HttpException

// Descobrir constelacoes publicas (paridade com web/mobile). Palco central: busca
// no topo (?q= com debounce) + grid de cards com banner. Entrar chama
// /discover/:id/join e o onJoined recarrega os servidores + cai na constelacao.
// API/DTOs vem do :shared (DiscoverApi movida do :app).
@Composable
fun DiscoverView(onJoined: (String) -> Unit, modifier: Modifier = Modifier) {
    val api = remember { GlobalContext.get().get<DiscoverApi>() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<DiscoverServerDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var joining by remember { mutableStateOf<String?>(null) }

    // Busca com debounce: ~400ms apos a ultima tecla (query vazia carrega na hora).
    LaunchedEffect(query) {
        loading = true
        error = null
        if (query.isNotBlank()) delay(400)
        val res = runCatching { api.discover(query.trim().ifBlank { null }).data.orEmpty() }
        results = res.getOrDefault(emptyList())
        error = if (res.isFailure) "Nao deu pra carregar a Descoberta" else null
        loading = false
    }

    fun join(id: String) {
        if (joining != null) return
        joining = id
        scope.launch {
            val r = runCatching { api.join(id) }
            joining = null
            // 201 (entrou) OU 409 (ja era membro) -> cai na constelacao do mesmo jeito.
            when {
                r.isSuccess -> onJoined(id)
                (r.exceptionOrNull() as? HttpException)?.code() == 409 -> onJoined(id)
                else -> error = "Nao deu pra entrar nessa constelacao"
            }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LIcon(Lucide.Compass, tint = Obsidian.accent, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "Descobrir constelacoes",
                style = TextStyle(color = Obsidian.text1, fontSize = 20.sp, fontFamily = DmSerif),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "entre em comunidades publicas do Astra",
            style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
        )
        Spacer(Modifier.height(14.dp))

        // Busca (?q= no backend). Mesmo visual da busca de sussurros.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Obsidian.base)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LIcon(Lucide.Search, tint = Obsidian.text3, size = 15.dp)
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("buscar constelacao", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                    cursorBrush = SolidColor(Obsidian.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            loading && results.isEmpty() -> Center("procurando constelacoes…")
            error != null && results.isEmpty() -> Center(error!!)
            results.isEmpty() -> Center(if (query.isBlank()) "nenhuma constelacao publica ainda" else "nada encontrado")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.id }) { s ->
                    DiscoverCard(s, joining = joining == s.id, onJoin = { join(s.id) })
                }
            }
        }
    }
}

@Composable
private fun DiscoverCard(s: DiscoverServerDto, joining: Boolean, onJoin: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
    ) {
        // Faixa de banner (imagem ou fundo do tema).
        Box(Modifier.fillMaxWidth().height(70.dp).background(Obsidian.overlay)) {
            if (!s.bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = s.bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesktopAvatar(s.iconUrl, s.name, 34)
                Spacer(Modifier.width(10.dp))
                Text(
                    s.name,
                    style = TextStyle(color = Obsidian.text1, fontSize = 14.sp, fontFamily = DmSerif),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            // Altura fixa (2 linhas) pra os cards alinharem no grid mesmo sem descricao.
            Text(
                s.description?.ifBlank { null } ?: "sem descricao",
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(32.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LIcon(Lucide.Users, tint = Obsidian.text3, size = 13.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    "${s.members}",
                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                )
                Spacer(Modifier.weight(1f))
                val joinSrc = remember { MutableInteractionSource() }
                Text(
                    if (joining) "entrando…" else "entrar",
                    style = TextStyle(color = Obsidian.accent, fontSize = 12.sp),
                    modifier = Modifier
                        .clickScale(joinSrc)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Obsidian.accentDim, RoundedCornerShape(8.dp))
                        .clickable(interactionSource = joinSrc, indication = null, enabled = !joining, onClick = onJoin)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
    }
}
