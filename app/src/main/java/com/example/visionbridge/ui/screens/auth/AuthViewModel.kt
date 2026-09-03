package com.example.visionbridge.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.R
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.AuthApi
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.data.User
import com.example.visionbridge.utils.LocaleHelper
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

    private fun getLocalizedContext(): android.content.Context {
        return LocaleHelper.wrapContext(getApplication(), sessionManager.language.value)
    }

    fun login(identifier: String, password: String) {
        val cleanIdentifier = identifier.trim()
        val cleanPassword = password.trim()

        if (cleanIdentifier.isBlank() || cleanPassword.isBlank()) {
            _uiState.value = AuthUiState.Error(getLocalizedContext().getString(R.string.auth_err_both_fields))
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                authApi.login(cleanIdentifier, cleanPassword)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = AuthUiState.Success(result.value)
                }
                is ApiResult.Failure -> {
                    _uiState.value = AuthUiState.Error(result.error.userMessage)
                }
            }
        }
    }

    fun register(
        name: String,
        username: String,
        password: String,
        confirmPassword: String,
        role: String,
        email: String? = null
    ) {
        val cleanName = name.trim()
        val cleanUsername = username.trim()
        val cleanEmail = email?.trim()?.takeIf { it.isNotBlank() }
        val localizedCtx = getLocalizedContext()

        if (cleanName.isBlank() || cleanUsername.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            _uiState.value = AuthUiState.Error(localizedCtx.getString(R.string.auth_err_all_fields))
            return
        }

        if (password.length < 6) {
            _uiState.value = AuthUiState.Error(localizedCtx.getString(R.string.auth_err_password_len))
            return
        }

        if (password != confirmPassword) {
            _uiState.value = AuthUiState.Error(localizedCtx.getString(R.string.auth_err_password_match))
            return
        }

        if (cleanEmail != null && !cleanEmail.contains("@")) {
            _uiState.value = AuthUiState.Error(localizedCtx.getString(R.string.auth_err_email_invalid))
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                authApi.register(
                    name = cleanName,
                    username = cleanUsername,
                    password = password,
                    role = role,
                    emailInput = cleanEmail
                )
            }
            when (result) {
                is ApiResult.Success -> {
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
