package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.Desinstalador
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.desktop.update.UpdateService
import app.astra.desktop.update.UpdateState
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess
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
            "atualizações automáticas só no app instalado (isto é um build de dev).",
            style = Tipo.descricao,
            modifier = Modifier.widthIn(max = LARGURA_DO_TEXTO_DE_CONFIG),
        )
        return
    }

    Text("atualizações", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "o Astra verifica ao abrir e a cada 20 minutos. você também pode procurar agora.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = LARGURA_DO_TEXTO_DE_CONFIG),
    )
    Spacer(Modifier.height(14.dp))

    when (val s = st) {
        is UpdateState.Checking -> AboutStatus("procurando atualizações…")
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
                Modifier.fillMaxWidth(),
                Obsidian.accent,
                Obsidian.overlay,
                6.dp,
                ProgressAnimation.Spring,
            )
        }
        is UpdateState.Ready -> {
            AboutStatus("${s.version} baixada — reinicie para aplicar")
            Spacer(Modifier.height(10.dp))
            AboutButton("reiniciar agora", accent = true) { scope.launch { updater.restartToInstall() } }
        }
        is UpdateState.Failed -> {
            AboutStatus(s.reason)
            if (s.releaseUrl != null) {
                Spacer(Modifier.height(10.dp))
                AboutButton("abrir página do release", accent = false) {
                    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(s.releaseUrl)) }
                }
            }
        }
        else -> {}
    }

    Spacer(Modifier.height(16.dp))
    BotaoProcurarAtualizacao { updater.check() }

    if (Desinstalador.disponivel) BlocoDeDesinstalar()
}

private const val TEMPO_MINIMO_DA_ETAPA_MS = 450L

@Composable
private fun BlocoDeDesinstalar() {
    var confirmar by remember { mutableStateOf(false) }
    var desinstalando by remember { mutableStateOf(false) }
    SettingsDivider()
    Text("desinstalar", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "apaga o Astra deste computador: o programa, as versões guardadas, os atalhos, o login e as " +
            "preferências. as regras de rede que o Windows criou para o Astra não são apagadas.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = LARGURA_DO_TEXTO_DE_CONFIG),
    )
    Spacer(Modifier.height(14.dp))
    BotaoDePerigo("desinstalar o Astra", Lucide.Trash2) { confirmar = true }
    if (confirmar) {
        ConfirmPopup(
            message = "desinstalar o Astra? o login e as preferências deste computador também saem.",
            confirmLabel = "desinstalar",
            onConfirm = {
                confirmar = false
                desinstalando = true
            },
            onDismiss = { confirmar = false },
            posicao = CenterInWindow,
        )
    }
    if (desinstalando) TelaDeDesinstalacao()
}

private object JanelaInteira : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@Composable
private fun TelaDeDesinstalacao() {
    val etapas = remember { Desinstalador.etapas() }
    var atual by remember { mutableIntStateOf(0) }
    var falhou by remember { mutableStateOf(false) }
    val reduzMovimento = LocalReduceMotion.current
    val progresso = remember { Animatable(0f) }
    val partes = etapas.size + 1

    LaunchedEffect(Unit) {
        suspend fun avancar(alvo: Float) {
            if (reduzMovimento) progresso.snapTo(alvo)
            else progresso.animateTo(alvo, tween(260, easing = EaseOutStd))
        }
        etapas.forEachIndexed { i, etapa ->
            atual = i
            val inicio = System.currentTimeMillis()
            withContext(Dispatchers.IO) { runCatching { etapa.trabalho() } }
            val resto = TEMPO_MINIMO_DA_ETAPA_MS - (System.currentTimeMillis() - inicio)
            if (resto > 0) delay(resto)
            avancar((i + 1f) / partes)
        }
        atual = etapas.size
        if (!withContext(Dispatchers.IO) { Desinstalador.concluir() }) {
            falhou = true
            return@LaunchedEffect
        }
        avancar(1f)
        delay(TEMPO_MINIMO_DA_ETAPA_MS)
        exitProcess(0)
    }

    Popup(popupPositionProvider = JanelaInteira, properties = PopupProperties(focusable = true)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Obsidian.void.copy(alpha = 0.82f))
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(360.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Obsidian.raised)
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                    .padding(22.dp),
            ) {
                Text(
                    "desinstalando o Astra",
                    style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        falhou -> "não foi possível terminar a remoção"
                        atual < etapas.size -> etapas[atual].rotulo + "…"
                        else -> "fechando o Astra…"
                    },
                    style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
                )
                Spacer(Modifier.height(16.dp))
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val raio = CornerRadius(size.height / 2f)
                    drawRoundRect(Obsidian.borderDim, cornerRadius = raio)
                    drawRoundRect(
                        if (falhou) Obsidian.danger else Obsidian.accent,
                        size = Size(size.width * progresso.value, size.height),
                        cornerRadius = raio,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (falhou) "feche o Astra e apague à mão a pasta do programa e a pasta Astra dentro de %APPDATA%."
                    else "o Astra fecha sozinho no fim, e os arquivos do programa somem logo depois.",
                    style = Tipo.apoio,
                )
                if (falhou) {
                    Spacer(Modifier.height(14.dp))
                    AboutButton("fechar o Astra", accent = false) { exitProcess(0) }
                }
            }
        }
    }
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
    val src = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clickScale(src)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (accent) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
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
