package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.prefs.DensityPref
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.FontSizePref
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.FamiliaDeTema
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.ThemePreset
import app.astra.desktop.ui.theme.ThemePresets
import app.astra.desktop.ui.theme.Tipo
import app.astra.desktop.ui.theme.accentOption
import app.astra.desktop.ui.theme.bgOption

@Composable
internal fun <T> SegmentedRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
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
            val pillSrc = remember { MutableInteractionSource() }
            Text(
                label,
                style = TextStyle(
                    color = fg, fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                ),
                modifier = Modifier
                    .clickScale(pillSrc)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable(interactionSource = pillSrc, indication = null) { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
internal fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Tipo.corpo)
            Text(sub, style = Tipo.apoio)
        }
        Toggle(on, onChange)
    }
    Spacer(Modifier.height(8.dp))
}

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

@Composable
internal fun AppearanceSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    FieldLabel("tema")
    PresetGrid(p.accentId, p.bgId) { prefs.setTheme(it.accentId, it.bgId) }

    SettingsDivider()
    LabeledControl("Fundo", "liso e o padrao; a aurora e um shader animado e cobra GPU") {
        SegmentedRow(FundoPref.entries.map { it.label to it }, fundoAtual(p)) { aplicarFundo(prefs, it) }
    }

    SettingsDivider()
    Spacer(Modifier.height(20.dp))
}

@Composable
internal fun PetsSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    TituloExplicavel(
        "Companheiro",
        "A cor troca a rampa que o artista desenhou, degrau por degrau — os olhos, o " +
            "contorno e os detalhes ficam como estão, e é isso que mantém o pet " +
            "reconhecível em vez de virar uma mancha de uma cor só. O nome aparece " +
            "sobre ele quando reage a uma mensagem. Clique nele para ver o que ele " +
            "faz; insistir demais o cansa.",
    )

    if (!p.petLigado) {
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                "O companheiro está desligado. Ligue em Acessibilidade › movimento.",
                style = Tipo.rotulo,
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    val pet = Pet.de(p.petTipo)
    var gesto by remember { mutableStateOf(Anim.PARADO) }
    if (gesto !in pet.passos) gesto = Anim.PARADO

    PetPalco(pet, Pelagem.de(p.petPelagem), gesto)
    Spacer(Modifier.height(12.dp))
    GestosDoPet(pet, gesto) { gesto = it }

    SettingsDivider()
    if (Pet.disponiveis.size > 1) {
        FieldLabel("pet")
        SegmentedRow(
            Pet.disponiveis.map { it.rotulo to it.name },
            p.petTipo,
            prefs::setPetTipo,
        )
        Spacer(Modifier.height(18.dp))
    }
    FieldLabel("pelagem")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pelagem.entries.forEach { pel ->
            AmostraDePelagem(pel, pel.name == p.petPelagem) { prefs.setPetPelagem(pel.name) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        Pelagem.de(p.petPelagem).rotulo,
        style = Tipo.rotulo,
    )

    Spacer(Modifier.height(18.dp))
    FieldLabel("nome")
    Box(
        Modifier.widthIn(max = 260.dp).fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (p.petNome.isEmpty()) {
            Text("Sem nome", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
        }
        BasicTextField(
            value = p.petNome,
            onValueChange = prefs::setPetNome,
            singleLine = true,
            textStyle = Tipo.corpo,
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SettingsDivider()
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun AmostraDePelagem(pelagem: Pelagem, escolhida: Boolean, onClick: () -> Unit) {
    val fonte = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(pelagem.amostra)
            .border(
                width = if (escolhida) 2.dp else 1.dp,
                color = if (escolhida) Obsidian.accent else Obsidian.borderDim,
                shape = CircleShape,
            )
            .clickable(interactionSource = fonte, indication = null, onClick = onClick)
            .clickScale(fonte, formaDoFoco = CircleShape)
            .semantics { contentDescription = pelagem.rotulo },
    )
}

@Composable
internal fun AccessibilitySection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    Text("Legibilidade do texto", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(10.dp))
    LabeledControl("Tamanho da fonte", "das mensagens no chat") {
        SegmentedRow(FontSizePref.entries.map { it.label to it }, p.fontSize, prefs::setFontSize)
    }
    LabeledControl("Densidade das mensagens", "respiro entre as mensagens") {
        SegmentedRow(DensityPref.entries.map { it.label to it }, p.density, prefs::setDensity)
    }

    SettingsDivider()
    TituloExplicavel(
        "Contraste",
        "O padrão do Astra é mais suave de propósito: contraste alto demais em fundo " +
            "escuro faz a borda da letra vibrar, e isso cansa em sessão longa à noite. " +
            "Ligue o alto contraste se o padrão for difícil de ler — a troca é sua, e " +
            "legibilidade ganha de conforto.",
    )
    ToggleRow(
        "Alto contraste",
        "clareia texto e bordas — vale na hora, em todas as telas",
        p.altoContraste, prefs::setAltoContraste,
    )

    SettingsDivider()
    TituloExplicavel(
        "Companheiro",
        "Um pet em pixel art que caminha por cima da interface. Ele passa a maior " +
            "parte do tempo parado e só anda em trechos curtos: movimento contínuo " +
            "no canto do olho ensina o olho a ignorar o resto da tela. Reage quando " +
            "chega mensagem, e some junto se você reduzir movimento. A cor e o nome " +
            "estão em Aparência.",
    )
    ToggleRow(
        "Pet na tela",
        "anda pela interface e reage a mensagem nova",
        p.petLigado, prefs::setPetLigado,
    )

    SettingsDivider()
    TituloExplicavel(
        "Movimento",
        "Congela a aurora e desliga as cascatas de entrada e os pulsos. Vale em todas " +
            "as telas, na hora. O Astra também obedece ao ajuste de movimento do próprio " +
            "Windows — este interruptor é para quando você quer parar tudo sem mexer no " +
            "sistema inteiro.",
    )
    ToggleRow(
        "Reduzir movimento",
        "congela a aurora e desliga cascatas e pulsos",
        p.reduceMotion, prefs::setReduceMotion,
    )
    Spacer(Modifier.height(20.dp))
}

private const val CASCATA_PASSO_MS = 40
private const val CASCATA_DURACAO_MS = 380
private const val CASCATA_DEGRAUS = 16
private val CASCATA_SUBIDA = 14.dp

@Composable
internal fun CascataVertical(
    chave: Any?,
    animar: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val deveAnimar = animar && !LocalReduceMotion.current
    val totalMs = CASCATA_DURACAO_MS + CASCATA_PASSO_MS * CASCATA_DEGRAUS
    val relogio = remember(chave) { Animatable(if (deveAnimar) 0f else 1f) }
    LaunchedEffect(chave) {
        if (deveAnimar) relogio.animateTo(1f, tween(totalMs, easing = LinearEasing))
    }
    val deslocamento = with(LocalDensity.current) { CASCATA_SUBIDA.toPx() }
    Layout(content = content, modifier = modifier) { medidos, constraints ->
        val filhos = medidos.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val largura = if (constraints.hasBoundedWidth) constraints.maxWidth
        else filhos.maxOfOrNull { it.width } ?: 0
        layout(largura, filhos.sumOf { it.height }) {
            val agora = relogio.value * totalMs
            var y = 0
            var degrau = 0
            filhos.forEach { filho ->
                val conta = filho.width > 0 && filho.height > 0
                val meu = if (conta) degrau++ else degrau
                val bruto =
                    ((agora - meu.coerceAtMost(CASCATA_DEGRAUS) * CASCATA_PASSO_MS) / CASCATA_DURACAO_MS)
                        .coerceIn(0f, 1f)
                val progresso = EaseOutSoft.transform(bruto)
                filho.placeWithLayer(0, y) {
                    alpha = progresso
                    translationY = (1f - progresso) * deslocamento
                }
                y += filho.height
            }
        }
    }
}

private enum class FundoPref(val label: String) {
    LISO("Liso"),
    ESTRELAS("Estrelas"),
    AURORA("Aurora"),
    AMBOS("Aurora e estrelas"),
}

private fun fundoAtual(p: DesktopPrefs.Prefs): FundoPref = when {
    p.auroraEnabled && p.starsEnabled -> FundoPref.AMBOS
    p.auroraEnabled -> FundoPref.AURORA
    p.starsEnabled -> FundoPref.ESTRELAS
    else -> FundoPref.LISO
}

private fun aplicarFundo(prefs: DesktopPrefs, f: FundoPref) {
    prefs.setAuroraEnabled(f == FundoPref.AURORA || f == FundoPref.AMBOS)
    prefs.setStarsEnabled(f == FundoPref.ESTRELAS || f == FundoPref.AMBOS)
}

@Composable
internal fun SettingsDivider() {
    Spacer(Modifier.height(32.dp))
}

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun PresetGrid(selAccent: String, selBg: String, onPick: (ThemePreset) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FamiliaDeTema.entries.forEach { familia ->
            val doGrupo = ThemePresets.filter { it.familia == familia }
            if (doGrupo.isEmpty()) return@forEach
            Text(
                familia.titulo.uppercase(),
                style = TextStyle(color = Obsidian.text3, fontSize = 9.sp, letterSpacing = 1.5.sp),
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
            doGrupo.chunked(2).forEach { pair ->
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
                style = Tipo.nota,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
