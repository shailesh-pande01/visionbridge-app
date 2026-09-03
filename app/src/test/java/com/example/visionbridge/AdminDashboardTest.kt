package com.example.visionbridge

import com.example.visionbridge.data.*
import org.junit.Assert.*
import org.junit.Test

class AdminDashboardTest {

    @Test
    fun testAdminOverviewStatsDefaults() {
        val stats = AdminOverviewStats()
        assertEquals(0, stats.totalUsers)
        assertEquals(0, stats.totalVolunteers)
        assertEquals(0, stats.activeVolunteers)
        assertEquals(0, stats.totalHelpRequests)
        assertEquals(0, stats.activeCalls)
        assertEquals(0, stats.completedCalls)
        assertEquals(0, stats.activeSos)
    }

    @Test
    fun testAdminOverviewStatsCustomValues() {
        val stats = AdminOverviewStats(
            totalUsers = 120,
            totalVolunteers = 45,
            activeVolunteers = 15,
            totalHelpRequests = 210,
            activeCalls = 3,
            completedCalls = 180,
            activeSos = 1
        )
        assertEquals(120, stats.totalUsers)
        assertEquals(45, stats.totalVolunteers)
        assertEquals(15, stats.activeVolunteers)
        assertEquals(210, stats.totalHelpRequests)
        assertEquals(3, stats.activeCalls)
        assertEquals(180, stats.completedCalls)
        assertEquals(1, stats.activeSos)
    }

    @Test
    fun testAdminSectionsEnum() {
        val sections = AdminSection.values()
        assertTrue(sections.contains(AdminSection.OVERVIEW))
        assertTrue(sections.contains(AdminSection.USERS))
        assertTrue(sections.contains(AdminSection.VOLUNTEERS))
        assertTrue(sections.contains(AdminSection.CALL_LOGS))
        assertTrue(sections.contains(AdminSection.EMERGENCY))
        assertTrue(sections.contains(AdminSection.PROFILE))
        assertEquals(6, sections.size)
    }

    @Test
    fun testAdminUserItem() {
        val user = AdminUserItem(
            id = "user-123",
            name = "Ramesh Kumar",
            username = "ramesh",
            role = "lowVisionUser",
            emergencyWhatsappNumber = "+919876543210",
            createdAt = "2026-09-01T12:00:00Z"
        )
        assertEquals("user-123", user.id)
        assertEquals("Ramesh Kumar", user.name)
        assertEquals("ramesh", user.username)
        assertEquals("lowVisionUser", user.role)
        assertEquals("+919876543210", user.emergencyWhatsappNumber)
    }

    @Test
    fun testAdminVolunteerItem() {
        val volunteer = AdminVolunteerItem(
            id = "vol-456",
            name = "Priya Sharma",
            username = "priya",
            role = "volunteer",
            availability = true,
            acceptedRequests = 14,
            completedRequests = 12,
            createdAt = "2026-08-15T09:30:00Z"
        )
        assertEquals("vol-456", volunteer.id)
        assertEquals("Priya Sharma", volunteer.name)
        assertTrue(volunteer.availability)
        assertEquals(14, volunteer.acceptedRequests)
        assertEquals(12, volunteer.completedRequests)
    }

    @Test
    fun testAdminCallLogItem() {
        val callLog = AdminCallLogItem(
            id = "log-789",
            helpRequestId = "req-001",
            userId = "user-123",
            userName = "Ramesh Kumar",
            userUsername = "ramesh",
            volunteerId = "vol-456",
            volunteerName = "Priya Sharma",
            volunteerUsername = "priya",
            startedAt = "2026-09-02T10:00:00Z",
            endedAt = "2026-09-02T10:05:30Z",
            durationSec = 330,
            status = "COMPLETED"
        )
        assertEquals("log-789", callLog.id)
        assertEquals("Ramesh Kumar", callLog.userName)
        assertEquals("Priya Sharma", callLog.volunteerName)
        assertEquals(330, callLog.durationSec)
        assertEquals("COMPLETED", callLog.status)
    }

    @Test
    fun testAdminEmergencyItem() {
        val sos = AdminEmergencyItem(
            id = "sos-999",
            userId = "user-123",
            userName = "Ramesh Kumar",
            userUsername = "ramesh",
            latitude = 18.5204,
            longitude = 73.8567,
            status = "ACTIVE",
            whatsappSent = true
        )
        assertEquals("sos-999", sos.id)
        assertEquals("ACTIVE", sos.status)
        assertTrue(sos.whatsappSent)
        assertEquals(18.5204, sos.latitude, 0.0001)
    }

    @Test
    fun testAdminPaginationCalculation() {
        val total = 25
        val limit = 8
        val totalPages = Math.max(1, (total + limit - 1) / limit)
        assertEquals(4, totalPages)

        val pag = AdminPagination(page = 2, limit = limit, total = total, totalPages = totalPages)
        assertEquals(2, pag.page)
        assertEquals(4, pag.totalPages)
    }

    @Test
    fun testRoleBasedRoutingLogic() {
        fun resolveRouteForRole(role: String): String {
            return when (role) {
                "admin" -> "admin_dashboard"
                "volunteer" -> "volunteer_dashboard"
                else -> "home"
            }
        }

        assertEquals("admin_dashboard", resolveRouteForRole("admin"))
        assertEquals("volunteer_dashboard", resolveRouteForRole("volunteer"))
        assertEquals("home", resolveRouteForRole("lowVisionUser"))
        assertEquals("home", resolveRouteForRole("unknown_role"))
    }
}
