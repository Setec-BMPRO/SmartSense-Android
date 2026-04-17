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
import com.smartsense.app.domain.usecase.SharedUseCase
import com.smartsense.app.domain.usecase.SharedUseCase.Companion.SYNC_WORK_NAME
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
    private val sharedUseCase: SharedUseCase,
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
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(SYNC_WORK_NAME)

    init {
        checkAuthState()
    }

    /**
     * Determines if the user is authenticated via Firebase AND
     * marked as registered in our local DataStore.
     */
    fun checkAuthState() {
        // ... (existing code)
    }

    fun signOut() {
        viewModelScope.launch {
            FirebaseAuth.getInstance().signOut()
            userPreferences.setIsSignedIn(false)
            userPreferences.setUserEmail(null)
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