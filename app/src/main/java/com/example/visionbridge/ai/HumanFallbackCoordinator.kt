package com.example.visionbridge.ai

import android.content.Context
import android.util.Log
import com.example.visionbridge.R

/**
 * Orchestrates the human fallback flow across AI features.
 * Decides when to prompt the user, builds structured volunteer request metadata,
 * and maintains session state to prevent repetitive unwanted volunteer prompts.
 */
class HumanFallbackCoordinator(val featureName: String) {

    private val declinedSignatures = mutableSetOf<String>()
    private var lastOfferedSignature: String? = null

    /**
     * Determines whether fallback should be offered for the given analysis signature.
     * Prevents prompting repeatedly for the exact same result if the user already declined.
     */
    fun shouldOfferFallback(confidence: Double?, resultSignature: String): Boolean {
        val level = ConfidenceEvaluator.evaluate(confidence)
        if (level != ConfidenceLevel.LOW) {
            return false
        }

        if (declinedSignatures.contains(resultSignature)) {
            Log.d(TAG, "[AI_FALLBACK] Fallback previously declined for signature '$resultSignature'. Suppressing unsolicited repeat prompt.")
            return false
        }

        lastOfferedSignature = resultSignature
        Log.i(TAG, "[AI_FALLBACK] Low confidence detected for $featureName. Offering human fallback.")
        return true
    }

    /**
     * Called when user explicitly declines the volunteer prompt ("Vision, no" / "cancel" / "try again").
     */
    fun markFallbackDeclined(resultSignature: String? = lastOfferedSignature) {
        if (resultSignature != null) {
            declinedSignatures.add(resultSignature)
            Log.d(TAG, "[AI_FALLBACK] User declined fallback for $featureName (signature: $resultSignature)")
        }
    }

    /**
     * Resets session state, e.g. when initiating a brand-new capture session.
     */
    fun resetSession() {
        declinedSignatures.clear()
        lastOfferedSignature = null
        Log.d(TAG, "[AI_FALLBACK] Session reset for $featureName")
    }

    /**
     * Creates a FallbackContext payload to be dispatched to Volunteer Help.
     */
    fun buildFallbackContext(
        confidence: Double?,
        reason: String,
        originalPrompt: String? = null
    ): FallbackContext {
        val level = ConfidenceEvaluator.evaluate(confidence)
        val context = FallbackContext(
            sourceFeature = featureName,
            confidence = confidence,
            level = level,
            reason = reason,
            originalPrompt = originalPrompt
        )
        Log.i(TAG, "[HUMAN_HANDOFF] Created fallback context: $context")
        return context
    }

    companion object {
        private const val TAG = "VB-FallbackCoordinator"
        const val REQUEST_TYPE_AI_FALLBACK = "AI_FALLBACK"

        /**
         * Resolves the primary spoken prompt when low confidence is detected for a feature.
         * Matches Web App speech strings:
         * speech.lowConfidenceCamera: "I'm not confident enough to answer accurately. Would you like to connect with a volunteer?"
         */
        fun getSpokenPrompt(context: Context, featureName: String): String {
            return when (featureName.lowercase()) {
                "describe_surroundings", "surroundings", "camera" ->
                    context.getString(R.string.confidence_low_describe_speech)
                else ->
                    context.getString(R.string.confidence_low_prompt) + " " + context.getString(R.string.confidence_ask_volunteer)
            }
        }
    }
}
