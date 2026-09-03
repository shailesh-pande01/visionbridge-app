package com.example.visionbridge.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Feature-scoped contextual memory slot.
 */
data class FeatureContextSlot(
    val featureId: String,
    val label: String,
    val summary: String,
    val structuredData: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Maintains lightweight, multi-slot feature-scoped session context.
 *
 * Guarantees:
 * 1. Multi-Slot Persistence: Retains outputs from Smart Reading, Surroundings, Currency,
 *    Transport, Object Finder, Location, and News in distinct slots.
 * 2. Non-Destructive Navigation: Navigating between screens (e.g. from Reading to Home)
 *    does NOT purge previous feature results, allowing natural follow-up questions anywhere.
 * 3. Freshness & TTL: Slots older than [CONTEXT_TTL_MS] (15 mins) expire naturally.
 * 4. Safe Caps: Summaries capped at 1500 chars, turns capped at 4 turns / 200 chars.
 * 5. Serialization: Produces compact JSON matching both the MERN backend and Supabase Edge Functions.
 */
object ContextMemoryManager {

    private const val TAG = "VB_VOICE_CONTEXT"
    const val MAX_TURNS = 4
    const val MAX_SUMMARY_CHARS = 1500
    const val MAX_TURN_CHARS = 200
    const val CONTEXT_TTL_MS = 15 * 60 * 1000L // 15 minutes

    var activeFeature: String = "home"
        private set

    private val featureSlots = ConcurrentHashMap<String, FeatureContextSlot>()
    private val recentTurns = mutableListOf<ConversationTurn>()
    private val lock = Any()

    private fun logD(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            // Unit test JVM fallback
        }
    }

    /**
     * Stores or updates the memory slot for a specific feature.
     * Replaces any existing context for that feature.
     */
    fun rememberContext(
        featureId: String,
        label: String,
        summary: String,
        structuredData: String? = null
    ) {
        if (summary.isBlank() && structuredData.isNullOrBlank()) return

        val cleanedSummary = summary.trim().take(MAX_SUMMARY_CHARS)
        val cleanedLabel = label.trim().take(100)
        val slot = FeatureContextSlot(
            featureId = featureId.lowercase(),
            label = cleanedLabel,
            summary = cleanedSummary,
            structuredData = structuredData,
            timestamp = System.currentTimeMillis()
        )

        featureSlots[featureId.lowercase()] = slot
        logD("Remembered context for feature='$featureId' (label='$cleanedLabel', length=${cleanedSummary.length} chars)")
    }

    /**
     * Compatibility overload matching existing screen calls.
     */
    fun setContext(feature: String, label: String, summary: String) {
        rememberContext(feature, label, summary)
    }

    /**
     * Gets the latest valid context for [featureId], or null if none/expired.
     */
    fun getSlot(featureId: String): FeatureContextSlot? {
        val slot = featureSlots[featureId.lowercase()] ?: return null
        val ageMs = System.currentTimeMillis() - slot.timestamp
        return if (ageMs <= CONTEXT_TTL_MS) slot else null
    }

    /**
     * Returns true if there is non-expired context for [featureId].
     */
    fun hasContextFor(featureId: String): Boolean {
        return getSlot(featureId) != null
    }

    /**
     * Appends one conversation turn, keeping only the most recent few.
     */
    fun addTurn(userUtterance: String, assistantResponse: String) {
        if (userUtterance.isBlank() && assistantResponse.isBlank()) return
        synchronized(lock) {
            recentTurns.add(
                ConversationTurn(
                    user = userUtterance.trim().take(MAX_TURN_CHARS),
                    assistant = assistantResponse.trim().take(MAX_TURN_CHARS)
                )
            )
            while (recentTurns.size > MAX_TURNS) {
                recentTurns.removeAt(0)
            }
        }
    }

    /**
     * Updates active screen without wiping stored feature memory slots.
     */
    fun setActiveScreen(screenId: String) {
        activeFeature = screenId.lowercase()
        logD("Active screen changed to '$activeFeature'")
    }

    /**
     * Clears context for one specific feature.
     */
    fun clearFeatureContext(featureId: String) {
        featureSlots.remove(featureId.lowercase())
        logD("Cleared context for feature='$featureId'")
    }

    /**
     * Clears all memory slots and conversation history (e.g. on user logout).
     */
    fun clearContext() {
        featureSlots.clear()
        synchronized(lock) {
            recentTurns.clear()
        }
        logD("Cleared all context memory")
    }

    /**
     * Full reset (active screen set to home, all slots cleared).
     */
    fun reset() {
        activeFeature = "home"
        clearContext()
    }

    /**
     * Builds the relevant context payload for Gemini.
     *
     * Rules:
     * 1. If [targetFeature] has a valid non-expired slot, use it.
     * 2. If [targetFeature] has no slot (e.g. "home", "volunteer"), fall back to the
     *    most recently updated non-expired slot among all features so the user can ask
     *    follow-up questions from anywhere in the app.
     */
    fun buildContextPayload(targetFeature: String? = null): JSONObject {
        val currentScreen = (targetFeature ?: activeFeature).lowercase()
        val now = System.currentTimeMillis()

        var selectedSlot = getSlot(currentScreen)

        // Fallback: On screens without visual/extracted context (e.g. home, volunteer, dialer),
        // pick the most recent valid slot so questions like "How much was the pizza?" work from home.
        if (selectedSlot == null) {
            selectedSlot = featureSlots.values
                .filter { (now - it.timestamp) <= CONTEXT_TTL_MS }
                .maxByOrNull { it.timestamp }
        }

        val json = JSONObject()
        json.put("activeFeature", currentScreen)

        if (selectedSlot != null) {
            json.put("contextLabel", selectedSlot.label)
            json.put("contextSummary", selectedSlot.summary)
            val ageSec = ((now - selectedSlot.timestamp) / 1000).coerceAtLeast(0)
            json.put("contextAgeSeconds", ageSec)
            if (!selectedSlot.structuredData.isNullOrBlank()) {
                json.put("structuredData", selectedSlot.structuredData)
            }
        } else {
            json.put("contextLabel", "")
            json.put("contextSummary", "")
        }

        val turnsArray = JSONArray()
        synchronized(lock) {
            for (turn in recentTurns) {
                val turnObj = JSONObject()
                turnObj.put("user", turn.user)
                turnObj.put("assistant", turn.assistant)
                turnsArray.put(turnObj)
            }
        }
        json.put("recentTurns", turnsArray)

        return json
    }

    /**
     * Serializes context for API calls.
     */
    fun toJson(): JSONObject {
        return buildContextPayload(activeFeature)
    }

    // Direct property getters for backward compatibility / inspection
    val contextLabel: String
        get() = getSlot(activeFeature)?.label ?: ""

    val contextSummary: String
        get() = getSlot(activeFeature)?.summary ?: ""
}
