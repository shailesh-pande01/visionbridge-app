package com.example.visionbridge.live

import android.app.Application
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.R
import com.example.visionbridge.api.ApiError
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.data.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared ViewModel powering both VisionBridge Live and AI Voice Call.
 * Coordinates AudioCapture, AudioPlayback, CameraStreamer, and GeminiLiveWebSocket.
 */
class LiveViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val liveApi = LiveApi(context)
    private val sessionManager = SessionManager.getInstance(context)

    // ── Live UI States ────────────────────────────────────────────────
    private val _liveState = MutableStateFlow(LiveState.IDLE)
    val liveState: StateFlow<LiveState> = _liveState.asStateFlow()

    private val _visualQualityMode = MutableStateFlow(VisualQualityMode.FAST_LIVE)
    val visualQualityMode: StateFlow<VisualQualityMode> = _visualQualityMode.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isAiSpeaking = MutableStateFlow(false)
    val isAiSpeaking: StateFlow<Boolean> = _isAiSpeaking.asStateFlow()

    private val _currentUserQuery = MutableStateFlow("")
    val currentUserQuery: StateFlow<String> = _currentUserQuery.asStateFlow()

    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

    private val _conversationTurns = MutableStateFlow<List<ConversationTurn>>(emptyList())
    val conversationTurns: StateFlow<List<ConversationTurn>> = _conversationTurns.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _diagnostics = MutableStateFlow(LiveDiagnostics())
    val diagnostics: StateFlow<LiveDiagnostics> = _diagnostics.asStateFlow()

    // ── Engines & Components ──────────────────────────────────────────
    private var liveMode = LiveMode.VISION
    private var audioEngine: LiveAudioEngine? = null
    private var cameraStreamer: LiveCameraStreamer? = null
    private var geminiWebSocket: GeminiLiveWebSocket? = null

    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var isCleaningUp = false

    // Latency profiling
    private var turnStartTime = 0L
    private var waitingFirstAudio = false
    private var audioChunksSentCount = 0L

    // ── Public Session APIs ───────────────────────────────────────────

    /**
     * Starts Live session for either Vision Live or AI Voice Call.
     */
    fun startSession(
        mode: LiveMode,
        lifecycleOwner: LifecycleOwner? = null,
        previewView: PreviewView? = null
    ) {
        if (_liveState.value != LiveState.IDLE &&
            _liveState.value != LiveState.STOPPED &&
            _liveState.value != LiveState.ERROR
        ) {
            stopSession()
        }

        liveMode = mode
        _visualQualityMode.value = if (mode == LiveMode.VOICE) VisualQualityMode.VOICE_CALL else VisualQualityMode.FAST_LIVE
        _errorMessage.value = ""
        _currentUserQuery.value = ""
        _currentResponse.value = ""
        _isMuted.value = false
        _isAiSpeaking.value = false
        audioChunksSentCount = 0L
        reconnectAttempts = 0
        isCleaningUp = false

        _liveState.value = LiveState.STARTING
        Log.d(TAG, "Starting Live session in mode: ${mode.name}")

        viewModelScope.launch {
            // 1. Initialize Audio Engine immediately
            val engine = LiveAudioEngine(
                context = context,
                onAudioChunk = { base64Pcm ->
                    if (geminiWebSocket?.isSetupComplete?.get() == true) {
                        geminiWebSocket?.sendRealtimeAudio(base64Pcm)
                        audioChunksSentCount++
                        _diagnostics.value = _diagnostics.value.copy(
                            audioChunksSent = audioChunksSentCount
                        )
                    }
                },
                onBargeIn = { rms ->
                    Log.d(TAG, "User voice barge-in detected (RMS: $rms). Interrupting AI output.")
                    interruptAi()
                },
                onSpeechStart = {
                    Log.d(TAG, "User speech started.")
                    interruptAi()
                    _liveState.value = LiveState.LISTENING
                },
                onSpeechEnd = {
                    Log.d(TAG, "User speech ended.")
                    // In vision mode, if local VAD detects end of speech, finalize turn with camera frame
                    if (liveMode == LiveMode.VISION && geminiWebSocket?.isSetupComplete?.get() == true) {
                        val isHigh = _visualQualityMode.value == VisualQualityMode.HIGH_DETAIL
                        cameraStreamer?.captureCurrentFrame(highDetail = isHigh) { frame ->
                            turnStartTime = System.currentTimeMillis()
                            waitingFirstAudio = true
                            _liveState.value = LiveState.THINKING
                            geminiWebSocket?.sendUserTurn(
                                text = "Answer what you see and hear.",
                                base64Jpeg = frame
                            )
                        }
                    }
                },
                onLevel = { rms ->
                    _diagnostics.value = _diagnostics.value.copy(
                        micLevel = rms,
                        micActive = rms > 0.001f
                    )
                },
                onPlaybackStateChange = { speaking ->
                    _isAiSpeaking.value = speaking
                    audioEngine?.isAiSpeaking?.set(speaking)
                    _diagnostics.value = _diagnostics.value.copy(isAiSpeaking = speaking)
                    if (!speaking && _liveState.value == LiveState.SPEAKING) {
                        _liveState.value = LiveState.LISTENING
                    }
                }
            )

            try {
                engine.start()
                audioEngine = engine
                _diagnostics.value = _diagnostics.value.copy(
                    echoCancellation = engine.aecActive,
                    noiseSuppression = engine.nsActive,
                    autoGainControl = engine.agcActive
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AudioEngine", e)
                handleSessionError("Could not initialize microphone or audio playback.")
                return@launch
            }

            // 2. Start Camera (Vision Mode only)
            if (mode == LiveMode.VISION && previewView != null && lifecycleOwner != null) {
                val streamer = LiveCameraStreamer(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    onFrame = { base64Jpeg ->
                        if (geminiWebSocket?.isSetupComplete?.get() == true) {
                            geminiWebSocket?.sendRealtimeFrame(base64Jpeg)
                        }
                    }
                )
                streamer.startPreview()
                cameraStreamer = streamer
            }

            // 3. Connect Live Session
            initiateConnection()
        }
    }

    private suspend fun initiateConnection() {
        _liveState.value = LiveState.CONNECTING

        val lang = sessionManager.getLanguage()
        val sessionResult = withContext(Dispatchers.IO) {
            liveApi.requestLiveSession(mode = liveMode, language = lang)
        }

        when (sessionResult) {
            is ApiResult.Success -> {
                val sessionData = sessionResult.value
                _diagnostics.value = _diagnostics.value.copy(
                    model = sessionData.model,
                    reconnectCount = reconnectAttempts
                )
                connectWebSocket(sessionData)
            }

            is ApiResult.Failure -> {
                Log.e(TAG, "Backend Live session creation failed: ${sessionResult.error.technicalDetail}")
                val message = formatErrorMessage(sessionResult.error)
                handleSessionError(message)
            }
        }
    }

    private fun connectWebSocket(sessionData: LiveSessionData) {
        val ws = GeminiLiveWebSocket(
            sessionData = sessionData,
            onConnected = {
                Log.d(TAG, "Gemini Live WebSocket open.")
                _diagnostics.value = _diagnostics.value.copy(connectedAt = System.currentTimeMillis())
            },
            onSetupComplete = {
                Log.d(TAG, "Setup complete! Live streaming ready.")
                _liveState.value = LiveState.LISTENING
                _diagnostics.value = _diagnostics.value.copy(setupCompleteReceived = true)

                // Start camera frame loop in vision mode
                if (liveMode == LiveMode.VISION) {
                    cameraStreamer?.startFrameStreaming()
                }
            },
            onAudioReceived = { base64Pcm ->
                if (waitingFirstAudio && turnStartTime > 0) {
                    val ttfb = System.currentTimeMillis() - turnStartTime
                    waitingFirstAudio = false
                    _diagnostics.value = _diagnostics.value.copy(lastTtfbMs = ttfb)
                    Log.d(TAG, "⚡ [TTFB Latency] Time to first audio: ${ttfb}ms")
                }

                _liveState.value = LiveState.SPEAKING
                audioEngine?.playChunk(base64Pcm)
            },
            onTextTranscriptReceived = { text ->
                _currentResponse.value += text
            },
            onInterrupted = {
                Log.d(TAG, "AI speech interrupted by server.")
                audioEngine?.interrupt()
                _isAiSpeaking.value = false
                waitingFirstAudio = false
                _liveState.value = LiveState.LISTENING
            },
            onTurnComplete = {
                Log.d(TAG, "Gemini turn complete.")
                waitingFirstAudio = false
                val fullText = _currentResponse.value.trim()
                if (fullText.isNotBlank()) {
                    val history = _conversationTurns.value.toMutableList()
                    history.add(ConversationTurn(role = "model", text = fullText))
                    _conversationTurns.value = history
                }
                _currentResponse.value = ""
                _currentUserQuery.value = ""
                if (_liveState.value == LiveState.SPEAKING || _liveState.value == LiveState.THINKING) {
                    _liveState.value = LiveState.LISTENING
                }
            },
            onToolCallReceived = { name, args ->
                handleToolCall(name, args)
            },
            onDisconnected = { code, reason, error ->
                Log.d(TAG, "WebSocket disconnected: $code ($reason)")
                _diagnostics.value = _diagnostics.value.copy(
                    lastCloseCode = code,
                    lastCloseReason = reason
                )

                if (!isCleaningUp && _liveState.value != LiveState.STOPPED && _liveState.value != LiveState.ERROR) {
                    attemptReconnect()
                }
            }
        )

        ws.connect()
        geminiWebSocket = ws
    }

    // ── Dispatch User Conversational Turn ─────────────────────────────

    fun sendUserQuery(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || geminiWebSocket?.isSetupComplete?.get() != true) return

        interruptAi()

        val history = _conversationTurns.value.toMutableList()
        history.add(ConversationTurn(role = "user", text = trimmed))
        _conversationTurns.value = history
        _currentUserQuery.value = trimmed

        turnStartTime = System.currentTimeMillis()
        waitingFirstAudio = true
        _liveState.value = LiveState.THINKING

        if (liveMode == LiveMode.VISION) {
            val isReadIntent = ReadingIntentDetector.isReadingIntent(trimmed)
            val qualityMode = if (isReadIntent) VisualQualityMode.HIGH_DETAIL else VisualQualityMode.FAST_LIVE
            _visualQualityMode.value = qualityMode
            _diagnostics.value = _diagnostics.value.copy(visualMode = qualityMode)

            cameraStreamer?.captureCurrentFrame(highDetail = isReadIntent) { frame ->
                geminiWebSocket?.sendUserTurn(text = trimmed, base64Jpeg = frame)
            }
        } else {
            _visualQualityMode.value = VisualQualityMode.VOICE_CALL
            geminiWebSocket?.sendUserTurn(text = trimmed, base64Jpeg = null)
        }
    }

    // ── Controls ──────────────────────────────────────────────────────

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        audioEngine?.setMuted(next)
    }

    fun switchCamera() {
        cameraStreamer?.switchFacingMode()
    }

    fun interruptAi() {
        audioEngine?.interrupt()
        _isAiSpeaking.value = false
        waitingFirstAudio = false
        if (_liveState.value == LiveState.SPEAKING || _liveState.value == LiveState.THINKING) {
            _liveState.value = LiveState.LISTENING
        }
    }

    // ── Reconnection Logic ────────────────────────────────────────────

    private fun attemptReconnect() {
        if (isCleaningUp) return

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached.")
            handleSessionError("Live connection was lost and could not reconnect. Please check your network.")
            return
        }

        reconnectAttempts++
        _liveState.value = LiveState.RECONNECTING

        val delayMs = RECONNECT_DELAY_BASE_MS * (1L shl (reconnectAttempts - 1))
        Log.d(TAG, "Reconnecting Live session (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS) in ${delayMs}ms...")

        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            try {
                geminiWebSocket?.close()
                initiateConnection()
            } catch (e: Exception) {
                Log.w(TAG, "Reconnect attempt error", e)
                attemptReconnect()
            }
        }
    }

    private fun handleToolCall(name: String, args: org.json.JSONObject) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                liveApi.executeLiveTool(name, args)
            } catch (e: Exception) {
                Log.w(TAG, "Tool execution error: ${e.message}")
            }
        }
    }

    private fun handleSessionError(message: String) {
        _errorMessage.value = message
        _liveState.value = LiveState.ERROR
        stopSession(keepError = true)
    }

    private fun formatErrorMessage(error: ApiError): String {
        return when (error.kind) {
            ApiError.Kind.BACKEND_UNREACHABLE -> context.getString(R.string.common_retry)
            ApiError.Kind.TIMEOUT -> "Server timed out connecting to Gemini Live."
            ApiError.Kind.NETWORK_IO -> "Network connection lost. Please check your internet."
            else -> error.userMessage.ifBlank { "Could not connect to Gemini Live." }
        }
    }

    // ── Stop & Cleanup ────────────────────────────────────────────────

    fun stopSession(keepError: Boolean = false) {
        isCleaningUp = true
        reconnectJob?.cancel()
        reconnectJob = null

        _liveState.value = LiveState.STOPPING

        geminiWebSocket?.close()
        geminiWebSocket = null

        audioEngine?.stop()
        audioEngine = null

        cameraStreamer?.stop()
        cameraStreamer = null

        _isAiSpeaking.value = false
        waitingFirstAudio = false

        if (!keepError) {
            _errorMessage.value = ""
            _liveState.value = LiveState.STOPPED
        }
        isCleaningUp = false
    }

    override fun onCleared() {
        super.onCleared()
        cameraStreamer?.release()
        stopSession()
    }

    companion object {
        private const val TAG = "VB-LiveViewModel"
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_DELAY_BASE_MS = 1500L
    }
}
