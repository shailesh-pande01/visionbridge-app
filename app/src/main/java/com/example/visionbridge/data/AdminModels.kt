package com.example.visionbridge.data

enum class AdminSection {
    OVERVIEW,
    USERS,
    VOLUNTEERS,
    CALL_LOGS,
    EMERGENCY,
    PROFILE
}

data class AdminOverviewStats(
    val totalUsers: Int = 0,
    val totalVolunteers: Int = 0,
    val activeVolunteers: Int = 0,
    val totalHelpRequests: Int = 0,
    val activeCalls: Int = 0,
    val completedCalls: Int = 0,
    val activeSos: Int = 0
)

data class AdminUserItem(
    val id: String,
    val name: String,
    val username: String,
    val role: String,
    val phone: String? = null,
    val emergencyWhatsappNumber: String? = null,
    val createdAt: String? = null
)

data class AdminVolunteerItem(
    val id: String,
    val name: String,
    val username: String,
    val role: String,
    val availability: Boolean = true,
    val acceptedRequests: Int = 0,
    val completedRequests: Int = 0,
    val phone: String? = null,
    val createdAt: String? = null
)

data class AdminCallLogItem(
    val id: String,
    val helpRequestId: String? = null,
    val userId: String? = null,
    val userName: String = "—",
    val userUsername: String = "",
    val volunteerId: String? = null,
    val volunteerName: String = "—",
    val volunteerUsername: String = "",
    val startedAt: String? = null,
    val endedAt: String? = null,
    val durationSec: Int? = null,
    val status: String = "COMPLETED",
    val createdAt: String? = null
)

data class AdminEmergencyItem(
    val id: String,
    val userId: String,
    val userName: String = "—",
    val userUsername: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationUrl: String? = null,
    val status: String = "ACTIVE",
    val whatsappSent: Boolean = false,
    val whatsappError: String? = null,
    val endedAt: String? = null,
    val createdAt: String? = null
)

data class AdminPagination(
    val page: Int = 1,
    val limit: Int = 10,
    val total: Int = 0,
    val totalPages: Int = 1
)

data class AdminPaginatedResult<T>(
    val data: List<T> = emptyList(),
    val pagination: AdminPagination = AdminPagination()
)
