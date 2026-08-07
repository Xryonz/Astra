package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.StickerApi
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.dto.CriarFigurinhaRequest
import app.astra.mobile.core.network.dto.ServerStickerDto
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.koin.core.context.GlobalContext

// FIGURINHAS DA CONSTELACAO — aba de gerenciamento.
//
// A imagem sobe INTEIRA pelo /api/upload, que desde a 0.1.73 guarda o original
// byte a byte. Nao ha recorte nem recompressao aqui de proposito: figurinha com
// fundo transparente perde o fundo se passar por re-encode pro formato errado, e
// o dono pediu explicitamente que arquivo nao perca qualidade.
//
// O upload devolve width/height medidos — guardamos os dois pra conversa reservar
// o espaco da figurinha antes de a imagem chegar.

@Composable
internal fun StickersSection(serverId: String, podeGerenciar: Boolean) {
    val stickerApi = remember { GlobalContext.get().get<StickerApi>() }
    val uploadApi = remember { GlobalContext.get().get<UploadApi>() }
    val escopo = rememberCoroutineScope()

    var figurinhas by remember(serverId) { mutableStateOf<List<ServerStickerDto>>(emptyList()) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    suspend fun recarregar() {
        runCatching { stickerApi.listar(serverId).stickers }
            .onSuccess { figurinhas = it }
            .onFailure { msg = "não deu pra carregar as figurinhas" to false }
    }
    LaunchedEffect(serverId) { recarregar() }

    FieldLabel("figurinhas desta constelação")
    Spacer(Modifier.height(8.dp))

    if (figurinhas.isEmpty()) {
        Text("nenhuma figurinha ainda.", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
    }

    figurinhas.forEach { fig ->
        LinhaDeFigurinha(
            fig = fig,
            podeApagar = podeGerenciar,
            onApagar = {
                escopo.launch {
                    runCatching { stickerApi.apagar(serverId, fig.id) }
                        .onSuccess { recarregar(); msg = "figurinha apagada" to true }
                        .onFailure { msg = "não deu pra apagar" to false }
                }
            },
        )
        Spacer(Modifier.height(4.dp))
    }

    if (podeGerenciar) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotaoIcone(Lucide.Upload, "subir uma figurinha", accent = true, ocupado = ocupado) {
                escopo.launch {
                    val arquivo = withContext(Dispatchers.IO) { chooseFiles().firstOrNull() } ?: return@launch
                    ocupado = true
                    msg = null
                    val r = withContext(Dispatchers.IO) {
                        runCatching {
                            val tipo = when (arquivo.extension.lowercase()) {
                                "png" -> "image/png"
                                "gif" -> "image/gif"
                                "webp" -> "image/webp"
                                "jpg", "jpeg" -> "image/jpeg"
                                else -> error("use PNG, GIF ou WebP")
                            }
                            val parte = MultipartBody.Part.createFormData(
                                "file", arquivo.name,
                                arquivo.asRequestBody(tipo.toMediaType()),
                            )
                            val enviado = uploadApi.upload(parte).data?.attachments?.firstOrNull()
                                ?: error("o servidor não devolveu a imagem")
                            // O nome vem do ARQUIVO, sem extensao: e o que a pessoa
                            // reconhece na lista, e pedir pra digitar de novo seria
                            // burocracia pra repetir o que ela ja escolheu.
                            val nome = arquivo.nameWithoutExtension.take(40).ifBlank { "figurinha" }
                            stickerApi.criar(
                                serverId,
                                CriarFigurinhaRequest(
                                    name = nome,
                                    url = enviado.url,
                                    width = enviado.width ?: 0,
                                    height = enviado.height ?: 0,
                                ),
                            )
                        }
                    }
                    ocupado = false
                    r.onSuccess { recarregar(); msg = "figurinha adicionada" to true }
                        .onFailure { msg = (it.message ?: "não deu pra subir") to false }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "PNG, GIF ou WebP — vai inteira, sem recompressão.",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            )
        }
    }

    msg?.let { (t, ok) ->
        Spacer(Modifier.height(10.dp))
        Text(t, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
    }
}

@Composable
private fun LinhaDeFigurinha(
    fig: ServerStickerDto,
    podeApagar: Boolean,
    onApagar: () -> Unit,
) {
    val src = remember(fig.id) { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hov) Obsidian.hover.copy(alpha = 0.5f) else Color.Transparent)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .hoverable(src)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Obsidian.base)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            AstraImage(
                url = fig.url,
                contentDescription = fig.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            fig.name,
            style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (fig.width > 0 && fig.height > 0) {
            Text(
                "${fig.width}×${fig.height}",
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
            )
            Spacer(Modifier.width(10.dp))
        }
        if (podeApagar) BotaoIcone(Lucide.Trash2, "apagar figurinha", danger = true) { onApagar() }
    }
}
