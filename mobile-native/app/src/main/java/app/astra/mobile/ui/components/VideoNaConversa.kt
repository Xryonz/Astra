package app.astra.mobile.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import app.astra.mobile.BuildConfig
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize2
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.X

private val formatoDoVideo = RoundedCornerShape(14.dp)

val LocalTocadorDaConversa = staticCompositionLocalOf<TocadorDaConversa?> { null }

@Stable
class TocadorDaConversa(private val context: Context) {
    var atual by mutableStateOf<String?>(null)
        private set
    var nome by mutableStateOf<String?>(null)
        private set
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var telaCheia by mutableStateOf(false)
        private set
    private var retomarAoGirar = false
    private var retomarQuandoAparecer = false
    private var superficiesNaTela = 0

    fun tocar(att: Attachment, desde: Long = 0L, tocando: Boolean = true) {
        if (atual == att.url) return
        soltar()
        player = ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(enderecoAbsoluto(att.url)), desde)
            prepare()
            playWhenReady = tocando
        }
        atual = att.url
        nome = att.name
    }

    fun abrirTelaCheia() {
        telaCheia = true
    }

    fun fecharTelaCheia() {
        telaCheia = false
        if (superficiesNaTela == 0) player?.pause()
    }

    fun soltar() {
        player?.release()
        player = null
        atual = null
        nome = null
        telaCheia = false
        retomarQuandoAparecer = false
    }

    internal fun superficieEntrou() {
        superficiesNaTela++
        retomarSeCombinado()
    }

    private fun retomarSeCombinado() {
        if (!retomarQuandoAparecer) return
        retomarQuandoAparecer = false
        player?.play()
    }

    internal fun superficieSaiu(dela: Player) {
        superficiesNaTela--
        if (player === dela && !telaCheia && superficiesNaTela == 0) dela.pause()
    }

    internal fun aoSairDoApp(girando: Boolean) {
        retomarAoGirar = girando && player?.playWhenReady == true
        player?.pause()
    }

    internal fun aoVoltarAoApp() {
        retomarAoGirar = false
    }

    internal fun retrato(): List<Any> = listOf(
        atual.orEmpty(),
        nome.orEmpty(),
        player?.currentPosition ?: 0L,
        telaCheia,
        retomarAoGirar,
    )

    internal fun restaurar(retrato: List<Any>) {
        val url = retrato[0] as String
        if (url.isEmpty()) return
        tocar(
            Attachment(url = url, name = (retrato[1] as String).ifEmpty { null }),
            desde = retrato[2] as Long,
            tocando = false,
        )
        telaCheia = retrato[3] as Boolean
        retomarQuandoAparecer = retrato[4] as Boolean
        if (telaCheia) retomarSeCombinado()
    }
}

@Composable
fun rememberTocadorDaConversa(): TocadorDaConversa {
    val janela = LocalContext.current.atividade()
    val context = LocalContext.current.applicationContext
    val tocador = rememberSaveable(
        saver = listSaver(
            save = { it.retrato() },
            restore = { TocadorDaConversa(context).apply { restaurar(it) } },
        ),
    ) { TocadorDaConversa(context) }
    DisposableEffect(tocador) { onDispose { tocador.soltar() } }
    LifecycleStartEffect(tocador) {
        tocador.aoVoltarAoApp()
        onStopOrDispose { tocador.aoSairDoApp(girando = janela?.isChangingConfigurations == true) }
    }
    return tocador
}

@Composable
fun VideoNaConversa(att: Attachment) {
    val tocador = LocalTocadorDaConversa.current
    val player = tocador?.player
    if (tocador != null && player != null && tocador.atual == att.url) {
        PlayerNaConversa(att, player, tocador)
    } else {
        CartaoDoVideo(att) { tocador?.tocar(att) }
    }
}

@Composable
fun TelaCheiaDoVideo(tocador: TocadorDaConversa) {
    val player = tocador.player ?: return
    if (!tocador.telaCheia) return
    VideoEmTelaCheia(player, tocador.nome, tocador::fecharTelaCheia)
}

@Composable
private fun CartaoDoVideo(att: Attachment, aoTocar: () -> Unit) {
    val formato = RoundedCornerShape(12.dp)
    val detalhes = listOfNotNull(
        att.duration?.let { tempo(it * 1000L) },
        fmtBytes(att.size).takeIf { it.isNotEmpty() },
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .clip(formato)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, formato)
            .clickable(onClickLabel = "Tocar o vídeo", onClick = aoTocar)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(astraColors.overlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Play, contentDescription = null, tint = astraColors.text1, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = att.name ?: "vídeo",
                style = MaterialTheme.typography.bodyMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detalhes.isNotEmpty()) {
                Text(text = detalhes, style = MaterialTheme.typography.labelSmall, color = astraColors.text3)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerNaConversa(att: Attachment, player: Player, tocador: TocadorDaConversa) {
    DisposableEffect(player) {
        tocador.superficieEntrou()
        onDispose { tocador.superficieSaiu(player) }
    }
    val botao = rememberPlayPauseButtonState(player)
    val proporcao = if (att.width != null && att.height != null && att.height > 0) {
        (att.width.toFloat() / att.height).coerceIn(0.6f, 2.2f)
    } else 16f / 9f

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(proporcao)
            .clip(formatoDoVideo)
            .background(Color.Black)
            .border(1.dp, astraColors.borderMid, formatoDoVideo)
            .then(if (botao.showPlay) Modifier else Modifier.keepScreenOn())
            .clickable(
                onClickLabel = if (botao.showPlay) "Tocar o vídeo" else "Pausar o vídeo",
                onClick = botao::onClick,
            )
            .semantics {
                contentDescription = att.name ?: "Vídeo"
                stateDescription = if (botao.showPlay) "Pausado" else "Tocando"
            },
    ) {
        if (!tocador.telaCheia) {
            ContentFrame(player, Modifier.fillMaxSize(), surfaceType = SURFACE_TYPE_TEXTURE_VIEW)
        }
        if (botao.showPlay) SinalDePlay(Modifier.align(Alignment.Center))
        BotaoDoVideo(
            Lucide.Maximize2,
            "Abrir em tela cheia",
            Modifier.align(Alignment.TopEnd).padding(6.dp),
            aoTocar = tocador::abrirTelaCheia,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoEmTelaCheia(player: Player, nome: String?, aoFechar: () -> Unit) {
    Dialog(
        onDismissRequest = aoFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        CobrirOCeu()
        val botao = rememberPlayPauseButtonState(player)
        val progresso = rememberProgressStateWithTickInterval(player, tickIntervalMs = 250)
        var arrastando by remember { mutableStateOf<Float?>(null) }
        val total = progresso.durationMs.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val posicao = arrastando?.let { (it * total).toLong() } ?: progresso.currentPositionMs

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(if (botao.showPlay) Modifier else Modifier.keepScreenOn())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = if (botao.showPlay) "Tocar o vídeo" else "Pausar o vídeo",
                    onClick = botao::onClick,
                )
                .semantics {
                    contentDescription = nome ?: "Vídeo"
                    stateDescription = if (botao.showPlay) "Pausado" else "Tocando"
                },
        ) {
            ContentFrame(player, Modifier.fillMaxSize())
            if (botao.showPlay) SinalDePlay(Modifier.align(Alignment.Center))
            BotaoDoVideo(
                Lucide.X,
                "Fechar",
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(12.dp),
                aoTocar = aoFechar,
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotaoDoVideo(
                    if (botao.showPlay) Lucide.Play else Lucide.Pause,
                    if (botao.showPlay) "Tocar o vídeo" else "Pausar o vídeo",
                    aoTocar = botao::onClick,
                )
                Spacer(Modifier.width(10.dp))
                Text(tempo(posicao), style = MaterialTheme.typography.labelMedium, color = astraColors.text2)
                Slider(
                    value = if (total > 0) (posicao.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    onValueChange = { arrastando = it },
                    onValueChangeFinished = {
                        arrastando?.let { player.seekTo((it * total).toLong()) }
                        arrastando = null
                    },
                    enabled = total > 0,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .semantics {
                            contentDescription = "Posição do vídeo"
                            stateDescription = "${tempo(posicao)} de ${tempo(total)}"
                        },
                )
                Text(tempo(total), style = MaterialTheme.typography.labelMedium, color = astraColors.text2)
            }
        }
    }
}

@Composable
private fun SinalDePlay(modifier: Modifier) {
    Box(
        modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Play, contentDescription = null, tint = astraColors.text1, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun BotaoDoVideo(icone: ImageVector, rotulo: String, modifier: Modifier = Modifier, aoTocar: () -> Unit) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = rotulo, tint = astraColors.text1, modifier = Modifier.size(18.dp))
    }
}

private tailrec fun Context.atividade(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.atividade()
    else -> null
}

private fun enderecoAbsoluto(url: String): String =
    if (url.startsWith("/")) BuildConfig.BASE_URL.trimEnd('/') + url else url

private fun tempo(ms: Long): String {
    val segundos = (ms / 1000).coerceAtLeast(0)
    return "${segundos / 60}:${(segundos % 60).toString().padStart(2, '0')}"
}
