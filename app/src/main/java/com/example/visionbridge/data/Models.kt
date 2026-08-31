package com.example.visionbridge.data

data class User(
    val id: String,
    val name: String,
    val username: String,
    val role: String, // "lowVisionUser", "volunteer", "admin"
    val token: String? = null
)

data class SceneAnalysis(
    val scene: String,
    val confidence: Double,
    val description: String,
    val objects: List<String>,
    val obstacles: List<String>,
    val lighting: String,
    val timeOfDay: String
)

data class HazardAnalysis(
    val speech: String,
    val sceneSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class CurrencyItem(
    val denomination: Double,
    val quantity: Int,
    val confidence: Double
)

data class CurrencyAnalysis(
    val currency: String,
    val symbol: String,
    val items: List<CurrencyItem>,
    val total: Double?,
    val confidence: Double,
    val speech: String
)

data class TransportAnalysis(
    val type: String,
    val title: String,
    val destination: String,
    val speech: String,
    val confidence: Double
)

data class FinderAnalysis(
    val found: Boolean,
    val objectName: String,
    val direction: String,
    val distance: String,
    val reference: String,
    val speech: String,
    val confidence: Double
)

data class LocationAnalysis(
    val summary: String,
    val address: String?,
    val landmarks: List<String>,
    val latitude: Double,
    val longitude: Double
)

data class HelpRequest(
    val id: String,
    val requester: String,
    val requesterName: String?,
    val requestType: String = "general",
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val destination: String? = null,
    val helpDescription: String,
    val status: String, // "PENDING", "searching", "ACCEPTED", "COMPLETED", "CANCELLED", "REJECTED"
    val volunteerId: String? = null,
    val volunteerName: String? = null,
    val createdAt: String? = null
)

data class EmergencyEvent(
    val id: String,
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val locationUrl: String? = null,
    val status: String, // "ACTIVE", "ENDED"
    val whatsappSent: Boolean = false,
    val whatsappError: String? = null,
    val timestamp: String? = null
)

data class EmergencyContact(
    val id: String,
    val userId: String,
    val name: String,
    val phone: String,
    val relationship: String
)

data class AssistantAction(
    val action: String, // "OPEN_FEATURE", "CAPTURE_IMAGE", "ASK_CONTEXTUAL_QUESTION", "FIND_OBJECT", "GO_HOME", "START_VOLUNTEER_HELP", "EMERGENCY_SOS", "CONFIRM", "REPEAT_LAST", "STOP_SPEAKING", "CANCEL", "UNKNOWN"
    val target: String? = null, // "surroundings", "hazard", "reading", "currency", "transport", "objectFinder", "location", "volunteer", "emergency", "home"
    val question: String? = null,
    val objectName: String? = null,
    val message: String? = null,
    val speech: String? = null,
    val confidence: Double = 0.8,
    val type: String? = null // "navigation", "action", "answer", "clarification", "error"
)

data class ConversationTurn(
    val user: String,
    val assistant: String
)
