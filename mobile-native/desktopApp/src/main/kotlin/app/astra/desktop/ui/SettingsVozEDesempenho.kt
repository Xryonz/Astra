package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.AtalhosGlobais
import app.astra.desktop.Canal
import app.astra.desktop.InicioComWindows
import app.astra.desktop.prefs.AuroraQuality
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.prefs.ScreenQuality
import app.astra.desktop.prefs.UiFps
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.desktop.voice.FonteDeAparelhos
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch

@Composable
internal fun PermissionsSection(onTestarAviso: () -> Unit) {
    Text(
        "o Windows decide o que cada programa pode usar — e quando ele bloqueia, não avisa: o microfone entrega silêncio, o aviso não aparece, a call não conecta. aqui é possível ver o que está liberado e liberar o que faltar.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.5.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 560.dp),
    )
    Spacer(Modifier.height(16.dp))
    PainelDePermissoes(onTestarAviso = onTestarAviso, modifier = Modifier.widthIn(max = 560.dp))
    Spacer(Modifier.height(20.dp))
    InfoNote(
        "Por que não aparece a caixa de \"permitir\"",
        "No navegador, um site pede permissão e você responde num pop-up. Programa " +
            "instalado no Windows não tem esse pedido: quem manda é um interruptor do " +
            "próprio sistema, o mesmo para todos os programas de área de trabalho.\n\n" +
            "Por isso o botão \"permitir\" aqui abre a página exata das Configurações do " +
            "Windows em vez de perguntar — e continua conferindo sozinho depois. Você liga " +
            "o interruptor lá, volta para cá, e a linha já está verde sem precisar clicar de novo.\n\n" +
            "Duas fogem da regra. Avisos não têm interruptor para ligar: o Windows só " +
            "registra o Astra quando ele manda o primeiro aviso, então permitir manda um. " +
            "E transmitir a tela não pede permissão nenhuma no Windows — inventar um " +
            "cadeado ali seria teatro.",
    )
}

@Composable
internal fun VoiceSection(
    p: DesktopPrefs.Prefs,
    prefs: DesktopPrefs,
    aparelhos: FonteDeAparelhos,
) {
    TituloExplicavel(
        "Transmissao de tela",
        "Vale ao iniciar a transmissão. O padrão de estreia sai da força do computador — " +
            "quem tem quatro núcleos ou menos começa em 540p, porque comprimir vídeo aqui " +
            "é trabalho do processador.",
    )
    RadioList(
        ScreenQuality.entries.map { it.label to it },
        p.screenQuality, prefs::setScreenQuality,
    )

    SettingsDivider()
    TituloExplicavel(
        "Motor de vídeo novo",
        "Comprime a transmissão na placa de vídeo, sem trazer o quadro para o processador. " +
            "Vale ao entrar na próxima chamada — não muda uma que já esteja em andamento.",
    )
    ToggleRow(
        "Usar o motor novo",
        "em teste. sem o pacote ou sem placa compatível, a chamada segue pelo caminho de sempre",
        p.motorNovo,
        prefs::setMotorNovo,
    )
    ToggleRow(
        "Transmitir em duas qualidades",
        "em teste. manda a tela cheia e uma versão menor ao mesmo tempo, para que quem está com " +
            "a rede curta receba a menor em vez de derrubar a qualidade de todo mundo. exige placa " +
            "com aceleração; sem ela, segue em uma qualidade só",
        p.duasCamadas,
        prefs::setDuasCamadas,
    )

    if (Canal.ehDeDesenvolvimento) {
        SettingsDivider()
        Text("Ninguém te escuta?", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
        Spacer(Modifier.height(4.dp))
        Text(
            "entrar numa call passa por várias etapas, e todas falham do mesmo jeito: silêncio. a lista abaixo mostra até onde chegou — a etapa que faltar é a culpada.",
            style = Tipo.apoio,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(10.dp))
        VoicePassos()
    }

    SettingsDivider()
    Text("Dispositivos", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(4.dp))
    Text(
        "\"padrao do Windows\" segue o que você escolheu no sistema — inclusive se trocar depois.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(10.dp))
    LaunchedEffect(Unit) { aparelhos.listar() }
    FieldLabel("saída (quem você ouve)")
    DeviceDropdown(aparelhos.saidas, p.audioOutput, prefs::setAudioOutput)
    Spacer(Modifier.height(12.dp))
    FieldLabel("entrada (seu microfone)")
    DeviceDropdown(aparelhos.microfones, p.audioInput, prefs::setAudioInput)

    Spacer(Modifier.height(16.dp))
    LinhaDeVolume("volume do microfone", p.volumeDoMicrofone, prefs::setVolumeDoMicrofone)
    Spacer(Modifier.height(10.dp))
    LinhaDeVolume("volume da escuta", p.volumeDaEscuta, prefs::setVolumeDaEscuta)
    Spacer(Modifier.height(6.dp))
    Text(
        "os dois só abaixam. para uma pessoa específica, use o botão direito no cartão dela na chamada.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )

    SettingsDivider()
    Text("Microfone", style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif))
    Spacer(Modifier.height(10.dp))
    ToggleRow("Cancelamento de eco", "evita o retorno do audio dos outros pelo seu mic — ao custo de qualidade", p.micEchoCancel, prefs::setMicEchoCancel)
    ToggleRow("Supressao de ruido", "corta ventilador, teclado e chiado de fundo", p.micNoiseSuppression, prefs::setMicNoiseSuppression)
    ToggleRow("Ganho automatico", "nivela o volume da sua voz sozinho", p.micAutoGain, prefs::setMicAutoGain)
    Spacer(Modifier.height(10.dp))
    Text(
        if (p.micEchoCancel) {
            "o cancelador do Windows trabalha a 16 kHz, e a sua voz sai de lá nessa taxa: " +
                "limpa, porém sem os agudos. Desligado, ela sobe para 48 kHz, a taxa cheia. " +
                "Quem usa fone não tem eco para cancelar — aí desligar só melhora."
        } else {
            "sua voz está indo a 48 kHz, a taxa cheia. Em compensação os dois ajustes acima " +
                "não têm efeito: no Windows os três vivem no mesmo componente, e sem ele o " +
                "microfone entra cru. Se alguém reclamar de ouvir a própria voz de volta, " +
                "é isto que precisa voltar."
        },
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(12.dp))
    MicSensitivityRow(p.micSensitivity, prefs::setMicSensitivity)
    Spacer(Modifier.height(4.dp))
    Text(
        "os ajustes do microfone valem na hora, mesmo com a call aberta — o som corta " +
            "por um instante enquanto o microfone reabre.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )
}

@Composable
internal fun AtalhosSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    CapturaDeTecla("mudo", p.teclaMudo, prefs::setTeclaMudo)
    Spacer(Modifier.height(12.dp))
    CapturaDeTecla("ensurdecer", p.teclaEnsurdecer, prefs::setTeclaEnsurdecer)

    SettingsDivider()
    FieldLabel("fixas")
    Spacer(Modifier.height(4.dp))
    AtalhoFixo("Ctrl K", "buscar")
    AtalhoFixo("Esc", "fechar o que estiver aberto")
    AtalhoFixo("Enter", "enviar a mensagem")
    AtalhoFixo("Shift Enter", "quebrar linha", ultima = true)

    Spacer(Modifier.height(16.dp))
    InfoNote(
        "O que o Astra escuta do seu teclado",
        "Para uma tecla funcionar com o jogo em primeiro plano, o Windows exige um " +
            "gancho de teclado do sistema — não existe outro caminho, e é o mesmo que " +
            "Discord e TeamSpeak usam.\n\n" +
            "O que o Astra faz com ele: compara a tecla apertada com as escolhidas " +
            "aqui em cima. Só isso. Nada é guardado, contado ou enviado para lugar " +
            "nenhum, e a tecla segue o caminho dela normalmente.\n\n" +
            "Sem nenhuma tecla escolhida, o gancho nem chega a ser instalado.",
    )
}

@Composable
private fun AtalhoFixo(tecla: String, oQueFaz: String, ultima: Boolean = false) {
    Row(
        Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tecla,
            style = TextStyle(color = Obsidian.text2, fontSize = 11.sp, fontFamily = DmMono),
            modifier = Modifier
                .widthIn(min = 92.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Obsidian.void.copy(alpha = 0.55f))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(oQueFaz, style = Tipo.descricao)
    }
    if (!ultima) Spacer(Modifier.height(7.dp))
}

@Composable
private fun CapturaDeTecla(rotulo: String, vk: Int, onEscolher: (Int) -> Unit) {
    var ouvindo by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val nome = remember(vk) { AtalhosGlobais.nomeDaTecla(vk) }

    DisposableEffect(Unit) { onDispose { AtalhosGlobais.cancelarCaptura() } }

    Row(
        Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rotulo, style = TextStyle(color = Obsidian.text2, fontSize = 13.sp))
            Text(
                if (ouvindo) "aperte a tecla — esc deixa sem nenhuma" else "clique para trocar",
                style = Tipo.apoio,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .clickScale(interaction, pressedScale = 0.97f, formaDoFoco = RoundedCornerShape(8.dp))
                .widthIn(min = 116.dp)
                .clip(FormaDeBotao)
                .background(
                    when {
                        ouvindo -> Obsidian.accent.copy(alpha = 0.16f)
                        hovered -> Obsidian.hover
                        else -> Obsidian.raised
                    },
                )
                .border(
                    1.dp,
                    if (ouvindo) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim,
                    FormaDeBotao,
                )
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) {
                    ouvindo = true
                    AtalhosGlobais.capturarProxima { escolhida ->
                        escopo.launch {
                            ouvindo = false
                            onEscolher(escolhida)
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (ouvindo) "ouvindo…" else nome,
                style = TextStyle(
                    color = if (ouvindo) Obsidian.accent else if (vk == 0) Obsidian.text3 else Obsidian.text1,
                    fontSize = 12.sp,
                    fontFamily = DmMono,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun <T> RadioList(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
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

@Composable
internal fun PerformanceSection(p: DesktopPrefs.Prefs, prefs: DesktopPrefs) {
    if (p.perfAutomatico.isNotBlank()) {
        Box(
            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                "O Astra ligou isto sozinho na primeira abertura: encontrou ${p.perfAutomatico} " +
                    "nesta máquina. Desligue à vontade — a escolha passa a ser sua e ele não " +
                    "mexe mais.",
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
    ToggleRow(
        "Modo desempenho",
        "desliga aurora + estrelas e reduz animações de uma vez — para jogar ou transmitir",
        p.performanceMode, prefs::setPerformanceMode,
    )
    Spacer(Modifier.height(6.dp))

    Column(Modifier.alpha(if (p.performanceMode) 0.45f else 1f)) {
        LabeledControl("Qualidade da aurora", "mais detalhe = mais GPU; escolha o fundo em Aparencia") {
            SegmentedRow(
                listOf("Alta" to AuroraQuality.HIGH, "Media" to AuroraQuality.MEDIUM, "Baixa" to AuroraQuality.LOW),
                p.auroraQuality, prefs::setAuroraQuality,
            )
        }
        LabeledControl("FPS das animações", "teto de quadros do fundo (livre segue o monitor)") {
            SegmentedRow(
                listOf("Livre" to UiFps.FREE, "60" to UiFps.CAP60, "30" to UiFps.CAP30),
                p.uiFps, prefs::setUiFps,
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    ToggleRow(
        "Janela translucida",
        "cantos arredondados + fundo vazando; opaca = mais nitido e leve",
        p.windowTransparent, prefs::setWindowTransparent,
    )
    Text(
        "a transparencia da janela so aplica ao reiniciar o app.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )

    Spacer(Modifier.height(6.dp))
    ToggleRow(
        "Fechar de vez ao fechar o app",
        "o X encerra o Astra em vez de minimizar para bandeja — sem nada em segundo plano",
        p.exitOnClose, prefs::setExitOnClose,
    )
    Spacer(Modifier.height(6.dp))
    ArranqueComWindows()
}

internal fun mascarar(email: String): String {
    val arroba = email.indexOf('@')
    if (arroba <= 0) return "•••"
    return email.first() + "•••" + email.substring(arroba)
}

@Composable
private fun ArranqueComWindows() {
    if (!InicioComWindows.disponivel()) return
    var ligado by remember { mutableStateOf(InicioComWindows.ligado()) }
    var escondido by remember { mutableStateOf(InicioComWindows.escondido()) }
    var falhou by remember { mutableStateOf(false) }

    fun gravar(novoLigado: Boolean, novoEscondido: Boolean) {
        val ok = InicioComWindows.aplicar(novoLigado, novoEscondido)
        falhou = !ok
        if (!ok) return
        ligado = novoLigado
        escondido = novoEscondido
    }

    ToggleRow(
        "Abrir junto com o Windows",
        "o Astra já sobe na bandeja ao ligar o computador — sem esperar você lembrar dele",
        ligado,
    ) { gravar(it, escondido) }
    if (ligado) {
        Spacer(Modifier.height(6.dp))
        ToggleRow(
            "Ao subir assim, começar sem janela",
            "só o ícone na bandeja: nada aparece na frente de quem acabou de ligar o PC",
            escondido,
        ) { gravar(true, it) }
    }
    if (falhou) {
        Spacer(Modifier.height(6.dp))
        Text(
            "o Windows recusou a mudança no arranque. dá para ligar e desligar isto também " +
                "pelo Gerenciador de Tarefas, na aba Inicializar.",
            style = TextStyle(color = Obsidian.danger, fontSize = 11.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
}


@Composable
internal fun LabeledControl(title: String, sub: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = Tipo.corpo)
        Text(sub, style = Tipo.apoio)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
