package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.MicOff
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.VolumeX
import androidx.compose.runtime.rememberCoroutineScope
import app.astra.mobile.core.network.SoundApi
import app.astra.mobile.core.network.dto.ServerSoundDto
import app.astra.mobile.core.network.dto.TocarSomRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.composables.icons.lucide.PhoneOff
import com.composables.icons.lucide.ScreenShare
import com.composables.icons.lucide.Settings
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.prefs.DesktopPrefs
import kotlin.math.roundToInt
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.voice.AparelhoDeAudio
import app.astra.desktop.voice.CallNaSala
import app.astra.desktop.voice.Permissao
import app.astra.desktop.voice.PermissoesWindows
import app.astra.desktop.voice.QuadroDeTela
import app.astra.desktop.voice.VoiceStatus
import kotlinx.coroutines.flow.StateFlow
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import org.koin.core.context.GlobalContext
import app.astra.desktop.ui.theme.Tipo

@Composable
fun VoiceView(
    channel: ChannelDto,
    members: List<ServerMemberDto>,
    me: ProfileUserDto?,
    call: CallNaSala,
    mudo: Boolean,
    aoAlternarMudo: () -> Unit,
    ensurdecido: Boolean,
    aoAlternarEnsurdecer: () -> Unit,
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

        FaixaDoFirewall()

        val connected = status as? VoiceStatus.Connected

        val pessoaPorId = remember(members) { members.associateBy { it.userId } }
        val comTela by call.quemTemTela.collectAsState()
        val mostrandoOutros by call.mostrandoTela.collectAsState()
        val transmitindo by call.transmitindo.collectAsState()
        val relatorio by call.relatorioDaTela.collectAsState()
        val ritmos by call.ritmoDeQuemMostra.collectAsState()
        val volumes by call.volumes.collectAsState()
        val mudos by call.mudosDaSala.collectAsState()
        val surdos by call.surdosDaSala.collectAsState()

        val mostrando = remember(mostrandoOutros, transmitindo) {
            if (transmitindo) mostrandoOutros + CallNaSala.EU else mostrandoOutros
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

        val tiles = remember(
            connected, me, micOn, ensurdecido, pessoaPorId, channel.name,
            mostrando, quemMostra, transmitindo, mudos, surdos,
        ) {
            buildList {
                if (connected != null) {
                    add(
                        Tile(
                            CallNaSala.EU, "você", connected.mySpeaking, me?.avatarUrl,
                            isMe = true, muted = !micOn, surdo = ensurdecido,
                            transmitindo = transmitindo,
                            emCartaz = quemMostra == CallNaSala.EU,
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
                                isMe = false,
                                muted = p.identity in mudos,
                                surdo = p.identity in surdos,
                                transmitindo = p.identity in mostrando,
                                emCartaz = p.identity == quemMostra,
                                fonte = membro?.user?.displayFont,
                            ),
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
            if (quemMostra == null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    ParticipantGrid(
                        tiles,
                        call.telasDosOutros,
                        volumeDe = { volumes[it] ?: CallNaSala.VOLUME_CHEIO },
                        aoMudarVolume = call::definirVolume,
                    ) { escolha ->
                        telaEscolhida = if (telaEscolhida == escolha) null else escolha
                    }
                }
            } else {
                val nomeDeQuemMostra = if (quemMostra == CallNaSala.EU) "sua tela"
                else pessoaPorId[quemMostra]?.user?.let { it.displayName ?: it.username }
                    ?: channel.name.ifBlank { "alguém" }

                PalcoDaTela(
                    legenda = when {
                        quemMostra == CallNaSala.EU -> "esta é a sua tela, como os outros a veem"
                        mostrando.size > 1 ->
                            "$nomeDeQuemMostra está compartilhando a tela · " +
                                "clique em outra pessoa para ver a dela"
                        else -> "$nomeDeQuemMostra está compartilhando a tela"
                    },
                    rostos = {
                        ParticipantGrid(
                            tiles,
                            call.telasDosOutros,
                            volumeDe = { volumes[it] ?: CallNaSala.VOLUME_CHEIO },
                            aoMudarVolume = call::definirVolume,
                        ) { escolha ->
                            telaEscolhida = if (telaEscolhida == escolha) null else escolha
                        }
                    },
                    tela = {
                        if (quemMostra !in comTela) {
                            Text(
                                "abrindo a tela de $nomeDeQuemMostra…",
                                style = Tipo.descricao,
                            )
                        } else {
                            TelaCompartilhada(call.telasDosOutros, quemMostra, Modifier.fillMaxSize())
                        }
                    },
                )
            }
        }

        var settingsOpen by remember { mutableStateOf(false) }
        var transmissaoAvisada by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CallIconButton(
                icon = if (micOn) Lucide.Mic else Lucide.MicOff,
                tone = if (micOn) CallTone.Normal else CallTone.Danger,
                rotulo = if (micOn) "Fechar o microfone" else "Abrir o microfone",
                onClick = aoAlternarMudo,
            )
            CallIconButton(
                icon = if (ensurdecido) Lucide.VolumeX else Lucide.Volume2,
                tone = if (ensurdecido) CallTone.Danger else CallTone.Normal,
                rotulo = if (ensurdecido) "Voltar a ouvir" else "Ensurdecer",
                onClick = aoAlternarEnsurdecer,
            )
            Box {
                CallIconButton(
                    icon = Lucide.Music,
                    tone = if (sonsAbertos) CallTone.Active else CallTone.Normal,
                    rotulo = "Sons da constelação",
                    onClick = { sonsAbertos = !sonsAbertos },
                )
                if (sonsAbertos) {
                    Popup(
                        popupPositionProvider = AboveAnchor,
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
                                    style = Tipo.descricao,
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
            val janelas by call.janelas.collectAsState()
            var escolhendoTela by remember { mutableStateOf(false) }
            var fonteNoAr by remember { mutableStateOf<FonteEscolhida?>(null) }
            var numerosAbertos by remember { mutableStateOf(false) }
            Box {
                val salaDePe = connected != null
                CallSplitButton(
                    icon = Lucide.ScreenShare,
                    tone = if (transmitindo) CallTone.Active else CallTone.Normal,
                    rotulo = when {
                        transmitindo -> "Trocar ou parar a transmissão"
                        !salaDePe -> "A chamada está se restabelecendo"
                        else -> "Transmitir a tela"
                    },
                    rotuloDaSeta = "Números da transmissão",
                    setaAberta = numerosAbertos,
                    habilitado = transmitindo || salaDePe,
                    onClick = {
                        escolhendoTela = true
                        call.pedirMonitores()
                    },
                    aoAbrirSeta = { numerosAbertos = !numerosAbertos },
                )
                if (numerosAbertos) {
                    Popup(
                        popupPositionProvider = AboveAnchor,
                        onDismissRequest = { numerosAbertos = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        NumerosDaTela(
                            minha = relatorio,
                            deQuemAssisto = ritmos[quemMostra].takeIf { quemMostra != CallNaSala.EU },
                        )
                    }
                }
                if (escolhendoTela) {
                    Popup(
                        popupPositionProvider = NoMeioDaJanela,
                        onDismissRequest = { escolhendoTela = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        SeletorDeTela(
                            monitores,
                            janelas,
                            noAr = fonteNoAr.takeIf { transmitindo },
                            aoPedirJanelas = { call.pedirJanelas() },
                            aoParar = {
                                escolhendoTela = false
                                call.pararDeTransmitir()
                                fonteNoAr = null
                                transmissaoAvisada = false
                            },
                        ) { fonte ->
                            escolhendoTela = false
                            val q = prefState.screenQuality
                            when (fonte) {
                                is FonteEscolhida.Monitor ->
                                    call.transmitir(fonte.indice, q.width, q.height, q.fps, q.bitrate / 1000)
                                is FonteEscolhida.Janela ->
                                    call.transmitirJanela(fonte.id, q.width, q.height, q.fps, q.bitrate / 1000)
                            }
                            fonteNoAr = fonte
                            transmissaoAvisada = true
                        }
                    }
                }
                if (transmissaoAvisada && transmitindo) {
                    Popup(
                        popupPositionProvider = AboveAnchor,
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
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Quem está na sala recebe a imagem. Para trocar de tela ou " +
                                    "encerrar, abra o mesmo botão de novo.",
                                style = Tipo.apoio,
                            )
                        }
                    }
                }
            }
            Box {
                CallIconButton(
                    icon = Lucide.Settings,
                    tone = if (settingsOpen) CallTone.Active else CallTone.Normal,
                    rotulo = "Configurações da chamada",
                    onClick = { settingsOpen = !settingsOpen },
                )
                if (settingsOpen) {
                    LaunchedEffect(Unit) { call.atualizarAparelhos() }
                    Popup(
                        popupPositionProvider = AboveAnchor,
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
            Spacer(Modifier.width(8.dp))
            CallIconButton(
                icon = Lucide.PhoneOff,
                tone = CallTone.Danger,
                rotulo = "Sair da chamada",
                onClick = onLeave,
                preenchido = true,
            )
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
        PanelHeader("Saída")
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
    val nomeAtual = opcoes.firstOrNull { it.id == atual }?.nome ?: "Padrão do Windows"
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
                style = Tipo.rotulo,
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
                    LinhaDeAparelho("Padrão do Windows", atual == null) {
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
                            style = Tipo.apoio,
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

private const val FIREWALL_DISPENSADO = "firewall:dispensado"

@Composable
private fun FaixaDoFirewall() {
    val store = remember { GlobalContext.get().get<SessionStore>() }
    var recado by remember { mutableStateOf<String?>(null) }
    var liberando by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    suspend fun conferir() {
        val c = withContext(Dispatchers.IO) { PermissoesWindows.uma(Permissao.REDE) }
        recado = c.explica.takeIf { c.podeResolver }
    }

    LaunchedEffect(Unit) {
        if (store.uiPref(FIREWALL_DISPENSADO) == "1") return@LaunchedEffect
        conferir()
    }

    val texto = recado ?: return
    val forma = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(forma)
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, forma)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(texto, style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp))
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AcaoDaFaixa(
                rotulo = if (liberando) "liberando…" else "liberar de vez",
                cor = Obsidian.accent,
                habilitado = !liberando,
            ) {
                liberando = true
                escopo.launch {
                    withContext(Dispatchers.IO) { PermissoesWindows.liberarNoFirewall() }
                    conferir()
                    liberando = false
                }
            }
            Spacer(Modifier.width(10.dp))
            AcaoDaFaixa(rotulo = "agora não", cor = Obsidian.text3, habilitado = !liberando) {
                store.setUiPref(FIREWALL_DISPENSADO, "1")
                recado = null
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun AcaoDaFaixa(rotulo: String, cor: Color, habilitado: Boolean, aoClicar: () -> Unit) {
    val fonte = remember { MutableInteractionSource() }
    val sobre by fonte.collectIsHoveredAsState()
    val forma = RoundedCornerShape(6.dp)
    Text(
        rotulo,
        style = TextStyle(
            color = if (habilitado) cor else Obsidian.text3.copy(alpha = 0.45f),
            fontSize = 12.sp,
        ),
        modifier = Modifier
            .clickScale(fonte, formaDoFoco = forma)
            .clip(forma)
            .background(if (sobre && habilitado) Obsidian.hover else Color.Transparent)
            .hoverable(fonte)
            .clickable(
                interactionSource = fonte,
                indication = null,
                enabled = habilitado,
                onClick = aoClicar,
            )
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

private enum class CallTone { Normal, Active, Danger }

private val ALTURA_DO_BOTAO = 46.dp
private val RAIO_DO_BOTAO = 23.dp
private val LARGURA_DA_SETA = 30.dp

@Composable
private fun corDaBorda(tone: CallTone) = animateColorAsState(
    when (tone) {
        CallTone.Danger -> Obsidian.danger
        CallTone.Active -> Obsidian.accent
        CallTone.Normal -> Obsidian.borderMid
    },
    tween(140),
).value

private fun corDoGlifo(tone: CallTone, habilitado: Boolean) = when {
    !habilitado -> Obsidian.text3.copy(alpha = 0.45f)
    tone == CallTone.Danger -> Obsidian.danger
    tone == CallTone.Active -> Obsidian.accent
    else -> Obsidian.text2
}

@Composable
private fun fundoDoBotao(aceso: Boolean) = animateColorAsState(
    if (aceso) Obsidian.hover else Obsidian.raised.copy(alpha = 0.4f),
    tween(140),
).value

@Composable
private fun CallSplitButton(
    icon: ImageVector,
    tone: CallTone,
    rotulo: String,
    rotuloDaSeta: String,
    setaAberta: Boolean,
    habilitado: Boolean,
    onClick: () -> Unit,
    aoAbrirSeta: () -> Unit,
) {
    val inteiro = RoundedCornerShape(RAIO_DO_BOTAO)
    val esquerda = RoundedCornerShape(topStart = RAIO_DO_BOTAO, bottomStart = RAIO_DO_BOTAO)
    val direita = RoundedCornerShape(topEnd = RAIO_DO_BOTAO, bottomEnd = RAIO_DO_BOTAO)
    val borda = corDaBorda(tone)
    val glifo = corDoGlifo(tone, habilitado)

    Box(Modifier.height(ALTURA_DO_BOTAO).clip(inteiro)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val daTela = remember { MutableInteractionSource() }
            val sobreATela by daTela.collectIsHoveredAsState()
            val fundoDaTela = fundoDoBotao(sobreATela)
            Box(
                Modifier
                    .size(ALTURA_DO_BOTAO)
                    .clickScale(daTela, formaDoFoco = esquerda)
                    .clip(esquerda)
                    .background(fundoDaTela)
                    .hoverable(daTela)
                    .clickable(
                        interactionSource = daTela,
                        indication = null,
                        enabled = habilitado,
                        onClick = onClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(icon, tint = glifo, size = 20.dp, rotulo = rotulo)
                DicaAcima(rotulo, sobreATela && habilitado)
            }

            Box(Modifier.width(1.dp).height(22.dp).background(Obsidian.borderMid))

            val daSeta = remember { MutableInteractionSource() }
            val sobreASeta by daSeta.collectIsHoveredAsState()
            val fundoDaSeta = fundoDoBotao(sobreASeta || setaAberta)
            Box(
                Modifier
                    .width(LARGURA_DA_SETA)
                    .height(ALTURA_DO_BOTAO)
                    .clickScale(daSeta, formaDoFoco = direita)
                    .clip(direita)
                    .background(fundoDaSeta)
                    .hoverable(daSeta)
                    .clickable(interactionSource = daSeta, indication = null, onClick = aoAbrirSeta),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(
                    if (setaAberta) Lucide.ChevronDown else Lucide.ChevronUp,
                    tint = if (setaAberta) Obsidian.accent else Obsidian.text3,
                    size = 16.dp,
                    rotulo = rotuloDaSeta,
                )
                DicaAcima(rotuloDaSeta, sobreASeta && !setaAberta)
            }
        }
        Box(Modifier.matchParentSize().border(1.dp, borda, inteiro))
    }
}

@Composable
private fun CallIconButton(
    icon: ImageVector,
    tone: CallTone,
    rotulo: String,
    onClick: () -> Unit,
    habilitado: Boolean = true,
    preenchido: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border = corDaBorda(tone)
    val fg = if (preenchido) Obsidian.void else corDoGlifo(tone, habilitado)
    val fundoCheio by animateColorAsState(
        if (hovered) Obsidian.danger else Obsidian.danger.copy(alpha = 0.82f),
        tween(140),
    )
    val bg = if (preenchido) fundoCheio else fundoDoBotao(hovered)
    Box {
        Box(
            Modifier
                .size(ALTURA_DO_BOTAO)
                .clickScale(interaction)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, border, CircleShape)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, enabled = habilitado, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            LIcon(icon, tint = fg, size = 20.dp, rotulo = rotulo)
        }
        DicaAcima(rotulo, hovered && habilitado)
    }
}

@Composable
private fun NumerosDaTela(minha: String, deQuemAssisto: String?) {
    Column(
        Modifier
            .popupReveal(originX = 0.5f, originY = 1f)
            .width(250.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        if (minha.isBlank() && deQuemAssisto.isNullOrBlank()) {
            Text(
                "Nenhuma tela no ar. Os números aparecem quando alguém começa a transmitir.",
                style = Tipo.apoio,
            )
            return@Column
        }
        if (minha.isNotBlank()) {
            Text("Subindo", style = TextStyle(color = Obsidian.text1, fontSize = 12.sp))
            Spacer(Modifier.height(3.dp))
            Text(
                minha,
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
            )
        }
        if (!deQuemAssisto.isNullOrBlank()) {
            if (minha.isNotBlank()) Spacer(Modifier.height(8.dp))
            Text("Chegando", style = TextStyle(color = Obsidian.text1, fontSize = 12.sp))
            Spacer(Modifier.height(3.dp))
            Text(
                deQuemAssisto,
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
            )
        }
    }
}

private const val QUANTO_O_PALCO_ESPERA = 2_600L

private const val COMPASSO_DO_MOUSE = 250L

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PalcoDaTela(
    legenda: String,
    rostos: @Composable () -> Unit,
    tela: @Composable BoxScope.() -> Unit,
) {
    var mexeu by remember { mutableStateOf(0L) }
    var aberto by remember { mutableStateOf(true) }
    val semMovimento = LocalReduceMotion.current

    LaunchedEffect(mexeu) {
        aberto = true
        delay(QUANTO_O_PALCO_ESPERA)
        aberto = false
    }

    val opacidade = animateFloatAsState(
        if (aberto) 1f else 0f,
        tween(if (semMovimento) 0 else 200),
    )
    val barraNaTela by remember { derivedStateOf { opacidade.value > 0.01f } }

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.void)
            .onPointerEvent(PointerEventType.Move) {
                val agora = System.currentTimeMillis()
                if (agora - mexeu > COMPASSO_DO_MOUSE) mexeu = agora
            },
        contentAlignment = Alignment.Center,
    ) {
        tela()

        if (barraNaTela) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = opacidade.value }
                    .background(Obsidian.void.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(legenda, style = TextStyle(color = Obsidian.text2, fontSize = 11.sp))
                Spacer(Modifier.height(8.dp))
                rostos()
            }
        }
    }
}

private data class Tile(
    val key: String,
    val label: String,
    val speaking: Boolean,
    val avatarUrl: String?,
    val isMe: Boolean,
    val muted: Boolean,
    val surdo: Boolean = false,
    val transmitindo: Boolean = false,
    val emCartaz: Boolean = false,
    val fonte: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantGrid(
    tiles: List<Tile>,
    previa: StateFlow<Map<String, QuadroDeTela>>? = null,
    volumeDe: (String) -> Int = { 100 },
    aoMudarVolume: (String, Int) -> Unit = { _, _ -> },
    aoEscolherTela: (String) -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val largura = larguraDoBloco(tiles.size, maxWidth, maxHeight)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ESPACO_ENTRE_BLOCOS, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(ESPACO_ENTRE_BLOCOS),
        ) {
            tiles.forEach { t ->
                key(t.key) {
                    PopIn {
                        if (t.isMe) {
                            ParticipantTile(t, previa, largura) { aoEscolherTela(t.key) }
                        } else {
                            EditorialContextMenu(entries = {
                                listOf(
                                    MenuEntry.VolumeSub(
                                        label = "volume",
                                        porcento = volumeDe(t.key),
                                        onChange = { aoMudarVolume(t.key, it) },
                                    ),
                                )
                            }) {
                                ParticipantTile(t, previa, largura) { aoEscolherTela(t.key) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ESPACO_ENTRE_BLOCOS = 10.dp
private val LARGURA_MINIMA_DO_BLOCO = 164.dp
private val LARGURA_MAXIMA_DO_BLOCO = 288.dp

private val SOBRA_ABAIXO_DO_RETRATO = 70.dp
private const val FATIA_DO_RETRATO = 0.45f

private fun larguraDoBloco(quantos: Int, espacoNaLargura: Dp, espacoNaAltura: Dp): Dp {
    if (quantos <= 0) return LARGURA_MINIMA_DO_BLOCO

    val cabemNaLinha = ((espacoNaLargura + ESPACO_ENTRE_BLOCOS) /
        (LARGURA_MINIMA_DO_BLOCO + ESPACO_ENTRE_BLOCOS)).toInt().coerceAtLeast(1)
    val colunas = minOf(quantos, cabemNaLinha)
    val linhas = (quantos + colunas - 1) / colunas

    val pelaLargura = (espacoNaLargura - ESPACO_ENTRE_BLOCOS * (colunas - 1)) / colunas
    if (!espacoNaAltura.isFinite) {
        return pelaLargura.coerceIn(LARGURA_MINIMA_DO_BLOCO, LARGURA_MAXIMA_DO_BLOCO)
    }

    val alturaPorLinha = (espacoNaAltura - ESPACO_ENTRE_BLOCOS * (linhas - 1)) / linhas
    val pelaAltura = (alturaPorLinha - SOBRA_ABAIXO_DO_RETRATO) / FATIA_DO_RETRATO
    return minOf(pelaLargura, pelaAltura).coerceIn(LARGURA_MINIMA_DO_BLOCO, LARGURA_MAXIMA_DO_BLOCO)
}

private const val DEGRAU_DO_RETRATO = 8

private fun diametroDoRetrato(largura: Dp): Dp {
    val bruto = (largura * FATIA_DO_RETRATO).coerceIn(62.dp, 150.dp)
    val degraus = (bruto.value / DEGRAU_DO_RETRATO).roundToInt()
    return (degraus * DEGRAU_DO_RETRATO).dp.coerceIn(62.dp, 150.dp)
}

@Composable
private fun ParticipantTile(
    tile: Tile,
    previa: StateFlow<Map<String, QuadroDeTela>>? = null,
    largura: Dp = LARGURA_MINIMA_DO_BLOCO,
    aoEscolherTela: () -> Unit = {},
) {
    val retrato = diametroDoRetrato(largura)
    val modifier = Modifier.width(largura)
    val reduce = LocalReduceMotion.current
    val interacao = remember { MutableInteractionSource() }
    val podeTrocar = tile.transmitindo && (!tile.emCartaz || tile.isMe)

    val borderColor by animateColorAsState(
        if (tile.speaking) Obsidian.accent else Obsidian.borderDim,
        tween(140),
    )
    val swell = animateFloatAsState(
        targetValue = if (tile.speaking) 1.04f else 1f,
        animationSpec = if (reduce) snap()
            else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
    )
    Column(
        modifier
            .graphicsLayer { scaleX = swell.value; scaleY = swell.value }
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
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Obsidian.void),
                contentAlignment = Alignment.Center,
            ) {
                TelaCompartilhada(previa, CallNaSala.EU, Modifier.fillMaxSize())
            }
        } else {
            Box(Modifier.size(retrato + 12.dp), contentAlignment = Alignment.Center) {
            if (tile.speaking) {
                Box(Modifier.fillMaxSize().drawBehind {
                    drawCircle(Obsidian.accent.copy(alpha = 0.16f), radius = size.minDimension / 2f)
                })
            }
            DesktopAvatar(tile.avatarUrl, tile.label, retrato.value.toInt())
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tile.surdo) {
                LIcon(Lucide.VolumeX, tint = Obsidian.danger, size = 13.dp, rotulo = "não está ouvindo")
                Spacer(Modifier.width(4.dp))
            } else if (tile.muted) {
                LIcon(Lucide.MicOff, tint = Obsidian.text3, size = 13.dp, rotulo = "microfone fechado")
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
                    fontFamily = tile.fonte?.let { profileFontFamily(it) },
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private object NoMeioDaJanela : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset = androidx.compose.ui.unit.IntOffset(
        x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
        y = ((windowSize.height - popupContentSize.height) / 2).coerceAtLeast(0),
    )
}
