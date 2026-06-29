package app.astra.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.astra.mobile.BuildConfig
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.model.isAudio
import app.astra.mobile.core.model.isImage
import app.astra.mobile.ui.theme.astraColors
import coil.compose.AsyncImage

@Composable
fun MessageAttachments(
    attachments: List<Attachment>,
    maxWidth: Dp,
    onOpenImage: (List<Attachment>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    val images = remember(attachments) { attachments.filter { it.isImage } }
    val files = remember(attachments) { attachments.filter { !it.isImage && !it.isAudio } }

    Column(modifier.width(maxWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (images.isNotEmpty()) ImageGrid(images, onOpen = { idx -> onOpenImage(images, idx) })
        files.forEach { FileChip(it) }
    }
}

private val tileShape = RoundedCornerShape(14.dp)

@Composable
private fun ImageGrid(images: List<Attachment>, onOpen: (Int) -> Unit) {
    if (images.size == 1) {
        val a = images[0]
        val ar = if (a.width != null && a.height != null && a.height > 0) {
            (a.width.toFloat() / a.height).coerceIn(0.6f, 2.2f)
        } else 1.4f
        ImageTile(a, Modifier.aspectRatio(ar)) { onOpen(0) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        images.chunked(2).forEachIndexed { rowIdx, rowImgs ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowImgs.forEachIndexed { colIdx, img ->
                    ImageTile(img, Modifier.weight(1f).aspectRatio(1f)) { onOpen(rowIdx * 2 + colIdx) }
                }
                if (rowImgs.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ImageTile(att: Attachment, modifier: Modifier, onOpen: () -> Unit) {
    Box(
        modifier
            .clip(tileShape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.borderMid, tileShape)
            .clickable(onClick = onOpen),
    ) {
        AsyncImage(
            model = att.url,
            contentDescription = att.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun FileChip(att: Attachment) {
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(12.dp)
    val open = {
        val url = att.url.let { if (it.startsWith("/")) BuildConfig.BASE_URL.trimEnd('/') + it else it }
        runCatching { uriHandler.openUri(url) }
        Unit
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, shape)
            .clickable(onClick = open)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(astraColors.base),
            contentAlignment = Alignment.Center,
        ) {
            Text("📎", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = att.name ?: "arquivo",
                style = MaterialTheme.typography.bodyMedium,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = fmtBytes(att.size),
                style = MaterialTheme.typography.labelSmall,
                color = astraColors.text3,
            )
        }
    }
}

private fun fmtBytes(b: Long?): String {
    if (b == null) return ""
    return when {
        b < 1024 -> "${b}B"
        b < 1024 * 1024 -> "${b / 1024}KB"
        else -> "%.1fMB".format(b / 1024f / 1024f)
    }
}
