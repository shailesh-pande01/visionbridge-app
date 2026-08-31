package com.example.visionbridge.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.AuthApi
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Success(val user: User) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authApi = AuthApi(application)
    private val sessionManager = SessionManager.getInstance(application)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter both username and password.")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                authApi.login(username, password)
            }
            when (result) {
                is ApiResult.Success -> {
                    sessionManager.saveUser(result.value)
                    _uiState.value = AuthUiState.Success(result.value)
                }
                is ApiResult.Failure -> {
                    _uiState.value = AuthUiState.Error(result.error.userMessage)
                }
            }
        }
    }

    fun register(name: String, username: String, password: String, role: String) {
        if (name.isBlank() || username.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all fields.")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                authApi.register(name, username, password, role)
            }
            when (result) {
                is ApiResult.Success -> {
                    sessionManager.saveUser(result.value)
                    _uiState.value = AuthUiState.Success(result.value)
                }
                is ApiResult.Failure -> {
                    _uiState.value = AuthUiState.Error(result.error.userMessage)
                }
            }
        }
    }

    fun resetError() {
        _uiState.value = AuthUiState.Idle
    }
}
