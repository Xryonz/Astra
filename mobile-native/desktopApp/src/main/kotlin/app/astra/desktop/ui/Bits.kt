package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import coil3.compose.AsyncImage

// Avatar circular com fallback de inicial — usado no shell e no chat.
@Composable
fun DesktopAvatar(url: String?, name: String, sizeDp: Int) {
    Box(
        modifier = Modifier.size(sizeDp.dp).clip(CircleShape).background(Obsidian.overlay),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            BasicText(
                text = name.take(1).uppercase(),
                style = TextStyle(color = Obsidian.accent, fontSize = (sizeDp * 0.42f).sp),
            )
        }
    }
}
