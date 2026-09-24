package app.astra.mobile.feature.server.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.SoundApi
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.dto.CriarSomRequest
import app.astra.mobile.core.network.dto.ServerSoundDto
import app.astra.mobile.core.upload.ConversorDeSom
import app.astra.mobile.core.voice.SonsDaConstelacao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

data class SonsDaOrbitaUiState(
    val carregando: Boolean = true,
    val erro: String? = null,
    val sons: List<ServerSoundDto> = emptyList(),
    val subindo: Boolean = false,
    val aviso: String? = null,
)

@HiltViewModel
class SonsDaOrbitaViewModel @Inject constructor(
    private val soundApi: SoundApi,
    private val uploadApi: UploadApi,
    private val sonsDaConstelacao: SonsDaConstelacao,
    @ApplicationContext private val contexto: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val orbitaId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(SonsDaOrbitaUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { recarregar() }
    }

    private suspend fun recarregar() {
        runCatching { soundApi.listar(orbitaId).sounds }
            .onSuccess { lista -> _state.update { it.copy(carregando = false, erro = null, sons = lista) } }
            .onFailure { _state.update { it.copy(carregando = false, erro = "Não foi possível carregar os sons") } }
    }

    fun ouvir(url: String) = sonsDaConstelacao.tocar(url)

    fun adicionar(uri: Uri) {
        _state.update { it.copy(subindo = true, aviso = null) }
        viewModelScope.launch {
            val feito = withContext(Dispatchers.IO) {
                runCatching {
                    val convertido = ConversorDeSom.paraWav(contexto, uri)
                        ?: error("Não foi possível ler esse arquivo")
                    val parte = MultipartBody.Part.createFormData(
                        "file",
                        "som.wav",
                        convertido.wav.toRequestBody("audio/wav".toMediaTypeOrNull()),
                    )
                    val enviado = uploadApi.upload(parte).data?.attachments?.firstOrNull()
                        ?: error("O servidor não devolveu o arquivo")
                    soundApi.criar(
                        orbitaId,
                        CriarSomRequest(
                            name = nomeDoArquivo(uri),
                            url = enviado.url,
                            durationMs = convertido.duracaoMs,
                        ),
                    )
                }
            }
            _state.update { it.copy(subindo = false, aviso = feito.exceptionOrNull()?.let { e -> e.message ?: "Não foi possível subir o som" }) }
            if (feito.isSuccess) recarregar()
        }
    }

    fun apagar(somId: String) {
        viewModelScope.launch {
            runCatching { soundApi.apagar(orbitaId, somId) }
                .onSuccess { _state.update { e -> e.copy(sons = e.sons.filterNot { it.id == somId }) } }
                .onFailure { _state.update { it.copy(aviso = "Não foi possível apagar") } }
        }
    }

    fun esquecerAviso() = _state.update { it.copy(aviso = null) }

    private fun nomeDoArquivo(uri: Uri): String {
        val cru = runCatching {
            contexto.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
        return cru.orEmpty().substringBeforeLast('.').trim().take(40).ifBlank { "som" }
    }
}
