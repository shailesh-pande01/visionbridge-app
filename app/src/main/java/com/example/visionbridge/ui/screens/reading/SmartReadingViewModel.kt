package com.example.visionbridge.ui.screens.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.data.SmartReadingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Anything the screen should read aloud exactly once when it appears.
 * [id] changes on every new outcome so a repeated result still gets spoken.
 */
interface SpokenState {
    val id: Long
    val speech: String
}

sealed interface ReadingUiState {

    /** Live camera preview, waiting for a capture. */
    data object Camera : ReadingUiState

    /** Capture taken — optimizing and waiting on the backend. */
    data object Processing : ReadingUiState

    data class Result(
        override val id: Long,
        val text: String,
        val confidence: Double?,
        val isLowConfidence: Boolean
    ) : ReadingUiState, SpokenState {
        override val speech: String get() = text
    }

    /** Backend answered successfully but found nothing readable. */
    data class NoText(
        override val id: Long,
        val message: String
    ) : ReadingUiState, SpokenState {
        override val speech: String get() = message
    }

    data class Failed(
        override val id: Long,
        val message: String,
        val technicalDetail: String?
    ) : ReadingUiState, SpokenState {
        override val speech: String get() = message
    }
}

class SmartReadingViewModel(
    private val repository: SmartReadingRepository = SmartReadingRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReadingUiState>(ReadingUiState.Camera)
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    private val outcomeIds = AtomicLong(0)

    /** Called the moment the shutter is tapped, before the image is available. */
    fun onCaptureStarted() {
        _uiState.value = ReadingUiState.Processing
    }

    /** CameraX handed us JPEG bytes — optimize, upload, and publish the outcome. */
    fun onImageCaptured(imageBytes: ByteArray, rotationDegrees: Int, language: String = "en") {
        _uiState.value = ReadingUiState.Processing

        viewModelScope.launch {
            _uiState.value = when (val result = repository.extractText(imageBytes, rotationDegrees, language)) {
                is ApiResult.Success -> {
                    val extraction = result.value
                    val text = extraction.extractedText

                    if (text.isNullOrBlank()) {
                        ReadingUiState.NoText(
                            id = outcomeIds.incrementAndGet(),
                            message = extraction.message
                                ?: "No readable text was found. Try pointing the camera at a sign, " +
                                "label, menu, or document."
                        )
                    } else {
                        val isLow = extraction.confidence != null && extraction.confidence < LOW_CONFIDENCE_THRESHOLD
                        val session = com.example.visionbridge.data.ReadingSession(
                            sessionId = "reading-${System.currentTimeMillis()}",
                            extractedText = text,
                            language = language,
                            timestamp = System.currentTimeMillis(),
                            confidence = extraction.confidence,
                            isLowConfidence = isLow
                        )
                        com.example.visionbridge.data.ContextMemoryManager.setActiveReadingSession(session)
                        ReadingUiState.Result(
                            id = outcomeIds.incrementAndGet(),
                            text = text,
                            confidence = extraction.confidence,
                            // Same 0.70 bar the web client uses to flag an unreliable read.
                            isLowConfidence = isLow
                        )
                    }
                }

                is ApiResult.Failure -> ReadingUiState.Failed(
                    id = outcomeIds.incrementAndGet(),
                    message = result.error.userMessage,
                    technicalDetail = result.error.technicalDetail
                )
            }
        }
    }

    /** The camera itself failed — permission, binding, or shutter error. */
    fun onCaptureFailed(userMessage: String, technicalDetail: String?) {
        _uiState.value = ReadingUiState.Failed(
            id = outcomeIds.incrementAndGet(),
            message = userMessage,
            technicalDetail = technicalDetail
        )
    }

    /** Back to the live preview for another capture. */
    fun reset() {
        _uiState.value = ReadingUiState.Camera
    }

    private companion object {
        const val LOW_CONFIDENCE_THRESHOLD = 0.70
    }
}
