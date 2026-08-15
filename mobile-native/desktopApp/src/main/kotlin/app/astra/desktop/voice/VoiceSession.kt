package app.astra.desktop.voice

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.ChannelDto
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import org.koin.core.Koin
import org.koin.core.qualifier.named

// Sessao de voz VIVA acima da navegacao.
//
// Antes o VoiceEngine nascia dentro do VoiceView (`remember(channel.id) { ... }`)
// e um DisposableEffect o matava quando a tela saia da composicao. Como abrir uma
// órbita de texto limpa o `voiceChannel` do palco, navegar DESCONECTAVA a call —
// era o "kick automatico". Aqui a sessão mora no shell: so desligar (ou entrar em
// outra sala) encerra.
//
// Dois conceitos que antes eram um so:
//   - `voiceChannel` (ShellVm) = que sala esta NO PALCO. Some ao navegar. Certo.
//   - `joined` (aqui)          = em que sala você esta CONECTADO. Sobrevive.
@Stable
class VoiceSession(private val scope: CoroutineScope, private val koin: Koin) {
    var joined by mutableStateOf<ChannelDto?>(null)
        private set
    var engine by mutableStateOf<VoiceEngine?>(null)
        private set

    // Engine so quando a sala do palco E a sala conectada — o lobby (sala aberta
    // mas não entrou) recebe null e desenha o botao de entrar.
    fun engineFor(channel: ChannelDto?): VoiceEngine? =
        if (channel != null && joined?.id == channel.id) engine else null

    fun join(channel: ChannelDto) = entrar("channel", channel)

    // Chamada de sussurro. A sala do LiveKit e `dm:<conversationId>` e o token
    // dela ja existia — o `connect` sempre foi generico, so ninguem chamava com
    // "dm".
    //
    // O ChannelDto aqui e SINTETICO: `id` = conversa, `name` = nome da pessoa.
    // Ele existe porque a tela de call inteira (VoiceView) e desenhada a partir
    // dele, e os participantes de la ja vem do LiveKit, nao da lista de membros
    // da constelacao — entao a mesma tela serve pra sussurro sem mudanca.
    fun joinDm(conversationId: String, titulo: String) =
        entrar("dm", ChannelDto(id = conversationId, name = titulo, type = "VOICE"))

    // `emSussurro` diz de que tipo e a sala conectada. A UI precisa porque num
    // sussurro nao ha soundboard nem lista de membros pra oferecer.
    var emSussurro by mutableStateOf(false)
        private set

    private fun entrar(tipo: String, sala: ChannelDto) {
        // "JA ESTOU NESTA SALA" TEM QUE INCLUIR TER MOTOR, e nao so o id bater.
        //
        // `joined` e o motor sao dois campos, e nada garantia que andassem juntos:
        // bastava um caminho deixar `joined` apontando pra sala e o motor nulo
        // (connect que falhou, dispose sem limpar) pra esta funcao virar um beco —
        // ela devolvia na hora, achando que ja estavamos dentro, e NUNCA mais se
        // entrava naquela sala. Sem erro, sem log: o botao simplesmente nao fazia
        // nada, que e o formato do "aceitei a chamada e nao entrei em call nenhuma".
        //
        // Com o motor na condicao, o estado inconsistente se conserta sozinho na
        // proxima tentativa em vez de travar pra sempre.
        if (joined?.id == sala.id && engine != null) return
        // Entrar noutra sala sai da anterior: uma call por vez (como o Discord).
        engine?.dispose()
        engine = VoiceEngine(
            scope,
            koin.get<VoiceApi>(),
            koin.get<OkHttpClient>(named("plain")),
            koin.get<DesktopPrefs>(),
        ).also { it.connect(tipo, sala.id) }
        joined = sala
        emSussurro = tipo == "dm"
    }

    fun leave() {
        engine?.dispose()
        engine = null
        joined = null
        emSussurro = false
    }
}
