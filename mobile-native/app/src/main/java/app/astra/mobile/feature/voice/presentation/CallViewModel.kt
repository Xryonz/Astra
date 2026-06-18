package app.astra.mobile.feature.voice.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.voice.CallStatus
import app.astra.mobile.core.voice.VoiceManager
import app.astra.mobile.feature.server.domain.ServerRepository
import app.astra.mobile.feature.server.domain.model.ServerMember
import dagger.hilt.android.lifecycle.HiltViewModel
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallParticipantUi(
    val identity: String,
    val name: String,
    val avatarUrl: String?,
    val isLocal: Boolean,
    val isSpeaking: Boolean,
    val micEnabled: Boolean,
    val cameraEnabled: Boolean,
    val videoTrack: VideoTrack?,
    val screenTrack: VideoTrack?,
)

data class CallUiState(
    val status: CallStatus = CallStatus.Idle,
    val channelName: String = "",
    val micEnabled: Boolean = false,
    val cameraOn: Boolean = false,
    val screenSharing: Boolean = false,
    val deafened: Boolean = false,
    val participants: List<CallParticipantUi> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class CallViewModel @Inject constructor(
    private val voiceManager: VoiceManager,
    private val serverRepository: ServerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val channelId: String = savedStateHandle["channelId"] ?: ""
    private val serverId: String = savedStateHandle["serverId"] ?: ""
    val channelName: String = savedStateHandle["name"] ?: "Canal de voz"

    private val members = MutableStateFlow<Map<String, ServerMember>>(emptyMap())

    // Junta o estado bruto do LiveKit (identities + quem fala/mic) com os nomes
    // reais vindos de /members.
    val state: StateFlow<CallUiState> =
        combine(voiceManager.state, members) { vs, mem ->
            CallUiState(
                status = vs.status,
                channelName = vs.channelName.ifBlank { channelName },
                micEnabled = vs.micEnabled,
                cameraOn = vs.cameraOn,
                screenSharing = vs.screenSharing,
                deafened = vs.deafened,
                error = vs.error,
                participants = vs.participants.map { p ->
                    val m = mem[p.identity]
                    CallParticipantUi(
                        identity = p.identity,
                        name = m?.name ?: if (p.isLocal) "Voce" else "Participante",
                        avatarUrl = m?.avatarUrl,
                        isLocal = p.isLocal,
                        isSpeaking = p.isSpeaking,
                        micEnabled = p.micEnabled,
                        cameraEnabled = p.cameraEnabled,
                        videoTrack = p.videoTrack,
                        screenTrack = p.screenTrack,
                    )
                },
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CallUiState(channelName = channelName),
        )

    init { if (serverId.isNotBlank()) loadMembers() }

    private fun loadMembers() {
        viewModelScope.launch {
            serverRepository.members(serverId).onSuccess { list ->
                members.value = list.associateBy { it.userId }
            }
        }
    }

    // Room pra alimentar o VideoTrackView (EGL/renderer). Estavel durante a call.
    val room: Room? get() = voiceManager.activeRoom

    fun join() = voiceManager.join("channel", channelId, channelName)
    fun toggleMic() = voiceManager.toggleMic()
    fun toggleCamera() = voiceManager.toggleCamera()
    fun toggleDeafen() = voiceManager.toggleDeafen()
    fun startScreenShare(resultData: android.content.Intent) = voiceManager.startScreenShare(resultData)
    fun stopScreenShare() = voiceManager.stopScreenShare()
    fun leave() = voiceManager.leave()
    fun permissionDenied() = voiceManager.setError("Permissao de microfone negada")
}
