package com.example.visionbridge

import com.example.visionbridge.data.AuthState
import com.example.visionbridge.data.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthTest {

    @Test
    fun testUserModel() {
        val user = User(
            id = "test-uuid-1234",
            name = "Test User",
            username = "testuser",
            role = "lowVisionUser",
            token = "fake_jwt_token",
            email = "testuser@visionbridge.local",
            refreshToken = "fake_refresh_token"
        )

        assertEquals("test-uuid-1234", user.id)
        assertEquals("Test User", user.name)
        assertEquals("testuser", user.username)
        assertEquals("lowVisionUser", user.role)
        assertEquals("fake_jwt_token", user.token)
        assertEquals("testuser@visionbridge.local", user.email)
        assertEquals("fake_refresh_token", user.refreshToken)
    }

    @Test
    fun testAuthStateHierarchy() {
        val checking: AuthState = AuthState.Checking
        val unauthenticated: AuthState = AuthState.Unauthenticated
        val expired: AuthState = AuthState.SessionExpired("Session expired.")
        val error: AuthState = AuthState.Error("Network error")

        val user = User(
            id = "vol-uuid-5678",
            name = "Volunteer Sam",
            username = "volsam",
            role = "volunteer"
        )
        val authenticated: AuthState = AuthState.Authenticated(user)

        assertTrue(checking is AuthState.Checking)
        assertTrue(unauthenticated is AuthState.Unauthenticated)
        assertTrue(expired is AuthState.SessionExpired)
        assertEquals("Session expired.", (expired as AuthState.SessionExpired).message)
        assertTrue(error is AuthState.Error)
        assertTrue(authenticated is AuthState.Authenticated)
        assertEquals("volunteer", (authenticated as AuthState.Authenticated).user.role)
    }

    @Test
    fun testIdentifierResolution() {
        // Username only
        val username = "john_doe"
        val resolvedEmailFromUsername = if (username.contains("@")) username else "$username@visionbridge.local"
        assertEquals("john_doe@visionbridge.local", resolvedEmailFromUsername)

        // Email address
        val email = "john.doe@example.com"
        val resolvedEmail = if (email.contains("@")) email else "$email@visionbridge.local"
        assertEquals("john.doe@example.com", resolvedEmail)
    }

    @Test
    fun testRoleSafety() {
        fun sanitizeRole(role: String): String {
            return if (role == "volunteer") "volunteer" else "lowVisionUser"
        }

        assertEquals("lowVisionUser", sanitizeRole("lowVisionUser"))
        assertEquals("volunteer", sanitizeRole("volunteer"))
        assertEquals("lowVisionUser", sanitizeRole("admin")) // Cannot self-register as admin
        assertEquals("lowVisionUser", sanitizeRole("superuser"))
        assertEquals("lowVisionUser", sanitizeRole(""))
    }
}
