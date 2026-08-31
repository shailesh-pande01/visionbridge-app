package com.example.visionbridge.voice

enum class VoiceStatus {
    IDLE,
    LISTENING,           // Ambient Wake-word listening ("Vision")
    AWAITING_COMMAND,    // Wake word detected, listening for command
    PROCESSING,          // Dispatching command (local fast path or backend assistant)
    NAVIGATING,          // Executing navigation action
    SPEAKING,            // Text-to-speech reading response aloud
    PAUSED,              // In a WebRTC call or app in background
    BLOCKED,             // Microphone permission denied
    UNSUPPORTED,         // Speech recognition not available on device
    ERROR
}

data class VoiceState(
    val status: VoiceStatus = VoiceStatus.IDLE,
    val transcript: String = "",
    val message: String = "",
    val lastResponse: String = "",
    val error: String = ""
)
