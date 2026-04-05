package com.smartsense.app


import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.repository.SensorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sensorRepository: SensorRepository,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context
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

    // Sync
    val syncWorkInfo: LiveData<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("sensor_sync_job")

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
                userPreferences.isSignedIn.distinctUntilChanged(),
                userPreferences.uploadSensorData.distinctUntilChanged(),
                authStateFlow.distinctUntilChanged()
            ) { isSignedIn, isSyncEnabled, firebaseUser ->
                // Create a stable data bundle
                Triple(isSignedIn && firebaseUser != null, isSyncEnabled, firebaseUser?.uid)
            }
                .distinctUntilChanged() // 👈 Only proceed if the Triple actually changes
                .collectLatest { (isAuthenticated, isSyncEnabled, uid) ->

                    // 1. Update UI State regardless
                    _uiState.value = if (isAuthenticated) MainUiState.Authenticated else MainUiState.Unauthenticated

                    // 2. 🛡️ THE GUARD: Only trigger sync if we have a valid User + Sync is ON
                    // This prevents the 'null' or 'false' emissions from starting the worker
                    if (isAuthenticated && isSyncEnabled && uid != null) {
                        Log.d("Sync", "🚀 Valid Sync Triggered for UID: $uid")
                        sensorRepository.triggerSync()
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