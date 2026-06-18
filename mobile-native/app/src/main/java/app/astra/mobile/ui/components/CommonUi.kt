package app.astra.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.astraColors
import coil.compose.AsyncImage

/** Avatar circular com ring sutil; fallback = inicial em prata. */
@Composable
fun AstraAvatar(url: String?, name: String, modifier: Modifier = Modifier, size: Int = 46) {
    val mod = modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(astraColors.raised)
        .border(1.dp, astraColors.borderMid, CircleShape)
    if (!url.isNullOrBlank()) {
        AsyncImage(model = url, contentDescription = null, modifier = mod, contentScale = ContentScale.Crop)
    } else {
        Box(mod, contentAlignment = Alignment.Center) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.accent,
            )
        }
    }
}

/** Estado vazio editorial: estrela esmaecida + linha serif + dica marginalia. */
@Composable
fun EmptyState(line: String, hint: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✦", color = astraColors.borderBright, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                text = line,
                style = MaterialTheme.typography.titleLarge,
                color = astraColors.text2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            MarginaliaLabel(hint)
        }
    }
}

/** Spinner cosmico (accent prata). */
@Composable
fun CosmicSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier, color = astraColors.accent, strokeWidth = 2.dp)
}
