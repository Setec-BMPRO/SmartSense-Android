package com.smartsense.app


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.domain.firebase.AuthRepository
import com.smartsense.app.domain.model.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    // --- UI State ---
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkAuthState()
    }

    /**
     * Determines if the user is authenticated via Firebase AND
     * marked as registered in our local DataStore.
     */
     fun checkAuthState() {
        viewModelScope.launch {
            // Combine Firebase check with local Preferences check
            userPreferences.isSignedIn.collectLatest { isSignedIn ->
                if (isSignedIn) {
                    _uiState.value = MainUiState.Authenticated
                } else {
                    _uiState.value = MainUiState.Unauthenticated
                }
            }
        }
    }
}

/**
 * Represents the high-level state of the App lifecycle
 */
sealed class MainUiState {
    object Loading : MainUiState() // Add this
    object Authenticated : MainUiState()
    object Unauthenticated : MainUiState()
}