package com.smartsense.app


import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.usecase.ScanUseCase
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
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context,
    private val scanUseCase: ScanUseCase
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
                // Log the raw inputs before processing
                Timber.d("📥 Raw Input - SignedIn: $isSignedIn, SyncEnabled: $isSyncEnabled, UID: ${firebaseUser?.uid}")

                Triple(isSignedIn && firebaseUser != null, isSyncEnabled, firebaseUser?.uid)
            }
                .distinctUntilChanged()
                .collectLatest { (isAuthenticated, isSyncEnabled, uid) ->
                    Timber.i("🔄 Combined State - Auth: $isAuthenticated, Sync: $isSyncEnabled, UID: $uid")

                    // 1. Update UI State
                    _uiState.value = if (isAuthenticated) {
                        Timber.d("✅ UI State: Authenticated")
                        MainUiState.Authenticated
                    } else {
                        Timber.d("❌ UI State: Unauthenticated")
                        MainUiState.Unauthenticated
                    }

                    // 2. 🛡️ THE GUARD
                    if (isAuthenticated && isSyncEnabled && uid != null) {
                        Timber.i("🚀 SYNC TRIGGERED for UID: $uid")
                        scanUseCase.triggerSync()
                    } else {
                        // Log exactly why it skipped for debugging
                        val reason = when {
                            !isAuthenticated -> "User not authenticated"
                            !isSyncEnabled -> "Sync toggle is OFF"
                            uid == null -> "UID is null"
                            else -> "Unknown condition"
                        }
                        Timber.w("🛑 Sync Skipped: $reason")
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