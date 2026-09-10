package com.example.visionbridge.ai

import android.util.Log

/**
 * Evaluates raw AI confidence scores against standardized thresholds.
 * Centralizes reliability scoring across VisionBridge AI features.
 *
 * Behavioral reference:
 * In the VisionBridge Web App, CONFIDENCE_THRESHOLD = 0.70 is used uniformly across
 * Camera Assistant, Currency, Reading, Object Finder, and Transport.
 * Scores < 0.70 trigger the low-confidence volunteer handoff state.
 */
object ConfidenceEvaluator {

    private const val TAG = "VB-Confidence"

    /** Web App reference threshold below which human assistance is proactively offered */
    const val DEFAULT_LOW_THRESHOLD = 0.70

    /** Threshold above which the answer is presented with full, unqualified confidence */
    const val DEFAULT_HIGH_THRESHOLD = 0.85

    /**
     * Evaluates a confidence score into a ConfidenceLevel tier.
     * Clamps input between 0.0 and 1.0. Null scores default to LOW.
     */
    fun evaluate(
        confidence: Double?,
        lowThreshold: Double = DEFAULT_LOW_THRESHOLD,
        highThreshold: Double = DEFAULT_HIGH_THRESHOLD
    ): ConfidenceLevel {
        if (confidence == null) {
            Log.w(TAG, "[AI_CONFIDENCE] Confidence score is null -> defaulting to LOW")
            return ConfidenceLevel.LOW
        }

        val clamped = confidence.coerceIn(0.0, 1.0)
        val level = when {
            clamped < lowThreshold -> ConfidenceLevel.LOW
            clamped < highThreshold -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.HIGH
        }

        Log.i(TAG, "[AI_CONFIDENCE] Evaluated score=$clamped -> $level (low=$lowThreshold, high=$highThreshold)")
        return level
    }

    /**
     * Quick check whether the AI result is reliable enough to present without fallback.
     */
    fun isReliable(confidence: Double?, threshold: Double = DEFAULT_LOW_THRESHOLD): Boolean {
        return evaluate(confidence, lowThreshold = threshold) != ConfidenceLevel.LOW
    }
}
