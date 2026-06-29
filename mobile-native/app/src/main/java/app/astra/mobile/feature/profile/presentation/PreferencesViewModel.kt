package app.astra.mobile.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.data.DensityPref
import app.astra.mobile.core.data.FontSizePref
import app.astra.mobile.core.data.PreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val store: PreferencesStore,
) : ViewModel() {
    fun setReduceMotion(v: Boolean) { viewModelScope.launch { store.setReduceMotion(v) } }
    fun setHaptics(v: Boolean) { viewModelScope.launch { store.setHaptics(v) } }
    fun setFontSize(v: FontSizePref) { viewModelScope.launch { store.setFontSize(v) } }
    fun setDensity(v: DensityPref) { viewModelScope.launch { store.setDensity(v) } }
}
