package app.astra.mobile.core.voice

import android.content.Context
import app.astra.mobile.core.realtime.DmCallInvite
import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.core.update.CuidadorDeAtualizacao
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.feature.profile.domain.model.UserStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class LigacaoNaTela(
    val conversationId: String,
    val nome: String,
    val avatarUrl: String?,
    val euLiguei: Boolean,
)

@Singleton
class LigacaoDeSussurro @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val socketManager: SocketManager,
    private val voiceManager: VoiceManager,
    private val userRepository: UserRepository,
) {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var prazo: Job? = null

    private val _ligacao = MutableStateFlow<LigacaoNaTela?>(null)
    val ligacao: StateFlow<LigacaoNaTela?> = _ligacao.asStateFlow()

    private val _entrouNaCall = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val entrouNaCall: SharedFlow<Unit> = _entrouNaCall.asSharedFlow()

    init {
        escopo.launch { socketManager.dmCallInvite.collect { receber(it) } }
        escopo.launch {
            socketManager.dmCallAccept.collect { conversa ->
                val atual = _ligacao.value ?: return@collect
                if (atual.conversationId != conversa) return@collect
                if (atual.euLiguei) entrarNaCall(atual) else limpar()
            }
        }
        escopo.launch {
            socketManager.dmCallReject.collect { conversa ->
                if (_ligacao.value?.conversationId == conversa) limpar()
            }
        }
        escopo.launch {
            socketManager.dmCallEnded.collect { conversa ->
                if (_ligacao.value?.conversationId == conversa) limpar()
            }
        }
    }

    fun ligar(conversationId: String, nome: String, avatarUrl: String?) {
        if (_ligacao.value != null || voiceManager.state.value.naCall) return
        socketManager.ligar(conversationId)
        mostrar(LigacaoNaTela(conversationId, nome, avatarUrl, euLiguei = true), TOQUE_MS)
        SonsDaCall.comecarToque(contexto, souEuQueLiguei = true)
    }

    fun atender() {
        val atual = _ligacao.value ?: return
        if (atual.euLiguei) return
        socketManager.atender(atual.conversationId)
        entrarNaCall(atual)
    }

    fun recusar() {
        val atual = _ligacao.value ?: return
        socketManager.desligar(atual.conversationId)
        limpar()
    }

    private fun receber(convite: DmCallInvite) {
        if (_ligacao.value != null || voiceManager.state.value.naCall) return
        val chegando = LigacaoNaTela(convite.conversationId, convite.fromDisplayName, convite.fromAvatarUrl, euLiguei = false)
        mostrar(chegando, convite.msRestantes)
        escopo.launch {
            val status = userRepository.me().getOrNull()?.status
            if (_ligacao.value != chegando) return@launch
            if (status != UserStatus.DND) SonsDaCall.comecarToque(contexto, souEuQueLiguei = false)
            if (!CuidadorDeAtualizacao.visivel) AvisoDeLigacao.mostrar(contexto, chegando)
        }
    }

    private fun mostrar(ligacao: LigacaoNaTela, duracaoMs: Long) {
        _ligacao.value = ligacao
        prazo?.cancel()
        prazo = escopo.launch {
            delay(duracaoMs.coerceIn(5_000L, TOQUE_MS))
            if (_ligacao.value == ligacao) limpar()
        }
    }

    private fun entrarNaCall(ligacao: LigacaoNaTela) {
        limpar()
        voiceManager.entrar(VoiceManager.TIPO_SUSSURRO, ligacao.conversationId, ligacao.nome, null, ligacao.avatarUrl)
        _entrouNaCall.tryEmit(Unit)
    }

    private fun limpar() {
        prazo?.cancel()
        prazo = null
        SonsDaCall.pararToque()
        AvisoDeLigacao.esconder(contexto)
        _ligacao.value = null
    }

    private companion object {
        const val TOQUE_MS = 45_000L
    }
}
