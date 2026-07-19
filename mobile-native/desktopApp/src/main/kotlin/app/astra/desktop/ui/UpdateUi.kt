package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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
// Logo do Astra no centro com estrelas orbitando (sensacao de carregando). Se
// achar versao nova, mostra a barra de progresso (RikkaUI) no download; senao
// (atualizado/falha/timeout) segue pro app. A janela e pequena e frameless.
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

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Obsidian.void)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
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
                is UpdateState.Downloading -> GateDownloading(s)
                is UpdateState.Ready -> GateStatus("reiniciando pra aplicar…")
                is UpdateState.Failed -> GateStatus(s.reason)
                is UpdateState.UpToDate -> GateStatus("voce esta na ultima versao")
                else -> GateStatus("verificando atualizacoes…")
            }
            Spacer(Modifier.height(18.dp))
            XpBar(reduceMotion)
        }
    }
}

// Barra de XP DECORATIVA: o sistema de XP (ganhar por msg/call -> recompensas) e
// feature FUTURA. Aqui so enfeite gamer sutil (nivel + trilho com fill no accent
// pulsando). Reduzir movimento -> fill estatico.
@Composable
private fun XpBar(reduceMotion: Boolean) {
    val glow = if (reduceMotion) 1f else {
        rememberInfiniteTransition(label = "xp").animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
            label = "xpGlow",
        ).value
    }
    Column(Modifier.fillMaxWidth(0.72f)) {
        Text("nv 1", style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Obsidian.raised)) {
            Box(
                Modifier.fillMaxWidth(0.62f).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Obsidian.accent.copy(alpha = glow)),
            )
        }
    }
}

@Composable
private fun RotatingStarsLogo(reduceMotion: Boolean) {
    val accent = Obsidian.accent
    val twoPi = (2.0 * PI).toFloat()
    // reduceMotion: fase fixa (anel parado, mas ainda com frente/tras).
    val phase = if (reduceMotion) 0.9f else {
        val t = rememberInfiniteTransition(label = "orbit")
        t.animateFloat(
            initialValue = 0f,
            targetValue = twoPi,
            animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
            label = "phase",
        ).value
    }
    val count = 14
    val tilt = (-12.0 * PI / 180.0).toFloat()
    // Anel de Saturno: elipse achatada; sin(theta) > 0 = lado perto (na frente
    // do planeta), <= 0 = lado longe (atras). Cada canvas desenha so um lado.
    fun DrawScope.drawRing(front: Boolean) {
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
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) { drawRing(front = false) }
        Image(
            painter = painterResource("astra-icon.png"),
            contentDescription = null,
            modifier = Modifier.size(78.dp),
        )
        Canvas(Modifier.size(150.dp)) { drawRing(front = true) }
    }
}

@Composable
private fun GateStatus(text: String) {
    Text(text, style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
}

@Composable
private fun GateDownloading(s: UpdateState.Downloading) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        GateStatus("baixando ${s.version}…")
        Spacer(Modifier.height(14.dp))
        // Barra determinada da RikkaUI (progress 0..1, mola pra suavizar o avanco).
        Progress(
            s.progress,
            Modifier.fillMaxWidth(),
            Obsidian.accent,
            Obsidian.overlay,
            6.dp,
            ProgressAnimation.Spring,
        )
        Spacer(Modifier.height(8.dp))
        Text("${(s.progress * 100).toInt()}%", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
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
