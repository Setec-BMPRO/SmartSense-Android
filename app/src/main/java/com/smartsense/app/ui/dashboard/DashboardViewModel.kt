package com.smartsense.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SensorRepository,
    userPreferences: UserPreferences
) : ViewModel() {

    val sensors: StateFlow<List<Sensor>> = repository.getSensors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unitSystem: StateFlow<UnitSystem> = userPreferences.unitSystem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UnitSystem.METRIC)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        _isRefreshing.value = false
    }
}
