package com.smartsense.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import com.smartsense.app.data.local.entity.toSensor
import com.smartsense.app.data.preferences.UserPreferences

import com.smartsense.app.domain.firebase.AuthRepository
import com.smartsense.app.domain.model.SensorLocation
import com.smartsense.app.domain.model.SensorUIModel
import com.smartsense.app.domain.model.UiState
import com.smartsense.app.domain.network.NetworkConnectivityObserver
import com.smartsense.app.domain.usecase.AccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferences,
    private val useCase: AccountUseCase,
    private val connectivityObserver: NetworkConnectivityObserver
) : ViewModel() {

    // --- Authentication State Flows ---
    private val _loginState = MutableStateFlow<Result<FirebaseUser>?>(null)
    val loginState: StateFlow<Result<FirebaseUser>?> = _loginState

    private val _signUpState = MutableStateFlow<Result<FirebaseUser>?>(null)
    val signUpState: StateFlow<Result<FirebaseUser>?> = _signUpState

    private val _forgotPasswordState = MutableStateFlow<Result<Unit>?>(null)
    val forgotPasswordState: StateFlow<Result<Unit>?> = _forgotPasswordState

    private val _updatePasswordState = MutableStateFlow<Result<Unit>?>(null)
    val updatePasswordState: StateFlow<Result<Unit>?> = _updatePasswordState

    val userEmail: StateFlow<String?> = userPreferences.userEmail
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keeps flow alive for 5s after UI stops
            initialValue = null
        )

    // --- Account Management State Flows ---
    private val _signOutState = MutableStateFlow<Boolean?>(null)
    val signOutState: StateFlow<Boolean?> = _signOutState

    private val _deleteAccountState = MutableStateFlow<Result<Unit>?>(null)
    val deleteAccountState: StateFlow<Result<Unit>?> = _deleteAccountState

    // 1. Private MutableStateFlow
    private val _removeSensorUiState = MutableStateFlow(UiState())
    // 2. Public Read-only StateFlow for the UI
    val removeSensorUiState = _removeSensorUiState.asStateFlow()


    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = authRepository.signIn(email, password)
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _signUpState.value = authRepository.signUp(email, password)
        }
    }

    fun forgotPassword(email: String) {
        executeResettingState(_forgotPasswordState) {
            authRepository.sendPasswordReset(email)
        }
    }

//    fun updatePassword(code: String, newPassword: String) {
//        executeResettingState(_updatePasswordState) {
//            authRepository.confirmPasswordReset(code, newPassword)
//        }
//    }

    // --- Destructive Actions ---

    fun signOut() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
                userPreferences.setIsSignedIn(false)
                //userPreferences.setUploadSensorData(false)
                // Optional network cleanup with safety timeout
                withTimeoutOrNull(5000) { /* repository.unregisterPushToken() */ }
            } catch (e: Exception) {
                userPreferences.setIsSignedIn(false)
            } finally {
                _signOutState.value = true
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _deleteAccountState.value = null // Show loading
            runCatching {
                withTimeout(15000) { authRepository.deleteAccount() }
            }.onSuccess { result ->
                result.onSuccess {
                    doAfterDeleteAccountSuccess()
                }
                _deleteAccountState.value = result
            }.onFailure { e ->
                val errorMessage = if (e is TimeoutCancellationException) {
                    "Network timeout. Please check your connection."
                } else e.message ?: "An unexpected error occurred"

                _deleteAccountState.value = Result.failure(Exception(errorMessage))
            }
        }
    }

    // --- Helper & State Management ---

    private fun doAfterDeleteAccountSuccess(){
        viewModelScope.launch {
            //userPreferences.setUploadSensorData(false)
            useCase.resetLocalDataForNewAccount()
            userPreferences.setIsSignedIn(false)
        }
    }

    /**
     * Common pattern: Nulls out state before executing a task to ensure
     * UI observers (like Flow/LiveData) always trigger.
     */
    private fun <T> executeResettingState(
        stateFlow: MutableStateFlow<Result<T>?>,
        block: suspend () -> Result<T>
    ) {
        viewModelScope.launch {
            stateFlow.value = null
            stateFlow.value = block()
        }
    }

    fun updateLoginStatus(isSignedIn: Boolean) {
        viewModelScope.launch { userPreferences.setIsSignedIn(isSignedIn) }
    }

    fun resetLoginState() { _loginState.value = null }
    fun resetSignUpState() { _signUpState.value = null }

    fun resetDeleteAccountState(){
        _deleteAccountState.value = null
    }
    fun resetSignOutState(){
        _signOutState.value=null
    }
    fun resetPasswordResetState(){
        _updatePasswordState.value=null
    }

    private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())
    val combinedSensors: Flow<List<SensorUIModel>> = combine(
        useCase.getAllRegisteredSensors(),    // Flow<List<Sensor>> from Room
        refreshTrigger.flatMapLatest { authRepository.getRemoteSensorsFlow() },
        connectivityObserver.status
    ) { localList, cloudList, connectionStatus ->
        Timber.tag("SyncLog").d("📊 Combine Triggered: Local=${localList.size}, Cloud=${cloudList.size}")
        // Get every unique address from both lists
        val allAddresses = (localList.map { it.address } + cloudList.map { it.address }).distinct()

        allAddresses.map { addr ->
            val local = localList.find { it.address == addr }
            val cloud = cloudList.find { it.address == addr }

            when {
                local != null && cloud != null -> SensorUIModel(local, SensorLocation.BOTH)
                local != null -> SensorUIModel(local, SensorLocation.LOCAL_ONLY)
                else -> SensorUIModel(cloud!!.toSensor(), SensorLocation.CLOUD_ONLY)
            }
        }
    }

    fun removeSensor(uiModel: SensorUIModel) {
        viewModelScope.launch {
            // Start Loading & Clear old errors
            _removeSensorUiState.update { it.copy(isDeleting = true, errorMessage = null) }

            try {
                when (uiModel.location) {
                    SensorLocation.LOCAL_ONLY -> {
                        useCase.unregisterSensorTankPermanent(uiModel.sensor.address)
                    }
                    SensorLocation.CLOUD_ONLY -> {
                        val result = authRepository.deleteSensor(uiModel.sensor.address)
                        result.onFailure { error ->
                            _removeSensorUiState.update { it.copy(errorMessage = error.message) }
                        }
                    }
                    SensorLocation.BOTH -> {
                        val result = useCase.unregisterSensor(uiModel.sensor.address, true)
                        result.onSuccess { wasSyncTriggered ->
                            Timber.d("✅ UI: Deletion successful for $uiModel.sensor.address. Sync triggered: $wasSyncTriggered")
                            _removeSensorUiState.update {
                                it.copy(
                                    isDeleting = false,
                                    successMessage = if (wasSyncTriggered) "Device removed & Sync started" else "Device removed locally"
                                )
                            }
                        }.onFailure { error ->
                            val errorMsg = error.message ?: "Unknown Error"
                            Timber.e("❌ UI: Deletion failed for $uiModel.sensor.address. Error: $errorMsg")
                            _removeSensorUiState.update {
                                it.copy(isDeleting = false, errorMessage = errorMsg)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _removeSensorUiState.update { it.copy(errorMessage = e.localizedMessage) }
            } finally {
                // Always hide the loading indicator
                _removeSensorUiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    // Helper to clear messages after they are shown (e.g., after a Toast/Snackbar)
    fun clearMessages() {
        _removeSensorUiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun refreshWholeList() {
        viewModelScope.launch {
            // 1. Force the Flow to restart by changing the timestamp
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

    fun setUploadSensorDataTrue(){
        viewModelScope.launch {
            userPreferences.setUploadSensorData(true)
        }
    }



    fun finalizeForceDelete(credential: AuthCredential) {
        viewModelScope.launch(Dispatchers.IO) { // Run on IO thread for safety
            val result = authRepository.reauthenticateAndDelete(credential)
            result.onSuccess {
                doAfterDeleteAccountSuccess()
                _deleteAccountState.value = Result.success(Unit)
            }.onFailure { error ->
                val errorMessage = if (error is TimeoutCancellationException) {
                    "Network timeout. Please check your connection."
                } else error.message ?: "An unexpected error occurred"
                _deleteAccountState.value = Result.failure(Exception(errorMessage))
            }
        }
    }

    suspend fun setUserEmail(email: String)=userPreferences.setUserEmail(email)


}
