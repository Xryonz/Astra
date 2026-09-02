package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.ContentScale
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
import app.astra.mobile.core.network.BadgeApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.XpApi
import app.astra.mobile.core.network.dto.MutualServerDto
import app.astra.mobile.core.network.dto.ProfileViewWrapper
import app.astra.mobile.core.network.dto.ProgressoDto
import app.astra.mobile.core.network.dto.UserBadgesDto
import app.astra.mobile.core.network.dto.UserDto
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.astra.desktop.ui.theme.Tipo

val LARGURA_PAGINA_PERFIL = 840.dp
val LARGURA_COLUNA_DO_CARTAO = 380.dp
val ALTURA_PAGINA_PERFIL = 640.dp

private fun Modifier.veuNoPe(): Modifier = drawWithContent {
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
    var progresso by remember(userId) { mutableStateOf<ProgressoDto?>(null) }
    var insignias by remember(userId) { mutableStateOf<UserBadgesDto?>(null) }
    LaunchedEffect(userId) {
        data = runCatching { koin.get<UserApi>().profile(userId).data }.getOrNull()
    }
    LaunchedEffect(userId) {
        progresso = runCatching { koin.get<XpApi>().de(userId).data }.getOrNull()
    }
    LaunchedEffect(userId) {
        insignias = runCatching { koin.get<BadgeApi>().de(userId).data }.getOrNull()
    }

    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            appear.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        }
    }
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
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { requestClose() },
                    )
                    .semCursorDeClique(),
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .graphicsLayer {
                            alpha = appear.value.coerceIn(0f, 1f)
                            val s = 0.94f + 0.06f * appear.value
                            scaleX = s
                            scaleY = s
                            translationY = (1f - appear.value) * 16.dp.toPx()
                        }
                        .width(LARGURA_PAGINA_PERFIL)
                        .height(ALTURA_PAGINA_PERFIL)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Obsidian.base)
                        .border(1.dp, Obsidian.borderMid, RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    val d = data
                    if (d == null) {
                        Column(Modifier.fillMaxWidth()) { PageSkeleton() }
                    } else {
                        val nome = d.user.displayName ?: d.user.username
                        Column(
                            Modifier
                                .width(LARGURA_COLUNA_DO_CARTAO)
                                .fillMaxHeight()
                                .veuNoPe(),
                        ) {
                            ProfileCard(
                                modifier = Modifier.fillMaxHeight(),
                                dados = d.user.paraCartao().copy(corDoNome = LocalCoresDeCargo.current[userId]),
                                variante = CardVariante.COMPLETO,
                                servidoresEmComum = emptyList(),
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
                        ColunaDeVinculos(
                            nome = nome,
                            isMe = isMe,
                            amigosEmComum = d.mutualFriends,
                            rostosEmComum = d.mutualFriendsList,
                            constelacoes = d.mutualServers,
                            progresso = progresso,
                            insignias = insignias,
                            onFechar = { requestClose() },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColunaDeVinculos(
    nome: String,
    isMe: Boolean,
    amigosEmComum: Int,
    rostosEmComum: List<UserDto>,
    constelacoes: List<MutualServerDto>,
    progresso: ProgressoDto?,
    insignias: UserBadgesDto?,
    onFechar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isMe) "seus vínculos" else "vínculos",
                style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif),
                modifier = Modifier.weight(1f),
            )
            val src = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(28.dp)
                    .clickScale(src, formaDoFoco = FormaDeBotao)
                    .clip(FormaDeBotao)
                    .background(Obsidian.overlay)
                    .border(1.dp, Obsidian.borderMid, FormaDeBotao)
                    .clickable(interactionSource = src, indication = null, onClick = onFechar),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(Lucide.X, tint = Obsidian.text2, size = 14.dp, rotulo = "fechar")
            }
        }
        Spacer(Modifier.height(14.dp))

        progresso?.let { p ->
            CartaoInterno(fundo = Obsidian.raised, padding = PaddingValues(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isMe) "SUA PROGRESSÃO" else "PROGRESSÃO",
                        style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "nível ${p.nivel}",
                        style = TextStyle(color = Obsidian.accent, fontSize = 12.sp, fontFamily = DmMono),
                    )
                }
                Spacer(Modifier.height(9.dp))
                BarraDeNivel(p)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${p.noNivel} / ${p.paraOProximo} para o próximo",
                    style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        val globais = insignias?.global.orEmpty()
        val deServidor = insignias?.server.orEmpty()
        if (globais.isNotEmpty() || deServidor.isNotEmpty()) {
            CartaoInterno(fundo = Obsidian.raised, padding = PaddingValues(12.dp)) {
                Text(
                    "INSÍGNIAS",
                    style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(9.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    globais.forEach { Insignia(it.icon, it.name, it.color) }
                    deServidor.forEach { b ->
                        val nome = b.serverName?.let { "${b.name} · $it" } ?: b.name
                        Insignia(b.icon, nome, b.color)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!isMe) {
            CartaoInterno(fundo = Obsidian.raised, padding = PaddingValues(12.dp)) {
                Text(
                    "AMIGOS EM COMUM",
                    style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(6.dp))
                if (rostosEmComum.isEmpty()) {
                    Text(
                        if (amigosEmComum == 0) "nenhum ainda" else "$amigosEmComum em comum",
                        style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
                    )
                } else {
                    Spacer(Modifier.height(3.dp))
                    val recuo = 6
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        rostosEmComum.forEachIndexed { i, amigo ->
                            Box(
                                Modifier
                                    .offset(x = (-recuo * i).dp)
                                    .clip(CircleShape)
                                    .background(Obsidian.raised)
                                    .padding(1.5.dp),
                            ) {
                                DesktopAvatar(amigo.avatarUrl, amigo.displayName ?: amigo.username, 26)
                            }
                        }
                        val sobrando = amigosEmComum - rostosEmComum.size
                        if (sobrando > 0) {
                            Text(
                                "+$sobrando",
                                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                                modifier = Modifier
                                    .offset(x = (-recuo * (rostosEmComum.size - 1)).dp)
                                    .padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        CartaoInterno(fundo = Obsidian.raised, padding = PaddingValues(12.dp)) {
            Text(
                if (isMe) "SUAS CONSTELAÇÕES" else "CONSTELAÇÕES EM COMUM",
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            if (constelacoes.isEmpty()) {
                Text(
                    if (isMe) "você ainda não entrou em nenhuma"
                    else "vocês ainda não dividem nenhuma",
                    style = Tipo.descricao,
                )
            } else {
                constelacoes.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Obsidian.overlay),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!s.iconUrl.isNullOrBlank() && !imagemMorreu(s.iconUrl)) {
                                AsyncImage(
                                    model = s.iconUrl,
                                    contentDescription = s.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    onState = { lembrarQueMorreu(s.iconUrl, it) },
                                )
                            } else {
                                Text(
                                    s.name.take(1).uppercase(),
                                    style = TextStyle(color = Obsidian.accent, fontSize = 12.sp, fontFamily = DmSerif),
                                )
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            s.name,
                            style = TextStyle(color = Obsidian.text2, fontSize = 13.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        cargoLegivel(s.role)?.let { rotulo ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                rotulo,
                                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 0.5.sp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (isMe) "o Astra ainda não guarda histórico de atividade."
            else "o Astra ainda não mostra o que $nome anda fazendo.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        )
    }
}

private fun cargoLegivel(role: String): String? = when (role.uppercase()) {
    "OWNER" -> "dono"
    "ADMIN" -> "admin"
    "MODERATOR", "MOD" -> "moderação"
    else -> null
}

@Composable
private fun BarraDeNivel(p: ProgressoDto) {
    val fracao = if (p.paraOProximo > 0) (p.noNivel.toFloat() / p.paraOProximo).coerceIn(0f, 1f) else 0f
    Box(
        Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).drawBehind {
            drawRect(Obsidian.overlay)
            drawRect(Obsidian.accent, size = Size(size.width * fracao, size.height))
        },
    )
}

@Composable
private fun Insignia(icone: String, nome: String, corHex: String?) {
    val cor = corHex?.removePrefix("#")?.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: Obsidian.accent
    Row(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Obsidian.overlay)
            .border(1.dp, cor.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone.isNotBlank()) {
            Text(icone, style = TextStyle(fontSize = 12.sp))
            Spacer(Modifier.width(6.dp))
        }
        Text(nome, style = TextStyle(color = Obsidian.text2, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
