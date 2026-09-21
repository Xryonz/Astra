package app.astra.mobile.feature.casca

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.astra.mobile.feature.home.ActiveVoiceRoom
import app.astra.mobile.feature.server.domain.model.Channel
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.Viagem
import app.astra.mobile.ui.components.viajante
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Volume2

@Composable
fun PainelDeCanais(
    orbita: Server,
    canalAberto: String?,
    naoLidos: Set<String>,
    vozAtiva: List<ActiveVoiceRoom>,
    aoAbrirCanal: (Channel) -> Unit,
    aoEntrarNaVoz: (Channel) -> Unit,
    aoBuscar: () -> Unit,
    aoAbrirAjustes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val porCategoria = remember(orbita) {
        val avulsos = orbita.channels.filter { canal -> orbita.categories.none { it.id == canal.categoryId } }
        val grupos = orbita.categories.sortedBy { it.position }
            .map { categoria -> categoria.name to orbita.channels.filter { it.categoryId == categoria.id } }
        buildList {
            if (avulsos.isNotEmpty()) add(null to avulsos)
            addAll(grupos.filter { it.second.isNotEmpty() })
        }
    }

    LazyColumn(modifier.fillMaxSize()) {
        item(key = "capa") {
            Capa(orbita = orbita, aoBuscar = aoBuscar, aoAbrirAjustes = aoAbrirAjustes)
        }

        porCategoria.forEach { (categoria, canais) ->
            if (categoria != null) {
                item(key = "cat-$categoria") {
                    MarginaliaLabel(categoria, Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
                }
            }
            items(canais, key = { it.id }) { canal ->
                LinhaDeCanal(
                    canal = canal,
                    aberto = canal.id == canalAberto,
                    naoLido = canal.id in naoLidos,
                    naVoz = vozAtiva.firstOrNull { it.channelId == canal.id }?.count ?: 0,
                    aoTocar = { if (canal.isVoice) aoEntrarNaVoz(canal) else aoAbrirCanal(canal) },
                )
            }
        }

        item(key = "respiro") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun Capa(orbita: Server, aoBuscar: () -> Unit, aoAbrirAjustes: () -> Unit) {
    Column {
        if (orbita.bannerUrl != null) {
            AsyncImage(
                model = orbita.bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = orbita.name,
                fontFamily = DmSerif,
                style = MaterialTheme.typography.headlineSmall,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BotaoRedondo(icone = Lucide.Search, rotulo = "Buscar", aoTocar = aoBuscar)
            Spacer(Modifier.width(8.dp))
            BotaoRedondo(icone = Lucide.Settings, rotulo = "Ajustes da órbita", aoTocar = aoAbrirAjustes)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun BotaoRedondo(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    rotulo: String,
    aoTocar: () -> Unit,
    marca: Int = 0,
) {
    Box {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(astraColors.raised)
                .border(1.dp, astraColors.border, CircleShape)
                .clickable(onClick = aoTocar)
                .semantics { contentDescription = if (marca > 0) "$rotulo, $marca novos" else rotulo },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icone, contentDescription = null, tint = astraColors.text2, modifier = Modifier.size(17.dp))
        }
        if (marca > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(astraColors.accent)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                ContadorQueMuda(if (marca > 9) "9+" else "$marca")
            }
        }
    }
}

@Composable
internal fun ContadorQueMuda(texto: String) {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    AnimatedContent(
        targetState = texto,
        transitionSpec = {
            if (semMovimento) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else {
                (slideInVertically(tween(220, easing = EaseOutSoft)) { it } + fadeIn(tween(180))) togetherWith
                    (slideOutVertically(tween(180, easing = EaseOutSoft)) { -it } + fadeOut(tween(140)))
            }
        },
        label = "contador",
    ) { valor ->
        Text(valor, style = MaterialTheme.typography.labelSmall, color = astraColors.textInv)
    }
}

@Composable
private fun LinhaDeCanal(
    canal: Channel,
    aberto: Boolean,
    naoLido: Boolean,
    naVoz: Int,
    aoTocar: () -> Unit,
) {
    val forma = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(forma)
            .background(if (aberto) astraColors.raised else Color.Transparent)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (canal.isVoice) Lucide.Volume2 else Lucide.Hash,
            contentDescription = null,
            tint = if (naoLido || aberto) astraColors.text1 else astraColors.text3,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            Text(
                text = canal.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (naoLido || aberto) astraColors.text1 else astraColors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (canal.isVoice) Modifier else Modifier.viajante(Viagem.nomeDoCanal(canal.id), ehTexto = true),
            )
        }
        if (naVoz > 0) MarginaliaLabel("$naVoz")
        if (naoLido && !aberto) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(astraColors.accent))
        }
    }
}
