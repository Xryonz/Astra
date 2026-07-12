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
import androidx.compose.foundation.horizontalScroll
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
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.voice.RemoteVideo
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
        Spacer(Modifier.height(14.dp))

        // Palco = grid de tiles (Discord): cada participante e um cartao com
        // avatar, nome, anel ambar ao falar e icone de mudo. Quem transmite tela
        // vira o video grande no topo e os tiles descem pra uma faixa embaixo.
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

        var watching by remember { mutableStateOf<RemoteVideo?>(null) }
        LaunchedEffect(videos) { if (videos.none { it === watching }) watching = videos.firstOrNull() }

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
            if (videos.isEmpty()) {
                // Sem transmissao: grid centralizado no palco.
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    ParticipantGrid(tiles)
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    watching?.let { w ->
                        RemoteVideoView(
                            w.track,
                            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)),
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
                    Spacer(Modifier.height(10.dp))
                    // Faixa de tiles menores sob o video (rolagem horizontal se lotar).
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tiles.forEach { ParticipantTile(it, Modifier.width(92.dp)) }
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
        tiles.forEach { ParticipantTile(it, Modifier.width(116.dp)) }
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
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            if (tile.speaking) {
                Box(
                    Modifier.fillMaxSize()
                        .clip(CircleShape)
                        .background(Obsidian.accent.copy(alpha = 0.22f * ring)),
                )
            }
            DesktopAvatar(tile.avatarUrl, tile.label, 54)
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
