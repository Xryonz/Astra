package app.astra.desktop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.voice.VoiceEngine
import app.astra.desktop.voice.VoiceStatus
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ChannelDto
import okhttp3.OkHttpClient
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

// Sala de voz — V3: signaling + subscriber PC; audio remoto ja toca no device
// padrao. Falar (V4) e transmissao 60fps (V5) a caminho
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
            BasicText(
                if (audioLive) "♪ canal de audio aberto — quem falar na sala, voce ouve"
                else "abrindo canal de audio… (falar = V4, transmissao 60fps = V5)",
                style = TextStyle(
                    color = if (audioLive) Obsidian.accent else Obsidian.text3,
                    fontSize = 11.sp,
                ),
            )
            Spacer(Modifier.height(20.dp))
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
