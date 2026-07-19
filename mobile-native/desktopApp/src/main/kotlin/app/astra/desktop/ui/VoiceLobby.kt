package app.astra.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Phone

// Antessala da orbita de voz: clicar numa sala NAO entra mais na call. Mostra
// quem ja esta la dentro (presenca do /voice/presence, a mesma que alimenta a
// sidebar) e um botao verde de telefone pra entrar de fato. Assim da pra ver se
// vale a pena entrar antes de abrir o microfone.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceLobby(
    channel: ChannelDto,
    members: List<ServerMemberDto>,
    presentUserIds: List<String>,
    onJoin: () -> Unit,
) {
    // Presenca vem como lista de ids; o nome/foto sai da lista de membros.
    val present = remember(presentUserIds, members) {
        presentUserIds.map { uid -> uid to members.find { it.userId == uid } }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "◉ ${channel.name}",
            style = TextStyle(color = Obsidian.accent, fontSize = 18.sp, fontFamily = DmSerif),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when (present.size) {
                0 -> "ninguem por aqui ainda"
                1 -> "1 pessoa na sala"
                else -> "${present.size} pessoas na sala"
            },
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        )

        if (present.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            FlowRow(
                Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                present.forEach { (uid, member) ->
                    val name = member?.user?.displayName ?: member?.user?.username ?: "alguem"
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .border(2.dp, userColor(uid), CircleShape)
                                .padding(2.dp),
                        ) {
                            DesktopAvatar(member?.user?.avatarUrl, name, 48)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            name,
                            style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(72.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(34.dp))
        JoinCallButton(onJoin)
        Spacer(Modifier.height(12.dp))
        Text(
            "entrar na call",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        )
    }
}

// Botao redondo verde (o gesto que todo mundo ja conhece de atender). Cresce um
// tico no hover; sem animacao continua — nada aqui pede frame por segundo.
@Composable
private fun JoinCallButton(onJoin: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val s by animateFloatAsState(if (hovered) 1.07f else 1f, tween(140), label = "joinScale")
    Box(
        Modifier
            .scale(s)
            .size(62.dp)
            .clip(CircleShape)
            .background(Obsidian.success)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onJoin),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(Lucide.Phone, tint = Obsidian.textInv, size = 25.dp)
    }
}
