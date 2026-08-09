package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.MutualServerDto
import app.astra.mobile.core.network.dto.ProfileViewWrapper
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Perfil completo (F: pagina de perfil) — modal CENTRAL sobre scrim, o irmao
// maior do ProfilePopup. Abre pelo botao "ver perfil completo" do card pequeno.
// Alem do que o card mostra, traz "membro desde" e os SERVIDORES EM COMUM (já vem
// do GET /api/profile/:id -> ProfileViewWrapper; o card so descartava). A entrada
// (scrim + card + cascata das secoes) segue o idioma do CenteredConfirmDialog;
// fable refina a coreografia depois. Respeita LocalReduceMotion.

// Proporcoes do painel, calcadas na referencia do Discord: ~1290x940 com a coluna
// da identidade em ~40% da largura. Aqui: 840 de largura, 380 pra coluna do
// cartao e o resto pros vinculos. A ALTURA e fixa (nao "o que o conteudo pedir"),
// porque uma conta nova tem duas linhas de conteudo e o painel encolheria pra um
// retangulo estranho no meio da tela.
val LARGURA_PAGINA_PERFIL = 840.dp
val LARGURA_COLUNA_DO_CARTAO = 380.dp
val ALTURA_PAGINA_PERFIL = 640.dp

// Rodape dissolvido. Quando o conteudo passa da altura maxima, o scroll FATIA o
// texto no meio e o corte seco parece quina dura; este veu faz o conteudo sumir
// em vez de ser cortado. Fica ANTES do verticalScroll de proposito: assim ele
// desenha no espaco da JANELA (fixo no pe da coluna) e nao rola junto.
private fun Modifier.veuNoPe(): Modifier = drawWithContent {
    drawContent()
    val h = 26.dp.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, Obsidian.void.copy(alpha = 0.85f)),
            startY = size.height - h,
            endY = size.height,
        ),
        topLeft = Offset(0f, size.height - h),
        size = Size(size.width, h),
    )
}

private object CenterFill : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePage(
    userId: String,
    isMe: Boolean,
    onStartDm: (username: String, title: String) -> Unit,
    onClose: () -> Unit,
) {
    val koin = GlobalContext.get()
    var data by remember(userId) { mutableStateOf<ProfileViewWrapper?>(null) }
    LaunchedEffect(userId) {
        data = runCatching { koin.get<UserApi>().profile(userId).data }.getOrNull()
    }

    // Entrada: scrim faz fade, o card escala/sobe de leve. Uma passada; reduzir
    // movimento -> aparece pronto. Lido dentro do graphicsLayer (frame sem recompor).
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            appear.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    // Fecha ANIMANDO (a mesma curva tocada pra tras) antes de avisar o host — sem
    // isso o modal some seco. `closing` trava reentrada (duplo clique scrim/X/Esc).
    fun requestClose() {
        if (closing) return
        closing = true
        if (reduce) { onClose(); return }
        scope.launch {
            appear.animateTo(0f, tween(160, easing = EaseOutStd))
            onClose()
        }
    }

    Popup(
        popupPositionProvider = CenterFill,
        onDismissRequest = { requestClose() },
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { e ->
            if (e.key == Key.Escape && e.type == KeyEventType.KeyDown) { requestClose(); true } else false
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            // Scrim: escurece o resto e fecha ao clicar fora.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { requestClose() },
                    ),
            )
            // Card central. DUAS COLUNAS (referencia do dono: o perfil do Discord):
            // a esquerda e quem a pessoa E — banner, foto, nome, bio, desde quando.
            // A direita e o que voces tem EM COMUM. Antes era uma coluna so, e as
            // constelacoes em comum ficavam no pe de um cartao que ja rolava.
            //
            // A aba "Atividade" da referencia ficou de FORA de proposito: nao
            // existe fonte pra ela (nem rota, nem tabela). Uma aba que so sabe
            // dizer "nada aqui" e pior que nao ter aba.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .graphicsLayer {
                            alpha = appear.value.coerceIn(0f, 1f)
                            val s = 0.94f + 0.06f * appear.value
                            scaleX = s
                            scaleY = s
                            translationY = (1f - appear.value) * 16.dp.toPx()
                        }
                        .width(LARGURA_PAGINA_PERFIL)
                        .height(ALTURA_PAGINA_PERFIL)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Obsidian.base)
                        .border(1.dp, Obsidian.borderMid, RoundedCornerShape(16.dp))
                        // Engole o clique (senao o scrim fecha ao clicar no card).
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    val d = data
                    if (d == null) {
                        Column(Modifier.fillMaxWidth()) { PageSkeleton() }
                    } else {
                        val nome = d.user.displayName ?: d.user.username
                        // Coluna da identidade. Rola sozinha: bio longa nao pode
                        // empurrar a coluna dos vinculos pra fora da tela.
                        // fillMaxHeight NO CARTAO, e nao so na coluna: o cartao tem
                        // fundo e borda proprios, entao parar na altura do conteudo
                        // deixava um retangulo curto boiando num painel alto — foi
                        // exatamente o que o dono viu. Com a altura toda, ele vira a
                        // coluna da esquerda de verdade.
                        // SEM verticalScroll aqui, e isso e deliberado: dentro de um
                        // scroll a altura maxima e INFINITA, e `fillMaxHeight` com
                        // maximo infinito nao faz nada — o cartao continuaria curto.
                        // Era essa a armadilha. Bio muito longa fica cortada com o
                        // veu no pe avisando; a coluna da direita e que rola.
                        Column(
                            Modifier
                                .width(LARGURA_COLUNA_DO_CARTAO)
                                .fillMaxHeight()
                                .veuNoPe(),
                        ) {
                            ProfileCard(
                                modifier = Modifier.fillMaxHeight(),
                                dados = d.user.paraCartao(),
                                variante = CardVariante.COMPLETO,
                                // Os servidores em comum saem daqui: eles sao
                                // VINCULO, e vinculo mora na coluna da direita.
                                servidoresEmComum = emptyList(),
                                rodape = if (isMe) null else ({
                                    val src = remember { MutableInteractionSource() }
                                    Text(
                                        "enviar sussurro",
                                        style = TextStyle(
                                            color = Obsidian.void, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickScale(src)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Obsidian.accent)
                                            .clickable(interactionSource = src, indication = null) {
                                                onStartDm(d.user.username, nome)
                                            }
                                            .padding(vertical = 11.dp),
                                        maxLines = 1,
                                    )
                                }),
                            )
                        }
                        ColunaDeVinculos(
                            nome = nome,
                            isMe = isMe,
                            amigosEmComum = d.mutualFriends,
                            constelacoes = d.mutualServers,
                            onFechar = { requestClose() },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

// A coluna da direita: o que voces tem EM COMUM. Cada bloco e um cartao, nao um
// item separado por traco — e a mesma regra do resto do app.
//
// Nao ha aba aqui. A referencia tem tres ("Atividade", "N amigos mutuos",
// "N servidores mutuos"), mas duas delas seriam abas de UMA lista curta cada, e
// a terceira nao tem dado nenhum por tras. Aba que esconde tres linhas custa um
// clique pra economizar nada.
@Composable
private fun ColunaDeVinculos(
    nome: String,
    isMe: Boolean,
    amigosEmComum: Int,
    constelacoes: List<MutualServerDto>,
    onFechar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isMe) "seus vínculos" else "vínculos",
                style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif),
                modifier = Modifier.weight(1f),
            )
            val src = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(28.dp)
                    .clickScale(src, formaDoFoco = FormaDeBotao)
                    .clip(FormaDeBotao)
                    .background(Obsidian.overlay)
                    .border(1.dp, Obsidian.borderMid, FormaDeBotao)
                    .clickable(interactionSource = src, indication = null, onClick = onFechar),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(Lucide.X, tint = Obsidian.text2, size = 14.dp, rotulo = "fechar")
            }
        }
        Spacer(Modifier.height(14.dp))

        if (!isMe) {
            CartaoInterno(fundo = Obsidian.raised, padding = PaddingValues(12.dp)) {
                Text(
                    "AMIGOS EM COMUM",
                    style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (amigosEmComum) {
                        0 -> "nenhum ainda"
                        1 -> "1 pessoa que vocês dois conhecem"
                        else -> "$amigosEmComum pessoas que vocês dois conhecem"
                    },
                    style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        CartaoInterno(fundo = Obsidian.raised, padding = PaddingValues(12.dp)) {
            Text(
                if (isMe) "SUAS CONSTELAÇÕES" else "CONSTELAÇÕES EM COMUM",
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            if (constelacoes.isEmpty()) {
                Text(
                    if (isMe) "você ainda não entrou em nenhuma"
                    else "vocês ainda não dividem nenhuma",
                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                )
            } else {
                constelacoes.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Obsidian.overlay),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!s.iconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = s.iconUrl,
                                    contentDescription = s.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    s.name.take(1).uppercase(),
                                    style = TextStyle(color = Obsidian.accent, fontSize = 12.sp, fontFamily = DmSerif),
                                )
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            s.name,
                            style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Dito em voz alta em vez de escondido numa aba vazia: o app nao guarda
        // atividade, e prometer uma aba que nunca tem nada seria pior.
        Text(
            if (isMe) "o Astra ainda não guarda histórico de atividade."
            else "o Astra ainda não mostra o que $nome anda fazendo.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        )
    }
}

@Composable
private fun PageSkeleton() {
    Box(Modifier.fillMaxWidth().height(140.dp).background(Obsidian.overlay))
    Column(Modifier.padding(horizontal = 20.dp)) {
        Box(
            Modifier
                .offset(y = (-38).dp)
                .clip(CircleShape)
                .background(Obsidian.raised)
                .border(4.dp, Obsidian.borderMid, CircleShape)
                .padding(4.dp),
        ) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(Obsidian.overlay))
        }
        Column(Modifier.offset(y = (-24).dp)) {
            Box(Modifier.width(180.dp).height(20.dp).clip(RoundedCornerShape(6.dp)).background(Obsidian.overlay))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(120.dp).height(12.dp).clip(RoundedCornerShape(5.dp)).background(Obsidian.overlay))
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(5.dp)).background(Obsidian.overlay))
            Spacer(Modifier.height(20.dp))
        }
    }
}
