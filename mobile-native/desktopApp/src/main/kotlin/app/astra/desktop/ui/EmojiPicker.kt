package app.astra.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.EmojiDto
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

// O SELETOR DE EMOJI.
//
// Antes eram 34 glifos numa lista cravada dentro do ChatView, sem categoria e sem
// busca. Agora sao ~700 em oito categorias, com busca e recentes.
//
// UMA GRADE SO, com os titulos ocupando a linha inteira (span cheio), em vez de uma
// Column de grades por categoria. Assim a rolagem e continua, os atalhos de baixo
// podem pular pra um indice exato, e — o que importa de verdade — a LazyVerticalGrid
// so compoe as celulas visiveis. Oito grades empilhadas dentro de um scroll comum
// comporiam as 700 celulas de uma vez, toda vez que o painel abrisse.

private const val COLUNAS = 8
private val LARGURA = 336.dp
private val ALTURA_GRADE = 260.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPicker(onPick: (String) -> Unit, personalizados: List<EmojiDto> = emptyList()) {
    val prefs = remember { GlobalContext.get().get<DesktopPrefs>() }
    val recentes by remember { derivedStateOf { prefs.state.value.emojiRecentes } }
    val prefState by prefs.state.collectAsState()
    var termo by remember { mutableStateOf("") }
    val grade = rememberLazyGridState()
    val escopo = rememberCoroutineScope()
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }

    val resultados = remember(termo) { if (termo.isBlank()) emptyList() else buscarEmojis(termo) }
    val achadosDaCasa = remember(termo, personalizados) {
        if (termo.isBlank()) emptyList()
        else personalizados.filter { it.name.contains(termo, ignoreCase = true) }
    }
    val buscando = termo.isNotBlank()

    // Onde comeca cada categoria na grade linear. Calculado uma vez (a lista de
    // recentes e a unica parte que muda) e usado pelos atalhos de baixo.
    val ancoras = remember(prefState.emojiRecentes.size, personalizados.size) {
        val mapa = LinkedHashMap<String, Int>()
        var i = 0
        if (personalizados.isNotEmpty()) {
            i += 1 + linhas(personalizados.size)
        }
        if (prefState.emojiRecentes.isNotEmpty()) {
            i += 1 + linhas(prefState.emojiRecentes.size)
        }
        for (c in CATEGORIAS_EMOJI) {
            mapa[c.id] = i
            i += 1 + linhas(c.itens.size)
        }
        mapa
    }

    val escolher: (String) -> Unit = { g ->
        prefs.registrarEmoji(g)
        onPick(g)
    }

    Column(
        Modifier
            .width(LARGURA)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
    ) {
        // ---- busca ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LIcon(Lucide.Search, tint = Obsidian.text3, size = 13.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (termo.isEmpty()) {
                    Text(
                        "buscar emoji…",
                        style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                    )
                }
                BasicTextField(
                    value = termo,
                    onValueChange = { termo = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
                    cursorBrush = SolidColor(Obsidian.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(foco),
                )
            }
        }
        // Sem traco aqui: o campo de busca logo acima JA e um cartao com borda
        // propria, entao a linha so repetia o limite que ele ja desenha.
        Spacer(Modifier.height(2.dp))

        // ---- grade ----
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUNAS),
            state = grade,
            modifier = Modifier.height(ALTURA_GRADE).padding(horizontal = 6.dp),
        ) {
            if (buscando) {
                // Os da constelacao entram na busca TAMBEM. Sem isto eles apareceriam
                // na primeira tela e sumiriam no instante em que se digita o nome
                // deles — que e justamente quando se sabe qual se quer.
                if (achadosDaCasa.isNotEmpty()) {
                    items(achadosDaCasa, key = { "b_${it.id}" }) { e ->
                        CelulaEmojiPersonalizado(e) { onPick(":${e.name}:") }
                    }
                }
                if (resultados.isEmpty() && achadosDaCasa.isEmpty()) {
                    item(span = { GridItemSpan(COLUNAS) }) {
                        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "nada com \"$termo\"",
                                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                            )
                        }
                    }
                } else {
                    items(resultados, key = { it }) { g -> CelulaEmoji(g) { escolher(g) } }
                }
            } else {
                // Os da CONSTELACAO vem primeiro, antes ate dos recentes: sao poucos,
                // sao os unicos que so existem aqui, e sao os que se procura quando se
                // abre este painel numa constelacao que tem os seus. Escolher um
                // insere `:nome:` no rascunho — o texto e que viaja, nao a imagem.
                //
                // Eles NAO entram nos recentes: a fileira de recentes desenha glifo de
                // texto, e um `:nome:` la sairia escrito em vez de desenhado.
                if (personalizados.isNotEmpty()) {
                    item(span = { GridItemSpan(COLUNAS) }, key = "t_desta") { TituloSecao("esta constelação") }
                    items(personalizados, key = { "p_${it.id}" }) { e ->
                        CelulaEmojiPersonalizado(e) { onPick(":${e.name}:") }
                    }
                }
                if (prefState.emojiRecentes.isNotEmpty()) {
                    item(span = { GridItemSpan(COLUNAS) }) { TituloSecao("recentes") }
                    items(prefState.emojiRecentes, key = { "r_$it" }) { g -> CelulaEmoji(g) { escolher(g) } }
                }
                for (c in CATEGORIAS_EMOJI) {
                    item(span = { GridItemSpan(COLUNAS) }, key = "t_${c.id}") { TituloSecao(c.nome) }
                    items(c.itens, key = { "${c.id}_$it" }) { g -> CelulaEmoji(g) { escolher(g) } }
                }
            }
        }

        // ---- atalhos de categoria ----
        // Escondidos durante a busca: pular pra "natureza" no meio de um resultado
        // filtrado levaria pra um lugar que nao existe naquela lista.
        if (!buscando) {
            // Faixa propria (um degrau acima do popup) no lugar do traco: os
            // atalhos sao NAVEGACAO, nao mais uma fileira de emoji — e sem
            // superficie propria eles se confundiam com a ultima linha da grade.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Obsidian.hover)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (c in CATEGORIAS_EMOJI) {
                    CelulaEmoji(c.atalho, tamanho = 26.dp) {
                        escopo.launch {
                            ancoras[c.id]?.let { grade.animateScrollToItem(it) }
                        }
                    }
                }
            }
        }
    }
}

// Quantas LINHAS da grade um bloco de N itens ocupa. Os titulos usam span cheio,
// entao contam como uma linha inteira cada — e e por isso que a ancora de cada
// categoria e calculada em INDICE DE ITEM e nao em posicao de pixel.
private fun linhas(n: Int): Int = (n + COLUNAS - 1) / COLUNAS

@Composable
private fun TituloSecao(nome: String) {
    Text(
        nome,
        style = TextStyle(
            color = Obsidian.text3, fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 1.1.sp,
        ),
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp),
    )
}

// Mesma celula, com imagem no lugar do glifo. O nome vai no rotulo de
// acessibilidade porque e ele que identifica o emoji — a imagem, sozinha, nao diz
// nada pra quem usa leitor de tela.
@Composable
private fun CelulaEmojiPersonalizado(e: EmojiDto, onClick: () -> Unit) {
    val src = remember(e.id) { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (hov) Obsidian.hover else Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AstraImage(
            url = e.url,
            contentDescription = ":${e.name}:",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CelulaEmoji(glifo: String, tamanho: androidx.compose.ui.unit.Dp = 34.dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(tamanho)
            .clip(RoundedCornerShape(7.dp))
            .background(if (hov) Obsidian.hover else Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glifo, style = TextStyle(fontSize = 17.sp, color = Obsidian.text1))
    }
}
