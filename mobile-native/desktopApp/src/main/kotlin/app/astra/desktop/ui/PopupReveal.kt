package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import app.astra.desktop.ui.theme.EaseOutStd

// Entrada padrao de popup/menu que abre por clique: fade + leve escala nascendo
// da origem ancorada no gatilho — a MESMA linguagem do menu de botao direito
// (EditorialMenu.MenuCard). Existe pra que os popups que apareciam "secos" (sem
// transicao) passem a nascer com o mesmo idioma, sem repetir o boilerplate.
//
// `originX/originY` = de qual canto o popup cresce, no eixo 0..1:
//   (0f,0f) topo-esquerda (menus)      · (1f,0f) topo-direita (sino, pill do chat)
//   (0.5f,1f) de baixo pra cima (call) · (1f,1f) rodape-direita (estrela)
//   (0f,0.5f) da borda esquerda (submenu ao lado)
//
// Respeita LocalReduceMotion: movimento reduzido -> aparece pronto (0ms), sem escala.

private const val REVEAL_MS = 150

// Wrapper composable: pro filho de Popup que NAO expoe um Modifier proprio no call
// site (ex: ProfileCard(...), ReactionPicker(...), CallSettingsPanel(...)).
@Composable
fun PopupReveal(
    originX: Float = 0f,
    originY: Float = 0f,
    initialScale: Float = 0.94f,
    content: @Composable () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val entered = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = entered,
        enter = if (reduce) {
            fadeIn(tween(0))
        } else {
            fadeIn(tween(REVEAL_MS, easing = EaseOutStd)) +
                scaleIn(
                    tween(REVEAL_MS, easing = EaseOutStd),
                    initialScale = initialScale,
                    transformOrigin = TransformOrigin(originX, originY),
                )
        },
    ) {
        content()
    }
}

// Variante Modifier: pro caso comum em que o filho do Popup JA tem cadeia de
// Modifier (Column/Row com clip/background). Aplicar CEDO na cadeia (antes de
// clip/background) pra escala/alpha envolverem o visual inteiro. Mesma entrada,
// via um unico Animatable lido dentro do graphicsLayer (frame sem recomposicao).
@Composable
fun Modifier.popupReveal(
    originX: Float = 0f,
    originY: Float = 0f,
    initialScale: Float = 0.94f,
): Modifier {
    val reduce = LocalReduceMotion.current
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) p.animateTo(1f, tween(REVEAL_MS, easing = EaseOutStd))
    }
    return this.graphicsLayer {
        alpha = p.value
        val s = initialScale + (1f - initialScale) * p.value
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin(originX, originY)
    }
}
