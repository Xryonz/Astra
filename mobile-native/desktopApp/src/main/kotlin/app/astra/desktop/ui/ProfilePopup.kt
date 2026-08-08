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
import androidx.compose.foundation.layout.aspectRatio
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
import app.astra.mobile.core.network.dto.MemberRoleDto
import app.astra.mobile.core.network.dto.ProfileViewWrapper
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import org.koin.core.context.GlobalContext
import zed.rainxch.rikkaui.components.ui.skeleton.Skeleton
import zed.rainxch.rikkaui.components.ui.skeleton.SkeletonAnimation

// Card de perfil dos OUTROS (F3): clique no avatar (chat/membros) abre popup
// com o perfil + "enviar sussurro". Busca so quando abre, com cache de 5min
// (mesma politica do ProfileHoverCard do web).

private const val CACHE_MS = 5 * 60_000L
// Guarda o ENVELOPE inteiro, e nao so o usuario. Os vinculos (constelacoes e
// amigos em comum) vem na mesma resposta e estavam sendo jogados fora — o card
// pedia o dado, recebia, e descartava antes de desenhar.
private val profileCache = mutableMapOf<String, Pair<ProfileViewWrapper, Long>>()

private fun cached(userId: String): ProfileViewWrapper? =
    profileCache[userId]?.takeIf { System.currentTimeMillis() - it.second < CACHE_MS }?.first

// Alguem editou o perfil -> a copia guardada envelheceu na hora. Sem isto o cache
// de 5min seguraria a foto e o nome VELHOS mesmo com o aviso ao vivo chegando: o
// evento atualizaria a lista de membros e o card continuaria desatualizado.
fun invalidateProfileCache(userId: String) {
    profileCache.remove(userId)
}

// Abre ao LADO da ancora (direita; vira pra esquerda se não couber) e clampa
// na vertical — funciona tanto no chat quanto no painel de membros na borda.
// AO LADO da ancora, encostando em nada.
//
// As medidas chegam aqui em PIXEL, não em dp — `calculatePosition` fala a lingua
// da tela crua. A versao antiga somava `8` direto, o que numa tela a 150% (o
// normal no Windows) dava ~5dp de folga: o card ficava colado no painel de
// membros, cruzando a linha que marca o limite dele. Por isso a folga agora
// chega convertida de dp pelo chamador, que tem o LocalDensity.
//
// A margem existe pelo mesmo motivo, na vertical: o clamp antigo era
// `coerceAtLeast(0)`, e zero e a borda EXATA da janela — o card do primeiro
// membro da lista encostava na barra de titulo.
private class AoLadoDaAncora(private val folgaPx: Int, private val margemPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Direita primeiro; se nao couber inteiro, esquerda. O painel de membros
        // mora na borda direita da janela, entao la cai sempre na esquerda — que e
        // exatamente onde o card deve nascer.
        val direita = anchorBounds.right + folgaPx
        val x = if (direita + popupContentSize.width + margemPx <= windowSize.width) direita
        else (anchorBounds.left - popupContentSize.width - folgaPx)
            .coerceIn(margemPx, (windowSize.width - popupContentSize.width - margemPx).coerceAtLeast(margemPx))
        val y = anchorBounds.top
            .coerceIn(margemPx, (windowSize.height - popupContentSize.height - margemPx).coerceAtLeast(margemPx))
        return IntOffset(x, y)
    }
}

// Envolve o gatilho (avatar/linha) com clique-abre-perfil.
@Composable
fun ProfileAnchor(
    userId: String,
    isMe: Boolean,
    onStartDm: (username: String, title: String) -> Unit,
    // Cargos da pessoa NESTA constelação. So o painel de membros tem isso em
    // maos; do chat vem vazio, e a secao de cargos some junto. Cartao sem a secao
    // e melhor que cartao com uma secao vazia.
    cargos: List<MemberRoleDto> = emptyList(),
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    // dp -> px AQUI, onde existe densidade. Dentro do PopupPositionProvider não
    // existe: ele nao e composable e so recebe pixel.
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
                    // "ver perfil completo": fecha o card e abre o modal central.
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

    // Entrada: fade + subida leve (curva do mobile).
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
            // MESMO desenho da previa das Configuracoes (ProfileCard) — nao existem
            // mais duas copias pra divergir. O que e proprio DAQUI sao os botoes,
            // que so fazem sentido no cartao de verdade.
            ProfileCard(
                dados = p.paraCartao(),
                variante = CardVariante.NORMAL,
                modifier = Modifier.width(320.dp),
                servidoresEmComum = v.mutualServers,
                amigosEmComum = v.mutualFriends,
                cargos = cargos,
                // As acoes sobem pro banner (estilo Discord): o rodape fica so com
                // "ver perfil completo", e o cartao encurta uma linha inteira.
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

// Acao redonda no canto do banner. Fundo escuro semitransparente porque ela
// pousa sobre uma IMAGEM que pode ser de qualquer cor — sem o veu, um banner
// claro engole o icone.
@Composable
private fun AcaoRedonda(icone: ImageVector, rotulo: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clickScale(src, formaDoFoco = CircleShape)
            .clip(CircleShape)
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
