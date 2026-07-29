package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import app.astra.desktop.profile.AvatarPicker
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

// Onboarding de primeiro acesso (combo: takeover curto + checklist no palco). ESTE
// arquivo e o TAKEOVER — 3 passos sobre o mesmo ceu (aurora/estrelas ja vivem na
// janela, Main.kt), no idioma editorial do login. Dispara so depois de CRIAR CONTA
// (Main passa isNew=true) e some ao concluir; o gatilho persiste numa pref local
// por conta (uiPref "onboarded:<userId>"). O checklist residual vive no palco vazio.
//
// Passos: boas-vindas -> o idioma do ceu (constelacao/orbita/sussurro) -> sua foto.
// A foto e opcional e usa o MESMO caminho do perfil (data-URI via AvatarPicker, nao
// upload). Reduzir movimento: as trocas viram instantaneas.
private enum class OnbStep { WELCOME, SKY, PHOTO }

@Composable
fun OnboardingScreen(displayName: String, onDone: () -> Unit) {
    val reduce = LocalReduceMotion.current
    var step by remember { mutableStateOf(OnbStep.WELCOME) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val steps = OnbStep.entries
    val idx = steps.indexOf(step)

    fun pickPhoto() {
        if (busy) return
        scope.launch {
            // O seletor nativo bloqueia (modal) — normal pra file dialog. So o
            // encode/rede vao pra IO.
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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(460.dp).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Moldura de altura estavel: os passos trocam DENTRO dela, sem o card
            // pular de tamanho a cada avanco.
            Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
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
                        OnbStep.PHOTO -> PhotoStep(displayName, avatarUrl, busy, ::pickPhoto)
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            // Pontinhos de progresso (acendem no accent conforme avanca).
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
                    OnbStep.SKY -> "continuar"
                    OnbStep.PHOTO -> "concluir"
                },
                onClick = {
                    when (step) {
                        OnbStep.WELCOME -> step = OnbStep.SKY
                        OnbStep.SKY -> step = OnbStep.PHOTO
                        OnbStep.PHOTO -> onDone()
                    }
                },
            )
            if (step == OnbStep.PHOTO) {
                Spacer(Modifier.height(12.dp))
                val skip = remember { MutableInteractionSource() }
                Text(
                    "pular por agora",
                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                    modifier = Modifier.clickable(interactionSource = skip, indication = null, onClick = onDone),
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
            "opcional — dá pra mudar quando quiser nas configurações.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, textAlign = TextAlign.Center),
        )
    }
}

// Botao do onboarding: primario (accent cheio) ou secundario (contorno). clickScale
// no mesmo idioma dos outros botoes do app.
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
