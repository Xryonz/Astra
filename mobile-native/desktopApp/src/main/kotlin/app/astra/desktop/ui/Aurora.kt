package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.first
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

// Aurora viva em SkSL (Skia RuntimeEffect) — a assinatura visual do desktop.
// NEBULOSA FLUIDA (decisao do dono): ruido dobrado sobre si mesmo (domain
// warping) faz nuvens cosmicas se enrolarem devagar, sutis, so ambiente. Paleta
// obsidiana escura; texto soberano (paineis translucidos/vidro por cima).
// PERF: custo = (octaves fbm) x (niveis de warp). Aqui 4 octaves + warp de 2
// niveis (5 fbm/pixel). Se pesar em GPU integrada, baixar OCTAVES pra 3 ou o
// warp pra 1 nivel sao os diais. O relogio pausa sem foco (zero custo em bg).
private val AURORA_SKSL = """
uniform float uTime;
uniform float2 uSize;

// Ruido de valor com interpolacao suave (base de tudo).
float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i + float2(0.0, 0.0));
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
// Ruido fractal (soma de octaves) — da a textura de nuvem.
float fbm(float2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * noise(p);
        p *= 2.0;
        amp *= 0.5;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    // Corrige a proporcao pra nuvem nao esticar em telas largas.
    float2 p = uv * float2(uSize.x / uSize.y, 1.0) * 2.2;
    float t = uTime * 0.035; // deriva bem lenta

    // Domain warping: o campo se distorce por outro campo (2 niveis) — e isso
    // que faz a fumaca "se enrolar" em vez de so escorrer reto.
    float2 q = float2(fbm(p + float2(0.0, t)),
                      fbm(p + float2(5.2, 1.3 - t)));
    float2 r = float2(fbm(p + 3.0 * q + float2(1.7, 9.2) + 0.12 * t),
                      fbm(p + 3.0 * q + float2(8.3, 2.8) - 0.10 * t));
    float f = fbm(p + 2.6 * r);

    // Onde ha nuvem (densidade) e como a cor varia pelo campo distorcido.
    float density = smoothstep(0.32, 0.98, f);
    float3 silver = float3(0.56, 0.60, 0.72);
    float3 purple = float3(0.42, 0.37, 0.60);
    float3 amber  = float3(0.78, 0.65, 0.44);
    float3 neb = mix(purple, silver, clamp(q.x * 0.6 + 0.5, 0.0, 1.0));
    neb = mix(neb, amber, clamp(r.y * 0.55, 0.0, 1.0));

    // Base quase preta-azulada + nuvem somada BEM baixo (sutil, decisao do dono).
    float3 col = float3(0.022, 0.023, 0.052);
    col += neb * density * 0.15;
    // Fiapos brilhantes nas cristas do campo (vida sem levantar o brilho geral).
    col += silver * pow(density, 3.0) * 0.05;

    float vig = smoothstep(1.30, 0.32, length(uv - float2(0.5, 0.45)));
    col *= mix(0.70, 1.0, vig);
    return half4(col, 1.0);
}
"""

// Fundo chapado da paleta, caso o shader nao compile no runtime (typo de SkSL
// nunca deve tirar o shell do ar — so tira o brilho).
private val AURORA_FALLBACK = Color(0xFF06060E)

@Composable
fun Modifier.auroraBackground(): Modifier {
    val effect = remember { runCatching { RuntimeEffect.makeForShader(AURORA_SKSL.trimIndent()) }.getOrNull() }
        ?: return this.drawBehind { drawRect(AURORA_FALLBACK) }
    val builder = remember { RuntimeShaderBuilder(effect) }
    val windowInfo = LocalWindowInfo.current
    // Relogio de frames com PAUSA: janela sem foco = nenhum frame pedido (zero
    // CPU/GPU em segundo plano — guardrail do dono). O tempo acumula, entao a
    // aurora retoma de onde parou em vez de pular.
    val timeSec by produceState(0f, windowInfo) {
        var acc = 0f
        while (true) {
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            var last = withFrameNanos { it }
            while (windowInfo.isWindowFocused) {
                withFrameNanos { now ->
                    acc += (now - last) / 1_000_000_000f
                    last = now
                    value = acc
                }
            }
        }
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
