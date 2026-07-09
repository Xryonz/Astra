package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

// Aurora viva em SkSL (Skia RuntimeEffect) — a assinatura visual do desktop.
// Mesma familia do RuntimeShader/AGSL do Android, mas rodando no Skia da JVM.
// Tres bandas senoidais com glow + vinheta sobre o void obsidiana.
private val AURORA_SKSL = """
uniform float uTime;
uniform float2 uSize;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float3 col = float3(0.024, 0.024, 0.055);

    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float speed = 0.04 + fi * 0.018;
        float y = 0.28 + fi * 0.20
            + 0.07 * sin(uv.x * 3.1 + uTime * speed * 6.2831 + fi * 2.1)
            + 0.03 * sin(uv.x * 7.3 - uTime * speed * 4.0 + fi);
        float d = abs(uv.y - y);
        float glow = 0.010 / (d * d + 0.012);
        float3 tint = (i == 0) ? float3(0.58, 0.61, 0.72)
                    : (i == 1) ? float3(0.44, 0.38, 0.62)
                               : float3(0.79, 0.66, 0.43);
        col += tint * glow * 0.085;
    }

    float vig = smoothstep(1.25, 0.35, length(uv - float2(0.5, 0.45)));
    col *= mix(0.72, 1.0, vig);
    return half4(col, 1.0);
}
"""

@Composable
fun Modifier.auroraBackground(): Modifier {
    val effect = remember { RuntimeEffect.makeForShader(AURORA_SKSL.trimIndent()) }
    val builder = remember { RuntimeShaderBuilder(effect) }
    // Relogio de frames: le-se dentro do drawBehind -> so o draw invalida por
    // frame (a composicao nao recompoe).
    val timeSec by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
    val paint = remember { Paint() }
    return drawBehind {
        builder.uniform("uTime", timeSec)
        builder.uniform("uSize", size.width, size.height)
        // Skia Shader nao e o Shader do Compose -> desenha direto no canvas nativo.
        paint.shader = builder.makeShader()
        drawIntoCanvas { it.nativeCanvas.drawRect(Rect.makeWH(size.width, size.height), paint) }
    }
}
