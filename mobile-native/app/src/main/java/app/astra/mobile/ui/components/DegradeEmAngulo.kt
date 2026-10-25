package app.astra.mobile.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal data class DegradeEmAngulo(val cores: List<Color>, val graus: Float) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val (inicio, fim) = pontasDoDegrade(graus, size.width, size.height)
        return LinearGradientShader(inicio, fim, cores)
    }
}

private fun pontasDoDegrade(graus: Float, largura: Float, altura: Float): Pair<Offset, Offset> {
    val rad = Math.toRadians(graus.toDouble())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val metade = (abs(largura * dx) + abs(altura * dy)) / 2f
    val cx = largura / 2f
    val cy = altura / 2f
    return Offset(cx - dx * metade, cy - dy * metade) to Offset(cx + dx * metade, cy + dy * metade)
}
