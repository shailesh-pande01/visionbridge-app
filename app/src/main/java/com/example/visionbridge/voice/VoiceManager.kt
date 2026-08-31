package com.example.visionbridge.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.visionbridge.api.AssistantApi
import com.example.visionbridge.data.AssistantAction
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.utils.LocaleHelper
import com.example.visionbridge.utils.TextToSpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Central, lifecycle-aware native Android Voice Assistant Manager.
 *
 * Responsibilities:
 * - Continuously listens for the wake-word "Vision" (and its Devanagari transliterations).
 * - Extracts commands in both single-utterance ("Vision, read this menu") and two-stage ("Vision" ... "read this menu") modes.
 * - Routes intents through local fast paths or backend Gemini assistant.
 * - Integrates with Text-To-Speech, providing echo rejection and automatic wake-word resumption when speaking ends.
 * - Manages SpeechRecognizer lifecycle cleanly without rapid retry loops, leaks, or conflicts with WebRTC calls.
 */
class VoiceManager private constructor(private val context: Context) {

    enum class Phase {
        WAKE,     // Listening for wake-word "Vision"
        COMMAND   // Wake-word detected, listening for user command
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val assistantApi = AssistantApi(context)
    val tts = TextToSpeechManager(context)

    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecognizerListening = false

    private var currentPhase: Phase = Phase.WAKE
    private var isBusy = false
    private var isPausedForCall = false
    private var isActivityPaused = false
    private var wantListening = true

    private var generation = 0
    private var lastResponseText = ""
    private var commandWindowUntil = 0L

    private var consecutiveErrors = 0
    private var lastErrorTime = 0L

    private var navigationHandler: ((String) -> Unit)? = null

    private val commandTimeoutRunnable = Runnable {
        if (currentPhase == Phase.COMMAND && !isBusy) {
            Log.d(TAG, "Command listening timed out. Returning to wake-word listening.")
            currentPhase = Phase.WAKE
            _voiceState.value = _voiceState.value.copy(
                status = VoiceStatus.LISTENING,
                transcript = "",
                error = ""
            )
            scheduleRestart(RESTART_DELAY_MS)
        }
    }

    private val restartRunnable = Runnable {
        if (wantListening && !isBusy && !isPausedForCall && !isActivityPaused && !tts.isSpeaking.value) {
            startRecognitionInternal()
        }
    }

    init {
        // Observe language changes
        scope.launch {
            SessionManager.getInstance(context).language.collect { langCode ->
                tts.setLanguage(langCode)
            }
        }

        // Observe media playback to resume wake-word when playback stops or pauses
        scope.launch {
            com.example.visionbridge.audio.EntertainmentMediaService.playbackStateFlow.collect { pState ->
                if (pState != com.example.visionbridge.audio.MediaPlaybackState.PLAYING &&
                    wantListening && !isBusy && !isPausedForCall && !isActivityPaused && !tts.isSpeaking.value
                ) {
                    scheduleRestart(200)
                }
            }
        }
    }

    fun setNavigationHandler(handler: (String) -> Unit) {
        this.navigationHandler = handler
    }

    // ── Public Control APIs ──────────────────────────────────────────

    /**
     * Starts continuous wake-word listening in the foreground app.
     */
    fun startAmbientListening() {
        wantListening = true
        currentPhase = Phase.WAKE
        consecutiveErrors = 0
        scheduleRestart(0)
    }

    /**
     * Stops listening completely.
     */
    fun stopVoice() {
        wantListening = false
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.removeCallbacks(restartRunnable)
        destroyRecognizer()
        tts.stop()
        _voiceState.value = VoiceState(status = VoiceStatus.IDLE)
    }

    /**
     * Manual activation (e.g. from tapping the microphone button or double-tap).
     * Bypasses wake-word requirement and immediately enters command listening.
     */
    fun activateVoice(initialCommand: String = "") {
        if (isBusy || isPausedForCall) return

        tts.stop()
        wantListening = true
        consecutiveErrors = 0

        if (initialCommand.isNotBlank()) {
            dispatchCommand(initialCommand)
            return
        }

        currentPhase = Phase.COMMAND
        commandWindowUntil = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
        _voiceState.value = _voiceState.value.copy(
            status = VoiceStatus.AWAITING_COMMAND,
            transcript = "",
            error = ""
        )

        // Duck background media audio during user voice interaction
        com.example.visionbridge.audio.EntertainmentAudioSession.setDucked(true)

        // Announce ready prompt
        tts.speak("Listening.") {
            if (!isBusy && currentPhase == Phase.COMMAND) {
                scheduleRestart(0)
                armCommandTimeout()
            }
        }
    }

    /**
     * Pauses global voice engine completely during an active WebRTC volunteer call.
     * WebRTC requires exclusive microphone access.
     */
    fun pauseForCall() {
        Log.d(TAG, "Pausing VoiceManager for WebRTC call.")
        isPausedForCall = true
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.removeCallbacks(restartRunnable)
        destroyRecognizer()
        _voiceState.value = _voiceState.value.copy(status = VoiceStatus.PAUSED)
    }

    /**
     * Resumes global voice engine after WebRTC volunteer call ends.
     */
    fun resumeAfterCall() {
        Log.d(TAG, "Resuming VoiceManager after WebRTC call.")
        isPausedForCall = false
        if (wantListening && !isActivityPaused) {
            startAmbientListening()
        }
    }

    /**
     * Called when the Activity is paused (app moved to background or screen locked).
     */
    fun onActivityPause() {
        Log.d(TAG, "Activity paused. Stopping speech recognizer for privacy and resource preservation.")
        isActivityPaused = true
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.removeCallbacks(restartRunnable)
        destroyRecognizer()
        tts.stop()
        _voiceState.value = _voiceState.value.copy(status = VoiceStatus.PAUSED)
    }

    /**
     * Called when the Activity is resumed (app returned to foreground).
     */
    fun onActivityResume() {
        Log.d(TAG, "Activity resumed. Re-arming wake-word listening.")
        isActivityPaused = false
        if (wantListening && !isPausedForCall) {
            startAmbientListening()
        }
    }

    // ── Internal Recognition Lifecycle ───────────────────────────────

    private fun startRecognitionInternal() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.value = _voiceState.value.copy(
                status = VoiceStatus.UNSUPPORTED,
                error = "Speech recognition is not available on this device."
            )
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _voiceState.value = _voiceState.value.copy(
                status = VoiceStatus.BLOCKED,
                error = "Microphone permission is required."
            )
            return
        }

        if (isBusy || isPausedForCall || isActivityPaused || tts.isSpeaking.value) {
            return
        }

        val isMediaPlaying = (com.example.visionbridge.audio.EntertainmentAudioSession.activeFeature.value == com.example.visionbridge.audio.ActiveAudioFeature.RADIO ||
                com.example.visionbridge.audio.EntertainmentAudioSession.activeFeature.value == com.example.visionbridge.audio.ActiveAudioFeature.STORY) &&
                com.example.visionbridge.audio.EntertainmentMediaService.playbackStateFlow.value == com.example.visionbridge.audio.MediaPlaybackState.PLAYING

        if (currentPhase == Phase.WAKE && isMediaPlaying) {
            _voiceState.value = _voiceState.value.copy(
                status = VoiceStatus.IDLE,
                message = ""
            )
            return
        }

        destroyRecognizer()

        val currentGen = ++generation
        val langCode = SessionManager.getInstance(context).getLanguage()
        val langTag = LocaleHelper.getLanguageTag(langCode)

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener(currentGen))
            }
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            recognizer.startListening(intent)
            isRecognizerListening = true

            val currentStatus = if (currentPhase == Phase.COMMAND) {
                VoiceStatus.AWAITING_COMMAND
            } else {
                VoiceStatus.LISTENING
            }
            _voiceState.value = _voiceState.value.copy(status = currentStatus, error = "")
            Log.d(TAG, "SpeechRecognizer started in phase: $currentPhase (gen: $currentGen, lang: $langTag)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            destroyRecognizer()
            handleRecognizerError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    private fun createRecognitionListener(currentGen: Int) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (currentGen != generation) return
            isRecognizerListening = true
            val status = if (currentPhase == Phase.COMMAND) VoiceStatus.AWAITING_COMMAND else VoiceStatus.LISTENING
            _voiceState.value = _voiceState.value.copy(status = status, error = "")
        }

        override fun onBeginningOfSpeech() {
            if (currentGen != generation) return
            Log.d(TAG, "Speech started")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            if (currentGen != generation) return
            isRecognizerListening = false
            Log.d(TAG, "Speech ended")
        }

        override fun onError(error: Int) {
            if (currentGen != generation) return
            isRecognizerListening = false
            Log.d(TAG, "SpeechRecognizer onError code: $error")
            handleRecognizerError(error)
        }

        override fun onResults(results: Bundle?) {
            if (currentGen != generation) return
            isRecognizerListening = false

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val primaryMatch = matches?.firstOrNull()?.trim() ?: ""

            Log.d(TAG, "SpeechRecognizer onResults: '$primaryMatch'")
            handleSpeechResult(primaryMatch)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (currentGen != generation) return
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!partial.isNullOrBlank() && currentPhase == Phase.COMMAND) {
                _voiceState.value = _voiceState.value.copy(transcript = partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleSpeechResult(rawText: String) {
        if (rawText.isBlank()) {
            if (currentPhase == Phase.COMMAND) {
                // Keep waiting for command until window expires
                if (System.currentTimeMillis() < commandWindowUntil) {
                    scheduleRestart(0)
                } else {
                    currentPhase = Phase.WAKE
                    _voiceState.value = _voiceState.value.copy(status = VoiceStatus.LISTENING)
                    scheduleRestart(RESTART_DELAY_MS)
                }
            } else {
                scheduleRestart(RESTART_DELAY_MS)
            }
            return
        }

        // Echo rejection check while TTS was talking
        if (tts.isSpeaking.value) {
            if (tts.isEchoOfCurrentSpeech(rawText)) {
                Log.d(TAG, "Echo rejected: '$rawText'")
                return
            }
            // Barge-in check: user said "stop", "cancel", "रुको", "चुप", "थांबा"
            if (rawText.matches(Regex(".*\\b(stop|cancel|quiet|enough)\\b.*", RegexOption.IGNORE_CASE)) ||
                rawText.contains("रुको") || rawText.contains("चुप") || rawText.contains("थांब")
            ) {
                tts.stop()
                scheduleRestart(RESTART_DELAY_MS)
                return
            }
        }

        if (currentPhase == Phase.WAKE) {
            val wakeResult = WakeWordMatcher.splitOnWakeWord(rawText)
            if (wakeResult != null) {
                Log.d(TAG, "Wake word '${wakeResult.matchedWakeWord}' detected! Extracted command: '${wakeResult.command}'")
                consecutiveErrors = 0

                if (wakeResult.command.isNotBlank()) {
                    // Single utterance: "Vision, read this menu"
                    dispatchCommand(wakeResult.command)
                } else {
                    // Wake word alone: "Vision"
                    currentPhase = Phase.COMMAND
                    commandWindowUntil = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
                    _voiceState.value = _voiceState.value.copy(
                        status = VoiceStatus.AWAITING_COMMAND,
                        transcript = "",
                        error = ""
                    )
                    tts.speak("Listening.") {
                        if (!isBusy && currentPhase == Phase.COMMAND) {
                            scheduleRestart(0)
                            armCommandTimeout()
                        }
                    }
                }
            } else {
                // Non-wake speech: ignore speech, stay listening
                Log.d(TAG, "Ignored speech without wake word: '$rawText'")
                _voiceState.value = _voiceState.value.copy(status = VoiceStatus.LISTENING, transcript = "")
                scheduleRestart(RESTART_DELAY_MS)
            }
        } else if (currentPhase == Phase.COMMAND) {
            // Command phase: user speaks their command
            mainHandler.removeCallbacks(commandTimeoutRunnable)

            // If the user repeated "Vision" in their command (e.g. "Vision read this"), strip it
            val wakeSplit = WakeWordMatcher.splitOnWakeWord(rawText)
            val commandToRun = if (wakeSplit != null && wakeSplit.command.isNotBlank()) {
                wakeSplit.command
            } else if (wakeSplit != null && wakeSplit.command.isBlank()) {
                // User repeated wake word alone, reset command window
                commandWindowUntil = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
                scheduleRestart(0)
                armCommandTimeout()
                return
            } else {
                rawText.trim()
            }

            currentPhase = Phase.WAKE
            dispatchCommand(commandToRun)
        }
    }

    private fun handleRecognizerError(errorCode: Int) {
        val now = System.currentTimeMillis()
        if (now - lastErrorTime < 3000) {
            consecutiveErrors++
        } else {
            consecutiveErrors = 1
        }
        lastErrorTime = now

        destroyRecognizer()

        if (errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            wantListening = false
            _voiceState.value = _voiceState.value.copy(
                status = VoiceStatus.BLOCKED,
                error = "Microphone permission is required."
            )
            return
        }

        // Backoff if too many errors occur in rapid succession
        val delay = when {
            consecutiveErrors > 5 -> 3000L
            consecutiveErrors > 3 -> 1500L
            errorCode == SpeechRecognizer.ERROR_NO_MATCH || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RESTART_DELAY_MS
            else -> ERROR_RESTART_DELAY_MS
        }

        if (currentPhase == Phase.COMMAND && System.currentTimeMillis() < commandWindowUntil && !isBusy) {
            scheduleRestart(delay)
        } else {
            currentPhase = Phase.WAKE
            if (wantListening && !isBusy && !isPausedForCall && !isActivityPaused) {
                scheduleRestart(delay)
            }
        }
    }

    private fun armCommandTimeout() {
        mainHandler.removeCallbacks(commandTimeoutRunnable)
        val remaining = (commandWindowUntil - System.currentTimeMillis()).coerceAtLeast(1000L)
        mainHandler.postDelayed(commandTimeoutRunnable, remaining)
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.removeCallbacks(restartRunnable)
        if (delayMs <= 0) {
            mainHandler.post(restartRunnable)
        } else {
            mainHandler.postDelayed(restartRunnable, delayMs)
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
        isRecognizerListening = false
    }

    // ── Command Dispatching & Intent Routing ─────────────────────────

    private fun dispatchCommand(rawCommand: String) {
        if (isBusy) return
        isBusy = true

        mainHandler.removeCallbacks(commandTimeoutRunnable)
        mainHandler.removeCallbacks(restartRunnable)
        destroyRecognizer()

        val command = WakeWordMatcher.normalize(rawCommand)
        if (command.isBlank()) {
            isBusy = false
            startAmbientListening()
            return
        }

        _voiceState.value = _voiceState.value.copy(
            status = VoiceStatus.PROCESSING,
            transcript = rawCommand,
            error = ""
        )

        scope.launch {
            // 1. Try local fast-path rules
            var action = VoiceActionRouter.matchFastPath(command)

            // 2. If no fast path matched, query backend Gemini Assistant
            if (action == null) {
                val langCode = SessionManager.getInstance(context).getLanguage()
                val apiRes = withContext(Dispatchers.IO) {
                    assistantApi.sendCommand(command, langCode)
                }
                action = when (apiRes) {
                    is com.example.visionbridge.api.ApiResult.Success -> apiRes.value
                    is com.example.visionbridge.api.ApiResult.Failure -> {
                        Log.e(TAG, "Assistant API request failed: ${apiRes.error.userMessage}")
                        AssistantAction(
                            action = VoiceActions.UNKNOWN,
                            speech = "I couldn't reach the server. Please try again."
                        )
                    }
                }
            }

            executeAction(command, action)
        }
    }

    private fun executeAction(command: String, action: AssistantAction) {
        Log.d(TAG, "Executing Action: ${action.action}, target: ${action.target}, speech: '${action.speech}'")
        val activeScreen = ScreenActionRegistry.getActiveScreen()
        val speechText = action.speech ?: ""

        if (speechText.isNotBlank()) {
            lastResponseText = speechText
            _voiceState.value = _voiceState.value.copy(
                lastResponse = speechText,
                message = speechText
            )
        }

        when (action.action) {
            VoiceActions.OPEN_FEATURE -> {
                val route = VoiceActionRouter.routeForTarget(action.target)
                _voiceState.value = _voiceState.value.copy(status = VoiceStatus.NAVIGATING)
                navigationHandler?.invoke(route)
                speakThenResume(speechText.ifBlank { "Opening ${action.target}" })
            }

            VoiceActions.GO_HOME -> {
                _voiceState.value = _voiceState.value.copy(status = VoiceStatus.NAVIGATING)
                navigationHandler?.invoke("home")
                speakThenResume(speechText.ifBlank { "Going home." })
            }

            VoiceActions.EMERGENCY_SOS -> {
                _voiceState.value = _voiceState.value.copy(status = VoiceStatus.NAVIGATING)
                navigationHandler?.invoke("sos")
                speakThenResume(speechText.ifBlank { "Starting Emergency SOS." })
            }

            VoiceActions.START_VOLUNTEER_HELP -> {
                _voiceState.value = _voiceState.value.copy(status = VoiceStatus.NAVIGATING)
                navigationHandler?.invoke("volunteer")
                speakThenResume(speechText.ifBlank { "Connecting you with a volunteer." })
            }

            VoiceActions.FIND_OBJECT -> {
                val objectName = action.objectName ?: ""
                if (activeScreen == "finder") {
                    ScreenActionRegistry.executeFindObject(objectName)
                    speakThenResume(speechText.ifBlank { "Searching for $objectName." })
                } else {
                    val route = VoiceActionRouter.routeForTarget(VoiceFeatures.OBJECT_FINDER, objectName)
                    _voiceState.value = _voiceState.value.copy(status = VoiceStatus.NAVIGATING)
                    navigationHandler?.invoke(route)
                    speakThenResume(speechText.ifBlank { "Looking for your $objectName." })
                }
            }

            VoiceActions.CAPTURE_IMAGE -> {
                if (ScreenActionRegistry.canCapture()) {
                    _voiceState.value = _voiceState.value.copy(status = VoiceStatus.SPEAKING)
                    tts.speak(speechText.ifBlank { "Capturing." }) {
                        ScreenActionRegistry.executeCapture()
                        resumeListening()
                    }
                } else {
                    speakThenResume("There is nothing to capture on this screen.")
                }
            }

            VoiceActions.CANCEL -> {
                ScreenActionRegistry.executeCancel()
                speakThenResume(speechText.ifBlank { "Cancelled." })
            }

            VoiceActions.CONFIRM -> {
                ScreenActionRegistry.executeSubmit()
                speakThenResume(speechText)
            }

            VoiceActions.REPEAT_LAST -> {
                val toRepeat = if (lastResponseText.isNotBlank()) lastResponseText else "There is nothing to repeat."
                speakThenResume(toRepeat)
            }

            VoiceActions.STOP_SPEAKING -> {
                tts.stop()
                resumeListening()
            }

            VoiceActions.GESTURE_HELP -> {
                speakThenResume(speechText.ifBlank { "Tap anywhere to activate voice, double-tap to read, hold to speak." })
            }

            VoiceActions.ASK_CONTEXTUAL_QUESTION -> {
                speakThenResume(speechText.ifBlank { "I don't have that information right now." })
            }

            VoiceActions.RADIO_PAUSE -> {
                com.example.visionbridge.audio.EntertainmentMediaService.instance?.pausePlayback()
                com.example.visionbridge.audio.StoryChunkedTtsPlayer.getInstance(context).pause()
                speakThenResume(speechText.ifBlank { "Paused." })
            }

            VoiceActions.RADIO_RESUME -> {
                com.example.visionbridge.audio.EntertainmentMediaService.instance?.resumePlayback()
                com.example.visionbridge.audio.StoryChunkedTtsPlayer.getInstance(context).resume()
                speakThenResume(speechText.ifBlank { "Resumed." })
            }

            VoiceActions.RADIO_STOP -> {
                com.example.visionbridge.audio.EntertainmentMediaService.instance?.stopPlayback()
                com.example.visionbridge.audio.StoryChunkedTtsPlayer.getInstance(context).stop()
                speakThenResume(speechText.ifBlank { "Stopped." })
            }

            VoiceActions.LIVE_RADIO_INFO -> {
                val currentSt = com.example.visionbridge.audio.EntertainmentMediaService.currentStationFlow.value
                val infoText = if (currentSt != null) {
                    "Now playing ${currentSt.name}."
                } else {
                    val currentStory = com.example.visionbridge.audio.EntertainmentMediaService.currentStoryFlow.value
                    if (currentStory != null) {
                        "Now playing ${currentStory.title}."
                    } else {
                        "Nothing is currently playing."
                    }
                }
                speakThenResume(infoText)
            }

            VoiceActions.LIVE_RADIO_NEXT -> {
                val service = com.example.visionbridge.audio.EntertainmentMediaService.instance
                val stations = com.example.visionbridge.audio.EntertainmentMediaService.stationsListFlow.value
                val current = com.example.visionbridge.audio.EntertainmentMediaService.currentStationFlow.value
                if (service != null && stations.isNotEmpty()) {
                    val currentIdx = stations.indexOfFirst { it.id == current?.id }
                    val nextIdx = if (currentIdx >= 0) (currentIdx + 1) % stations.size else 0
                    service.playRadioStation(stations[nextIdx])
                    speakThenResume("Playing ${stations[nextIdx].name}.")
                } else {
                    speakThenResume("No radio stations available.")
                }
            }

            VoiceActions.LIVE_RADIO_PREV -> {
                val service = com.example.visionbridge.audio.EntertainmentMediaService.instance
                val stations = com.example.visionbridge.audio.EntertainmentMediaService.stationsListFlow.value
                val current = com.example.visionbridge.audio.EntertainmentMediaService.currentStationFlow.value
                if (service != null && stations.isNotEmpty()) {
                    val currentIdx = stations.indexOfFirst { it.id == current?.id }
                    val prevIdx = if (currentIdx > 0) currentIdx - 1 else stations.size - 1
                    service.playRadioStation(stations[prevIdx])
                    speakThenResume("Playing ${stations[prevIdx].name}.")
                } else {
                    speakThenResume("No radio stations available.")
                }
            }

            VoiceActions.STORY_NEXT_CHAPTER -> {
                val ok = com.example.visionbridge.audio.StoryChunkedTtsPlayer.getInstance(context).nextChapter()
                if (ok) {
                    speakThenResume("Next chapter.")
                } else {
                    speakThenResume("Reached the end of the story.")
                }
            }

            VoiceActions.STORY_PREV_CHAPTER -> {
                val ok = com.example.visionbridge.audio.StoryChunkedTtsPlayer.getInstance(context).prevChapter()
                if (ok) {
                    speakThenResume("Previous chapter.")
                } else {
                    speakThenResume("Already at the first chapter.")
                }
            }

            else -> {
                speakThenResume(speechText.ifBlank { "I didn't understand that command. Please try again." })
            }
        }
    }

    private fun speakThenResume(text: String) {
        if (text.isBlank()) {
            resumeListening()
            return
        }

        _voiceState.value = _voiceState.value.copy(
            status = VoiceStatus.SPEAKING,
            message = text
        )

        // Duck background media audio during assistant speech
        com.example.visionbridge.audio.EntertainmentAudioSession.setDucked(true)

        tts.speak(text) {
            com.example.visionbridge.audio.EntertainmentAudioSession.setDucked(false)
            resumeListening()
        }
    }

    private fun resumeListening() {
        isBusy = false
        if (!wantListening || isPausedForCall || isActivityPaused) {
            _voiceState.value = _voiceState.value.copy(
                status = if (isPausedForCall || isActivityPaused) VoiceStatus.PAUSED else VoiceStatus.IDLE
            )
            return
        }

        currentPhase = Phase.WAKE
        _voiceState.value = _voiceState.value.copy(
            status = VoiceStatus.LISTENING,
            transcript = "",
            error = ""
        )
        scheduleRestart(RESTART_DELAY_MS)
    }

    companion object {
        private const val TAG = "VB-VoiceManager"
        private const val RESTART_DELAY_MS = 300L
        private const val ERROR_RESTART_DELAY_MS = 600L
        private const val COMMAND_TIMEOUT_MS = 9000L

        @Volatile
        private var instance: VoiceManager? = null

        fun getInstance(context: Context): VoiceManager {
            return instance ?: synchronized(this) {
                instance ?: VoiceManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
