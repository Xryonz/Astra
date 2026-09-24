package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.StickerApi
import app.astra.mobile.core.network.dto.CriarFigurinhaRequest
import app.astra.mobile.core.network.dto.ServerStickerDto
import app.astra.mobile.core.upload.ImageUploader
import app.astra.mobile.core.upload.UploadFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FigurinhasDaOrbitaUiState(
    val carregando: Boolean = true,
    val erro: String? = null,
    val figurinhas: List<ServerStickerDto> = emptyList(),
    val subindo: Boolean = false,
    val aviso: String? = null,
)

@HiltViewModel
class FigurinhasDaOrbitaViewModel @Inject constructor(
    private val stickerApi: StickerApi,
    private val imageUploader: ImageUploader,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val orbitaId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(FigurinhasDaOrbitaUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { recarregar() }
    }

    private suspend fun recarregar() {
        runCatching { stickerApi.listar(orbitaId).stickers }
            .onSuccess { lista -> _state.update { it.copy(carregando = false, erro = null, figurinhas = lista) } }
            .onFailure { _state.update { it.copy(carregando = false, erro = "Não foi possível carregar as figurinhas") } }
    }

    fun adicionar(nome: String, bytes: ByteArray, mime: String, arquivo: String) {
        val limpo = nome.trim().take(40).ifBlank { "figurinha" }
        _state.update { it.copy(subindo = true, aviso = null) }
        viewModelScope.launch {
            imageUploader.uploadMany(listOf(UploadFile(bytes, mime, arquivo)))
                .mapCatching { enviados ->
                    val enviado = enviados.firstOrNull() ?: error("O servidor não devolveu a imagem")
                    stickerApi.criar(
                        orbitaId,
                        CriarFigurinhaRequest(
                            name = limpo,
                            url = enviado.url,
                            width = enviado.width ?: 0,
                            height = enviado.height ?: 0,
                        ),
                    )
                }
                .onSuccess {
                    _state.update { e -> e.copy(subindo = false) }
                    recarregar()
                }
                .onFailure { e ->
                    _state.update { it.copy(subindo = false, aviso = e.message ?: "Não foi possível subir a figurinha") }
                }
        }
    }

    fun apagar(figurinhaId: String) {
        viewModelScope.launch {
            runCatching { stickerApi.apagar(orbitaId, figurinhaId) }
                .onSuccess { _state.update { e -> e.copy(figurinhas = e.figurinhas.filterNot { it.id == figurinhaId }) } }
                .onFailure { _state.update { it.copy(aviso = "Não foi possível apagar") } }
        }
    }

    fun esquecerAviso() = _state.update { it.copy(aviso = null) }
}
