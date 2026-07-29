package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.update.UpdateService
import app.astra.desktop.update.UpdateState
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zed.rainxch.rikkaui.components.ui.progress.Progress
import zed.rainxch.rikkaui.components.ui.progress.ProgressAnimation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---- Gate de boot (estilo Discord): janelinha que verifica a versao ----
// Logo do Astra no centro com estrelas orbitando (sensacao de carregando) sobre o
// ceu do app. A barra segmentada embaixo mostra o download quando ha um; senao
// varre. Atualizado/falha/timeout seguem pro app. Janela pequena e frameless.
@Composable
fun UpdaterGate(updater: UpdateService, reduceMotion: Boolean, onDone: () -> Unit) {
    val st by updater.state.collectAsState()

    // Tempo minimo de tela (pedido do dono: mais demorada pra aproveitar a animacao):
    // mesmo com a checagem instantanea o gate fica no ar ~4.8s, tempo de sobra pra ver
    // a constelacao se formar e a barra encher com as palavras espaciais antes de cair
    // no app. So conta pro caminho "atualizado".
    val gateStart = remember { System.currentTimeMillis() }
    val holdMs = 4800L
    suspend fun holdThenDone() {
        val left = holdMs - (System.currentTimeMillis() - gateStart)
        if (left > 0) delay(left)
        onDone()
    }

    // Gate silencioso: falha de rede vira "atualizado" e segue (sem "nao deu").
    LaunchedEffect(Unit) { updater.check(silent = true) }
    // Resolucao TOTALMENTE automatica: achou versao nova -> baixa sozinho (sem
    // clique) -> quando pronto, reinicia e instala. Atualizado/falha seguem pro
    // app. Basta ter QUALQUER versao instalada: o resto anda sem GitHub na mao.
    LaunchedEffect(st) {
        when (val s = st) {
            is UpdateState.Available -> updater.downloadAndStage(s)
            is UpdateState.UpToDate -> holdThenDone()
            is UpdateState.Failed -> { delay(1300); onDone() }
            is UpdateState.Ready -> { delay(700); updater.restartToInstall() }
            else -> {}
        }
    }
    // Rede de seguranca: travou verificando (offline lento) -> segue em 8s.
    LaunchedEffect(Unit) { delay(8_000); if (updater.state.value is UpdateState.Checking) onDone() }

    // Entrada "constelacao se forma", toca UMA vez ao abrir o gate: as 14
    // estrelas do anel nascem espalhadas e convergem pra orbita (ver
    // RotatingStarsLogo). null = direto no estado final, sem stagger — e o que
    // reduceMotion pede e tambem o default dos outros lugares que usam o logo
    // (login/onboarding), que nao tocam essa entrada.
    val entrance: State<Float>? = if (reduceMotion) null else {
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { started = true }
        animateFloatAsState(
            targetValue = if (started) 1f else 0f,
            animationSpec = tween(2600, easing = LinearEasing),
            label = "gateEntrance",
        )
    }

    // Preenchimento da barra fina: no download e o progresso REAL; no resto
    // (verificando / atualizado) uma barra SINTETICA que enche devagar ao longo do
    // hold — so pra dar a sensacao de "algo acontecendo" enquanto a animacao roda.
    val realProgress = (st as? UpdateState.Downloading)?.progress
    val syntheticFill = run {
        var go by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { go = true }
        animateFloatAsState(
            targetValue = if (go) 1f else 0f,
            animationSpec = tween(holdMs.toInt(), easing = LinearEasing),
            label = "gateFill",
        )
    }
    val barProgress = realProgress ?: syntheticFill.value
    // Palavra acima da barra: nos estados reais, o que esta havendo; no hold comum, uma
    // palavra "espacial" que avanca junto com o preenchimento (pedido do dono).
    val statusLabel = when (val s = st) {
        is UpdateState.Available   -> "nova versao ${s.version}"
        is UpdateState.Downloading -> "baixando ${s.version} · ${(s.progress * 100).toInt()}%"
        is UpdateState.Ready       -> "reiniciando pra aplicar"
        is UpdateState.Failed      -> s.reason
        else                       -> spaceWord(barProgress)
    }

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Obsidian.void)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Fundo: o gate era logo + preto liso. Ganha o MESMO ceu do app (estrelas
        // fixas, piscar e meteoros) e um halo atras do planeta — reuso do StarField
        // que ja existe, nao arte nova. Aurora ficou de fora de proposito: e um
        // shader por pixel e isto e a tela de BOOT, tem que abrir na hora.
        StarField(Modifier.fillMaxSize())
        // Halo: separa o planeta do fundo e dá profundidade. Um gradiente radial no
        // draw, custo de um retangulo.
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            Obsidian.accent.copy(alpha = 0.10f),
                            Obsidian.accent.copy(alpha = 0.03f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height * 0.34f),
                        radius = size.minDimension * 0.62f,
                    ),
                )
            },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 24.dp).fillMaxWidth(),
        ) {
            // Logo TRANSPARENTE (astra-glyph.png): so o planeta branco, sem o quadrado
            // preto que poluia o ceu do gate. As estrelas continuam orbitando o corpo.
            RotatingStarsLogo(reduceMotion, entrance = entrance, planetRes = "astra-glyph.png")
            Spacer(Modifier.height(18.dp))
            Text(
                "Astra",
                style = TextStyle(color = Obsidian.text1, fontSize = 22.sp, fontFamily = DmSerif),
                // Titulo resolve por ultimo (1.5..2.0 do virtual da entrada): alpha lido
                // dentro do graphicsLayer, nao no corpo — nao recompoe a cada frame.
                modifier = Modifier.graphicsLayer {
                    val ms = (entrance?.value ?: 1f) * 2000f
                    alpha = ((ms - 1500f) / 500f).coerceIn(0f, 1f)
                },
            )
            Spacer(Modifier.height(16.dp))
            ThinProgress(barProgress, statusLabel)
        }
    }
}

// Barra de progresso FINA e minimalista (pedido do dono): um trilho de 2dp que
// enche da esquerda pra direita, com uma palavra "espacial" centralizada acima.
// No download o preenchimento e o progresso REAL; no hold comum e a barra sintetica
// que sobe devagar. Canvas (nao Box) pra uma unica passada numa tela que abre na hora.
@Composable
private fun ThinProgress(progress: Float, label: String) {
    Column(
        Modifier.fillMaxWidth(0.62f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, letterSpacing = 0.4.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(9.dp))
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val h = size.height
            val cr = CornerRadius(h / 2f)
            drawRoundRect(color = Obsidian.raised, size = size, cornerRadius = cr)
            val w = (size.width * progress).coerceIn(0f, size.width)
            if (w > 0f) {
                drawRoundRect(color = Obsidian.accent, size = Size(w, h), cornerRadius = cr)
                // Brilho discreto na cabeca do preenchimento (toque editorial).
                drawCircle(color = Obsidian.accent.copy(alpha = 0.5f), radius = h * 1.6f, center = Offset(w, h / 2f))
            }
        }
    }
}

// Palavras "espaciais" que trocam conforme a barra enche — dao a sensacao de que
// algo esta sendo feito (pedido do dono: "alinhando orbitas" e afins). ASCII de
// proposito (convencao do projeto: sem acento em string literal).
private val SPACE_WORDS = listOf(
    "acordando o cosmos",
    "alinhando orbitas",
    "tracando a rota estelar",
    "calibrando constelacoes",
    "quase la",
)

private fun spaceWord(progress: Float): String =
    SPACE_WORDS[(progress * SPACE_WORDS.size).toInt().coerceIn(0, SPACE_WORDS.lastIndex)]

// internal + tamanho parametrizavel: a tela de login reusa o MESMO planeta, pra o
// objeto que abre o app ser o mesmo que recebe no login (continuidade de marca).
//
// entrance: progresso 0..1 do one-shot "constelacao se forma", tocado SO pelo
// gate de boot (UpdaterGate) num Animatable/animateFloatAsState proprio. null
// (default) = ja assentado, ou seja, o comportamento de sempre — login,
// onboarding e o proprio gate com reduceMotion continuam iguais, sem esse custo.
// planetRes: recurso do planeta no centro. O gate passa a variante TRANSPARENTE
// (astra-glyph.png = so o planeta branco, anel/estrela vazados, sem o quadrado preto
// que poluia o ceu); gate/login/onboarding/rail usam essa mesma marca transparente.
@Composable
internal fun RotatingStarsLogo(reduceMotion: Boolean, diameter: Dp = 150.dp, entrance: State<Float>? = null, planetRes: String = "astra-icon.png") {
    val accent = Obsidian.accent
    val twoPi = (2.0 * PI).toFloat()
    // Fase lida DENTRO do draw (drawRing roda no DrawScope do Canvas): o composable
    // nao recompoe por frame, so os Canvas redesenham. Antes o .value saia no corpo
    // e a tela de login recompunha 60fps enquanto voce digitava a senha. Movimento
    // reduzido / janela em segundo plano: anel parado. (Auditoria de movimento, #4.)
    val phaseState = if (reduceMotion || !LocalWindowActive.current) null else {
        rememberInfiniteTransition(label = "orbit").animateFloat(
            initialValue = 0f,
            targetValue = twoPi,
            animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
            label = "phase",
        )
    }
    val count = 14
    val tilt = (-12.0 * PI / 180.0).toFloat()

    // Posicao de orbita (formula de sempre) extraida pra fn: a entrada precisa
    // dela tanto de alvo do "reune" quanto do desenho do regime normal.
    fun DrawScope.orbitPos(theta: Float): Offset {
        val half = size.minDimension / 2f
        val rx = half * 0.92f
        val ry = half * 0.30f
        val ex = rx * cos(theta)
        val ey = ry * sin(theta)
        return Offset(
            center.x + ex * cos(tilt) - ey * sin(tilt),
            center.y + ex * sin(tilt) + ey * cos(tilt),
        )
    }
    // Posicao espalhada em t=0 da entrada: um circulo bem mais largo que o anel,
    // angulo por indice via angulo aureo (~137.5deg) pra nao empilhar duas
    // estrelas na mesma direcao. Deterministico por indice — sem Random, sem
    // alocar nada no draw.
    fun DrawScope.scatterPos(i: Int): Offset {
        val half = size.minDimension / 2f
        val ang = i * 2.4f
        val rad = half * (1.5f + 0.6f * ((i * 53) % 5) / 4f)
        return Offset(center.x + rad * cos(ang), center.y + rad * sin(ang))
    }
    // Anel de Saturno: elipse achatada; sin(theta) > 0 = lado perto (na frente
    // do planeta), <= 0 = lado longe (atras). Cada canvas desenha so um lado.
    fun DrawScope.drawRing(front: Boolean) {
        val phase = phaseState?.value ?: 0.9f
        // "Reune" (0..1.1s da entrada): 0 = tudo espalhado, 1 = na orbita. Fora
        // do gate (entrance == null) cai sempre em 1 = formula de sempre.
        val gather = ((entrance?.value ?: 1f) * 2000f / 1100f).coerceIn(0f, 1f)
            .let { FastOutSlowInEasing.transform(it) }
        repeat(count) { i ->
            val theta = phase + i * (twoPi / count)
            val depth = sin(theta)
            if ((depth > 0f) != front) return@repeat
            val target = orbitPos(theta)
            val p = if (gather >= 1f) target else lerp(scatterPos(i), target, gather)
            // Paralaxe: perto = maior e mais brilhante; longe = menor e apagado.
            val t01 = (depth + 1f) / 2f
            val lead = i % 5 == 0
            // Espalhada nasce apagada; ganha brilho junto com a aproximacao.
            val entryAlpha = if (gather >= 1f) 1f else 0.15f + 0.85f * gather
            drawCircle(
                color = accent.copy(alpha = (0.30f + 0.70f * t01) * entryAlpha),
                radius = (1.3f + 2.1f * t01).dp.toPx() * (if (lead) 1.35f else 1f),
                center = p,
            )
        }
    }
    // Linhas da constelacao sendo esbocada: conecta vizinhos na ORDEM do anel
    // (i -> i+1), o mesmo desenho que a orbita final, so que ainda se formando.
    // Sobe durante o "reune", some no "assenta" (1.1..1.7s) — so existe durante
    // a entrada do gate, entrance == null sai no primeiro if.
    fun DrawScope.drawConstellationLines() {
        val e = entrance?.value ?: return
        val phase = phaseState?.value ?: 0.9f
        val ms = e * 2000f
        val gather = (ms / 1100f).coerceIn(0f, 1f).let { FastOutSlowInEasing.transform(it) }
        val settle = ((ms - 1100f) / 600f).coerceIn(0f, 1f)
        val lineAlpha = gather * (1f - settle)
        if (lineAlpha <= 0f) return
        val pts = (0 until count).map { i ->
            val theta = phase + i * (twoPi / count)
            lerp(scatterPos(i), orbitPos(theta), gather)
        }
        for (i in 0 until count) {
            drawLine(
                color = accent.copy(alpha = 0.16f * lineAlpha),
                start = pts[i],
                end = pts[(i + 1) % count],
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
    // O planeta ocupa 52% do diametro; o resto e o espaco onde o anel passa.
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            drawConstellationLines()
            drawRing(front = false)
        }
        Image(
            painter = painterResource(planetRes),
            contentDescription = null,
            modifier = Modifier
                .size(diameter * 0.52f)
                // Planeta escondido em t=0, funde+cresce no "assenta" (1.1..1.7s).
                // Lido dentro do graphicsLayer — nao recompoe o composable por frame.
                .graphicsLayer {
                    val ms = (entrance?.value ?: 1f) * 2000f
                    val a = ((ms - 1100f) / 600f).coerceIn(0f, 1f)
                    alpha = a
                    scaleX = 0.85f + 0.15f * a
                    scaleY = 0.85f + 0.15f * a
                },
        )
        Canvas(Modifier.size(diameter)) { drawRing(front = true) }
    }
}

@Composable
private fun PillButton(label: String, accent: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Text(
        label,
        style = TextStyle(color = if (accent) Obsidian.accent else Obsidian.text3, fontSize = 12.sp),
        modifier = Modifier
            .clickScale(src)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (accent) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ---- Banner in-app (topo): lembrete quando o update foi adiado ("depois") ou
// achado na checagem manual. Desliza de cima e conduz o mesmo mini-fluxo
// (disponivel -> baixando -> pronto) usando o estado compartilhado do service. ----
@Composable
fun BoxScope.UpdateBanner(updater: UpdateService) {
    val st by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }
    val show = !dismissed && (
        st is UpdateState.Available || st is UpdateState.Downloading || st is UpdateState.Ready
    )
    AnimatedVisibility(
        visible = show,
        enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Row(
            Modifier
                .padding(top = 8.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LIcon(Lucide.ArrowUp, tint = Obsidian.accent, size = 16.dp)
            Spacer(Modifier.width(10.dp))
            when (val s = st) {
                is UpdateState.Available -> {
                    Text(
                        "Astra ${s.version} disponivel",
                        style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                        modifier = Modifier.weight(1f),
                    )
                    PillButton("atualizar", accent = true, onClick = { scope.launch { updater.downloadAndStage(s) } })
                    Spacer(Modifier.width(6.dp))
                    BannerClose { dismissed = true }
                }
                is UpdateState.Downloading -> {
                    Text(
                        "baixando ${s.version}… ${(s.progress * 100).toInt()}%",
                        style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Progress(
                        s.progress,
                        Modifier.weight(1f),
                        Obsidian.accent,
                        Obsidian.overlay,
                        5.dp,
                        ProgressAnimation.Spring,
                    )
                }
                is UpdateState.Ready -> {
                    Text(
                        "${s.version} pronto — reinicie pra aplicar",
                        style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                        modifier = Modifier.weight(1f),
                    )
                    PillButton("reiniciar", accent = true, onClick = { updater.restartToInstall() })
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun BannerClose(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(26.dp)
            .clickScale(src)
            .clip(RoundedCornerShape(7.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(Lucide.X, tint = Obsidian.text3, size = 14.dp)
    }
}
