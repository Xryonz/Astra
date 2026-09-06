package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.desktop.update.UpdateService
import app.astra.desktop.update.UpdateState
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import zed.rainxch.rikkaui.components.ui.progress.Progress
import zed.rainxch.rikkaui.components.ui.progress.ProgressAnimation

@Composable
internal fun AboutSection() {
    val updater = remember { GlobalContext.get().get<UpdateService>() }
    val st by updater.state.collectAsState()
    val scope = rememberCoroutineScope()

    ReadRow("versão", updater.currentVersion)
    Spacer(Modifier.height(22.dp))

    if (!updater.installed) {
        Text(
            "atualizacoes automaticas so no app instalado (isto e um build de dev).",
            style = Tipo.descricao,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        return
    }

    Text("atualizacoes", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "o Astra verifica ao abrir e a cada 20 minutos. você também pode procurar agora.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(14.dp))

    when (val s = st) {
        is UpdateState.Checking -> AboutStatus("procurando atualizacoes…")
        is UpdateState.UpToDate -> AboutStatus(
            "você está na ${s.vista} — a mais nova publicada, conferido ${haQuantoTempo(s.conferidoEm)}",
        )
        is UpdateState.Available -> {
            AboutStatus("nova versão ${s.version} disponível")
            Spacer(Modifier.height(10.dp))
            AboutButton("baixar e reiniciar", accent = true) { scope.launch { updater.downloadAndStage(s) } }
        }
        is UpdateState.Downloading -> {
            AboutStatus("baixando ${s.version}… ${(s.progress * 100).toInt()}%")
            Spacer(Modifier.height(10.dp))
            Progress(
                s.progress,
                Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                Obsidian.accent,
                Obsidian.overlay,
                6.dp,
                ProgressAnimation.Spring,
            )
        }
        is UpdateState.Ready -> {
            AboutStatus("${s.version} baixada — reinicie para aplicar")
            Spacer(Modifier.height(10.dp))
            AboutButton("reiniciar agora", accent = true) { updater.restartToInstall() }
        }
        is UpdateState.Failed -> {
            AboutStatus(s.reason)
            if (s.releaseUrl != null) {
                Spacer(Modifier.height(10.dp))
                AboutButton("abrir pagina do release", accent = false) {
                    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(s.releaseUrl)) }
                }
            }
        }
        else -> {}
    }

    Spacer(Modifier.height(16.dp))
    BotaoProcurarAtualizacao { updater.check() }
}

private const val PISO_DA_BUSCA = 1_800L
private val ETAPAS_DA_BUSCA = listOf("consultando o repositório…", "comparando versões…")

@Composable
private fun BotaoProcurarAtualizacao(procurar: suspend () -> Unit) {
    val escopo = rememberCoroutineScope()
    var procurando by remember { mutableStateOf(false) }
    var etapa by remember { mutableIntStateOf(0) }

    Column {
        AboutButton(
            label = if (procurando) ETAPAS_DA_BUSCA[etapa] else "procurar atualizações",
            accent = false,
            icone = Lucide.RefreshCw,
        ) {
            if (procurando) return@AboutButton
            procurando = true
            etapa = 0
            escopo.launch {
                val comecou = System.currentTimeMillis()
                val trabalho = launch { runCatching { procurar() } }
                delay(PISO_DA_BUSCA / ETAPAS_DA_BUSCA.size)
                etapa = 1
                trabalho.join()
                val resta = PISO_DA_BUSCA - (System.currentTimeMillis() - comecou)
                if (resta > 0) delay(resta)
                procurando = false
            }
        }
        if (procurando) {
            Spacer(Modifier.height(6.dp))
            BarraDeVarredura()
        }
    }
}

@Composable
private fun BarraDeVarredura() {
    val reduzMovimento = LocalReduceMotion.current
    val transicao = rememberInfiniteTransition(label = "varredura")
    val posicao by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "posicao",
    )
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawRect(color = Obsidian.borderDim, size = size)
        if (reduzMovimento) {
            drawRect(color = Obsidian.accentDim, size = size)
        } else {
            val largura = size.width * 0.35f
            val x = posicao * (size.width + largura) - largura
            drawRect(
                color = Obsidian.accentDim,
                topLeft = Offset(x.coerceAtLeast(0f), 0f),
                size = Size(
                    width = (x + largura).coerceAtMost(size.width) - x.coerceAtLeast(0f),
                    height = size.height,
                ),
            )
        }
    }
}

private fun haQuantoTempo(quando: Long): String {
    val min = (System.currentTimeMillis() - quando) / 60_000
    return when {
        min < 1L  -> "agora mesmo"
        min < 60L -> "há $min min"
        else      -> "há ${min / 60} h"
    }
}

@Composable
private fun AboutStatus(text: String) {
    Text(text, style = TextStyle(color = Obsidian.text2, fontSize = 13.sp))
}

@Composable
internal fun AboutButton(label: String, accent: Boolean, icone: ImageVector? = null, onClick: () -> Unit) {
    val cor = if (accent) Obsidian.accent else Obsidian.text2
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (accent) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icone?.let {
            LIcon(it, tint = cor, size = 14.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(label, style = TextStyle(color = cor, fontSize = 13.sp))
    }
}

internal fun mesEAno(iso: String?): String {
    val data = iso?.let { runCatching { java.time.OffsetDateTime.parse(it) }.getOrNull() } ?: return "—"
    val meses = listOf(
        "jan", "fev", "mar", "abr", "mai", "jun",
        "jul", "ago", "set", "out", "nov", "dez",
    )
    return "${meses[data.monthValue - 1]} ${data.year}"
}

@Composable
internal fun BotaoDePerigo(label: String, icone: ImageVector, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val fundo by animateColorAsState(
        if (hov) Obsidian.danger.copy(alpha = 0.16f) else Color.Transparent, tween(140),
    )
    val borda by animateColorAsState(
        if (hov) Obsidian.danger else Obsidian.danger.copy(alpha = 0.55f), tween(140),
    )
    Row(
        modifier = Modifier
            .clickScale(src)
            .clip(RoundedCornerShape(8.dp))
            .background(fundo)
            .border(1.dp, borda, RoundedCornerShape(8.dp))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(icone, tint = Obsidian.danger, size = 14.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = TextStyle(color = Obsidian.danger, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}
