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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.EmojiApi
import app.astra.mobile.core.network.dto.EmojiDto
import app.astra.mobile.core.network.dto.RenameEmojiRequest
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.koin.core.context.GlobalContext

private const val TETO_EMOJIS = 50

@Composable
internal fun EmojisSection(serverId: String, podeGerenciar: Boolean) {
    val api = remember { GlobalContext.get().get<EmojiApi>() }
    val escopo = rememberCoroutineScope()

    var emojis by remember(serverId) { mutableStateOf<List<EmojiDto>>(emptyList()) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    suspend fun recarregar() {
        runCatching { api.listar(serverId).data.orEmpty() }
            .onSuccess {
                emojis = it
                EmojisDaConstelacao.invalidar(serverId)
            }
            .onFailure { msg = "não foi possível carregar os emojis" to false }
    }
    LaunchedEffect(serverId) { recarregar() }

    FieldLabel("emojis desta constelação")
    Spacer(Modifier.height(8.dp))

    if (emojis.isEmpty()) {
        Text("nenhum emoji ainda.", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
    }

    emojis.forEach { emoji ->
        LinhaDeEmoji(
            emoji = emoji,
            podeGerenciar = podeGerenciar,
            onRenomear = { novo ->
                escopo.launch {
                    runCatching { api.renomear(serverId, emoji.id, RenameEmojiRequest(novo)) }
                        .onSuccess { recarregar(); msg = "agora é :$novo:" to true }
                        .onFailure { msg = "não foi possível renomear — o nome pode já estar em uso" to false }
                }
            },
            onApagar = {
                escopo.launch {
                    runCatching { api.apagar(serverId, emoji.id) }
                        .onSuccess { recarregar(); msg = "emoji apagado" to true }
                        .onFailure { msg = "não foi possível apagar" to false }
                }
            },
        )
        Spacer(Modifier.height(4.dp))
    }

    if (podeGerenciar) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotaoIcone(Lucide.Upload, "subir um emoji", accent = true, ocupado = ocupado) {
                escopo.launch {
                    val arquivo = withContext(Dispatchers.IO) { chooseFiles().firstOrNull() } ?: return@launch
                    if (emojis.size >= TETO_EMOJIS) {
                        msg = "esta constelação já tem $TETO_EMOJIS emojis" to false
                        return@launch
                    }
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
                            val nome = nomeDeEmoji(arquivo.nameWithoutExtension)
                            api.criar(serverId, nome.toRequestBody(null), parte)
                        }
                    }
                    ocupado = false
                    r.onSuccess { recarregar(); msg = "emoji adicionado" to true }
                        .onFailure { msg = (it.message ?: "não foi possível subir") to false }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "PNG, GIF ou WebP até 512KB — vira 128px. O nome sai do arquivo e pode ser corrigido.",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            )
        }
    }

    msg?.let { (t, ok) ->
        Spacer(Modifier.height(10.dp))
        Text(t, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
    }
}

internal fun nomeDeEmoji(bruto: String): String {
    val limpo = buildString {
        for (c in bruto.lowercase()) {
            when {
                c.isLetter() && c.code < 128 || c.isDigit() -> append(c)
                c == '_' || c == ' ' || c == '-' -> append('_')
            }
        }
    }.trim('_').replace(Regex("_{2,}"), "_").take(32)
    return if (limpo.length >= 2) limpo else "emoji"
}

@Composable
private fun LinhaDeEmoji(
    emoji: EmojiDto,
    podeGerenciar: Boolean,
    onRenomear: (String) -> Unit,
    onApagar: () -> Unit,
) {
    val src = remember(emoji.id) { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    var editando by remember(emoji.id) { mutableStateOf(false) }
    var rascunho by remember(emoji.id) { mutableStateOf(emoji.name) }
    val foco = remember { FocusRequester() }

    fun confirmar() {
        val novo = nomeDeEmoji(rascunho)
        editando = false
        if (novo != emoji.name) onRenomear(novo)
    }

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
                url = emoji.url,
                contentDescription = ":${emoji.name}:",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(10.dp))
        if (editando) {
            LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
            BasicTextField(
                value = rascunho,
                onValueChange = { rascunho = it.take(32) },
                singleLine = true,
                textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmMono),
                cursorBrush = SolidColor(Obsidian.accent),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(foco)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Enter -> { confirmar(); true }
                            Key.Escape -> { rascunho = emoji.name; editando = false; true }
                            else -> false
                        }
                    },
            )
        } else {
            Text(
                ":${emoji.name}:",
                style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmMono),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (podeGerenciar) {
            if (editando) {
                BotaoIcone(Lucide.Check, "confirmar o nome", accent = true) { confirmar() }
            } else {
                BotaoIcone(Lucide.Pencil, "renomear emoji") { rascunho = emoji.name; editando = true }
            }
            Spacer(Modifier.width(4.dp))
            BotaoIcone(Lucide.Trash2, "apagar emoji", danger = true) { onApagar() }
        }
    }
}
