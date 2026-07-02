package app.astra.mobile.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.AuthApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authApi: AuthApi,
) : ViewModel() {

    // Fire-and-forget: se a rede falhar, o gate local (consumeOnboarding) ja
    // impediu re-exibicao nesta sessao; na proxima o backend mostra de novo.
    fun markDone() {
        viewModelScope.launch {
            try { authApi.markOnboarded() } catch (_: Exception) {}
        }
    }
}
