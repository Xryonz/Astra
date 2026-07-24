package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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

    // Tempo minimo de tela: mesmo com a checagem instantanea, o gate fica no ar
    // ~2.6s pra a animacao de carregamento (planeta + estrelas orbitando) ser
    // vista inteira antes de cair no app. So conta pro caminho "atualizado".
    val gateStart = remember { System.currentTimeMillis() }
    suspend fun holdThenDone() {
        val minMs = 2600L
        val left = minMs - (System.currentTimeMillis() - gateStart)
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

    // Progresso REAL: so o download sabe quanto falta. Nos outros estados a barra
    // varre (indeterminada) em vez de fingir uma porcentagem.
    val progress = (st as? UpdateState.Downloading)?.progress

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
            RotatingStarsLogo(reduceMotion)
            Spacer(Modifier.height(16.dp))
            Text("Astra", style = TextStyle(color = Obsidian.text1, fontSize = 22.sp, fontFamily = DmSerif))
            Spacer(Modifier.height(8.dp))
            when (val s = st) {
                is UpdateState.Available -> GateStatus("nova versao ${s.version} — baixando…")
                is UpdateState.Downloading -> GateStatus("baixando ${s.version}…")
                is UpdateState.Ready -> GateStatus("reiniciando pra aplicar…")
                is UpdateState.Failed -> GateStatus(s.reason)
                is UpdateState.UpToDate -> GateStatus("voce esta na ultima versao")
                else -> GateStatus("verificando atualizacoes…")
            }
            Spacer(Modifier.height(18.dp))
            XpBar(progress, reduceMotion)
        }
    }
}

// Barra de carregamento SEGMENTADA — colunas dividindo o trilho, no espirito de
// uma barra de XP de jogo (pedido do dono). Substitui a barra lisa da RikkaUI E a
// antiga barra decorativa: eram duas barras empilhadas durante o download, uma
// real e outra de enfeite.
//
// progress != null -> avanco REAL do download (celula parcial na ponta, pra o
// movimento nao ser em degraus de 5%). progress == null -> nao ha o que medir
// (verificando/pronto/falhou) e a barra VARRE, em vez de fingir porcentagem.
//
// Canvas e nao 20 Box: uma unica passada de desenho, sem 20 nos de layout numa
// tela que precisa abrir na hora.
@Composable
private fun XpBar(progress: Float?, reduceMotion: Boolean) {
    val cells = 20
    // Varredura so quando indeterminada e com movimento ligado.
    val sweep = if (progress != null || reduceMotion) -1f else {
        rememberInfiniteTransition(label = "xp").animateFloat(
            initialValue = -0.25f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
            label = "xpSweep",
        ).value
    }
    val accent = Obsidian.accent
    val track = Obsidian.raised
    Column(Modifier.fillMaxWidth(0.72f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (progress != null) "baixando" else "nv 1",
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
                modifier = Modifier.weight(1f),
            )
            // Porcentagem so quando ela existe de verdade.
            if (progress != null) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(7.dp)) {
            val gap = 2.5.dp.toPx()
            val cellW = (size.width - gap * (cells - 1)) / cells
            val radius = CornerRadius(1.5.dp.toPx())
            for (i in 0 until cells) {
                val x = i * (cellW + gap)
                val lit = if (progress != null) {
                    // Quanto DESTA celula ja encheu: a da ponta acende em fracao.
                    ((progress * cells) - i).coerceIn(0f, 1f)
                } else {
                    // Varredura: brilho decai com a distancia ate a frente da onda.
                    val d = kotlin.math.abs((i + 0.5f) / cells - sweep)
                    (1f - d * 5f).coerceIn(0f, 1f)
                }
                drawRoundRect(
                    color = track,
                    topLeft = Offset(x, 0f),
                    size = Size(cellW, size.height),
                    cornerRadius = radius,
                )
                if (lit > 0f) {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.35f + 0.65f * lit),
                        topLeft = Offset(x, 0f),
                        size = Size(cellW * lit, size.height),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

// internal + tamanho parametrizavel: a tela de login reusa o MESMO planeta, pra o
// objeto que abre o app ser o mesmo que recebe no login (continuidade de marca).
@Composable
internal fun RotatingStarsLogo(reduceMotion: Boolean, diameter: Dp = 150.dp) {
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
    // Anel de Saturno: elipse achatada; sin(theta) > 0 = lado perto (na frente
    // do planeta), <= 0 = lado longe (atras). Cada canvas desenha so um lado.
    fun DrawScope.drawRing(front: Boolean) {
        val phase = phaseState?.value ?: 0.9f
        val half = size.minDimension / 2f
        val rx = half * 0.92f
        val ry = half * 0.30f
        repeat(count) { i ->
            val theta = phase + i * (twoPi / count)
            val depth = sin(theta)
            if ((depth > 0f) != front) return@repeat
            val ex = rx * cos(theta)
            val ey = ry * depth
            val p = Offset(
                center.x + ex * cos(tilt) - ey * sin(tilt),
                center.y + ex * sin(tilt) + ey * cos(tilt),
            )
            // Paralaxe: perto = maior e mais brilhante; longe = menor e apagado.
            val t01 = (depth + 1f) / 2f
            val lead = i % 5 == 0
            drawCircle(
                color = accent.copy(alpha = 0.30f + 0.70f * t01),
                radius = (1.3f + 2.1f * t01).dp.toPx() * (if (lead) 1.35f else 1f),
                center = p,
            )
        }
    }
    // O planeta ocupa 52% do diametro; o resto e o espaco onde o anel passa.
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) { drawRing(front = false) }
        Image(
            painter = painterResource("astra-icon.png"),
            contentDescription = null,
            modifier = Modifier.size(diameter * 0.52f),
        )
        Canvas(Modifier.size(diameter)) { drawRing(front = true) }
    }
}

@Composable
private fun GateStatus(text: String) {
    Text(text, style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
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
