package com.example.visionbridge.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Maintains lightweight, feature-scoped session context.
 * Used by the conversational AI assistant to answer follow-up questions
 * (e.g. "What is the price of paneer butter masala?") without reprocessing the image.
 */
object ContextMemoryManager {

    private const val MAX_TURNS = 3
    private const val MAX_SUMMARY_CHARS = 1500

    var activeFeature: String = "home"
        private set

    var contextLabel: String = ""
        private set

    var contextSummary: String = ""
        private set

    private var lastContextTimestamp: Long = System.currentTimeMillis()

    private val recentTurns = mutableListOf<ConversationTurn>()

    fun setContext(feature: String, label: String, summary: String) {
        activeFeature = feature
        contextLabel = label
        contextSummary = summary.trim().take(MAX_SUMMARY_CHARS)
        lastContextTimestamp = System.currentTimeMillis()
    }

    fun addTurn(userUtterance: String, assistantResponse: String) {
        if (userUtterance.isBlank() && assistantResponse.isBlank()) return
        recentTurns.add(ConversationTurn(userUtterance.trim().take(200), assistantResponse.trim().take(200)))
        while (recentTurns.size > MAX_TURNS) {
            recentTurns.removeAt(0)
        }
    }

    fun clearContext() {
        contextLabel = ""
        contextSummary = ""
        recentTurns.clear()
        lastContextTimestamp = System.currentTimeMillis()
    }

    fun setActiveScreen(feature: String) {
        if (activeFeature != feature) {
            activeFeature = feature
            clearContext()
        }
    }

    /**
     * Serializes context to the format expected by the MERN backend:
     * POST /api/assistant/command and POST /api/assistant/ask
     */
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("activeFeature", activeFeature)
        if (contextLabel.isNotBlank()) json.put("contextLabel", contextLabel)
        if (contextSummary.isNotBlank()) json.put("contextSummary", contextSummary)

        val ageSec = ((System.currentTimeMillis() - lastContextTimestamp) / 1000).coerceAtLeast(0)
        json.put("contextAgeSeconds", ageSec)

        val turnsArray = JSONArray()
        for (turn in recentTurns) {
            val turnObj = JSONObject()
            turnObj.put("user", turn.user)
            turnObj.put("assistant", turn.assistant)
            turnsArray.put(turnObj)
        }
        json.put("recentTurns", turnsArray)

        return json
    }
}
