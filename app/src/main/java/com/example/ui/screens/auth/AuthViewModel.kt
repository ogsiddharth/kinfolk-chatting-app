package com.example.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data class LoggedIn(val profile: UserProfile) : AuthUiState
    data class Welcome(val hasOwner: Boolean, val isGoogleAuthenticated: Boolean = false) : AuthUiState
    data object OwnerBootstrap : AuthUiState
    data object JoinWithInvite : AuthUiState
    data object Recovery : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _createdInviteCode = MutableStateFlow<String?>(null)
    val createdInviteCode: StateFlow<String?> = _createdInviteCode.asStateFlow()

    private val _recoveryCode = MutableStateFlow<String?>(null)
    val recoveryCode: StateFlow<String?> = _recoveryCode.asStateFlow()

    private val _actionLoading = MutableStateFlow(false)
    val actionLoading: StateFlow<Boolean> = _actionLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        checkInitialState()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun checkInitialState() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val authUser = Firebase.auth.currentUser
            if (authUser != null) {
                val profile = authRepository.fetchProfileForCurrentAuthUser()
                if (profile != null) {
                    _uiState.value = AuthUiState.LoggedIn(profile)
                } else {
                    val hasOwner = authRepository.hasAnyRegisteredOwner()
                    _uiState.value = AuthUiState.Welcome(hasOwner = hasOwner, isGoogleAuthenticated = true)
                }
            } else {
                _uiState.value = AuthUiState.Welcome(hasOwner = false, isGoogleAuthenticated = false)
            }
        }
    }

    fun onGoogleAuthSuccess() {
        checkInitialState()
    }

    fun navigateToOwnerBootstrap() {
        _errorMessage.value = null
        _uiState.value = AuthUiState.OwnerBootstrap
    }

    fun navigateToJoinWithInvite() {
        _errorMessage.value = null
        _uiState.value = AuthUiState.JoinWithInvite
    }

    fun navigateToRecovery() {
        _errorMessage.value = null
        _uiState.value = AuthUiState.Recovery
    }

    fun navigateToWelcome() {
        _errorMessage.value = null
        checkInitialState()
    }

    fun bootstrapOwner(username: String, displayName: String, pass: String) {
        if (username.isBlank() || displayName.isBlank() || pass.length < 6) {
            _errorMessage.value = "Please enter all fields. Password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            _actionLoading.value = true
            _errorMessage.value = null
            val result = authRepository.bootstrapOwner(username, displayName, pass)
            _actionLoading.value = false
            result.onSuccess { (profile, sisterInvite) ->
                _createdInviteCode.value = sisterInvite
                _uiState.value = AuthUiState.LoggedIn(profile)
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to initialize family space."
            }
        }
    }

    fun joinWithInvite(code: String, username: String, displayName: String, pass: String) {
        if (code.isBlank() || username.isBlank() || displayName.isBlank() || pass.length < 6) {
            _errorMessage.value = "Please complete all fields. Password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            _actionLoading.value = true
            _errorMessage.value = null
            val result = authRepository.joinWithInvitation(code, username, displayName, pass)
            _actionLoading.value = false
            result.onSuccess { (profile, recCode) ->
                _recoveryCode.value = recCode
                _uiState.value = AuthUiState.LoggedIn(profile)
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to join with invitation."
            }
        }
    }

    fun login(username: String, pass: String) {
        if (username.isBlank() || pass.isBlank()) {
            _errorMessage.value = "Please enter your username and password."
            return
        }
        viewModelScope.launch {
            _actionLoading.value = true
            _errorMessage.value = null
            val result = authRepository.login(username, pass)
            _actionLoading.value = false
            result.onSuccess { profile ->
                _uiState.value = AuthUiState.LoggedIn(profile)
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Login failed."
            }
        }
    }

    fun recoverAccount(username: String, code: String, newPass: String) {
        if (username.isBlank() || code.isBlank() || newPass.length < 6) {
            _errorMessage.value = "Please fill all fields. Password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            _actionLoading.value = true
            _errorMessage.value = null
            val result = authRepository.recoverAccount(username, code, newPass)
            _actionLoading.value = false
            result.onSuccess {
                _errorMessage.value = "Password reset successfully! Please log in with your new password."
                checkInitialState()
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Account recovery failed."
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val current = (uiState.value as? AuthUiState.LoggedIn)?.profile
            authRepository.logout(current?.userId)
            Firebase.auth.signOut()
            _uiState.value = AuthUiState.Welcome(hasOwner = false, isGoogleAuthenticated = false)
        }
    }
}
