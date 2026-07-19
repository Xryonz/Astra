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
// orbita de texto limpa o `voiceChannel` do palco, navegar DESCONECTAVA a call —
// era o "kick automatico". Aqui a sessao mora no shell: so desligar (ou entrar em
// outra sala) encerra.
//
// Dois conceitos que antes eram um so:
//   - `voiceChannel` (ShellVm) = que sala esta NO PALCO. Some ao navegar. Certo.
//   - `joined` (aqui)          = em que sala voce esta CONECTADO. Sobrevive.
@Stable
class VoiceSession(private val scope: CoroutineScope, private val koin: Koin) {
    var joined by mutableStateOf<ChannelDto?>(null)
        private set
    var engine by mutableStateOf<VoiceEngine?>(null)
        private set

    // Engine so quando a sala do palco E a sala conectada — o lobby (sala aberta
    // mas nao entrou) recebe null e desenha o botao de entrar.
    fun engineFor(channel: ChannelDto?): VoiceEngine? =
        if (channel != null && joined?.id == channel.id) engine else null

    fun join(channel: ChannelDto) {
        if (joined?.id == channel.id) return
        // Entrar noutra sala sai da anterior: uma call por vez (como o Discord).
        engine?.dispose()
        engine = VoiceEngine(
            scope,
            koin.get<VoiceApi>(),
            koin.get<OkHttpClient>(named("plain")),
            koin.get<DesktopPrefs>(),
        ).also { it.connect("channel", channel.id) }
        joined = channel
    }

    fun leave() {
        engine?.dispose()
        engine = null
        joined = null
    }
}
