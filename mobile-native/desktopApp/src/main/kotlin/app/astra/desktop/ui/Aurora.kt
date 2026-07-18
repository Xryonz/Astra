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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import app.astra.desktop.ui.theme.Obsidian
import kotlinx.coroutines.flow.first
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

// Aurora viva em SkSL (Skia RuntimeEffect) — a assinatura visual do desktop.
// PORTA DA AURORA DO MOBILE (StarField.kt AURORA_AGSL; AGSL e SkSL sao o mesmo
// dialeto do Skia). Cortinas organicas por ruido fractal (FBM), prata sobre o
// void. O TEMPO ANDA NUM CIRCULO no espaco de ruido (cos/sin * raio), entao o
// loop fecha PERFEITO sem salto — corrige o "cortada" do nebula anterior (tempo
// linear crescia sem fim -> dominio do ruido estourava a precisao do float e a
// animacao travava). Tilt/toque do mobile ficaram de fora (sao de celular:
// acelerometro/dedo). PERF: value-noise ALU-only, 3 oitavas, 2 cortinas.
// octaves = qualidade (Settings > Desempenho): mais oitavas = ruido mais rico e
// mais caro. SkSL exige bound de loop constante -> a contagem entra no source e
// recompila-se uma variante por nivel. Normaliza-se por (1-0.5^oct) pra aurora
// manter o mesmo brilho em qualquer qualidade (senao LOW fica visivelmente mais
// escura, parece bug). accent + void ENTRAM COMO UNIFORMS (uAccent/uVoid) pra a
// aurora seguir o tema de Aparencia ao vivo — antes eram cravados (#D4D8E0 sobre
// #06060E) e nao recoloriam. So o octaves recompila; cor troca por uniform (barato).
private fun auroraSksl(octaves: Int): String {
    // Normaliza pela soma de amplitudes da qualidade ALTA (3 oitavas) -> HIGH fica
    // IDENTICA a aurora ja validada (inv=1.0) e as qualidades menores so sobem o
    // brilho pra bater (senao LOW ficaria escura, parece bug).
    val ref = 1.0 - Math.pow(0.5, 3.0)
    val inv = ref / (1.0 - Math.pow(0.5, octaves.toDouble()))
    return """
uniform float uTime;
uniform float2 uSize;
uniform float3 uAccent;
uniform float3 uVoid;

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
    for (int k = 0; k < $octaves; k++) {
        v += a * vnoise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v * $inv;
}
float curtain(float2 uv, float yC, float seed, float2 flow) {
    float n = fbm(float2(uv.x * 2.6 + seed, seed * 3.1) + flow);
    float d = uv.y - (yC + (n - 0.5) * 0.30);
    // Borda superior mais dura (34 vs 26) -> cortina com contorno definido.
    float body = exp(-abs(d) * (d < 0.0 ? 34.0 : 10.0));
    // Estrias verticais crispadas: o MESMO fbm de raios, re-shapeado por
    // smoothstep (contraste alto) — definicao sem custo extra de ruido.
    float r = fbm(float2(uv.x * 11.0 - seed, 2.7 + seed) + flow * 1.6);
    float rays = 0.30 + 0.70 * smoothstep(0.30, 0.78, r);
    return body * rays * (0.5 + n);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    // Tempo em CIRCULO -> loop sem emenda (a chave do "sem salto" do mobile).
    // Todo termo temporal usa multiplos INTEIROS de ang -> fecha no periodo.
    float ang = uTime * 0.1;
    float2 flow1 = float2(cos(ang), sin(ang)) * 1.6;
    float2 flow2 = float2(cos(ang * 2.0 + 2.1), sin(ang * 2.0 + 2.1)) * 1.1;
    float c1 = curtain(uv, 0.24, 0.0, flow1);
    float c2 = curtain(uv, 0.46, 5.3, flow2) * 0.6;
    float fall = 1.0 - smoothstep(0.05, 0.9, uv.y);
    float aur = (c1 + c2) * fall * 0.22;

    // Bandas de luz diagonais varrendo as cortinas. Posicao oscila com
    // sin(ang*N) (periodico); so exp/abs, custo ~zero de ALU.
    float beam1 = exp(-abs(uv.x * 0.8 - uv.y * 0.45 - 0.18 - 0.45 * sin(ang * 2.0 + 0.7)) * 5.5);
    float beam2 = exp(-abs(uv.x * 0.6 + uv.y * 0.55 - 0.45 - 0.50 * sin(ang * 3.0 + 3.9)) * 7.0) * 0.6;
    float beams = beam1 + beam2;
    aur *= 1.0 + 0.45 * beams;
    aur += beams * fall * 0.02;

    // Respiracao: pulso global lento (periodo = AURORA_LOOP/3 ~ 21s).
    aur *= 0.85 + 0.15 * sin(ang * 3.0 + 0.9);

    // Variacao sutil do accent pelo campo: intensidade (+-12%, pre-clamp) e
    // calor (+-6% nos canais do PROPRIO accent — nenhuma cor nova). 1 fbm
    // extra de baixa frequencia (5 chamadas fbm no total).
    float wf = fbm(uv * float2(1.3, 2.0) + flow1 * 0.35 + float2(7.7, 3.3));
    aur *= 0.88 + 0.24 * wf;
    float3 acc = uAccent * mix(float3(0.94, 0.99, 1.05), float3(1.06, 1.00, 0.94), wf);

    float3 col = uVoid + acc * min(aur, 0.30);
    return half4(col, 1.0);
}
"""
}

// Periodo do loop: ang = uTime*0.1 fecha o circulo em uTime = 2*PI/0.1 = 20*PI.
// flow2 usa ang*2 -> fecha 2 voltas no mesmo intervalo. Enrolar o tempo do
// desktop nesse periodo torna o loop imperceptivel (o quadro em uTime=0 e
// identico ao de uTime=AURORA_LOOP).
private const val AURORA_LOOP = 62.831853f

// Quadro estatico agradavel pro "reduzir movimento" (cortinas bem postas).
private const val AURORA_STILL = 12f

@Composable
fun Modifier.auroraBackground(): Modifier {
    val render = LocalRenderPrefs.current
    // Cor do tema (Aparencia): a aurora glow no accent sobre o void escolhido.
    // Ler aqui torna o modifier reativo — troca de tema recompoe e repinta.
    val accent = Obsidian.accent
    val voidC = Obsidian.void
    // Recompila so quando a qualidade (octaves) muda — barato, raro.
    val effect = remember(render.auroraOctaves) {
        runCatching { RuntimeEffect.makeForShader(auroraSksl(render.auroraOctaves).trimIndent()) }.getOrNull()
    } ?: return this.drawBehind { drawRect(voidC) } // shader falhou -> void chapado do tema
    val builder = remember(effect) { RuntimeShaderBuilder(effect) }
    val reduceMotion = LocalReduceMotion.current
    // Gate por VISIBILIDADE (nao foco): rememberUpdatedState pra ler o valor mais
    // fresco dentro do loop sem reiniciar o produceState (o que zeraria o tempo).
    val active = rememberUpdatedState(LocalWindowActive.current)
    // Teto de FPS (Settings > Desempenho): limita a taxa de REDESENHO do shader
    // (emitir menos 'value' = menos invocacoes por segundo). O tempo acumula em
    // toda frame (acc), so a emissao e afinada — a velocidade da animacao nao muda.
    val fpsCap = rememberUpdatedState(render.fpsCap)
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
        var lastEmit = 0L
        while (true) {
            snapshotFlow { active.value }.first { it }
            var last = withFrameNanos { it }
            while (active.value) {
                withFrameNanos { now ->
                    acc += (now - last) / 1_000_000_000f
                    if (acc >= AURORA_LOOP) acc -= AURORA_LOOP
                    last = now
                    val cap = fpsCap.value
                    val minInterval = if (cap <= 0) 0L else 1_000_000_000L / cap
                    if (minInterval == 0L || now - lastEmit >= minInterval) {
                        lastEmit = now
                        value = acc
                    }
                }
            }
        }
    }
    val paint = remember { Paint() }
    // Guarda o shader que ESTE modifier criou no ultimo frame, pra fecha-lo na mao
    // antes de criar o proximo (ver CORTE 2 abaixo). Array de 1 = celula mutavel
    // barata que sobrevive entre frames sem recompor.
    val lastShader = remember { arrayOfNulls<Shader>(1) }
    return drawBehind {
        // CORTE 1 (ao redimensionar): num frame da transicao a janela reporta 0 ->
        // uv = fragCoord/0 = NaN -> half4 com NaN -> quadro preto. Sem tamanho
        // valido, pinta so o void do tema e sai (nada de shader).
        if (size.width <= 0f || size.height <= 0f) { drawRect(voidC); return@drawBehind }
        builder.uniform("uTime", timeSec)
        builder.uniform("uSize", size.width, size.height)
        builder.uniform("uAccent", accent.red, accent.green, accent.blue)
        builder.uniform("uVoid", voidC.red, voidC.green, voidC.blue)
        // CORTE 2 (parado/idle): makeShader() aloca um Shader Skia NATIVO por frame.
        // Sem fechar, eles so somem quando o GC roda o cleaner num lote -> engasgo
        // periodico que parecia a aurora "cortando" do nada. Fecho o do frame
        // anterior (a celula ainda segura 1 ref; o SkPaint tem a dele) ANTES de
        // trocar -> liberacao deterministica, sem surto de GC.
        lastShader[0]?.close()
        val shader = builder.makeShader()
        lastShader[0] = shader
        // Skia Shader nao e o Shader do Compose -> desenha direto no canvas nativo.
        paint.shader = shader
        drawIntoCanvas { it.nativeCanvas.drawRect(Rect.makeWH(size.width, size.height), paint) }
    }
}
