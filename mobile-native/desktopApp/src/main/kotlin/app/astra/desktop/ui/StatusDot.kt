package app.astra.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
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
    val cobre = remember(bordered, borderColor, cutoutColor) {
        cobreLegivelSobre(if (bordered) borderColor else cutoutColor)
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = Offset(s / 2f, s / 2f)
        val anel = if (bordered) s * ESPESSURA_DO_ARO else 0f
        if (bordered) drawCircle(borderColor, s / 2f, c)
        val r = s / 2f - anel
        when (status) {
            UserStatus.ONLINE -> drawPath(estrelaDeQuatroPontas(c, r), luz)
            UserStatus.IDLE -> {
                drawCircle(LARANJA_DA_LUA, r * RAIO_DA_LUZ_DA_LUA, c)
                val escala = r / RAIO_DA_LUA_NO_DESENHO
                withTransform({
                    translate(c.x, c.y)
                    rotate(GIRO_DA_LUA, pivot = Offset.Zero)
                    scale(escala, escala, pivot = Offset.Zero)
                    translate(-CENTRO_DA_LUA_NO_DESENHO, -CENTRO_DA_LUA_NO_DESENHO)
                }) {
                    drawPath(LuaDaMyna.caminho, cobre)
                }
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

fun tamanhoComAro(ponto: Dp): Dp = ponto / (1f - 2 * ESPESSURA_DO_ARO)

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

private object LuaDaMyna {
    val caminho: Path by lazy {
        val svg = checkNotNull(javaClass.getResourceAsStream("/icones/lua-eclipse.svg")) { "ícone da lua ausente" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val desenho = checkNotNull(Regex("""\sd="([^"]+)"""").find(svg)) { "ícone da lua sem desenho" }.groupValues[1]
        PathParser().parsePathString(desenho).toPath()
    }
}

private fun cobreLegivelSobre(fundo: Color): Color {
    val luzDoFundo = fundo.luminance()
    for (passo in 0..PASSOS_ATE_O_LARANJA) {
        val cobre = lerp(COBRE_DA_LUA, LARANJA_DA_LUA, passo / PASSOS_ATE_O_LARANJA.toFloat())
        if (contraste(cobre.luminance(), luzDoFundo) >= CONTRASTE_MINIMO_DA_LUA) return cobre
    }
    return LARANJA_DA_LUA
}

private fun contraste(a: Float, b: Float): Float = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)

internal val COBRE_DA_LUA = Color(0xFFB8442B)
internal val LARANJA_DA_LUA = Color(0xFFF48C3C)

private const val PINCA_DA_ESTRELA = 0.26f
private const val ESPESSURA_DO_ARO = 0.16f
private const val RAIO_DA_LUZ_DA_LUA = 0.92f
private const val RAIO_DA_LUA_NO_DESENHO = 9.75f
private const val CENTRO_DA_LUA_NO_DESENHO = 12f
private const val GIRO_DA_LUA = 180f
private const val PASSOS_ATE_O_LARANJA = 20
private const val CONTRASTE_MINIMO_DA_LUA = 3f
private const val ESCALA_CONTORNO = 0.90f
private const val ESPESSURA_DO_CONTORNO = 0.32f
private val ESPESSURA_MINIMA = 1.15.dp
private const val RAIO_DO_ECLIPSE = 0.86f
private const val RAIO_DO_DISCO = 0.50f
