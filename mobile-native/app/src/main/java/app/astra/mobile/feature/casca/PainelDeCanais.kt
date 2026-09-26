package app.astra.mobile.feature.casca

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.astra.mobile.feature.server.domain.model.Channel
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.Viagem
import app.astra.mobile.ui.components.viajante
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.Volume2

private const val AVULSOS = ""
private const val PREFIXO_DA_CATEGORIA = "cat-"
private const val PREFIXO_DA_VOZ = "voz-"
private val ZONA_DE_ROLAGEM = 56.dp
private val PASSO_DA_ROLAGEM = 9.dp
private const val ESCALA_NA_MAO = 1.03f
private val CANTO_DO_CARTAO = 16.dp
private val MARGEM_DO_CARTAO = 10.dp
private val CANTO_DA_LINHA = RoundedCornerShape(12.dp)

private class Secao(val categoriaId: String, val nome: String?, val canais: List<Channel>)

private fun secoesDe(orbita: Server): List<Secao> {
    val idsDeCategoria = orbita.categories.mapTo(HashSet()) { it.id }
    val avulsos = orbita.channels.filter { it.categoryId !in idsDeCategoria }.sortedBy { it.position }
    return buildList {
        if (avulsos.isNotEmpty()) add(Secao(AVULSOS, null, avulsos))
        orbita.categories.sortedBy { it.position }.forEach { categoria ->
            add(Secao(categoria.id, categoria.name, orbita.channels.filter { it.categoryId == categoria.id }.sortedBy { it.position }))
        }
    }
}

private enum class OQueEstaNaMao { CANAL, CATEGORIA }

private enum class LugarNoCartao { SOZINHA, PRIMEIRA, MEIO, ULTIMA }

private sealed interface EntradaDoCartao {
    data class Canal(val canal: Channel) : EntradaDoCartao
    data class Voz(val canalId: String, val pessoa: PessoaNaVoz) : EntradaDoCartao
}

private fun lugarNoCartao(indice: Int, quantas: Int): LugarNoCartao = when {
    quantas <= 1 -> LugarNoCartao.SOZINHA
    indice == 0 -> LugarNoCartao.PRIMEIRA
    indice == quantas - 1 -> LugarNoCartao.ULTIMA
    else -> LugarNoCartao.MEIO
}

@Composable
private fun CartaoDaSecao(
    lugar: LugarNoCartao,
    modifier: Modifier = Modifier,
    conteudo: @Composable () -> Unit,
) {
    val topo = lugar == LugarNoCartao.PRIMEIRA || lugar == LugarNoCartao.SOZINHA
    val base = lugar == LugarNoCartao.ULTIMA || lugar == LugarNoCartao.SOZINHA
    val forma = RoundedCornerShape(
        topStart = if (topo) CANTO_DO_CARTAO else 0.dp,
        topEnd = if (topo) CANTO_DO_CARTAO else 0.dp,
        bottomStart = if (base) CANTO_DO_CARTAO else 0.dp,
        bottomEnd = if (base) CANTO_DO_CARTAO else 0.dp,
    )
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MARGEM_DO_CARTAO)
            .clip(forma)
            .background(astraColors.raised)
            .padding(top = if (topo) 6.dp else 0.dp, bottom = if (base) 6.dp else 0.dp),
    ) {
        conteudo()
    }
}

private class ArrastoNoPainel {
    var tipo by mutableStateOf<OQueEstaNaMao?>(null)
    var id by mutableStateOf<String?>(null)
    var secao by mutableStateOf(AVULSOS)
    var pontoY by mutableFloatStateOf(0f)
    var mexeu by mutableStateOf(false)
    var categoriaSob by mutableStateOf<String?>(null)
    var ordem by mutableStateOf<List<String>>(emptyList())
    var seguradoId by mutableStateOf<String?>(null)
    val arrastando: Boolean get() = id != null

    fun soltar() {
        tipo = null
        id = null
        mexeu = false
        categoriaSob = null
        ordem = emptyList()
        seguradoId = null
    }
}

private fun chaveSob(info: LazyListLayoutInfo, y: Float): String? =
    info.visibleItemsInfo.firstOrNull {
        val topo = it.offset - info.viewportStartOffset
        y >= topo && y < topo + it.size
    }?.key as? String

private fun centroNaLista(info: LazyListLayoutInfo, chave: String): Float? =
    info.visibleItemsInfo.firstOrNull { it.key == chave }
        ?.let { it.offset + it.size / 2f - info.viewportStartOffset }

private fun velocidadeDaBorda(y: Float, altura: Float, zona: Float, teto: Float): Float {
    if (altura <= 0f || zona <= 0f) return 0f
    return when {
        y < zona -> -teto * ((zona - y) / zona).coerceIn(0f, 1f)
        y > altura - zona -> teto * ((y - (altura - zona)) / zona).coerceIn(0f, 1f)
        else -> 0f
    }
}

data class PessoaNaVoz(val id: String, val nome: String, val foto: String?, val souEu: Boolean)

@Composable
fun PainelDeCanais(
    orbita: Server,
    canalAberto: String?,
    naoLidos: Set<String>,
    naVoz: Map<String, List<PessoaNaVoz>>,
    recolhidas: Set<String>,
    podeArrumar: Boolean,
    aoAbrirCanal: (Channel) -> Unit,
    aoEntrarNaVoz: (Channel) -> Unit,
    aoSegurarCanal: (Channel) -> Unit,
    aoBuscar: () -> Unit,
    aoConvidar: (() -> Unit)?,
    aoAbrirAjustes: () -> Unit,
    aoAlternarCategoria: (String) -> Unit,
    aoReordenarCanais: (List<String>) -> Unit,
    aoMoverParaCategoria: (canalId: String, categoriaId: String) -> Unit,
    aoReordenarCategorias: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptico = LocalHapticFeedback.current
    val comVibracao = LocalAppPrefs.current.haptics
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val estado = rememberLazyListState()
    val arrasto = remember { ArrastoNoPainel() }
    val secoesReais = remember(orbita) { secoesDe(orbita) }

    val secoes = when (arrasto.tipo) {
        OQueEstaNaMao.CATEGORIA -> {
            val porId = secoesReais.associateBy { it.categoriaId }
            secoesReais.filter { it.categoriaId == AVULSOS } + arrasto.ordem.mapNotNull { porId[it] }
        }
        OQueEstaNaMao.CANAL -> secoesReais.map { secao ->
            if (secao.categoriaId != arrasto.secao) secao
            else {
                val porId = secao.canais.associateBy { it.id }
                Secao(secao.categoriaId, secao.nome, arrasto.ordem.mapNotNull { porId[it] })
            }
        }
        null -> secoesReais
    }
    val secoesAgora by rememberUpdatedState(secoesReais)
    val podeAgora by rememberUpdatedState(podeArrumar)
    val canaisAgora by rememberUpdatedState(orbita.channels)
    val segurarAgora by rememberUpdatedState(aoSegurarCanal)

    fun abrirOMenuDoCanal(canalId: String) {
        canaisAgora.firstOrNull { it.id == canalId }?.let(segurarAgora)
    }

    val acompanhar = {
        val sob = chaveSob(estado.layoutInfo, arrasto.pontoY)
        val id = arrasto.id
        when (arrasto.tipo) {
            OQueEstaNaMao.CANAL -> {
                val secao = secoesAgora.firstOrNull { it.categoriaId == arrasto.secao }
                when {
                    sob == null || id == null || secao == null -> Unit
                    sob.startsWith(PREFIXO_DA_CATEGORIA) -> {
                        val alvo = sob.removePrefix(PREFIXO_DA_CATEGORIA)
                        arrasto.categoriaSob = alvo.takeIf { it != arrasto.secao }
                    }
                    sob != id && sob in arrasto.ordem -> {
                        arrasto.categoriaSob = null
                        val nova = arrasto.ordem.toMutableList()
                        nova.add(arrasto.ordem.indexOf(sob), nova.removeAt(arrasto.ordem.indexOf(id)))
                        arrasto.ordem = nova
                        arrasto.mexeu = true
                    }
                    else -> arrasto.categoriaSob = null
                }
            }
            OQueEstaNaMao.CATEGORIA -> {
                val alvo = sob?.takeIf { it.startsWith(PREFIXO_DA_CATEGORIA) }?.removePrefix(PREFIXO_DA_CATEGORIA)
                if (id != null && alvo != null && alvo != id && alvo in arrasto.ordem) {
                    val nova = arrasto.ordem.toMutableList()
                    nova.add(arrasto.ordem.indexOf(alvo), nova.removeAt(arrasto.ordem.indexOf(id)))
                    arrasto.ordem = nova
                    arrasto.mexeu = true
                }
            }
            null -> Unit
        }
    }

    val zona = with(LocalDensity.current) { ZONA_DE_ROLAGEM.toPx() }
    val teto = with(LocalDensity.current) { PASSO_DA_ROLAGEM.toPx() }
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

    LazyColumn(
        state = estado,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { ponto ->
                        val sob = chaveSob(estado.layoutInfo, ponto.y) ?: return@detectDragGesturesAfterLongPress
                        if (!sob.startsWith(PREFIXO_DA_CATEGORIA)) {
                            arrasto.seguradoId = sob
                            if (comVibracao) haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (!podeAgora) return@detectDragGesturesAfterLongPress
                        if (sob.startsWith(PREFIXO_DA_CATEGORIA)) {
                            val id = sob.removePrefix(PREFIXO_DA_CATEGORIA)
                            arrasto.tipo = OQueEstaNaMao.CATEGORIA
                            arrasto.id = id
                            arrasto.ordem = secoesAgora.map { it.categoriaId }.filter { it != AVULSOS }
                        } else {
                            val secao = secoesAgora.firstOrNull { s -> s.canais.any { it.id == sob } }
                                ?: return@detectDragGesturesAfterLongPress
                            arrasto.tipo = OQueEstaNaMao.CANAL
                            arrasto.id = sob
                            arrasto.secao = secao.categoriaId
                            arrasto.ordem = secao.canais.map { it.id }
                        }
                        arrasto.pontoY = ponto.y
                        arrasto.mexeu = false
                        if (comVibracao && sob.startsWith(PREFIXO_DA_CATEGORIA)) {
                            haptico.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        val tipo = arrasto.tipo
                        val ordem = arrasto.ordem
                        val destino = arrasto.categoriaSob
                        val mexeu = arrasto.mexeu
                        val segurado = arrasto.seguradoId
                        arrasto.soltar()
                        when {
                            !mexeu && segurado != null -> abrirOMenuDoCanal(segurado)
                            id == null -> Unit
                            tipo == OQueEstaNaMao.CANAL && destino != null -> aoMoverParaCategoria(id, destino)
                            tipo == OQueEstaNaMao.CANAL && mexeu -> aoReordenarCanais(ordem)
                            tipo == OQueEstaNaMao.CATEGORIA && mexeu -> aoReordenarCategorias(ordem)
                        }
                    },
                    onDragCancel = {
                        val segurado = arrasto.seguradoId.takeIf { !arrasto.mexeu }
                        arrasto.soltar()
                        segurado?.let { abrirOMenuDoCanal(it) }
                    },
                )
            },
    ) {
        item(key = "capa") {
            Capa(orbita = orbita, aoBuscar = aoBuscar, aoConvidar = aoConvidar, aoAbrirAjustes = aoAbrirAjustes)
        }

        secoes.forEach { secao ->
            val nome = secao.nome
            val recolhida = secao.categoriaId in recolhidas
            if (nome != null) {
                val chave = PREFIXO_DA_CATEGORIA + secao.categoriaId
                item(key = chave) {
                    val naMao = arrasto.tipo == OQueEstaNaMao.CATEGORIA && arrasto.id == secao.categoriaId
                    TituloDaCategoria(
                        nome = nome,
                        recolhida = secao.categoriaId in recolhidas,
                        acesa = arrasto.categoriaSob == secao.categoriaId,
                        aoTocar = { aoAlternarCategoria(secao.categoriaId) },
                        modifier = Modifier
                            .zIndex(if (naMao) 1f else 0f)
                            .graphicsLayer {
                                if (!naMao) return@graphicsLayer
                                val centro = centroNaLista(estado.layoutInfo, chave) ?: return@graphicsLayer
                                translationY = arrasto.pontoY - centro
                                scaleX = ESCALA_NA_MAO
                                scaleY = ESCALA_NA_MAO
                            },
                    )
                }
            }
            val visiveis = when {
                arrasto.tipo == OQueEstaNaMao.CATEGORIA -> emptyList()
                recolhida -> secao.canais.filter { it.id == canalAberto || it.id in naoLidos || it.id == arrasto.id }
                else -> secao.canais
            }
            val entradas = buildList {
                visiveis.forEach { canal ->
                    add(EntradaDoCartao.Canal(canal))
                    if (canal.isVoice && arrasto.id != canal.id) {
                        naVoz[canal.id].orEmpty().forEach { add(EntradaDoCartao.Voz(canal.id, it)) }
                    }
                }
            }
            entradas.forEachIndexed { posicaoNaLista, entrada ->
                val lugar = lugarNoCartao(posicaoNaLista, entradas.size)
                when (entrada) {
                    is EntradaDoCartao.Canal -> {
                        val canal = entrada.canal
                        item(key = canal.id) {
                            val naMao = arrasto.tipo == OQueEstaNaMao.CANAL && arrasto.id == canal.id
                            CartaoDaSecao(
                                lugar = if (naMao) LugarNoCartao.SOZINHA else lugar,
                                modifier = Modifier
                                    .zIndex(if (naMao) 1f else 0f)
                                    .then(
                                        if (naMao || semMovimento) Modifier
                                        else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(180)),
                                    )
                                    .graphicsLayer {
                                        if (!naMao) return@graphicsLayer
                                        val centro = centroNaLista(estado.layoutInfo, canal.id) ?: return@graphicsLayer
                                        translationY = arrasto.pontoY - centro
                                        scaleX = ESCALA_NA_MAO
                                        scaleY = ESCALA_NA_MAO
                                    },
                            ) {
                                LinhaDeCanal(
                                    canal = canal,
                                    aberto = canal.id == canalAberto,
                                    naoLido = canal.id in naoLidos,
                                    naMao = naMao,
                                    aoTocar = { if (canal.isVoice) aoEntrarNaVoz(canal) else aoAbrirCanal(canal) },
                                )
                            }
                        }
                    }
                    is EntradaDoCartao.Voz -> {
                        item(key = PREFIXO_DA_VOZ + entrada.canalId + ":" + entrada.pessoa.id) {
                            CartaoDaSecao(
                                lugar = lugar,
                                modifier = if (semMovimento) Modifier
                                else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = tween(160), placementSpec = tween(180)),
                            ) {
                                LinhaDeQuemEstaNaVoz(entrada.pessoa)
                            }
                        }
                    }
                }
            }
        }

        item(key = "respiro") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun Capa(orbita: Server, aoBuscar: () -> Unit, aoConvidar: (() -> Unit)?, aoAbrirAjustes: () -> Unit) {
    Column {
        if (orbita.bannerUrl != null) {
            AsyncImage(
                model = orbita.bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
            )
        }
        Row(
            modifier = Modifier
                .padding(start = 10.dp, end = 12.dp, top = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "abrir ajustes da órbita", onClick = aoAbrirAjustes)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = orbita.name,
                fontFamily = DmSerif,
                style = MaterialTheme.typography.headlineSmall,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Icon(Lucide.ChevronRight, contentDescription = null, tint = astraColors.text3, modifier = Modifier.size(18.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val forma = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(forma)
                    .background(astraColors.raised)
                    .border(1.dp, astraColors.border, forma)
                    .clickable(onClick = aoBuscar),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Search, contentDescription = null, tint = astraColors.text2, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Buscar", style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
            }
            if (aoConvidar != null) {
                Spacer(Modifier.width(8.dp))
                BotaoRedondo(icone = Lucide.UserPlus, rotulo = "Convidar pessoas", aoTocar = aoConvidar)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TituloDaCategoria(
    nome: String,
    recolhida: Boolean,
    acesa: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val giro by animateFloatAsState(if (recolhida) -90f else 0f, tween(160), label = "giro")
    val forma = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = MARGEM_DO_CARTAO, end = MARGEM_DO_CARTAO, top = 16.dp, bottom = 6.dp)
            .clip(forma)
            .background(if (acesa) astraColors.accentDim else Color.Transparent)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { stateDescription = if (recolhida) "recolhida" else "aberta" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = nome,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = if (acesa) astraColors.accent else astraColors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Lucide.ChevronDown,
            contentDescription = null,
            tint = astraColors.text3,
            modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = giro },
        )
    }
}

@Composable
private fun LinhaDeCanal(
    canal: Channel,
    aberto: Boolean,
    naoLido: Boolean,
    naMao: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destaque = naoLido || aberto
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(CANTO_DA_LINHA)
            .background(
                when {
                    naMao -> astraColors.hover
                    aberto -> astraColors.overlay
                    else -> Color.Transparent
                },
            )
            .clickable(onClick = aoTocar)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (canal.isVoice) Lucide.Volume2 else Lucide.Hash,
            contentDescription = null,
            tint = if (destaque) astraColors.text1 else astraColors.text3,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            Text(
                text = canal.name,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                fontWeight = if (naoLido) FontWeight.SemiBold else FontWeight.Normal,
                color = if (destaque) astraColors.text1 else astraColors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (canal.isVoice) Modifier else Modifier.viajante(Viagem.nomeDoCanal(canal.id), ehTexto = true),
            )
        }
        if (naoLido && !aberto) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(astraColors.accent))
        }
    }
}

@Composable
private fun LinhaDeQuemEstaNaVoz(pessoa: PessoaNaVoz, modifier: Modifier = Modifier) {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val chegada = remember { Animatable(if (semMovimento) 1f else 0f) }
    LaunchedEffect(Unit) { if (chegada.value < 1f) chegada.animateTo(1f, tween(300, easing = EaseOutSoft)) }
    val recuo = with(LocalDensity.current) { 10.dp.toPx() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = chegada.value
                translationX = (1f - chegada.value) * -recuo
            }
            .padding(start = 52.dp, end = 16.dp, top = 2.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = if (pessoa.souEu) "Você está nesta sala" else "${pessoa.nome} está nesta sala" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AstraAvatar(pessoa.foto, pessoa.nome, size = 22)
        Spacer(Modifier.width(10.dp))
        Text(
            pessoa.nome,
            style = MaterialTheme.typography.bodyMedium,
            color = if (pessoa.souEu) astraColors.text2 else astraColors.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun BotaoRedondo(
    icone: ImageVector,
    rotulo: String,
    aoTocar: () -> Unit,
    marca: Int = 0,
) {
    Box {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(astraColors.raised)
                .border(1.dp, astraColors.border, CircleShape)
                .clickable(onClick = aoTocar)
                .semantics { contentDescription = if (marca > 0) "$rotulo, $marca novos" else rotulo },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icone, contentDescription = null, tint = astraColors.text2, modifier = Modifier.size(17.dp))
        }
        if (marca > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(astraColors.accent)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                ContadorQueMuda(if (marca > 9) "9+" else "$marca")
            }
        }
    }
}

@Composable
internal fun ContadorQueMuda(texto: String) {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    AnimatedContent(
        targetState = texto,
        transitionSpec = {
            if (semMovimento) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else {
                (slideInVertically(tween(220, easing = EaseOutSoft)) { it } + fadeIn(tween(180))) togetherWith
                    (slideOutVertically(tween(180, easing = EaseOutSoft)) { -it } + fadeOut(tween(140)))
            }
        },
        label = "contador",
    ) { valor ->
        Text(valor, style = MaterialTheme.typography.labelSmall, color = astraColors.textInv)
    }
}
