package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.MutualServerDto
import app.astra.mobile.core.network.dto.ProfileViewWrapper
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Perfil completo (F: pagina de perfil) — modal CENTRAL sobre scrim, o irmao
// maior do ProfilePopup. Abre pelo botao "ver perfil completo" do card pequeno.
// Alem do que o card mostra, traz "membro desde" e os SERVIDORES EM COMUM (já vem
// do GET /api/profile/:id -> ProfileViewWrapper; o card so descartava). A entrada
// (scrim + card + cascata das secoes) segue o idioma do CenteredConfirmDialog;
// fable refina a coreografia depois. Respeita LocalReduceMotion.

private object CenterFill : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePage(
    userId: String,
    isMe: Boolean,
    onStartDm: (username: String, title: String) -> Unit,
    onClose: () -> Unit,
) {
    val koin = GlobalContext.get()
    var data by remember(userId) { mutableStateOf<ProfileViewWrapper?>(null) }
    LaunchedEffect(userId) {
        data = runCatching { koin.get<UserApi>().profile(userId).data }.getOrNull()
    }

    // Entrada: scrim faz fade, o card escala/sobe de leve. Uma passada; reduzir
    // movimento -> aparece pronto. Lido dentro do graphicsLayer (frame sem recompor).
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            appear.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    // Fecha ANIMANDO (a mesma curva tocada pra tras) antes de avisar o host — sem
    // isso o modal some seco. `closing` trava reentrada (duplo clique scrim/X/Esc).
    fun requestClose() {
        if (closing) return
        closing = true
        if (reduce) { onClose(); return }
        scope.launch {
            appear.animateTo(0f, tween(160, easing = EaseOutStd))
            onClose()
        }
    }

    Popup(
        popupPositionProvider = CenterFill,
        onDismissRequest = { requestClose() },
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { e ->
            if (e.key == Key.Escape && e.type == KeyEventType.KeyDown) { requestClose(); true } else false
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            // Scrim: escurece o resto e fecha ao clicar fora.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { requestClose() },
                    ),
            )
            // Card central.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .graphicsLayer {
                            alpha = appear.value.coerceIn(0f, 1f)
                            val s = 0.94f + 0.06f * appear.value
                            scaleX = s
                            scaleY = s
                            translationY = (1f - appear.value) * 16.dp.toPx()
                        }
                        .width(LARGURA_CARTAO_COMPLETO)
                        .heightIn(max = 720.dp)
                        .clip(RoundedCornerShape(16.dp))
                        // Rodape dissolvido: os cantos já eram arredondados, mas quando o
                        // conteudo passa da altura maxima o scroll FATIA o texto no meio e
                        // o corte seco parece quina dura. Este veu na borda de baixo faz o
                        // conteudo sumir em vez de ser cortado. Fica ANTES do verticalScroll
                        // de proposito: assim ele desenha no espaco da JANELA (fixo no pe do
                        // cartao) e não rola junto com o conteudo.
                        .drawWithContent {
                            drawContent()
                            val h = 26.dp.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    listOf(Color.Transparent, Obsidian.void.copy(alpha = 0.85f)),
                                    startY = size.height - h,
                                    endY = size.height,
                                ),
                                topLeft = Offset(0f, size.height - h),
                                size = Size(size.width, h),
                            )
                        }
                        // Engole o clique (senao o scrim fecha ao clicar no card).
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .verticalScroll(rememberScrollState()),
                ) {
                    val d = data
                    if (d == null) {
                        PageSkeleton()
                    } else {
                        val nome = d.user.displayName ?: d.user.username
                        ProfileCard(
                            dados = d.user.paraCartao(),
                            variante = CardVariante.COMPLETO,
                            servidoresEmComum = d.mutualServers,
                            aoFechar = { requestClose() },
                            rodape = if (isMe) null else ({
                                val src = remember { MutableInteractionSource() }
                                Text(
                                    "enviar sussurro",
                                    style = TextStyle(
                                        color = Obsidian.void, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickScale(src)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Obsidian.accent)
                                        .clickable(interactionSource = src, indication = null) {
                                            onStartDm(d.user.username, nome)
                                        }
                                        .padding(vertical = 11.dp),
                                    maxLines = 1,
                                )
                            }),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageSkeleton() {
    Box(Modifier.fillMaxWidth().height(140.dp).background(Obsidian.overlay))
    Column(Modifier.padding(horizontal = 20.dp)) {
        Box(
            Modifier
                .offset(y = (-38).dp)
                .clip(CircleShape)
                .background(Obsidian.raised)
                .border(4.dp, Obsidian.borderMid, CircleShape)
                .padding(4.dp),
        ) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(Obsidian.overlay))
        }
        Column(Modifier.offset(y = (-24).dp)) {
            Box(Modifier.width(180.dp).height(20.dp).clip(RoundedCornerShape(6.dp)).background(Obsidian.overlay))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(120.dp).height(12.dp).clip(RoundedCornerShape(5.dp)).background(Obsidian.overlay))
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(5.dp)).background(Obsidian.overlay))
            Spacer(Modifier.height(20.dp))
        }
    }
}
