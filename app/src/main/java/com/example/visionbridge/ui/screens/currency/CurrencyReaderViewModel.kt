package com.example.visionbridge.ui.screens.currency

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.VisionApi
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.CurrencyAnalysis
import com.example.visionbridge.utils.ImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CurrencyUiState {
    object Camera : CurrencyUiState
    object Processing : CurrencyUiState
    data class Result(val analysis: CurrencyAnalysis) : CurrencyUiState
    data class Failed(val message: String, val technicalDetail: String? = null) : CurrencyUiState
}

class CurrencyReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val visionApi = VisionApi(application)

    private val _uiState = MutableStateFlow<CurrencyUiState>(CurrencyUiState.Camera)
    val uiState: StateFlow<CurrencyUiState> = _uiState.asStateFlow()

    fun onCaptureStarted() {
        _uiState.value = CurrencyUiState.Processing
    }

    fun onImageCaptured(imageBytes: ByteArray, rotationDegrees: Int, language: String = "en") {
        viewModelScope.launch {
            _uiState.value = CurrencyUiState.Processing

            val optimized = try {
                withContext(Dispatchers.Default) {
                    ImageHelper.optimizeAndEncode(imageBytes, rotationDegrees)
                }
            } catch (e: Exception) {
                _uiState.value = CurrencyUiState.Failed(
                    message = "Could not process currency photo.",
                    technicalDetail = e.message
                )
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                visionApi.analyzeCurrency(optimized.base64, optimized.mimeType, language)
            }

            when (result) {
                is ApiResult.Success -> {
                    val analysis = result.value
                    ContextMemoryManager.setContext(
                        "currency",
                        "Detected currency: ${analysis.symbol}${analysis.total ?: 0}",
                        analysis.speech
                    )
                    _uiState.value = CurrencyUiState.Result(analysis)
                }
                is ApiResult.Failure -> {
                    _uiState.value = CurrencyUiState.Failed(
                        message = result.error.userMessage,
                        technicalDetail = result.error.technicalDetail
                    )
                }
            }
        }
    }

    fun reset() {
        _uiState.value = CurrencyUiState.Camera
    }
}
