package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.desktop.voice.FonteDeAparelhos
import app.astra.mobile.core.network.BotPersonaApi
import app.astra.mobile.core.network.dto.ProfileUserDto
import com.composables.icons.lucide.Accessibility
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.Keyboard
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

enum class SettingsTab(val label: String, val sub: String, val icon: ImageVector) {
    ACCOUNT("Conta", "email e senha", Lucide.User),
    PROFILE("Perfil", "avatar, nome e recado", Lucide.Pencil),
    NAME_COLORS("Cores do nome", "sua cor em cada constelação", Lucide.Palette),
    SESSIONS("Sessões", "onde sua conta está logada", Lucide.LogOut),
    NOTIFICATIONS("Notificacoes", "avisos na bandeja", Lucide.Bell),
    PRIVACY("Privacidade", "o que os outros veem de você", Lucide.Eye),
    APPEARANCE("Aparencia", "cores e fundo", Lucide.Palette),
    PETS("Pets", "companheiro, cor e gestos", Lucide.PawPrint),
    ACCESSIBILITY("Acessibilidade", "leitura, contraste e movimento", Lucide.Accessibility),
    PERFORMANCE("Desempenho", "graficos, animações, fps", Lucide.ChartColumn),
    VOICE("Voz", "microfone e transmissão", Lucide.Volume2),
    SHORTCUTS("Atalhos", "teclas do app", Lucide.Keyboard),
    PERMISSIONS("Permissoes", "o que o Windows libera", Lucide.ShieldCheck),
    ABOUT("Sobre", "versão e atualizacoes", Lucide.Info),
    BOTS("Bots", "aparencia da Sparkle e da Sparxie", Lucide.Bot),
    DIAGNOSTICS("Diagnostico", "o que o app esta vendo agora", Lucide.CircleDot),
}

private val abaDeDev: Boolean =
    System.getProperty("jpackage.app-path") == null || System.getProperty("astra.dev") != null

private val abasVisiveis: List<SettingsTab> =
    SettingsTab.entries.filter {
        (it != SettingsTab.DIAGNOSTICS || abaDeDev) && it != SettingsTab.BOTS
    }

private val LARGURA_DA_PREVIA = 470.dp
private val ESPACO_PARA_A_BARRA = 78.dp
private const val VIDA_DA_CONFIRMACAO_MS = 2_600L
private const val RECUSA_DO_ARRANQUE =
    "o Windows recusou a mudança no arranque. dá para ligar e desligar isto também pelo " +
        "Gerenciador de Tarefas, na aba Inicializar."
internal val FORMA_DO_CARTAO_DE_CONFIG = RoundedCornerShape(16.dp)

@Composable
fun SettingsScreen(
    me: ProfileUserDto?,
    prefs: DesktopPrefs,
    aparelhos: FonteDeAparelhos,
    onClose: () -> Unit,
    onProfileSaved: () -> Unit = {},
    initialTab: SettingsTab = SettingsTab.ACCOUNT,
    onTestarNotificacao: () -> Unit = {},
    aoSairDaConta: () -> Unit = {},
) {
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    val tabAtiva = tab
    val jaAnimaram = remember { mutableSetOf<SettingsTab>() }
    val prefState by prefs.state.collectAsState()
    var draft by remember(me) { mutableStateOf(ProfileDraft.from(me)) }
    var perfilSalvo by remember(me) { mutableStateOf(ProfileDraft.from(me)) }

    val arranque = remember { RascunhoDoArranque() }
    val pendentesDePrefs by prefs.mudancasPendentes.collectAsState()
    val ajustesPendentes = pendentesDePrefs + if (arranque.mudou) 1 else 0
    val perfilMudou = draft != perfilSalvo
    val temRascunho = ajustesPendentes > 0 || perfilMudou

    var salvando by remember { mutableStateOf(false) }
    var confirmacao by remember { mutableStateOf<String?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var insistencia by remember { mutableStateOf(0) }
    val escopo = rememberCoroutineScope()

    DisposableEffect(prefs) {
        prefs.abrirRascunho()
        onDispose { prefs.fecharRascunho() }
    }

    LaunchedEffect(confirmacao) {
        if (confirmacao == null) return@LaunchedEffect
        delay(VIDA_DA_CONFIRMACAO_MS)
        confirmacao = null
    }

    fun salvar() {
        if (salvando || !temRascunho) return
        salvando = true
        erro = null
        confirmacao = null
        val queriaPerfil = perfilMudou
        val paraSalvar = draft
        escopo.launch {
            var ajustes = prefs.salvarRascunho()
            var falha: String? = null
            if (arranque.mudou) {
                if (arranque.salvar()) ajustes++ else falha = RECUSA_DO_ARRANQUE
            }
            if (queriaPerfil) {
                salvarPerfil(paraSalvar, perfilSalvo)
                    .onSuccess { perfilSalvo = paraSalvar; onProfileSaved() }
                    .onFailure { falha = saveErrorMessage(it) }
            }
            salvando = false
            erro = falha
            if (falha == null) confirmacao = recadoDoSalvamento(ajustes, queriaPerfil)
        }
    }

    fun descartar() {
        prefs.descartarRascunho()
        arranque.descartar()
        draft = perfilSalvo
        erro = null
        confirmacao = null
    }

    fun tentarFechar() {
        if (temRascunho) insistencia++ else onClose()
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { tentarFechar(); true } else false
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { tentarFechar() },
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 30.dp)
                .widthIn(max = 1180.dp),
        ) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(FORMA_DO_CARTAO_DE_CONFIG)
                .background(Obsidian.base)
                .border(1.dp, Obsidian.borderMid, FORMA_DO_CARTAO_DE_CONFIG)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                Modifier.width(220.dp).fillMaxHeight().padding(horizontal = 12.dp, vertical = 18.dp),
            ) {
                Text(
                    "configurações",
                    style = TextStyle(color = Obsidian.text1, fontSize = 18.sp, fontFamily = DmSerif),
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                )
                var ehDono by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    ehDono = runCatching {
                        GlobalContext.get().get<BotPersonaApi>().personas().data != null
                    }.getOrDefault(false)
                }
                val abas = remember(ehDono) {
                    if (ehDono) abasVisiveis + SettingsTab.BOTS else abasVisiveis
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    abas.forEach { t ->
                        NavRow(t.icon, t.label, t.sub, active = t == tabAtiva) { tab = t }
                    }
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val larguraPrevia = LARGURA_DA_PREVIA
            val pinned = maxWidth > larguraPrevia + 280.dp
            val acoesDoCartao = remember { AcoesDoCartao() }
            val contentMax =
                if (pinned) minOf(720.dp, (maxWidth - larguraPrevia - 76.dp).coerceAtLeast(280.dp)) else 720.dp
            Column(
                Modifier.align(Alignment.TopStart).widthIn(max = contentMax).fillMaxWidth()
                    .fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        fadeIn(tween(95)).togetherWith(fadeOut(tween(70))) using
                            SizeTransform(clip = false) { _, _ -> tween(120) }
                    },
                    label = "settingsSection",
                ) { current ->
                    val temPrevia = temPrevia(current)
                    val jaVisto = current in jaAnimaram
                    LaunchedEffect(current) { jaAnimaram += current }
                    Column(Modifier.fillMaxWidth()) {
                    Text(
                        current.label,
                        style = TextStyle(color = Obsidian.text1, fontSize = 26.sp, fontFamily = DmSerif),
                    )
                    Spacer(Modifier.height(18.dp))
                    if (!pinned && temPrevia) {
                        SettingsPreview(current, me, prefState, draft, Modifier.widthIn(max = larguraPrevia).fillMaxWidth(), acoesDoCartao)
                        Spacer(Modifier.height(18.dp))
                    }
                    CascataVertical(chave = current, animar = !jaVisto, modifier = Modifier.fillMaxWidth()) {
                    when (current) {
                        SettingsTab.ACCOUNT -> AccountSection(me, aoSairDaConta)
                        SettingsTab.PROFILE -> ProfileSection(me, draft, { draft = it }, acoesDoCartao)
                        SettingsTab.NAME_COLORS -> CoresDoNomeSection(me)
                        SettingsTab.SESSIONS -> SessionsSection()
                        SettingsTab.NOTIFICATIONS -> Column {
                            BlocoDeAjustes(
                                "neste computador",
                                "mandam no balão da bandeja desta máquina. Não mudam o que o " +
                                    "servidor envia, então o sino continua contando.",
                            ) {
                                ToggleRow(
                                    "Sussurros (DMs)", "avisa quando chega mensagem privada",
                                    prefState.notifyDms, prefs::setNotifyDms,
                                )
                                ToggleRow(
                                    "Atividade de canal", "avisa nova mensagem nas constelações",
                                    prefState.notifyChannels, prefs::setNotifyChannels,
                                )
                                ToggleRow(
                                    "Som do aviso",
                                    "duas notas curtas quando chega algo dirigido a você",
                                    prefState.somDeAviso, prefs::setSomDeAviso,
                                )
                                ToggleRow(
                                    "Aviso sem conteúdo",
                                    "some quem escreveu e o que escreveu — fica só “sussurro novo”",
                                    prefState.avisoDiscreto, prefs::setAvisoDiscreto,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "os avisos aparecem na bandeja so com a janela fechada ou minimizada.",
                                    style = Tipo.apoio,
                                    modifier = Modifier.widthIn(max = 460.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "o aviso sem conteúdo serve para transmitir a tela: o balão do Windows " +
                                        "aparece por cima de tudo, e o que estiver escrito nele entra na gravação.",
                                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
                                    modifier = Modifier.widthIn(max = 460.dp),
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            AvisosDaContaBloco()
                        }
                        SettingsTab.PRIVACY -> PrivacySection(prefState, prefs, me, onProfileSaved)
                        SettingsTab.APPEARANCE -> AppearanceSection(prefState, prefs)
                        SettingsTab.PETS -> PetsSection(prefState, prefs)

                        SettingsTab.ACCESSIBILITY -> AccessibilitySection(prefState, prefs)
                        SettingsTab.PERFORMANCE -> PerformanceSection(prefState, prefs, arranque)
                        SettingsTab.VOICE -> VoiceSection(prefState, prefs, aparelhos)
                        SettingsTab.SHORTCUTS -> AtalhosSection(prefState, prefs)
                        SettingsTab.PERMISSIONS -> PermissionsSection(onTestarNotificacao)
                        SettingsTab.ABOUT -> AboutSection()
                        SettingsTab.DIAGNOSTICS -> DiagnosticsSection()
                        SettingsTab.BOTS -> BotsSection()
                    }
                    }
                    }
                }
                Spacer(Modifier.height(ESPACO_PARA_A_BARRA))
            }
                if (pinned) {
                    Column(
                        Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 32.dp).width(larguraPrevia),
                        horizontalAlignment = Alignment.End,
                    ) {
                        val hov = remember { MutableInteractionSource() }
                        val h by hov.collectIsHoveredAsState()
                        Box(
                            Modifier
                                .size(30.dp)
                                .clickScale(hov, formaDoFoco = FormaDeBotao)
                                .clip(FormaDeBotao)
                                .background(if (h) Obsidian.hover else Obsidian.overlay)
                                .border(1.dp, Obsidian.borderMid, FormaDeBotao)
                                .hoverable(hov)
                                .clickable(interactionSource = hov, indication = null) { tentarFechar() },
                            contentAlignment = Alignment.Center,
                        ) {
                            LIcon(Lucide.X, tint = Obsidian.text2, size = 15.dp, rotulo = "fechar")
                        }
                        Spacer(Modifier.height(16.dp))
                        AnimatedContent(
                            targetState = tabAtiva,
                            transitionSpec = {
                                fadeIn(tween(110)).togetherWith(fadeOut(tween(80))) using
                                    SizeTransform(clip = false) { _, _ -> tween(135) }
                            },
                            label = "previaDaSecao",
                        ) { secao ->
                            Column(Modifier.fillMaxWidth()) {
                                if (temPrevia(secao)) {
                                    SettingsPreview(secao, me, prefState, draft, Modifier.fillMaxWidth(), acoesDoCartao)
                                }
                            }
                        }
                    }
                }
            }
        }
            BarraDeSalvar(
                ajustes = ajustesPendentes,
                perfil = perfilMudou,
                salvando = salvando,
                confirmacao = confirmacao,
                erro = erro,
                insistencia = insistencia,
                aoSalvar = { salvar() },
                aoDescartar = { descartar() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            )
        }
    }
}

