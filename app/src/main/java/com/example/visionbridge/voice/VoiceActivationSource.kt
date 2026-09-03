package com.example.visionbridge.voice

/**
 * Origin source that triggered voice command recognition.
 * Used for structured diagnostics and telemetry.
 */
enum class VoiceActivationSource {
    WAKE_WORD,
    GESTURE_TOUCH,
    GESTURE_SHAKE,
    BUTTON
}
