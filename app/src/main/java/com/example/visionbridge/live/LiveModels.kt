package com.example.visionbridge.live

import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Operating mode for the shared Live infrastructure.
 */
enum class LiveMode {
    VISION,     // Real-time camera + microphone + Gemini Live
    VOICE       // Microphone + Gemini Live general conversational assistant (audio-only)
}

/**
 * Granular state model for Live sessions.
 */
enum class LiveState {
    IDLE,
    STARTING,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    INTERRUPTED,
    MUTED,
    RECONNECTING,
    ERROR,
    STOPPING,
    STOPPED
}

/**
 * Adaptive camera visual quality modes for Vision Live.
 */
enum class VisualQualityMode {
    FAST_LIVE,      // 800px max dimension, 0.72 quality (~1200ms background interval)
    HIGH_DETAIL,    // 1280px max dimension, 0.85 quality on-demand for OCR
    VOICE_CALL      // Camera inactive
}

/**
 * Parsed ephemeral session details returned by Render backend (POST /api/live/session).
 */
data class LiveSessionData(
    val ephemeralToken: String?,
    val endpoint: String,
    val mode: String,
    val model: String,
    val voice: String,
    val language: String,
    val expireTime: String?,
    val setupPayload: JSONObject
)

/**
 * Live conversational turn history item.
 */
data class ConversationTurn(
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Real-time diagnostic statistics for development, logging, and monitoring.
 */
data class LiveDiagnostics(
    val model: String = "",
    val connectedAt: Long? = null,
    val setupCompleteReceived: Boolean = false,
    val reconnectCount: Int = 0,
    val lastCloseCode: Int? = null,
    val lastCloseReason: String = "",
    val lastTtfbMs: Long? = null,
    val visualMode: VisualQualityMode = VisualQualityMode.FAST_LIVE,
    val audioChunksSent: Long = 0,
    val micLevel: Float = 0f,
    val micActive: Boolean = false,
    val echoCancellation: Boolean = true,
    val noiseSuppression: Boolean = true,
    val autoGainControl: Boolean = true,
    val isAiSpeaking: Boolean = false
)

/**
 * Multilingual natural language intent detection for text/OCR/numbers.
 * Matches English, Hindi, and Marathi reading/OCR queries to automatically elevate
 * camera capture to High-Detail (1280px) mode.
 */
object ReadingIntentDetector {

    private val READING_PATTERNS = listOf(
        Pattern.compile(
            "\\b(read|reading|says|written|text|sign|signboard|menu|label|bottle|medicine|expiry|date|number|price|cost|bus|train|ticket|document|page|book|paper|poster|board|headline|brand|name)\\b",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(पढ़ो|पढो|पढ़ना|लिखा|मजकूर|बोर्ड|दुकान|दवा|कीमत|मूल्य|नंबर|तारीख|मेन्यू|कागज|पर्चा|अक्षर)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(वाचा|वाचणे|लिहिलं|पाटी|मजकूर|किंमत|तारीख|औषध|बस क्रमांक|तिकीट|कागद)",
            Pattern.CASE_INSENSITIVE
        )
    )

    fun isReadingIntent(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        return READING_PATTERNS.any { it.matcher(trimmed).find() }
    }
}
