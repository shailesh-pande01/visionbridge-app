package com.example.visionbridge.ai

/**
 * Represents the reliability tier of an AI analysis.
 * Matches web app reference semantics:
 * - HIGH: Answer is presented directly with normal flow.
 * - MEDIUM: Answer is presented with qualifying context; volunteer help available optionally.
 * - LOW: Answer is not stated as fact; user is prompted with human fallback offer.
 */
enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Contextual payload passed when an AI feature triggers human fallback.
 * Preserved and relayed to Volunteer Help and WebRTC call sessions so volunteers
 * understand why help was requested.
 */
data class FallbackContext(
    val sourceFeature: String,
    val confidence: Double?,
    val level: ConfidenceLevel,
    val reason: String,
    val originalPrompt: String? = null
) {
    /**
     * Formats a clean, descriptive summary suitable for help request descriptions.
     */
    fun toHelpDescription(): String {
        val confidencePct = confidence?.let { "${(it * 100).toInt()}%" } ?: "unknown"
        return "[AI Fallback - $sourceFeature] $reason (AI Confidence: $confidencePct)"
    }
}

/**
 * Generic container pairing an AI result with its reliability evaluation.
 */
data class EvaluatedAiResult<T>(
    val data: T,
    val confidence: Double?,
    val level: ConfidenceLevel,
    val shouldOfferHumanHelp: Boolean,
    val summarySpeech: String,
    val reason: String? = null
)
