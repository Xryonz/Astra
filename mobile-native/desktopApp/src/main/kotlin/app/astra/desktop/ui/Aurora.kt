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
import kotlin.math.sqrt
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
// Quanto o fbm BALANCA em relacao a propria escala, por numero de oitavas: soma
// quadratica das amplitudes (o desvio, ja que as oitavas sao ~independentes)
// dividida pela soma linear (o alcance). 1 oitava = 1.0; 3 oitavas = 0.655, ou
// seja o campo de 1 oitava e 1.53x mais largo relativo ao proprio intervalo.
// E por isso que uma curva de contraste fixa nao serve pras tres qualidades.
private fun fbmSigmaRel(octaves: Int): Double {
    var sumSq = 0.0
    var sumLin = 0.0
    var a = 0.5
    repeat(octaves) {
        sumSq += a * a
        sumLin += a
        a *= 0.5
    }
    return sqrt(sumSq) / sumLin
}

private fun auroraSksl(octaves: Int): String {
    // Normaliza pela soma de amplitudes da qualidade ALTA (3 oitavas) -> HIGH fica
    // IDENTICA a aurora ja validada (inv=1.0) e as qualidades menores so sobem o
    // brilho pra bater (senao LOW ficaria escura, parece bug).
    val ref = 1.0 - Math.pow(0.5, 3.0)
    val inv = ref / (1.0 - Math.pow(0.5, octaves.toDouble()))
    // Inclinacao da curva das estrias, ajustada ao quanto o campo BALANCA nesta
    // qualidade. Menos oitavas = ruido de uma frequencia so = balanca muito mais
    // em relacao a propria escala (sigmaRel: 0.65 com 3 oitavas, 1.0 com 1). Sem
    // isto a mesma curva recebe campos de larguras diferentes e o LOW satura em
    // placas. 12.5 = inclinacao que reproduz no centro a do smoothstep validado.
    val steep = 12.5 * (fbmSigmaRel(3) / fbmSigmaRel(octaves))
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
// CORTE 8 — a causa da "borda reta diagonal", e a que sobreviveu a todas as
// tentativas anteriores (oitavas, relogio, teto de brilho, dither de 8 bits).
//
// exp(-abs(d) * k) NAO e suave em d = 0: tem uma CUSPIDE. A derivada salta de
// +k pra -k instantaneamente — descontinuidade de PRIMEIRA derivada, ou seja um
// vinco. O olho detecta isso muito melhor do que detecta diferenca de valor
// (bandas de Mach / inibicao lateral), entao o vinco aparece como um risco
// nitido mesmo com o brilho variando pouco ali.
//
// Nos feixes o argumento do abs e uma funcao LINEAR de uv -> o vinco e uma
// LINHA RETA; a direcao e a do feixe -> DIAGONAL; e ele varre a tela com
// sin(ang*2) -> "de vez em quando". Por isso nenhuma correcao anterior pegava:
// nao era quantizacao nem saturacao, era geometria.
//
// softAbs arredonda a ponta num raio ~sqrt(EPS) (aqui ~0.008 em uv, uns poucos
// pixels) e e C-infinito. Longe de zero e indistinguivel de abs(), entao o
// formato e o alcance dos feixes/cortinas continuam os validados — some so o
// risco.
float softAbs(float d) { return sqrt(d * d + 6e-5); }

float curtain(float2 uv, float yC, float seed, float2 flow) {
    float n = fbm(float2(uv.x * 2.6 + seed, seed * 3.1) + flow);
    float d = uv.y - (yC + (n - 0.5) * 0.30);
    // Borda superior mais dura (34 vs 10) -> cortina com contorno definido.
    // CORTE 8: era exp(-abs(d) * (d < 0.0 ? 34.0 : 10.0)) — DOIS vincos no mesmo
    // ponto. O abs faz a derivada saltar de +k pra -k em d=0, e o ternario troca a
    // propria inclinacao de degrau. softAbs arredonda a ponta e o mix troca a
    // inclinacao numa faixa estreita em vez de num salto. Ver CORTE 8 nos feixes.
    float kd = mix(10.0, 34.0, smoothstep(0.006, -0.006, d));
    float body = exp(-softAbs(d) * kd);
    // Estrias verticais crispadas: o MESMO fbm de raios, re-shapeado com contraste
    // alto — definicao sem custo extra de ruido.
    //
    // CORTE 6 (o "de vez em quando a iluminacao nao segue um padrao"): isto era
    // smoothstep(0.30, 0.78, r), e smoothstep SATURA por definicao — fora da faixa
    // a derivada e ZERO, ou seja platô. Com 3 oitavas o campo tem sigma ~0.115 em
    // torno de 0.4375, entao a borda de baixo (0.30) cai a 1.2 sigma: ~12% da tela
    // ja vivia grudada no piso, chapada, com borda dura onde cruzava. Em LOW (1
    // oitava, campo 1.53x mais largo) virava metade da tela, e placas inteiras
    // trocavam de nivel de uma vez conforme o flow andava — a luz mudava em blocos
    // que nao acompanhavam a cortina.
    //
    // A logistica tem a MESMA inclinacao no centro (12.5/4 = 3.125 = a do
    // smoothstep validado) e o mesmo valor em r=0.54, mas nunca encosta no piso:
    // derivada sempre > 0, entao nao existe platô nem borda dura em qualidade
    // nenhuma. Custo: 1 exp no lugar de 1 smoothstep.
    float r = fbm(float2(uv.x * 11.0 - seed, 2.7 + seed) + flow * 1.6);
    float rays = 0.30 + 0.70 / (1.0 + exp(-$steep * (r - 0.54)));
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
    // softAbs, nao abs: e AQUI que nascia a borda reta diagonal (ver CORTE 8).
    float beam1 = exp(-softAbs(uv.x * 0.8 - uv.y * 0.45 - 0.18 - 0.45 * sin(ang * 2.0 + 0.7)) * 5.5);
    float beam2 = exp(-softAbs(uv.x * 0.6 + uv.y * 0.55 - 0.45 - 0.50 * sin(ang * 3.0 + 3.9)) * 7.0) * 0.6;
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

    // CORTE 5 (iluminacao "sem padrao"): o teto era min(aur, 0.30) — duro. Como
    // aur chega a ~1.0 no pico (cortinas ~2.4 * 0.22, depois feixes *1.72 e o
    // wf), regioes GRANDES batiam no limite e viravam um PLATO chapado, com
    // borda dura onde saturava: o trajeto seguia certo, mas a luz parecia
    // "cortada". Joelho suave: linear ate 0.22 (miolo identico ao validado) e
    // dai rola assintotico ate 0.30 — sem nunca chapar. Branchless, 1 exp.
    float knee = 0.22;
    float ceiling = 0.30;
    float over = max(aur - knee, 0.0);
    float lum = min(aur, knee) + (ceiling - knee) * (1.0 - exp(-over / (ceiling - knee)));
    float3 col = uVoid + acc * lum;

    // CORTE 7 (banding): com fundo PRETO PURO a aurora inteira vive entre 0 e
    // ~0.25, ou seja nos 64 primeiros valores de 256 de um display de 8 bits. Um
    // gradiente suave espremido em 64 degraus vira FAIXAS chapadas de borda dura,
    // e quando o campo deriva o contorno de cada faixa salta de posicao de uma vez
    // — a luz muda em placas que nao acompanham a cortina. Independe de oitavas,
    // por isso sobrevivia em qualidade ALTA.
    //
    // Dither: meio degrau (1/255) de ruido branco por pixel antes da quantizacao.
    // O degrau vira transicao granulada, que o olho integra como gradiente. O hash
    // usa fragCoord CRU e sem tempo: padrao fixo na tela (dither temporal ficaria
    // fervendo) e a perda de precisao do sin em coordenada grande aqui e util —
    // queremos ruido branco mesmo.
    col += (hashn(fragCoord) - 0.5) / 255.0;
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

// pulse: 0..1, lido no draw (nao recompoe). No login o ceu "respira" — o brilho
// sobe ~15% e decai. So MODULA o uniform uAccent (Kotlin), nao toca o SkSL: como
// col = uVoid + uAccent*..*lum, escalar o accent clareia a aurora proporcional.
@Composable
fun Modifier.auroraBackground(pulse: () -> Float = { 0f }): Modifier {
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
                    // CORTE 3 (ao voltar pra aba): alt-tab / outro monitor NAO mexem em
                    // windowVisible nem isMinimized -> 'active' segue true, mas o SO para
                    // de entregar frames pra janela ocluida. Na volta, o 1o frame traz
                    // 'now' varios SEGUNDOS a frente -> dt gigante empurraria o tempo num
                    // salto = a aurora "pula" (o corte "do nada"). Clampo o dt em 50ms
                    // (~3 frames): pior caso a aurora so atrasa um tico imperceptivel.
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    acc += dt
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
        // Pulso de login lido AQUI (draw phase): clareia o accent em ate 15% e some.
        val boost = 1f + 0.15f * pulse().coerceIn(0f, 1f)
        builder.uniform("uAccent", accent.red * boost, accent.green * boost, accent.blue * boost)
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
