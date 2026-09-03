package com.example.visionbridge.gesture

import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.visionbridge.data.ContextMemoryManager
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.voice.ScreenActionRegistry
import com.example.visionbridge.voice.VoiceActivationSource
import com.example.visionbridge.voice.VoiceManager
import com.example.visionbridge.voice.VoiceStatus

/**
 * Centralized coordinator for VisionBridge voice activation gestures.
 *
 * Responsibilities:
 * - Detects and debounces two-finger double-taps and deliberate device shakes.
 * - Enforces feature conflict policies (protecting Gemini Live, AI Voice Call, WebRTC Calls).
 * - Honors user accessibility preferences and toggles.
 * - Triggers haptic confirmation and routes activation directly to VoiceManager.
 */
class GestureController private constructor(private val context: Context) {

    private val sessionManager = SessionManager.getInstance(context)
    private val voiceManager = VoiceManager.getInstance(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var lastActivationTimestamp = 0L
    private var isSensorActive = false

    private val shakeDetector = ShakeDetector {
        onGestureDetected(VoiceActivationSource.GESTURE_SHAKE)
    }

    /**
     * Called when a two-finger double tap is detected on the UI.
     */
    fun onTwoFingerDoubleTap(): Boolean {
        return onGestureDetected(VoiceActivationSource.GESTURE_TOUCH)
    }

    /**
     * Primary gesture activation handler.
     */
    fun onGestureDetected(source: VoiceActivationSource): Boolean {
        val now = System.currentTimeMillis()
        val activeScreen = ContextMemoryManager.activeFeature.ifBlank { ScreenActionRegistry.getActiveScreen() }

        Log.d(TAG_STATE, "Gesture received: source=$source, activeScreen=$activeScreen")

        // 1. Preference Check
        if (!sessionManager.isGestureVoiceEnabled()) {
            Log.d(TAG_REJECTED, "Rejected $source: Gesture voice activation disabled in settings.")
            return false
        }

        if (source == VoiceActivationSource.GESTURE_SHAKE && !sessionManager.isShakeGestureEnabled()) {
            Log.d(TAG_REJECTED, "Rejected $source: Shake gesture disabled in settings.")
            return false
        }

        // 2. Cooldown / Debounce Check
        if (now - lastActivationTimestamp < COOLDOWN_MS) {
            Log.d(TAG_COOLDOWN, "Rejected $source: Cooldown active (${now - lastActivationTimestamp}ms < ${COOLDOWN_MS}ms).")
            return false
        }

        // 3. Exclusive Realtime Audio Feature Conflict Check
        // NEVER steal microphone from active Gemini Live, AI Voice Call, or WebRTC Volunteer Call
        val normalizedScreen = activeScreen.lowercase()
        if (normalizedScreen.contains("livevision") || normalizedScreen.contains("live_vision") ||
            normalizedScreen.contains("voicecall") || normalizedScreen.contains("voice_call") ||
            normalizedScreen.contains("volunteer")
        ) {
            Log.w(TAG_REJECTED, "Rejected $source: Conflict with exclusive realtime session on screen '$activeScreen'.")
            return false
        }

        // 4. Voice Manager State Check
        if (voiceManager.isBusy() || voiceManager.isPausedForCall()) {
            Log.d(TAG_REJECTED, "Rejected $source: VoiceManager is busy or paused for call.")
            return false
        }

        val currentStatus = voiceManager.voiceState.value.status
        if (currentStatus == VoiceStatus.AWAITING_COMMAND || currentStatus == VoiceStatus.PROCESSING) {
            Log.d(TAG_REJECTED, "Rejected $source: VoiceManager already in state $currentStatus.")
            return false
        }

        // 5. Validated Gesture Activation
        lastActivationTimestamp = now
        Log.i(TAG_DETECTED, "ACCEPTED $source on screen '$activeScreen'! Activating voice listening.")

        // Haptic feedback confirmation
        if (sessionManager.isHapticFeedbackEnabled()) {
            performHapticFeedback()
        }

        // Trigger VoiceManager directly into COMMAND phase
        voiceManager.activateVoice(source = source)
        return true
    }

    /**
     * Lifecycle registration: resumes sensors when Activity is resumed.
     */
    fun onResume() {
        if (sessionManager.isGestureVoiceEnabled() && sessionManager.isShakeGestureEnabled()) {
            if (!isSensorActive) {
                isSensorActive = shakeDetector.start(sensorManager)
            }
        } else {
            onPause()
        }
    }

    /**
     * Lifecycle unregistration: pauses sensors when Activity is paused to preserve battery.
     */
    fun onPause() {
        if (isSensorActive) {
            shakeDetector.stop(sensorManager)
            isSensorActive = false
        }
    }

    /**
     * Provides subtle accessible haptic feedback.
     */
    private fun performHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Haptic feedback error", e)
        }
    }

    companion object {
        private const val TAG = "VB_GESTURE"
        private const val TAG_DETECTED = "VB_GESTURE_DETECTED"
        private const val TAG_REJECTED = "VB_GESTURE_REJECTED"
        private const val TAG_COOLDOWN = "VB_GESTURE_COOLDOWN"
        private const val TAG_STATE = "VB_GESTURE_STATE"

        const val COOLDOWN_MS = 1500L

        @Volatile
        private var instance: GestureController? = null

        fun getInstance(context: Context): GestureController {
            return instance ?: synchronized(this) {
                instance ?: GestureController(context.applicationContext).also { instance = it }
            }
        }
    }
}
