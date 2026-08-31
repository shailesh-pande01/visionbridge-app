package com.example.visionbridge.voice

import java.util.regex.Pattern

data class WakeWordResult(
    val command: String,
    val matchedWakeWord: String
)

object WakeWordMatcher {

    /**
     * Matches "Vision" and common phonetic / speech recognition transcriptions in Latin and Devanagari.
     * Optional politeness prefixes: "hey", "ok", "okay", "अरे", "ओके", "हे".
     */
    private val WAKE_PATTERN = Pattern.compile(
        "(?:^|\\s)(?:hey\\s+|ok\\s+|okay\\s+|अरे\\s+|ओके\\s+|हे\\s+)?(vision|visions|vison|wision|vishon|विजन|विज़न|वीजन|वीज़न|व्हिजन|व्हीजन|विझन|विशन|बिजन|विजान|विज़ान)(?:[\\s,.:;!?।॥]|$)",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    )

    /**
     * Splits [transcript] on the LAST occurrence of the wake word.
     * Returns null if no wake word was heard.
     * Returns a [WakeWordResult] with [command] (can be empty string if user said only the wake word).
     */
    fun splitOnWakeWord(transcript: String?): WakeWordResult? {
        if (transcript.isNullOrBlank()) return null

        val padded = " ${transcript.trim()} "
        val matcher = WAKE_PATTERN.matcher(padded)

        var lastStart = -1
        var lastEnd = -1
        var matchedWord = ""

        while (matcher.find()) {
            lastStart = matcher.start()
            lastEnd = matcher.end()
            matchedWord = matcher.group(1) ?: "vision"
        }

        if (lastStart == -1 || lastEnd == -1) {
            return null
        }

        val rawCommand = padded.substring(lastEnd)
        // Clean leading and trailing punctuation and whitespace
        val cleanedCommand = rawCommand
            .replace(Regex("^[\\s,.:;!?।॥-]+"), "")
            .replace(Regex("[\\s,.:;!?।॥-]+$"), "")
            .trim()

        return WakeWordResult(
            command = cleanedCommand,
            matchedWakeWord = matchedWord
        )
    }

    /**
     * Checks if the transcript contains the wake word.
     */
    fun containsWakeWord(transcript: String?): Boolean {
        return splitOnWakeWord(transcript) != null
    }

    /**
     * Normalizes text for comparison.
     */
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
