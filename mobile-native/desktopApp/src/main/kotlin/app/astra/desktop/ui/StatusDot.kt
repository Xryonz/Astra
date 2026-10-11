package app.astra.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Obsidian

enum class UserStatus { ONLINE, IDLE, DND, INVISIBLE, OFFLINE }

fun userStatus(raw: String?): UserStatus = when (raw?.uppercase()) {
    "ONLINE" -> UserStatus.ONLINE
    "IDLE" -> UserStatus.IDLE
    "DND" -> UserStatus.DND
    "INVISIBLE" -> UserStatus.INVISIBLE
    else -> UserStatus.OFFLINE
}

@Composable
fun StatusDot(
    status: UserStatus,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    bordered: Boolean = false,
    borderColor: Color = Obsidian.base,
    cutoutColor: Color = Obsidian.base,
) {
    val luz = Obsidian.text1
    val apagada = Obsidian.text3
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = Offset(s / 2f, s / 2f)
        val anel = if (bordered) s * 0.16f else 0f
        if (bordered) drawCircle(borderColor, s / 2f, c)
        val r = s / 2f - anel
        when (status) {
            UserStatus.ONLINE -> drawPath(estrelaDeQuatroPontas(c, r), luz)
            UserStatus.IDLE -> {
                drawPath(estrelaDeQuatroPontas(c, r), luz)
                drawCircle(
                    cutoutColor,
                    r * RAIO_DA_SOMBRA,
                    Offset(c.x + r * DESVIO_DA_SOMBRA, c.y - r * DESVIO_DA_SOMBRA),
                )
            }
            UserStatus.DND -> {
                drawCircle(luz, r * RAIO_DO_ECLIPSE, c)
                drawCircle(cutoutColor, r * RAIO_DO_DISCO, c)
            }
            UserStatus.INVISIBLE, UserStatus.OFFLINE -> drawPath(
                estrelaDeQuatroPontas(c, r * ESCALA_CONTORNO),
                apagada,
                style = Stroke(
                    width = maxOf(r * ESPESSURA_DO_CONTORNO, ESPESSURA_MINIMA.toPx()),
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

fun estrelaDeQuatroPontas(centro: Offset, raio: Float): Path {
    val p = raio * PINCA_DA_ESTRELA
    return Path().apply {
        moveTo(centro.x, centro.y - raio)
        quadraticTo(centro.x + p, centro.y - p, centro.x + raio, centro.y)
        quadraticTo(centro.x + p, centro.y + p, centro.x, centro.y + raio)
        quadraticTo(centro.x - p, centro.y + p, centro.x - raio, centro.y)
        quadraticTo(centro.x - p, centro.y - p, centro.x, centro.y - raio)
        close()
    }
}

private const val PINCA_DA_ESTRELA = 0.26f
private const val RAIO_DA_SOMBRA = 0.62f
private const val DESVIO_DA_SOMBRA = 0.42f
private const val ESCALA_CONTORNO = 0.90f
private const val ESPESSURA_DO_CONTORNO = 0.32f
private val ESPESSURA_MINIMA = 1.15.dp
private const val RAIO_DO_ECLIPSE = 0.86f
private const val RAIO_DO_DISCO = 0.50f
