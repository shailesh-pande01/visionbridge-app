package com.example.visionbridge.api

import android.content.Context
import android.util.Log
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.data.User
import com.example.visionbridge.supabase.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject

class AuthApi(private val context: Context) {

    private val supabaseClient = SupabaseClient.getInstance(context)
    private val sessionManager = SessionManager.getInstance(context)

    fun register(
        name: String,
        username: String,
        password: String,
        role: String,
        emailInput: String? = null
    ): ApiResult<User> {
        val trimmedName = name.trim()
        val trimmedUsername = username.trim().lowercase().replace(" ", "_")
        val safeRole = if (role == "volunteer") "volunteer" else "lowVisionUser"

        val email = when {
            !emailInput.isNullOrBlank() && emailInput.contains("@") -> emailInput.trim().lowercase()
            trimmedUsername.contains("@") -> trimmedUsername
            else -> "$trimmedUsername@visionbridge.local"
        }

        val metadata = JSONObject()
            .put("name", trimmedName)
            .put("username", trimmedUsername)
            .put("role", safeRole)

        return when (val res = supabaseClient.authSignup(email, password, metadata)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val json = res.value
                val sessionObj = json.optJSONObject("session")
                val userObj = json.optJSONObject("user") ?: json

                var accessToken = sessionObj?.optString("access_token") ?: json.optString("access_token", "")
                var refreshToken = sessionObj?.optString("refresh_token") ?: json.optString("refresh_token", "")
                var expiresIn = sessionObj?.optLong("expires_in") ?: json.optLong("expires_in", 3600L)

                // If signup returned user but no direct session, attempt login
                if (accessToken.isBlank()) {
                    val loginRes = supabaseClient.authLoginWithPassword(email, password)
                    if (loginRes is ApiResult.Success) {
                        val loginJson = loginRes.value
                        accessToken = loginJson.optString("access_token", "")
                        refreshToken = loginJson.optString("refresh_token", "")
                        expiresIn = loginJson.optLong("expires_in", 3600L)
                    }
                }

                val userId = userObj.optString("id", "")
                val userMetadata = userObj.optJSONObject("user_metadata")

                var returnedName = userMetadata?.optString("name", trimmedName) ?: trimmedName
                var returnedUsername = userMetadata?.optString("username", trimmedUsername) ?: trimmedUsername
                var returnedRole = userMetadata?.optString("role", safeRole) ?: safeRole

                // Fetch server-verified profile from profiles table if we have an access token
                if (accessToken.isNotBlank() && userId.isNotBlank()) {
                    val profileRes = supabaseClient.restGet("profiles?id=eq.$userId&select=*")
                    if (profileRes is ApiResult.Success) {
                        try {
                            val arr = JSONArray(profileRes.value)
                            if (arr.length() > 0) {
                                val p = arr.getJSONObject(0)
                                returnedName = p.optString("name", returnedName)
                                returnedUsername = p.optString("username", returnedUsername)
                                returnedRole = p.optString("role", returnedRole)
                            }
                        } catch (_: Exception) {}
                    }
                }

                val user = User(
                    id = userId,
                    name = returnedName.ifBlank { returnedUsername },
                    username = returnedUsername,
                    role = returnedRole,
                    email = email,
                    token = accessToken,
                    refreshToken = refreshToken
                )

                if (accessToken.isNotBlank()) {
                    sessionManager.saveSession(
                        user = user,
                        accessToken = accessToken,
                        refreshToken = refreshToken.takeIf { it.isNotBlank() },
                        expiresInSeconds = expiresIn
                    )
                }

                ApiResult.Success(user)
            }
        }
    }

    fun login(
        identifier: String,
        password: String
    ): ApiResult<User> {
        val trimmed = identifier.trim().lowercase()
        val email = if (trimmed.contains("@")) trimmed else "$trimmed@visionbridge.local"

        return when (val res = supabaseClient.authLoginWithPassword(email, password)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val json = res.value
                val token = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token", "")
                val expiresIn = json.optLong("expires_in", 3600L)
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id", "") ?: ""
                val userMetadata = userObj?.optJSONObject("user_metadata")

                var returnedName = userMetadata?.optString("name", "") ?: ""
                var returnedUsername = userMetadata?.optString("username", trimmed) ?: trimmed
                var returnedRole = userMetadata?.optString("role", "lowVisionUser") ?: "lowVisionUser"

                // Save initial session so subsequent restGet can use token
                val initialUser = User(
                    id = userId,
                    name = returnedName.ifBlank { returnedUsername },
                    username = returnedUsername,
                    role = returnedRole,
                    email = userObj?.optString("email", email) ?: email,
                    token = token,
                    refreshToken = refreshToken
                )
                sessionManager.saveSession(
                    user = initialUser,
                    accessToken = token,
                    refreshToken = refreshToken.takeIf { it.isNotBlank() },
                    expiresInSeconds = expiresIn
                )

                // Fetch server-verified profile from profiles table
                if (userId.isNotBlank() && token.isNotBlank()) {
                    val profileRes = supabaseClient.restGet("profiles?id=eq.$userId&select=*")
                    if (profileRes is ApiResult.Success) {
                        try {
                            val arr = JSONArray(profileRes.value)
                            if (arr.length() > 0) {
                                val p = arr.getJSONObject(0)
                                returnedName = p.optString("name", returnedName)
                                returnedUsername = p.optString("username", returnedUsername)
                                returnedRole = p.optString("role", returnedRole)
                            }
                        } catch (_: Exception) {}
                    }
                }

                val finalUser = initialUser.copy(
                    name = returnedName.ifBlank { returnedUsername },
                    username = returnedUsername,
                    role = returnedRole
                )

                sessionManager.saveSession(
                    user = finalUser,
                    accessToken = token,
                    refreshToken = refreshToken.takeIf { it.isNotBlank() },
                    expiresInSeconds = expiresIn
                )

                ApiResult.Success(finalUser)
            }
        }
    }

    fun validateSession(): ApiResult<User> {
        return supabaseClient.validateOrRefreshSession()
    }

    fun logout(): ApiResult<Boolean> {
        val currentToken = sessionManager.token
        try {
            supabaseClient.authSignOut(currentToken)
        } catch (e: Exception) {
            Log.w("AuthApi", "Sign out remote call error", e)
        }
        sessionManager.clearSession()
        return ApiResult.Success(true)
    }

    fun getMe(): ApiResult<User> {
        val token = sessionManager.token
        if (token.isNullOrBlank()) {
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.BACKEND_AUTH,
                    userMessage = "No active session.",
                    technicalDetail = "Token is null or blank in SessionManager"
                )
            )
        }

        return when (val res = supabaseClient.authGetUser(token)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val userObj = res.value
                val userId = userObj.optString("id", "")
                val userMetadata = userObj.optJSONObject("user_metadata")

                var name = userMetadata?.optString("name", "") ?: ""
                var username = userMetadata?.optString("username", "") ?: ""
                var role = userMetadata?.optString("role", "lowVisionUser") ?: "lowVisionUser"

                if (userId.isNotBlank()) {
                    val profileRes = supabaseClient.restGet("profiles?id=eq.$userId&select=*")
                    if (profileRes is ApiResult.Success) {
                        try {
                            val arr = JSONArray(profileRes.value)
                            if (arr.length() > 0) {
                                val p = arr.getJSONObject(0)
                                name = p.optString("name", name)
                                username = p.optString("username", username)
                                role = p.optString("role", role)
                            }
                        } catch (_: Exception) {}
                    }
                }

                val user = User(
                    id = userId,
                    name = name.ifBlank { username },
                    username = username,
                    role = role,
                    email = if (userObj.has("email") && !userObj.isNull("email")) userObj.optString("email") else null,
                    token = token,
                    refreshToken = sessionManager.refreshToken
                )
                sessionManager.saveUser(user)
                ApiResult.Success(user)
            }
        }
    }
}
