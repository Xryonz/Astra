package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.profile.AvatarPicker
import app.astra.desktop.shell.codigoDoConvite
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.InviteApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.InvitePreviewDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import app.astra.desktop.ui.theme.Tipo

private enum class OnbStep { WELCOME, SKY, ENTRAR, PHOTO, PERMS }

private val ALTURA_PADRAO = 300.dp
private val ALTURA_ENTRAR = 400.dp
private val ALTURA_PERMS = 620.dp

private const val ESPERA_DA_BUSCA_MS = 420L

@Composable
fun OnboardingScreen(displayName: String, onTestarAviso: () -> Unit, onDone: () -> Unit) {
    val reduce = LocalReduceMotion.current
    var step by remember { mutableStateOf(OnbStep.WELCOME) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var entrou by remember { mutableStateOf<ServerDto?>(null) }
    val scope = rememberCoroutineScope()
    val steps = OnbStep.entries
    val idx = steps.indexOf(step)

    fun pickPhoto() {
        if (busy) return
        scope.launch {
            val file = AvatarPicker.choose("Sua foto") ?: return@launch
            busy = true
            withContext(Dispatchers.IO) { AvatarPicker.encode(file) }
                .onSuccess { uri ->
                    withContext(Dispatchers.IO) {
                        runCatching { GlobalContext.get().get<UserApi>().updateProfile(UpdateProfileRequest(avatarUrl = uri)) }
                    }
                    avatarUrl = uri
                }
            busy = false
        }
    }

    val altura by animateDpAsState(
        when (step) {
            OnbStep.PERMS -> ALTURA_PERMS
            OnbStep.ENTRAR -> ALTURA_ENTRAR
            else -> ALTURA_PADRAO
        },
        tween(if (reduce) 0 else 320),
        label = "onbAltura",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(620.dp).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(altura), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val d = if (reduce) 0 else 320
                        (fadeIn(tween(d)) + slideInHorizontally(tween(d)) { it / 8 }) togetherWith
                            (fadeOut(tween(d)) + slideOutHorizontally(tween(d)) { -it / 8 })
                    },
                    label = "onbStep",
                ) { s ->
                    when (s) {
                        OnbStep.WELCOME -> WelcomeStep(reduce)
                        OnbStep.SKY -> SkyStep()
                        OnbStep.ENTRAR -> EntrarStep(
                            entrou = entrou,
                            aoEntrar = { entrou = it },
                            aoExplorar = {
                                lembrarOndeAbrir("discover")
                                step = OnbStep.PHOTO
                            },
                        )
                        OnbStep.PHOTO -> PhotoStep(displayName, avatarUrl, busy, ::pickPhoto)
                        OnbStep.PERMS -> PermsStep(onTestarAviso)
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                steps.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .size(if (i == idx) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i <= idx) Obsidian.accent else Obsidian.borderMid),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            OnbButton(
                text = when (step) {
                    OnbStep.WELCOME -> "começar"
                    OnbStep.ENTRAR -> if (entrou == null) "deixar para depois" else "continuar"
                    OnbStep.SKY, OnbStep.PHOTO -> "continuar"
                    OnbStep.PERMS -> "concluir"
                },
                onClick = {
                    when (step) {
                        OnbStep.WELCOME -> step = OnbStep.SKY
                        OnbStep.SKY -> step = OnbStep.ENTRAR
                        OnbStep.ENTRAR -> {
                            entrou?.let { lembrarOndeAbrir("server:${it.id}") }
                            step = OnbStep.PHOTO
                        }
                        OnbStep.PHOTO -> step = OnbStep.PERMS
                        OnbStep.PERMS -> onDone()
                    }
                },
            )
            if (step == OnbStep.PHOTO) {
                Spacer(Modifier.height(12.dp))
                val skip = remember { MutableInteractionSource() }
                Text(
                    "pular por agora",
                    style = Tipo.descricao,
                    modifier = Modifier.clickable(interactionSource = skip, indication = null) {
                        step = OnbStep.PERMS
                    },
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(reduce: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RotatingStarsLogo(reduce, diameter = 116.dp, planetRes = "astra-glyph.png")
        Spacer(Modifier.height(18.dp))
        Text(
            "sua constelação começa aqui",
            style = TextStyle(color = Obsidian.text1, fontSize = 24.sp, fontFamily = DmSerif, fontWeight = FontWeight.Light, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Astra é onde suas conversas viram um céu só seu — calmo, escuro, sem barulho.",
            style = TextStyle(color = Obsidian.text3, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center),
            modifier = Modifier.width(340.dp),
        )
    }
}

@Composable
private fun SkyStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "o idioma do céu",
            style = TextStyle(color = Obsidian.text1, fontSize = 22.sp, fontFamily = DmSerif, fontWeight = FontWeight.Light),
        )
        Spacer(Modifier.height(22.dp))
        Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SkyTerm("✦", "constelação", "um servidor — sua comunidade e a galera dela.")
            SkyTerm("◉", "órbita", "um canal dentro de uma constelação.")
            SkyTerm("☾", "sussurro", "uma conversa direta, no pé do ouvido.")
        }
    }
}

@Composable
private fun SkyTerm(glyph: String, term: String, meaning: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Obsidian.raised),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, style = TextStyle(color = Obsidian.accent, fontSize = 15.sp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(term, style = TextStyle(color = Obsidian.accent, fontSize = 15.sp, fontFamily = DmSerif))
            Text(meaning, style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 16.sp))
        }
    }
}

private fun lembrarOndeAbrir(destino: String) {
    runCatching { GlobalContext.get().get<SessionStore>().setUiPref("lastSelection", destino) }
}

@Composable
private fun EntrarStep(entrou: ServerDto?, aoEntrar: (ServerDto) -> Unit, aoExplorar: () -> Unit) {
    val api = remember { GlobalContext.get().get<InviteApi>() }
    var cru by remember { mutableStateOf("") }
    var previa by remember { mutableStateOf<InvitePreviewDto?>(null) }
    var procurando by remember { mutableStateOf(false) }
    var entrando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val codigo = remember(cru) { codigoDoConvite(cru) }

    LaunchedEffect(codigo) {
        previa = null
        procurando = false
        if (codigo.isEmpty() || entrou != null) return@LaunchedEffect
        delay(ESPERA_DA_BUSCA_MS)
        procurando = true
        val achada = withContext(Dispatchers.IO) { runCatching { api.preview(codigo).data }.getOrNull() }
        procurando = false
        previa = achada
        if (achada == null) erro = "Convite inválido ou expirado"
    }

    val alvo = previa
    val podeEntrar = alvo != null && !alvo.isGroup && !entrando
    val entrar: () -> Unit = {
        if (podeEntrar) {
            entrando = true
            erro = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { api.join(codigo).data } }
                entrando = false
                val novo = r.getOrNull()
                if (novo != null) aoEntrar(novo) else erro = "Convite inválido ou expirado"
            }
        }
    }

    Column(Modifier.width(400.dp)) {
        Text(
            "onde você vai entrar",
            style = TextStyle(color = Obsidian.text1, fontSize = 22.sp, fontFamily = DmSerif, fontWeight = FontWeight.Light),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Uma conta sozinha é um céu vazio. Cole o convite que te mandaram — ou " +
                "veja as constelações abertas a qualquer pessoa.",
            style = TextStyle(color = Obsidian.text3, fontSize = 12.5.sp, lineHeight = 18.sp),
        )
        Spacer(Modifier.height(16.dp))

        if (entrou != null) {
            CartaoDeEntrada(entrou.name)
            return@Column
        }

        DialogField(cru, inviteLink("codigo-do-convite"), { cru = it; erro = null }, entrar)

        if (procurando) {
            Spacer(Modifier.height(10.dp))
            Text("procurando a constelação…", style = Tipo.apoio)
        }
        alvo?.let {
            Spacer(Modifier.height(12.dp))
            CartaoDaConstelacao(it)
            Spacer(Modifier.height(12.dp))
            DialogButton(
                label = if (entrando) "entrando…" else "entrar",
                accent = true,
                habilitado = podeEntrar,
                onClick = entrar,
            )
        }
        erro?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Tipo.erro)
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(28.dp).height(1.dp).background(Obsidian.borderDim))
            Text("ou", style = Tipo.apoio)
            Box(Modifier.width(28.dp).height(1.dp).background(Obsidian.borderDim))
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OnbButton("explorar constelações abertas", primary = false, onClick = aoExplorar)
        }
    }
}

@Composable
private fun CartaoDeEntrada(nome: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✦", style = TextStyle(color = Obsidian.accent, fontSize = 16.sp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "você entrou em $nome",
                style = TextStyle(color = Obsidian.text1, fontSize = 14.sp),
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text("o Astra vai abrir por lá.", style = Tipo.apoio)
        }
    }
}

@Composable
private fun PhotoStep(displayName: String, avatarUrl: String?, busy: Boolean, onPick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "sua foto",
            style = TextStyle(color = Obsidian.text1, fontSize = 22.sp, fontFamily = DmSerif, fontWeight = FontWeight.Light),
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.clip(CircleShape).border(2.dp, Obsidian.accent, CircleShape).padding(3.dp),
        ) {
            DesktopAvatar(avatarUrl, displayName, 96)
        }
        Spacer(Modifier.height(18.dp))
        OnbButton(
            text = if (busy) "carregando…" else if (avatarUrl == null) "escolher imagem" else "trocar imagem",
            primary = false,
            onClick = onPick,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "opcional — é possível mudar quando quiser nas configurações.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun PermsStep(onTestarAviso: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "o que o Astra precisa",
            style = TextStyle(color = Obsidian.text1, fontSize = 22.sp, fontFamily = DmSerif, fontWeight = FontWeight.Light),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "O Windows guarda microfone, câmera e avisos atrás de um interruptor — e quando ele " +
                "bloqueia, não avisa ninguém: some o som e pronto. É possível liberar agora ou depois, " +
                "em Configurações > Permissões.",
            style = TextStyle(color = Obsidian.text3, fontSize = 12.5.sp, lineHeight = 18.sp),
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PainelDePermissoes(onTestarAviso = onTestarAviso, detalhado = false)
        }
    }
}

@Composable
private fun OnbButton(text: String, primary: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .clickScale(src)
            .clip(shape)
            .background(
                when {
                    !primary -> if (hovered) Obsidian.raised else Obsidian.overlay.copy(alpha = 0.0f)
                    hovered -> Obsidian.text1
                    else -> Obsidian.accent
                },
            )
            .border(1.dp, if (primary) Obsidian.accent else Obsidian.borderMid, shape)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 34.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(
                color = if (primary) Obsidian.void else Obsidian.text2,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
