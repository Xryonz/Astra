package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
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
            loading && results.isEmpty() -> Center("procurando constelacoes…")
            error != null && results.isEmpty() -> Center(error!!)
            results.isEmpty() -> DiscoverEmptyMap(query)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(results, key = { _, s -> s.id }) { i, s ->
                    CascadeIn(i, results.size) {
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
private fun TreasureMapCanvas(width: Dp, height: Dp) {
    val reduce = LocalReduceMotion.current
    val accent = Obsidian.accent

    val draw = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) { if (!reduce) draw.animateTo(1f, tween(1700, easing = FastOutSlowInEasing)) }

    val inf = rememberInfiniteTransition(label = "map")
    val clock by inf.animateFloat(
        0f, (2.0 * Math.PI).toFloat(),
        infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "clock",
    )
    val t = if (reduce) 0f else clock
    val pulse = if (reduce) 1f else 0.82f + 0.18f * sin(t * 1.6f)

    val route = remember {
        listOf(
            Offset(0.09f, 0.74f), Offset(0.27f, 0.42f), Offset(0.44f, 0.63f),
            Offset(0.61f, 0.30f), Offset(0.79f, 0.52f), Offset(0.92f, 0.28f),
        )
    }
    val stars = remember {
        val r = java.util.Random(7)
        List(16) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f) }
    }

    Box(Modifier.size(width = width, height = height)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            stars.forEach { (sx, sy, ph) ->
                val a = 0.10f + 0.22f * (0.5f + 0.5f * sin(t + ph))
                drawCircle(accent.copy(alpha = a), radius = 1.1f, center = Offset(sx * w, sy * h))
            }
            val pts = route.map { Offset(it.x * w, it.y * h) }
            val lens = pts.zipWithNext().map { (a, b) -> (b - a).getDistance() }
            val total = lens.sum().coerceAtLeast(1f)
            val reached = draw.value * total
            val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 6f), 0f)
            var remaining = reached
            for (i in lens.indices) {
                if (remaining <= 0f) break
                val a = pts[i]; val b = pts[i + 1]
                val frac = (remaining / lens[i]).coerceAtMost(1f)
                val end = Offset(a.x + (b.x - a.x) * frac, a.y + (b.y - a.y) * frac)
                drawLine(accent.copy(alpha = 0.5f), a, end, strokeWidth = 1.6f, cap = StrokeCap.Round, pathEffect = dash)
                remaining -= lens[i]
            }
            val cum = FloatArray(pts.size)
            for (i in 1 until pts.size) cum[i] = cum[i - 1] + lens[i - 1]
            pts.forEachIndexed { i, p ->
                if (i < pts.lastIndex && cum[i] <= reached) {
                    drawCircle(accent.copy(alpha = 0.85f), radius = 2.4f, center = p)
                    drawCircle(accent.copy(alpha = 0.20f), radius = 4.6f, center = p)
                }
            }
            if (draw.value > 0.98f) {
                val tp = pts.last()
                drawCircle(accent.copy(alpha = 0.10f), radius = 16f * pulse, center = tp)
                drawCircle(accent.copy(alpha = 0.18f), radius = 8f * pulse, center = tp)
                val r = minOf(w, h) * 0.07f * pulse
                val inner = r * 0.4f
                val star = Path()
                for (k in 0 until 8) {
                    val rad = if (k % 2 == 0) r else inner
                    val a = (-90.0 + k * 45.0) * Math.PI / 180.0
                    val px = tp.x + (rad * cos(a)).toFloat()
                    val py = tp.y + (rad * sin(a)).toFloat()
                    if (k == 0) star.moveTo(px, py) else star.lineTo(px, py)
                }
                star.close()
                drawPath(star, accent)
            }
        }
    }
}

@Composable
private fun DiscoverEmptyMap(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TreasureMapCanvas(width = 280.dp, height = 150.dp)
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
            TreasureMapCanvas(width = 168.dp, height = 104.dp)
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
