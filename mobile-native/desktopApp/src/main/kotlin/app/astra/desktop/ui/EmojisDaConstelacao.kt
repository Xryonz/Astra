package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import app.astra.mobile.core.network.EmojiApi
import app.astra.mobile.core.network.dto.EmojiDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.context.GlobalContext

internal val REGEX_EMOJI_PERSONALIZADO = Regex(":([A-Za-z0-9_]{2,32}):")

internal object EmojisDaConstelacao {
    private val trava = Any()
    private val cache = HashMap<String, List<EmojiDto>>()
    private val _versao = MutableStateFlow(0)
    val versao: StateFlow<Int> = _versao

    suspend fun carregar(serverId: String): List<EmojiDto> {
        synchronized(trava) { cache[serverId] }?.let { return it }
        val api = GlobalContext.get().get<EmojiApi>()
        val lista = runCatching { api.listar(serverId).data.orEmpty() }.getOrDefault(emptyList())
        synchronized(trava) { cache[serverId] = lista }
        return lista
    }

    fun invalidar(serverId: String) {
        synchronized(trava) { cache.remove(serverId) }
        _versao.value += 1
    }
}

@Immutable
internal class EmojisDaSala(
    val lista: List<EmojiDto>,
    val porNome: Map<String, EmojiDto>,
    val inline: Map<String, InlineTextContent>,
) {
    fun realce(texto: String): Realce {
        if (texto.isEmpty()) return Realce.NENHUM
        var temPersonalizado = false
        var quantos = 0
        var soEmoji = true
        var i = 0
        while (i < texto.length) {
            if (texto[i] == ':' && porNome.isNotEmpty()) {
                val m = REGEX_EMOJI_PERSONALIZADO.matchAt(texto, i)
                if (m != null && porNome.containsKey(m.groupValues[1].lowercase())) {
                    temPersonalizado = true
                    quantos++
                    i = m.range.last + 1
                    continue
                }
            }
            val cp = texto.codePointAt(i)
            when {
                Character.isWhitespace(cp) -> {}
                ehGlifoDeEmoji(cp) -> quantos++
                else -> soEmoji = false
            }
            i += Character.charCount(cp)
        }
        return Realce(temPersonalizado, soEmoji && quantos in 1..12)
    }

    companion object {
        val VAZIO = EmojisDaSala(emptyList(), emptyMap(), emptyMap())
    }
}

internal data class Realce(val temPersonalizado: Boolean, val soEmoji: Boolean) {
    companion object { val NENHUM = Realce(false, false) }
}

internal val LocalEmojisDaSala = staticCompositionLocalOf { EmojisDaSala.VAZIO }

@Composable
internal fun rememberEmojisDaSala(serverId: String?): EmojisDaSala {
    val versao by EmojisDaConstelacao.versao.collectAsState()
    var lista by remember(serverId) { mutableStateOf<List<EmojiDto>>(emptyList()) }
    LaunchedEffect(serverId, versao) {
        lista = if (serverId == null) emptyList() else EmojisDaConstelacao.carregar(serverId)
    }
    return remember(lista) {
        if (lista.isEmpty()) EmojisDaSala.VAZIO
        else EmojisDaSala(lista, lista.associateBy { it.name.lowercase() }, montarInline(lista))
    }
}

private const val ALTURA_EM = 1.4f

private fun montarInline(lista: List<EmojiDto>): Map<String, InlineTextContent> =
    lista.associate { e ->
        idInline(e.name) to InlineTextContent(
            Placeholder(ALTURA_EM.em, ALTURA_EM.em, PlaceholderVerticalAlign.TextCenter),
        ) {
            AstraImage(
                url = e.url,
                contentDescription = ":${e.name}:",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

private fun idInline(nome: String) = "e:" + nome.lowercase()

internal fun AnnotatedString.Builder.appendComEmojis(s: String, emojis: EmojisDaSala) {
    if (emojis.porNome.isEmpty() || ':' !in s) {
        append(s)
        return
    }
    var i = 0
    for (m in REGEX_EMOJI_PERSONALIZADO.findAll(s)) {
        val nome = m.groupValues[1].lowercase()
        if (!emojis.porNome.containsKey(nome)) continue
        append(s.substring(i, m.range.first))
        appendInlineContent(idInline(nome), m.value)
        i = m.range.last + 1
    }
    append(s.substring(i))
}

private fun ehGlifoDeEmoji(cp: Int): Boolean = when (cp) {
    0x200D, 0xFE0F, 0xFE0E, 0x20E3 -> true
    else -> cp in 0x2300..0x23FF || cp in 0x25A0..0x27BF ||
        cp in 0x2B00..0x2BFF || cp in 0x1F000..0x1FAFF
}
