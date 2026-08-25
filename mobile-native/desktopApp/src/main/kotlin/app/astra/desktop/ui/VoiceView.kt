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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch
import com.composables.icons.lucide.PhoneOff
import com.composables.icons.lucide.ScreenShare
import com.composables.icons.lucide.Settings
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.prefs.DesktopPrefs
import kotlin.math.cos
import kotlin.math.sin
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.voice.AparelhoDeAudio
import app.astra.desktop.voice.CallEmMalha
import app.astra.desktop.voice.QuadroDeTela
import app.astra.desktop.voice.VoiceStatus
import kotlinx.coroutines.flow.StateFlow
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import org.koin.core.context.GlobalContext

@Composable
fun VoiceView(
    channel: ChannelDto,
    members: List<ServerMemberDto>,
    me: ProfileUserDto?,
    call: CallEmMalha,
    mudo: Boolean,
    aoAlternarMudo: () -> Unit,
    onLeave: () -> Unit,
    serverId: String? = null,
) {
    val koin = GlobalContext.get()
    val prefs = remember { koin.get<DesktopPrefs>() }

    val soundApi = remember { koin.get<SoundApi>() }
    val escopoSons = rememberCoroutineScope()
    var sons by remember(serverId) { mutableStateOf<List<ServerSoundDto>>(emptyList()) }
    var sonsAbertos by remember { mutableStateOf(false) }
    LaunchedEffect(serverId) {
        val sid = serverId ?: return@LaunchedEffect
        runCatching { sons = soundApi.listar(sid).sounds }
    }
    val prefState by prefs.state.collectAsState()
    val status by call.status.collectAsState()
    val microfones by call.microfones.collectAsState()
    val saidas by call.saidas.collectAsState()
    val micOn = !mudo

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "◉ ${channel.name}",
            style = TextStyle(color = Obsidian.accent, fontSize = 18.sp, fontFamily = DmSerif),
        )
        Spacer(Modifier.height(4.dp))
        val (label, color) = when (val s = status) {
            VoiceStatus.Connecting -> "conectando…" to Obsidian.text3
            is VoiceStatus.Connected ->
                if (s.audioLive) "conectado" to Obsidian.success
                else "entrou na sala, mas o áudio ainda não passou" to Obsidian.accent
            is VoiceStatus.Failed -> s.reason to Obsidian.danger
            VoiceStatus.Closed -> "sinal encerrado" to Obsidian.text3
        }
        val inicio by call.inicio.collectAsState()
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

        val connected = status as? VoiceStatus.Connected

        val pessoaPorId = remember(members) { members.associateBy { it.userId } }
        val comTela by call.quemTemTela.collectAsState()
        val mostrandoOutros by call.mostrandoTela.collectAsState()
        val transmitindo by call.transmitindo.collectAsState()
        val relatorio by call.relatorioDaTela.collectAsState()

        val mostrando = remember(mostrandoOutros, transmitindo) {
            if (transmitindo) mostrandoOutros + CallEmMalha.EU else mostrandoOutros
        }

        var telaEscolhida by remember { mutableStateOf<String?>(null) }
        val quemMostra = remember(comTela, mostrando, mostrandoOutros, telaEscolhida) {
            telaEscolhida?.takeIf { it in mostrando }
                ?: mostrandoOutros.firstOrNull { it in comTela }
                ?: mostrandoOutros.firstOrNull()
        }

        val naTela = LocalJanelaNaTela.current

        LaunchedEffect(quemMostra, naTela) { call.assistir(if (naTela) quemMostra else null) }
        DisposableEffect(Unit) {
            onDispose { call.assistir(null) }
        }

        val tiles = remember(connected, me, micOn, pessoaPorId, channel.name, mostrando, quemMostra, transmitindo) {
            buildList {
                if (connected != null) {
                    add(
                        Tile(
                            CallEmMalha.EU, "você", connected.mySpeaking, me?.avatarUrl,
                            isMe = true, muted = !micOn,
                            transmitindo = transmitindo,
                            emCartaz = quemMostra == CallEmMalha.EU,
                        ),
                    )
                    connected.others.forEach { p ->
                        val membro = pessoaPorId[p.identity]
                        val nome = membro?.user?.displayName
                            ?: membro?.user?.username
                            ?: channel.name.ifBlank { "alguém" }
                        add(
                            Tile(
                                p.identity, nome, p.speaking, membro?.user?.avatarUrl,
                                isMe = false, muted = false,
                                transmitindo = p.identity in mostrando,
                                emCartaz = p.identity == quemMostra,
                            ),
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
            if (quemMostra == null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    ParticipantGrid(tiles, call.telasDosOutros) { escolha ->
                        telaEscolhida = if (telaEscolhida == escolha) null else escolha
                    }
                    LinhaDoRelatorio(relatorio)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    val nomeDeQuemMostra = if (quemMostra == CallEmMalha.EU) "sua tela"
                    else pessoaPorId[quemMostra]?.user?.let { it.displayName ?: it.username }
                        ?: channel.name.ifBlank { "alguém" }
                    Box(
                        Modifier.weight(1f).fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Obsidian.void),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (quemMostra !in comTela) {
                            Text(
                                "abrindo a tela de $nomeDeQuemMostra…",
                                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                            )
                        } else {
                            TelaCompartilhada(call.telasDosOutros, quemMostra, Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val quantasTelas = mostrando.size
                    Text(
                        when {
                            quemMostra == CallEmMalha.EU ->
                                "esta é a sua tela, como os outros a veem"
                            quantasTelas > 1 ->
                                "$nomeDeQuemMostra está compartilhando a tela · " +
                                    "clique em outra pessoa para ver a dela"
                            else -> "$nomeDeQuemMostra está compartilhando a tela"
                        },
                        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                    )
                    LinhaDoRelatorio(relatorio)
                    Spacer(Modifier.height(8.dp))
                    ParticipantGrid(tiles, call.telasDosOutros) { escolha ->
                        telaEscolhida = if (telaEscolhida == escolha) null else escolha
                    }
                }
            }
        }

        var settingsOpen by remember { mutableStateOf(false) }
        var transmissaoAvisada by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CallIconButton(
                icon = if (micOn) Lucide.Mic else Lucide.MicOff,
                tone = if (micOn) CallTone.Normal else CallTone.Danger,
                onClick = aoAlternarMudo,
            )
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
            val monitores by call.monitores.collectAsState()
            var escolhendoTela by remember { mutableStateOf(false) }
            Box {
                CallIconButton(
                    icon = Lucide.ScreenShare,
                    tone = if (transmitindo) CallTone.Active else CallTone.Normal,
                    onClick = {
                        if (transmitindo) {
                            call.pararDeTransmitir()
                            transmissaoAvisada = false
                            escolhendoTela = false
                        } else {
                            escolhendoTela = true
                            call.pedirMonitores()
                        }
                    },
                )
                if (escolhendoTela && !transmitindo) {
                    Popup(
                        onDismissRequest = { escolhendoTela = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        SeletorDeTela(monitores) { indice ->
                            escolhendoTela = false
                            val q = prefState.screenQuality
                            call.transmitir(indice, q.width, q.height, q.fps, q.bitrate / 1000)
                            transmissaoAvisada = true
                        }
                    }
                }
                if (transmissaoAvisada && transmitindo) {
                    Popup(
                        onDismissRequest = { transmissaoAvisada = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier
                                .popupReveal(originX = 0.5f, originY = 1f)
                                .width(230.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Obsidian.raised)
                                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                        ) {
                            Text(
                                "Transmitindo",
                                style = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
                            )
                            if (relatorio.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    relatorio,
                                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Quem está na sala recebe a imagem. Para trocar de tela, " +
                                    "pare a transmissão e escolha outra.",
                                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                            )
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
                    LaunchedEffect(Unit) { call.atualizarAparelhos() }
                    Popup(
                        onDismissRequest = { settingsOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        PopupReveal(originX = 0.5f, originY = 1f) {
                            CallSettingsPanel(
                                microfones = microfones,
                                saidas = saidas,
                                microfoneAtual = prefState.audioInput,
                                saidaAtual = prefState.audioOutput,
                                aoTrocarMicrofone = {
                                    prefs.setAudioInput(it)
                                    call.escolherMicrofone(it)
                                },
                                aoTrocarSaida = {
                                    prefs.setAudioOutput(it)
                                    call.escolherSaida(it)
                                },
                            )
                        }
                    }
                }
            }
            CallIconButton(icon = Lucide.PhoneOff, tone = CallTone.Danger, onClick = onLeave)
        }
    }
}

@Composable
private fun CallSettingsPanel(
    microfones: List<AparelhoDeAudio>,
    saidas: List<AparelhoDeAudio>,
    microfoneAtual: String?,
    saidaAtual: String?,
    aoTrocarMicrofone: (String?) -> Unit,
    aoTrocarSaida: (String?) -> Unit,
) {
    Column(
        Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        PanelHeader("Microfone")
        SeletorDeAparelho(microfones, microfoneAtual, aoTrocarMicrofone)
        Spacer(Modifier.height(10.dp))
        PanelHeader("Saida")
        SeletorDeAparelho(saidas, saidaAtual, aoTrocarSaida)
        Spacer(Modifier.height(10.dp))
        Text(
            "Trocar vale na hora, sem sair da call.",
            style = TextStyle(color = Obsidian.text3, fontSize = 10.5.sp),
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun SeletorDeAparelho(
    opcoes: List<AparelhoDeAudio>,
    atual: String?,
    aoEscolher: (String?) -> Unit,
) {
    var aberto by remember { mutableStateOf(false) }
    val nomeAtual = opcoes.firstOrNull { it.id == atual }?.nome ?: "Padrao do Windows"
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.base)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .clickable { aberto = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                nomeAtual,
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            LIcon(Lucide.ChevronDown, tint = Obsidian.text3, size = 14.dp)
        }
        if (aberto) {
            Popup(
                onDismissRequest = { aberto = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                ) {
                    LinhaDeAparelho("Padrao do Windows", atual == null) {
                        aoEscolher(null); aberto = false
                    }
                    opcoes.forEach { ap ->
                        LinhaDeAparelho(ap.nome, ap.id == atual) {
                            aoEscolher(ap.id); aberto = false
                        }
                    }
                    if (opcoes.isEmpty()) {
                        Text(
                            "procurando aparelhos…",
                            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaDeAparelho(rotulo: String, ativo: Boolean, aoClicar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val sobHover by interacao.collectIsHoveredAsState()
    val fundo by animateColorAsState(if (sobHover) Obsidian.hover else Color.Transparent, tween(100))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(fundo)
            .hoverable(interacao)
            .clickable(interactionSource = interacao, indication = null, onClick = aoClicar)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rotulo,
            style = TextStyle(color = if (ativo) Obsidian.accent else Obsidian.text2, fontSize = 12.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (ativo) {
            Spacer(Modifier.width(6.dp))
            LIcon(Lucide.Check, tint = Obsidian.accent, size = 13.dp)
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

private enum class CallTone { Normal, Active, Danger }

@Composable
private fun CallIconButton(
    icon: ImageVector,
    tone: CallTone,
    onClick: () -> Unit,
    habilitado: Boolean = true,
) {
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
    val fg = when {
        !habilitado -> Obsidian.text3.copy(alpha = 0.45f)
        tone == CallTone.Danger -> Obsidian.danger
        tone == CallTone.Active -> Obsidian.accent
        else -> Obsidian.text2
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

@Composable
private fun LinhaDoRelatorio(relatorio: String) {
    if (relatorio.isBlank()) return
    Spacer(Modifier.height(4.dp))
    Text(
        relatorio,
        style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private data class Tile(
    val key: String,
    val label: String,
    val speaking: Boolean,
    val avatarUrl: String?,
    val isMe: Boolean,
    val muted: Boolean,
    val transmitindo: Boolean = false,
    val emCartaz: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantGrid(
    tiles: List<Tile>,
    previa: StateFlow<Map<String, QuadroDeTela>>? = null,
    aoEscolherTela: (String) -> Unit = {},
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.forEach { t ->
            key(t.key) {
                PopIn { ParticipantTile(t, previa, Modifier.width(164.dp)) { aoEscolherTela(t.key) } }
            }
        }
    }
}

@Composable
private fun ParticipantTile(
    tile: Tile,
    previa: StateFlow<Map<String, QuadroDeTela>>? = null,
    modifier: Modifier = Modifier,
    aoEscolherTela: () -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val active = LocalWindowActive.current
    val interacao = remember { MutableInteractionSource() }
    val podeTrocar = tile.transmitindo && (!tile.emCartaz || tile.isMe)
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
    val swell by animateFloatAsState(
        targetValue = if (tile.speaking) 1.04f else 1f,
        animationSpec = if (reduce) snap()
            else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
    )
    Column(
        modifier
            .graphicsLayer { scaleX = swell; scaleY = swell }
            .clip(RoundedCornerShape(14.dp))
            .background(if (tile.emCartaz) Obsidian.overlay else Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .then(
                if (podeTrocar) {
                    Modifier
                        .hoverable(interacao)
                        .clickScale(interacao, formaDoFoco = RoundedCornerShape(14.dp))
                        .clickable(interactionSource = interacao, indication = null, onClick = aoEscolherTela)
                } else Modifier,
            )
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (tile.isMe && tile.transmitindo && previa != null) {
            Box(
                Modifier.width(140.dp).height(79.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Obsidian.void),
                contentAlignment = Alignment.Center,
            ) {
                TelaCompartilhada(previa, CallEmMalha.EU, Modifier.fillMaxSize())
            }
        } else {
            Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
            if (tile.speaking) {
                Box(Modifier.fillMaxSize().drawBehind {
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
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tile.muted) {
                LIcon(Lucide.MicOff, tint = Obsidian.text3, size = 13.dp)
                Spacer(Modifier.width(4.dp))
            }
            if (tile.transmitindo) {
                LIcon(
                    Lucide.ScreenShare,
                    tint = if (tile.emCartaz) Obsidian.text2 else Obsidian.text3,
                    size = 13.dp,
                )
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
