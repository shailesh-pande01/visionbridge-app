package com.example.visionbridge.ui.screens.emergency

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.EmergencyApi
import com.example.visionbridge.data.EmergencyEvent
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SosUiState {
    data class Countdown(val secondsLeft: Int) : SosUiState
    object Sending : SosUiState
    data class Active(val event: EmergencyEvent) : SosUiState
    object Cancelled : SosUiState
    data class Error(val message: String) : SosUiState
}

class EmergencySosViewModel(application: Application) : AndroidViewModel(application) {

    private val emergencyApi = EmergencyApi(application)
    private val sessionManager = SessionManager.getInstance(application)

    private val _uiState = MutableStateFlow<SosUiState>(SosUiState.Countdown(5))
    val uiState: StateFlow<SosUiState> = _uiState.asStateFlow()

    private var isCancelled = false
    private var activeEvent: EmergencyEvent? = null

    fun startCountdown() {
        isCancelled = false
        _uiState.value = SosUiState.Countdown(5)
        viewModelScope.launch {
            for (sec in 5 downTo 1) {
                if (isCancelled) return@launch
                _uiState.value = SosUiState.Countdown(sec)
                delay(1000)
            }
            if (!isCancelled) {
                sendEmergencyAlert()
            }
        }
    }

    fun sendEmergencyAlert() {
        if (_uiState.value is SosUiState.Sending || _uiState.value is SosUiState.Active) return

        _uiState.value = SosUiState.Sending
        viewModelScope.launch {
            val user = sessionManager.currentUser.value
            if (user == null || user.id.isBlank()) {
                _uiState.value = SosUiState.Error("You must be signed in to trigger SOS.")
                return@launch
            }
            val userId = user.id

            val (lat, lng) = LocationHelper.getCurrentLocation(getApplication())

            val result = withContext(Dispatchers.IO) {
                emergencyApi.triggerSOS(userId, lat, lng)
            }

            when (result) {
                is ApiResult.Success -> {
                    activeEvent = result.value
                    _uiState.value = SosUiState.Active(result.value)
                }
                is ApiResult.Failure -> {
                    _uiState.value = SosUiState.Error(result.error.userMessage)
                }
            }
        }
    }

    fun cancelSos() {
        isCancelled = true
        _uiState.value = SosUiState.Cancelled
    }

    fun endEmergency() {
        val event = activeEvent
        if (event != null) {
            viewModelScope.launch(Dispatchers.IO) {
                emergencyApi.endSOS(event.id)
            }
        }
        _uiState.value = SosUiState.Cancelled
    }
}
