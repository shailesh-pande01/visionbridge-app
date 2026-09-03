package com.example.visionbridge.supabase

import android.content.Context
import android.util.Log
import com.example.visionbridge.api.ApiError
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.data.AuthState
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.data.User
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SupabaseClient private constructor(private val context: Context) {

    private val sessionManager = SessionManager.getInstance(context)
    private val refreshLock = ReentrantLock()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ── Edge Function Invocation ──────────────────────────────────────

    fun callFunction(
        functionName: String,
        body: JSONObject = JSONObject(),
        authRequired: Boolean = false
    ): ApiResult<JSONObject> {
        val url = if (functionName.startsWith("http")) functionName else "${SupabaseConfig.FUNCTIONS_URL}/$functionName"
        val requestBody = body.toString().toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")

        val token = sessionManager.token
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        } else if (authRequired) {
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.BACKEND_AUTH,
                    userMessage = "You must be signed in to perform this action.",
                    technicalDetail = "Auth token is missing for Edge Function $functionName"
                )
            )
        }

        return executeRequest(requestBuilder.build())
    }

    // ── PostgREST Operations ──────────────────────────────────────────

    fun restGet(
        pathWithQuery: String,
        authRequired: Boolean = true
    ): ApiResult<String> {
        val url = if (pathWithQuery.startsWith("http")) pathWithQuery else "${SupabaseConfig.REST_URL}/$pathWithQuery"
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Accept", "application/json")

        val token = sessionManager.token
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        } else if (authRequired) {
            requestBuilder.addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
        }

        return executeRawRequest(requestBuilder.build())
    }

    fun restPost(
        table: String,
        body: Any, // JSONObject or JSONArray
        preferReturn: Boolean = true,
        authRequired: Boolean = true
    ): ApiResult<String> {
        val url = if (table.startsWith("http")) table else "${SupabaseConfig.REST_URL}/$table"
        val requestBody = body.toString().toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")

        if (preferReturn) {
            requestBuilder.addHeader("Prefer", "return=representation")
        }

        val token = sessionManager.token
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        } else if (authRequired) {
            requestBuilder.addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
        }

        return executeRawRequest(requestBuilder.build())
    }

    fun restPatch(
        tableWithFilter: String,
        body: JSONObject,
        preferReturn: Boolean = true
    ): ApiResult<String> {
        val url = if (tableWithFilter.startsWith("http")) tableWithFilter else "${SupabaseConfig.REST_URL}/$tableWithFilter"
        val requestBody = body.toString().toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .patch(requestBody)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")

        if (preferReturn) {
            requestBuilder.addHeader("Prefer", "return=representation")
        }

        val token = sessionManager.token
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        return executeRawRequest(requestBuilder.build())
    }

    fun restDelete(tableWithFilter: String): ApiResult<Boolean> {
        val url = if (tableWithFilter.startsWith("http")) tableWithFilter else "${SupabaseConfig.REST_URL}/$tableWithFilter"

        val requestBuilder = Request.Builder()
            .url(url)
            .delete()
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)

        val token = sessionManager.token
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        return when (val res = executeRawRequest(requestBuilder.build())) {
            is ApiResult.Success -> ApiResult.Success(true)
            is ApiResult.Failure -> res
        }
    }

    // ── RPC (PostgreSQL Stored Procedures) ────────────────────────────

    fun rpc(
        functionName: String,
        params: JSONObject = JSONObject(),
        authRequired: Boolean = true
    ): ApiResult<String> {
        val url = "${SupabaseConfig.REST_URL}/rpc/$functionName"
        val requestBody = params.toString().toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")

        val token = sessionManager.token
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        } else if (authRequired) {
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.BACKEND_AUTH,
                    userMessage = "You must be signed in to execute this operation.",
                    technicalDetail = "Missing token for RPC $functionName"
                )
            )
        }

        return executeRawRequest(requestBuilder.build())
    }

    // ── Supabase Auth Operations ──────────────────────────────────────

    fun authSignup(
        email: String,
        password: String,
        data: JSONObject
    ): ApiResult<JSONObject> {
        val url = "${SupabaseConfig.AUTH_URL}/signup"
        val body = JSONObject()
            .put("email", email.trim().lowercase())
            .put("password", password)
            .put("data", data)

        val requestBody = body.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .build()

        return executeRequest(request, allowAutoRefresh = false)
    }

    fun authLoginWithPassword(
        email: String,
        password: String
    ): ApiResult<JSONObject> {
        val url = "${SupabaseConfig.AUTH_URL}/token?grant_type=password"
        val body = JSONObject()
            .put("email", email.trim().lowercase())
            .put("password", password)

        val requestBody = body.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .build()

        return executeRequest(request, allowAutoRefresh = false)
    }

    fun authRefreshToken(refreshToken: String): ApiResult<JSONObject> {
        val url = "${SupabaseConfig.AUTH_URL}/token?grant_type=refresh_token"
        val body = JSONObject().put("refresh_token", refreshToken)

        val requestBody = body.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .build()

        return executeRequest(request, allowAutoRefresh = false)
    }

    fun authGetUser(token: String): ApiResult<JSONObject> {
        val url = "${SupabaseConfig.AUTH_URL}/user"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .build()

        return executeRequest(request, allowAutoRefresh = false)
    }

    fun authSignOut(token: String?): ApiResult<Boolean> {
        if (token.isNullOrBlank()) {
            return ApiResult.Success(true)
        }

        val url = "${SupabaseConfig.AUTH_URL}/logout"
        val requestBody = "".toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .build()

        return when (val res = executeRawRequest(request, allowAutoRefresh = false)) {
            is ApiResult.Success -> ApiResult.Success(true)
            is ApiResult.Failure -> ApiResult.Success(true) // Graceful fallback
        }
    }

    /**
     * Startup session validation and token restoration:
     * - Validates cached token against Supabase Auth.
     * - If expired or invalid, uses refreshToken to obtain a fresh session.
     * - Loads verified profile from profiles table.
     */
    fun validateOrRefreshSession(): ApiResult<User> {
        val currentToken = sessionManager.token
        val currentRefreshToken = sessionManager.refreshToken
        val cachedUser = sessionManager.currentUser.value

        if (currentToken.isNullOrBlank() && currentRefreshToken.isNullOrBlank()) {
            sessionManager.setAuthState(AuthState.Unauthenticated)
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.BACKEND_AUTH,
                    userMessage = "No active session found.",
                    technicalDetail = "Tokens are empty in SessionManager"
                )
            )
        }

        // Check if token is expired or soon to expire
        if (sessionManager.isTokenExpired() && !currentRefreshToken.isNullOrBlank()) {
            Log.d(TAG, "Access token expired. Attempting token refresh...")
            val refreshResult = authRefreshToken(currentRefreshToken)
            if (refreshResult is ApiResult.Success) {
                val refreshData = refreshResult.value
                val newAccessToken = refreshData.optString("access_token", "")
                val newRefreshToken = refreshData.optString("refresh_token", currentRefreshToken)
                val expiresIn = refreshData.optLong("expires_in", 3600L)

                if (newAccessToken.isNotBlank()) {
                    sessionManager.updateTokens(newAccessToken, newRefreshToken, expiresIn)
                    return loadProfileForUser(newAccessToken, refreshData.optJSONObject("user"))
                }
            } else {
                Log.w(TAG, "Refresh token failed on startup")
                sessionManager.notifySessionExpired()
                return ApiResult.Failure(
                    ApiError(
                        kind = ApiError.Kind.BACKEND_AUTH,
                        userMessage = "Session expired. Please sign in again.",
                        technicalDetail = "Refresh token expired or invalid"
                    )
                )
            }
        }

        // Validate active access token with authGetUser
        if (!currentToken.isNullOrBlank()) {
            val userResult = authGetUser(currentToken)
            if (userResult is ApiResult.Success) {
                return loadProfileForUser(currentToken, userResult.value)
            } else if (!currentRefreshToken.isNullOrBlank()) {
                // Token may have been invalidated, attempt refresh
                val refreshResult = authRefreshToken(currentRefreshToken)
                if (refreshResult is ApiResult.Success) {
                    val refreshData = refreshResult.value
                    val newAccessToken = refreshData.optString("access_token", "")
                    val newRefreshToken = refreshData.optString("refresh_token", currentRefreshToken)
                    val expiresIn = refreshData.optLong("expires_in", 3600L)

                    if (newAccessToken.isNotBlank()) {
                        sessionManager.updateTokens(newAccessToken, newRefreshToken, expiresIn)
                        return loadProfileForUser(newAccessToken, refreshData.optJSONObject("user"))
                    }
                }
            }
        }

        // If validation and refresh failed:
        sessionManager.notifySessionExpired()
        return ApiResult.Failure(
            ApiError(
                kind = ApiError.Kind.BACKEND_AUTH,
                userMessage = "Session expired. Please sign in again.",
                technicalDetail = "Unable to validate or restore session"
            )
        )
    }

    private fun loadProfileForUser(token: String, userObj: JSONObject?): ApiResult<User> {
        val userId = userObj?.optString("id", "") ?: sessionManager.currentUser.value?.id.orEmpty()
        val userEmail = userObj?.optString("email", "") ?: sessionManager.currentUser.value?.email.orEmpty()
        val userMetadata = userObj?.optJSONObject("user_metadata")

        var name = userMetadata?.optString("name", "") ?: sessionManager.currentUser.value?.name.orEmpty()
        var username = userMetadata?.optString("username", "") ?: sessionManager.currentUser.value?.username.orEmpty()
        var role = userMetadata?.optString("role", "lowVisionUser") ?: sessionManager.currentUser.value?.role ?: "lowVisionUser"

        // Fetch verified profile from profiles table
        if (userId.isNotBlank()) {
            val profileRes = restGet("profiles?id=eq.$userId&select=*")
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
            name = name.ifBlank { username.ifBlank { "VisionBridge User" } },
            username = username,
            role = role,
            email = userEmail.ifBlank { null },
            token = token,
            refreshToken = sessionManager.refreshToken
        )

        sessionManager.saveUser(user)
        return ApiResult.Success(user)
    }

    // ── Internal Request Execution ────────────────────────────────────

    private fun executeRequest(request: Request, allowAutoRefresh: Boolean = true): ApiResult<JSONObject> {
        return when (val rawResult = executeRawRequest(request, allowAutoRefresh)) {
            is ApiResult.Failure -> rawResult
            is ApiResult.Success -> {
                val bodyStr = rawResult.value.trim()
                try {
                    if (bodyStr.startsWith("{")) {
                        ApiResult.Success(JSONObject(bodyStr))
                    } else if (bodyStr.startsWith("[")) {
                        val arr = JSONArray(bodyStr)
                        ApiResult.Success(JSONObject().put("data", arr))
                    } else if (bodyStr.isEmpty()) {
                        ApiResult.Success(JSONObject().put("success", true))
                    } else {
                        ApiResult.Success(JSONObject().put("value", bodyStr))
                    }
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse server response.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message} (Raw: $bodyStr)"
                        )
                    )
                }
            }
        }
    }

    private fun executeRawRequest(request: Request, allowAutoRefresh: Boolean = true): ApiResult<String> {
        val startedAt = System.currentTimeMillis()
        return try {
            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            val rawBody = response.body?.string().orEmpty()
            response.close()

            val elapsed = System.currentTimeMillis() - startedAt
            Log.d(TAG, "${request.method} ${request.url} -> HTTP $code (${elapsed}ms)")

            if (code in 200..299) {
                ApiResult.Success(rawBody)
            } else if (code == 401 && allowAutoRefresh && !request.url.toString().contains("/auth/v1/")) {
                // Intercept 401 Unauthorized for authenticated API requests
                handle401AndRetry(request)
            } else {
                val userMsg = extractErrorMessage(rawBody, code)
                val kind = when (code) {
                    401, 403 -> ApiError.Kind.BACKEND_AUTH
                    429 -> ApiError.Kind.RATE_LIMITED
                    413 -> ApiError.Kind.PAYLOAD_TOO_LARGE
                    else -> ApiError.Kind.HTTP_ERROR
                }

                ApiResult.Failure(
                    ApiError(
                        kind = kind,
                        userMessage = userMsg,
                        technicalDetail = "HTTP $code at ${request.url}: $rawBody"
                    )
                )
            }
        } catch (e: SocketTimeoutException) {
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.TIMEOUT,
                    userMessage = "The request timed out. Please try again.",
                    technicalDetail = "Timeout talking to ${request.url}: ${e.message}"
                )
            )
        } catch (e: IOException) {
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.NETWORK_IO,
                    userMessage = "Unable to connect to the server. Please check your internet connection.",
                    technicalDetail = "${e.javaClass.simpleName} at ${request.url}: ${e.message}"
                )
            )
        } catch (e: Exception) {
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.UNKNOWN,
                    userMessage = "An unexpected error occurred.",
                    technicalDetail = "${e.javaClass.simpleName} at ${request.url}: ${e.message}"
                )
            )
        }
    }

    private fun handle401AndRetry(originalRequest: Request): ApiResult<String> {
        val rToken = sessionManager.refreshToken
        if (rToken.isNullOrBlank()) {
            sessionManager.notifySessionExpired()
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.BACKEND_AUTH,
                    userMessage = "Your session has expired. Please sign in again.",
                    technicalDetail = "401 received and no refresh token available"
                )
            )
        }

        // Thread-safe refresh
        var refreshSucceeded = false
        refreshLock.withLock {
            val refreshRes = authRefreshToken(rToken)
            if (refreshRes is ApiResult.Success) {
                val json = refreshRes.value
                val newAccessToken = json.optString("access_token", "")
                val newRefreshToken = json.optString("refresh_token", rToken)
                val expiresIn = json.optLong("expires_in", 3600L)

                if (newAccessToken.isNotBlank()) {
                    sessionManager.updateTokens(newAccessToken, newRefreshToken, expiresIn)
                    refreshSucceeded = true
                }
            }
        }

        if (refreshSucceeded) {
            val newToken = sessionManager.token
            val retriedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
            // Retry once with allowAutoRefresh = false to prevent loops
            return executeRawRequest(retriedRequest, allowAutoRefresh = false)
        } else {
            sessionManager.notifySessionExpired()
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.BACKEND_AUTH,
                    userMessage = "Your session has expired. Please sign in again.",
                    technicalDetail = "Refresh token expired or rejected"
                )
            )
        }
    }

    private fun extractErrorMessage(rawBody: String, code: Int): String {
        try {
            if (rawBody.isNotBlank()) {
                val json = JSONObject(rawBody)
                val msg = when {
                    json.has("msg") -> json.getString("msg")
                    json.has("error_description") -> json.getString("error_description")
                    json.has("message") -> json.getString("message")
                    json.has("error") -> {
                        val err = json.get("error")
                        if (err is String) err
                        else if (err is JSONObject && err.has("message")) err.getString("message")
                        else null
                    }
                    else -> null
                }

                if (!msg.isNullOrBlank()) {
                    val lower = msg.lowercase()
                    return when {
                        lower.contains("invalid login credentials") || lower.contains("invalid_grant") || lower.contains("invalid credentials") ->
                            "Incorrect username/email or password."
                        lower.contains("user already registered") || lower.contains("already exists") ->
                            "An account with this username or email already exists."
                        lower.contains("password should be at least") || lower.contains("weak_password") ->
                            "Password must be at least 6 characters long."
                        lower.contains("rate limit") || lower.contains("over_email_send_rate_limit") ->
                            "Too many attempts. Please wait a moment and try again."
                        lower.contains("invalid email") ->
                            "Please enter a valid email address."
                        lower.contains("signup requires a valid password") ->
                            "Please provide a valid password."
                        else -> msg
                    }
                }
            }
        } catch (_: Exception) {}

        return when (code) {
            400 -> "Bad request. Please verify your input."
            401 -> "Invalid credentials or session expired."
            403 -> "You do not have permission to perform this action."
            404 -> "Requested item was not found."
            429 -> "Too many requests. Please wait a moment."
            in 500..599 -> "Server error. Please try again later."
            else -> "Request failed (HTTP $code)."
        }
    }

    companion object {
        private const val TAG = "VB-SupabaseClient"

        @Volatile
        private var instance: SupabaseClient? = null

        fun getInstance(context: Context): SupabaseClient {
            return instance ?: synchronized(this) {
                instance ?: SupabaseClient(context.applicationContext).also { instance = it }
            }
        }
    }
}
