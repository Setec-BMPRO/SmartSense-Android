package com.smartsense.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.smartsense.app.data.local.entity.toSensor
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.repository.SensorRepository

import com.smartsense.app.domain.firebase.AuthRepository
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.SensorLocation
import com.smartsense.app.domain.model.SensorUIModel
import com.smartsense.app.domain.usecase.SensorScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val userPreferences: UserPreferences,
    private val sensorScanUseCase: SensorScanUseCase,
    private val sensorRepository: SensorRepository
) : ViewModel() {

    // --- Authentication State Flows ---
    private val _loginState = MutableStateFlow<Result<FirebaseUser>?>(null)
    val loginState: StateFlow<Result<FirebaseUser>?> = _loginState

    private val _signUpState = MutableStateFlow<Result<FirebaseUser>?>(null)
    val signUpState: StateFlow<Result<FirebaseUser>?> = _signUpState

    private val _resetEmailState = MutableStateFlow<Result<Unit>?>(null)
    val resetEmailState: StateFlow<Result<Unit>?> = _resetEmailState

    private val _updatePasswordState = MutableStateFlow<Result<Unit>?>(null)
    val updatePasswordState: StateFlow<Result<Unit>?> = _updatePasswordState

    // --- Account Management State Flows ---
    private val _signOutState = MutableStateFlow<Boolean?>(null)
    val signOutState: StateFlow<Boolean?> = _signOutState

    private val _deleteAccountState = MutableStateFlow<Result<Unit>?>(null)
    val deleteAccountState: StateFlow<Result<Unit>?> = _deleteAccountState

    // This Flow automatically filters out 'DELETED' sensors via the DAO query we wrote
    val registeredSensors: StateFlow<List<Sensor>> = sensorScanUseCase.getAllRegisteredSensors()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    // --- Primary Actions ---

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = repository.signIn(email, password)
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _signUpState.value = repository.signUp(email, password)
        }
    }

    fun sendPasswordReset(email: String) {
        executeResettingState(_resetEmailState) {
            repository.sendPasswordReset(email)
        }
    }

    fun updatePassword(code: String, newPassword: String) {
        executeResettingState(_updatePasswordState) {
            repository.confirmPasswordReset(code, newPassword)
        }
    }

    // --- Destructive Actions ---

    fun signOut() {
        viewModelScope.launch {
            try {
                repository.signOut()
                userPreferences.setIsSignedIn(false)

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
                withTimeout(15000) { repository.deleteAccount() }
            }.onSuccess { result ->
                result.onSuccess { userPreferences.setIsSignedIn(false) }
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


    fun unregisterSensor(sensorAddress: String) {
        viewModelScope.launch {
            sensorScanUseCase.unregisterSensor(sensorAddress,userPreferences.uploadSensorData.first())
        }
    }
    fun triggerSync() {
        viewModelScope.launch {
            sensorScanUseCase.triggerSync()
        }
    }
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
        sensorRepository.getAllRegisteredSensors(),    // Flow<List<Sensor>> from Room
        refreshTrigger.flatMapLatest { repository.getRemoteSensorsFlow() }
    ) { localList, cloudList ->
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
            when (uiModel.location) {
                SensorLocation.LOCAL_ONLY -> {
                    // Just remove from Room
                    sensorScanUseCase.unregisterSensor(uiModel.sensor.address, false)
                }
                SensorLocation.CLOUD_ONLY -> {
                    // You'll need a function in repository to delete a specific doc from Firestore
                    // repository.deleteRemoteSensor(uiModel.sensor.address)
                }
                SensorLocation.BOTH -> {
                    // Trigger both or ask user via UI first
                    sensorScanUseCase.unregisterSensor(uiModel.sensor.address, true)
                    // repository.deleteRemoteSensor(uiModel.sensor.address)
                }
            }
        }
    }

    fun refreshWholeList() {
        viewModelScope.launch {
            // 1. Force the Flow to restart by changing the timestamp
            refreshTrigger.value = System.currentTimeMillis()
        }
    }

}