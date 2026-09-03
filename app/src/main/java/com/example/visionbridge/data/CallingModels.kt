package com.example.visionbridge.data

data class PhoneContact(
    val id: String = "",
    val userId: String = "",
    val name: String,
    val phoneNumber: String,
    val relationship: String = "Friend",
    val isFavorite: Boolean = false,
    val normalizedName: String = ""
)

data class PhoneCallLog(
    val id: String = "",
    val userId: String = "",
    val contactId: String? = null,
    val name: String = "",
    val phoneNumber: String,
    val direction: String = "outgoing",
    val status: String = "completed",
    val duration: Int = 0,
    val startedAt: String = "",
    val endedAt: String? = null
)

enum class CallState {
    IDLE,
    CONFIRMING,
    AMBIGUOUS_CONTACT,
    CALLING,
    ENDED
}
