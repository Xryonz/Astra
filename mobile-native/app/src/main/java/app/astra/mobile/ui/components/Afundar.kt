package app.astra.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.EaseOutSoft
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val DESCIDA_MS = 90
private const val SUBIDA_MS = 180
private const val ENCOLHIMENTO_MAXIMO = 0.06f
private val PROFUNDIDADE = 6.dp
private val VEU_SEM_MOVIMENTO = Color.Black.copy(alpha = 0.18f)

class Afundar(private val semMovimento: Boolean) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        NoDeAfundar(interactionSource, semMovimento)

    override fun equals(other: Any?): Boolean = other is Afundar && other.semMovimento == semMovimento

    override fun hashCode(): Int = semMovimento.hashCode()
}

private class NoDeAfundar(
    private val fonte: InteractionSource,
    private val semMovimento: Boolean,
) : Modifier.Node(), DrawModifierNode {

    private val fundo = Animatable(0f)
    private var apertado = false
    private var trabalho: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            val ativos = mutableListOf<PressInteraction.Press>()
            var desde = 0L
            fonte.interactions.collect { interacao ->
                when (interacao) {
                    is PressInteraction.Press -> ativos += interacao
                    is PressInteraction.Release -> ativos -= interacao.press
                    is PressInteraction.Cancel -> ativos -= interacao.press
                    else -> return@collect
                }
                val agora = ativos.isNotEmpty()
                if (agora == apertado) return@collect
                apertado = agora
                if (semMovimento) {
                    invalidateDraw()
                    return@collect
                }
                trabalho?.cancel()
                trabalho = if (agora) {
                    desde = System.currentTimeMillis()
                    launch { fundo.animateTo(1f, tween(DESCIDA_MS, easing = EaseOutSoft)) }
                } else {
                    val falta = DESCIDA_MS - (System.currentTimeMillis() - desde)
                    launch {
                        if (falta > 0) {
                            fundo.animateTo(1f, tween(falta.toInt(), easing = EaseOutSoft))
                        }
                        fundo.animateTo(0f, tween(SUBIDA_MS, easing = EaseOutSoft))
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (semMovimento) {
            drawContent()
            if (apertado) drawRect(VEU_SEM_MOVIMENTO)
            return
        }
        val quanto = fundo.value
        if (quanto == 0f) {
            drawContent()
            return
        }
        val maior = maxOf(size.width, size.height).coerceAtLeast(1f)
        val encolhe = minOf(ENCOLHIMENTO_MAXIMO, PROFUNDIDADE.toPx() / maior) * quanto
        scale(1f - encolhe) { this@draw.drawContent() }
    }
}
