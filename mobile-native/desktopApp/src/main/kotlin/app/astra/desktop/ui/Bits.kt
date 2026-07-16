package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import app.astra.desktop.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import coil3.compose.AsyncImage

// Feedback tatil de clique (decisao do dono): o alvo encolhe pra ~0.96 enquanto
// pressionado e volta com mola ao soltar. GPU-only (graphicsLayer scale). Reduzir
// movimento -> sem escala. Reaproveita o MESMO InteractionSource que o componente
// ja usa pro hover; pra funcionar, o clickable precisa receber esse source
// (clickable(interactionSource = it, indication = null, ...)). Aplique cedo na
// cadeia (antes de clip/background) pra escala envolver o visual inteiro.
@Composable
fun Modifier.clickScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val reduce = LocalReduceMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "clickScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

// Icone Lucide tingido. O desktop NAO tem material (sem Icon()), entao renderiza
// o ImageVector via foundation.Image + ColorFilter.tint. Substitui os glifos/emoji
// que faziam papel de icone de chrome; a marca ✦ do Astra fica de fora (e
// identidade, nao icone). Mesma lib/versao do :app Android (com.composables.icons.lucide).
@Composable
fun LIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Obsidian.text2,
    size: Dp = 16.dp,
) {
    Image(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

// Avatar circular com fallback de inicial — usado no shell e no chat.
@Composable
fun DesktopAvatar(url: String?, name: String, sizeDp: Int) {
    Box(
        modifier = Modifier.size(sizeDp.dp).clip(CircleShape).background(Obsidian.overlay),
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

// "Reduzir movimento" (Settings > Movimento): quando ligado, as animacoes de
// fundo (aurora, cascata, pulsos) param. Provido no ShellScreen a partir do
// DesktopPrefs; muda em tempo real. Modifiers @Composable (auroraBackground,
// CascadeIn) e os pulsos leem daqui.
val LocalReduceMotion = staticCompositionLocalOf { false }

// Janela "ativa" = visivel E nao minimizada (provido no Main a partir do estado
// da janela). Aurora e estrelas gastam frame SO quando ativa — na bandeja/
// minimizada param (guardrail do dono). IMPORTANTE: e diferente de "focada".
// Popups focaveis do desktop (menu de botao direito, dialogs) roubam o foco da
// janela, entao gatear por FOCO congelava a aurora toda vez que abria um menu
// (o "cortada de vez em quando"). Visibilidade nao pisca com popup -> sem corte.
val LocalWindowActive = staticCompositionLocalOf { true }

// Prefs de RENDER das animacoes de fundo (Settings > Desempenho), desacopladas
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

@Composable
fun CascadeIn(index: Int, listKey: Any?, content: @Composable () -> Unit) {
    // Reduzir movimento: entra pronto, sem stagger.
    if (LocalReduceMotion.current) {
        content()
        return
    }
    val animate = index in 0 until CASCADE_MAX
    val enter = remember(listKey) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(listKey) {
        if (enter.value < 1f) {
            delay(index * 26L)
            enter.animateTo(1f, tween(230, easing = EaseOutStd))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = enter.value
            translationY = (1f - enter.value) * 10.dp.toPx()
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
