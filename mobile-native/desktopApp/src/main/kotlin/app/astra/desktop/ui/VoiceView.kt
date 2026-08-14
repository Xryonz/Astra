package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.MicOff
import com.composables.icons.lucide.Volume2
import androidx.compose.runtime.rememberCoroutineScope
import app.astra.mobile.core.network.SoundApi
import app.astra.mobile.core.network.dto.ServerSoundDto
import app.astra.mobile.core.network.dto.TocarSomRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.composables.icons.lucide.PhoneOff
import com.composables.icons.lucide.ScreenShare
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Video
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.ScreenQuality
import kotlin.math.cos
import kotlin.math.sin
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.voice.VoiceEngine
import app.astra.desktop.voice.VoiceStatus
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import dev.onvoid.webrtc.media.FourCC
import dev.onvoid.webrtc.media.video.VideoBufferConverter
import dev.onvoid.webrtc.media.video.VideoTrack
import dev.onvoid.webrtc.media.video.VideoTrackSink
import dev.onvoid.webrtc.media.video.VideoDevice
import dev.onvoid.webrtc.media.video.desktop.DesktopSource
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Image as SkiaImage
import org.koin.core.context.GlobalContext

// Sala de voz — V3..V6: audio bidirecional (mute), transmissão de tela a 60fps,
// palco de video remoto e speaking indicators
// (plano: docs/plans/2026-07-10-astra-voz-nativa.md).
@Composable
fun VoiceView(
    channel: ChannelDto,
    members: List<ServerMemberDto>,
    me: ProfileUserDto?,
    // O engine vem de fora (VoiceSession, no shell). Não pode nascer aqui: era o
    // DisposableEffect desta tela que desconectava a call ao navegar.
    engine: VoiceEngine,
    onLeave: () -> Unit,
    // So pra soundboard: ChannelDto nao carrega a constelacao, e a rota de tocar
    // precisa dela pra checar se voce e membro.
    serverId: String? = null,
) {
    val koin = GlobalContext.get()
    val prefs = remember { koin.get<DesktopPrefs>() }

    // Soundboard. A lista e buscada UMA vez por constelacao: sons mudam quando
    // alguem sobe um novo, e isso nao acontece no meio de uma call.
    val soundApi = remember { koin.get<SoundApi>() }
    val escopoSons = rememberCoroutineScope()
    var sons by remember(serverId) { mutableStateOf<List<ServerSoundDto>>(emptyList()) }
    var sonsAbertos by remember { mutableStateOf(false) }
    LaunchedEffect(serverId) {
        val sid = serverId ?: return@LaunchedEffect
        runCatching { sons = soundApi.listar(sid).sounds }
    }
    val prefState by prefs.state.collectAsState()
    val status by engine.status.collectAsState()
    val screenOn by engine.screenOn.collectAsState()
    val micOn by engine.micOn.collectAsState()
    val videos by engine.remoteVideos.collectAsState()
    val localScreen by engine.localScreen.collectAsState()
    val sharingCamera by engine.sharingCamera.collectAsState()
    // NAO coletamos localPreview aqui: emitia por frame (60fps) e recompunha o palco
    // inteiro so pra escolher a view. directPreview muda so no start/stop; o frame vai
    // direto pro LocalPreviewView (State lido na fase de desenho).
    val directPreview by engine.directPreview.collectAsState()
    val screenStats by engine.screenStats.collectAsState()
    val quedaAutomatica by engine.quedaAutomatica.collectAsState()

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
            // "Conectado" era MENTIRA quando o canal de áudio não subia: entrar na
            // sala (sinalização) e a voz achar caminho pela rede são duas coisas
            // diferentes, e só a segunda faz alguém ouvir alguém. Dizer "conectado"
            // nas duas escondia justamente a falha que a pessoa está sentindo — ela
            // ficava olhando pro verde sem entender por que ninguém a escuta.
            is VoiceStatus.Connected ->
                if (s.audioLive) "conectado" to Obsidian.success
                else "entrou na sala, mas o áudio ainda não passou" to Obsidian.accent
            is VoiceStatus.Failed -> s.reason to Obsidian.danger
            VoiceStatus.Closed -> "sinal encerrado" to Obsidian.text3
        }
        // Estado da conexao e tempo de sala na mesma linha: sao as duas coisas que se
        // olha de relance. O cronometro em mono pra o numero nao dancar a cada
        // segundo (fonte proporcional muda a largura do "1" pro "8").
        val inicio by engine.inicio.collectAsState()
        val tempo by lembrarTempoDeCall(inicio)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(color = color, fontSize = 11.sp))
            if (tempo.isNotEmpty()) {
                Text(
                    "  ·  $tempo",
                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Palco. Sem transmissão (minha nem de outros) = grid de tiles. Com
        // transmissão = video grande em DESTAQUE + faixa de tiles embaixo. A MINHA
        // tela entra como stream também (auto-preview Discord) e vem PRIMEIRO ->
        // ao transmitir, já vejo em destaque o que estou compartilhando.
        val connected = status as? VoiceStatus.Connected
        val avatarByUser = remember(members) { members.associate { it.userId to it.user.avatarUrl } }
        val tiles = remember(connected, me, micOn, avatarByUser) {
            buildList {
                if (connected != null) {
                    add(Tile("me", "você", connected.mySpeaking, me?.avatarUrl, isMe = true, muted = !micOn))
                    connected.others.forEach { p ->
                        // Foto: primeiro a do token (metadata) — vale em DM tb; se faltar,
                        // cai na lista de membros do servidor.
                        add(Tile(p.identity, p.label, p.speaking, p.avatarUrl ?: avatarByUser[p.identity], isMe = false, muted = false))
                    }
                }
            }
        }

        val streams = remember(localScreen, videos, sharingCamera, directPreview) {
            buildList {
                val meuRotulo = if (sharingCamera) "sua camera" else "sua tela"
                // A minha transmissao entra por UM dos dois caminhos, nunca os dois:
                // com faixa (caminho de sempre) ou so com a previa do cano (motor novo).
                when {
                    localScreen != null -> add(StageStream("eu", meuRotulo, localScreen, isMe = true))
                    directPreview -> add(StageStream("eu", meuRotulo, null, isMe = true))
                }
                videos.forEachIndexed { i, v ->
                    add(StageStream("${v.ownerSid}#$i", v.ownerLabel, v.track, isMe = false, faixa = v.trackSid))
                }
            }
        }
        var watchingId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(streams) {
            if (streams.none { it.id == watchingId }) watchingId = streams.firstOrNull()?.id
        }
        val watching = streams.find { it.id == watchingId }
        // AVISA O SERVIDOR o que esta no palco, pra ele parar de mandar o resto. A minha
        // propria transmissao tem `faixa` nula: ela nao vem da rede, nasce aqui.
        LaunchedEffect(watching?.faixa) { engine.assistir(watching?.faixa) }

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
                        // Minha tela = preview direto da captura (o sink da track local
                        // não entrega frames do CustomVideoSource). Sem preview direto
                        // (fallback GDI) cai pro sink da track.
                        if (w.isMe && directPreview) LocalPreviewView(engine, stageMod)
                        else w.track?.let { RemoteVideoView(it, stageMod) }
                        Spacer(Modifier.height(6.dp))
                        // Na MINHA transmissão mostro os fps reais (envio + captura) e
                        // o motivo se o WebRTC degradou — e como saber se bateu 60.
                        val st = screenStats
                        val txt = when {
                            !w.isMe -> "transmissão de ${w.label}"
                            st == null -> "você está transmitindo"
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
                        // A queda automatica de qualidade dita em voz alta. Fica so na
                        // MINHA transmissao: quem assiste nao tem o que fazer com isso.
                        val queda = quedaAutomatica
                        if (w.isMe && queda != null) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "a qualidade baixou para ${queda.label.substringBefore(" —")} — este computador não estava dando conta do preset anterior",
                                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
                            )
                        }
                        // Abas so quando ha mais de uma transmissão (a minha + de outros).
                        if (streams.size > 1) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                streams.forEach { s ->
                                    val on = s.id == watchingId
                                    Text(
                                        s.label,
                                        style = TextStyle(
                                            color = if (on) Obsidian.accent else Obsidian.text3,
                                            fontSize = 11.sp,
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .border(1.dp, if (on) Obsidian.accent else Obsidian.borderDim, RoundedCornerShape(999.dp))
                                            .clickable { watchingId = s.id }
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
                        tiles.forEach { t ->
                            key(t.key) { PopIn { ParticipantTile(t, Modifier.width(148.dp)) } }
                        }
                    }
                }
            }
        }

        // Controles minimalistas (Discord): botoes de simbolo com borda, sem texto.
        var shareChoices by remember { mutableStateOf<List<ShareChoice>?>(null) }
        // Enquanto a enumeracao de telas/cameras roda (fora da UI, pode levar segundos
        // em maquina fraca), o botao fica aceso: sem isso ele parece nao ter respondido.
        var procurandoFontes by remember { mutableStateOf(false) }
        var settingsOpen by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CallIconButton(
                icon = if (micOn) Lucide.Mic else Lucide.MicOff,
                tone = if (micOn) CallTone.Normal else CallTone.Danger,
                onClick = engine::toggleMic,
            )
            // SOUNDBOARD. Clicar num som NAO mistura audio no seu microfone: o
            // servidor avisa a sala e cada um toca o arquivo original localmente
            // (ver SoundboardPlayer). Passar pelo mic faria o som atravessar o Opus
            // da voz, que e afinado pra fala e esmaga efeito.
            //
            // Sem freio entre disparos — decisao explicita do dono.
            Box {
                CallIconButton(
                    icon = Lucide.Volume2,
                    tone = if (sonsAbertos) CallTone.Active else CallTone.Normal,
                    onClick = { sonsAbertos = !sonsAbertos },
                )
                if (sonsAbertos) {
                    Popup(
                        onDismissRequest = { sonsAbertos = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier
                                .popupReveal(originX = 0.5f, originY = 1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Obsidian.raised)
                                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                        ) {
                            if (sons.isEmpty()) {
                                Text(
                                    "nenhum som aqui ainda",
                                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                            sons.forEach { som ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            val sid = serverId
                                            if (sid != null) escopoSons.launch {
                                                runCatching {
                                                    soundApi.tocar(sid, som.id, TocarSomRequest(channel.id))
                                                }
                                            }
                                            // O menu NAO fecha: soundboard e feita
                                            // pra disparar varios seguidos, e reabrir
                                            // a cada som mataria a graca.
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    LIcon(Lucide.Volume2, tint = Obsidian.text3, size = 13.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(som.name, style = TextStyle(color = Obsidian.text1, fontSize = 12.sp))
                                }
                            }
                        }
                    }
                }
            }
            Box {
                CallIconButton(
                    icon = Lucide.ScreenShare,
                    tone = if (screenOn || procurandoFontes) CallTone.Active else CallTone.Normal,
                    onClick = {
                        if (screenOn) {
                            engine.stopScreenShare()
                        } else if (!procurandoFontes) {
                            // A busca demora o que demorar (em maquina fraca, segundos):
                            // a guarda evita empilhar uma enumeracao nativa por clique
                            // de quem acha que o botao nao respondeu.
                            procurandoFontes = true
                            escopoSons.launch {
                                val choices = shareChoicesOf(engine)
                                procurandoFontes = false
                                when {
                                    choices.isEmpty() -> {}
                                    choices.size == 1 -> startShare(engine, choices.first())
                                    else -> shareChoices = choices
                                }
                            }
                        }
                    },
                )
                // Escolher a fonte: telas + cameras (o notebook tem tela(s) e webcam).
                shareChoices?.let { choices ->
                    Popup(
                        onDismissRequest = { shareChoices = null },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier
                                .popupReveal(originX = 0.5f, originY = 1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Obsidian.raised)
                                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                        ) {
                            choices.forEach { c ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            startShare(engine, c)
                                            shareChoices = null
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    LIcon(
                                        if (c is ShareChoice.Camera) Lucide.Video else Lucide.ScreenShare,
                                        tint = Obsidian.text3, size = 13.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(c.label, style = TextStyle(color = Obsidian.text1, fontSize = 12.sp))
                                }
                            }
                        }
                    }
                }
            }
            Box {
                CallIconButton(
                    icon = Lucide.Settings,
                    tone = if (settingsOpen) CallTone.Active else CallTone.Normal,
                    onClick = { settingsOpen = !settingsOpen },
                )
                if (settingsOpen) {
                    Popup(
                        onDismissRequest = { settingsOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        PopupReveal(originX = 0.5f, originY = 1f) {
                            CallSettingsPanel(
                                current = prefState.screenQuality,
                                onPick = { engine.setScreenQuality(it) },
                                inputs = remember { engine.inputDevices() },
                                outputs = remember { engine.outputDevices() },
                                selectedInput = prefState.audioInput,
                                selectedOutput = prefState.audioOutput,
                                onPickInput = { engine.setInputDevice(it) },
                                onPickOutput = { engine.setOutputDevice(it) },
                            )
                        }
                    }
                }
            }
            CallIconButton(icon = Lucide.PhoneOff, tone = CallTone.Danger, onClick = onLeave)
        }
    }
}

// Config da call (gear): escolher a fluidez da transmissão (aplica ao vivo) e —
// futuro — o cancelador de ruido Krisp. Presets = os 2 do ScreenQuality (720p 60/30).
@Composable
private fun CallSettingsPanel(
    current: ScreenQuality,
    onPick: (ScreenQuality) -> Unit,
    inputs: List<String>,
    outputs: List<String>,
    selectedInput: String?,
    selectedOutput: String?,
    onPickInput: (String?) -> Unit,
    onPickOutput: (String?) -> Unit,
) {
    Column(
        Modifier
            .width(232.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        // 1080p continua fora (o encoder e software e nao segura o fps). O terceiro
        // degrau nao e "mais uma opcao": e o unico preset que roda decente em PC de
        // quatro nucleos, onde o mesmo encoder que custa 8% num PC forte passa da
        // metade da maquina.
        PanelHeader("Transmissao")
        CallSegmented(
            options = listOf(
                "720p60" to ScreenQuality.SMOOTH_720_60,
                "720p30" to ScreenQuality.LIGHT_720_30,
                "540p30" to ScreenQuality.TINY_540_30,
            ),
            selected = current,
            onPick = onPick,
        )
        Spacer(Modifier.height(10.dp))
        PanelHeader("Entrada (microfone)")
        DeviceSelect(inputs, selectedInput, onPickInput)
        Spacer(Modifier.height(8.dp))
        PanelHeader("Saida (som)")
        DeviceSelect(outputs, selectedOutput, onPickOutput)
        Spacer(Modifier.height(10.dp))
        PanelHeader("Microfone")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LIcon(Lucide.Mic, tint = Obsidian.text3, size = 14.dp)
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

// Pilulas segmentadas (um eixo). Ativa = accent; muda na hora.
@Composable
private fun <T> CallSegmented(options: List<Pair<String, T>>, selected: T, onPick: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.base)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            val bg by animateColorAsState(
                if (on) Obsidian.accent.copy(alpha = 0.16f) else Obsidian.base.copy(alpha = 0f),
                tween(140),
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .border(1.dp, if (on) Obsidian.accent.copy(alpha = 0.5f) else Obsidian.borderDim.copy(alpha = 0f), RoundedCornerShape(6.dp))
                    .clickable { onPick(value) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = TextStyle(color = if (on) Obsidian.accent else Obsidian.text2, fontSize = 12.sp))
            }
        }
    }
}

// Seletor de dispositivo de audio (entrada/saida): mostra o atual e abre a lista
// num popup. "Padrao do sistema" = null (primeiro/default do WebRTC/Java Sound).
@Composable
private fun DeviceSelect(options: List<String>, selected: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.base)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected ?: "Padrao do sistema",
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            LIcon(Lucide.ChevronDown, tint = Obsidian.text3, size = 14.dp)
        }
        if (open) {
            Popup(
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(212.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                ) {
                    DeviceRow("Padrao do sistema", selected == null) { onPick(null); open = false }
                    options.forEach { d -> DeviceRow(d, d == selected) { onPick(d); open = false } }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(label: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) Obsidian.hover else Color.Transparent, tween(100))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = TextStyle(color = if (active) Obsidian.accent else Obsidian.text2, fontSize = 12.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            Spacer(Modifier.width(6.dp))
            LIcon(Lucide.Check, tint = Obsidian.accent, size = 13.dp)
        }
    }
}

// Uma transmissão no palco: minha tela (auto-preview) OU de outro participante.
// `track` PODE SER NULA, e essa e a diferenca que apagou a propria transmissao da tela.
//
// No motor novo o video nao passa por objeto nenhum do webrtc-java: ele nasce e morre
// dentro do cano do GStreamer. Nao existe `VideoTrack` pra pendurar aqui. Enquanto este
// campo era obrigatorio, a lista de transmissoes saia VAZIA quem estava transmitindo --
// o palco nao aparecia, e ficava parecendo que nada estava sendo enviado.
//
// Por isso a selecao passou a ser por `id` e nao pelo objeto da faixa: sem faixa, nao ha
// objeto pra comparar.
// `faixa` = o SID da faixa no servidor, pra poder pedir "pausa as outras". Nula na minha
// propria transmissao, que nao vem da rede.
private data class StageStream(
    val id: String,
    val label: String,
    val track: VideoTrack?,
    val isMe: Boolean,
    val faixa: String? = null,
)

// Uma fonte transmissivel: uma tela (monitor) OU uma camera. So uma por vez.
private sealed interface ShareChoice {
    val label: String
    data class Screen(val source: DesktopSource, override val label: String) : ShareChoice
    data class Camera(val device: VideoDevice, override val label: String) : ShareChoice
}

// NUNCA na thread da UI. As duas enumeracoes aqui sao nativas e bloqueantes: a de
// telas constroi um ScreenCapturer, e a de cameras acorda o subsistema de captura do
// Windows e carrega os drivers de webcam dentro do processo. Rodando no clique, isso
// aparecia como um pico de CPU e RAM assim que o menu abria — antes de existir
// qualquer transmissao, o que fazia parecer culpa do encoder.
private suspend fun shareChoicesOf(engine: VoiceEngine): List<ShareChoice> =
    withContext(Dispatchers.IO) {
        // s.title vem do webrtc-java (Java, pode ser null) — protege contra o NPE que já
        // mordeu no seletor so-de-telas.
        val screens = engine.screens().mapIndexed { i, s ->
            ShareChoice.Screen(s, (s.title ?: "").ifBlank { "tela ${i + 1}" })
        }
        val cams = engine.cameras().mapIndexed { i, d ->
            ShareChoice.Camera(d, d.name.ifBlank { "camera ${i + 1}" })
        }
        screens + cams
    }

private fun startShare(engine: VoiceEngine, choice: ShareChoice) {
    when (choice) {
        is ShareChoice.Screen -> engine.startScreenShare(choice.source)
        is ShareChoice.Camera -> engine.startCameraShare(choice.device)
    }
}

// Tom do botao de call (borda + simbolo): normal, ativo (accent) ou perigo.
private enum class CallTone { Normal, Active, Danger }

// Botao minimalista de call (Discord): so o ícone, circulo com borda que troca
// de cor pelo estado. Sem texto. Icone Lucide monocromatico -> o tint AGORA pega
// no glifo (antes, com emoji colorido, so a borda carregava o estado).
@Composable
private fun CallIconButton(icon: ImageVector, tone: CallTone, onClick: () -> Unit) {
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
            .clickScale(interaction)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, tint = fg, size = 20.dp)
    }
}

// Um participante no palco. muted so e conhecido pra mim (o engine não expoe o
// mute dos outros ainda) — nos outros o ícone fica de fora.
private data class Tile(
    // Identidade estavel do participante — e a CHAVE da animação de entrar/sair.
    // Sem ela o Compose reusaria o mesmo slot e a troca de gente seria muda.
    val key: String,
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
        // Mesmo "estouro" da mini-tela: quem entra na call nasce pequeno e passa do
        // tamanho antes de assentar; quem sai encolhe. A key e a identidade — sem ela
        // o Compose reusaria o slot e a troca seria muda.
        tiles.forEach { t ->
            key(t.key) { PopIn { ParticipantTile(t, Modifier.width(164.dp)) } }
        }
    }
}

// Cartao: avatar grande centralizado + nome; anel/halo ambar pulsa ao falar
// (respeita reduzir movimento — fica aceso e parado). Layout estavel: o halo
// vive num Box de tamanho fixo, entao falar não empurra o tile.
@Composable
private fun ParticipantTile(tile: Tile, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val active = LocalWindowActive.current
    // Estrela de fala: UMA fase de órbita, so quando fala + janela visivel +
    // movimento ligado, lida DENTRO do drawBehind. Antes um halo pulsante era
    // lido no corpo e recompunha o cartao inteiro (avatar/nome/mic) 60fps por
    // pessoa falando; agora so redesenha. (Auditoria de movimento, achado #1.)
    val orbit = if (tile.speaking && !reduce && active) {
        rememberInfiniteTransition(label = "orbit-${tile.label}").animateFloat(
            0f, (2.0 * Math.PI).toFloat(),
            infiniteRepeatable(tween(2600, easing = LinearEasing)),
        )
    } else null

    val borderColor by animateColorAsState(
        if (tile.speaking) Obsidian.accent else Obsidian.borderDim,
        tween(140),
    )
    // Inchada ao falar: o card cresce ~4% com mola suave (escala VISUAL via
    // graphicsLayer -> não empurra os vizinhos; cresce por cima). Reduzir movimento
    // = fica maior parado, sem animar.
    val swell by animateFloatAsState(
        targetValue = if (tile.speaking) 1.04f else 1f,
        animationSpec = if (reduce) snap()
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
                Box(Modifier.fillMaxSize().drawBehind {
                    // Halo suave constante; a estrela órbita por cima (ou so o halo,
                    // se movimento reduzido / janela em segundo plano).
                    drawCircle(Obsidian.accent.copy(alpha = 0.16f), radius = size.minDimension / 2f)
                    orbit?.let { ph ->
                        val r = size.minDimension / 2f
                        val ang = ph.value
                        val trail = Offset(center.x + cos(ang - 0.35f) * r, center.y + sin(ang - 0.35f) * r)
                        val star = Offset(center.x + cos(ang) * r, center.y + sin(ang) * r)
                        drawCircle(Obsidian.accent.copy(alpha = 0.35f), radius = 1.5.dp.toPx(), center = trail)
                        drawCircle(Obsidian.accent, radius = 3.dp.toPx(), center = star)
                    }
                })
            }
            DesktopAvatar(tile.avatarUrl, tile.label, 62)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tile.muted) {
                LIcon(Lucide.MicOff, tint = Obsidian.text3, size = 13.dp)
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

// Desenha o bitmap com ContentScale.Fit MANUAL (o Canvas nao faz sozinho): escala pra
// caber mantendo o aspecto e centraliza. Chamado no lambda de DESENHO -> frame novo so
// redesenha, sem recompor+relayout. (Perf P0-1.)
private fun DrawScope.drawImageFit(img: ImageBitmap) {
    val iw = img.width.toFloat()
    val ih = img.height.toFloat()
    if (iw <= 0f || ih <= 0f || size.width <= 0f || size.height <= 0f) return
    val scale = minOf(size.width / iw, size.height / ih)
    val dw = (iw * scale).roundToInt()
    val dh = (ih * scale).roundToInt()
    val left = ((size.width - dw) / 2f).roundToInt()
    val top = ((size.height - dh) / 2f).roundToInt()
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(dw, dh),
    )
}

// Recicla as Skia Images do video. makeRaster COPIA os pixels pra uma imagem raster
// nova = memoria NATIVA (off-heap) que, sozinha, so seria liberada no finalizador do
// GC. A 60fps × ~2MB/frame sao ~120MB/s de memoria nativa; entre os System.gc() do
// skiko (~40s) isso empilha GIGABYTES de RSS (era o "3GB ao transmitir"). Aqui
// fechamos cada imagem de forma DETERMINISTICA, na propria thread do sink: guardamos
// current+prev e so fechamos a de 2 quadros atras — ela saiu do frame.value ha 2
// trocas, entao o render thread ja desenhou muito alem dela (nenhum draw a referencia).
// Resultado: memoria nativa presa em ~2 frames em vez de centenas, sem depender do GC.
private class RasterRecycler {
    private var prev: SkiaImage? = null
    private var prevPrev: SkiaImage? = null

    fun wrap(info: ImageInfo, pixels: ByteArray, rowBytes: Int): ImageBitmap {
        val img = SkiaImage.makeRaster(info, pixels, rowBytes)
        runCatching { prevPrev?.close() } // 2 quadros atras: seguro fechar
        prevPrev = prev
        prev = img
        return img.toComposeImageBitmap()
    }

    fun dispose() {
        runCatching { prev?.close() }
        runCatching { prevPrev?.close() }
        prev = null; prevPrev = null
    }
}

// Renderiza a track remota: sink nativo -> I420 -> RGBA -> ImageBitmap por frame.
// makeRaster copia os bytes (frame nativo pode ser reciclado logo apos o callback);
// o buffer de conversao e reutilizado — so o sink escreve nele. A Skia Image e
// reciclada (RasterRecycler) pra memoria nativa não estourar. O frame e um State
// lido DENTRO do Canvas (fase de desenho) -> cada frame (60fps) redesenha, NAO recompoe.
@Composable
private fun RemoteVideoView(track: VideoTrack, modifier: Modifier = Modifier) {
    val frame = remember(track) { mutableStateOf<ImageBitmap?>(null) }
    DisposableEffect(track) {
        var scratch = ByteArray(0)
        val recycler = RasterRecycler()
        val sink = VideoTrackSink { vf ->
            runCatching {
                val buf = vf.buffer
                val w = buf.width
                val h = buf.height
                val need = w * h * 4
                if (scratch.size != need) scratch = ByteArray(need)
                VideoBufferConverter.convertFromI420(buf, scratch, FourCC.ABGR)
                frame.value = recycler.wrap(
                    ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.OPAQUE),
                    scratch,
                    w * 4,
                )
            }
        }
        track.addSink(sink)
        onDispose { runCatching { track.removeSink(sink) }; recycler.dispose() }
    }
    Canvas(modifier) { frame.value?.let { drawImageFit(it) } }
}

// Auto-preview da MINHA tela: o frame já vem como ImageBitmap PRONTO (o makeRaster
// roda na thread do preview, no VoiceEngine — não na UI). Coletamos o flow AQUI e
// guardamos num State lido DENTRO do Canvas (fase de desenho): cada frame só
// REDESENHA, sem recompor. Antes o ScreenPreview era coletado no palco (recompunha
// tudo 60fps) e a UI fazia o raster — o que travava com video/jogo.
@Composable
private fun LocalPreviewView(engine: VoiceEngine, modifier: Modifier = Modifier) {
    val frame = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(engine) {
        engine.localPreview.collect { frame.value = it?.image }
    }
    Canvas(modifier) { frame.value?.let { drawImageFit(it) } }
}
