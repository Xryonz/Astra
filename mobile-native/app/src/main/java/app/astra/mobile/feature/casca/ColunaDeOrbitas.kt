package app.astra.mobile.feature.casca

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.astra.mobile.R
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

private val LADO = 46.dp
private val LARGURA_DA_COLUNA = 68.dp
private val CANTO = RoundedCornerShape(8.dp)
private const val CHAVE_SUSSURROS = "sussurros"
private const val CHAVE_DIVISORIA = "divisoria"
private const val CHAVE_NOVA = "nova"
private const val ACENDER_MS = 180
private const val ESCALA_NA_MAO = 1.08f
private val ZONA_DE_ROLAGEM = 56.dp
private val PASSO_DA_ROLAGEM = 9.dp

fun naOrdemEscolhida(orbitas: List<Server>, ordem: List<String>): List<Server> {
    if (ordem.isEmpty() || orbitas.size < 2) return orbitas
    val porId = orbitas.associateBy { it.id }
    val escolhidas = ordem.mapNotNull { porId[it] }
    if (escolhidas.isEmpty()) return orbitas
    val jaPostas = escolhidas.mapTo(HashSet()) { it.id }
    return escolhidas + orbitas.filterNot { it.id in jaPostas }
}

private class ArrastoDaColuna {
    var id by mutableStateOf<String?>(null)
    var pontoY by mutableFloatStateOf(0f)
    var mexeu by mutableStateOf(false)
    val arrastando: Boolean get() = id != null

    fun soltar() {
        id = null
        mexeu = false
    }
}

private fun centroNaColuna(info: LazyListLayoutInfo, chave: Any): Float? =
    info.visibleItemsInfo.firstOrNull { it.key == chave }
        ?.let { it.offset + it.size / 2f - info.viewportStartOffset }

private fun orbitaSob(info: LazyListLayoutInfo, y: Float, ids: Set<String>): String? =
    info.visibleItemsInfo.firstOrNull {
        val topo = it.offset - info.viewportStartOffset
        it.key in ids && y >= topo && y < topo + it.size
    }?.key as? String

private fun velocidadeDaBorda(y: Float, altura: Float, zona: Float, teto: Float): Float {
    if (altura <= 0f || zona <= 0f) return 0f
    return when {
        y < zona -> -teto * ((zona - y) / zona).coerceIn(0f, 1f)
        y > altura - zona -> teto * ((y - (altura - zona)) / zona).coerceIn(0f, 1f)
        else -> 0f
    }
}

@Composable
fun ColunaDeOrbitas(
    orbitas: List<Server>,
    orbitaAberta: String?,
    emSussurros: Boolean,
    sussurroNaoLido: Boolean,
    naoLidas: Set<String>,
    silenciadas: Set<String>,
    aoAbrirSussurros: () -> Unit,
    aoAbrirOrbita: (String) -> Unit,
    aoSegurarOrbita: (String) -> Unit,
    aoReordenar: (List<String>) -> Unit,
    aoAdicionar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val haptico = LocalHapticFeedback.current
    val comVibracao = LocalAppPrefs.current.haptics
    val estado = rememberLazyListState()
    val arrasto = remember { ArrastoDaColuna() }
    var ordemNaMao by remember { mutableStateOf<List<String>>(emptyList()) }
    val naTela = if (arrasto.arrastando && ordemNaMao.isNotEmpty()) {
        val porId = orbitas.associateBy { it.id }
        ordemNaMao.mapNotNull { porId[it] }
    } else {
        orbitas
    }
    val orbitasAgora by rememberUpdatedState(naTela)
    val idsAgora by rememberUpdatedState(orbitas.map { it.id })

    val zona = with(LocalDensity.current) { ZONA_DE_ROLAGEM.toPx() }
    val teto = with(LocalDensity.current) { PASSO_DA_ROLAGEM.toPx() }
    val acompanhar = {
        val id = arrasto.id
        val lista = orbitasAgora.map { it.id }
        val onde = lista.indexOf(id)
        val alvo = orbitaSob(estado.layoutInfo, arrasto.pontoY, lista.toSet())
        if (id != null && onde >= 0 && alvo != null && alvo != id) {
            val nova = lista.toMutableList()
            nova.add(lista.indexOf(alvo), nova.removeAt(onde))
            ordemNaMao = nova
            arrasto.mexeu = true
        }
    }
    LaunchedEffect(arrasto.arrastando) {
        if (!arrasto.arrastando) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val altura = estado.layoutInfo.viewportSize.height.toFloat()
            val velocidade = velocidadeDaBorda(arrasto.pontoY, altura, zona, teto)
            if (velocidade != 0f) {
                estado.scrollBy(velocidade)
                acompanhar()
            }
        }
    }

    Box(
        modifier
            .width(LARGURA_DA_COLUNA)
            .fillMaxHeight()
            .background(astraColors.void),
    ) {
        LazyColumn(
            state = estado,
            modifier = Modifier
                .width(LARGURA_DA_COLUNA)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { ponto ->
                            val id = orbitaSob(estado.layoutInfo, ponto.y, idsAgora.toSet())
                            if (id != null) {
                                arrasto.id = id
                                arrasto.pontoY = ponto.y
                                arrasto.mexeu = false
                                ordemNaMao = idsAgora
                                if (comVibracao) haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { mudanca, _ ->
                            if (arrasto.arrastando) {
                                mudanca.consume()
                                arrasto.pontoY = mudanca.position.y
                                acompanhar()
                            }
                        },
                        onDragEnd = {
                            val id = arrasto.id
                            val mexeu = arrasto.mexeu
                            val ordem = ordemNaMao
                            arrasto.soltar()
                            when {
                                id == null -> Unit
                                mexeu -> aoReordenar(ordem)
                                else -> aoSegurarOrbita(id)
                            }
                        },
                        onDragCancel = { arrasto.soltar() },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            item(key = CHAVE_SUSSURROS) {
                val accent = astraColors.accent
                Box(
                    Modifier
                        .size(64.dp)
                        .drawBehind {
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.16f),
                                        accent.copy(alpha = 0.05f),
                                        Color.Transparent,
                                    ),
                                    center = center,
                                    radius = size.minDimension * 0.52f,
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Peca(
                        selecionada = emSussurros,
                        rotulo = "Sussurros",
                        naoLida = sussurroNaoLido,
                        aoTocar = aoAbrirSussurros,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.astra_glifo),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }

            item(key = CHAVE_DIVISORIA) {
                Box(
                    Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(astraColors.border),
                )
            }

            items(naTela, key = { it.id }) { orbita ->
                val naMao = arrasto.id == orbita.id
                Box(
                    Modifier
                        .zIndex(if (naMao) 1f else 0f)
                        .then(
                            if (naMao || semMovimento) Modifier
                            else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(200)),
                        )
                        .graphicsLayer {
                            if (!naMao) return@graphicsLayer
                            val centro = centroNaColuna(estado.layoutInfo, orbita.id) ?: return@graphicsLayer
                            translationY = arrasto.pontoY - centro
                            scaleX = ESCALA_NA_MAO
                            scaleY = ESCALA_NA_MAO
                        },
                ) {
                    Peca(
                        selecionada = orbita.id == orbitaAberta && !emSussurros,
                        rotulo = orbita.name,
                        naoLida = orbita.id in naoLidas && orbita.id !in silenciadas,
                        apagada = orbita.id in silenciadas,
                        aoTocar = { aoAbrirOrbita(orbita.id) },
                    ) {
                        var semImagem by remember(orbita.iconUrl) { mutableStateOf(orbita.iconUrl == null) }
                        if (!semImagem) {
                            AsyncImage(
                                model = orbita.iconUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                onError = { semImagem = true },
                                modifier = Modifier.size(LADO).clip(CANTO),
                            )
                        } else {
                            Text(
                                text = orbita.name.take(2).uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = astraColors.text2,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            item(key = CHAVE_NOVA) {
                Peca(
                    selecionada = false,
                    rotulo = "Nova órbita",
                    aoTocar = aoAdicionar,
                ) {
                    Icon(
                        Lucide.Plus,
                        contentDescription = null,
                        tint = astraColors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

    }
}

@Composable
private fun Peca(
    selecionada: Boolean,
    rotulo: String,
    aoTocar: () -> Unit,
    naoLida: Boolean = false,
    apagada: Boolean = false,
    conteudo: @Composable () -> Unit,
) {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val fundo by animateColorAsState(
        if (selecionada) astraColors.overlay else astraColors.raised,
        tween(ACENDER_MS),
        label = "fundo",
    )
    val aceso by animateFloatAsState(
        if (selecionada) 1f else 0f,
        if (semMovimento) tween(0) else tween(ACENDER_MS, easing = EaseOutSoft),
        label = "aceso",
    )
    Box {
        Box(
            modifier = Modifier
                .size(LADO)
                .alpha(if (apagada) 0.45f else 1f)
                .clip(CANTO)
                .background(fundo)
                .border(1.dp, astraColors.border, CANTO)
                .clickable(onClick = aoTocar)
                .semantics { contentDescription = if (naoLida) "$rotulo, mensagens novas" else rotulo },
            contentAlignment = Alignment.Center,
        ) {
            conteudo()
        }
        if (aceso > 0f) {
            Box(
                Modifier
                    .size(LADO)
                    .graphicsLayer {
                        val abertura = 0.86f + 0.14f * aceso
                        scaleX = abertura
                        scaleY = abertura
                        alpha = aceso
                    }
                    .border(1.5.dp, astraColors.accent.copy(alpha = 0.55f), CANTO),
            )
        }
        if (naoLida) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(astraColors.void),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(astraColors.accent))
            }
        }
    }
}
