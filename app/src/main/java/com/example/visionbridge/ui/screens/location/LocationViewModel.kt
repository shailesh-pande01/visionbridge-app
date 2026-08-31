package com.example.visionbridge.ui.screens.location

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.LocationApi
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.LocationAnalysis
import com.example.visionbridge.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LocationUiState {
    object Loading : LocationUiState
    data class Success(val analysis: LocationAnalysis) : LocationUiState
    data class Error(val message: String) : LocationUiState
}

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val locationApi = LocationApi(application)

    private val _uiState = MutableStateFlow<LocationUiState>(LocationUiState.Loading)
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    fun fetchCurrentLocation(language: String = "en") {
        _uiState.value = LocationUiState.Loading
        viewModelScope.launch {
            val (lat, lng) = LocationHelper.getCurrentLocation(getApplication())
            val result = withContext(Dispatchers.IO) {
                locationApi.getCurrentLocation(lat, lng, language)
            }

            when (result) {
                is ApiResult.Success -> {
                    val analysis = result.value
                    ContextMemoryManager.setContext(
                        "location",
                        "Location: ${analysis.address ?: "Nearby places"}",
                        analysis.summary
                    )
                    _uiState.value = LocationUiState.Success(analysis)
                }
                is ApiResult.Failure -> {
                    _uiState.value = LocationUiState.Error(result.error.userMessage)
                }
            }
        }
    }
}
