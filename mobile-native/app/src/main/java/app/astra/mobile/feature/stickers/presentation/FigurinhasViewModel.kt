package app.astra.mobile.feature.stickers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.StickerApi
import app.astra.mobile.core.network.dto.ServerStickerDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FigurinhasViewModel @Inject constructor(
    private val stickerApi: StickerApi,
) : ViewModel() {

    private val _figurinhas = MutableStateFlow<List<ServerStickerDto>>(emptyList())
    val figurinhas = _figurinhas.asStateFlow()

    private val _carregando = MutableStateFlow(false)
    val carregando = _carregando.asStateFlow()

    private var jaPedida: String? = null

    fun carregar(orbitaId: String) {
        if (orbitaId.isBlank() || jaPedida == orbitaId) return
        jaPedida = orbitaId
        _carregando.value = true
        viewModelScope.launch {
            _figurinhas.value = runCatching { stickerApi.listar(orbitaId).stickers }.getOrDefault(emptyList())
            _carregando.value = false
        }
    }
}
