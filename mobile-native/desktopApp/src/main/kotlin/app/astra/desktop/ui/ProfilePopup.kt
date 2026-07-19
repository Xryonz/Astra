package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseSpring
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ProfileUserDto
import org.koin.core.context.GlobalContext
import zed.rainxch.rikkaui.components.ui.skeleton.Skeleton
import zed.rainxch.rikkaui.components.ui.skeleton.SkeletonAnimation

// Card de perfil dos OUTROS (F3): clique no avatar (chat/membros) abre popup
// com o perfil + "enviar sussurro". Busca so quando abre, com cache de 5min
// (mesma politica do ProfileHoverCard do web).

private const val CACHE_MS = 5 * 60_000L
private val profileCache = mutableMapOf<String, Pair<ProfileUserDto, Long>>()

private fun cached(userId: String): ProfileUserDto? =
    profileCache[userId]?.takeIf { System.currentTimeMillis() - it.second < CACHE_MS }?.first

// Abre ao LADO da ancora (direita; vira pra esquerda se nao couber) e clampa
// na vertical — funciona tanto no chat quanto no painel de membros na borda.
private object BesideAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val right = anchorBounds.right + 8
        val x = if (right + popupContentSize.width <= windowSize.width) right
        else (anchorBounds.left - popupContentSize.width - 8).coerceAtLeast(0)
        val y = anchorBounds.top.coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

// Envolve o gatilho (avatar/linha) com clique-abre-perfil.
@Composable
fun ProfileAnchor(
    userId: String,
    isMe: Boolean,
    onStartDm: (username: String, title: String) -> Unit,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { open = true },
    ) {
        content()
        if (open) {
            Popup(
                popupPositionProvider = BesideAnchor,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                ProfilePopupCard(
                    userId = userId,
                    isMe = isMe,
                    onStartDm = { u, t ->
                        open = false
                        onStartDm(u, t)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfilePopupCard(userId: String, isMe: Boolean, onStartDm: (String, String) -> Unit) {
    val koin = GlobalContext.get()
    var profile by remember(userId) { mutableStateOf(cached(userId)) }
    LaunchedEffect(userId) {
        if (profile == null) {
            profile = runCatching { koin.get<UserApi>().profile(userId).data?.user }.getOrNull()
                ?.also { profileCache[userId] = it to System.currentTimeMillis() }
        }
    }

    // Entrada: fade + subida leve (curva do mobile).
    val entered = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = entered,
        enter = fadeIn(tween(240, easing = EaseSpring)) +
            slideInVertically(tween(280, easing = EaseSpring)) { it / 10 },
    ) {
        Column(
            Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
        ) {
            val p = profile
            if (p == null) {
                CardSkeleton()
            } else {
                val ring = userColor(p.id)
                // bannerColor guarda CSS ("linear-gradient(...)"), nao hex — ler
                // como hex fazia TODO gradiente virar cinza liso aqui. ProfileBanner
                // traduz o CSS e aplica o enquadramento salvo.
                ProfileBanner(
                    css = p.bannerColor,
                    imageUrl = p.bannerUrl,
                    positionY = p.bannerPositionY ?: 50,
                    scale = p.bannerScale ?: 100,
                    fallback = Obsidian.overlay,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Box(
                        Modifier
                            .offset(y = (-26).dp)
                            .clip(CircleShape)
                            .background(Obsidian.raised)
                            .border(3.dp, ring, CircleShape)
                            .padding(3.dp),
                    ) {
                        DesktopAvatar(p.avatarUrl, p.displayName ?: p.username, 52)
                    }
                    Column(Modifier.offset(y = (-14).dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.displayName ?: p.username,
                                style = TextStyle(color = Obsidian.text1, fontSize = 18.sp, fontFamily = DmSerif),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            StatusDot(
                                status = userStatus(p.effectiveStatus),
                                size = 10.dp,
                                cutoutColor = Obsidian.raised,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "@${p.username}",
                                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                            )
                            if (!p.pronouns.isNullOrBlank()) {
                                Text(
                                    "  ·  ${p.pronouns}",
                                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                                )
                            }
                        }
                        if (!p.customStatus.isNullOrBlank() || !p.statusEmoji.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                listOfNotNull(p.statusEmoji, p.customStatus).joinToString(" "),
                                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                            )
                        }
                        if (!p.bio.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            HairRule()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                p.bio.orEmpty(),
                                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
                                maxLines = 3, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!isMe) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "enviar sussurro",
                                style = TextStyle(
                                    color = Obsidian.accent,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Obsidian.accentDim, RoundedCornerShape(8.dp))
                                    .clickable { onStartDm(p.username, p.displayName ?: p.username) }
                                    .padding(vertical = 8.dp),
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSkeleton() {
    val shimmer = SkeletonAnimation.Shimmer
    Column(Modifier.padding(16.dp)) {
        Skeleton(Modifier.size(52.dp), shimmer, CircleShape)
        Spacer(Modifier.height(10.dp))
        Skeleton(Modifier.width(150.dp).height(14.dp), shimmer, RoundedCornerShape(5.dp))
        Spacer(Modifier.height(6.dp))
        Skeleton(Modifier.width(100.dp).height(10.dp), shimmer, RoundedCornerShape(5.dp))
        Spacer(Modifier.height(10.dp))
        Skeleton(Modifier.fillMaxWidth().height(10.dp), shimmer, RoundedCornerShape(5.dp))
        Spacer(Modifier.height(5.dp))
        Skeleton(Modifier.width(180.dp).height(10.dp), shimmer, RoundedCornerShape(5.dp))
    }
}
