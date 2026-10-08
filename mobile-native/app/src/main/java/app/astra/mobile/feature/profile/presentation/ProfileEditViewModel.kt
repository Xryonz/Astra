package app.astra.mobile.feature.profile.presentation

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.FriendsApi
import app.astra.mobile.core.network.dto.CustomStatusRequest
import app.astra.mobile.core.upload.ImageEncoder
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.feature.profile.domain.model.Profile
import app.astra.mobile.ui.components.PROPORCAO_DO_BANNER_DO_PERFIL
import app.astra.mobile.ui.components.zoomQueCobre
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val friendsApi: FriendsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileEditUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.me()
                .onSuccess { p -> _state.update { applyProfile(it, p) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    private fun applyProfile(s: ProfileEditUiState, p: Profile) = s.copy(
        loading = false,
        displayName = p.displayName,
        username = p.username,
        avatarUrl = p.avatarUrl.orEmpty(), origAvatarUrl = p.avatarUrl.orEmpty(),
        bannerUrl = p.bannerUrl.orEmpty(), origBannerUrl = p.bannerUrl.orEmpty(),
        bio = p.bio.orEmpty(), origBio = p.bio.orEmpty(),
        pronouns = p.pronouns.orEmpty(), origPronouns = p.pronouns.orEmpty(),
        bannerColor = p.bannerColor.orEmpty(), origBannerColor = p.bannerColor.orEmpty(),
        profileTheme = p.profileTheme.orEmpty(), origProfileTheme = p.profileTheme.orEmpty(),
        bannerPositionY = p.bannerPositionY.coerceIn(0, 100), origBannerPositionY = p.bannerPositionY.coerceIn(0, 100),
        bannerScale = p.bannerScale.coerceIn(ZOOM_MIN, ZOOM_MAX), origBannerScale = p.bannerScale.coerceIn(ZOOM_MIN, ZOOM_MAX),
        displayFont = p.displayFont, origDisplayFont = p.displayFont,
        customStatus = p.customStatus.orEmpty(), origCustomStatus = p.customStatus.orEmpty(),
    )

    fun onBio(v: String) = _state.update { it.copy(bio = v, saved = false, error = null) }
    fun onCustomStatus(v: String) = _state.update { it.copy(customStatus = v.take(128), saved = false, error = null) }
    fun onPronouns(v: String) = _state.update { it.copy(pronouns = v, saved = false, error = null) }
    fun onCorDoPerfil(css: String) = _state.update { it.copy(bannerColor = css, profileTheme = css, saved = false, error = null) }
    fun onBannerPositionY(v: Int) = _state.update { it.copy(bannerPositionY = v.coerceIn(0, 100), saved = false, error = null) }
    fun onBannerScale(v: Int) = _state.update { it.copy(bannerScale = v.coerceIn(ZOOM_MIN, ZOOM_MAX), saved = false, error = null) }
    fun onDisplayFont(v: String) = _state.update { it.copy(displayFont = v, saved = false, error = null) }

    fun uploadAvatarRecortado(origem: Bitmap, recorte: Rect) {
        _state.update { it.copy(uploadingAvatar = true, error = null, saved = false) }
        viewModelScope.launch {
            ImageEncoder.cropToDataUri(origem, recorte, AVATAR_DIM)
                .onSuccess { uri -> _state.update { it.copy(uploadingAvatar = false, avatarUrl = uri) } }
                .onFailure { e -> _state.update { it.copy(uploadingAvatar = false, error = e.message) } }
        }
    }

    fun uploadAvatarAnimado(bytes: ByteArray, mime: String, origem: Bitmap, recorte: Rect) {
        if (bytes.size > AVATAR_GIF_MAX) {
            _state.update { it.copy(error = "GIF muito grande — escolha um menor.", saved = false) }
            return
        }
        _state.update { it.copy(uploadingAvatar = true, error = null, saved = false) }
        viewModelScope.launch {
            val fator = (ImageEncoder.larguraOriginal(bytes) ?: origem.width).toFloat() / origem.width
            val imagem = withContext(Dispatchers.Default) {
                "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
            userRepository.recortarFoto(
                imagem,
                x = (recorte.left * fator).roundToInt().coerceAtLeast(0),
                y = (recorte.top * fator).roundToInt().coerceAtLeast(0),
                lado = (recorte.width() * fator).roundToInt().coerceAtLeast(1),
            )
                .onSuccess { url -> _state.update { it.copy(uploadingAvatar = false, avatarUrl = url) } }
                .onFailure { e -> _state.update { it.copy(uploadingAvatar = false, error = e.message) } }
        }
    }

    fun uploadBanner(bytes: ByteArray, mime: String) {
        _state.update { it.copy(uploadingBanner = true, error = null, saved = false) }
        viewModelScope.launch {
            val zoom = ImageEncoder.aspectRatio(bytes)?.let { zoomQueCobre(it, PROPORCAO_DO_BANNER_DO_PERFIL) } ?: 100
            ImageEncoder.toDataUri(bytes, mime, BANNER_DIM, BANNER_GIF_MAX)
                .onSuccess { uri ->
                    _state.update { it.copy(uploadingBanner = false, bannerUrl = uri, bannerPositionY = 50, bannerScale = zoom) }
                }
                .onFailure { e -> _state.update { it.copy(uploadingBanner = false, error = e.message) } }
        }
    }

    fun uploadBannerRecortado(origem: Bitmap, recorte: Rect) {
        _state.update { it.copy(uploadingBanner = true, error = null, saved = false) }
        viewModelScope.launch {
            ImageEncoder.cropToDataUri(origem, recorte, BANNER_DIM)
                .onSuccess { uri ->
                    _state.update { it.copy(uploadingBanner = false, bannerUrl = uri, bannerPositionY = 50, bannerScale = 100) }
                }
                .onFailure { e -> _state.update { it.copy(uploadingBanner = false, error = e.message) } }
        }
    }

    fun removeBanner() = _state.update { it.copy(bannerUrl = "", saved = false, error = null) }

    fun desfazer() = _state.update {
        it.copy(
            avatarUrl = it.origAvatarUrl,
            bannerUrl = it.origBannerUrl,
            bio = it.origBio,
            pronouns = it.origPronouns,
            bannerColor = it.origBannerColor,
            profileTheme = it.origProfileTheme,
            bannerPositionY = it.origBannerPositionY,
            bannerScale = it.origBannerScale,
            displayFont = it.origDisplayFont,
            customStatus = it.origCustomStatus,
            saved = false,
            error = null,
        )
    }

    fun esquecerSalvo() = _state.update { it.copy(saved = false) }

    fun limparErro() = _state.update { it.copy(error = null) }

    fun save() {
        val s = _state.value
        if (s.saving || !s.dirty) return
        _state.update { it.copy(saving = true, error = null, saved = false) }
        viewModelScope.launch {
            val recado = s.customStatus.trim()
            if (recado != s.origCustomStatus) {
                try {
                    friendsApi.setCustomStatus(CustomStatusRequest(recado))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(saving = false, error = "Não foi possível salvar o recado") }
                    return@launch
                }
            }
            userRepository.updateProfile(
                bio = s.bio.takeIf { it != s.origBio },
                avatarUrl = s.avatarUrl.takeIf { it != s.origAvatarUrl },
                bannerUrl = s.bannerUrl.takeIf { it != s.origBannerUrl },
                bannerColor = s.bannerColor.takeIf { it != s.origBannerColor },
                pronouns = s.pronouns.takeIf { it != s.origPronouns },
                profileTheme = s.profileTheme.takeIf { it != s.origProfileTheme },
                bannerPositionY = s.bannerPositionY.takeIf { it != s.origBannerPositionY },
                bannerScale = s.bannerScale.takeIf { it != s.origBannerScale },
                displayFont = s.displayFont.takeIf { it != s.origDisplayFont },
            )
                .onSuccess { p -> _state.update { applyProfile(it, p).copy(saved = true, saving = false, customStatus = recado, origCustomStatus = recado) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }

    private companion object {
        const val AVATAR_DIM = 512
        const val BANNER_DIM = 1280
        const val AVATAR_GIF_MAX = 4_500_000
        const val BANNER_GIF_MAX = 5_500_000
        const val ZOOM_MIN = 50
        const val ZOOM_MAX = 300
    }
}
