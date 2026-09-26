package app.astra.mobile.feature.voice.presentation

import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.ServerSoundDto
import app.astra.mobile.core.voice.CallStatus
import app.astra.mobile.core.voice.SaidaDeSom
import app.astra.mobile.core.voice.TipoDeSaida
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.BotaoDeTexto
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.ItemDeMenu
import app.astra.mobile.ui.components.SemEscurecerODialogo
import app.astra.mobile.ui.components.SubirAoAbrir
import app.astra.mobile.ui.components.puxavelParaBaixo
import app.astra.mobile.ui.components.rememberEstadoDaFolha
import app.astra.mobile.ui.theme.DmMono
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Bluetooth
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Headphones
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.MicOff
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.PhoneOff
import com.composables.icons.lucide.ScreenShare
import com.composables.icons.lucide.ScreenShareOff
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Speaker
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.VolumeX
import com.composables.icons.lucide.X
import io.livekit.android.compose.ui.RendererType
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import kotlinx.coroutines.delay

private const val ESCALA_MAXIMA = 4f
private const val CONTROLES_SOMEM_MS = 2_600L
private const val AVISO_SOME_MS = 4_000L

@Composable
fun CallScreen(
    aoEncolher: () -> Unit,
    viewModel: CallViewModel = hiltViewModel(),
) {
    val estado by viewModel.state.collectAsState()
    val contexto = LocalContext.current
    val folha = rememberEstadoDaFolha(aoEncolher)
    var telaCheia by rememberSaveable { mutableStateOf<String?>(null) }
    var escolhida by rememberSaveable { mutableStateOf<String?>(null) }

    SemEscurecerODialogo()
    SubirAoAbrir(folha)
    BackHandler(enabled = telaCheia != null) { telaCheia = null }

    LaunchedEffect(estado.sala) { if (estado.sala == null) folha.fechar() }

    val transmitindo = estado.pessoas.filter { it.transmitindo && !it.souEu }
    val quemMostra = escolhida?.takeIf { id -> transmitindo.any { it.identity == id } }
        ?: transmitindo.firstOrNull()?.identity
    LaunchedEffect(quemMostra) { if (quemMostra == null) telaCheia = null }
    val emTelaCheia = telaCheia?.takeIf { it == quemMostra }
    LaunchedEffect(quemMostra, emTelaCheia) { viewModel.assistir(quemMostra, emTelaCheia != null) }
    DisposableEffect(Unit) { onDispose { viewModel.assistir(null, false) } }

    val pedirTela = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val dados = r.data
        if (r.resultCode == Activity.RESULT_OK && dados != null) viewModel.comecarATransmitir(dados)
    }
    val aoTela = {
        if (estado.transmitindo) {
            viewModel.pararDeTransmitir()
        } else {
            val gerente = contexto.getSystemService(MediaProjectionManager::class.java)
            pedirTela.launch(gerente.createScreenCaptureIntent())
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .puxavelParaBaixo(folha, ativo = emTelaCheia == null)
            .background(astraColors.base),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            CabecaDaCall(estado, aoEncolher = { folha.fechar() })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    estado.status == CallStatus.Error -> FalhaDaCall(
                        motivo = estado.erro ?: "A call não está mais no ar.",
                        aoTentarDeNovo = viewModel::tentarDeNovo,
                        aoFechar = viewModel::sair,
                    )
                    estado.status == CallStatus.Connecting && estado.pessoas.isEmpty() -> Conectando()
                    quemMostra != null -> {
                        val quem = transmitindo.first { it.identity == quemMostra }
                        PalcoDaCall(
                            quem = quem,
                            pessoas = estado.pessoas,
                            room = viewModel.room,
                            aoTelaCheia = { telaCheia = quem.identity },
                            aoEscolher = { escolhida = it },
                        )
                    }
                    else -> GradeDaCall(estado.pessoas, aoParar = viewModel::pararDeTransmitir, aoEscolher = { escolhida = it })
                }
            }
            AvisoPassageiro(estado.erro.takeIf { estado.status != CallStatus.Error }, viewModel::esquecerErro)
            val sons by viewModel.sons.collectAsState()
            BarraDaCall(
                estado = estado,
                sons = sons,
                aoMicrofone = viewModel::alternarMudo,
                aoSurdo = viewModel::alternarSurdo,
                aoEscolherSaida = viewModel::escolherSaida,
                aoTocarSom = viewModel::tocarSom,
                aoTela = aoTela,
                aoSair = viewModel::sair,
            )
        }

        val quemEmCheia = estado.pessoas.firstOrNull { it.identity == emTelaCheia }
        if (quemEmCheia != null) {
            TelaCheiaDaTransmissao(quemEmCheia, viewModel.room, aoFechar = { telaCheia = null })
        }
    }
}

@Composable
private fun CabecaDaCall(estado: CallUiState, aoEncolher: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = aoEncolher)
                .semantics { contentDescription = "Encolher a call" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.ChevronDown, contentDescription = null, tint = astraColors.text1, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "◉ ${estado.sala?.nome.orEmpty()}",
                fontFamily = DmSerif,
                fontSize = 19.sp,
                color = astraColors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val tempo = tempoDesde(estado.inicio)
            val (rotulo, cor) = when (estado.status) {
                CallStatus.Connecting -> "conectando…" to astraColors.text3
                CallStatus.Connected -> "conectado" to astraColors.success
                CallStatus.Error -> "sinal encerrado" to astraColors.danger
                CallStatus.Idle -> "sinal encerrado" to astraColors.text3
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rotulo, fontSize = 12.sp, color = cor)
                if (tempo.isNotEmpty() && estado.status == CallStatus.Connected) {
                    Text("  ·  $tempo", fontSize = 12.sp, fontFamily = DmMono, color = astraColors.text3)
                }
            }
        }
    }
}

@Composable
private fun tempoDesde(inicio: Long?): String {
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(inicio) {
        while (inicio != null) {
            agora = System.currentTimeMillis()
            delay(1_000)
        }
    }
    if (inicio == null) return ""
    val s = ((agora - inicio) / 1_000).coerceAtLeast(0)
    return if (s >= 3_600) "%d:%02d:%02d".format(s / 3_600, s % 3_600 / 60, s % 60)
    else "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Conectando() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CosmicSpinner()
        Spacer(Modifier.height(12.dp))
        Text("abrindo o canal de voz…", fontSize = 13.sp, color = astraColors.text3)
    }
}

@Composable
private fun FalhaDaCall(motivo: String, aoTentarDeNovo: () -> Unit, aoFechar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(motivo, style = MaterialTheme.typography.bodyLarge, color = astraColors.text1, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoDeTexto(onClick = aoFechar) { Text("Fechar", color = astraColors.text2) }
            BotaoDeTexto(onClick = aoTentarDeNovo) { Text("Tentar de novo", color = astraColors.accent) }
        }
    }
}

@Composable
private fun GradeDaCall(pessoas: List<PessoaNaTela>, aoParar: () -> Unit, aoEscolher: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        items(pessoas, key = { it.identity }) { p ->
            CartaoDaPessoa(
                p = p,
                aoParar = aoParar.takeIf { p.souEu && p.transmitindo },
                aoTocar = if (p.transmitindo && !p.souEu) ({ aoEscolher(p.identity) }) else null,
            )
        }
    }
}

@Composable
private fun CartaoDaPessoa(p: PessoaNaTela, aoParar: (() -> Unit)?, aoTocar: (() -> Unit)?) {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val chegada = remember { Animatable(if (semMovimento) 1f else 0f) }
    LaunchedEffect(Unit) { if (chegada.value < 1f) chegada.animateTo(1f, tween(240, easing = EaseOutSoft)) }
    val incha by animateFloatAsState(if (p.falando && !semMovimento) 1.04f else 1f, tween(160), label = "incha")
    val contorno by animateColorAsState(if (p.falando) astraColors.accent else astraColors.border, tween(140), label = "contorno")
    val forma = RoundedCornerShape(14.dp)
    val accent = astraColors.accent

    Column(
        Modifier
            .graphicsLayer {
                alpha = chegada.value
                val s = (0.92f + 0.08f * chegada.value) * incha
                scaleX = s
                scaleY = s
            }
            .clip(forma)
            .background(astraColors.raised)
            .border(1.dp, contorno, forma)
            .then(if (aoTocar != null) Modifier.clickable(onClick = aoTocar) else Modifier)
            .semantics { contentDescription = descricaoDe(p) }
            .padding(vertical = 18.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            if (p.falando) {
                Box(Modifier.fillMaxSize().drawBehind { drawCircle(accent.copy(alpha = 0.16f)) })
            }
            AstraAvatar(p.foto, p.nome, size = 72)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                p.surdo -> IconeDeEstado(Lucide.VolumeX, astraColors.danger)
                p.mudo -> IconeDeEstado(Lucide.MicOff, astraColors.text3)
            }
            if (p.transmitindo) IconeDeEstado(Lucide.ScreenShare, astraColors.text2)
            Text(
                p.nome,
                fontSize = 13.sp,
                color = if (p.falando) astraColors.accent else astraColors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (aoParar != null) {
            Spacer(Modifier.height(10.dp))
            SeloDeTransmitindo()
            BotaoDeTexto(onClick = aoParar) { Text("Parar", color = astraColors.danger, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun SeloDeTransmitindo() {
    Text(
        "Transmitindo",
        fontSize = 11.sp,
        color = astraColors.accent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(astraColors.accentDim)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun IconeDeEstado(icone: ImageVector, cor: Color) {
    Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(4.dp))
}

private fun descricaoDe(p: PessoaNaTela): String = buildString {
    append(p.nome)
    if (p.falando) append(", falando")
    if (p.surdo) append(", sem ouvir") else if (p.mudo) append(", microfone fechado")
    if (p.transmitindo) append(", transmitindo a tela")
}

@Composable
private fun PalcoDaCall(
    quem: PessoaNaTela,
    pessoas: List<PessoaNaTela>,
    room: Room?,
    aoTelaCheia: () -> Unit,
    aoEscolher: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(astraColors.void)
                .clickable(onClickLabel = "ver em tela cheia", onClick = aoTelaCheia),
            contentAlignment = Alignment.Center,
        ) {
            val tela = quem.tela
            if (tela != null) {
                VideoTrackView(
                    videoTrack = tela,
                    modifier = Modifier.fillMaxSize(),
                    passedRoom = room,
                    scaleType = ScaleType.FitInside,
                    rendererType = RendererType.Texture,
                )
            } else {
                Text("abrindo a tela de ${quem.nome}…", fontSize = 13.sp, color = astraColors.text3)
            }
            Text(
                "${quem.nome} está transmitindo · toque para ver em tela cheia",
                fontSize = 11.sp,
                color = astraColors.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(astraColors.void.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pessoas, key = { it.identity }) { p ->
                RostoPequeno(
                    p = p,
                    emCartaz = p.identity == quem.identity,
                    aoTocar = if (p.transmitindo && !p.souEu) ({ aoEscolher(p.identity) }) else null,
                )
            }
        }
    }
}

@Composable
private fun RostoPequeno(p: PessoaNaTela, emCartaz: Boolean, aoTocar: (() -> Unit)?) {
    val contorno by animateColorAsState(if (p.falando) astraColors.accent else astraColors.border, tween(140), label = "contorno")
    val forma = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(76.dp)
            .clip(forma)
            .background(if (emCartaz) astraColors.overlay else astraColors.raised)
            .border(1.dp, contorno, forma)
            .then(if (aoTocar != null) Modifier.clickable(onClick = aoTocar) else Modifier)
            .semantics { contentDescription = descricaoDe(p) }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AstraAvatar(p.foto, p.nome, size = 40)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                p.surdo -> IconeDeEstado(Lucide.VolumeX, astraColors.danger)
                p.mudo -> IconeDeEstado(Lucide.MicOff, astraColors.text3)
            }
            Text(
                p.nome,
                fontSize = 11.sp,
                color = if (p.falando) astraColors.accent else astraColors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TelaCheiaDaTransmissao(quem: PessoaNaTela, room: Room?, aoFechar: () -> Unit) {
    val janela = (LocalView.current.parent as? DialogWindowProvider)?.window
    val vista = LocalView.current
    DisposableEffect(janela) {
        val controle = janela?.let { WindowCompat.getInsetsController(it, vista) }
        controle?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controle?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controle?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }
    var tamanho by remember { mutableStateOf(IntSize.Zero) }
    var controles by remember { mutableStateOf(true) }
    var mexeu by remember { mutableLongStateOf(0L) }
    LaunchedEffect(mexeu, controles) {
        if (!controles) return@LaunchedEffect
        delay(CONTROLES_SOMEM_MS)
        controles = false
    }
    val opacidade by animateFloatAsState(if (controles) 1f else 0f, tween(200), label = "controles")

    fun limitar(alvo: Offset, e: Float): Offset {
        val maxX = tamanho.width * (e - 1f) / 2f
        val maxY = tamanho.height * (e - 1f) / 2f
        return Offset(alvo.x.coerceIn(-maxX, maxX), alvo.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { tamanho = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controles = !controles
                        mexeu = System.currentTimeMillis()
                    },
                    onDoubleTap = {
                        escala = 1f
                        deslocamento = Offset.Zero
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nova = (escala * zoom).coerceIn(1f, ESCALA_MAXIMA)
                    escala = nova
                    deslocamento = if (nova <= 1f) Offset.Zero else limitar(deslocamento + pan, nova)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val tela = quem.tela
        if (tela != null) {
            VideoTrackView(
                videoTrack = tela,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = escala
                        scaleY = escala
                        translationX = deslocamento.x
                        translationY = deslocamento.y
                    },
                passedRoom = room,
                scaleType = ScaleType.FitInside,
                rendererType = RendererType.Texture,
            )
        } else {
            Text("abrindo a tela de ${quem.nome}…", fontSize = 13.sp, color = astraColors.text3)
        }
        if (opacidade > 0.01f) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = opacidade }
                    .background(astraColors.void.copy(alpha = 0.6f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = aoFechar)
                        .semantics { contentDescription = "Sair da tela cheia" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.X, contentDescription = null, tint = astraColors.text1, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Tela de ${quem.nome} · dois toques voltam ao tamanho original",
                    fontSize = 12.sp,
                    color = astraColors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AvisoPassageiro(texto: String?, aoSumir: () -> Unit) {
    LaunchedEffect(texto) {
        if (texto == null) return@LaunchedEffect
        delay(AVISO_SOME_MS)
        aoSumir()
    }
    if (texto == null) return
    Text(
        texto,
        fontSize = 13.sp,
        color = astraColors.danger,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

private enum class Tom { NORMAL, ATIVO, PERIGO, SAIR }

@Composable
private fun BarraDaCall(
    estado: CallUiState,
    sons: List<ServerSoundDto>,
    aoMicrofone: () -> Unit,
    aoSurdo: () -> Unit,
    aoEscolherSaida: (String) -> Unit,
    aoTocarSom: (String) -> Unit,
    aoTela: () -> Unit,
    aoSair: () -> Unit,
) {
    val noAr = estado.status == CallStatus.Connected
    var saidasAbertas by remember { mutableStateOf(false) }
    var sonsAbertos by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotaoDaCall(
            icone = if (estado.mudo) Lucide.MicOff else Lucide.Mic,
            tom = if (estado.mudo) Tom.PERIGO else Tom.NORMAL,
            rotulo = if (estado.mudo) "Abrir o microfone" else "Fechar o microfone",
            habilitado = noAr,
            aoTocar = aoMicrofone,
        )
        BotaoDaCall(
            icone = if (estado.surdo) Lucide.VolumeX else Lucide.Volume2,
            tom = if (estado.surdo) Tom.PERIGO else Tom.NORMAL,
            rotulo = if (estado.surdo) "Voltar a ouvir" else "Ensurdecer",
            habilitado = noAr,
            aoTocar = aoSurdo,
        )
        Box {
            BotaoDaCall(
                icone = iconeDaSaida(estado.saidaAtual),
                tom = if (saidasAbertas) Tom.ATIVO else Tom.NORMAL,
                rotulo = "Saída de som: ${estado.saidaAtual?.nome ?: "automática"}",
                habilitado = noAr && estado.saidas.size > 1,
                aoTocar = { saidasAbertas = true },
            )
            DropdownMenu(
                expanded = saidasAbertas,
                onDismissRequest = { saidasAbertas = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = astraColors.overlay,
                border = BorderStroke(1.dp, astraColors.border),
            ) {
                estado.saidas.forEach { saida ->
                    ItemDeMenu(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(iconeDaSaida(saida), contentDescription = null, tint = astraColors.text2, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(saida.nome, color = astraColors.text1, modifier = Modifier.weight(1f, fill = false))
                                if (saida.chave == estado.saidaAtual?.chave) {
                                    Spacer(Modifier.width(10.dp))
                                    Icon(Lucide.Check, contentDescription = "em uso", tint = astraColors.accent, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        onClick = {
                            saidasAbertas = false
                            aoEscolherSaida(saida.chave)
                        },
                    )
                }
            }
        }
        Box {
            BotaoDaCall(
                icone = Lucide.Music,
                tom = if (sonsAbertos) Tom.ATIVO else Tom.NORMAL,
                rotulo = "Sons da constelação",
                habilitado = noAr && sons.isNotEmpty(),
                aoTocar = { sonsAbertos = true },
            )
            DropdownMenu(
                expanded = sonsAbertos,
                onDismissRequest = { sonsAbertos = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = astraColors.overlay,
                border = BorderStroke(1.dp, astraColors.border),
            ) {
                sons.forEach { som ->
                    ItemDeMenu(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Lucide.Volume2, contentDescription = null, tint = astraColors.text3, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(som.name, color = astraColors.text1)
                            }
                        },
                        onClick = {
                            sonsAbertos = false
                            aoTocarSom(som.id)
                        },
                    )
                }
            }
        }
        BotaoDaCall(
            icone = if (estado.transmitindo) Lucide.ScreenShareOff else Lucide.ScreenShare,
            tom = if (estado.transmitindo) Tom.ATIVO else Tom.NORMAL,
            rotulo = if (estado.transmitindo) "Parar de transmitir a tela" else "Transmitir a tela",
            habilitado = noAr,
            aoTocar = aoTela,
        )
        BotaoDaCall(
            icone = Lucide.PhoneOff,
            tom = Tom.SAIR,
            rotulo = "Sair da call",
            habilitado = true,
            aoTocar = aoSair,
        )
    }
}

private fun iconeDaSaida(saida: SaidaDeSom?): ImageVector = when (saida?.tipo) {
    TipoDeSaida.ALTO_FALANTE, null -> Lucide.Speaker
    TipoDeSaida.APARELHO -> Lucide.Smartphone
    TipoDeSaida.FONE_COM_FIO -> Lucide.Headphones
    TipoDeSaida.BLUETOOTH -> Lucide.Bluetooth
}

@Composable
private fun BotaoDaCall(
    icone: ImageVector,
    tom: Tom,
    rotulo: String,
    habilitado: Boolean,
    aoTocar: () -> Unit,
) {
    val comVibracao = LocalAppPrefs.current.haptics
    val haptico = LocalHapticFeedback.current
    val (fundo, borda, tinta) = when (tom) {
        Tom.NORMAL -> Triple(astraColors.raised, astraColors.border, astraColors.text1)
        Tom.ATIVO -> Triple(astraColors.accentDim, astraColors.accent.copy(alpha = 0.5f), astraColors.accent)
        Tom.PERIGO -> Triple(astraColors.danger.copy(alpha = 0.14f), astraColors.danger.copy(alpha = 0.5f), astraColors.danger)
        Tom.SAIR -> Triple(astraColors.danger, astraColors.danger, Color.White)
    }
    Box(
        Modifier
            .size(56.dp)
            .graphicsLayer { alpha = if (habilitado) 1f else 0.4f }
            .clip(CircleShape)
            .background(fundo)
            .border(1.dp, borda, CircleShape)
            .clickable(enabled = habilitado) {
                if (comVibracao) {
                    haptico.performHapticFeedback(if (tom == Tom.SAIR) HapticFeedbackType.Reject else HapticFeedbackType.ToggleOn)
                }
                aoTocar()
            }
            .semantics {
                contentDescription = rotulo
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = null, tint = tinta, modifier = Modifier.size(22.dp))
    }
}
