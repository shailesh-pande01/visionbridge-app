package com.example.visionbridge.ui.screens.hazard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.VisionApi
import com.example.visionbridge.utils.ImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HazardAlert(
    val speech: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HazardModeViewModel(application: Application) : AndroidViewModel(application) {

    private val visionApi = VisionApi(application)

    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _lastAlert = MutableStateFlow<HazardAlert?>(null)
    val lastAlert: StateFlow<HazardAlert?> = _lastAlert.asStateFlow()

    private var sceneMemory: String = ""

    fun processFrame(imageBytes: ByteArray, rotationDegrees: Int, language: String = "en", onSpeak: (String) -> Unit) {
        if (!_isScanning.value || _isAnalyzing.value) return

        _isAnalyzing.value = true
        viewModelScope.launch {
            try {
                val optimized = withContext(Dispatchers.Default) {
                    ImageHelper.optimizeAndEncode(imageBytes, rotationDegrees)
                }

                val result = withContext(Dispatchers.IO) {
                    visionApi.analyzeHazard(optimized.base64, optimized.mimeType, sceneMemory, language)
                }

                if (result is ApiResult.Success) {
                    val analysis = result.value
                    sceneMemory = analysis.sceneSummary

                    val cleanSpeech = analysis.speech.trim()
                    val isNoChange = cleanSpeech.equals("No significant change.", ignoreCase = true) ||
                            cleanSpeech.contains("कोई खास बदलाव नहीं") ||
                            cleanSpeech.contains("कोणताही बदल नाही")

                    if (!isNoChange && cleanSpeech.isNotBlank()) {
                        _lastAlert.value = HazardAlert(cleanSpeech, analysis.timestamp)
                        onSpeak(cleanSpeech)
                    }
                }
            } catch (e: Exception) {
                // Ignore transient network errors in periodic background scan
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun toggleScanning() {
        _isScanning.value = !_isScanning.value
    }

    fun stop() {
        _isScanning.value = false
        sceneMemory = ""
        _lastAlert.value = null
    }
}
