package com.example.visionbridge.data

data class User(
    val id: String,
    val name: String,
    val username: String,
    val role: String, // "lowVisionUser", "volunteer", "admin"
    val token: String? = null,
    val email: String? = null,
    val refreshToken: String? = null
)

sealed interface AuthState {
    object Checking : AuthState
    data class Authenticated(val user: User) : AuthState
    object Unauthenticated : AuthState
    data class SessionExpired(val message: String = "Session expired. Please log in again.") : AuthState
    data class Error(val message: String) : AuthState
}

data class SceneAnalysis(
    val scene: String,
    val confidence: Double,
    val description: String,
    val objects: List<String>,
    val obstacles: List<String>,
    val lighting: String,
    val timeOfDay: String
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
    val action: String, // e.g. "OPEN_FEATURE", "CAPTURE_IMAGE", "ASK_CONTEXTUAL_QUESTION", "FIND_OBJECT", "GO_HOME", etc.
    val target: String? = null, // "surroundings", "reading", "currency", "transport", "objectFinder", "location", "volunteer", "emergency", "home", "news", "calling", "liveVision", "voiceCall", "radio", "stories", "games", "progress"
    val question: String? = null,
    val answer: String? = null,
    val objectName: String? = null,
    val contactName: String? = null,
    val phoneNumber: String? = null,
    val message: String? = null,
    val speech: String? = null,
    val storyQuery: String? = null,
    val genre: String? = null,
    val newsCategory: String? = null,
    val speed: String? = null,
    val newsLang: String? = null,
    val needsNewImage: Boolean = false,
    val confidence: Double = 0.8,
    val type: String? = null // "navigation", "action", "answer", "clarification", "error"
)

data class ConversationTurn(
    val user: String,
    val assistant: String
)
