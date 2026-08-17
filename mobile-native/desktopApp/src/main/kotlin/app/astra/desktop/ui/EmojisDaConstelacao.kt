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

// EMOJI PERSONALIZADO DA CONSTELACAO.
//
// O que se digita e `:nome:`, e a MESMA regra do servidor e do site — 2 a 32
// caracteres, letras, numeros e underscore. Divergir aqui faria a conversa se ler
// diferente em cada cliente: o que o site desenha como imagem sairia como texto
// cru no desktop, na mesma mensagem.
//
// O texto guardado E o `:nome:`. Nao ha id de emoji dentro da mensagem, e isso e
// deliberado no backend: mensagem antiga continua legivel depois que o emoji some
// (vira o proprio `:nome:` escrito), em vez de virar um retangulo quebrado.
internal val REGEX_EMOJI_PERSONALIZADO = Regex(":([A-Za-z0-9_]{2,32}):")

// Cacheado por constelacao e nao por tela: a conversa e o seletor pedem a mesma
// lista, e sem cache o painel do compositor buscaria de novo a cada abertura.
//
// `versao` e o que faz a aba de configuracoes conversar com a conversa: subir ou
// apagar um emoji invalida, a versao muda, e quem estiver lendo recarrega. Sem
// isso, o emoji que voce acabou de subir so apareceria depois de trocar de
// constelacao — e a primeira coisa que alguem faz depois de subir e usar.
internal object EmojisDaConstelacao {
    private val trava = Any()
    private val cache = HashMap<String, List<EmojiDto>>()
    private val _versao = MutableStateFlow(0)
    val versao: StateFlow<Int> = _versao

    suspend fun carregar(serverId: String): List<EmojiDto> {
        synchronized(trava) { cache[serverId] }?.let { return it }
        val api = GlobalContext.get().get<EmojiApi>()
        // Falha vira lista vazia: sem emoji a conversa ainda se le (o `:nome:` fica
        // escrito). Barrar a mensagem por causa de um enfeite seria pior.
        val lista = runCatching { api.listar(serverId).data.orEmpty() }.getOrDefault(emptyList())
        synchronized(trava) { cache[serverId] = lista }
        return lista
    }

    fun invalidar(serverId: String) {
        synchronized(trava) { cache.remove(serverId) }
        _versao.value += 1
    }
}

// A lista ja montada nas duas formas que a tela precisa: por nome (pra achar) e
// como conteudo embutido (pra desenhar). Montar isto uma vez por constelacao e o
// que permite o texto da mensagem ser memoizado — ver o comentario do ChatView
// sobre o caminho mais quente do app.
@Immutable
internal class EmojisDaSala(
    val lista: List<EmojiDto>,
    val porNome: Map<String, EmojiDto>,
    val inline: Map<String, InlineTextContent>,
) {
    // Duas perguntas numa varredura so, porque as duas saem da mesma leitura do
    // texto e ele e percorrido em toda mensagem da conversa.
    fun realce(texto: String): Realce {
        if (texto.isEmpty()) return Realce.NENHUM
        var temPersonalizado = false
        var quantos = 0
        var soEmoji = true
        var i = 0
        while (i < texto.length) {
            if (texto[i] == ':' && porNome.isNotEmpty()) {
                // matchAt ANCORA no indice; `find(texto, i)` varreria o resto do texto
                // atras de um casamento mais adiante. A diferenca so aparece no caso
                // ruim — uma mensagem cheia de dois-pontos que nao formam emoji nenhum
                // faria uma varredura ate o fim a cada um deles.
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
        // Teto no numero: uma mensagem com cinquenta emojis desenhada ao dobro
        // vira uma parede que empurra a conversa inteira pra fora da tela. Ate uma
        // duzia ainda se le como "mandei emojis"; acima disso e texto.
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

// Busca a lista da constelacao e ja monta as duas formas. serverId nulo (sussurro)
// devolve o vazio sem tocar na rede: emoji pertence a uma constelacao, e sussurro
// nao tem de onde tirar — mesma regra da figurinha e da enquete.
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

// O TAMANHO VEM EM `em`, NAO EM `dp`, e isso resolve dois problemas de uma vez: o
// emoji acompanha o ajuste de tamanho de fonte das configuracoes sem uma segunda
// conta, e a mensagem que e so emoji fica grande apenas aumentando a fonte da
// linha — sem precisar de um segundo mapa de conteudo embutido so pro tamanho
// dobrado.
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

// Troca `:nome:` conhecido por imagem; o desconhecido segue como texto, igual ao
// site. O texto alternativo e o proprio `:nome:` — e ele que o leitor de tela fala
// e o que sai quando se copia a mensagem.
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

// Emoji unicode, so o bastante pra saber se a mensagem e "so emoji". Nao e uma
// tabela do padrao Unicode e nao precisa ser: errar pra menos deixa a mensagem no
// tamanho normal, que e o comportamento de sempre.
//
// As setas tipograficas (U+2190..21FF) ficaram DE FORA de proposito — "→" sozinho
// e pontuacao, nao emoji, e desenha-lo ao dobro seria estranho.
private fun ehGlifoDeEmoji(cp: Int): Boolean = when (cp) {
    0x200D, 0xFE0F, 0xFE0E, 0x20E3 -> true // juntador, seletor de variacao, tecla
    else -> cp in 0x2300..0x23FF || cp in 0x25A0..0x27BF ||
        cp in 0x2B00..0x2BFF || cp in 0x1F000..0x1FAFF
}
