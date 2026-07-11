package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.voice.RemoteVideo
import app.astra.desktop.voice.VoiceEngine
import app.astra.desktop.voice.VoiceStatus
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ChannelDto
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
fun VoiceView(channel: ChannelDto, onLeave: () -> Unit) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    val engine = remember(channel.id) {
        VoiceEngine(scope, koin.get<VoiceApi>(), koin.get<OkHttpClient>(named("plain")))
            .also { it.connect("channel", channel.id) }
    }
    DisposableEffect(channel.id) { onDispose { engine.dispose() } }
    val status by engine.status.collectAsState()
    val screenOn by engine.screenOn.collectAsState()
    val micOn by engine.micOn.collectAsState()
    val videos by engine.remoteVideos.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "◉ ${channel.name}",
            style = TextStyle(color = Obsidian.accent, fontSize = 20.sp, fontFamily = DmSerif),
        )
        Spacer(Modifier.height(10.dp))
        val (label, color) = when (val s = status) {
            VoiceStatus.Connecting -> "conectando ao sinal de voz…" to Obsidian.text3
            is VoiceStatus.Connected -> "conectado" to Obsidian.success
            is VoiceStatus.Failed -> s.reason to Obsidian.danger
            VoiceStatus.Closed -> "sinal encerrado" to Obsidian.text3
        }
        Text(label, style = TextStyle(color = color, fontSize = 13.sp))
        Spacer(Modifier.height(6.dp))
        val audioLive = (status as? VoiceStatus.Connected)?.audioLive == true
        Text(
            when {
                screenOn -> "📡 transmitindo tela a 60fps"
                audioLive -> "♪ canal de audio aberto"
                else -> "abrindo canal de audio…"
            },
            style = TextStyle(
                color = if (audioLive || screenOn) Obsidian.accent else Obsidian.text3,
                fontSize = 11.sp,
            ),
        )
        Spacer(Modifier.height(12.dp))
        (status as? VoiceStatus.Connected)?.let { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VoiceChip("voce", s.mySpeaking)
                s.others.forEach { VoiceChip(it.label, it.speaking) }
            }
        }

        // Palco: transmissao de outro participante (se houver mais de uma, abas).
        var watching by remember { mutableStateOf<RemoteVideo?>(null) }
        LaunchedEffect(videos) { if (videos.none { it === watching }) watching = videos.firstOrNull() }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            watching?.let { w ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    RemoteVideoView(
                        w.track,
                        Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "📡 transmissao de ${w.ownerLabel}",
                        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                    )
                    if (videos.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            videos.forEach { v ->
                                Text(
                                    v.ownerLabel,
                                    style = TextStyle(
                                        color = if (v === watching) Obsidian.accent else Obsidian.text3,
                                        fontSize = 11.sp,
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { watching = v }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        var screenChoices by remember { mutableStateOf<List<DesktopSource>?>(null) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (micOn) "mutar mic" else "🔇 desmutar",
                style = TextStyle(
                    color = if (micOn) Obsidian.text2 else Obsidian.danger,
                    fontSize = 13.sp,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        if (micOn) Obsidian.borderMid else Obsidian.danger,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable(onClick = engine::toggleMic)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
            Box {
                Text(
                    text = if (screenOn) "parar transmissao" else "transmitir tela",
                    style = TextStyle(
                        color = if (screenOn) Obsidian.danger else Obsidian.text2,
                        fontSize = 13.sp,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (screenOn) Obsidian.danger else Obsidian.borderMid,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable {
                            if (screenOn) {
                                engine.stopScreenShare()
                            } else {
                                val screens = engine.screens()
                                if (screens.size <= 1) engine.startScreenShare(screens.firstOrNull())
                                else screenChoices = screens
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
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
            Text(
                text = "sair da sala",
                style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                    .clickable(onClick = onLeave)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

// Pill de participante; acende ambar e pulsa um halo enquanto fala
// (SpeakersChanged). O pulso so anima quando speaking = true (sem custo parado).
@Composable
private fun VoiceChip(label: String, speaking: Boolean) {
    val border by animateColorAsState(
        if (speaking) Obsidian.accent else Obsidian.borderMid,
        tween(140),
    )
    val text by animateColorAsState(
        if (speaking) Obsidian.accent else Obsidian.text2,
        tween(140),
    )
    // Halo que respira: alpha 0 quando calado, pulsa 0.10..0.22 enquanto fala.
    // Reduzir movimento: halo fixo (ainda marca quem fala) em vez de pulsar.
    val glow = when {
        !speaking -> 0f
        LocalReduceMotion.current -> 0.16f
        else -> {
            val t = rememberInfiniteTransition()
            t.animateFloat(
                initialValue = 0.10f,
                targetValue = 0.22f,
                animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
            ).value
        }
    }
    Text(
        label,
        style = TextStyle(color = text, fontSize = 12.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Obsidian.accent.copy(alpha = glow))
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
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
