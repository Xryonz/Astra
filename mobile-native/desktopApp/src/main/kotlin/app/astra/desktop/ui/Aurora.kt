package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.flow.first
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

// Aurora viva em SkSL (Skia RuntimeEffect) — a assinatura visual do desktop.
// PORTA DA AURORA DO MOBILE (StarField.kt AURORA_AGSL; AGSL e SkSL sao o mesmo
// dialeto do Skia). Cortinas organicas por ruido fractal (FBM), prata sobre o
// void. O TEMPO ANDA NUM CIRCULO no espaco de ruido (cos/sin * raio), entao o
// loop fecha PERFEITO sem salto — corrige o "cortada" do nebula anterior (tempo
// linear crescia sem fim -> dominio do ruido estourava a precisao do float e a
// animacao travava). Tilt/toque do mobile ficaram de fora (sao de celular:
// acelerometro/dedo). PERF: value-noise ALU-only, 3 oitavas, 2 cortinas.
private val AURORA_SKSL = """
uniform float uTime;
uniform float2 uSize;

float hashn(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}
float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hashn(i), hashn(i + float2(1.0, 0.0)), u.x),
               mix(hashn(i + float2(0.0, 1.0)), hashn(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int k = 0; k < 3; k++) {
        v += a * vnoise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}
float curtain(float2 uv, float yC, float seed, float2 flow) {
    float n = fbm(float2(uv.x * 2.6 + seed, seed * 3.1) + flow);
    float d = uv.y - (yC + (n - 0.5) * 0.30);
    float body = exp(-abs(d) * (d < 0.0 ? 26.0 : 9.0));
    float rays = 0.55 + 0.45 * fbm(float2(uv.x * 7.0 - seed, 2.7 + seed) + flow * 1.4);
    return body * rays * (0.5 + n);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    // Tempo em CIRCULO -> loop sem emenda (a chave do "sem salto" do mobile).
    float ang = uTime * 0.1;
    float2 flow1 = float2(cos(ang), sin(ang)) * 1.6;
    float2 flow2 = float2(cos(ang * 2.0 + 2.1), sin(ang * 2.0 + 2.1)) * 1.1;
    float c1 = curtain(uv, 0.24, 0.0, flow1);
    float c2 = curtain(uv, 0.46, 5.3, flow2) * 0.6;
    float fall = 1.0 - smoothstep(0.05, 0.9, uv.y);
    float aur = (c1 + c2) * fall * 0.16;

    // Prata (Obsidian.accent #D4D8E0) somada sobre o void; opaco (o desktop
    // desenha a aurora como fundo inteiro, nao como overlay transparente).
    float3 accent = float3(0.831, 0.847, 0.878);
    float3 col = float3(0.024, 0.024, 0.055) + accent * min(aur, 0.30);
    return half4(col, 1.0);
}
"""

// Periodo do loop: ang = uTime*0.1 fecha o circulo em uTime = 2*PI/0.1 = 20*PI.
// flow2 usa ang*2 -> fecha 2 voltas no mesmo intervalo. Enrolar o tempo do
// desktop nesse periodo torna o loop imperceptivel (o quadro em uTime=0 e
// identico ao de uTime=AURORA_LOOP).
private const val AURORA_LOOP = 62.831853f

// Quadro estatico agradavel pro "reduzir movimento" (cortinas bem postas).
private const val AURORA_STILL = 12f

// Fundo chapado da paleta, caso o shader nao compile no runtime (typo de SkSL
// nunca deve tirar o shell do ar — so tira o brilho).
private val AURORA_FALLBACK = Color(0xFF06060E)

@Composable
fun Modifier.auroraBackground(): Modifier {
    val effect = remember { runCatching { RuntimeEffect.makeForShader(AURORA_SKSL.trimIndent()) }.getOrNull() }
        ?: return this.drawBehind { drawRect(AURORA_FALLBACK) }
    val builder = remember { RuntimeShaderBuilder(effect) }
    val reduceMotion = LocalReduceMotion.current
    // Gate por VISIBILIDADE (nao foco): rememberUpdatedState pra ler o valor mais
    // fresco dentro do loop sem reiniciar o produceState (o que zeraria o tempo).
    val active = rememberUpdatedState(LocalWindowActive.current)
    // Relogio de frames com PAUSA: minimizada/na bandeja = nenhum frame pedido
    // (zero CPU/GPU em segundo plano — guardrail do dono). Enquanto VISIVEL segue
    // animando, mesmo com um popup/menu focavel aberto por cima (era o "cortada"
    // de vez em quando: gatear por foco congelava a cada menu). O tempo acumula e
    // ENROLA no periodo do loop (AURORA_LOOP): mantem o dominio do ruido limitado
    // (sem estouro de precisao) e o giro fecha sem salto.
    // Reduzir movimento (Settings): congela num quadro fixo — aurora parada, sem
    // pedir frame nenhum (a chave e restartar o produceState quando o pref muda).
    val timeSec by produceState(0f, reduceMotion) {
        if (reduceMotion) {
            value = AURORA_STILL
            return@produceState
        }
        var acc = 0f
        while (true) {
            snapshotFlow { active.value }.first { it }
            var last = withFrameNanos { it }
            while (active.value) {
                withFrameNanos { now ->
                    acc += (now - last) / 1_000_000_000f
                    if (acc >= AURORA_LOOP) acc -= AURORA_LOOP
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
