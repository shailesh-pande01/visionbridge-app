package com.example.visionbridge.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<User?>(loadUser())
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(
        if (_currentUser.value != null && !token.isNullOrBlank()) {
            AuthState.Authenticated(_currentUser.value!!)
        } else {
            AuthState.Unauthenticated
        }
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _language = MutableStateFlow(
        when (prefs.getString(KEY_LANG, "en")?.lowercase()?.trim()) {
            "hi", "hindi" -> "hi"
            "mr", "marathi" -> "mr"
            else -> "en"
        }
    )
    val language: StateFlow<String> = _language.asStateFlow()

    private val _isGestureEnabled = MutableStateFlow(prefs.getBoolean(KEY_GESTURE_VOICE_ENABLED, true))
    val isGestureEnabled: StateFlow<Boolean> = _isGestureEnabled.asStateFlow()

    private val _isShakeEnabled = MutableStateFlow(prefs.getBoolean(KEY_GESTURE_SHAKE_ENABLED, true))
    val isShakeEnabled: StateFlow<Boolean> = _isShakeEnabled.asStateFlow()

    private val _isHapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_GESTURE_HAPTICS_ENABLED, true))
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    val token: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null) ?: prefs.getString(KEY_TOKEN_LEGACY, null)

    val refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)

    val expiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)

    val isLoggedIn: Boolean
        get() = !token.isNullOrBlank()

    fun isTokenExpired(): Boolean {
        val exp = expiresAt
        if (exp <= 0L) return false
        // Consider token expired if within 60 seconds of expiration
        return System.currentTimeMillis() >= (exp - 60_000L)
    }

    fun saveSession(
        user: User,
        accessToken: String,
        refreshToken: String? = null,
        expiresInSeconds: Long? = null
    ) {
        val expiresAtMs = if (expiresInSeconds != null && expiresInSeconds > 0) {
            System.currentTimeMillis() + (expiresInSeconds * 1000L)
        } else {
            System.currentTimeMillis() + (3600 * 1000L) // Default 1 hour
        }

        val updatedUser = user.copy(
            token = accessToken,
            refreshToken = refreshToken ?: user.refreshToken ?: this.refreshToken
        )

        prefs.edit().apply {
            putString(KEY_ID, updatedUser.id)
            putString(KEY_NAME, updatedUser.name)
            putString(KEY_USERNAME, updatedUser.username)
            putString(KEY_ROLE, updatedUser.role)
            updatedUser.email?.let { putString(KEY_EMAIL, it) }
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_TOKEN_LEGACY, accessToken)
            if (refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            putLong(KEY_EXPIRES_AT, expiresAtMs)
            apply()
        }

        _currentUser.value = updatedUser
        _authState.value = AuthState.Authenticated(updatedUser)
    }

    fun saveUser(user: User) {
        saveSession(
            user = user,
            accessToken = user.token ?: token ?: "",
            refreshToken = user.refreshToken ?: refreshToken,
            expiresInSeconds = null
        )
    }

    fun updateTokens(
        accessToken: String,
        refreshToken: String? = null,
        expiresInSeconds: Long? = null
    ) {
        val expiresAtMs = if (expiresInSeconds != null && expiresInSeconds > 0) {
            System.currentTimeMillis() + (expiresInSeconds * 1000L)
        } else {
            System.currentTimeMillis() + (3600 * 1000L)
        }

        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_TOKEN_LEGACY, accessToken)
            if (refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            putLong(KEY_EXPIRES_AT, expiresAtMs)
            apply()
        }

        val cur = _currentUser.value
        if (cur != null) {
            val updated = cur.copy(
                token = accessToken,
                refreshToken = refreshToken ?: cur.refreshToken ?: this.refreshToken
            )
            _currentUser.value = updated
            _authState.value = AuthState.Authenticated(updated)
        }
    }

    fun clearSession() {
        prefs.edit().apply {
            remove(KEY_ID)
            remove(KEY_NAME)
            remove(KEY_USERNAME)
            remove(KEY_EMAIL)
            remove(KEY_ROLE)
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_TOKEN_LEGACY)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_EXPIRES_AT)
            apply()
        }
        _currentUser.value = null
        _authState.value = AuthState.Unauthenticated
    }

    fun clearUser() {
        clearSession()
    }

    fun notifySessionExpired(message: String = "Your session has expired. Please sign in again.") {
        clearSession()
        _authState.value = AuthState.SessionExpired(message)
    }

    fun setAuthState(state: AuthState) {
        _authState.value = state
        if (state is AuthState.Authenticated) {
            _currentUser.value = state.user
        } else if (state is AuthState.Unauthenticated || state is AuthState.SessionExpired) {
            _currentUser.value = null
        }
    }

    fun setLanguage(langCode: String?) {
        val normalized = when (langCode?.lowercase()?.trim()) {
            "hi", "hindi" -> "hi"
            "mr", "marathi" -> "mr"
            else -> "en"
        }
        prefs.edit().putString(KEY_LANG, normalized).apply()
        _language.value = normalized
    }

    fun getLanguage(): String = _language.value

    fun setGestureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GESTURE_VOICE_ENABLED, enabled).apply()
        _isGestureEnabled.value = enabled
    }

    fun isGestureVoiceEnabled(): Boolean = _isGestureEnabled.value

    fun setShakeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GESTURE_SHAKE_ENABLED, enabled).apply()
        _isShakeEnabled.value = enabled
    }

    fun isShakeGestureEnabled(): Boolean = _isShakeEnabled.value

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GESTURE_HAPTICS_ENABLED, enabled).apply()
        _isHapticsEnabled.value = enabled
    }

    fun isHapticFeedbackEnabled(): Boolean = _isHapticsEnabled.value

    private fun loadUser(): User? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val name = prefs.getString(KEY_NAME, "") ?: ""
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val role = prefs.getString(KEY_ROLE, "lowVisionUser") ?: "lowVisionUser"
        val email = prefs.getString(KEY_EMAIL, null)
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: prefs.getString(KEY_TOKEN_LEGACY, null)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)

        return User(
            id = id,
            name = name,
            username = username,
            role = role,
            email = email,
            token = accessToken,
            refreshToken = refreshToken
        )
    }

    companion object {
        private const val PREF_NAME = "visionbridge_session_prefs"
        private const val KEY_ID = "user_id"
        private const val KEY_NAME = "user_name"
        private const val KEY_USERNAME = "user_username"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_ROLE = "user_role"
        private const val KEY_ACCESS_TOKEN = "supabase_access_token"
        private const val KEY_TOKEN_LEGACY = "auth_token"
        private const val KEY_REFRESH_TOKEN = "supabase_refresh_token"
        private const val KEY_EXPIRES_AT = "supabase_token_expires_at"
        private const val KEY_LANG = "selected_language"
        private const val KEY_GESTURE_VOICE_ENABLED = "gesture_voice_enabled"
        private const val KEY_GESTURE_SHAKE_ENABLED = "gesture_shake_enabled"
        private const val KEY_GESTURE_HAPTICS_ENABLED = "gesture_haptics_enabled"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
