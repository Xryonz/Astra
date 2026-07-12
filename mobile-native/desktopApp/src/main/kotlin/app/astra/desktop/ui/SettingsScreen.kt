package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import app.astra.desktop.prefs.AuroraQuality
import app.astra.desktop.prefs.DensityPref
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.FontSizePref
import app.astra.desktop.prefs.ScreenQuality
import app.astra.desktop.prefs.UiFps
import app.astra.desktop.ui.theme.AccentOptions
import app.astra.desktop.ui.theme.BgOptions
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.ThemePreset
import app.astra.desktop.ui.theme.ThemePresets
import app.astra.desktop.ui.theme.accentOption
import app.astra.desktop.ui.theme.bgOption
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ChangePasswordRequest
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.SetPasswordRequest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

private enum class SettingsTab(val label: String, val sub: String) {
    ACCOUNT("Conta", "email e senha"),
    NOTIFICATIONS("Notificacoes", "avisos na bandeja"),
    APPEARANCE("Aparencia", "cores, fonte, densidade"),
    PERFORMANCE("Desempenho", "graficos, animacoes, fps"),
    VOICE("Voz", "microfone e transmissao"),
}

// Settings em TAKEOVER estilo Discord (decisao do dono): ocupa o shell inteiro,
// nav de secoes na esquerda + conteudo na direita. Secoes v1: Conta (senha),
// Notificacoes (toggles do tray) e Movimento (reduzir animacoes).
@Composable
fun SettingsScreen(me: ProfileUserDto?, prefs: DesktopPrefs, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(SettingsTab.ACCOUNT) }
    val prefState by prefs.state.collectAsState()

    // ESC fecha: foco no root do takeover + captura da tecla.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Obsidian.base.copy(alpha = 0.97f))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            // Nav das secoes
            Column(
                Modifier.width(220.dp).fillMaxHeight().padding(horizontal = 12.dp, vertical = 18.dp),
            ) {
                Text(
                    "configuracoes",
                    style = TextStyle(color = Obsidian.text1, fontSize = 18.sp, fontFamily = DmSerif),
                    modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
                )
                SettingsTab.entries.forEach { t ->
                    NavRow(t.label, t.sub, active = t == tab) { tab = t }
                }
            }

            // Conteudo da secao — coluna capada (~720) estilo Discord: nao esparrama
            // pelo palco todo (o "enxuto" que o dono pediu). O Box segura a coluna
            // encostada a esquerda; os controles leem como uma coluna so em vez de
            // soltos num vazao grande a direita. Titulo + fechar vivem dentro dela.
            Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(
                Modifier.align(Alignment.TopStart).widthIn(max = 720.dp).fillMaxWidth()
                    .fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tab.label,
                        style = TextStyle(color = Obsidian.text1, fontSize = 22.sp, fontFamily = DmSerif),
                        modifier = Modifier.weight(1f),
                    )
                    // Fechar (ESC tambem, via foco no shell) volta pro shell.
                    val hov = remember { MutableInteractionSource() }
                    val h by hov.collectIsHoveredAsState()
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (h) Obsidian.hover else Obsidian.overlay)
                            .border(1.dp, Obsidian.borderMid, CircleShape)
                            .hoverable(hov)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        LIcon(Lucide.X, tint = Obsidian.text2, size = 15.dp)
                    }
                }
                Spacer(Modifier.height(20.dp))

                when (tab) {
                    SettingsTab.ACCOUNT -> AccountSection(me)
                    SettingsTab.NOTIFICATIONS -> Column {
                        ToggleRow(
                            "Sussurros (DMs)", "avisa quando chega mensagem privada",
                            prefState.notifyDms, prefs::setNotifyDms,
                        )
                        ToggleRow(
                            "Atividade de canal", "avisa nova mensagem nas constelacoes",
                            prefState.notifyChannels, prefs::setNotifyChannels,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "os avisos aparecem na bandeja so com a janela fechada ou minimizada.",
                            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                            modifier = Modifier.widthIn(max = 460.dp),
                        )
                    }
                    SettingsTab.APPEARANCE -> AppearanceSection(prefState, prefs)
                    SettingsTab.PERFORMANCE -> PerformanceSection(prefState, prefs)
                    SettingsTab.VOICE -> VoiceSection(prefState, prefs)
                }
            }
            }
        }
    }
}

@Composable
private fun AccountSection(me: ProfileUserDto?) {
    ReadRow("email", me?.email ?: "—")
    Spacer(Modifier.height(8.dp))
    ReadRow("usuario", me?.let { "@${it.username}" } ?: "—")
    Spacer(Modifier.height(22.dp))
    Text(
        if (me?.hasPassword == false) "definir senha" else "trocar senha",
        style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif),
    )
    Spacer(Modifier.height(4.dp))
    if (me?.hasPassword == false) {
        Text(
            "conta google sem senha — defina uma pra entrar por email tambem.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        )
    }
    Spacer(Modifier.height(12.dp))
    PasswordForm(hasPassword = me?.hasPassword != false)
}

@Composable
private fun PasswordForm(hasPassword: Boolean) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // texto + ok?

    if (hasPassword) {
        PasswordField("senha atual", current) { current = it; msg = null }
        Spacer(Modifier.height(8.dp))
    }
    PasswordField("nova senha", next) { next = it; msg = null }
    Spacer(Modifier.height(8.dp))
    PasswordField("confirmar nova senha", confirm) { confirm = it; msg = null }
    Spacer(Modifier.height(12.dp))

    msg?.let { (text, ok) ->
        Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
        Spacer(Modifier.height(8.dp))
    }

    val canSave = !busy && next.length >= 8 && next == confirm && (!hasPassword || current.isNotBlank())
    Text(
        if (busy) "salvando…" else "salvar",
        style = TextStyle(color = if (canSave) Obsidian.accent else Obsidian.text3, fontSize = 13.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (canSave) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(enabled = canSave) {
                busy = true
                msg = null
                scope.launch {
                    val result = runCatching {
                        val api = koin.get<UserApi>()
                        if (hasPassword) api.changePassword(ChangePasswordRequest(current, next))
                        else api.setPassword(SetPasswordRequest(next))
                    }
                    busy = false
                    if (result.isSuccess) {
                        current = ""; next = ""; confirm = ""
                        msg = "senha atualizada" to true
                    } else {
                        msg = "nao deu — confira a senha atual" to false
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (next.isNotEmpty() && next.length < 8) {
        Spacer(Modifier.height(6.dp))
        Text("minimo 8 caracteres", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
    }
}

@Composable
private fun PasswordField(placeholder: String, value: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            // Campo de formulario (~420), NAO a coluna toda. A ordem importa:
            // widthIn ANTES de fillMaxWidth — invertido, o fillMaxWidth fixava a
            // largura no pai e o cap de 360 era reconstrangido de volta (era o bug
            // do input de senha esticando pelo eixo X inteiro).
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReadRow(label: String, value: String) {
    Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TextStyle(color = Obsidian.text3, fontSize = 12.sp), modifier = Modifier.width(80.dp))
        Text(value, style = TextStyle(color = Obsidian.text1, fontSize = 13.sp))
    }
}

@Composable
private fun NavRow(label: String, sub: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (active) Obsidian.active else if (hovered) Obsidian.hover else androidx.compose.ui.graphics.Color.Transparent,
        tween(120),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = TextStyle(
                color = if (active || hovered) Obsidian.text1 else Obsidian.text2,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            ),
        )
        Text(sub, style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
    }
}

// Aba Voz: qualidade da transmissao de tela (presets) + processamento do mic.
@Composable
private fun VoiceSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    Text("Transmissao de tela", style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "vale ao iniciar a transmissao. o padrao 1080p60 e o minimo que combinamos.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(10.dp))
    RadioList(
        ScreenQuality.entries.map { it.label to it },
        p.screenQuality, prefs::setScreenQuality,
    )

    Spacer(Modifier.height(22.dp))
    Text("Microfone", style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(10.dp))
    ToggleRow("Supressao de ruido", "corta ventilador, teclado e chiado de fundo", p.micNoiseSuppression, prefs::setMicNoiseSuppression)
    ToggleRow("Cancelamento de eco", "evita o retorno do audio dos outros pelo seu mic", p.micEchoCancel, prefs::setMicEchoCancel)
    ToggleRow("Ganho automatico", "nivela o volume da sua voz sozinho", p.micAutoGain, prefs::setMicAutoGain)
    Spacer(Modifier.height(4.dp))
    Text(
        "as opcoes de microfone valem na proxima vez que voce entrar numa sala.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
}

// Lista de opcao unica (radio) — pra escolhas com rotulos longos (presets).
@Composable
private fun <T> RadioList(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        options.forEach { (label, value) ->
            val active = value == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val bg by animateColorAsState(
                when {
                    active -> Obsidian.active
                    hovered -> Obsidian.hover
                    else -> Obsidian.raised.copy(alpha = 0.5f)
                },
                tween(120),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.dp, if (active) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim, RoundedCornerShape(10.dp))
                    .hoverable(interaction)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LIcon(
                    if (active) Lucide.CircleDot else Lucide.Circle,
                    tint = if (active) Obsidian.accent else Obsidian.text3,
                    size = 15.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    style = TextStyle(color = if (active) Obsidian.text1 else Obsidian.text2, fontSize = 13.sp),
                )
            }
        }
    }
}

// Aba Desempenho: kill-switch gamer no topo, depois os controles finos (que ele
// sobrepoe — ficam esmaecidos com o modo ligado). Graficos + fps + transparencia.
@Composable
private fun PerformanceSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    ToggleRow(
        "Modo desempenho",
        "desliga aurora + estrelas e reduz animacoes de uma vez — pra jogar ou transmitir",
        p.performanceMode, prefs::setPerformanceMode,
    )
    Spacer(Modifier.height(6.dp))

    // Controles finos: o modo desempenho ja sobrepoe, entao esmaece quando ligado
    // (continuam clicaveis — sao a tua preferencia fora do modo desempenho).
    Column(Modifier.alpha(if (p.performanceMode) 0.45f else 1f)) {
        ToggleRow("Aurora", "fundo animado em shader", p.auroraEnabled, prefs::setAuroraEnabled)
        LabeledControl("Qualidade da aurora", "mais detalhe = mais GPU") {
            SegmentedRow(
                listOf("Alta" to AuroraQuality.HIGH, "Media" to AuroraQuality.MEDIUM, "Baixa" to AuroraQuality.LOW),
                p.auroraQuality, prefs::setAuroraQuality,
            )
        }
        ToggleRow("Estrelas", "campo de estrelas + meteoros sobre a aurora", p.starsEnabled, prefs::setStarsEnabled)
        LabeledControl("FPS das animacoes", "teto de quadros do fundo (livre segue o monitor)") {
            SegmentedRow(
                listOf("Livre" to UiFps.FREE, "60" to UiFps.CAP60, "30" to UiFps.CAP30),
                p.uiFps, prefs::setUiFps,
            )
        }
        ToggleRow("Reduzir movimento", "congela a aurora e desliga cascatas e pulsos", p.reduceMotion, prefs::setReduceMotion)
    }

    Spacer(Modifier.height(6.dp))
    ToggleRow(
        "Janela translucida",
        "cantos arredondados + fundo vazando; opaca = mais nitido e leve",
        p.windowTransparent, prefs::setWindowTransparent,
    )
    Text(
        "a transparencia da janela so aplica ao reiniciar o app.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
}

// Rotulo + subtitulo + um controle embaixo (usado com o SegmentedRow).
@Composable
private fun LabeledControl(title: String, sub: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = TextStyle(color = Obsidian.text1, fontSize = 13.sp))
        Text(sub, style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
        Spacer(Modifier.height(8.dp))
        content()
    }
}

// Segmentado obsidiana: pilulas numa trilha; a ativa acende ambar.
@Composable
private fun <T> SegmentedRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.void.copy(alpha = 0.55f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (label, value) ->
            val active = value == selected
            val bg by animateColorAsState(if (active) Obsidian.accent else Color.Transparent, tween(140))
            val fg by animateColorAsState(if (active) Obsidian.textInv else Obsidian.text2, tween(140))
            Text(
                label,
                style = TextStyle(
                    color = fg, fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            // Preenche a coluna capada (~720, estilo Discord): interruptor grudado
            // na ponta direita. Quem limita a largura agora e a coluna, nao a linha.
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(color = Obsidian.text1, fontSize = 13.sp))
            Text(sub, style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
        }
        Toggle(on, onChange)
    }
    Spacer(Modifier.height(8.dp))
}

// Interruptor obsidiana: trilho ambar quando ligado, botao desliza.
@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (on) Obsidian.accent else Obsidian.overlay, tween(160))
    val knobX by animateDpAsState(if (on) 18.dp else 2.dp, tween(160))
    Box(
        Modifier
            .width(38.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(track)
            .border(1.dp, if (on) Obsidian.accent else Obsidian.borderMid, RoundedCornerShape(11.dp))
            .clickable { onChange(!on) },
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (on) Obsidian.void else Obsidian.text3),
        )
    }
}

// Aba Aparencia (paridade com o mobile): tema rapido (presets), cor de destaque,
// fundo, tamanho da fonte e densidade. accent/fundo aplicam AO VIVO no app inteiro
// (Obsidian reativo); fonte/densidade valem no chat.
@Composable
private fun AppearanceSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    FieldLabel("previa")
    AppearancePreview(p.fontSize, p.density)

    Spacer(Modifier.height(20.dp))
    FieldLabel("tema rapido")
    PresetGrid(p.accentId, p.bgId) { prefs.setTheme(it.accentId, it.bgId) }

    Spacer(Modifier.height(20.dp))
    FieldLabel("cor de destaque")
    AccentRow(p.accentId, prefs::setAccent)

    Spacer(Modifier.height(20.dp))
    FieldLabel("fundo")
    BgList(p.bgId, prefs::setBg)

    Spacer(Modifier.height(14.dp))
    LabeledControl("Tamanho da fonte", "das mensagens no chat") {
        SegmentedRow(FontSizePref.entries.map { it.label to it }, p.fontSize, prefs::setFontSize)
    }
    LabeledControl("Densidade das mensagens", "respiro entre as mensagens") {
        SegmentedRow(DensityPref.entries.map { it.label to it }, p.density, prefs::setDensity)
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun AppearancePreview(fontSize: FontSizePref, density: DensityPref) {
    val samples = listOf("Bora marcar a call?", "fechou, 21h entao")
    Column(Modifier.fillMaxWidth()) {
        samples.forEachIndexed { i, text ->
            if (i > 0) Spacer(Modifier.height((density.groupedTopDp + 2).dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Obsidian.raised)
                    .border(1.dp, Obsidian.borderMid, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(text, style = TextStyle(color = Obsidian.text1, fontSize = (14 * fontSize.scale).sp))
            }
        }
    }
}

@Composable
private fun PresetGrid(selAccent: String, selBg: String, onPick: (ThemePreset) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemePresets.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { preset ->
                    PresetCard(
                        preset,
                        active = selAccent == preset.accentId && selBg == preset.bgId,
                        onClick = { onPick(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PresetCard(preset: ThemePreset, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val bg = bgOption(preset.bgId)
    val accent = accentOption(preset.accentId).value
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Obsidian.accentDim else Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, if (active) Obsidian.accent else Obsidian.borderDim, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 38.dp, height = 26.dp).clip(RoundedCornerShape(6.dp))
                .background(bg.voidC).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
        ) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(9.dp)
                    .clip(CircleShape).background(accent),
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                preset.label,
                style = TextStyle(color = if (active) Obsidian.accent else Obsidian.text1, fontSize = 12.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                preset.hint,
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentRow(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccentOptions.forEach { opt ->
            val active = opt.id == selected
            val check = if (opt.value.luminance() > 0.5f) Color(0xFF09091A) else Color.White
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(opt.value)
                    .border(
                        if (active) 2.5.dp else 1.dp,
                        if (active) Obsidian.text1 else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(9.dp),
                    )
                    .clickable { onSelect(opt.id) },
                contentAlignment = Alignment.Center,
            ) {
                if (active) LIcon(Lucide.Check, tint = check, size = 15.dp)
            }
        }
    }
}

@Composable
private fun BgList(selected: String, onSelect: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BgOptions.forEach { opt ->
            val active = opt.id == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Obsidian.accentDim else Obsidian.raised.copy(alpha = 0.5f))
                    .border(1.dp, if (active) Obsidian.accent else Obsidian.borderDim, RoundedCornerShape(10.dp))
                    .clickable { onSelect(opt.id) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(width = 34.dp, height = 22.dp).clip(RoundedCornerShape(6.dp))
                        .background(opt.voidC).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    opt.label,
                    style = TextStyle(color = if (active) Obsidian.accent else Obsidian.text2, fontSize = 13.sp),
                    modifier = Modifier.weight(1f),
                )
                if (active) LIcon(Lucide.Check, tint = Obsidian.accent, size = 15.dp)
            }
        }
    }
}
