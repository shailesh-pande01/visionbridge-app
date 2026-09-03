package com.example.visionbridge.utils

enum class VoiceIntent {
    SMART_READING,
    AI_SURROUNDINGS,
    CURRENCY_READER,
    PUBLIC_TRANSPORT,
    SMART_OBJECT_FINDER,
    WHERE_AM_I,
    VOLUNTEER_HELP,
    EMERGENCY_SOS,
    HOME,
    UNKNOWN
}

data class ParsedVoiceCommand(
    val intent: VoiceIntent,
    val targetObject: String? = null,
    val rawCommand: String
)

object VoiceCommandParser {

    fun parseCommand(speech: String): ParsedVoiceCommand {
        val trimmed = speech.trim()
        val cleaned = trimmed.lowercase()
            .replace("hey vision", "")
            .replace("vision", "")
            .replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 1. Currency
        val currencyKeywords = listOf(
            "currency", "money", "count money", "count currency", "cash", "rupees", "notes",
            "पैसे", "पैसे गिनो", "रुपये", "पैसे मोजा", "चलन"
        )
        if (currencyKeywords.any { cleaned.contains(it) }) {
            return ParsedVoiceCommand(VoiceIntent.CURRENCY_READER, rawCommand = trimmed)
        }

        // 2. Emergency SOS
        val emergencyKeywords = listOf(
            "emergency", "sos", "danger", "help me emergency", "i am in danger",
            "आपातकाल", "खतरे में", "मदद करो", "संकट"
        )
        if (emergencyKeywords.any { cleaned.contains(it) }) {
            return ParsedVoiceCommand(VoiceIntent.EMERGENCY_SOS, rawCommand = trimmed)
        }

        // 3. Volunteer Help
        val volunteerKeywords = listOf(
            "volunteer", "human help", "connect me", "call a volunteer", "connect to person",
            "वॉलंटियर", "सहायक", "मदतनीस", "स्वयंसेवक"
        )
        if (volunteerKeywords.any { cleaned.contains(it) }) {
            return ParsedVoiceCommand(VoiceIntent.VOLUNTEER_HELP, rawCommand = trimmed)
        }

        // 4. Public Transport
        val transportKeywords = listOf(
            "bus", "train", "platform", "metro", "transport", "signboard", "sign",
            "बस", "ट्रेन", "रेलगाड़ी", "फलाट", "दिशा"
        )
        if (transportKeywords.any { cleaned.contains(it) }) {
            return ParsedVoiceCommand(VoiceIntent.PUBLIC_TRANSPORT, rawCommand = trimmed)
        }

        // 5. Where Am I (Location)
        val locationKeywords = listOf(
            "where am i", "my location", "locate me", "what is this place", "where are we",
            "मैं कहाँ हूँ", "मी कुठे आहे", "माझे स्थान", "मेरा स्थान"
        )
        if (locationKeywords.any { cleaned.contains(it) }) {
            return ParsedVoiceCommand(VoiceIntent.WHERE_AM_I, rawCommand = trimmed)
        }

        // 6. Smart Object Finder
        val objectPatterns = listOf(
            "find my ", "find a ", "find the ", "find ", "locate my ", "where is my ", "where is the ",
            "ढूँढो", "शोधा", "कुठे आहे", "कहाँ है"
        )
        for (pattern in objectPatterns) {
            if (cleaned.contains(pattern)) {
                var objName: String? = null
                val idx = cleaned.indexOf(pattern)
                val after = cleaned.substring(idx + pattern.length).trim()
                if (after.isNotBlank()) objName = after
                return ParsedVoiceCommand(VoiceIntent.SMART_OBJECT_FINDER, targetObject = objName, rawCommand = trimmed)
            }
        }

        // 7. Smart Reading
        val readingKeywords = listOf(
            "read", "text", "menu", "sign", "label", "document", "book", "read this", "read for me",
            "पढ़ो", "वाचा", "मेन्यू", "मजकूर"
        )
        if (readingKeywords.any { cleaned.contains(it) }) {
            return ParsedVoiceCommand(VoiceIntent.SMART_READING, rawCommand = trimmed)
        }

        // 8. Surroundings
        val surroundingsKeywords = listOf(
            "surrounding", "around me", "what do you see", "describe scene", "look around", "what is in front",
            "आसपास", "सामने क्या है", "परिसर", "समोर काय आहे"
        )
        if (surroundingsKeywords.any { cleaned.contains(it) }) {
            return ParsedVoiceCommand(VoiceIntent.AI_SURROUNDINGS, rawCommand = trimmed)
        }

        // 9. Home
        val homeKeywords = listOf(
            "home", "go home", "take me home", "back",
            "होम", "घर", "मागे"
        )
        if (homeKeywords.any { cleaned.contains(it) }) {
            return ParsedVoiceCommand(VoiceIntent.HOME, rawCommand = trimmed)
        }

        return ParsedVoiceCommand(VoiceIntent.UNKNOWN, rawCommand = trimmed)
    }

    fun mapIntentToRoute(intent: VoiceIntent): String? {
        return when (intent) {
            VoiceIntent.SMART_READING -> "reading"
            VoiceIntent.AI_SURROUNDINGS -> "surroundings"
            VoiceIntent.CURRENCY_READER -> "currency"
            VoiceIntent.PUBLIC_TRANSPORT -> "transport"
            VoiceIntent.SMART_OBJECT_FINDER -> "finder"
            VoiceIntent.WHERE_AM_I -> "location"
            VoiceIntent.VOLUNTEER_HELP -> "volunteer"
            VoiceIntent.EMERGENCY_SOS -> "sos"
            VoiceIntent.HOME -> "home"
            VoiceIntent.UNKNOWN -> null
        }
    }
}
