package com.example.visionbridge.ui.screens.finder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.FinderApi
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.FinderAnalysis
import com.example.visionbridge.utils.ImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FinderUiState {
    object PromptTarget : FinderUiState
    object Camera : FinderUiState
    object Processing : FinderUiState
    data class Result(val analysis: FinderAnalysis) : FinderUiState
    data class Failed(val message: String, val technicalDetail: String? = null) : FinderUiState
}

class ObjectFinderViewModel(application: Application) : AndroidViewModel(application) {

    private val finderApi = FinderApi(application)

    private val _uiState = MutableStateFlow<FinderUiState>(FinderUiState.PromptTarget)
    val uiState: StateFlow<FinderUiState> = _uiState.asStateFlow()

    private val _targetObject = MutableStateFlow("")
    val targetObject: StateFlow<String> = _targetObject.asStateFlow()

    fun setTarget(target: String) {
        val clean = target.trim()
        if (clean.isNotBlank()) {
            _targetObject.value = clean
            _uiState.value = FinderUiState.Camera
        }
    }

    fun onCaptureStarted() {
        _uiState.value = FinderUiState.Processing
    }

    fun onImageCaptured(imageBytes: ByteArray, rotationDegrees: Int, language: String = "en") {
        val target = _targetObject.value
        if (target.isBlank()) {
            _uiState.value = FinderUiState.PromptTarget
            return
        }

        viewModelScope.launch {
            _uiState.value = FinderUiState.Processing

            val optimized = try {
                withContext(Dispatchers.Default) {
                    ImageHelper.optimizeAndEncode(imageBytes, rotationDegrees)
                }
            } catch (e: Exception) {
                _uiState.value = FinderUiState.Failed(
                    message = "Could not process image.",
                    technicalDetail = e.message
                )
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                finderApi.searchObject(optimized.base64, target, optimized.mimeType, language)
            }

            when (result) {
                is ApiResult.Success -> {
                    val analysis = result.value
                    ContextMemoryManager.setContext(
                        "objectFinder",
                        "Searching for: $target. Found: ${analysis.found}",
                        analysis.speech
                    )
                    _uiState.value = FinderUiState.Result(analysis)
                }
                is ApiResult.Failure -> {
                    _uiState.value = FinderUiState.Failed(
                        message = result.error.userMessage,
                        technicalDetail = result.error.technicalDetail
                    )
                }
            }
        }
    }

    fun resetToCamera() {
        _uiState.value = FinderUiState.Camera
    }

    fun changeTarget() {
        _targetObject.value = ""
        _uiState.value = FinderUiState.PromptTarget
    }
}
