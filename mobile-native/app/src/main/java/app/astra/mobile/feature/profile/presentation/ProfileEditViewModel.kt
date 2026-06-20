package app.astra.mobile.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        bio = p.bio.orEmpty(), origBio = p.bio.orEmpty(),
        pronouns = p.pronouns.orEmpty(), origPronouns = p.pronouns.orEmpty(),
        bannerColor = p.bannerColor.orEmpty(), origBannerColor = p.bannerColor.orEmpty(),
    )

    fun onAvatarUrl(v: String) = _state.update { it.copy(avatarUrl = v, saved = false, error = null) }
    fun onBio(v: String) = _state.update { it.copy(bio = v, saved = false, error = null) }
    fun onPronouns(v: String) = _state.update { it.copy(pronouns = v, saved = false, error = null) }
    fun onBannerColor(v: String) = _state.update { it.copy(bannerColor = v, saved = false, error = null) }

    fun save() {
        val s = _state.value
        if (s.saving || !s.dirty) return
        _state.update { it.copy(saving = true, error = null, saved = false) }
        viewModelScope.launch {
            userRepository.updateProfile(
                bio = s.bio.takeIf { it != s.origBio },
                avatarUrl = s.avatarUrl.takeIf { it != s.origAvatarUrl },
                bannerColor = s.bannerColor.takeIf { it != s.origBannerColor },
                pronouns = s.pronouns.takeIf { it != s.origPronouns },
            )
                .onSuccess { p -> _state.update { applyProfile(it, p).copy(saved = true, saving = false) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }
}
