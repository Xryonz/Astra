package app.astra.mobile.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.upload.ImageEncoder
import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.feature.profile.domain.model.Profile
import app.astra.mobile.feature.profile.domain.model.UserStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val userRepository: UserRepository,
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
        status = p.status,
        avatarUrl = p.avatarUrl.orEmpty(), origAvatarUrl = p.avatarUrl.orEmpty(),
        bannerUrl = p.bannerUrl.orEmpty(), origBannerUrl = p.bannerUrl.orEmpty(),
        bio = p.bio.orEmpty(), origBio = p.bio.orEmpty(),
        pronouns = p.pronouns.orEmpty(), origPronouns = p.pronouns.orEmpty(),
        bannerColor = p.bannerColor.orEmpty(), origBannerColor = p.bannerColor.orEmpty(),
    )

    fun onStatus(v: UserStatus) {
        if (v == _state.value.status) return
        _state.update { it.copy(status = v) }
        viewModelScope.launch { userRepository.setStatus(v) }
    }

    fun onBio(v: String) = _state.update { it.copy(bio = v, saved = false, error = null) }
    fun onPronouns(v: String) = _state.update { it.copy(pronouns = v, saved = false, error = null) }
    fun onBannerColor(v: String) = _state.update { it.copy(bannerColor = v, saved = false, error = null) }

    fun uploadAvatar(bytes: ByteArray, mime: String) {
        _state.update { it.copy(uploadingAvatar = true, error = null, saved = false) }
        viewModelScope.launch {
            ImageEncoder.toDataUri(bytes, mime, AVATAR_DIM, AVATAR_GIF_MAX)
                .onSuccess { uri -> _state.update { it.copy(uploadingAvatar = false, avatarUrl = uri) } }
                .onFailure { e -> _state.update { it.copy(uploadingAvatar = false, error = e.message) } }
        }
    }

    fun uploadBanner(bytes: ByteArray, mime: String) {
        _state.update { it.copy(uploadingBanner = true, error = null, saved = false) }
        viewModelScope.launch {
            ImageEncoder.toDataUri(bytes, mime, BANNER_DIM, BANNER_GIF_MAX)
                .onSuccess { uri -> _state.update { it.copy(uploadingBanner = false, bannerUrl = uri) } }
                .onFailure { e -> _state.update { it.copy(uploadingBanner = false, error = e.message) } }
        }
    }

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
        const val AVATAR_DIM = 512
        const val BANNER_DIM = 1280
        const val AVATAR_GIF_MAX = 4_500_000
        const val BANNER_GIF_MAX = 5_500_000
    }
}
