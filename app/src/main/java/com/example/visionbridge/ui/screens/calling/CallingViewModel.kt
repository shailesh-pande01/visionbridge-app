package com.example.visionbridge.ui.screens.calling

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.CallingApi
import com.example.visionbridge.data.CallState
import com.example.visionbridge.data.PhoneCallLog
import com.example.visionbridge.data.PhoneContact
import com.example.visionbridge.data.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallingViewModel(application: Application) : AndroidViewModel(application) {

    private val callingApi = CallingApi(application)
    private val sessionManager = SessionManager.getInstance(application)

    private val _contacts = MutableStateFlow<List<PhoneContact>>(emptyList())
    val contacts: StateFlow<List<PhoneContact>> = _contacts.asStateFlow()

    private val _recentCalls = MutableStateFlow<List<PhoneCallLog>>(emptyList())
    val recentCalls: StateFlow<List<PhoneCallLog>> = _recentCalls.asStateFlow()

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _activeContact = MutableStateFlow<PhoneContact?>(null)
    val activeContact: StateFlow<PhoneContact?> = _activeContact.asStateFlow()

    private val _targetNumber = MutableStateFlow("")
    val targetNumber: StateFlow<String> = _targetNumber.asStateFlow()

    private val _spokenPrompt = MutableStateFlow("")
    val spokenPrompt: StateFlow<String> = _spokenPrompt.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val defaultSeedContacts = listOf(
        PhoneContact(id = "seed_1", name = "Mom", phoneNumber = "+919876543210", relationship = "Mother", isFavorite = true),
        PhoneContact(id = "seed_2", name = "Rahul Sharma", phoneNumber = "+919812345678", relationship = "Friend", isFavorite = true),
        PhoneContact(id = "seed_3", name = "Dr. Mehta", phoneNumber = "+919822011223", relationship = "Doctor", isFavorite = false)
    )

    init {
        loadData()
    }

    fun loadData() {
        val user = sessionManager.currentUser.value
        if (user == null || user.id.isBlank()) {
            _isLoading.value = false
            return
        }
        val userId = user.id

        _isLoading.value = true
        viewModelScope.launch {
            val contactRes = withContext(Dispatchers.IO) {
                callingApi.getContacts(userId)
            }
            if (contactRes is ApiResult.Success && contactRes.value.isNotEmpty()) {
                _contacts.value = contactRes.value
            } else if (_contacts.value.isEmpty()) {
                _contacts.value = defaultSeedContacts
            }

            val callsRes = withContext(Dispatchers.IO) {
                callingApi.getRecentCalls(userId)
            }
            if (callsRes is ApiResult.Success) {
                _recentCalls.value = callsRes.value
            }
            _isLoading.value = false
        }
    }

    fun addContact(name: String, phoneNumber: String, relationship: String = "Friend", isFavorite: Boolean = false) {
        val user = sessionManager.currentUser.value ?: return
        val userId = user.id

        val cleaned = phoneNumber.replace(Regex("[^\\d+]"), "")
        val newContact = PhoneContact(
            id = "contact_${System.currentTimeMillis()}",
            userId = userId,
            name = name.trim(),
            phoneNumber = cleaned,
            relationship = relationship.trim(),
            isFavorite = isFavorite,
            normalizedName = name.trim().lowercase()
        )

        _contacts.value = listOf(newContact) + _contacts.value

        viewModelScope.launch(Dispatchers.IO) {
            callingApi.addContact(userId, name.trim(), cleaned, relationship, isFavorite)
        }
    }

    fun deleteContact(contactId: String) {
        _contacts.value = _contacts.value.filter { it.id != contactId }
        viewModelScope.launch(Dispatchers.IO) {
            callingApi.deleteContact(contactId)
        }
    }

    fun resolveAndInitiateCall(query: String, context: Context) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return

        // 1. Direct phone number check
        val normalizedNumber = normalizeSpokenNumber(cleanQuery)
        if (normalizedNumber.length >= 3 && (cleanQuery.matches(Regex("^[0-9+\\s()-]+$")) || normalizedNumber.length >= 10)) {
            _targetNumber.value = normalizedNumber
            _activeContact.value = null
            _callState.value = CallState.CALLING
            _spokenPrompt.value = "Dialing $normalizedNumber."
            triggerDial(normalizedNumber, null, context)
            return
        }

        // 2. Name matching against contacts
        val search = cleanQuery.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097F]"), "")
        val matches = _contacts.value.filter { c ->
            val nName = c.name.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097F]"), "")
            nName.contains(search) || search.contains(nName) || c.relationship.lowercase() == search
        }

        when {
            matches.size == 1 -> {
                val contact = matches[0]
                _activeContact.value = contact
                _targetNumber.value = contact.phoneNumber
                _callState.value = CallState.CALLING
                _spokenPrompt.value = "Calling ${contact.name}."
                triggerDial(contact.phoneNumber, contact, context)
            }
            matches.size > 1 -> {
                _callState.value = CallState.AMBIGUOUS_CONTACT
                _spokenPrompt.value = "Multiple contacts found. Please choose: ${matches.take(3).joinToString { it.name }}."
            }
            else -> {
                _callState.value = CallState.IDLE
                _spokenPrompt.value = "Contact not found for $cleanQuery."
            }
        }
    }

    fun triggerDial(phoneNumber: String, contact: PhoneContact?, context: Context) {
        val user = sessionManager.currentUser.value
        val userId = user?.id ?: "user_default"

        // Log call
        viewModelScope.launch(Dispatchers.IO) {
            callingApi.logCall(
                userId = userId,
                phoneNumber = phoneNumber,
                name = contact?.name ?: "",
                contactId = contact?.id
            )
        }

        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("CallingViewModel", "Could not start dialer intent", e)
        }
    }

    fun normalizeSpokenNumber(raw: String): String {
        var text = raw.lowercase().trim()
        text = text.replace(Regex("\\bdouble\\s+([a-z0-9\\u0900-\\u097F]+)"), "$1 $1")
        text = text.replace(Regex("\\btriple\\s+([a-z0-9\\u0900-\\u097F]+)"), "$1 $1 $1")

        val spokenMap = mapOf(
            "zero" to "0", "oh" to "0", "one" to "1", "two" to "2", "three" to "3",
            "four" to "4", "five" to "5", "six" to "6", "seven" to "7", "eight" to "8",
            "nine" to "9", "plus" to "+",
            "शून्य" to "0", "जीरो" to "0", "एक" to "1", "दो" to "2", "तीन" to "3",
            "चार" to "4", "पाँच" to "5", "पांच" to "5", "छह" to "6", "सात" to "7",
            "आठ" to "8", "नौ" to "9",
            "दोन" to "2", "पाच" to "5", "सहा" to "6", "नऊ" to "9"
        )

        val words = text.split(Regex("[\\s,.-]+"))
        var digits = ""
        for (w in words) {
            if (w.matches(Regex("^\\+?\\d+$"))) {
                digits += w
            } else if (spokenMap.containsKey(w)) {
                digits += spokenMap[w]
            }
        }
        return digits.replace(Regex("[^\\d+]"), "")
    }

    fun resetState() {
        _callState.value = CallState.IDLE
        _spokenPrompt.value = ""
    }
}
