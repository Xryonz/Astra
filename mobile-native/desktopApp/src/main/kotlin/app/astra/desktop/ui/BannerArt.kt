package app.astra.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// O bannerColor do Astra guarda uma string CSS ("linear-gradient(135deg,#a,#b)")
// — foi o web que definiu o formato e o mobile seguiu. O desktop ate agora so
// tentava ler como hex (toLongOrNull(16)), entao TODO gradiente virava cinza
// liso aqui. Este arquivo traduz CSS -> Brush do Compose pra o banner ficar
// igual nos tres clientes. Aceita tambem hex puro ("#0f0c29"), que e o que
// contas antigas podem ter.

data class BannerGradient(val id: String, val label: String, val css: String)

// Mesma lista (e mesma ordem) de apps/web CustomizationSection.tsx — o que voce
// escolhe num cliente aparece igual no outro.
val BANNER_GRADIENTS = listOf(
    BannerGradient("sunrise", "Amanhecer", "linear-gradient(135deg,#ff6b9d,#ff9874,#ffd6a5)"),
    BannerGradient("coral", "Coral", "linear-gradient(135deg,#ff7e5f,#feb47b)"),
    BannerGradient("petal", "Petala", "linear-gradient(135deg,#ffafbd,#ffc3a0)"),
    BannerGradient("sunset", "Por-do-sol", "linear-gradient(135deg,#fc4a1a,#f7b733)"),
    BannerGradient("ember", "Brasa", "linear-gradient(135deg,#ff5722,#ff9800,#ffc107)"),
    BannerGradient("magma", "Magma", "linear-gradient(135deg,#f12711,#f5af19)"),
    BannerGradient("amber", "Ambar", "linear-gradient(135deg,#d97706,#facc15)"),
    BannerGradient("saffron", "Acafrao", "linear-gradient(135deg,#ee9b00,#ca6702)"),
    BannerGradient("mint", "Menta", "linear-gradient(135deg,#11998e,#38ef7d)"),
    BannerGradient("willow", "Salgueiro", "linear-gradient(135deg,#7a9e7e,#c8d5b9)"),
    BannerGradient("moss", "Musgo", "linear-gradient(135deg,#5a7140,#a1aa6d)"),
    BannerGradient("forest", "Floresta", "linear-gradient(135deg,#134e5e,#71b280)"),
    BannerGradient("lagoon", "Lagoa", "linear-gradient(135deg,#43cea2,#185a9d)"),
    BannerGradient("ocean", "Oceano", "linear-gradient(135deg,#2193b0,#6dd5ed)"),
    BannerGradient("arctic", "Artico", "linear-gradient(135deg,#a1c4fd,#c2e9fb)"),
    BannerGradient("mist", "Bruma", "linear-gradient(135deg,#bdc3c7,#2c3e50)"),
    BannerGradient("cyber", "Cyber", "linear-gradient(135deg,#6e57e0,#4fc3f7,#00d4ff)"),
    BannerGradient("twilight", "Crepusculo", "linear-gradient(135deg,#3a1c71,#4a00e0)"),
    BannerGradient("plasma", "Plasma", "linear-gradient(135deg,#8e2de2,#4a00e0,#f12711)"),
    BannerGradient("aurora", "Aurora", "linear-gradient(135deg,#3a1c71,#d76d77,#ffaf7b)"),
    BannerGradient("lavender", "Lavanda", "linear-gradient(135deg,#8e44ad,#c39bd3)"),
    BannerGradient("velvet", "Veludo", "linear-gradient(135deg,#41295a,#2f0743)"),
    BannerGradient("neon", "Neon", "linear-gradient(135deg,#ff00cc,#333399)"),
    BannerGradient("wine", "Vinho", "linear-gradient(135deg,#6e0d25,#bd5734)"),
    BannerGradient("burgundy", "Borgonha", "linear-gradient(135deg,#600000,#9c1f1f)"),
    BannerGradient("galaxy", "Galaxia", "linear-gradient(135deg,#0f0c29,#302b63,#24243e)"),
    BannerGradient("obsidian", "Obsidiana", "linear-gradient(135deg,#000000,#1a4d2e)"),
    BannerGradient("ink", "Tinta", "linear-gradient(135deg,#000000,#0f3460)"),
    BannerGradient("charcoal", "Carvao", "linear-gradient(135deg,#232526,#414345)"),
    BannerGradient("onyx", "Onix", "linear-gradient(135deg,#0c0c0c,#3c3c3c)"),
)

private fun parseHex(raw: String): Color? {
    val h = raw.trim().removePrefix("#")
    val v = h.toLongOrNull(16) ?: return null
    return when (h.length) {
        6 -> Color(0xFF000000 or v)
        8 -> Color(v) // AARRGGBB
        3 -> { // #abc -> #aabbcc
            val r = ((v shr 8) and 0xF); val g = ((v shr 4) and 0xF); val b = v and 0xF
            Color(0xFF000000 or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17))
        }
        else -> null
    }
}

// Converte o CSS guardado no perfil num Brush. `size` e o tamanho em px da area
// pintada: o angulo do CSS precisa dele pra virar um par de pontos start/end.
// Formatos aceitos: "linear-gradient(<n>deg,#a,#b,...)", "#rrggbb" e nulo.
fun bannerBrush(css: String?, width: Float, height: Float, fallback: Color): Brush {
    val raw = css?.trim().orEmpty()
    if (raw.isEmpty()) return SolidColor(fallback)
    if (!raw.startsWith("linear-gradient")) return SolidColor(parseHex(raw) ?: fallback)

    val inner = raw.substringAfter('(').substringBeforeLast(')')
    val parts = inner.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return SolidColor(fallback)

    // 1o item pode ser o angulo ("135deg"); sem ele, o CSS assume 180deg.
    val hasAngle = parts[0].endsWith("deg")
    val deg = if (hasAngle) parts[0].removeSuffix("deg").trim().toFloatOrNull() ?: 180f else 180f
    val colors = parts.drop(if (hasAngle) 1 else 0).mapNotNull { parseHex(it.substringBefore(' ')) }
    if (colors.isEmpty()) return SolidColor(fallback)
    if (colors.size == 1) return SolidColor(colors[0])

    // No CSS 0deg aponta pra CIMA e cresce em sentido horario. O eixo Y do
    // Compose cresce pra BAIXO, entao o vetor fica (sin, -cos). O comprimento
    // usa a projecao da diagonal pra a linha cobrir a caixa inteira (como o CSS).
    val rad = Math.toRadians(deg.toDouble())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val len = (abs(width * dx) + abs(height * dy)) / 2f
    val cx = width / 2f
    val cy = height / 2f
    return Brush.linearGradient(
        colors = colors,
        start = Offset(cx - dx * len, cy - dy * len),
        end = Offset(cx + dx * len, cy + dy * len),
    )
}

// Banner do perfil: gradiente/cor por baixo e, se houver imagem, ela por cima
// com o enquadramento salvo. positionY 0..100 (0=topo, 50=centro, 100=base) e
// bannerScale em porcento (100 = cobre a caixa). Mesma semantica do web.
@Composable
fun ProfileBanner(
    css: String?,
    imageUrl: String?,
    positionY: Int,
    scale: Int,
    fallback: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clipToBounds()
            .drawBehind { drawRect(bannerBrush(css, size.width, size.height, fallback)) },
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // bias -1 = topo, 0 = centro, +1 = base.
                alignment = BiasAlignment(0f, (positionY.coerceIn(0, 100) / 50f) - 1f),
                modifier = Modifier.fillMaxSize().scale((scale.coerceIn(100, 300)) / 100f),
            )
        }
    }
}
