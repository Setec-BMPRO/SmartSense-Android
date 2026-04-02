package com.smartsense.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.smartsense.app.data.preferences.UserPreferences

import com.smartsense.app.domain.firebase.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val userPreferences: UserPreferences // Made private for consistency
) : ViewModel() {

    // --- State Flows ---

    private val _loginState = MutableStateFlow<Result<FirebaseUser>?>(null)
    val loginState: StateFlow<Result<FirebaseUser>?> = _loginState

    private val _signUpState = MutableStateFlow<Result<FirebaseUser>?>(null)
    val signUpState: StateFlow<Result<FirebaseUser>?> = _signUpState

    private val _resetEmailState = MutableStateFlow<Result<Unit>?>(null)
    val resetEmailState: StateFlow<Result<Unit>?> = _resetEmailState

    private val _updatePasswordState = MutableStateFlow<Result<Unit>?>(null)
    val updatePasswordState: StateFlow<Result<Unit>?> = _updatePasswordState

    // --- Authentication Actions ---

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
        viewModelScope.launch {
            // Nulling out first ensures the Fragment observer triggers even if
            // the result (e.g., failure) is the same as the previous one.
            _resetEmailState.value = null
            _resetEmailState.value = repository.sendPasswordReset(email)
        }
    }

    fun updatePassword(code: String, newPassword: String) {
        viewModelScope.launch {
            _updatePasswordState.value = null
            _updatePasswordState.value = repository.confirmPasswordReset(code, newPassword)
        }
    }

    // --- State Resets ---

    /**
     * Call these from the Fragment after handling a result to prevent
     * duplicate UI events (like Snackbars) on configuration changes.
     */
    fun resetLoginState() {
        _loginState.value = null
    }

    fun resetSignUpState() {
        _signUpState.value = null
    }

    fun resetResetEmailState() {
        _resetEmailState.value = null
    }

    fun resetUpdatePasswordState() {
        _updatePasswordState.value = null
    }
}