package app.astra.mobile.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.upload.ImageUploader
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.feature.profile.domain.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val uploader: ImageUploader,
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
    )

    fun onBio(v: String) = _state.update { it.copy(bio = v, saved = false, error = null) }
    fun onPronouns(v: String) = _state.update { it.copy(pronouns = v, saved = false, error = null) }
    fun onBannerColor(v: String) = _state.update { it.copy(bannerColor = v, saved = false, error = null) }

    // Upload de avatar (limite 5MB, igual Capacitor). Preenche o campo; o
    // SALVAR persiste. Preview atualiza na hora.
    fun uploadAvatar(bytes: ByteArray, mime: String, filename: String) {
        if (bytes.size > MAX_AVATAR) {
            _state.update { it.copy(error = "Avatar maior que 5MB.") }
            return
        }
        _state.update { it.copy(uploadingAvatar = true, error = null, saved = false) }
        viewModelScope.launch {
            uploader.upload(bytes, mime, filename)
                .onSuccess { url -> _state.update { it.copy(uploadingAvatar = false, avatarUrl = url) } }
                .onFailure { e -> _state.update { it.copy(uploadingAvatar = false, error = e.message) } }
        }
    }

    // Upload de banner (limite 8MB, animado ok).
    fun uploadBanner(bytes: ByteArray, mime: String, filename: String) {
        if (bytes.size > MAX_BANNER) {
            _state.update { it.copy(error = "Banner maior que 8MB.") }
            return
        }
        _state.update { it.copy(uploadingBanner = true, error = null, saved = false) }
        viewModelScope.launch {
            uploader.upload(bytes, mime, filename)
                .onSuccess { url -> _state.update { it.copy(uploadingBanner = false, bannerUrl = url) } }
                .onFailure { e -> _state.update { it.copy(uploadingBanner = false, error = e.message) } }
        }
    }

    // "" limpa o banner no backend (volta pra constelacao-assinatura do nome).
    fun removeBanner() = _state.update { it.copy(bannerUrl = "", saved = false, error = null) }

    fun save() {
        val s = _state.value
        if (s.saving || !s.dirty) return
        _state.update { it.copy(saving = true, error = null, saved = false) }
        viewModelScope.launch {
            userRepository.updateProfile(
                bio = s.bio.takeIf { it != s.origBio },
                avatarUrl = s.avatarUrl.takeIf { it != s.origAvatarUrl },
                bannerUrl = s.bannerUrl.takeIf { it != s.origBannerUrl },
                bannerColor = s.bannerColor.takeIf { it != s.origBannerColor },
                pronouns = s.pronouns.takeIf { it != s.origPronouns },
            )
                .onSuccess { p -> _state.update { applyProfile(it, p).copy(saved = true, saving = false) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }

    private companion object {
        const val MAX_AVATAR = 5 * 1024 * 1024
        const val MAX_BANNER = 8 * 1024 * 1024
    }
}
