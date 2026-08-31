package com.example.visionbridge.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow(loadUser())
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString(KEY_LANG, "en") ?: "en")
    val language: StateFlow<String> = _language.asStateFlow()

    val token: String?
        get() = prefs.getString(KEY_TOKEN, null)

    val isLoggedIn: Boolean
        get() = !token.isNullOrBlank()

    fun saveUser(user: User) {
        prefs.edit().apply {
            putString(KEY_ID, user.id)
            putString(KEY_NAME, user.name)
            putString(KEY_USERNAME, user.username)
            putString(KEY_ROLE, user.role)
            user.token?.let { putString(KEY_TOKEN, it) }
            apply()
        }
        _currentUser.value = user
    }

    fun clearUser() {
        prefs.edit().apply {
            remove(KEY_ID)
            remove(KEY_NAME)
            remove(KEY_USERNAME)
            remove(KEY_ROLE)
            remove(KEY_TOKEN)
            apply()
        }
        _currentUser.value = null
    }

    fun setLanguage(langCode: String) {
        val normalized = when (langCode.lowercase()) {
            "hi" -> "hi"
            "mr" -> "mr"
            else -> "en"
        }
        prefs.edit().putString(KEY_LANG, normalized).apply()
        _language.value = normalized
    }

    fun getLanguage(): String = _language.value

    private fun loadUser(): User? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val name = prefs.getString(KEY_NAME, "") ?: ""
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val role = prefs.getString(KEY_ROLE, "lowVisionUser") ?: "lowVisionUser"
        val token = prefs.getString(KEY_TOKEN, null)
        return User(id = id, name = name, username = username, role = role, token = token)
    }

    companion object {
        private const val PREF_NAME = "visionbridge_session_prefs"
        private const val KEY_ID = "user_id"
        private const val KEY_NAME = "user_name"
        private const val KEY_USERNAME = "user_username"
        private const val KEY_ROLE = "user_role"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_LANG = "selected_language"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
