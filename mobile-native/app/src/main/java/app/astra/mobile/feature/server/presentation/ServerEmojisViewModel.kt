package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.EmojisApi
import app.astra.mobile.core.network.dto.RenameEmojiRequest
import app.astra.mobile.core.upload.ImageEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import javax.inject.Inject

data class EmojiUi(
    val id: String,
    val name: String,
    val url: String,
)

data class ServerEmojisUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val emojis: List<EmojiUi> = emptyList(),
    val uploading: Boolean = false,
    val actionError: String? = null,
)

@HiltViewModel
class ServerEmojisViewModel @Inject constructor(
    private val emojisApi: EmojisApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(ServerEmojisUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val list = emojisApi.emojis(serverId).data.orEmpty().map { EmojiUi(it.id, it.name, it.url) }
                _state.update { it.copy(loading = false, emojis = list) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Sem conexao com o servidor") }
            }
        }
    }

    // name = sem os dois-pontos; backend valida [a-z0-9_]{2,32}
    fun addEmoji(name: String, bytes: ByteArray, mime: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        _state.update { it.copy(uploading = true, actionError = null) }
        viewModelScope.launch {
            ImageEncoder.toUploadBytes(bytes, mime, EMOJI_DIM, EMOJI_TARGET, EMOJI_GIF_MAX)
                .onSuccess { (data, outMime) ->
                    try {
                        val ext = if (outMime == "image/gif") "gif" else "jpg"
                        val part = MultipartBody.Part.createFormData(
                            "file", "emoji.$ext", data.toRequestBody(outMime.toMediaTypeOrNull()),
                        )
                        val namePart = clean.toRequestBody("text/plain".toMediaTypeOrNull())
                        val created = emojisApi.createEmoji(serverId, namePart, part).data ?: return@onSuccess
                        _state.update {
                            it.copy(uploading = false, emojis = it.emojis + EmojiUi(created.id, created.name, created.url))
                        }
                    } catch (e: HttpException) {
                        val msg = when (e.code()) {
                            409 -> "Ja existe um emoji com esse nome"
                            413 -> "Imagem maior que 512KB"
                            422 -> "Nome invalido (2-32, letras/numeros/_)"
                            429 -> "Limite de 50 emojis atingido"
                            else -> "Nao foi possivel adicionar"
                        }
                        _state.update { it.copy(uploading = false, actionError = msg) }
                    } catch (e: Exception) {
                        _state.update { it.copy(uploading = false, actionError = "Nao foi possivel adicionar") }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(uploading = false, actionError = e.message ?: "Imagem invalida") }
                }
        }
    }

    fun rename(id: String, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val prev = _state.value.emojis
        _state.update { it.copy(emojis = it.emojis.map { e -> if (e.id == id) e.copy(name = clean) else e }) }
        viewModelScope.launch {
            try {
                emojisApi.renameEmoji(serverId, id, RenameEmojiRequest(clean))
            } catch (e: Exception) {
                _state.update { it.copy(emojis = prev, actionError = "Nao foi possivel renomear") }
            }
        }
    }

    fun delete(id: String) {
        val prev = _state.value.emojis
        _state.update { it.copy(emojis = it.emojis.filterNot { e -> e.id == id }) }
        viewModelScope.launch {
            try {
                emojisApi.deleteEmoji(serverId, id)
            } catch (e: Exception) {
                _state.update { it.copy(emojis = prev, actionError = "Nao foi possivel apagar") }
            }
        }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }

    private companion object {
        const val EMOJI_DIM = 256
        const val EMOJI_TARGET = 400_000
        const val EMOJI_GIF_MAX = 500_000
    }
}
