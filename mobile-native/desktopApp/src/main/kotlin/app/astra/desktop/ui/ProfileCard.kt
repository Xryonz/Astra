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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.MemberRoleDto
import app.astra.mobile.core.network.dto.MutualServerDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CardVariante {
    NORMAL,

    COMPLETO,
}

val LARGURA_CARTAO_COMPLETO = 330.dp

val LARGURA_CARTAO_NORMAL = LARGURA_CARTAO_COMPLETO

data class DadosDoCartao(
    val nome: String,
    val username: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val bannerPositionY: Int = 50,
    val bannerScale: Int = 100,
    val pronomes: String? = null,
    val bio: String? = null,
    val statusEmoji: String? = null,
    val recado: String? = null,
    val fonte: String? = null,
    val status: String? = null,
    val criadoEm: String? = null,
    val atividade: String? = null,
    val atividadeDesde: Long = 0L,
    val corDoNome: Color? = null,
)

fun ProfileUserDto.paraCartao() = DadosDoCartao(
    nome = displayName ?: username,
    username = username,
    avatarUrl = avatarUrl,
    bannerUrl = bannerUrl,
    bannerColor = bannerColor,
    bannerPositionY = bannerPositionY ?: 50,
    bannerScale = bannerScale ?: 100,
    pronomes = pronouns,
    bio = bio,
    statusEmoji = statusEmoji,
    recado = customStatus,
    fonte = displayFont,
    status = effectiveStatus,
    criadoEm = createdAt,
)

@Composable
fun ProfileCard(
    dados: DadosDoCartao,
    variante: CardVariante = CardVariante.NORMAL,
    modifier: Modifier = Modifier,
    servidoresEmComum: List<MutualServerDto> = emptyList(),
    amigosEmComum: Int = 0,
    cargos: List<MemberRoleDto> = emptyList(),
    aoFechar: (() -> Unit)? = null,
    acoesNoBanner: (@Composable RowScope.() -> Unit)? = null,
    animar: Boolean = true,
    acoesDaFoto: (() -> List<MenuEntry>)? = null,
    acoesDoBanner: (() -> List<MenuEntry>)? = null,
    rodape: @Composable (() -> Unit)? = null,
) {
    val completo = variante == CardVariante.COMPLETO
    val recuoH = if (completo) 20.dp else 16.dp
    val aspectoBanner = ProfileBannerAspect

    Column(
        modifier
            .clip(RoundedCornerShape(if (completo) 16.dp else 12.dp))
            .profileCardBackdrop(dados.bannerColor, aspectoBanner)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(if (completo) 16.dp else 12.dp)),
    ) {
        Box {
            ProfileBanner(
                css = null,
                imageUrl = dados.bannerUrl,
                positionY = dados.bannerPositionY,
                scale = dados.bannerScale,
                fallback = bannerBackdrop(dados.bannerUrl),
                modifier = Modifier.fillMaxWidth().aspectRatio(aspectoBanner),
            )
            if (completo && animar) {
                BannerSweep(dados.username, Modifier.fillMaxWidth().aspectRatio(aspectoBanner))
            }
            acoesDoBanner?.let { acoes ->
                FotoEditavel(
                    forma = RectangleShape,
                    rotulo = "editar o banner do perfil",
                    glifo = 22.dp,
                    acoes = acoes,
                    modifier = Modifier.fillMaxWidth().aspectRatio(aspectoBanner),
                ) {  }
            }
            if (aoFechar != null || acoesNoBanner != null) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    acoesNoBanner?.invoke(this)
                    if (aoFechar != null) {
                        val fecharSrc = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .clickScale(fecharSrc, formaDoFoco = FormaDeBotao)
                                .clip(FormaDeBotao)
                                .background(Obsidian.void.copy(alpha = 0.5f))
                                .clickable(interactionSource = fecharSrc, indication = null) { aoFechar() },
                            contentAlignment = Alignment.Center,
                        ) {
                            LIcon(Lucide.X, tint = Obsidian.text2, size = 15.dp, rotulo = "Fechar", modifier = Modifier.padding(5.dp))
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = recuoH)) {
            AvatarDoCartao(dados, completo, animar, acoesDaFoto)
            Column(Modifier.offset(y = if (completo) (-24).dp else (-40).dp)) {
                if (completo) CorpoCompleto(dados, servidoresEmComum, animar, rodape)
                else CorpoCompacto(dados, amigosEmComum, servidoresEmComum, cargos, rodape)
                Spacer(Modifier.height(if (completo) 20.dp else 12.dp))
            }
        }
    }
}

@Composable
private fun AvatarDoCartao(
    dados: DadosDoCartao,
    completo: Boolean,
    animar: Boolean,
    acoesDaFoto: (() -> List<MenuEntry>)? = null,
) {
    val px = if (completo) 88 else 64
    val reduzir = LocalReduceMotion.current
    val pop = remember(dados.username, completo) {
        Animatable(if (reduzir || !completo || !animar) 1f else 0f)
    }
    LaunchedEffect(dados.username, completo) {
        if (pop.value < 1f) {
            delay(200)
            pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
        }
    }
    Box(
        Modifier
            .offset(y = if (completo) (-42).dp else (-px / 2 - 4).dp)
            .graphicsLayer {
                alpha = pop.value.coerceIn(0f, 1f)
                val s = 0.6f + 0.4f * pop.value
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(Obsidian.raised)
            .padding(3.dp),
    ) {
        if (acoesDaFoto != null) {
            FotoEditavel(
                forma = CircleShape,
                rotulo = "editar a foto do perfil",
                glifo = if (completo) 20.dp else 16.dp,
                acoes = acoesDaFoto,
                modifier = Modifier.size(px.dp),
            ) { aceso ->
                DesktopAvatar(dados.avatarUrl, dados.nome, px)
            }
        } else {
            DesktopAvatar(dados.avatarUrl, dados.nome, px)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CorpoCompacto(
    dados: DadosDoCartao,
    amigosEmComum: Int,
    servidoresEmComum: List<MutualServerDto>,
    cargos: List<MemberRoleDto>,
    rodape: @Composable (() -> Unit)?,
) {
    NomeELinha(dados, tamanhoNome = 19, tamanhoPonto = 10)

    val vinculos = buildList {
        if (amigosEmComum > 0) {
            add(if (amigosEmComum == 1) "1 amigo em comum" else "$amigosEmComum amigos em comum")
        }
        if (servidoresEmComum.isNotEmpty()) {
            add(
                if (servidoresEmComum.size == 1) "1 constelação em comum"
                else "${servidoresEmComum.size} constelações em comum",
            )
        }
    }
    if (vinculos.isNotEmpty()) {
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LIcon(Lucide.Users, tint = Obsidian.text3, size = 12.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                vinculos.joinToString(" · "),
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (!dados.recado.isNullOrBlank() || !dados.statusEmoji.isNullOrBlank()) {
        Spacer(Modifier.height(9.dp))
        Text(recadoInteiro(dados), style = TextStyle(color = Obsidian.text2, fontSize = 12.sp))
    }
    dados.atividade?.takeIf { it.isNotBlank() }?.let { programa ->
        Spacer(Modifier.height(8.dp))
        val arte = arteDaAtividade(programa, Obsidian.accent)
        var agora by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(programa) {
            while (true) {
                delay(30_000)
                agora = System.currentTimeMillis()
            }
        }
        val decorrido = tempoDeAtividade(dados.atividadeDesde, agora)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(arte.cor.copy(alpha = 0.14f))
                    .border(1.dp, arte.cor.copy(alpha = 0.22f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(arte.glifo, tint = arte.cor, size = 16.dp)
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    programa,
                    style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (decorrido != null) {
                    Text(decorrido, style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
                }
            }
        }
    }
    if (!dados.bio.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Obsidian.void.copy(alpha = 0.38f))
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Text(
                dados.bio.orEmpty(),
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (cargos.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Obsidian.void.copy(alpha = 0.38f))
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Text(
                "CARGOS",
                style = TextStyle(color = Obsidian.text3, fontSize = 9.sp, letterSpacing = 1.sp),
            )
            Spacer(Modifier.height(7.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                cargos.forEach { EtiquetaDeCargo(it) }
            }
        }
    }

    membroDesde(dados.criadoEm)?.let { desde ->
        Spacer(Modifier.height(9.dp))
        Text(
            "nas estrelas desde $desde",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            maxLines = 1,
        )
    }

    if (rodape != null) {
        Spacer(Modifier.height(12.dp))
        rodape()
    }
}

@Composable
private fun EtiquetaDeCargo(cargo: MemberRoleDto) {
    val cor = memberRoleColor(cargo.color) ?: Obsidian.text3
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(5.dp))
        Text(
            cargo.name,
            style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CorpoCompleto(
    dados: DadosDoCartao,
    servidoresEmComum: List<MutualServerDto>,
    animar: Boolean,
    rodape: @Composable (() -> Unit)?,
) {
    val chave = dados.username
    val cascata: @Composable (Int, @Composable () -> Unit) -> Unit = { i, conteudo ->
        if (animar) CascadeIn(i, chave, stepMs = 40L, startDelayMs = 220L) { conteudo() } else conteudo()
    }

    cascata(0) { NomeELinha(dados, tamanhoNome = 24, tamanhoPonto = 12, so = Parte.NOME) }
    cascata(1) { NomeELinha(dados, tamanhoNome = 24, tamanhoPonto = 12, so = Parte.ARROBA) }
    if (!dados.recado.isNullOrBlank() || !dados.statusEmoji.isNullOrBlank()) {
        Spacer(Modifier.height(12.dp))
        cascata(2) {
            Text(recadoInteiro(dados), style = TextStyle(color = Obsidian.text2, fontSize = 13.sp))
        }
    }
    if (!dados.bio.isNullOrBlank()) {
        Spacer(Modifier.height(14.dp))
        cascata(3) {
            Secao("sobre") {
                Text(dados.bio.orEmpty(), style = TextStyle(color = Obsidian.text2, fontSize = 13.sp, lineHeight = 19.sp))
            }
        }
    }
    membroDesde(dados.criadoEm)?.let { desde ->
        Spacer(Modifier.height(14.dp))
        cascata(4) {
            Secao("membro") {
                Text("nas estrelas desde $desde", style = TextStyle(color = Obsidian.text2, fontSize = 13.sp))
            }
        }
    }
    if (servidoresEmComum.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        cascata(5) {
            Secao("servidores em comum · ${servidoresEmComum.size}") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    servidoresEmComum.forEachIndexed { i, s -> MutualChip(s, if (animar) i else CHIP_STAGGER_MAX) }
                }
            }
        }
    }
    if (rodape != null) {
        Spacer(Modifier.height(18.dp))
        cascata(6) { rodape() }
    }
}

private enum class Parte { TUDO, NOME, ARROBA }

@Composable
private fun NomeELinha(
    dados: DadosDoCartao,
    tamanhoNome: Int,
    tamanhoPonto: Int,
    so: Parte = Parte.TUDO,
) {
    if (so != Parte.ARROBA) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dados.nome,
                style = TextStyle(
                    color = dados.corDoNome ?: Obsidian.text1,
                    fontSize = tamanhoNome.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = profileFontFamily(dados.fonte),
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            StatusDot(
                status = userStatus(dados.status),
                size = tamanhoPonto.dp,
                cutoutColor = Obsidian.raised,
            )
        }
    }
    if (so != Parte.NOME) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "@${dados.username}",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
            )
            if (!dados.pronomes.isNullOrBlank()) {
                Text("  ·  ${dados.pronomes}", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
            }
        }
    }
}

private fun recadoInteiro(dados: DadosDoCartao) = listOfNotNull(
    dados.statusEmoji?.ifBlank { null },
    dados.recado?.ifBlank { null },
).joinToString(" ")

@Composable
private fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    CartaoInterno(fundo = Obsidian.hover, padding = PaddingValues(horizontal = 11.dp, vertical = 9.dp)) {
        Text(
            titulo.uppercase(),
            style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(6.dp))
        conteudo()
    }
}

private const val CHIP_STAGGER_MAX = 10

@Composable
private fun MutualChip(s: MutualServerDto, index: Int) {
    val reduce = LocalReduceMotion.current
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

private fun membroDesde(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val date = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
        date.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR")))
    }.getOrNull()
}
