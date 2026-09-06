package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.shell.ChatTarget
import app.astra.desktop.shell.ChatVm
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.voice.CallNaSala
import app.astra.mobile.core.network.dto.ChannelDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.Volume2

@Composable
internal fun Stage(
    server: ServerDto?,
    chat: ChatTarget?,
    voiceChannel: ChannelDto?,
    call: CallNaSala?,
    mudo: Boolean,
    aoAlternarMudo: () -> Unit,
    ensurdecido: Boolean,
    aoAlternarEnsurdecer: () -> Unit,
    voicePresence: List<String>,
    onJoinVoice: () -> Unit,
    onLeaveVoice: () -> Unit,
    onLigarSussurro: (ChatTarget.Dm, video: Boolean) -> Unit,
    botDoOutroLado: Boolean,
    fonteDoSussurro: String?,
    createChatVm: (ChatTarget) -> ChatVm,
    members: List<ServerMemberDto>,
    me: ProfileUserDto?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onStartDm: (String, String) -> Unit,
    showDiscover: Boolean,
    onDiscoverJoined: (String) -> Unit,
    joinedServerIds: Set<String> = emptySet(),
    showFriends: Boolean,
    leiturasAoEntrar: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val pulo = remember { PuloParaMensagem() }
    androidx.compose.runtime.CompositionLocalProvider(LocalPuloParaMensagem provides pulo) {
    Column(modifier.fillMaxHeight().panelSurface(Obsidian.raised, 0.52f)) {
        if (showFriends) {
            FriendsView(onStartDm, Modifier.fillMaxSize())
            return@Column
        }
        if (showDiscover) {
            DiscoverView(onDiscoverJoined, joinedIds = joinedServerIds, modifier = Modifier.fillMaxSize())
            return@Column
        }
        val bareLanding = chat == null && voiceChannel == null
        if (!bareLanding) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Obsidian.overlay.copy(alpha = 0.45f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val leadIcon = when {
                    voiceChannel != null -> Lucide.Volume2
                    chat is ChatTarget.Channel -> Lucide.Hash
                    else -> null
                }
                if (leadIcon != null) {
                    LIcon(leadIcon, tint = Obsidian.text1, size = 15.dp)
                    Spacer(Modifier.width(7.dp))
                }
                Box(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            voiceChannel != null -> AnnotatedString(voiceChannel.name)
                            chat is ChatTarget.Channel -> AnnotatedString(chat.title)
                            chat is ChatTarget.Dm -> buildAnnotatedString {
                                append("sussurro · ")
                                withStyle(SpanStyle(fontFamily = fonteDoSussurro?.let { profileFontFamily(it) })) {
                                    append(chat.title)
                                }
                            }
                            server != null -> AnnotatedString("constelação · ${server.name}")
                            else -> AnnotatedString("sussurros")
                        },
                        style = TextStyle(
                            color = if (chat != null || voiceChannel != null) Obsidian.text1 else Obsidian.text3,
                            fontSize = 13.sp,
                        ),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
                    )
                }
                if (chat is ChatTarget.Channel && voiceChannel == null) {
                    AlfineteDoCanal(chat.id)
                }
                if (chat is ChatTarget.Dm && voiceChannel == null && !botDoOutroLado) {
                    BotaoDeLigar(Lucide.Phone, "ligar") { onLigarSussurro(chat, false) }
                    Spacer(Modifier.width(4.dp))
                    BotaoDeLigar(Lucide.Video, "chamada de vídeo") { onLigarSussurro(chat, true) }
                }
            }
        }

        if (voiceChannel != null) {
            if (call != null) VoiceView(voiceChannel, members, me, call, mudo, aoAlternarMudo, ensurdecido, aoAlternarEnsurdecer, onLeaveVoice, server?.id)
            else VoiceLobby(voiceChannel, members, voicePresence, onJoinVoice)
            return@Column
        }

        TrocaDePagina(
            alvo = chat,
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            if (target != null) {
                key(target.id) {
                val chatVm = remember { createChatVm(target) }
                DisposableEffect(Unit) { onDispose { chatVm.dispose() } }
                val botAqui = remember(target.id, server) {
                    val ch = server?.channels?.find { it.id == target.id }
                    val cat = server?.categories?.find { it.id == ch?.categoryId }
                    ch?.botEnabled ?: cat?.botEnabled ?: true
                }
                ChatView(
                    target, chatVm, onStartDm,
                    botAqui = botAqui,
                    serverId = server?.id,
                    membros = if (target is ChatTarget.Channel) members else emptyList(),
                    lidoAte = leiturasAoEntrar[target.id],
                )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> ChatSkeleton()
                        error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error, style = TextStyle(color = Obsidian.danger, fontSize = 13.sp))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "tentar de novo",
                                style = TextStyle(color = Obsidian.accent, fontSize = 13.sp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                                    .clickable(onClick = onRetry)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        else -> EmptyStage(isServer = server != null)
                    }
                }
            }
        }
    }
    }
}
