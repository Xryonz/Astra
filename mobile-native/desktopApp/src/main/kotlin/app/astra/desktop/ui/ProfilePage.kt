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
// Alem do que o card mostra, traz "membro desde" e os SERVIDORES EM COMUM (ja vem
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
                        .width(440.dp)
                        .heightIn(max = 640.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Obsidian.raised)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(16.dp))
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
                        PageBody(d, isMe, onStartDm, { requestClose() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageBody(d: ProfileViewWrapper, isMe: Boolean, onStartDm: (String, String) -> Unit, onClose: () -> Unit) {
    val p = d.user
    val name = p.displayName ?: p.username
    // Avatar "pop" com overshoot depois que o card assenta (unico momento bouncy).
    val reduce = LocalReduceMotion.current
    val avatarPop = remember(p.id) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(p.id) {
        if (!reduce) {
            delay(200)
            avatarPop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
        }
    }

    Box {
        ProfileBanner(
            css = p.bannerColor,
            imageUrl = p.bannerUrl,
            positionY = p.bannerPositionY ?: 50,
            scale = p.bannerScale ?: 100,
            fallback = Obsidian.overlay,
            modifier = Modifier.fillMaxWidth().height(140.dp),
        )
        // Sweep de luz ambar atravessa o banner uma vez (alvorecer).
        BannerSweep(p.id, Modifier.fillMaxWidth().height(140.dp))
        // Fechar (x) no canto do banner.
        val closeSrc = remember { MutableInteractionSource() }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(CircleShape)
                .background(Obsidian.void.copy(alpha = 0.5f))
                .clickable(interactionSource = closeSrc, indication = null) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            LIcon(Lucide.X, tint = Obsidian.text2, size = 15.dp, modifier = Modifier.padding(5.dp))
        }
    }

    Column(Modifier.padding(horizontal = 20.dp)) {
        // Avatar sobrepondo ~1/3 do banner (mesma proporcao do card).
        Box(
            // Sem anel por enquanto (decoracoes de perfil tipo Discord virao depois).
            Modifier
                .offset(y = (-38).dp)
                .graphicsLayer {
                    alpha = avatarPop.value.coerceIn(0f, 1f)
                    val s = 0.6f + 0.4f * avatarPop.value
                    scaleX = s
                    scaleY = s
                },
        ) {
            DesktopAvatar(p.avatarUrl, name, 88)
        }
        Column(Modifier.offset(y = (-24).dp)) {
            // Secoes em cascata (stagger fade+subida) — idioma do CascadeIn.
            CascadeIn(0, p.id, stepMs = 40L, startDelayMs = 220L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = TextStyle(
                            color = Obsidian.text1, fontSize = 24.sp, fontWeight = FontWeight.Medium,
                            fontFamily = profileFontFamily(p.displayFont),
                        ),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusDot(status = userStatus(p.effectiveStatus), size = 12.dp, cutoutColor = Obsidian.raised)
                }
            }
            CascadeIn(1, p.id, stepMs = 40L, startDelayMs = 220L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@${p.username}", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp, fontFamily = DmMono))
                    if (!p.pronouns.isNullOrBlank()) {
                        Text("  ·  ${p.pronouns}", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
                    }
                }
            }
            if (!p.customStatus.isNullOrBlank() || !p.statusEmoji.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                CascadeIn(2, p.id, stepMs = 40L, startDelayMs = 220L) {
                    Text(
                        listOfNotNull(p.statusEmoji, p.customStatus).joinToString(" "),
                        style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
                    )
                }
            }
            if (!p.bio.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                CascadeIn(3, p.id, stepMs = 40L, startDelayMs = 220L) {
                    Column {
                        HairRule()
                        Spacer(Modifier.height(10.dp))
                        SectionLabel("sobre")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            p.bio.orEmpty(),
                            style = TextStyle(color = Obsidian.text2, fontSize = 13.sp, lineHeight = 19.sp),
                        )
                    }
                }
            }
            memberSince(p.createdAt)?.let { since ->
                Spacer(Modifier.height(14.dp))
                CascadeIn(4, p.id, stepMs = 40L, startDelayMs = 220L) {
                    Column {
                        HairRule()
                        Spacer(Modifier.height(10.dp))
                        SectionLabel("membro")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "nas estrelas desde $since",
                            style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
                        )
                    }
                }
            }
            if (d.mutualServers.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                CascadeIn(5, p.id, stepMs = 40L, startDelayMs = 220L) {
                    Column {
                        HairRule()
                        Spacer(Modifier.height(10.dp))
                        SectionLabel("servidores em comum · ${d.mutualServers.size}")
                        Spacer(Modifier.height(9.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            d.mutualServers.forEachIndexed { i, s -> MutualChip(s, i) }
                        }
                    }
                }
            }
            if (!isMe) {
                Spacer(Modifier.height(18.dp))
                CascadeIn(6, p.id, stepMs = 40L, startDelayMs = 220L) {
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
                                onStartDm(p.username, name)
                            }
                            .padding(vertical = 11.dp),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
    )
}

private const val CHIP_STAGGER_MAX = 10

@Composable
private fun MutualChip(s: MutualServerDto, index: Int) {
    val reduce = LocalReduceMotion.current
    // 2o nivel de stagger: cada chip escala/aparece um tico depois do anterior.
    // Escala (nao translateY) — o FlowRow quebra linha, subir por linha ficaria torto.
    val pop = remember(s.id) { Animatable(if (reduce || index !in 0 until CHIP_STAGGER_MAX) 1f else 0f) }
    LaunchedEffect(s.id) {
        if (pop.value < 1f) {
            delay(index * 18L)
            pop.animateTo(1f, tween(180, easing = EaseOutStd))
        }
    }
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier
            .graphicsLayer {
                alpha = pop.value
                val sc = 0.85f + 0.15f * pop.value
                scaleX = sc
                scaleY = sc
            }
            .clickScale(src)
            .clip(RoundedCornerShape(9.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Obsidian.raised), contentAlignment = Alignment.Center) {
            if (!s.iconUrl.isNullOrBlank()) {
                DesktopAvatar(s.iconUrl, s.name, 22)
            } else {
                Text(s.name.take(1).uppercase(), style = TextStyle(color = Obsidian.accent, fontSize = 11.sp, fontFamily = DmSerif))
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            s.name,
            style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp),
        )
    }
}

// Sweep de luz ambar diagonal que atravessa o banner UMA vez na abertura (tipo
// alvorecer). Sutil (pico alpha 0.15), GPU-only, um-shot. Reduzir movimento = some.
@Composable
private fun BannerSweep(seedKey: Any?, modifier: Modifier = Modifier) {
    if (LocalReduceMotion.current) return
    val sweep = remember(seedKey) { Animatable(0f) }
    LaunchedEffect(seedKey) {
        delay(90)
        sweep.animateTo(1f, tween(650, easing = EaseOutSoft))
    }
    Box(
        modifier
            .clipToBounds()
            .graphicsLayer {
                translationX = (sweep.value * 1.8f - 0.5f) * size.width
                rotationZ = -16f
            }
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Obsidian.accent.copy(alpha = 0.15f), Color.Transparent),
                    ),
                    size = Size(size.width * 0.4f, size.height * 2f),
                )
            },
    )
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

// createdAt (ISO) -> "julho de 2026" (pt-BR). Falha silenciosa: sem data, sem linha.
private fun memberSince(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val date = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
        date.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR")))
    }.getOrNull()
}
