package com.example.visionbridge.voice

import java.util.regex.Pattern

/**
 * Normalizes speech recognition transcripts into clean, predictable phrases.
 *
 * Responsibilities:
 * - Cleans punctuation, casing, diacritics, extra whitespace.
 * - Strips wake word residues ("vision", "hey vision", "विज़न", "व्हिजन").
 * - Handles common speech recognition phonetic variances ("object finer" -> "object finder").
 * - Strips conversational filler and politeness prefixes ("please", "can you", "could you", "i want to")
 *   while preserving parameters and short safety phrases ("help me").
 */
object VoiceCommandNormalizer {

    private val APOSTROPHE_REGEX = Regex("['’‘]")
    private val PUNCTUATION_REGEX = Regex("[.,!?;:।॥\"`~@#$%^&*()_+=<>{}\\[\\]|/\\\\-]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    // Phonetic corrections for common speech-to-text recognition errors
    private val PHONETIC_REPLACEMENTS = listOf(
        Regex("\\bobject finer\\b", RegexOption.IGNORE_CASE) to "object finder",
        Regex("\\bsmart reeding\\b", RegexOption.IGNORE_CASE) to "smart reading",
        Regex("\\bopen reeding\\b", RegexOption.IGNORE_CASE) to "open reading",
        Regex("\\bcamer assistant\\b", RegexOption.IGNORE_CASE) to "camera assistant",
        Regex("\\blive visi?on\\b", RegexOption.IGNORE_CASE) to "live vision",
        Regex("\\bvolenteer\\b", RegexOption.IGNORE_CASE) to "volunteer",
        Regex("\\bvolunter\\b", RegexOption.IGNORE_CASE) to "volunteer"
    )

    // Polite / conversational prefixes in English, Hindi, and Marathi
    // Order from longest to shortest to prevent prefix collisions
    private val CONVERSATIONAL_PREFIX_PATTERNS = listOf(
        // English prefixes
        Pattern.compile("^(?:could you please|can you please|please can you|please could you|would you please)\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(?:i would like you to|i'd like you to|i would like to|i'd like to)\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(?:i want you to|i need you to|i want to|i need to)\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(?:can you|could you|would you|will you|please|kindly)\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(?:help me to|go ahead and|try to|just)\\s+", Pattern.CASE_INSENSITIVE),
        // Hindi prefixes
        Pattern.compile("^(?:कृपया क्या आप|क्या आप कृपया|कृपया मुझे|क्या आप|कृपया|जरा)\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(?:मुझे चाहिए कि आप|मुझे चाहिए कि|मुझे ऐसा लगता है कि)\\s+", Pattern.CASE_INSENSITIVE),
        // Marathi prefixes
        Pattern.compile("^(?:कृपया तुम्ही|तुम्ही कृपया|कृपया मला|तुम्ही|कृपया|जरा)\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(?:मला पाहिजे की तुम्ही|मला वाटतं की)\\s+", Pattern.CASE_INSENSITIVE)
    )

    /**
     * Primary normalization method.
     */
    fun normalize(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""

        var text = rawText.trim()

        // 1. Remove wake word prefixes
        text = stripWakeWord(text)

        // 2. Normalize apostrophes (e.g. "what's" -> "whats", "i'm" -> "im") and punctuation
        text = text.replace(APOSTROPHE_REGEX, "")
            .replace(PUNCTUATION_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .lowercase()

        // 3. Apply speech recognition phonetic corrections
        for ((regex, replacement) in PHONETIC_REPLACEMENTS) {
            text = text.replace(regex, replacement)
        }

        return text
    }

    /**
     * Normalizes text and strips conversational filler prefixes if a substantial command remains.
     */
    fun normalizeAndStripFillers(rawText: String?): String {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) return ""

        // Preserve single short phrases that have special meaning (e.g. "help me", "please")
        if (normalized == "help me" || normalized == "help" || normalized == "please" ||
            normalized == "मदद करो" || normalized == "मदत करा"
        ) {
            return normalized
        }

        var stripped = normalized
        for (pattern in CONVERSATIONAL_PREFIX_PATTERNS) {
            val matcher = pattern.matcher(stripped)
            if (matcher.find()) {
                val candidate = stripped.substring(matcher.end()).trim()
                // Only strip if the remaining command is non-empty
                if (candidate.isNotBlank()) {
                    stripped = candidate
                }
            }
        }

        return stripped.trim()
    }

    /**
     * Strips wake words ("vision", "hey vision", "विज़न", "व्हिजन") from the beginning of speech.
     * Preserves feature names that contain "vision" (e.g. "open live vision", "vision live", "talk to vision").
     */
    private fun stripWakeWord(text: String): String {
        var result = text.trim()
        val lower = result.lowercase()

        // Protect feature names that start with or contain "vision"
        if (lower.startsWith("vision live") ||
            lower.startsWith("vision assistant") ||
            lower.startsWith("visionbridge") ||
            lower.startsWith("vision bridge")
        ) {
            return result
        }

        // Return empty string if the utterance is only the wake word alone
        if (lower == "vision" || lower == "hey vision" || lower == "ok vision" || lower == "okay vision" ||
            lower == "हे विजन" || lower == "हे विज़न" || lower == "विजन" || lower == "विज़न" ||
            lower == "हे व्हिजन" || lower == "व्हिजन"
        ) {
            return ""
        }

        val wakeWords = listOf(
            "hey vision", "ok vision", "okay vision", "vision",
            "हे विजन", "हे विज़न", "विजन", "विज़न",
            "हे व्हिजन", "व्हिजन"
        )

        for (ww in wakeWords) {
            if (result.startsWith(ww, ignoreCase = true)) {
                val candidate = result.substring(ww.length).trim()
                if (candidate.startsWith(",") || candidate.startsWith(":")) {
                    result = candidate.substring(1).trim()
                } else if (candidate.isNotBlank()) {
                    result = candidate
                }
                break
            }
        }

        return result
    }
}
