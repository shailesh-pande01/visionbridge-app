package com.example.visionbridge.voice

/**
 * Explicit domain intent types supported by the VisionBridge Voice Command system.
 */
enum class CommandIntentType {
    OPEN_HOME,
    OPEN_SMART_READING,
    OPEN_SURROUNDINGS,
    OPEN_HAZARD_DETECTION,
    OPEN_OBJECT_FINDER,
    OPEN_CURRENCY_READER,
    OPEN_TRANSPORT,
    OPEN_LOCATION,
    OPEN_VOLUNTEER,
    OPEN_SOS,
    OPEN_VISIONBRIDGE_LIVE,
    OPEN_VOICE_CALL,
    OPEN_CALLING,
    OPEN_NEWS,
    OPEN_ENTERTAINMENT,
    OPEN_RADIO,
    OPEN_STORIES,
    OPEN_GAMES,
    OPEN_PROGRESS,
    OPEN_PROFILE_SETTINGS,
    CAPTURE_IMAGE,
    ASK_CONTEXTUAL_QUESTION,
    REPLAY_READING,
    STOP_SPEAKING,
    CANCEL,
    CONFIRM,
    REPEAT_LAST,
    GESTURE_HELP,
    RADIO_CONTROL,
    STORY_CONTROL,
    NEWS_CONTROL,
    CLARIFICATION,
    UNKNOWN
}

/**
 * Structured model representing the interpreted intent of a user's voice command.
 */
data class VoiceCommandIntent(
    val intentType: CommandIntentType,
    val targetFeature: String? = null,
    val confidence: Double = 1.0,
    val parameters: Map<String, String> = emptyMap(),
    val spokenFeedback: String? = null,
    val clarificationPrompt: String? = null,
    val secondaryIntent: CommandIntentType? = null
)
