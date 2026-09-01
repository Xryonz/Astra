package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import app.astra.desktop.ui.theme.EaseSpring
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.AtividadeDto
import app.astra.mobile.core.network.dto.MemberRoleDto
import app.astra.mobile.core.network.dto.ProfileViewWrapper
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import org.koin.core.context.GlobalContext
import zed.rainxch.rikkaui.components.ui.skeleton.Skeleton
import zed.rainxch.rikkaui.components.ui.skeleton.SkeletonAnimation

private const val CACHE_MS = 5 * 60_000L

private val ALTURA_MIN_CARTAO = 420.dp
private val profileCache = mutableMapOf<String, Pair<ProfileViewWrapper, Long>>()

private fun cached(userId: String): ProfileViewWrapper? =
    profileCache[userId]?.takeIf { System.currentTimeMillis() - it.second < CACHE_MS }?.first

fun invalidateProfileCache(userId: String) {
    profileCache.remove(userId)
}

private class AoLadoDaAncora(private val folgaPx: Int, private val margemPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val direita = anchorBounds.right + folgaPx
        val x = if (direita + popupContentSize.width + margemPx <= windowSize.width) direita
        else (anchorBounds.left - popupContentSize.width - folgaPx)
            .coerceIn(margemPx, (windowSize.width - popupContentSize.width - margemPx).coerceAtLeast(margemPx))
        val y = anchorBounds.top
            .coerceIn(margemPx, (windowSize.height - popupContentSize.height - margemPx).coerceAtLeast(margemPx))
        return IntOffset(x, y)
    }
}

@Composable
fun ProfileAnchor(
    userId: String,
    isMe: Boolean,
    onStartDm: (username: String, title: String) -> Unit,
    cargos: List<MemberRoleDto> = emptyList(),
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    val densidade = LocalDensity.current
    val posicao = remember(densidade) {
        with(densidade) { AoLadoDaAncora(folgaPx = 12.dp.roundToPx(), margemPx = 12.dp.roundToPx()) }
    }
    Box(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { open = true },
    ) {
        content()
        if (open) {
            Popup(
                popupPositionProvider = posicao,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                ProfilePopupCard(
                    userId = userId,
                    isMe = isMe,
                    cargos = cargos,
                    onStartDm = { u, t ->
                        open = false
                        onStartDm(u, t)
                    },
                    onOpenFull = { open = false; full = true },
                )
            }
        }
    }
    if (full) {
        ProfilePage(
            userId = userId,
            isMe = isMe,
            onStartDm = { u, t -> full = false; onStartDm(u, t) },
            onClose = { full = false },
        )
    }
}

@Composable
fun ProfileCardNoPonto(
    userId: String,
    at: IntOffset,
    isMe: Boolean,
    onStartDm: (username: String, title: String) -> Unit,
    onClose: () -> Unit,
) {
    var full by remember(userId) { mutableStateOf(false) }
    if (!full) {
        Popup(
            popupPositionProvider = remember(at) { AtPointer(at) },
            onDismissRequest = onClose,
            properties = PopupProperties(focusable = true),
        ) {
            ProfilePopupCard(
                userId = userId,
                isMe = isMe,
                cargos = emptyList(),
                onStartDm = { u, t -> onStartDm(u, t) },
                onOpenFull = { full = true },
            )
        }
    } else {
        ProfilePage(
            userId = userId,
            isMe = isMe,
            onStartDm = { u, t -> onStartDm(u, t) },
            onClose = onClose,
        )
    }
}

@Composable
private fun ProfilePopupCard(
    userId: String,
    isMe: Boolean,
    cargos: List<MemberRoleDto>,
    onStartDm: (String, String) -> Unit,
    onOpenFull: () -> Unit,
) {
    val koin = GlobalContext.get()
    var visao by remember(userId) { mutableStateOf(cached(userId)) }
    LaunchedEffect(userId) {
        if (visao == null) {
            visao = runCatching { koin.get<UserApi>().profile(userId).data }.getOrNull()
                ?.also { profileCache[userId] = it to System.currentTimeMillis() }
        }
    }
    var atividade by remember(userId) { mutableStateOf<AtividadeDto?>(null) }
    LaunchedEffect(userId) {
        atividade = runCatching { koin.get<UserApi>().activity(userId).data?.get(userId) }.getOrNull()
    }

    val entered = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = entered,
        enter = fadeIn(tween(240, easing = EaseSpring)) +
            slideInVertically(tween(280, easing = EaseSpring)) { it / 10 },
    ) {
        val v = visao
        if (v == null) {
            Column(
                Modifier.width(320.dp).clip(RoundedCornerShape(12.dp))
                    .profileCardBackdrop(null)
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
            ) { CardSkeleton() }
        } else {
            val p = v.user
            ProfileCard(
                dados = p.paraCartao().copy(
                    atividade = atividade?.text,
                    atividadeDesde = atividade?.since ?: 0L,
                    corDoNome = LocalCoresDeCargo.current[userId],
                ),
                variante = CardVariante.NORMAL,
                modifier = Modifier.width(320.dp).heightIn(min = ALTURA_MIN_CARTAO),
                servidoresEmComum = v.mutualServers,
                amigosEmComum = v.mutualFriends,
                cargos = cargos,
                acoesNoBanner = if (isMe) null else {
                    {
                        AcaoRedonda(Lucide.MessageCircle, "Enviar sussurro") {
                            onStartDm(p.username, p.displayName ?: p.username)
                        }
                    }
                },
            ) {
                val fullSrc = remember { MutableInteractionSource() }
                Text(
                    "ver perfil completo",
                    style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickScale(fullSrc)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Obsidian.borderMid, RoundedCornerShape(8.dp))
                        .clickable(interactionSource = fullSrc, indication = null, onClick = onOpenFull)
                        .padding(vertical = 8.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AcaoRedonda(icone: ImageVector, rotulo: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clickScale(src, formaDoFoco = FormaDeBotao)
            .clip(FormaDeBotao)
            .background(Obsidian.void.copy(alpha = 0.5f))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icone, tint = Obsidian.text2, size = 15.dp, rotulo = rotulo, modifier = Modifier.padding(5.dp))
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
