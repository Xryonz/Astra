package app.astra.mobile.feature.voice.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import app.astra.mobile.core.voice.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val voiceManager: VoiceManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val channelId: String = savedStateHandle["channelId"] ?: ""
    val channelName: String = savedStateHandle["name"] ?: "Canal de voz"

    val state = voiceManager.state

    fun join() = voiceManager.join("channel", channelId, channelName)
    fun toggleMic() = voiceManager.toggleMic()
    fun leave() = voiceManager.leave()
    fun permissionDenied() = voiceManager.setError("Permissao de microfone negada")
}
