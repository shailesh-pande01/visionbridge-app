package com.example.visionbridge.ui.screens.surroundings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.VisionApi
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.SceneAnalysis
import com.example.visionbridge.utils.ImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SurroundingsUiState {
    object Camera : SurroundingsUiState
    object Processing : SurroundingsUiState
    data class Result(val analysis: SceneAnalysis, val speech: String) : SurroundingsUiState
    data class Failed(val message: String, val technicalDetail: String? = null) : SurroundingsUiState
}

class SurroundingsViewModel(application: Application) : AndroidViewModel(application) {

    private val visionApi = VisionApi(application)

    private val _uiState = MutableStateFlow<SurroundingsUiState>(SurroundingsUiState.Camera)
    val uiState: StateFlow<SurroundingsUiState> = _uiState.asStateFlow()

    fun onCaptureStarted() {
        _uiState.value = SurroundingsUiState.Processing
    }

    fun onImageCaptured(imageBytes: ByteArray, rotationDegrees: Int, language: String = "en") {
        viewModelScope.launch {
            _uiState.value = SurroundingsUiState.Processing

            val optimized = try {
                withContext(Dispatchers.Default) {
                    ImageHelper.optimizeAndEncode(imageBytes, rotationDegrees)
                }
            } catch (e: Exception) {
                _uiState.value = SurroundingsUiState.Failed(
                    message = "Could not process image for analysis.",
                    technicalDetail = e.message
                )
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                visionApi.analyzeSurroundings(optimized.base64, optimized.mimeType, language)
            }

            when (result) {
                is ApiResult.Success -> {
                    val analysis = result.value
                    ContextMemoryManager.setContext("surroundings", "Scene: ${analysis.scene}", analysis.description)
                    _uiState.value = SurroundingsUiState.Result(analysis, analysis.description)
                }
                is ApiResult.Failure -> {
                    _uiState.value = SurroundingsUiState.Failed(
                        message = result.error.userMessage,
                        technicalDetail = result.error.technicalDetail
                    )
                }
            }
        }
    }

    fun reset() {
        _uiState.value = SurroundingsUiState.Camera
    }
}
