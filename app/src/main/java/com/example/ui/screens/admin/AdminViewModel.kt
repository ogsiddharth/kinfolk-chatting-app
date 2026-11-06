package com.example.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AuditLog
import com.example.data.model.Invitation
import com.example.data.model.UserProfile
import com.example.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(
    private val adminRepository: AdminRepository,
    val ownerId: String
) : ViewModel() {

    val invitations: StateFlow<List<Invitation>> = adminRepository.observeInvitations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val members: StateFlow<List<UserProfile>> = adminRepository.observeAllMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = adminRepository.observeAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _createdInvite = MutableStateFlow<String?>(null)
    val createdInvite: StateFlow<String?> = _createdInvite.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearCreatedInvite() {
        _createdInvite.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun generateInvitation(durationDays: Int = 7) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = adminRepository.createInvitation(ownerId, durationDays)
            _isLoading.value = false
            result.onSuccess { code ->
                _createdInvite.value = code
                _message.value = "New invitation code generated: $code"
            }.onFailure { e ->
                _message.value = "Failed to create invitation: ${e.message}"
            }
        }
    }

    fun revokeInvitation(code: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = adminRepository.revokeInvitation(code, ownerId)
            _isLoading.value = false
            result.onSuccess {
                _message.value = "Invitation $code has been revoked."
            }.onFailure { e ->
                _message.value = "Error revoking invitation: ${e.message}"
            }
        }
    }

    fun toggleLockMember(userId: String, currentLock: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = adminRepository.toggleLockMember(userId, !currentLock, ownerId)
            _isLoading.value = false
            result.onSuccess {
                _message.value = if (!currentLock) "Account has been locked." else "Account has been unlocked."
            }.onFailure { e ->
                _message.value = "Failed to update account status: ${e.message}"
            }
        }
    }
}
