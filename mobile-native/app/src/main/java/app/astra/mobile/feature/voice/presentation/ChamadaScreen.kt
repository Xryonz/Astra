package app.astra.mobile.feature.voice.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import app.astra.mobile.core.voice.LigacaoDeSussurro
import app.astra.mobile.core.voice.LigacaoNaTela
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.theme.DmMono
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.PhoneOff
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LigacaoViewModel @Inject constructor(
    private val ligacaoDeSussurro: LigacaoDeSussurro,
) : ViewModel() {
    val ligacao = ligacaoDeSussurro.ligacao
    val entrouNaCall = ligacaoDeSussurro.entrouNaCall

    fun ligar(conversationId: String, nome: String, foto: String?) = ligacaoDeSussurro.ligar(conversationId, nome, foto)
    fun atender() = ligacaoDeSussurro.atender()
    fun recusar() = ligacaoDeSussurro.recusar()
}

@Composable
fun ChamadaScreen(
    ligacao: LigacaoNaTela,
    atenderJa: Boolean,
    aoAtender: () -> Unit,
    aoRecusar: () -> Unit,
) {
    val contexto = LocalContext.current
    val semMovimento = LocalAppPrefs.current.reduceMotion
    var semMicrofone by remember { mutableStateOf(false) }
    val pedirMicrofone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { liberado ->
        if (liberado) aoAtender() else semMicrofone = true
    }
    val atender = {
        val tem = ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (tem) aoAtender() else pedirMicrofone.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(atenderJa) { if (atenderJa) atender() }
    BackHandler { aoRecusar() }

    val pulso = if (semMovimento) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "halo").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Restart),
            label = "halo",
        )
    }
    val accent = astraColors.accent

    Box(
        Modifier
            .fillMaxSize()
            .background(astraColors.base)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(190.dp).drawBehind {
                        if (semMovimento) return@drawBehind
                        for (atraso in floatArrayOf(0f, 0.5f)) {
                            val t = (pulso.value + atraso) % 1f
                            drawCircle(
                                color = accent.copy(alpha = 0.28f * (1f - t)),
                                radius = size.minDimension / 2f * (0.52f + t * 0.48f),
                            )
                        }
                    },
                )
                AstraAvatar(ligacao.avatarUrl, ligacao.nome, size = 104)
            }
            Spacer(Modifier.height(26.dp))
            Text(ligacao.nome, fontFamily = DmSerif, fontSize = 26.sp, color = astraColors.text1, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                if (ligacao.euLiguei) "chamando…" else "está te chamando",
                fontFamily = DmMono,
                fontSize = 13.sp,
                color = astraColors.text3,
            )
            if (semMicrofone) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "O microfone está bloqueado para o Astra. Libere nas configurações do Android para atender.",
                    fontSize = 13.sp,
                    color = astraColors.danger,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(44.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (!ligacao.euLiguei) {
                    BotaoDeChamada(Lucide.Phone, "atender", astraColors.success, onClick = atender)
                }
                BotaoDeChamada(
                    Lucide.PhoneOff,
                    if (ligacao.euLiguei) "desistir" else "recusar",
                    astraColors.danger,
                    onClick = aoRecusar,
                )
            }
        }
    }
}

@Composable
private fun BotaoDeChamada(icone: ImageVector, rotulo: String, cor: Color, onClick: () -> Unit) {
    val comVibracao = LocalAppPrefs.current.haptics
    val haptico = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(cor.copy(alpha = 0.14f))
                .border(1.dp, cor.copy(alpha = 0.5f), CircleShape)
                .clickable {
                    if (comVibracao) haptico.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                }
                .semantics {
                    contentDescription = rotulo
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(rotulo, fontFamily = DmMono, fontSize = 12.sp, color = astraColors.text3)
    }
}
