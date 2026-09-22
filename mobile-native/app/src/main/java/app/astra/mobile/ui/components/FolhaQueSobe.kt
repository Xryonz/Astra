package app.astra.mobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val SUBIDA_MS = 320
private const val DESCIDA_MS = 220
private const val FRACAO_QUE_FECHA = 0.25f
private const val VELOCIDADE_QUE_FECHA = 1_500f
private const val ESCURO_MAXIMO = 0.55f

@Stable
class EstadoDaFolha internal constructor(
    private val escopo: CoroutineScope,
    private val semMovimento: State<Boolean>,
    private val aoFechar: State<() -> Unit>,
) {
    internal var altura by mutableIntStateOf(0)
    internal var y by mutableFloatStateOf(Float.NaN)
    private var saindo = false

    internal val visivel: Float
        get() = if (altura <= 0 || y.isNaN()) 0f else (1f - y / altura).coerceIn(0f, 1f)

    fun fechar(depois: () -> Unit = aoFechar.value) {
        if (saindo) return
        saindo = true
        escopo.launch {
            if (!semMovimento.value && altura > 0 && !y.isNaN()) {
                animate(y, altura.toFloat(), animationSpec = tween(DESCIDA_MS, easing = EaseOutSoft)) { v, _ -> y = v }
            }
            depois()
        }
    }

    internal suspend fun subir() {
        if (altura <= 0 || !y.isNaN()) return
        if (semMovimento.value) {
            y = 0f
        } else {
            y = altura.toFloat()
            animate(y, 0f, animationSpec = tween(SUBIDA_MS, easing = EaseOutSoft)) { v, _ -> y = v }
        }
    }

    internal fun arrastar(delta: Float) {
        if (y.isNaN() || saindo) return
        y = (y + delta).coerceAtLeast(0f)
    }

    internal fun assentar(velocidade: Float) {
        if (altura <= 0 || y.isNaN()) return
        if (y > altura * FRACAO_QUE_FECHA || velocidade > VELOCIDADE_QUE_FECHA) {
            fechar()
        } else {
            escopo.launch { animate(y, 0f, animationSpec = tween(DESCIDA_MS, easing = EaseOutSoft)) { v, _ -> y = v } }
        }
    }

    internal val conexao = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f && !y.isNaN() && y > 0f) {
                val novo = (y + available.y).coerceAtLeast(0f)
                val usado = novo - y
                y = novo
                return Offset(0f, usado)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y > 0f && source == NestedScrollSource.UserInput && !y.isNaN()) {
                y += available.y
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!y.isNaN() && y > 0f) {
                assentar(available.y)
                return available
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberEstadoDaFolha(aoFechar: () -> Unit): EstadoDaFolha {
    val escopo = rememberCoroutineScope()
    val semMovimento = rememberUpdatedState(LocalAppPrefs.current.reduceMotion)
    val fechar = rememberUpdatedState(aoFechar)
    return remember { EstadoDaFolha(escopo, semMovimento, fechar) }
}

@Composable
fun SemEscurecerODialogo() {
    val janela = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { janela?.setDimAmount(0f) }
}

fun Modifier.puxavelParaBaixo(estado: EstadoDaFolha, ativo: Boolean = true): Modifier = this
    .onSizeChanged { estado.altura = it.height }
    .pointerInput(estado, ativo) {
        if (!ativo) return@pointerInput
        val medidor = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { medidor.resetTracking() },
            onDragEnd = { estado.assentar(medidor.calculateVelocity().y) },
            onDragCancel = { estado.assentar(0f) },
        ) { mudanca, delta ->
            medidor.addPosition(mudanca.uptimeMillis, mudanca.position)
            estado.arrastar(delta)
            mudanca.consume()
        }
    }
    .nestedScroll(estado.conexao)
    .graphicsLayer { translationY = if (estado.y.isNaN()) size.height else estado.y }

@Composable
fun SubirAoAbrir(estado: EstadoDaFolha) {
    LaunchedEffect(estado.altura) { estado.subir() }
    BackHandler { estado.fechar() }
}

@Composable
fun FolhaQueSobe(
    estado: EstadoDaFolha,
    rotuloDoFundo: String,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    SemEscurecerODialogo()
    SubirAoAbrir(estado)

    val alturaMaxima = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(astraColors.void.copy(alpha = ESCURO_MAXIMO * estado.visivel))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { estado.fechar() }
                .semantics { contentDescription = rotuloDoFundo },
        )
        val forma = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = alturaMaxima)
                .onSizeChanged { estado.altura = it.height }
                .graphicsLayer { translationY = if (estado.y.isNaN()) size.height else estado.y }
                .clip(forma)
                .background(astraColors.base)
                .border(1.dp, astraColors.border, forma)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .nestedScroll(estado.conexao)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            content = conteudo,
        )
    }
}

@Composable
fun AlcaDaFolha(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(astraColors.text3.copy(alpha = 0.6f)),
    )
}
