package app.astra.mobile.core.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import app.astra.mobile.core.network.VoiceApi
import app.astra.mobile.core.network.dto.VoiceTokenRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
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

// identity = userId (LiveKit usa req.userId como identity). Nome/avatar sao
// resolvidos na camada de cima (CallViewModel via /members).
data class CallParticipant(
    val identity: String,
    val isLocal: Boolean,
    val isSpeaking: Boolean,
    val micEnabled: Boolean,
    val cameraEnabled: Boolean = false,
    // Track da camera (null = sem video). Renderizada via VideoTrackView.
    val videoTrack: VideoTrack? = null,
    // Track de compartilhamento de tela (null = nao esta compartilhando).
    val screenTrack: VideoTrack? = null,
)

data class VoiceState(
    val status: CallStatus = CallStatus.Idle,
    val channelName: String = "",
    val micEnabled: Boolean = false,
    val cameraOn: Boolean = false,
    val screenSharing: Boolean = false,
    val deafened: Boolean = false,
    val participants: List<CallParticipant> = emptyList(),
    val error: String? = null,
)

/**
 * Chamada de voz unica do app (espelha apps/web/src/store/voiceStore.ts).
 *
 * Fluxo: POST /api/voice/token -> LiveKit.create -> room.connect(url, token)
 * -> setMicrophoneEnabled(true). O audio remoto toca sozinho (o SDK gerencia
 * playback). CallService mantem a call viva com o app em background.
 *
 * M6b: expoe a lista de participantes (quem fala / mic) e deafen (setVolume 0
 * em todas as tracks remotas). M6c/M6d: camera/tela.
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

    // Room ativa — VideoTrackView precisa dela (EGL/renderer). Estavel durante a call.
    val activeRoom: Room? get() = room

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
                        participants = snapshot(r),
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

    // Camera e opcional (entra mudo de video). UI pede permissao CAMERA antes.
    fun toggleCamera() {
        val r = room ?: return
        scope.launch {
            val next = !_state.value.cameraOn
            try {
                r.localParticipant.setCameraEnabled(next)
                _state.update { it.copy(cameraOn = next) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Falha ao acessar a camera") }
            }
        }
    }

    // Screenshare: o resultData vem do dialogo MediaProjection (pedido na UI).
    // O LiveKit roda o proprio foreground service (type mediaProjection) usando
    // a notificacao passada aqui — a lib ja declara o service no manifesto dela.
    fun startScreenShare(resultData: Intent) {
        val r = room ?: return
        scope.launch {
            try {
                r.localParticipant.setScreenShareEnabled(
                    true,
                    ScreenCaptureParams(
                        mediaProjectionPermissionResultData = resultData,
                        notificationId = SCREEN_NOTIF_ID,
                        notification = screenShareNotification(),
                    ),
                )
                _state.update { it.copy(screenSharing = true) }
            } catch (e: Exception) {
                Log.w(TAG, "screenshare falhou: ${e.message}")
                _state.update { it.copy(error = "Falha ao compartilhar a tela") }
            }
        }
    }

    fun stopScreenShare() {
        val r = room ?: return
        scope.launch {
            try { r.localParticipant.setScreenShareEnabled(false) } catch (_: Exception) {}
            _state.update { it.copy(screenSharing = false) }
        }
    }

    private fun screenShareNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = appContext.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(SCREEN_CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        SCREEN_CHANNEL_ID,
                        "Compartilhamento de tela",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
        }
        return NotificationCompat.Builder(appContext, SCREEN_CHANNEL_ID)
            .setContentTitle("Compartilhando a tela")
            .setContentText("Astra")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build()
    }

    // Deafen estilo Discord: silencia todo mundo (setVolume 0) E corta seu mic.
    // Undeafen restaura o volume e reativa o mic.
    fun toggleDeafen() {
        val r = room ?: return
        scope.launch {
            val deaf = !_state.value.deafened
            applyDeafen(r, deaf)
            try { r.localParticipant.setMicrophoneEnabled(!deaf) } catch (_: Exception) {}
            _state.update { it.copy(deafened = deaf, micEnabled = !deaf) }
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
                if (event is RoomEvent.Disconnected) {
                    // Servidor/rede derrubou: solta a room sem cancelar este
                    // coletor de dentro dele mesmo.
                    releaseRoom()
                    _state.value = VoiceState()
                } else {
                    // Qualquer outro evento (entrou/saiu/falou/mutou/publicou track)
                    // -> rebuild barato da lista. Listas de call sao pequenas.
                    refreshParticipants(r)
                }
            }
        }
    }

    private fun refreshParticipants(r: Room) {
        // Reaplica deafen pra pegar tracks que acabaram de chegar.
        if (_state.value.deafened) applyDeafen(r, true)
        val list = snapshot(r)
        val localMic = list.firstOrNull { it.isLocal }?.micEnabled ?: _state.value.micEnabled
        _state.update { it.copy(participants = list, micEnabled = localMic) }
    }

    private fun snapshot(r: Room): List<CallParticipant> {
        val out = ArrayList<CallParticipant>()
        out += r.localParticipant.toCallParticipant(isLocal = true)
        r.remoteParticipants.values.forEach { out += it.toCallParticipant(isLocal = false) }
        return out
    }

    private fun Participant.toCallParticipant(isLocal: Boolean) = CallParticipant(
        identity = identity?.value.orEmpty(),
        isLocal = isLocal,
        isSpeaking = isSpeaking,
        micEnabled = isMicrophoneEnabled,
        cameraEnabled = isCameraEnabled,
        videoTrack = getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack,
        screenTrack = getTrackPublication(Track.Source.SCREEN_SHARE)?.track as? VideoTrack,
    )

    private fun applyDeafen(r: Room, deaf: Boolean) {
        val vol = if (deaf) 0.0 else 1.0
        r.remoteParticipants.values.forEach { p ->
            p.audioTrackPublications.forEach { (_, track) ->
                (track as? RemoteAudioTrack)?.setVolume(vol)
            }
        }
    }

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

    private companion object {
        const val TAG = "VoiceManager"
        const val SCREEN_NOTIF_ID = 4202
        const val SCREEN_CHANNEL_ID = "astra_screen"
    }
}
