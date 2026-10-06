package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.EaseSpring
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.AtividadeDto
import app.astra.mobile.core.network.dto.MemberRoleDto
import app.astra.mobile.core.network.dto.ProfileViewWrapper
import org.koin.core.context.GlobalContext

private const val CACHE_MS = 5 * 60_000L

private val ALTURA_MIN_CARTAO = 420.dp
private val profileCache = mutableMapOf<String, Pair<ProfileViewWrapper, Long>>()

private fun cached(userId: String): ProfileViewWrapper? =
    profileCache[userId]?.takeIf { System.currentTimeMillis() - it.second < CACHE_MS }?.first

private fun lembrarPerfil(userId: String, visao: ProfileViewWrapper) {
    val agora = System.currentTimeMillis()
    profileCache.entries.removeAll { agora - it.value.second >= CACHE_MS }
    profileCache[userId] = visao to agora
}

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
    entraPelaDireita: Boolean = false,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    val acoes = LocalAcoesDoPerfil.current
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
                    onChamar = { u, t -> open = false; acoes.chamar(u, t) },
                    onEditarPerfil = { open = false; acoes.editarPerfil() },
                    onOpenFull = { open = false; full = true },
                    entraPelaDireita = entraPelaDireita,
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
    val acoes = LocalAcoesDoPerfil.current
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
                onChamar = { u, t -> onClose(); acoes.chamar(u, t) },
                onEditarPerfil = { onClose(); acoes.editarPerfil() },
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
    onChamar: (String, String) -> Unit,
    onEditarPerfil: () -> Unit,
    onOpenFull: () -> Unit,
    entraPelaDireita: Boolean = false,
) {
    val koin = GlobalContext.get()
    var visao by remember(userId) { mutableStateOf(cached(userId)) }
    LaunchedEffect(userId) {
        if (visao == null) {
            visao = runCatching { koin.get<UserApi>().profile(userId).data }.getOrNull()
                ?.also { lembrarPerfil(userId, it) }
        }
    }
    var atividade by remember(userId) { mutableStateOf<AtividadeDto?>(null) }
    LaunchedEffect(userId) {
        atividade = runCatching { koin.get<UserApi>().activity(userId).data?.get(userId) }.getOrNull()
    }

    val entered = remember { MutableTransitionState(false).apply { targetState = true } }
    val entrada = when {
        LocalReduceMotion.current -> EnterTransition.None
        entraPelaDireita -> fadeIn(tween(240, easing = EaseSpring)) +
            slideInHorizontally(tween(280, easing = EaseSpring)) { it / 8 }
        else -> fadeIn(tween(240, easing = EaseSpring)) +
            slideInVertically(tween(280, easing = EaseSpring)) { it / 10 }
    }
    AnimatedVisibility(visibleState = entered, enter = entrada) {
        val v = visao
        if (v == null) {
            Column(
                Modifier.width(320.dp).height(ALTURA_MIN_CARTAO)
                    .clip(RoundedCornerShape(12.dp))
                    .profileCardBackdrop(null)
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
            ) { EsperaNoTopo(true) }
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
            ) {
                val nome = p.displayName ?: p.username
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isMe) {
                        BotaoDoCartao("editar perfil", principal = true, Modifier.weight(1f), onEditarPerfil)
                    } else {
                        BotaoDoCartao("sussurrar", principal = true, Modifier.weight(1f)) {
                            onStartDm(p.username, nome)
                        }
                        if (p.username != USUARIO_DA_BOT) {
                            BotaoDoCartao("chamar", principal = false, Modifier.weight(1f)) {
                                onChamar(p.username, nome)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                BotaoDoCartao("ver perfil completo", principal = false, Modifier.fillMaxWidth(), onOpenFull)
            }
        }
    }
}

@Composable
private fun BotaoDoCartao(rotulo: String, principal: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val forma = RoundedCornerShape(8.dp)
    Text(
        rotulo,
        style = TextStyle(
            color = if (principal) Obsidian.void else Obsidian.text1,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
        modifier = modifier
            .clickScale(src, formaDoFoco = RoundedCornerShape(11.dp), folgaDoFoco = 3.dp)
            .clip(forma)
            .then(
                if (principal) Modifier.background(Obsidian.accent)
                else Modifier.border(1.dp, Obsidian.borderMid, forma)
            )
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = 9.dp),
        maxLines = 1,
    )
}

