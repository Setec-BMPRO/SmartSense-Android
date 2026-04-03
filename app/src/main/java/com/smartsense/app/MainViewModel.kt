package com.smartsense.app


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.repository.Sensor1Repository
import com.smartsense.app.domain.firebase.AuthRepository
import com.smartsense.app.domain.model.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sensorRepository: Sensor1Repository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    // --- UI State ---
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // This turns the Firebase Listener into a Coroutine Flow
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)

        // Clean up the listener when the Flow is cancelled
        awaitClose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }

    init {
        checkAuthState()
    }

    /**
     * Determines if the user is authenticated via Firebase AND
     * marked as registered in our local DataStore.
     */
    fun checkAuthState() {
        viewModelScope.launch {
            combine(
                userPreferences.isSignedIn,
                userPreferences.uploadSensorData, // Your new sync flag
                authStateFlow // Firebase session
            ) { isSignedIn, isSyncEnabled, firebaseUser ->

                val isAuthenticated = isSignedIn && firebaseUser != null

                if (isAuthenticated) {
                    _uiState.value = MainUiState.Authenticated

                    // Only trigger the background worker if the user allows it
                    if (isSyncEnabled) {
                        Log.d("Sync", "Auth valid and Sync enabled: Triggering Worker")
                        sensorRepository.triggerSync()
                    } else {
                        Log.d("Sync", "Sync is disabled by user in Preferences.")
                    }
                } else {
                    _uiState.value = MainUiState.Unauthenticated
                }
            }.collectLatest {
                // The logic is handled inside the combine block
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