package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import app.astra.desktop.ui.theme.EaseOutStd
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.astra.desktop.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import coil3.compose.AsyncImage
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

// Feedback tatil de clique (decisao do dono): o alvo encolhe pra ~0.96 enquanto
// pressionado e volta com mola ao soltar. GPU-only (graphicsLayer scale). Reduzir
// movimento -> sem escala. Reaproveita o MESMO InteractionSource que o componente
// já usa pro hover; pra funcionar, o clickable precisa receber esse source
// (clickable(interactionSource = it, indication = null, ...)). Aplique cedo na
// cadeia (antes de clip/background) pra escala envolver o visual inteiro.
//
// TAMBEM desenha o anel de FOCO DE TECLADO, e por isso o nome ficou menor que o
// trabalho — mantido assim porque renomear em 29 lugares seria puro ruido de
// diff. Este e o "jeito de botao" compartilhado do Astra: aperta e encolhe,
// recebe foco e ganha anel.
//
// O anel resolve um buraco que valia pro app INTEIRO: havia 97 lugares com
// `indication = null` e ZERO uso de estado de foco no projeto. O Tab andava pelos
// botoes, mas nada mostrava onde voce estava — quem navega so por teclado ficava
// as cegas. E ficar as cegas nao e um detalhe de acessibilidade: e o app inutil.
//
// Duas escolhas de implementacao que importam:
//  - `onFocusChanged`, e nao o InteractionSource. O modificador observa o foco de
//    quem vem DEPOIS dele na cadeia — e o clickable vem depois. Assim o anel nao
//    depende de o clickable repassar (ou nao) interacao de foco pro source.
//  - o anel e pintado no `drawWithContent`, DEPOIS do drawContent. Como o
//    clickScale entra cedo na cadeia, uma borda comum aqui seria coberta pelo
//    background que vem logo adiante; desenhar por cima e o unico jeito de o anel
//    sobreviver a qualquer ordem de clip/background do chamador.
//
// O ANEL SO APARECE PRA QUEM VEIO DE TECLADO. Clicar com o mouse tambem da foco
// ao alvo — e por isso a borda ficava acesa depois de cada clique, o que o dono
// (com razao) leu como sujeira. Quem separa os dois casos e o
// `LocalInputModeManager`: o Compose troca pra InputMode.Keyboard quando alguem
// anda de Tab e pra Touch quando mexe o mouse. E o mesmo mecanismo que o
// :focus-visible do CSS resolve na web. Apagar o anel de vez nao era opcao: sem
// ele, quem navega so de teclado fica sem saber onde esta.
//
// No lugar do anel, o mouse ganha LUZ: um halo curto no accent atras do alvo
// enquanto ele esta apertado. Halo em vez de borda porque borda desenha um limite
// novo (mais uma linha na tela) e luz so ilumina o limite que ja existe.
// FORMA DE TODO BOTAO DE ICONE DO APP: quadrado de pontas quebradas, nunca
// circulo. O circulo sobrou de quando cada tela resolvia sozinha, e deixava o app
// falando dois idiomas — o rail, o compositor e os menus ja eram 8dp; so os "X" de
// fechar e as acoes de banner continuavam redondos.
//
// O que SEGUE redondo, e de proposito: foto de perfil, bolinha de status e anel de
// XP (sao identidade, nao botao) e os controles de chamada, onde o circulo e
// convencao universal — o vermelho redondo se le como "desligar" sem precisar ler.
val FormaDeBotao = RoundedCornerShape(8.dp)

@Composable
fun Modifier.clickScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
    // Forma do anel. O default cobre quase tudo; botao redondo passa CircleShape,
    // senao sai um anel quadrado em volta de um circulo.
    formaDoFoco: Shape = RoundedCornerShape(8.dp),
): Modifier {
    val reduce = LocalReduceMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    var focado by remember { mutableStateOf(false) }
    val modoDeEntrada = LocalInputModeManager.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "clickScale",
    )
    // Acende rapido e apaga devagar: acender junto com o dedo, apagar deixando
    // rastro. O contrario (apagar seco) faz o clique parecer que nao pegou.
    val brilho by animateFloatAsState(
        targetValue = if (pressed && !reduce) 1f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 90 else 320),
        label = "clickGlow",
    )
    val corDoFoco = Obsidian.accent
    return onFocusChanged { focado = it.isFocused }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawBehind {
            if (brilho <= 0.01f) return@drawBehind
            // Raio maior que a caixa pra a luz vazar pra fora das bordas — dentro
            // dela o background do chamador cobriria quase tudo.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        corDoFoco.copy(alpha = 0.34f * brilho),
                        corDoFoco.copy(alpha = 0.10f * brilho),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = size.minDimension * 0.95f,
                ),
                radius = size.minDimension * 0.95f,
            )
        }
        .drawWithContent {
            drawContent()
            if (!focado || modoDeEntrada.inputMode != InputMode.Keyboard) return@drawWithContent
            val traco = Stroke(width = 2.dp.toPx())
            when (val contorno = formaDoFoco.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> drawRect(corDoFoco, style = traco)
                is Outline.Rounded -> drawPath(Path().apply { addRoundRect(contorno.roundRect) }, corDoFoco, style = traco)
                is Outline.Generic -> drawPath(contorno.path, corDoFoco, style = traco)
            }
        }
}

// CARTAO DENTRO DE CARTAO — a estrutura preferida do projeto, e o que substituiu
// as 14 linhas de separacao que existiam no app.
//
// A regra que ele materializa: conteudo se separa por ANINHAMENTO DE SUPERFICIE,
// nao por traco. Um painel e um cartao; dentro dele, cada bloco e outro cartao,
// um degrau mais claro. Traco de borda a borda le como linha de tabela, e o olho
// passa a ver grade em vez de conteudo.
//
// `fundo` fica exposto porque o degrau depende de onde o cartao mora: dentro de
// um popup (que ja e `overlay`) subir pra `overlay` de novo nao mostraria nada —
// ali o passo certo e `hover`, que e o degrau seguinte da rampa.
@Composable
fun CartaoInterno(
    modifier: Modifier = Modifier,
    fundo: Color = Obsidian.raised,
    borda: Boolean = true,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val forma = RoundedCornerShape(8.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(forma)
            .background(fundo)
            .then(if (borda) Modifier.border(1.dp, Obsidian.borderDim, forma) else Modifier)
            .padding(padding),
        content = conteudo,
    )
}

// Icone Lucide tingido. O desktop NAO tem material (sem Icon()), entao renderiza
// o ImageVector via foundation.Image + ColorFilter.tint. Substitui os glifos/emoji
// que faziam papel de ícone de chrome; a marca ✦ do Astra fica de fora (e
// identidade, não ícone). Mesma lib/versão do :app Android (com.composables.icons.lucide).
//
// `rotulo` e o nome que o leitor de tela anuncia. Fica NULO por padrao de
// proposito: icone ao lado de um texto e decoracao, e anunciar "lixeira, Apagar
// conversa" faz o leitor repetir tudo duas vezes. Quem PRECISA de rotulo e o
// botao so-icone, onde o desenho e a unica pista que existe — e ali estava o
// buraco: os 90 usos de LIcon passavam null sem nem ter como mudar isso.
@Composable
fun LIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Obsidian.text2,
    size: Dp = 16.dp,
    rotulo: String? = null,
) {
    Image(
        imageVector = icon,
        contentDescription = rotulo,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

// Avatar circular com fallback de inicial — usado no shell e no chat. No HOVER: o
// cursor vira mãozinha e um BRILHO acende em volta da foto.
//
// Era um anel que se desenhava de 0 a 360 graus. O anel tinha dois problemas: a
// borda dura competia com a propria foto (que ja e um circulo), e a varredura
// virava uma animacaozinha que pedia atencao toda vez que o mouse passava — e o
// mouse passa por avatar o tempo todo num app de chat. O brilho difuso diz a
// mesma coisa ("da pra clicar") sem desenhar uma segunda borda nem chamar aten-
// cao pra si. So acende no hover -> custo zero parado.
//
// `externalHover` deixa a LINHA que contem o avatar acender o brilho (ex: hover
// na linha de sussurro), não só o hover direto na foto.
@Composable
fun DesktopAvatar(url: String?, name: String, sizeDp: Int, externalHover: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val selfHover by interaction.collectIsHoveredAsState()
    val hovered = selfHover || externalHover
    val brilho by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(durationMillis = if (hovered) 220 else 180, easing = FastOutSlowInEasing),
        label = "avatarBrilho",
    )
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            // O halo vaza pra FORA da caixa do avatar (1.4x o raio) — e o que faz
            // parecer luz e nao mais uma borda. Fica em drawBehind pra passar por
            // baixo da foto; por cima viraria um veu leitoso sobre o rosto.
            .drawBehind {
                if (brilho <= 0.01f) return@drawBehind
                val raio = size.minDimension / 2f * 1.4f
                drawCircle(
                    brush = Brush.radialGradient(
                        // Miolo transparente: a foto tapa essa parte de qualquer
                        // jeito, e pintar por baixo dela so gastaria pixel.
                        0.55f to Color.Transparent,
                        0.72f to Obsidian.accent.copy(alpha = 0.30f * brilho),
                        1f to Color.Transparent,
                        center = center,
                        radius = raio,
                    ),
                    radius = raio,
                )
            }
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clip(CircleShape).background(Obsidian.overlay),
            contentAlignment = Alignment.Center,
        ) {
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = TextStyle(color = Obsidian.accent, fontSize = (sizeDp * 0.42f).sp),
                )
            }
        }
    }
}

// "Reduzir movimento" (Settings > Movimento): quando ligado, as animações de
// fundo (aurora, cascata, pulsos) param. Provido no ShellScreen a partir do
// DesktopPrefs; muda em tempo real. Modifiers @Composable (auroraBackground,
// CascadeIn) e os pulsos leem daqui.
val LocalReduceMotion = staticCompositionLocalOf { false }

// Quem sou eu, pra quem desenha mensagem. Vem por CompositionLocal e nao por
// parametro porque o destaque de mencao mora na FOLHA da arvore (o span de texto
// dentro da bolha), e enfiar dois campos por quatro camadas de assinatura so pra
// pintar uma palavra e pior que a magia.
data class MinhaConta(val id: String? = null, val usuario: String? = null)
val LocalMinhaConta = staticCompositionLocalOf { MinhaConta() }

// Janela "ativa" = visivel E não minimizada (provido no Main a partir do estado
// da janela). Aurora e estrelas gastam frame SO quando ativa — na bandeja/
// minimizada param (guardrail do dono). IMPORTANTE: e diferente de "focada".
// Popups focaveis do desktop (menu de botao direito, dialogs) roubam o foco da
// janela, entao gatear por FOCO congelava a aurora toda vez que abria um menu
// (o "cortada de vez em quando"). Visibilidade não pisca com popup -> sem corte.
val LocalWindowActive = staticCompositionLocalOf { true }

// Prefs de RENDER das animações de fundo (Settings > Desempenho), desacopladas
// dos enums de prefs: octaves do FBM da aurora e teto de FPS (0 = livre). O
// ShellScreen mapeia AuroraQuality/UiFps -> isto e provee; Aurora/StarField leem.
data class RenderPrefs(val auroraOctaves: Int = 3, val fpsCap: Int = 0)
val LocalRenderPrefs = staticCompositionLocalOf { RenderPrefs() }

// Aparencia do CHAT (Settings > Aparencia): multiplicador do tamanho da fonte das
// mensagens + respiro entre elas (topDp/groupedTopDp). Provido no ChatView a partir
// do DesktopPrefs; ContentBlock le a fonte e MessageRow o espacamento.
val LocalMsgFontScale = staticCompositionLocalOf { 1f }
data class MsgDensity(val topDp: Int = 10, val groupedTopDp: Int = 2)
val LocalMsgDensity = staticCompositionLocalOf { MsgDensity() }

// Entrada em cascata (F6): itens de lista revelam um a um (fade + subida leve).
// GPU-only (alpha/translation em graphicsLayer). So os primeiros CASCADE_MAX
// indices animam — item que entra por scroll aparece pronto (LazyColumn recicla).
private const val CASCADE_MAX = 14

// stepMs/startDelayMs/translateY tem default = comportamento antigo, entao os
// call-sites de lista (que passam so index+listKey) não mudam. O ProfilePage passa
// um passo maior (ritmo "um de cada vez") pro perfil.
@Composable
fun CascadeIn(
    index: Int,
    listKey: Any?,
    stepMs: Long = 26L,
    startDelayMs: Long = 0L,
    translateY: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    // Reduzir movimento: entra pronto, sem stagger.
    if (LocalReduceMotion.current) {
        content()
        return
    }
    val animate = index in 0 until CASCADE_MAX
    val enter = remember(listKey) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(listKey) {
        if (enter.value < 1f) {
            delay(startDelayMs + index * stepMs)
            enter.animateTo(1f, tween(230, easing = EaseOutStd))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = enter.value
            translationY = (1f - enter.value) * translateY.toPx()
        },
    ) {
        content()
    }
}

// Tres pontinhos em onda (bounce sequencial) — "digitando…" no chat e sidebar.
@Composable
fun TypingDots(color: Color = Obsidian.text3, dotSize: Dp = 4.dp) {
    // Reduzir movimento: tres pontinhos parados (ainda comunica "digitando").
    if (LocalReduceMotion.current) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { Box(Modifier.size(dotSize).clip(CircleShape).background(color)) }
        }
        return
    }
    val transition = rememberInfiniteTransition()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(3) { i ->
            val dy by transition.animateFloat(
                initialValue = 0f,
                targetValue = -3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(280, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 140),
                ),
            )
            Box(
                Modifier
                    .size(dotSize)
                    .graphicsLayer { translationY = dy * density }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

// "Estouro" de entrada/saida de gente na call (pedido do dono, no idioma do
// Discord): quem chega ENTRA estourando — nasce pequeno e passa do tamanho antes
// de assentar (mola com pouco amortecimento) — e quem sai encolhe e some. E o que
// faz a call parecer viva em vez de a lista so trocar de conteudo.
//
// Uso: envolver cada item de uma lista que muda, com `key` = id da pessoa. O
// AnimatedVisibility precisa nascer com visible=false e virar true no 1o frame,
// senao ele considera o item "ja estava la" e não anima.
// Reduzir movimento -> aparece pronto, sem estouro.
@Composable
fun PopIn(content: @Composable () -> Unit) {
    if (LocalReduceMotion.current) {
        content()
        return
    }
    val vis = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = vis,
        enter = scaleIn(
            // dampingRatio baixo = passa do alvo e volta (o "pop"). Sem isso vira
            // um fade escalado, que não comunica "alguem chegou".
            animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
            initialScale = 0.4f,
        ) + fadeIn(tween(120)),
        exit = scaleOut(tween(150), targetScale = 0.55f) + fadeOut(tween(130)),
    ) {
        content()
    }
}
