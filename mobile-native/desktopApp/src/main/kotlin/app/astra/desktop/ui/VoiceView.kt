package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.astra.desktop.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.ScreenQuality
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.voice.ScreenPreview
import app.astra.desktop.voice.VoiceEngine
import app.astra.desktop.voice.VoiceStatus
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import dev.onvoid.webrtc.media.FourCC
import dev.onvoid.webrtc.media.video.VideoBufferConverter
import dev.onvoid.webrtc.media.video.VideoTrack
import dev.onvoid.webrtc.media.video.VideoTrackSink
import dev.onvoid.webrtc.media.video.desktop.DesktopSource
import okhttp3.OkHttpClient
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Image as SkiaImage
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

// Sala de voz — V3..V6: audio bidirecional (mute), transmissao de tela a 60fps,
// palco de video remoto e speaking indicators
// (plano: docs/plans/2026-07-10-astra-voz-nativa.md).
@Composable
fun VoiceView(
    channel: ChannelDto,
    members: List<ServerMemberDto>,
    me: ProfileUserDto?,
    onLeave: () -> Unit,
) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    val engine = remember(channel.id) {
        VoiceEngine(scope, koin.get<VoiceApi>(), koin.get<OkHttpClient>(named("plain")), koin.get<DesktopPrefs>())
            .also { it.connect("channel", channel.id) }
    }
    DisposableEffect(channel.id) { onDispose { engine.dispose() } }
    val prefs = remember { koin.get<DesktopPrefs>() }
    val prefState by prefs.state.collectAsState()
    val status by engine.status.collectAsState()
    val screenOn by engine.screenOn.collectAsState()
    val micOn by engine.micOn.collectAsState()
    val videos by engine.remoteVideos.collectAsState()
    val localScreen by engine.localScreen.collectAsState()
    val localPreview by engine.localPreview.collectAsState()
    val screenStats by engine.screenStats.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header enxuto (Discord-like: pouco texto).
        Text(
            text = "◉ ${channel.name}",
            style = TextStyle(color = Obsidian.accent, fontSize = 18.sp, fontFamily = DmSerif),
        )
        Spacer(Modifier.height(4.dp))
        val (label, color) = when (val s = status) {
            VoiceStatus.Connecting -> "conectando…" to Obsidian.text3
            is VoiceStatus.Connected -> "conectado" to Obsidian.success
            is VoiceStatus.Failed -> s.reason to Obsidian.danger
            VoiceStatus.Closed -> "sinal encerrado" to Obsidian.text3
        }
        Text(label, style = TextStyle(color = color, fontSize = 11.sp))
        Spacer(Modifier.height(14.dp))

        // Palco. Sem transmissao (minha nem de outros) = grid de tiles. Com
        // transmissao = video grande em DESTAQUE + faixa de tiles embaixo. A MINHA
        // tela entra como stream tambem (auto-preview Discord) e vem PRIMEIRO ->
        // ao transmitir, ja vejo em destaque o que estou compartilhando.
        val connected = status as? VoiceStatus.Connected
        val avatarByUser = remember(members) { members.associate { it.userId to it.user.avatarUrl } }
        val tiles = remember(connected, me, micOn, avatarByUser) {
            buildList {
                if (connected != null) {
                    add(Tile("voce", connected.mySpeaking, me?.avatarUrl, isMe = true, muted = !micOn))
                    connected.others.forEach { p ->
                        add(Tile(p.label, p.speaking, avatarByUser[p.identity], isMe = false, muted = false))
                    }
                }
            }
        }

        val streams = remember(localScreen, videos) {
            buildList {
                localScreen?.let { add(StageStream("sua tela", it, isMe = true)) }
                videos.forEach { add(StageStream(it.ownerLabel, it.track, isMe = false)) }
            }
        }
        var watchingTrack by remember { mutableStateOf<VideoTrack?>(null) }
        LaunchedEffect(streams) {
            if (streams.none { it.track === watchingTrack }) watchingTrack = streams.firstOrNull()?.track
        }
        val watching = streams.find { it.track === watchingTrack }

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
            if (streams.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    ParticipantGrid(tiles)
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    watching?.let { w ->
                        // Destaque: borda accent no video em foco.
                        val stageMod = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Obsidian.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        val lp = localPreview
                        // Minha tela = preview direto da captura (o sink da track local
                        // nao entrega frames do CustomVideoSource). Sem tee (fallback
                        // GDI) cai pro sink da track.
                        if (w.isMe && lp != null) LocalPreviewView(lp, stageMod)
                        else RemoteVideoView(w.track, stageMod)
                        Spacer(Modifier.height(6.dp))
                        // Na MINHA transmissao mostro os fps reais (envio + captura) e
                        // o motivo se o WebRTC degradou — e como saber se bateu 60.
                        val st = screenStats
                        val txt = when {
                            !w.isMe -> "transmissao de ${w.label}"
                            st == null -> "voce esta transmitindo"
                            else -> buildString {
                                append("transmitindo · envio ${st.sendFps}fps · captura ${st.captureFps}fps")
                                if (st.limit != "none" && st.limit.isNotBlank()) append(" · limite: ${st.limit}")
                            }
                        }
                        val txtColor = when {
                            !w.isMe || st == null -> Obsidian.text3
                            st.sendFps >= 50 -> Obsidian.success
                            st.sendFps >= 1 -> Obsidian.warning
                            else -> Obsidian.text3
                        }
                        Text(txt, style = TextStyle(color = txtColor, fontSize = 11.sp))
                        // Abas so quando ha mais de uma transmissao (a minha + de outros).
                        if (streams.size > 1) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                streams.forEach { s ->
                                    val on = s.track === watchingTrack
                                    Text(
                                        s.label,
                                        style = TextStyle(
                                            color = if (on) Obsidian.accent else Obsidian.text3,
                                            fontSize = 11.sp,
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .border(1.dp, if (on) Obsidian.accent else Obsidian.borderDim, RoundedCornerShape(999.dp))
                                            .clickable { watchingTrack = s.track }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tiles.forEach { ParticipantTile(it, Modifier.width(148.dp)) }
                    }
                }
            }
        }

        // Controles minimalistas (Discord): botoes de simbolo com borda, sem texto.
        var screenChoices by remember { mutableStateOf<List<DesktopSource>?>(null) }
        var settingsOpen by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CallIconButton(
                symbol = if (micOn) "🎤" else "🔇",
                tone = if (micOn) CallTone.Normal else CallTone.Danger,
                onClick = engine::toggleMic,
            )
            Box {
                CallIconButton(
                    symbol = "🖥",
                    tone = if (screenOn) CallTone.Active else CallTone.Normal,
                    onClick = {
                        if (screenOn) {
                            engine.stopScreenShare()
                        } else {
                            val screens = engine.screens()
                            if (screens.size <= 1) engine.startScreenShare(screens.firstOrNull())
                            else screenChoices = screens
                        }
                    },
                )
                // Mais de um monitor: escolher qual transmitir.
                screenChoices?.let { screens ->
                    Popup(
                        onDismissRequest = { screenChoices = null },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Obsidian.raised)
                                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                        ) {
                            screens.forEachIndexed { i, s ->
                                Text(
                                    text = s.title.ifBlank { "tela ${i + 1}" },
                                    style = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            engine.startScreenShare(s)
                                            screenChoices = null
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            Box {
                CallIconButton(
                    symbol = "⚙",
                    tone = if (settingsOpen) CallTone.Active else CallTone.Normal,
                    onClick = { settingsOpen = !settingsOpen },
                )
                if (settingsOpen) {
                    Popup(
                        onDismissRequest = { settingsOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        CallSettingsPanel(
                            current = prefState.screenQuality,
                            onPick = { engine.setScreenQuality(it) },
                        )
                    }
                }
            }
            CallIconButton(symbol = "✕", tone = CallTone.Danger, onClick = onLeave)
        }
    }
}

// Config da call (gear): escolher qualidade + fluidez da transmissao (aplica ao
// vivo) e — futuro — o cancelador de ruido Krisp. Presets = os 4 do ScreenQuality.
@Composable
private fun CallSettingsPanel(current: ScreenQuality, onPick: (ScreenQuality) -> Unit) {
    Column(
        Modifier
            .width(232.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(10.dp))
            .padding(6.dp),
    ) {
        PanelHeader("Transmissao")
        ScreenQuality.entries.forEach { q ->
            val on = q == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onPick(q) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (on) "●" else "○",
                    style = TextStyle(color = if (on) Obsidian.accent else Obsidian.text3, fontSize = 11.sp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    q.label,
                    style = TextStyle(color = if (on) Obsidian.text1 else Obsidian.text2, fontSize = 12.sp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        PanelHeader("Microfone")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🎙", style = TextStyle(fontSize = 12.sp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Krisp — cancelar ruido", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
                Text("em breve", style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
            }
        }
    }
}

@Composable
private fun PanelHeader(text: String) {
    Text(
        text.uppercase(),
        style = TextStyle(color = Obsidian.text3, fontSize = 9.sp, letterSpacing = 1.sp),
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// Uma transmissao no palco: minha tela (auto-preview) OU de outro participante.
private data class StageStream(val label: String, val track: VideoTrack, val isMe: Boolean)

// Tom do botao de call (borda + simbolo): normal, ativo (accent) ou perigo.
private enum class CallTone { Normal, Active, Danger }

// Botao minimalista de call (Discord): so o simbolo, circulo com borda que troca
// de cor pelo estado. Sem texto.
@Composable
private fun CallIconButton(symbol: String, tone: CallTone, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(
        when (tone) {
            CallTone.Danger -> Obsidian.danger
            CallTone.Active -> Obsidian.accent
            CallTone.Normal -> Obsidian.borderMid
        },
        tween(140),
    )
    val fg = when (tone) {
        CallTone.Danger -> Obsidian.danger
        CallTone.Active -> Obsidian.accent
        CallTone.Normal -> Obsidian.text2
    }
    val bg by animateColorAsState(if (hovered) Obsidian.hover else Obsidian.raised.copy(alpha = 0.4f), tween(140))
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = TextStyle(color = fg, fontSize = 16.sp))
    }
}

// Um participante no palco. muted so e conhecido pra mim (o engine nao expoe o
// mute dos outros ainda) — nos outros o icone fica de fora.
private data class Tile(
    val label: String,
    val speaking: Boolean,
    val avatarUrl: String?,
    val isMe: Boolean,
    val muted: Boolean,
)

// Grid que quebra linha sozinho (FlowRow), centralizado.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantGrid(tiles: List<Tile>) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.forEach { ParticipantTile(it, Modifier.width(164.dp)) }
    }
}

// Cartao: avatar grande centralizado + nome; anel/halo ambar pulsa ao falar
// (respeita reduzir movimento — fica aceso e parado). Layout estavel: o halo
// vive num Box de tamanho fixo, entao falar nao empurra o tile.
@Composable
private fun ParticipantTile(tile: Tile, modifier: Modifier = Modifier) {
    val ring = when {
        !tile.speaking -> 0f
        LocalReduceMotion.current -> 1f
        else -> {
            val t = rememberInfiniteTransition()
            t.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            ).value
        }
    }
    val borderColor by animateColorAsState(
        if (tile.speaking) Obsidian.accent.copy(alpha = ring) else Obsidian.borderDim,
        tween(140),
    )
    // Inchada ao falar: o card cresce ~4% com mola suave (escala VISUAL via
    // graphicsLayer -> nao empurra os vizinhos; cresce por cima). Reduzir movimento
    // = fica maior parado, sem animar.
    val swell by animateFloatAsState(
        targetValue = if (tile.speaking) 1.04f else 1f,
        animationSpec = if (LocalReduceMotion.current) snap()
            else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
    )
    Column(
        modifier
            .graphicsLayer { scaleX = swell; scaleY = swell }
            .clip(RoundedCornerShape(14.dp))
            .background(Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
            if (tile.speaking) {
                Box(
                    Modifier.fillMaxSize()
                        .clip(CircleShape)
                        .background(Obsidian.accent.copy(alpha = 0.22f * ring)),
                )
            }
            DesktopAvatar(tile.avatarUrl, tile.label, 62)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tile.muted) {
                Text("🔇", style = TextStyle(fontSize = 11.sp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                tile.label,
                style = TextStyle(
                    color = if (tile.speaking) Obsidian.accent else Obsidian.text2,
                    fontSize = 12.sp,
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Renderiza a track remota: sink nativo -> I420 -> RGBA -> ImageBitmap por frame.
// makeRaster copia os bytes (frame nativo pode ser reciclado logo apos o callback);
// o buffer de conversao e reutilizado — so o sink escreve nele.
@Composable
private fun RemoteVideoView(track: VideoTrack, modifier: Modifier = Modifier) {
    var frame by remember(track) { mutableStateOf<ImageBitmap?>(null) }
    DisposableEffect(track) {
        var scratch = ByteArray(0)
        val sink = VideoTrackSink { vf ->
            runCatching {
                val buf = vf.buffer
                val w = buf.width
                val h = buf.height
                val need = w * h * 4
                if (scratch.size != need) scratch = ByteArray(need)
                VideoBufferConverter.convertFromI420(buf, scratch, FourCC.ABGR)
                frame = SkiaImage.makeRaster(
                    ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.OPAQUE),
                    scratch,
                    w * 4,
                ).toComposeImageBitmap()
            }
        }
        track.addSink(sink)
        onDispose { runCatching { track.removeSink(sink) } }
    }
    val f = frame
    if (f != null) {
        Image(f, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier)
    }
}

// Auto-preview da MINHA tela: os frames vem direto da captura (ScreenPreview, ARGB
// cru) porque o sink da track local nao dispara pra CustomVideoSource. makeRaster
// copia os bytes; cada ScreenPreview novo (~15fps) recompoe e redesenha.
@Composable
private fun LocalPreviewView(preview: ScreenPreview, modifier: Modifier = Modifier) {
    val image = remember(preview) {
        runCatching {
            SkiaImage.makeRaster(
                ImageInfo(preview.width, preview.height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE),
                preview.argb,
                preview.width * 4,
            ).toComposeImageBitmap()
        }.getOrNull()
    }
    if (image != null) {
        Image(image, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier)
    }
}
