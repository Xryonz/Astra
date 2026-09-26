package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.astra.mobile.feature.profile.domain.model.MutualServer
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.BadgeChips
import app.astra.mobile.ui.components.BadgeUi
import app.astra.mobile.ui.components.StatusDot
import app.astra.mobile.ui.components.displayFontFamily
import app.astra.mobile.ui.components.parseGradientBrush
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PerfilVisivel(
    val nome: String,
    val usuario: String,
    val avatar: String?,
    val banner: String? = null,
    val corDoBanner: String? = null,
    val bannerY: Int = 50,
    val bannerEscala: Int = 100,
    val fonte: String? = null,
    val pronomes: String? = null,
    val recado: String? = null,
    val bio: String? = null,
    val tema: String? = null,
    val status: UserStatus? = null,
    val emblemas: List<BadgeUi> = emptyList(),
)

private val LADO_DA_FOTO = 88.dp
private val ANEL = 6.dp

fun String?.comoCor(): Color? {
    val limpo = this?.trim()?.removePrefix("#") ?: return null
    if (limpo.length != 6) return null
    return runCatching { Color("FF$limpo".toLong(16)) }.getOrNull()
}

@Composable
fun FundoDoTema(tema: String?, modifier: Modifier = Modifier, conteudo: @Composable BoxScope.() -> Unit) {
    val pincel = remember(tema) { parseGradientBrush(tema) }
    Box(modifier) {
        if (pincel != null) {
            Box(Modifier.matchParentSize().background(pincel))
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)))
        }
        conteudo()
    }
}

@Composable
fun BannerDoPerfil(p: PerfilVisivel, altura: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(altura)
            .clipToBounds()
            .background(p.corDoBanner.comoCor() ?: astraColors.overlay),
    ) {
        if (!p.banner.isNullOrBlank()) {
            AsyncImage(
                model = p.banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, (p.bannerY / 50f - 1f).coerceIn(-1f, 1f)),
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = p.bannerEscala / 100f
                        scaleY = p.bannerEscala / 100f
                    },
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.38f)),
            ),
        )
    }
}

@Composable
fun CabecaDoPerfil(
    p: PerfilVisivel,
    alturaDoBanner: Dp = 120.dp,
    fundoDoAnel: Color = astraColors.void,
    textoSemRecado: String? = null,
    modificadorDoBanner: Modifier = Modifier,
    aoTocarNaFoto: (() -> Unit)? = null,
    rotuloDoToqueNaFoto: String? = null,
    aoTocarNoRecado: (() -> Unit)? = null,
    sobreOBanner: @Composable BoxScope.() -> Unit = {},
    presoAFoto: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(alturaDoBanner + LADO_DA_FOTO / 2)) {
            BannerDoPerfil(p, alturaDoBanner, modificadorDoBanner)
            sobreOBanner()
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .size(LADO_DA_FOTO + ANEL * 2)
                        .background(fundoDoAnel, CircleShape)
                        .then(
                            if (aoTocarNaFoto != null) {
                                Modifier
                                    .semantics { contentDescription = "Foto de ${p.nome}" }
                                    .clickable(onClickLabel = rotuloDoToqueNaFoto, onClick = aoTocarNaFoto)
                            } else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AstraAvatar(p.avatar, p.nome, size = LADO_DA_FOTO.value.toInt())
                    p.status?.let { status ->
                        StatusDot(
                            status = status,
                            size = 26.dp,
                            bordered = true,
                            borderColor = fundoDoAnel,
                            cutoutColor = fundoDoAnel,
                            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-4).dp),
                        )
                    }
                    presoAFoto()
                }
                val recado = p.recado?.takeIf { it.isNotBlank() }
                if (recado != null || textoSemRecado != null) {
                    BalaoDoRecado(
                        texto = recado ?: textoSemRecado.orEmpty(),
                        vazio = recado == null,
                        aoTocar = aoTocarNoRecado,
                        modifier = Modifier.padding(bottom = LADO_DA_FOTO / 2 + 6.dp).weight(1f, fill = false),
                    )
                }
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = p.nome,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = p.fonte?.let(::displayFontFamily),
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append("@${p.usuario}")
                    p.pronomes?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (p.emblemas.isNotEmpty()) BadgeChips(p.emblemas, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun BalaoDoRecado(texto: String, vazio: Boolean, aoTocar: (() -> Unit)?, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(18.dp)
    Row(modifier.padding(start = 2.dp), verticalAlignment = Alignment.Bottom) {
        Column(horizontalAlignment = Alignment.End) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(astraColors.raised)
                    .border(1.dp, astraColors.border, CircleShape),
            )
            Spacer(Modifier.height(2.dp))
        }
        Box(
            Modifier
                .padding(bottom = 8.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(astraColors.raised)
                .border(1.dp, astraColors.border, CircleShape),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (vazio) FontStyle.Italic else FontStyle.Normal,
            color = if (vazio) astraColors.text3 else astraColors.text1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(bottom = 14.dp)
                .clip(forma)
                .background(astraColors.raised)
                .border(1.dp, astraColors.border, forma)
                .then(if (aoTocar != null) Modifier.clickable(onClick = aoTocar) else Modifier)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
fun FaixaDoPerfil(p: PerfilVisivel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AstraAvatar(p.avatar, p.nome, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = p.nome,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = p.fonte?.let(::displayFontFamily),
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val linha = p.recado?.takeIf { it.isNotBlank() } ?: "@${p.usuario}"
            Text(
                text = linha,
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun BotaoFechar(aoFechar: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(astraColors.void.copy(alpha = 0.6f))
            .clickable(onClick = aoFechar)
            .semantics { contentDescription = "Fechar" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.X, contentDescription = null, tint = astraColors.text1, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun IconeDaOrbita(orbita: MutualServer, aoTocar: () -> Unit) {
    val forma = RoundedCornerShape(10.dp)
    var semImagem by remember(orbita.iconUrl) { mutableStateOf(orbita.iconUrl == null) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(forma)
            .background(astraColors.overlay)
            .border(1.dp, astraColors.border, forma)
            .clickable(onClick = aoTocar)
            .semantics { contentDescription = "Abrir ${orbita.name}" },
        contentAlignment = Alignment.Center,
    ) {
        if (!semImagem) {
            AsyncImage(
                model = orbita.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { semImagem = true },
                modifier = Modifier.size(40.dp),
            )
        } else {
            Text(
                orbita.name.take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = astraColors.text2,
            )
        }
    }
}

fun mesPorExtenso(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(MES_POR_EXTENSO)
    }.getOrNull()
}

private val MES_POR_EXTENSO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))

@Composable
fun CartaoDeSecao(
    titulo: String?,
    modifier: Modifier = Modifier,
    aoTocar: (() -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, forma)
            .then(if (aoTocar != null) Modifier.clickable(onClick = aoTocar) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (titulo != null) {
            Text(titulo, style = MaterialTheme.typography.labelLarge, color = astraColors.text2)
        }
        conteudo()
    }
}
