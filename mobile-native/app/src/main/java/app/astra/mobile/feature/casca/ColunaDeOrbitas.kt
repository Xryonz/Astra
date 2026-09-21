package app.astra.mobile.feature.casca

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessagesSquare
import com.composables.icons.lucide.Plus

private val LADO = 46.dp
private val CANTO = RoundedCornerShape(10.dp)

@Composable
fun ColunaDeOrbitas(
    orbitas: List<Server>,
    orbitaAberta: String?,
    emSussurros: Boolean,
    naoLidas: Set<String>,
    silenciadas: Set<String>,
    aoAbrirSussurros: () -> Unit,
    aoAbrirOrbita: (String) -> Unit,
    aoSegurarOrbita: (String) -> Unit,
    aoAdicionar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .width(68.dp)
            .fillMaxHeight()
            .background(astraColors.void),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
    ) {
        item {
            Peca(
                selecionada = emSussurros,
                forma = CircleShape,
                rotulo = "Sussurros",
                aoTocar = aoAbrirSussurros,
            ) {
                Icon(
                    Lucide.MessagesSquare,
                    contentDescription = null,
                    tint = if (emSussurros) astraColors.textInv else astraColors.text2,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        item {
            Box(
                Modifier
                    .padding(vertical = 2.dp)
                    .width(24.dp)
                    .height(1.dp)
                    .background(astraColors.border),
            )
        }

        items(orbitas, key = { it.id }) { orbita ->
            Peca(
                selecionada = orbita.id == orbitaAberta && !emSussurros,
                forma = CANTO,
                rotulo = orbita.name,
                naoLida = orbita.id in naoLidas && orbita.id !in silenciadas,
                apagada = orbita.id in silenciadas,
                aoTocar = { aoAbrirOrbita(orbita.id) },
                aoSegurar = { aoSegurarOrbita(orbita.id) },
            ) {
                var semImagem by remember(orbita.iconUrl) { mutableStateOf(orbita.iconUrl == null) }
                if (!semImagem) {
                    AsyncImage(
                        model = orbita.iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { semImagem = true },
                        modifier = Modifier.size(LADO).clip(CANTO),
                    )
                } else {
                    Text(
                        text = orbita.name.take(2).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = astraColors.text2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item {
            Peca(
                selecionada = false,
                forma = CANTO,
                rotulo = "Nova órbita",
                aoTocar = aoAdicionar,
            ) {
                Icon(
                    Lucide.Plus,
                    contentDescription = null,
                    tint = astraColors.text2,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun Peca(
    selecionada: Boolean,
    forma: RoundedCornerShape,
    rotulo: String,
    aoTocar: () -> Unit,
    naoLida: Boolean = false,
    apagada: Boolean = false,
    aoSegurar: (() -> Unit)? = null,
    conteudo: @Composable () -> Unit,
) {
    val marca by animateDpAsState(
        targetValue = if (selecionada) 22.dp else if (naoLida) 8.dp else 0.dp,
        animationSpec = tween(160),
        label = "marca",
    )
    Box(contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .padding(start = 2.dp)
                .width(3.dp)
                .height(marca)
                .clip(RoundedCornerShape(2.dp))
                .background(astraColors.accent),
        )
        Box(
            modifier = Modifier
                .padding(start = 11.dp)
                .size(LADO)
                .alpha(if (apagada) 0.45f else 1f)
                .clip(forma)
                .background(if (selecionada) astraColors.accent else astraColors.raised)
                .border(1.dp, if (selecionada) astraColors.accent else astraColors.border, forma)
                .combinedClickable(onClick = aoTocar, onLongClick = aoSegurar)
                .semantics { contentDescription = rotulo },
            contentAlignment = Alignment.Center,
        ) {
            conteudo()
        }
    }
}
