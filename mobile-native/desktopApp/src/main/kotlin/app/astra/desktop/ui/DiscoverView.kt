package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.DiscoverApi
import app.astra.mobile.core.network.dto.DiscoverServerDto
import coil3.compose.AsyncImage
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.LogIn
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Users
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import retrofit2.HttpException
import app.astra.desktop.ui.theme.Tipo

@Composable
fun DiscoverView(onJoined: (String) -> Unit, joinedIds: Set<String> = emptySet(), modifier: Modifier = Modifier) {
    val api = remember { GlobalContext.get().get<DiscoverApi>() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<DiscoverServerDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var joining by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        loading = true
        error = null
        if (query.isNotBlank()) delay(400)
        val res = runCatching { api.discover(query.trim().ifBlank { null }).data.orEmpty() }
        results = res.getOrDefault(emptyList())
        error = if (res.isFailure) "Não foi possível carregar a Descoberta" else null
        loading = false
    }

    fun join(id: String) {
        if (joining != null) return
        joining = id
        scope.launch {
            val r = runCatching { api.join(id) }
            joining = null
            when {
                r.isSuccess -> onJoined(id)
                (r.exceptionOrNull() as? HttpException)?.code() == 409 -> onJoined(id)
                else -> error = "Não foi possível entrar nessa constelação"
            }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LIcon(Lucide.Compass, tint = Obsidian.accent, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "Descobrir constelações",
                style = TextStyle(color = Obsidian.text1, fontSize = 20.sp, fontFamily = DmSerif),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "entre em comunidades públicas do Astra",
            style = Tipo.descricao,
        )
        Spacer(Modifier.height(14.dp))

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
                    Text("buscar constelação", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = Tipo.corpo,
                    cursorBrush = SolidColor(Obsidian.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            loading && results.isEmpty() -> Center("procurando constelações…")
            error != null && results.isEmpty() -> Center(error!!)
            results.isEmpty() -> DiscoverEmptyMap(query)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.id }) { s ->
                    DiscoverCard(
                        s,
                        joining = joining == s.id,
                        isMember = s.id in joinedIds,
                        onJoin = { join(s.id) },
                        onAbrir = { onJoined(s.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverCard(
    s: DiscoverServerDto,
    joining: Boolean,
    isMember: Boolean,
    onJoin: () -> Unit,
    onAbrir: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(ServerBannerAspect).background(Obsidian.overlay)) {
            var bannerFalhou by remember(s.bannerUrl, rodadaDasImagens) { mutableStateOf(false) }
            if (!s.bannerUrl.isNullOrBlank() && !bannerFalhou) {
                AsyncImage(
                    model = s.bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { bannerFalhou = true },
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
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LIcon(Lucide.Users, tint = Obsidian.text3, size = 12.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${s.members}",
                        style = TextStyle(color = Obsidian.text3, fontSize = 12.sp, fontFamily = DmMono),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                s.description?.ifBlank { null } ?: "sem descrição",
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(32.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                val joinSrc = remember { MutableInteractionSource() }
                if (isMember) {
                    Row(
                        Modifier
                            .clickScale(joinSrc)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                            .clickable(interactionSource = joinSrc, indication = null, onClick = onAbrir)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LIcon(Lucide.ArrowRight, tint = Obsidian.text2, size = 13.dp)
                        Text("abrir", style = Tipo.rotulo)
                    }
                } else {
                    Row(
                        Modifier
                            .clickScale(joinSrc)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Obsidian.accentDim, RoundedCornerShape(8.dp))
                            .clickable(interactionSource = joinSrc, indication = null, enabled = !joining, onClick = onJoin)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LIcon(Lucide.LogIn, tint = Obsidian.accent, size = 13.dp)
                        Text(
                            if (joining) "entrando…" else "entrar",
                            style = TextStyle(color = Obsidian.accent, fontSize = 12.sp),
                        )
                    }
                }
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

@Composable
private fun DesenhoDaConstelacao(modifier: Modifier) {
    AnimacaoDeArtista(
        arquivo = "constelacao",
        vez = 1,
        luz = Obsidian.accent,
        cores = ::claroViraFundo,
        quadros = 0..QUADRO_DO_DESENHO_COMPLETO,
        ficaNoFim = true,
        modifier = modifier,
    )
}

private const val QUADRO_DO_DESENHO_COMPLETO = 39

@Composable
private fun DiscoverEmptyMap(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DesenhoDaConstelacao(Modifier.size(width = 280.dp, height = 150.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                if (query.isBlank()) "o mapa ainda está vazio" else "nada no mapa",
                style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (query.isBlank()) "seja o primeiro a fincar bandeira numa constelação"
                else "nenhuma constelação encontrada — tente outro nome",
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp, lineHeight = 17.sp),
            )
        }
    }
}

@Composable
internal fun DiscoverSidebarMap() {
    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DesenhoDaConstelacao(Modifier.size(width = 168.dp, height = 104.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                "a rota leva ao palco ao lado",
                style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmSerif, textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "busque e entre em constelações públicas",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.Center),
            )
        }
    }
}
