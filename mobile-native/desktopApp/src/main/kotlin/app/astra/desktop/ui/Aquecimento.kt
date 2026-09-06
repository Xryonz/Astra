package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Cinzel
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSans
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.GreatVibes
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import com.composables.icons.lucide.BellRing
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val QUADROS = 3
private const val OPACIDADE = 0.004f

private const val AMOSTRA = "Astra 0123 — constelação, órbita e sussurro"

private val FAMILIAS = listOf(DmSans, DmSerif, DmMono, GreatVibes, Cinzel)

private val TELAS_PESADAS = listOf(
    "app.astra.desktop.ui.SettingsScreenKt",
    "app.astra.desktop.ui.ServerSettingsScreenKt",
    "app.astra.desktop.ui.ServerRolesTabKt",
    "app.astra.desktop.ui.ProfileCardKt",
    "app.astra.desktop.ui.VoiceViewKt",
    "app.astra.desktop.ui.FriendsViewKt",
    "app.astra.desktop.ui.DiscoverViewKt",
    "app.astra.desktop.ui.EmojiPickerKt",
    "app.astra.desktop.ui.GifPickerKt",
    "app.astra.desktop.ui.NotifPanelKt",
    "app.astra.desktop.ui.CommandPaletteKt",
    "app.astra.desktop.ui.ImageCropKt",
)

private const val PARTE_DO_VOCABULARIO = 0.3f

@Composable
fun Aquecimento(aoAvancar: (Float) -> Unit = {}, aoTerminar: () -> Unit = {}) {
    var fase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        fase = 1
        repeat(QUADROS) { withFrameNanos { } }
        aoAvancar(PARTE_DO_VOCABULARIO)
        fase = 2
        val carregador = Obsidian::class.java.classLoader
        TELAS_PESADAS.forEachIndexed { i, nome ->
            withContext(Dispatchers.IO) {
                runCatching { Class.forName(nome, false, carregador) }
            }
            val feito = (i + 1f) / TELAS_PESADAS.size
            aoAvancar(PARTE_DO_VOCABULARIO + (1f - PARTE_DO_VOCABULARIO) * feito)
        }
        aoTerminar()
    }
    if (fase != 1) return
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = OPACIDADE }) {
        Column(Modifier.padding(4.dp)) {
            FAMILIAS.forEach { familia ->
                Text(AMOSTRA, style = TextStyle(fontFamily = familia, fontSize = 13.sp))
                Text(
                    AMOSTRA,
                    style = TextStyle(
                        fontFamily = familia,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Text(
                AMOSTRA,
                style = TextStyle(
                    fontFamily = DmSerif,
                    fontSize = 15.sp,
                    fontStyle = FontStyle.Italic,
                ),
            )
            Row {
                Superficies()
                Gradientes()
                Icones()
            }
        }
    }
}

@Composable
private fun Superficies() {
    val rampa = listOf(
        Obsidian.void, Obsidian.base, Obsidian.raised,
        Obsidian.overlay, Obsidian.hover, Obsidian.active,
    )
    Row {
        rampa.forEachIndexed { i, cor ->
            val forma = RoundedCornerShape((6 + i * 2).dp)
            Box(
                Modifier
                    .size(22.dp)
                    .clip(forma)
                    .background(cor)
                    .border(1.dp, Obsidian.borderDim, forma),
            )
        }
    }
}

@Composable
private fun Gradientes() {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(listOf(Obsidian.accent, Obsidian.accentDim, Obsidian.void)),
            ),
    )
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.radialGradient(listOf(Obsidian.accentDim, Obsidian.void)),
            ),
    )
}

@Composable
private fun Icones() {
    Row {
        listOf(Lucide.Search, Lucide.Settings, Lucide.Users, Lucide.BellRing, Lucide.Mic, Lucide.Check, Lucide.X)
            .forEach { LIcon(it, size = 16.dp) }
    }
}

