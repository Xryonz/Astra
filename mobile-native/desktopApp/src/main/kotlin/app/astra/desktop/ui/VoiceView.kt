package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.voice.VoiceEngine
import app.astra.desktop.voice.VoiceStatus
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ChannelDto
import dev.onvoid.webrtc.media.video.desktop.DesktopSource
import okhttp3.OkHttpClient
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

// Sala de voz — V3+V4: audio bidirecional (ouvir + falar com mute). Transmissao
// de tela 60fps (V5) a caminho (plano: docs/plans/2026-07-10-astra-voz-nativa.md).
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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                text = "◉ ${channel.name}",
                style = TextStyle(color = Obsidian.accent, fontSize = 20.sp, fontFamily = FontFamily.Serif),
            )
            Spacer(Modifier.height(12.dp))
            val (label, color) = when (val s = status) {
                VoiceStatus.Connecting -> "conectando ao sinal de voz…" to Obsidian.text3
                is VoiceStatus.Connected ->
                    (if (s.others.isEmpty()) "conectado — so voce na sala"
                     else "na sala: ${s.others.joinToString(", ")}") to Obsidian.success
                is VoiceStatus.Failed -> s.reason to Obsidian.danger
                VoiceStatus.Closed -> "sinal encerrado" to Obsidian.text3
            }
            BasicText(label, style = TextStyle(color = color, fontSize = 13.sp))
            Spacer(Modifier.height(6.dp))
            val audioLive = (status as? VoiceStatus.Connected)?.audioLive == true
            val screenOn by engine.screenOn.collectAsState()
            BasicText(
                when {
                    screenOn -> "📡 transmitindo tela a 60fps"
                    audioLive -> "♪ canal de audio aberto — voz ao vivo nos dois sentidos"
                    else -> "abrindo canal de audio…"
                },
                style = TextStyle(
                    color = if (audioLive || screenOn) Obsidian.accent else Obsidian.text3,
                    fontSize = 11.sp,
                ),
            )
            Spacer(Modifier.height(20.dp))
            val micOn by engine.micOn.collectAsState()
            var screenChoices by remember { mutableStateOf<List<DesktopSource>?>(null) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
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
                    BasicText(
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
                                    BasicText(
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
                BasicText(
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
}
