package app.astra.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.shell.ChamadaNaTela
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.PhoneOff
import com.composables.icons.lucide.Video

@Composable
fun ChamadaScreen(
    chamada: ChamadaNaTela,
    onAtender: () -> Unit,
    onRecusar: () -> Unit,
) {
    val reduzir = LocalReduceMotion.current
    val pulso by if (reduzir) {
        remember { androidx.compose.runtime.mutableStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "halo").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
            label = "halo",
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Obsidian.base),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(190.dp).drawBehind {
                        if (reduzir) return@drawBehind
                        for (atraso in listOf(0f, 0.5f)) {
                            val t = (pulso + atraso) % 1f
                            drawCircle(
                                color = Obsidian.accent.copy(alpha = 0.28f * (1f - t)),
                                radius = size.minDimension / 2f * (0.52f + t * 0.48f),
                            )
                        }
                    },
                )
                Box(
                    Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(Obsidian.raised)
                        .border(1.dp, Obsidian.borderDim, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (chamada.avatarUrl != null) {
                        AstraImage(
                            url = chamada.avatarUrl,
                            contentDescription = chamada.nome,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            chamada.nome.take(1).uppercase(),
                            style = TextStyle(color = Obsidian.text2, fontSize = 34.sp, fontFamily = DmSerif),
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Text(
                chamada.nome,
                style = TextStyle(color = Obsidian.text1, fontSize = 24.sp, fontFamily = DmSerif),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    chamada.euLiguei && chamada.video -> "chamando em vídeo…"
                    chamada.euLiguei -> "chamando…"
                    chamada.video -> "está te chamando em vídeo"
                    else -> "está te chamando"
                },
                style = TextStyle(color = Obsidian.text3, fontSize = 12.sp, fontFamily = DmMono),
            )

            Spacer(Modifier.height(42.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                if (!chamada.euLiguei) {
                    BotaoDeChamada(
                        icone = if (chamada.video) Lucide.Video else Lucide.Phone,
                        rotulo = "atender",
                        cor = Obsidian.success,
                        onClick = onAtender,
                    )
                }
                BotaoDeChamada(
                    icone = Lucide.PhoneOff,
                    rotulo = if (chamada.euLiguei) "desistir" else "recusar",
                    cor = Obsidian.danger,
                    onClick = onRecusar,
                )
            }
        }
    }
}

@Composable
private fun BotaoDeChamada(
    icone: ImageVector,
    rotulo: String,
    cor: Color,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(58.dp)
                .graphicsLayer { val s = if (hov) 1.06f else 1f; scaleX = s; scaleY = s }
                .clip(CircleShape)
                .background(cor.copy(alpha = if (hov) 0.22f else 0.14f))
                .border(1.dp, cor.copy(alpha = if (hov) 0.9f else 0.5f), CircleShape)
                .hoverable(src)
                .clickable(interactionSource = src, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            LIcon(icone, tint = cor, size = 22.dp)
        }
        Spacer(Modifier.height(9.dp))
        Text(rotulo, style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono))
    }
}
