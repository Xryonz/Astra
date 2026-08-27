package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import kotlin.math.roundToInt

private const val ALTURA_ALVO_NO_PALCO = 92

private val Pet.escalaDePalco: Int
    get() = (ALTURA_ALVO_NO_PALCO.toFloat() / pes).roundToInt().coerceAtLeast(1)

private val ALTURA_DO_PALCO = 168.dp

@Composable
internal fun PetPalco(
    pet: Pet,
    pelagem: Pelagem,
    anim: Anim,
    modifier: Modifier = Modifier,
) {
    val folhas = remember(pet, pelagem) { FolhasDoPet.folhas(pet, pelagem) }
    val passo = pet.passos[anim]
    val densidade = LocalDensity.current.density
    val mult = (pet.escalaDePalco * densidade).roundToInt().coerceAtLeast(1)

    val reduzir = LocalReduceMotion.current
    var quadro by remember(anim, pet) { mutableStateOf(0) }
    if (passo != null && !reduzir) {
        LaunchedEffect(anim, pet) {
            val nanosPorQuadro = 1_000_000_000L / passo.fps
            var acumulado = 0L
            var anterior = 0L
            while (true) {
                withFrameNanos { agora ->
                    if (anterior != 0L) acumulado += agora - anterior
                    anterior = agora
                }
                if (acumulado >= nanosPorQuadro) {
                    quadro = (quadro + acumulado / nanosPorQuadro).toInt() % passo.quadros
                    acumulado %= nanosPorQuadro
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(ALTURA_DO_PALCO)
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .semantics {
                contentDescription = "${pet.rotulo}, ${anim.rotulo}, pelagem ${pelagem.rotulo}"
            },
    ) {
        if (folhas == null || passo == null) {
            Box(Modifier.fillMaxWidth().height(ALTURA_DO_PALCO), contentAlignment = Alignment.Center) {
                Text(
                    "A arte deste companheiro não carregou.",
                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                )
            }
            return@Box
        }

        Canvas(Modifier.fillMaxWidth().height(ALTURA_DO_PALCO)) {
            val larguraPx = (pet.cw * mult).toFloat()
            val alturaPx = (pet.ch * mult).toFloat()
            val pesPx = (pet.pes * mult).toFloat()

            val chao = size.height * 0.72f
            val meia = size.width / 2f
            val larguraDoChao = (larguraPx * 2.4f).coerceAtMost(size.width * 0.5f)
            drawLine(
                color = Obsidian.borderMid,
                start = Offset(meia - larguraDoChao / 2f, chao),
                end = Offset(meia + larguraDoChao / 2f, chao),
                strokeWidth = 1f,
            )

            val esq = (meia - larguraPx / 2f).roundToInt()
            val topo = (chao - pesPx).roundToInt()

            scale(
                scaleX = if (pet.olhaParaDireita) 1f else -1f,
                scaleY = 1f,
                pivot = Offset(meia, chao),
            ) {
                drawImage(
                    image = folhas[anim] ?: return@scale,
                    srcOffset = IntOffset(
                        quadro * pet.quadroW + pet.cx,
                        passo.linha * pet.quadroW + pet.cy,
                    ),
                    srcSize = IntSize(pet.cw, pet.ch),
                    dstOffset = IntOffset(esq, topo),
                    dstSize = IntSize(larguraPx.toInt(), alturaPx.toInt()),
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}

@Composable
internal fun GestosDoPet(
    pet: Pet,
    escolhido: Anim,
    onEscolher: (Anim) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Anim.entries.filter { it in pet.passos }.forEach { a ->
            BotaoDeGesto(a.rotulo, a == escolhido) { onEscolher(a) }
        }
    }
}

@Composable
private fun BotaoDeGesto(rotulo: String, ativo: Boolean, onClick: () -> Unit) {
    val fonte = remember { MutableInteractionSource() }
    val sobMouse by fonte.collectIsHoveredAsState()
    val fundo by animateColorAsState(
        when {
            ativo -> Obsidian.active
            sobMouse -> Obsidian.hover
            else -> Obsidian.base
        },
        tween(120),
        label = "fundoDoGesto",
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(fundo)
            .border(1.dp, if (ativo) Obsidian.borderMid else Color.Transparent, RoundedCornerShape(8.dp))
            .hoverable(fonte)
            .clickScale(fonte)
            .clickable(interactionSource = fonte, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            rotulo,
            style = TextStyle(
                color = if (ativo) Obsidian.text1 else Obsidian.text2,
                fontSize = 12.sp,
            ),
        )
    }
}
