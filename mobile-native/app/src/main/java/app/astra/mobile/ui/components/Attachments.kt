package app.astra.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.astra.mobile.BuildConfig
import app.astra.mobile.core.model.Attachment
import app.astra.mobile.core.model.isAudio
import app.astra.mobile.core.model.isImage
import app.astra.mobile.core.model.isVideo
import app.astra.mobile.core.upload.AndamentoDoVideo
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play

@Composable
fun MessageAttachments(
    attachments: List<Attachment>,
    maxWidth: Dp,
    onOpenImage: (List<Attachment>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    val figurinhas = remember(attachments) { attachments.filter { it.figurinha } }
    val images = remember(attachments) { attachments.filter { it.isImage && !it.figurinha } }
    val videos = remember(attachments) { attachments.filter { it.isVideo && !it.figurinha } }
    val files = remember(attachments) { attachments.filter { !it.isImage && !it.isAudio && !it.isVideo && !it.figurinha } }

    Column(modifier.width(maxWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        figurinhas.forEach { Figurinha(it) }
        if (images.isNotEmpty()) ImageGrid(images, onOpen = { idx -> onOpenImage(images, idx) })
        videos.forEach { VideoNaConversa(it) }
        files.forEach { FileChip(it) }
    }
}

private val LADO_DA_FIGURINHA = 150.dp

@Composable
private fun Figurinha(att: Attachment) {
    val proporcao = if (att.width != null && att.height != null && att.height > 0) {
        att.width.toFloat() / att.height
    } else null
    AsyncImage(
        model = att.url,
        contentDescription = att.name,
        contentScale = ContentScale.Fit,
        modifier = if (proporcao != null) {
            Modifier.sizeIn(maxWidth = LADO_DA_FIGURINHA, maxHeight = LADO_DA_FIGURINHA).aspectRatio(proporcao)
        } else {
            Modifier.size(LADO_DA_FIGURINHA)
        },
    )
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
                .background(astraColors.overlay),
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

@Composable
fun PendingAttachmentsBar(
    attachments: List<Attachment>,
    onRemove: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    videoEmPreparo: AndamentoDoVideo? = null,
    aoCancelarVideo: (Int) -> Unit = {},
) {
    if (attachments.isEmpty() && videoEmPreparo == null) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { att ->
            Box(Modifier.size(62.dp)) {
                val thumbShape = RoundedCornerShape(10.dp)
                if (att.isImage) {
                    AsyncImage(
                        model = att.url,
                        contentDescription = att.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(thumbShape)
                            .background(astraColors.raised)
                            .border(1.dp, astraColors.borderMid, thumbShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(thumbShape)
                            .background(astraColors.raised)
                            .border(1.dp, astraColors.borderMid, thumbShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (att.isVideo) {
                            Icon(Lucide.Play, contentDescription = att.name, tint = astraColors.text2, modifier = Modifier.size(22.dp))
                        } else {
                            Text("📎", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                BotaoDeTirar("Remover anexo", Modifier.align(Alignment.TopEnd)) { onRemove(att) }
            }
        }
        if (videoEmPreparo != null) VideoEmPreparo(videoEmPreparo, aoCancelarVideo)
    }
}

@Composable
private fun VideoEmPreparo(andamento: AndamentoDoVideo, aoCancelar: (Int) -> Unit) {
    val formato = RoundedCornerShape(10.dp)
    val enviando = andamento.enviando
    val fracao = andamento.fracao
    val porcentagem = "${(fracao * 100).toInt()}%"
    val contagem = if (andamento.total > 1) "${andamento.posicao} de ${andamento.total}" else null
    val qual = contagem?.let { " $it" }.orEmpty()
    Box(Modifier.size(62.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(formato)
                .background(astraColors.raised)
                .border(1.dp, astraColors.borderMid, formato)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (enviando) "Enviando o vídeo$qual" else "Comprimindo o vídeo$qual, $porcentagem"
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (enviando) "enviando" else porcentagem,
                    style = MaterialTheme.typography.labelSmall,
                    color = astraColors.text1,
                )
                if (contagem != null) {
                    Text(text = contagem, style = MaterialTheme.typography.labelSmall, color = astraColors.text3)
                }
            }
            if (!enviando) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(astraColors.base),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fracao)
                            .fillMaxHeight()
                            .background(astraColors.accent, CircleShape),
                    )
                }
            }
        }
        BotaoDeTirar("Cancelar o vídeo", Modifier.align(Alignment.TopEnd)) { aoCancelar(andamento.posicao) }
    }
}

@Composable
private fun BotaoDeTirar(rotulo: String, modifier: Modifier, aoTocar: () -> Unit) {
    Box(
        modifier = modifier
            .padding(2.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = aoTocar)
            .semantics { contentDescription = rotulo },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "×",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

internal fun fmtBytes(b: Long?): String {
    if (b == null) return ""
    return when {
        b < 1024 -> "${b}B"
        b < 1024 * 1024 -> "${b / 1024}KB"
        else -> "%.1fMB".format(b / 1024f / 1024f)
    }
}
