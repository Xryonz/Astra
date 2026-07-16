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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.cos
import kotlin.math.sin

// ---- Gate de boot (estilo Discord): janelinha que verifica a versao ----
// Logo do Astra no centro com estrelas orbitando (sensacao de carregando). Se
// achar versao nova, mostra a barra de progresso (RikkaUI) no download; senao
// (atualizado/falha/timeout) segue pro app. A janela e pequena e frameless.
@Composable
fun UpdaterGate(updater: UpdateService, reduceMotion: Boolean, onDone: () -> Unit) {
    val st by updater.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { updater.check(silent = false) }
    // Resolucao: atualizado/falha seguem pro app; pronto -> reinicia e instala.
    LaunchedEffect(st) {
        when (st) {
            is UpdateState.UpToDate -> { delay(800); onDone() }
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
                is UpdateState.Available ->
                    GateAvailable(s, onUpdate = { scope.launch { updater.downloadAndStage(s) } }, onLater = onDone)
                is UpdateState.Downloading -> GateDownloading(s)
                is UpdateState.Ready -> GateStatus("reiniciando pra aplicar…")
                is UpdateState.Failed -> GateStatus("nao deu pra verificar")
                is UpdateState.UpToDate -> GateStatus("voce esta na ultima versao")
                else -> GateStatus("verificando atualizacoes…")
            }
        }
    }
}

@Composable
private fun RotatingStarsLogo(reduceMotion: Boolean) {
    val accent = Obsidian.accent
    val angle = if (reduceMotion) 0f else {
        val t = rememberInfiniteTransition(label = "orbit")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
            label = "angle",
        ).value
    }
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        // Estrelas num anel que gira em volta do planeta (o logo fica parado).
        Canvas(Modifier.size(150.dp).graphicsLayer { rotationZ = angle }) {
            val radius = size.minDimension / 2f * 0.86f
            val count = 10
            repeat(count) { i ->
                val a = (i.toFloat() / count) * (2.0 * Math.PI).toFloat()
                val p = Offset(center.x + radius * cos(a), center.y + radius * sin(a))
                val big = i % 3 == 0
                drawCircle(
                    color = accent.copy(alpha = if (big) 0.95f else 0.4f),
                    radius = if (big) 3.dp.toPx() else 1.6.dp.toPx(),
                    center = p,
                )
            }
        }
        Image(
            painter = painterResource("astra-icon.png"),
            contentDescription = null,
            modifier = Modifier.size(78.dp),
        )
    }
}

@Composable
private fun GateStatus(text: String) {
    Text(text, style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
}

@Composable
private fun GateAvailable(s: UpdateState.Available, onUpdate: () -> Unit, onLater: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        GateStatus("nova versao ${s.version} disponivel")
        if (s.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                s.notes,
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 15.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("depois", accent = false, onClick = onLater)
            PillButton("atualizar agora", accent = true, onClick = onUpdate)
        }
    }
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
