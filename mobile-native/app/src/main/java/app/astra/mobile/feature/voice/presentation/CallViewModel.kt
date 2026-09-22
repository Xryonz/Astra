package app.astra.mobile.feature.voice.presentation

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.voice.CallStatus
import app.astra.mobile.core.voice.Rosto
import app.astra.mobile.core.voice.RostosDaCall
import app.astra.mobile.core.voice.SaidaDeSom
import app.astra.mobile.core.voice.SalaDaCall
import app.astra.mobile.core.voice.VoiceManager
import app.astra.mobile.core.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class PessoaNaTela(
    val identity: String,
    val nome: String,
    val foto: String?,
    val souEu: Boolean,
    val falando: Boolean,
    val mudo: Boolean,
    val surdo: Boolean,
    val transmitindo: Boolean,
    val tela: VideoTrack?,
)

@Immutable
data class CallUiState(
    val status: CallStatus = CallStatus.Idle,
    val sala: SalaDaCall? = null,
    val mudo: Boolean = false,
    val surdo: Boolean = false,
    val transmitindo: Boolean = false,
    val pessoas: List<PessoaNaTela> = emptyList(),
    val inicio: Long? = null,
    val saidas: List<SaidaDeSom> = emptyList(),
    val saidaAtual: SaidaDeSom? = null,
    val erro: String? = null,
)

@HiltViewModel
class CallViewModel @Inject constructor(
    private val voiceManager: VoiceManager,
    rostosDaCall: RostosDaCall,
) : ViewModel() {

    val state: StateFlow<CallUiState> =
        combine(voiceManager.state, rostosDaCall.rostos, ::montar)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                montar(voiceManager.state.value, rostosDaCall.rostos.value),
            )

    val room: Room? get() = voiceManager.activeRoom

    fun alternarMudo() = voiceManager.alternarMudo()
    fun alternarSurdo() = voiceManager.alternarSurdo()
    fun sair() = voiceManager.sair()
    fun escolherSaida(chave: String) = voiceManager.escolherSaida(chave)
    fun comecarATransmitir(permissao: Intent) = voiceManager.comecarATransmitir(permissao)
    fun pararDeTransmitir() = voiceManager.pararDeTransmitir()
    fun assistir(quem: String?, emTelaCheia: Boolean) = voiceManager.assistir(quem, emTelaCheia)
    fun esquecerErro() = voiceManager.esquecerErro()

    fun tentarDeNovo() {
        val sala = voiceManager.state.value.sala ?: return
        voiceManager.entrar(sala.tipo, sala.id, sala.nome, sala.orbitaId, sala.fotoDoSussurro)
    }

    private fun montar(v: VoiceState, rostos: Map<String, Rosto>) = CallUiState(
        status = v.status,
        sala = v.sala,
        mudo = v.mudo,
        surdo = v.surdo,
        transmitindo = v.transmitindo,
        pessoas = v.pessoas.map { p ->
            val rosto = rostos[p.identity] ?: RostosDaCall.rostoPadrao(v.sala.takeUnless { p.souEu })
            PessoaNaTela(
                identity = p.identity,
                nome = if (p.souEu) "você" else rosto.nome,
                foto = rosto.foto,
                souEu = p.souEu,
                falando = p.falando,
                mudo = p.mudo,
                surdo = p.surdo,
                transmitindo = p.transmitindo,
                tela = p.tela,
            )
        },
        inicio = v.inicio,
        saidas = v.saidas,
        saidaAtual = v.saidaAtual,
        erro = v.erro,
    )
}
