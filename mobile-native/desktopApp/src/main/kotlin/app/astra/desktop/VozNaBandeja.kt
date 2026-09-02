package app.astra.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.desktop.voice.VoiceSession

object VozNaBandeja {

    var sessao: VoiceSession? by mutableStateOf(null)
        private set

    fun assumir(voz: VoiceSession) {
        sessao = voz
    }

    fun largar(voz: VoiceSession) {
        if (sessao === voz) sessao = null
    }
}
