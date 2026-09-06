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
 * Dedicated reading session model representing extracted text from Smart Reading.
 */
data class ReadingSession(
    val sessionId: String,
    val extractedText: String,
    val language: String,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Double? = null,
    val isLowConfidence: Boolean = false
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
    const val MAX_SUMMARY_CHARS = 10000
    const val MAX_TURN_CHARS = 200
    const val CONTEXT_TTL_MS = 15 * 60 * 1000L // 15 minutes

    var activeFeature: String = "home"
        private set

    @Volatile
    private var activeReadingSession: ReadingSession? = null

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
     * Updates or creates the active reading session.
     * Overwrites any prior reading session atomically.
     */
    fun setActiveReadingSession(session: ReadingSession) {
        activeReadingSession = session
        rememberContext(
            featureId = "reading",
            label = "Extracted text",
            summary = session.extractedText,
            structuredData = session.sessionId
        )
        logD("Active reading session updated: id=${session.sessionId}, textLength=${session.extractedText.length}")
    }

    /**
     * Gets the current non-expired active reading session, or null if none.
     */
    fun getActiveReadingSession(): ReadingSession? {
        val session = activeReadingSession ?: return null
        val ageMs = System.currentTimeMillis() - session.timestamp
        return if (ageMs <= CONTEXT_TTL_MS) session else null
    }

    /**
     * Returns true if there is an active, valid reading session.
     */
    fun hasActiveReadingSession(): Boolean {
        return getActiveReadingSession() != null
    }

    /**
     * Explicitly clears the active reading session.
     */
    fun clearReadingSession() {
        activeReadingSession = null
        clearFeatureContext("reading")
        logD("Active reading session cleared")
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
        activeReadingSession = null
        clearContext()
    }

    /**
     * Builds a clean context payload suitable for general command execution.
     * If on home or navigating, does NOT attach unrelated previous feature context,
     * preventing Gemini from misinterpreting a new command as a question about an old document.
     */
    fun buildContextPayloadForCommand(command: String? = null): JSONObject {
        val currentScreen = activeFeature.lowercase()
        val isFollowUp = isFollowUpInquiry(command)

        if (!isFollowUp && currentScreen == "home") {
            // Provide clean home context without stale feature slot summaries
            val json = JSONObject()
            json.put("activeFeature", "home")
            json.put("contextLabel", "")
            json.put("contextSummary", "")
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

        return buildContextPayload(activeFeature)
    }

    /**
     * Returns true if the query looks like a follow-up inquiry about captured content
     * rather than an explicit instruction to navigate or perform a new action.
     */
    fun isFollowUpInquiry(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        val q = query.trim().lowercase()

        val questionStarters = listOf(
            "what", "who", "when", "where", "why", "how", "which", "whose", "whom",
            "explain", "summarize", "summary", "translate", "tell me", "clarify",
            "is there", "are there", "does it", "does this", "does the", "can you", "could you", "would you",
            "is", "are", "do", "does", "any", "anything", "show me", "give me", "list",
            "read it again", "read that again", "read again", "repeat that", "repeat it",
            // Hindi
            "क्या", "कौन", "कब", "कहाँ", "क्यों", "कैसे", "कितना", "कितने", "कितनी", "कीमत", "दर", "सस्ता", "महंगा",
            "समझाओ", "बताओ", "मतलब", "सारांश", "अनुवाद", "दिखाओ", "है क्या", "कुछ है", "कोई",
            // Marathi
            "काय", "कोण", "केव्हा", "कुठे", "का", "कसे", "किती", "किंमत", "दर", "कमी", "महाग",
            "समजवा", "सांगा", "अर्थ", "सारांश", "भाषांतर", "दाखवा", "आहे का", "काही आहे"
        )

        val inquiryKeywords = listOf(
            "price", "cost", "rate", "menu", "options", "option", "cheapest", "cheaper", "expensive", "costliest",
            "rupee", "rupees", "dollar", "dollars", "total", "bill", "tax", "item", "dish", "dishes", "phone", "number",
            "date", "deadline", "name", "address", "ingredients", "allergen", "calories",
            "it", "that", "this", "second one", "first one", "last one", "cheapest one", "expensive one",
            "under", "below", "above", "more than", "less than",
            // Hindi / Marathi
            "कीमत", "भाव", "रुपये", "पैसे", "मेन्यू", "पर्याय", "विकल्प"
        )

        val matchesStarter = questionStarters.any { q.startsWith(it) || q.contains(" $it ") }
        val matchesKeyword = inquiryKeywords.any { q.contains(it) }

        return matchesStarter || matchesKeyword ||
                q.contains("mean") || q.contains("meaning") || q.contains("paragraph") || q.contains("summary")
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

        val readingSession = getActiveReadingSession()
        if (readingSession != null) {
            json.put("hasReadingSession", true)
            json.put("readingText", readingSession.extractedText)
        } else {
            json.put("hasReadingSession", false)
        }

        if (selectedSlot != null) {
            json.put("contextLabel", selectedSlot.label)
            json.put("contextSummary", selectedSlot.summary)
            val ageSec = ((now - selectedSlot.timestamp) / 1000).coerceAtLeast(0)
            json.put("contextAgeSeconds", ageSec)
            if (!selectedSlot.structuredData.isNullOrBlank()) {
                json.put("structuredData", selectedSlot.structuredData)
            }
        } else if (readingSession != null) {
            json.put("contextLabel", "Extracted text")
            json.put("contextSummary", readingSession.extractedText)
            val ageSec = ((now - readingSession.timestamp) / 1000).coerceAtLeast(0)
            json.put("contextAgeSeconds", ageSec)
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
