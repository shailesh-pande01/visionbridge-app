package com.example.visionbridge.ui.screens.transport

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.TransportApi
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.TransportAnalysis
import com.example.visionbridge.utils.ImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TransportUiState {
    object Camera : TransportUiState
    object Processing : TransportUiState
    data class Result(val analysis: TransportAnalysis) : TransportUiState
    data class Failed(val message: String, val technicalDetail: String? = null) : TransportUiState
}

class TransportViewModel(application: Application) : AndroidViewModel(application) {

    private val transportApi = TransportApi(application)

    private val _uiState = MutableStateFlow<TransportUiState>(TransportUiState.Camera)
    val uiState: StateFlow<TransportUiState> = _uiState.asStateFlow()

    fun onCaptureStarted() {
        _uiState.value = TransportUiState.Processing
    }

    fun onImageCaptured(imageBytes: ByteArray, rotationDegrees: Int, language: String = "en") {
        viewModelScope.launch {
            _uiState.value = TransportUiState.Processing

            val optimized = try {
                withContext(Dispatchers.Default) {
                    ImageHelper.optimizeAndEncode(imageBytes, rotationDegrees)
                }
            } catch (e: Exception) {
                _uiState.value = TransportUiState.Failed(
                    message = "Could not process signboard photo.",
                    technicalDetail = e.message
                )
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                transportApi.analyzeTransport(optimized.base64, optimized.mimeType, language)
            }

            when (result) {
                is ApiResult.Success -> {
                    val analysis = result.value
                    ContextMemoryManager.setContext(
                        "transport",
                        "Transport: ${analysis.type} - ${analysis.title} to ${analysis.destination}",
                        analysis.speech
                    )
                    _uiState.value = TransportUiState.Result(analysis)
                }
                is ApiResult.Failure -> {
                    _uiState.value = TransportUiState.Failed(
                        message = result.error.userMessage,
                        technicalDetail = result.error.technicalDetail
                    )
                }
            }
        }
    }

    fun reset() {
        _uiState.value = TransportUiState.Camera
    }
}
