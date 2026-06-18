package app.astra.mobile.core.voice

import android.content.Context
import android.util.Log
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.VoiceTokenRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

enum class CallStatus { Idle, Connecting, Connected, Error }

data class VoiceState(
    val status: CallStatus = CallStatus.Idle,
    val channelName: String = "",
    val micEnabled: Boolean = false,
    val participants: Int = 0,
    val error: String? = null,
)

/**
 * Chamada de voz unica do app (espelha apps/web/src/store/voiceStore.ts).
 *
 * Fluxo: POST /api/voice/token -> LiveKit.create -> room.connect(url, token)
 * -> setMicrophoneEnabled(true). O audio remoto toca automaticamente (o SDK
 * gerencia playback). CallService mantem a call viva com o app em background.
 *
 * M6a = so audio. Camera/tela entram em M6c/M6d.
 */
@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val voiceApi: VoiceApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var room: Room? = null
    private var eventsJob: Job? = null

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    fun join(roomKind: String, roomId: String, channelName: String) {
        val current = _state.value.status
        if (current == CallStatus.Connecting || current == CallStatus.Connected) return
        _state.value = VoiceState(status = CallStatus.Connecting, channelName = channelName)
        scope.launch {
            try {
                val data = voiceApi.token(VoiceTokenRequest(roomKind, roomId)).data
                    ?: error("Resposta vazia do servidor")
                val r = LiveKit.create(appContext)
                room = r
                observe(r)
                r.connect(data.url, data.token)
                r.localParticipant.setMicrophoneEnabled(true)
                CallService.start(appContext, channelName)
                _state.update {
                    it.copy(
                        status = CallStatus.Connected,
                        micEnabled = true,
                        participants = participantCount(r),
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "join falhou: ${e.message}")
                cleanup()
                _state.update { it.copy(status = CallStatus.Error, error = humanize(e)) }
            }
        }
    }

    fun toggleMic() {
        val r = room ?: return
        scope.launch {
            val next = !_state.value.micEnabled
            try {
                r.localParticipant.setMicrophoneEnabled(next)
                _state.update { it.copy(micEnabled = next) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Falha ao acessar microfone") }
            }
        }
    }

    fun leave() {
        scope.launch {
            cleanup()
            _state.value = VoiceState()
        }
    }

    // Permissao de mic negada na UI: nao tem como entrar (FGS type=microphone exige).
    fun setError(message: String) {
        cleanup()
        _state.value = VoiceState(status = CallStatus.Error, error = message)
    }

    private fun observe(r: Room) {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            r.events.collect { event ->
                when (event) {
                    is RoomEvent.Disconnected -> {
                        // Servidor/rede derrubou: solta a room sem cancelar este
                        // coletor de dentro dele mesmo.
                        releaseRoom()
                        _state.value = VoiceState()
                    }
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected,
                    is RoomEvent.TrackSubscribed,
                    is RoomEvent.TrackUnsubscribed,
                    -> _state.update { it.copy(participants = participantCount(r)) }
                    else -> {}
                }
            }
        }
    }

    private fun participantCount(r: Room): Int = r.remoteParticipants.size + 1

    private fun releaseRoom() {
        room?.let {
            try { it.disconnect() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        room = null
        CallService.stop(appContext)
    }

    private fun cleanup() {
        eventsJob?.cancel()
        eventsJob = null
        releaseRoom()
    }

    private fun humanize(e: Exception): String = when {
        e is HttpException && e.code() == 503 -> "Chamadas estao desativadas no servidor"
        e is HttpException && e.code() == 403 -> "Voce nao tem acesso a esse canal"
        else -> e.message ?: "Falha ao conectar na chamada"
    }

    private companion object { const val TAG = "VoiceManager" }
}
