package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.AtividadeDoSistema
import app.astra.desktop.ModoTransmissao
import app.astra.desktop.prefs.DensityPref
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.FontSizePref
import app.astra.desktop.prefs.UiFps
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.MutualServerDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import com.composables.icons.lucide.Info
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

internal fun temPrevia(tab: SettingsTab): Boolean = when (tab) {
    SettingsTab.SESSIONS, SettingsTab.PERMISSIONS,
    SettingsTab.ABOUT, SettingsTab.DIAGNOSTICS, SettingsTab.BOTS,
    SettingsTab.SHORTCUTS,
    SettingsTab.PETS,
    SettingsTab.NAME_COLORS,
    SettingsTab.ACCOUNT -> false
    else -> true
}

@Composable
internal fun SettingsPreview(
    tab: SettingsTab,
    me: ProfileUserDto?,
    p: DesktopPrefs.Prefs,
    draft: ProfileDraft,
    modifier: Modifier = Modifier,
    acoesDoCartao: AcoesDoCartao? = null,
) {
    Column(modifier) {
        FieldLabel("previa")
        Box {
            when (tab) {
                SettingsTab.PROFILE -> ProfileCardPreview(me, draft, acoesDoCartao)
                SettingsTab.NOTIFICATIONS -> NotifPreviewCard(p.reduceMotionEff, p.avisoDiscreto)
                SettingsTab.PRIVACY -> AtividadePreview(p.atividadeVisivel)
                SettingsTab.APPEARANCE, SettingsTab.ACCESSIBILITY -> UiSamplePreview(p.fontSize, p.density)
                SettingsTab.PERFORMANCE -> CostMeter(p)
                SettingsTab.VOICE -> VoicePreview(p)
                SettingsTab.SESSIONS, SettingsTab.ABOUT, SettingsTab.DIAGNOSTICS,
                SettingsTab.PERMISSIONS, SettingsTab.BOTS, SettingsTab.SHORTCUTS,
                SettingsTab.PETS, SettingsTab.NAME_COLORS, SettingsTab.ACCOUNT -> Unit
            }
            if (tab != SettingsTab.PROFILE) {
                Box(Modifier.matchParentSize().engoleOPonteiro())
            }
        }
    }
}

private fun Modifier.engoleOPonteiro(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

internal data class ProfileDraft(
    val displayName: String = "",
    val pronouns: String = "",
    val bio: String = "",
    val statusEmoji: String = "",
    val customStatus: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val bannerPositionY: Int = 50,
    val bannerScale: Int = 100,
    val profileTheme: String? = null,
    val displayFont: String? = null,
) {
    companion object {
        fun from(me: ProfileUserDto?) = ProfileDraft(
            displayName = me?.displayName.orEmpty(),
            pronouns = me?.pronouns.orEmpty(),
            bio = me?.bio.orEmpty(),
            statusEmoji = me?.statusEmoji.orEmpty(),
            customStatus = me?.customStatus.orEmpty(),
            avatarUrl = me?.avatarUrl,
            bannerUrl = me?.bannerUrl,
            bannerColor = me?.bannerColor,
            bannerPositionY = me?.bannerPositionY ?: 50,
            bannerScale = me?.bannerScale ?: 100,
            profileTheme = me?.profileTheme,
            displayFont = me?.displayFont,
        )
    }
}

@Composable
private fun ProfileCardPreview(
    me: ProfileUserDto?,
    draft: ProfileDraft?,
    acoes: AcoesDoCartao? = null,
) {
    if (me == null) {
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Text("carregando…", style = Tipo.descricao)
        }
        return
    }
    val dados = DadosDoCartao(
        nome = draft?.displayName?.trim()?.ifBlank { null } ?: me.displayName ?: me.username,
        username = me.username,
        avatarUrl = draft?.avatarUrl ?: me.avatarUrl,
        bannerUrl = draft?.bannerUrl ?: me.bannerUrl,
        bannerColor = draft?.bannerColor ?: me.bannerColor,
        bannerPositionY = draft?.bannerPositionY ?: me.bannerPositionY ?: 50,
        bannerScale = draft?.bannerScale ?: me.bannerScale ?: 100,
        pronomes = draft?.pronouns ?: me.pronouns,
        bio = draft?.bio ?: me.bio,
        statusEmoji = draft?.statusEmoji ?: me.statusEmoji,
        recado = draft?.customStatus ?: me.customStatus,
        fonte = draft?.displayFont ?: me.displayFont,
        status = me.effectiveStatus,
        criadoEm = me.createdAt,
    )
    var mutuais by remember(me.id) { mutableStateOf<List<MutualServerDto>>(emptyList()) }
    LaunchedEffect(me.id) {
        mutuais = withContext(Dispatchers.IO) {
            runCatching { GlobalContext.get().get<UserApi>().profile(me.id).data?.mutualServers }
                .getOrNull().orEmpty()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        CartaoDaPrevia(
            rotulo = "cartão completo",
            larguraReal = LARGURA_CARTAO_COMPLETO,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ProfileCard(
                dados,
                CardVariante.COMPLETO,
                Modifier.fillMaxWidth(),
                servidoresEmComum = mutuais,
                animar = false,
                acoesDaFoto = acoes?.let { { it.foto() } },
                acoesDoBanner = acoes?.let { { it.banner() } },
            )
        }
    }
}

@Composable
private fun CartaoDaPrevia(
    rotulo: String,
    larguraReal: Dp,
    modifier: Modifier = Modifier,
    conteudo: @Composable () -> Unit,
) {
    Column(modifier) {
        RotuloDaPrevia(rotulo)
        BoxWithConstraints {
            val larguraRealPx = with(LocalDensity.current) { larguraReal.roundToPx() }
            val escala = (constraints.maxWidth.toFloat() / larguraRealPx).coerceAtMost(1f)
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val p = measurable.measure(Constraints.fixedWidth(larguraRealPx))
                        layout((p.width * escala).toInt(), (p.height * escala).toInt()) { p.place(0, 0) }
                    }
                    .graphicsLayer {
                        scaleX = escala
                        scaleY = escala
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                conteudo()
            }
        }
    }
}

@Composable
private fun RotuloDaPrevia(texto: String) {
    Text(
        texto.uppercase(),
        style = TextStyle(color = Obsidian.text3, fontSize = 9.sp, letterSpacing = 1.4.sp),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

private const val PASSEIO_DO_AVISO = 14f

@Composable
internal fun PrivacySection(
    prefState: DesktopPrefs.Prefs,
    prefs: DesktopPrefs,
    me: ProfileUserDto?,
    onSalvou: () -> Unit,
) {
    Column {
        Text("Quem pode te mandar sussurro", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
        Spacer(Modifier.height(4.dp))
        Text(
            "vale para conversa NOVA. quem já está falando com você continua falando — " +
                "apertar isto não cala ninguém que você já estava respondendo.",
            style = Tipo.apoio,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(10.dp))
        FiltroDeSussurro(me, onSalvou)
        Spacer(Modifier.height(8.dp))
        Text(
            "quem for barrado recebe a mesma recusa de quem foi bloqueado. não dá para " +
                "descobrir, do outro lado, qual é o seu ajuste.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )

        SettingsDivider()
        ModoTransmissaoBloco(prefState, prefs)

        SettingsDivider()
        Text("O que os outros veem", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            "Mostrar o que estou usando",
            "quem te vê passa a ver o nome do programa em primeiro plano",
            prefState.atividadeVisivel,
            prefs::setAtividadeVisivel,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "sai apenas o nome do programa, o mesmo que o Windows mostra no Gerenciador de Tarefas.",
            style = Tipo.rotulo,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "o título da janela nunca é lido. Ele entregaria arquivo aberto, aba, endereço e busca — " +
                "por isso navegador aparece só como “Navegando”, sem dizer qual nem o quê.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "nada disso é guardado: some sozinho um minuto depois de você fechar o Astra, " +
                "e desligar aqui apaga na hora.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "desligado, o Astra nem chega a olhar qual programa está na frente.",
            style = Tipo.apoio,
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
}

@Composable
private fun ModoTransmissaoBloco(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    val detectado by ModoTransmissao.detectado.collectAsState()
    val ativo by ModoTransmissao.ativo.collectAsState()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Modo transmissão", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
        if (ativo) {
            Spacer(Modifier.width(8.dp))
            Text(
                if (detectado && !p.modoTransmissao) "valendo — programa de transmissão aberto" else "valendo",
                style = TextStyle(color = Obsidian.accent, fontSize = 11.sp),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "com ele valendo: o aviso da bandeja perde nome e texto, o som de aviso não toca, " +
            "e o seu e-mail vira máscara na aba Conta.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(10.dp))
    ToggleRow(
        "Ligar agora", "vale enquanto estiver marcado",
        p.modoTransmissao, prefs::setModoTransmissao,
    )
    ToggleRow(
        "Ligar sozinho quando eu abrir o OBS",
        "também vale para Streamlabs, XSplit e Twitch Studio",
        p.modoTransmissaoAuto, prefs::setModoTransmissaoAuto,
    )
    if (p.modoTransmissaoAuto) {
        Spacer(Modifier.height(10.dp))
        InfoNote(
            "O que o Astra olha para detectar",
            "Para saber que o OBS está aberto é preciso olhar a lista de programas em " +
                "execução — uma leitura mais ampla que a do “o que estou usando”, que só " +
                "olha a janela da frente. Por isso isto é uma escolha sua, e não o padrão.\n\n" +
                "O que se faz com essa lista: comparar o nome do executável com quatro " +
                "nomes conhecidos de programas de transmissão. Nada além disso é lido — " +
                "título de janela continua sendo o que o Astra nunca olha.\n\n" +
                "O resultado é um sim ou não que fica nesta máquina. Nem o servidor vê.",
        )
    }
}

@Composable
private fun FiltroDeSussurro(me: ProfileUserDto?, onSalvou: () -> Unit) {
    val koin = GlobalContext.get()
    val escopo = rememberCoroutineScope()
    val doServidor = me?.dmPrivacy ?: "all"
    var escolhido by remember(doServidor) { mutableStateOf(doServidor) }
    var erro by remember { mutableStateOf<String?>(null) }

    RadioList(
        listOf(
            "qualquer pessoa" to "all",
            "quem divide constelação comigo" to "shared",
            "só meus amigos" to "friends",
        ),
        escolhido,
    ) { novo ->
        if (novo == escolhido) return@RadioList
        val anterior = escolhido
        escolhido = novo
        erro = null
        escopo.launch {
            val r = runCatching {
                koin.get<UserApi>().updateProfile(UpdateProfileRequest(dmPrivacy = novo))
            }
            if (r.isSuccess) {
                onSalvou()
            } else {
                escolhido = anterior
                erro = "não deu para salvar. verifique a conexão e tente de novo."
            }
        }
    }
    erro?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = TextStyle(color = Obsidian.danger, fontSize = 11.sp))
    }
}

@Composable
private fun AtividadePreview(ligado: Boolean) {
    val agora = if (ligado) {
        remember { AtividadeDoSistema.emPrimeiroPlano() } ?: "Navegando"
    } else null
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Obsidian.base))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("você", style = Tipo.corpo)
                if (agora != null) {
                    Text(agora, style = Tipo.apoio)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (ligado) "é isto que aparece para quem vê você." else "ninguém vê nada além do seu nome.",
            style = Tipo.apoio,
        )
    }
}

@Composable
private fun NotifPreviewCard(reduceMotion: Boolean, discreto: Boolean) {
    val t = rememberInfiniteTransition(label = "toast")
    val cycle by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
        label = "toastCycle",
    )
    var dx = 0f
    var a = 1f
    if (!reduceMotion) {
        when {
            cycle < 0.12f -> {
                val k = EaseOutStd.transform(cycle / 0.12f)
                dx = (1f - k) * PASSEIO_DO_AVISO
                a = k
            }
            cycle < 0.84f -> { dx = 0f; a = 1f }
            else -> {
                val k = EaseOutSoft.transform((cycle - 0.84f) / 0.16f)
                dx = k * PASSEIO_DO_AVISO
                a = 1f - k
            }
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().alpha(0.4f)) {
            UiSamplePreview(FontSizePref.MD, DensityPref.COMFORTABLE)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .fillMaxWidth(0.92f)
                .offset(x = dx.dp)
                .alpha(a),
        ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Obsidian.accentDim),
                contentAlignment = Alignment.Center,
            ) {
                Text("A", style = TextStyle(color = Obsidian.accent, fontSize = 15.sp, fontFamily = DmSerif))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Astra", style = Tipo.nota)
                if (discreto) {
                    Text("sussurro novo", style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmSerif))
                    Text("sem nome e sem texto — é tudo que aparece.", style = Tipo.apoio)
                } else {
                    Text("ana", style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmSerif))
                    Text("e ai, bora marcar a call?", style = TextStyle(color = Obsidian.text2, fontSize = 11.sp))
                }
            }
        }
        }
    }
}

@Composable
private fun UiSamplePreview(fontSize: FontSizePref, density: DensityPref) {
    val s = fontSize.scale
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised.copy(alpha = 0.6f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#", style = TextStyle(color = Obsidian.text3, fontSize = 15.sp))
            Spacer(Modifier.width(6.dp))
            Text("geral", style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmSerif))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Obsidian.overlay.copy(alpha = 0.5f))
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            SampleMsg("ana", "e ai, bora marcar a call?", s)
            Spacer(Modifier.height((density.topDp).dp))
            SampleMsg("você", "fechou, 21h entao", s)
        }
        Box(
            Modifier.padding(11.dp).fillMaxWidth().clip(RoundedCornerShape(9.dp))
                .background(Obsidian.void.copy(alpha = 0.5f))
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text("escrever…", style = TextStyle(color = Obsidian.text3, fontSize = (13 * s).sp))
        }
    }
}

@Composable
private fun SampleMsg(name: String, text: String, scale: Float) {
    Row(verticalAlignment = Alignment.Top) {
        val c = userColor(name)
        Box(
            Modifier.size((26 * scale).dp).clip(CircleShape).background(c),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), style = TextStyle(color = Obsidian.textInv, fontSize = (11 * scale).sp))
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(name, style = TextStyle(color = c, fontSize = (12 * scale).sp, fontFamily = DmSerif))
            Text(text, style = TextStyle(color = Obsidian.text2, fontSize = (13 * scale).sp, lineHeight = (18 * scale).sp))
        }
    }
}

@Composable
private fun CostMeter(p: DesktopPrefs.Prefs) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("custo estimado", style = TextStyle(color = Obsidian.text1, fontSize = 14.sp, fontFamily = DmSerif))
        Spacer(Modifier.height(14.dp))
        val gpu = gpuCost(p)
        val cpu = cpuCost(p)
        CostBar("GPU", gpu)
        Spacer(Modifier.height(12.dp))
        CostBar("CPU", cpu)
        Spacer(Modifier.height(14.dp))
        Text(costVerdict(gpu, cpu), style = Tipo.apoio)
    }
}

@Composable
private fun CostBar(label: String, value: Float) {
    val v by animateFloatAsState(value, tween(340), label = "cost-$label")
    val col = when {
        v < 0.36f -> Obsidian.success
        v < 0.68f -> Obsidian.accent
        else -> Obsidian.danger
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(color = Obsidian.text2, fontSize = 11.sp), modifier = Modifier.width(38.dp))
            Spacer(Modifier.weight(1f))
            Text(costWord(v), style = TextStyle(color = col, fontSize = 10.sp))
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                .background(Obsidian.void.copy(alpha = 0.6f)),
        ) {
            Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(col))
        }
    }
}

private fun gpuCost(p: DesktopPrefs.Prefs): Float {
    if (p.performanceMode) return 0.08f
    var c = 0.06f
    if (p.auroraOn) c += 0.18f + p.auroraQuality.octaves * 0.09f
    if (p.starsOn) c += 0.14f
    if (p.windowTransparent) c += 0.08f
    val mul = when (p.uiFps) { UiFps.FREE -> 1f; UiFps.CAP60 -> 0.82f; UiFps.CAP30 -> 0.6f }
    return (0.06f + (c - 0.06f) * mul).coerceIn(0.05f, 1f)
}

private fun cpuCost(p: DesktopPrefs.Prefs): Float {
    if (p.performanceMode) return 0.06f
    var c = 0.05f
    if (p.auroraOn) c += 0.08f
    if (p.starsOn) c += 0.07f
    if (!p.reduceMotionEff) c += 0.05f
    val mul = when (p.uiFps) { UiFps.FREE -> 1f; UiFps.CAP60 -> 0.85f; UiFps.CAP30 -> 0.65f }
    return (0.05f + (c - 0.05f) * mul).coerceIn(0.04f, 1f)
}

private fun costWord(v: Float) = when {
    v < 0.36f -> "leve"
    v < 0.68f -> "medio"
    else -> "pesado"
}

private fun costVerdict(gpu: Float, cpu: Float): String {
    val m = maxOf(gpu, cpu)
    return when {
        m < 0.36f -> "leve — sobra folga para jogar ou transmitir junto."
        m < 0.68f -> "equilibrado — visual completo sem pesar."
        else -> "pesado — o modo desempenho corta isso num toque."
    }
}

@Composable
private fun VoicePreview(p: DesktopPrefs.Prefs) {
    Column(Modifier.fillMaxWidth()) {
        var testing by remember { mutableStateOf(false) }
        val q = p.screenQuality
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Obsidian.raised.copy(alpha = 0.5f))
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                    .background(Obsidian.void.copy(alpha = 0.6f))
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("sua tela", style = Tipo.descricao)
            }
            Spacer(Modifier.height(9.dp))
            Text(
                "${q.height}p · ${q.fps}fps · ${q.bitrate / 1_000_000} Mbps",
                style = Tipo.rotulo,
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Obsidian.raised.copy(alpha = 0.5f))
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Text("seu microfone", style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmSerif))
            Spacer(Modifier.height(10.dp))
            MicMeter(testing, p.micSensitivity)
            Spacer(Modifier.height(10.dp))
            AboutButton(if (testing) "parar teste" else "testar microfone", accent = !testing) {
                testing = !testing
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (p.micNoiseSuppression) "supressao de ruido: ligada" else "supressao de ruido: desligada",
                style = Tipo.nota,
            )
        }
    }
}

@Composable
private fun MicMeter(active: Boolean, threshold: Float = 0f) {
    var level by remember { mutableFloatStateOf(0f) }
    var available by remember { mutableStateOf(true) }
    DisposableEffect(active) {
        if (!active) {
            level = 0f
            available = true
            return@DisposableEffect onDispose { }
        }
        val running = AtomicBoolean(true)
        var line: TargetDataLine? = null
        val worker = thread(isDaemon = true, name = "astra-mic-preview") {
            val fmt = AudioFormat(16_000f, 16, 1, true, false)
            val l = runCatching {
                (AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, fmt)) as TargetDataLine)
                    .apply { open(fmt); start() }
            }.getOrNull()
            if (l == null) { available = false; return@thread }
            line = l
            val buf = ByteArray(1024)
            while (running.get()) {
                val n = runCatching { l.read(buf, 0, buf.size) }.getOrDefault(-1)
                if (n <= 0) break
                var sum = 0.0
                var i = 0
                while (i < n - 1) {
                    val sample = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
                    sum += sample.toDouble() * sample
                    i += 2
                }
                val rms = kotlin.math.sqrt(sum / (n / 2)).toFloat()
                val norm = (rms / 7000f).coerceIn(0f, 1f)
                level = if (norm > level) norm else level * 0.82f + norm * 0.18f
            }
            runCatching { l.stop(); l.close() }
        }
        onDispose {
            running.set(false)
            runCatching { line?.close() }
        }
    }
    if (!available) {
        Text("microfone indisponivel", style = Tipo.apoio)
        return
    }
    val lvl by animateFloatAsState(level, tween(90), label = "micLvl")
    val meterColor by animateColorAsState(
        when {
            lvl < 0.10f -> Obsidian.text3
            lvl < 0.45f -> Obsidian.warning
            else -> Obsidian.success
        },
        tween(220),
        label = "micColor",
    )
    Box(Modifier.fillMaxWidth().height(30.dp)) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val bars = 16
            for (i in 0 until bars) {
                val shape = 0.45f + 0.55f * sin((i + 0.5f) / bars * PI).toFloat()
                val h = (lvl * shape).coerceIn(0.05f, 1f)
                Box(
                    Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(2.dp))
                        .background(meterColor.copy(alpha = 0.4f + 0.5f * h)),
                )
            }
        }
        if (threshold > 0f) {
            Row(Modifier.fillMaxSize()) {
                val f = threshold.coerceIn(0.02f, 0.98f)
                Spacer(Modifier.weight(f))
                Box(Modifier.width(2.dp).fillMaxHeight().background(Obsidian.accent.copy(alpha = 0.9f)))
                Spacer(Modifier.weight(1f - f))
            }
        }
    }
}

@Composable
internal fun MicSensitivityRow(value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sensibilidade de entrada", style = Tipo.corpo)
            Text(
                if (value <= 0f) "sempre transmite" else "${(value * 100).toInt()}%",
                style = Tipo.apoio,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Obsidian.void.copy(alpha = 0.6f)))
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Obsidian.accent))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val f = value.coerceIn(0f, 1f)
                if (f > 0f) Spacer(Modifier.weight(f))
                Box(Modifier.size(14.dp).clip(CircleShape).background(Obsidian.accent).border(2.dp, Obsidian.raised, CircleShape))
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "abaixo desse nível o mic não transmite. 0 = sempre aberto. teste o mic acima para calibrar.",
            style = Tipo.apoio,
        )
    }
}
