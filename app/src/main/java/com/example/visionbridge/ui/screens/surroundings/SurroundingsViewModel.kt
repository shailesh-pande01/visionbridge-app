package com.example.visionbridge.ui.screens.surroundings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.R
import com.example.visionbridge.ai.ConfidenceEvaluator
import com.example.visionbridge.ai.ConfidenceLevel
import com.example.visionbridge.ai.FallbackContext
import com.example.visionbridge.ai.HumanFallbackCoordinator
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
    data class Result(
        val analysis: SceneAnalysis,
        val speech: String,
        val confidenceLevel: ConfidenceLevel = ConfidenceLevel.HIGH,
        val isQualified: Boolean = false,
        val fallbackContext: FallbackContext? = null
    ) : SurroundingsUiState
    data class LowConfidence(
        val analysis: SceneAnalysis,
        val spokenPrompt: String,
        val fallbackContext: FallbackContext
    ) : SurroundingsUiState
    data class Failed(
        val message: String,
        val technicalDetail: String? = null,
        val canOfferVolunteer: Boolean = true
    ) : SurroundingsUiState
}

class SurroundingsViewModel(application: Application) : AndroidViewModel(application) {

    private val visionApi = VisionApi(application)
    val fallbackCoordinator = HumanFallbackCoordinator("Describe Surroundings")

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
                    val confidence = analysis.confidence
                    val level = ConfidenceEvaluator.evaluate(confidence)

                    Log.i(TAG, "[AI_RESULT] Scene='${analysis.scene}', confidence=$confidence, level=$level")

                    ContextMemoryManager.setContext("surroundings", "Scene: ${analysis.scene}", analysis.description)

                    when (level) {
                        ConfidenceLevel.LOW -> {
                            val signature = "${analysis.scene}_${analysis.description.take(40)}"
                            val shouldOffer = fallbackCoordinator.shouldOfferFallback(confidence, signature)
                            val fallbackContext = fallbackCoordinator.buildFallbackContext(
                                confidence = confidence,
                                reason = "Scene was unclear or ambiguous",
                                originalPrompt = analysis.scene
                            )

                            if (shouldOffer) {
                                val prompt = HumanFallbackCoordinator.getSpokenPrompt(getApplication(), "describe_surroundings")
                                Log.i(TAG, "[AI_FALLBACK] Low confidence triggering fallback offer: $prompt")
                                _uiState.value = SurroundingsUiState.LowConfidence(
                                    analysis = analysis,
                                    spokenPrompt = prompt,
                                    fallbackContext = fallbackContext
                                )
                            } else {
                                // Fallback was previously declined in this session; present result with qualification
                                _uiState.value = SurroundingsUiState.Result(
                                    analysis = analysis,
                                    speech = analysis.description,
                                    confidenceLevel = ConfidenceLevel.LOW,
                                    isQualified = true,
                                    fallbackContext = fallbackContext
                                )
                            }
                        }

                        ConfidenceLevel.MEDIUM -> {
                            val qualifier = getApplication<Application>().getString(R.string.confidence_medium_qualifier)
                            val qualifiedSpeech = "$qualifier ${analysis.description}"
                            val fallbackContext = fallbackCoordinator.buildFallbackContext(
                                confidence = confidence,
                                reason = "Moderately confident scene description",
                                originalPrompt = analysis.scene
                            )
                            _uiState.value = SurroundingsUiState.Result(
                                analysis = analysis,
                                speech = qualifiedSpeech,
                                confidenceLevel = ConfidenceLevel.MEDIUM,
                                isQualified = true,
                                fallbackContext = fallbackContext
                            )
                        }

                        ConfidenceLevel.HIGH -> {
                            _uiState.value = SurroundingsUiState.Result(
                                analysis = analysis,
                                speech = analysis.description,
                                confidenceLevel = ConfidenceLevel.HIGH,
                                isQualified = false,
                                fallbackContext = null
                            )
                        }
                    }
                }

                is ApiResult.Failure -> {
                    Log.e(TAG, "[AI_RESULT] Technical failure: ${result.error.userMessage}")
                    _uiState.value = SurroundingsUiState.Failed(
                        message = result.error.userMessage,
                        technicalDetail = result.error.technicalDetail,
                        canOfferVolunteer = true
                    )
                }
            }
        }
    }

    fun onFallbackDeclined() {
        fallbackCoordinator.markFallbackDeclined()
        _uiState.value = SurroundingsUiState.Camera
    }

    fun reset() {
        _uiState.value = SurroundingsUiState.Camera
    }

    fun retryCapture() {
        fallbackCoordinator.resetSession()
        _uiState.value = SurroundingsUiState.Camera
    }

    companion object {
        private const val TAG = "VB-SurroundingsVM"
    }
}
