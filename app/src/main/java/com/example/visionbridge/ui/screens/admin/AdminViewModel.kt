package com.example.visionbridge.ui.screens.admin

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.R
import com.example.visionbridge.api.AdminApi
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.data.*
import com.example.visionbridge.supabase.SupabaseRealtimeClient
import com.example.visionbridge.utils.LocaleHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val adminApi = AdminApi(application)
    private val sessionManager = SessionManager.getInstance(application)
    private val realtimeClient = SupabaseRealtimeClient()

    // ── Active Section ──────────────────────────────────────────────
    private val _activeSection = MutableStateFlow(AdminSection.OVERVIEW)
    val activeSection: StateFlow<AdminSection> = _activeSection.asStateFlow()

    // ── Overview ────────────────────────────────────────────────────
    private val _overviewStats = MutableStateFlow<AdminOverviewStats?>(null)
    val overviewStats: StateFlow<AdminOverviewStats?> = _overviewStats.asStateFlow()

    private val _overviewLoading = MutableStateFlow(false)
    val overviewLoading: StateFlow<Boolean> = _overviewLoading.asStateFlow()

    private val _overviewError = MutableStateFlow<String?>(null)
    val overviewError: StateFlow<String?> = _overviewError.asStateFlow()

    // ── Users ───────────────────────────────────────────────────────
    private val _users = MutableStateFlow<List<AdminUserItem>>(emptyList())
    val users: StateFlow<List<AdminUserItem>> = _users.asStateFlow()

    private val _usersSearch = MutableStateFlow("")
    val usersSearch: StateFlow<String> = _usersSearch.asStateFlow()

    private val _usersPage = MutableStateFlow(1)
    val usersPage: StateFlow<Int> = _usersPage.asStateFlow()

    private val _usersPagination = MutableStateFlow(AdminPagination())
    val usersPagination: StateFlow<AdminPagination> = _usersPagination.asStateFlow()

    private val _usersLoading = MutableStateFlow(false)
    val usersLoading: StateFlow<Boolean> = _usersLoading.asStateFlow()

    private val _usersError = MutableStateFlow<String?>(null)
    val usersError: StateFlow<String?> = _usersError.asStateFlow()

    // ── Volunteers ──────────────────────────────────────────────────
    private val _volunteers = MutableStateFlow<List<AdminVolunteerItem>>(emptyList())
    val volunteers: StateFlow<List<AdminVolunteerItem>> = _volunteers.asStateFlow()

    private val _volunteersSearch = MutableStateFlow("")
    val volunteersSearch: StateFlow<String> = _volunteersSearch.asStateFlow()

    private val _volunteersAvailability = MutableStateFlow("all")
    val volunteersAvailability: StateFlow<String> = _volunteersAvailability.asStateFlow()

    private val _volunteersPage = MutableStateFlow(1)
    val volunteersPage: StateFlow<Int> = _volunteersPage.asStateFlow()

    private val _volunteersPagination = MutableStateFlow(AdminPagination())
    val volunteersPagination: StateFlow<AdminPagination> = _volunteersPagination.asStateFlow()

    private val _volunteersLoading = MutableStateFlow(false)
    val volunteersLoading: StateFlow<Boolean> = _volunteersLoading.asStateFlow()

    private val _volunteersError = MutableStateFlow<String?>(null)
    val volunteersError: StateFlow<String?> = _volunteersError.asStateFlow()

    // ── Call Logs ───────────────────────────────────────────────────
    private val _callLogs = MutableStateFlow<List<AdminCallLogItem>>(emptyList())
    val callLogs: StateFlow<List<AdminCallLogItem>> = _callLogs.asStateFlow()

    private val _callLogsSearch = MutableStateFlow("")
    val callLogsSearch: StateFlow<String> = _callLogsSearch.asStateFlow()

    private val _callLogsStatus = MutableStateFlow("all")
    val callLogsStatus: StateFlow<String> = _callLogsStatus.asStateFlow()

    private val _callLogsFrom = MutableStateFlow("")
    val callLogsFrom: StateFlow<String> = _callLogsFrom.asStateFlow()

    private val _callLogsTo = MutableStateFlow("")
    val callLogsTo: StateFlow<String> = _callLogsTo.asStateFlow()

    private val _callLogsSort = MutableStateFlow("latest")
    val callLogsSort: StateFlow<String> = _callLogsSort.asStateFlow()

    private val _callLogsPage = MutableStateFlow(1)
    val callLogsPage: StateFlow<Int> = _callLogsPage.asStateFlow()

    private val _callLogsPagination = MutableStateFlow(AdminPagination())
    val callLogsPagination: StateFlow<AdminPagination> = _callLogsPagination.asStateFlow()

    private val _callLogsLoading = MutableStateFlow(false)
    val callLogsLoading: StateFlow<Boolean> = _callLogsLoading.asStateFlow()

    private val _callLogsError = MutableStateFlow<String?>(null)
    val callLogsError: StateFlow<String?> = _callLogsError.asStateFlow()

    // ── Emergency SOS ───────────────────────────────────────────────
    private val _emergencyEvents = MutableStateFlow<List<AdminEmergencyItem>>(emptyList())
    val emergencyEvents: StateFlow<List<AdminEmergencyItem>> = _emergencyEvents.asStateFlow()

    private val _emergencyLoading = MutableStateFlow(false)
    val emergencyLoading: StateFlow<Boolean> = _emergencyLoading.asStateFlow()

    private val _emergencyError = MutableStateFlow<String?>(null)
    val emergencyError: StateFlow<String?> = _emergencyError.asStateFlow()

    // ── Realtime Status ─────────────────────────────────────────────
    private val _isRealtimeConnected = MutableStateFlow(false)
    val isRealtimeConnected: StateFlow<Boolean> = _isRealtimeConnected.asStateFlow()

    // ── Profile / Auth ──────────────────────────────────────────────
    val adminUser: StateFlow<User?> = sessionManager.currentUser

    private var userSearchJob: Job? = null
    private var volunteerSearchJob: Job? = null
    private var callLogSearchJob: Job? = null

    init {
        loadOverview()
        setupRealtime()
    }

    override fun onCleared() {
        super.onCleared()
        realtimeClient.disconnect()
    }

    fun setSection(section: AdminSection) {
        _activeSection.value = section
        when (section) {
            AdminSection.OVERVIEW -> loadOverview()
            AdminSection.USERS -> if (_users.value.isEmpty()) loadUsers()
            AdminSection.VOLUNTEERS -> if (_volunteers.value.isEmpty()) loadVolunteers()
            AdminSection.CALL_LOGS -> if (_callLogs.value.isEmpty()) loadCallLogs()
            AdminSection.EMERGENCY -> loadEmergencyEvents()
            AdminSection.PROFILE -> {}
        }
    }

    fun refreshCurrentSection() {
        when (_activeSection.value) {
            AdminSection.OVERVIEW -> loadOverview()
            AdminSection.USERS -> loadUsers()
            AdminSection.VOLUNTEERS -> loadVolunteers()
            AdminSection.CALL_LOGS -> loadCallLogs()
            AdminSection.EMERGENCY -> loadEmergencyEvents()
            AdminSection.PROFILE -> {}
        }
    }

    // ── Overview Loader ─────────────────────────────────────────────
    fun loadOverview() {
        _overviewLoading.value = true
        _overviewError.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                adminApi.getOverview()
            }
            _overviewLoading.value = false
            when (result) {
                is ApiResult.Success -> _overviewStats.value = result.value
                is ApiResult.Failure -> _overviewError.value = result.error.userMessage
            }
        }
    }

    // ── Users Section ───────────────────────────────────────────────
    fun setUsersSearch(query: String) {
        _usersSearch.value = query
        userSearchJob?.cancel()
        userSearchJob = viewModelScope.launch {
            delay(300)
            _usersPage.value = 1
            loadUsers()
        }
    }

    fun setUsersPage(page: Int) {
        if (page != _usersPage.value && page >= 1) {
            _usersPage.value = page
            loadUsers()
        }
    }

    fun loadUsers() {
        _usersLoading.value = true
        _usersError.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                adminApi.getUsers(
                    page = _usersPage.value,
                    limit = 8,
                    search = _usersSearch.value
                )
            }
            _usersLoading.value = false
            when (result) {
                is ApiResult.Success -> {
                    _users.value = result.value.data
                    _usersPagination.value = result.value.pagination
                }
                is ApiResult.Failure -> _usersError.value = result.error.userMessage
            }
        }
    }

    // ── Volunteers Section ──────────────────────────────────────────
    fun setVolunteersSearch(query: String) {
        _volunteersSearch.value = query
        volunteerSearchJob?.cancel()
        volunteerSearchJob = viewModelScope.launch {
            delay(300)
            _volunteersPage.value = 1
            loadVolunteers()
        }
    }

    fun setVolunteersAvailability(availability: String) {
        _volunteersAvailability.value = availability
        _volunteersPage.value = 1
        loadVolunteers()
    }

    fun setVolunteersPage(page: Int) {
        if (page != _volunteersPage.value && page >= 1) {
            _volunteersPage.value = page
            loadVolunteers()
        }
    }

    fun loadVolunteers() {
        _volunteersLoading.value = true
        _volunteersError.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                adminApi.getVolunteers(
                    page = _volunteersPage.value,
                    limit = 8,
                    search = _volunteersSearch.value,
                    availability = _volunteersAvailability.value
                )
            }
            _volunteersLoading.value = false
            when (result) {
                is ApiResult.Success -> {
                    _volunteers.value = result.value.data
                    _volunteersPagination.value = result.value.pagination
                }
                is ApiResult.Failure -> _volunteersError.value = result.error.userMessage
            }
        }
    }

    // ── Call Logs Section ───────────────────────────────────────────
    fun setCallLogsSearch(query: String) {
        _callLogsSearch.value = query
        callLogSearchJob?.cancel()
        callLogSearchJob = viewModelScope.launch {
            delay(300)
            _callLogsPage.value = 1
            loadCallLogs()
        }
    }

    fun setCallLogsStatus(status: String) {
        _callLogsStatus.value = status
        _callLogsPage.value = 1
        loadCallLogs()
    }

    fun setCallLogsDateRange(from: String, to: String) {
        _callLogsFrom.value = from
        _callLogsTo.value = to
        _callLogsPage.value = 1
        loadCallLogs()
    }

    fun toggleCallLogsSort() {
        _callLogsSort.value = if (_callLogsSort.value == "latest") "oldest" else "latest"
        _callLogsPage.value = 1
        loadCallLogs()
    }

    fun setCallLogsPage(page: Int) {
        if (page != _callLogsPage.value && page >= 1) {
            _callLogsPage.value = page
            loadCallLogs()
        }
    }

    fun loadCallLogs() {
        _callLogsLoading.value = true
        _callLogsError.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                adminApi.getCallLogs(
                    page = _callLogsPage.value,
                    limit = 8,
                    search = _callLogsSearch.value,
                    status = _callLogsStatus.value,
                    fromDate = _callLogsFrom.value,
                    toDate = _callLogsTo.value,
                    sort = _callLogsSort.value
                )
            }
            _callLogsLoading.value = false
            when (result) {
                is ApiResult.Success -> {
                    _callLogs.value = result.value.data
                    _callLogsPagination.value = result.value.pagination
                }
                is ApiResult.Failure -> _callLogsError.value = result.error.userMessage
            }
        }
    }

    // ── Emergency SOS Section ───────────────────────────────────────
    fun loadEmergencyEvents() {
        _emergencyLoading.value = true
        _emergencyError.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                adminApi.getEmergencyEvents(page = 1, limit = 20, status = "all")
            }
            _emergencyLoading.value = false
            when (result) {
                is ApiResult.Success -> _emergencyEvents.value = result.value.data
                is ApiResult.Failure -> _emergencyError.value = result.error.userMessage
            }
        }
    }

    fun resolveEmergency(eventId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                adminApi.resolveEmergencyEvent(eventId)
            }
            if (result is ApiResult.Success) {
                loadEmergencyEvents()
                loadOverview()
            }
        }
    }

    // ── Supabase Realtime Setup ─────────────────────────────────────
    private fun setupRealtime() {
        val token = sessionManager.token
        realtimeClient.connectAndSubscribe(
            channelName = "admin-feed",
            authToken = token,
            listener = object : SupabaseRealtimeClient.RealtimeEventListener {
                override fun onConnected() {
                    _isRealtimeConnected.value = true
                    Log.d("AdminViewModel", "Admin Realtime Connected")
                }

                override fun onDisconnected() {
                    _isRealtimeConnected.value = false
                    Log.d("AdminViewModel", "Admin Realtime Disconnected")
                }

                override fun onPostgresChangeReceived(event: String, schema: String, table: String, record: JSONObject) {
                    Log.d("AdminViewModel", "Admin Realtime Event: $event on $table")
                    viewModelScope.launch(Dispatchers.Main) {
                        // Refresh overview metrics on any state changes
                        loadOverview()
                        if (table == "emergency_events" && _activeSection.value == AdminSection.EMERGENCY) {
                            loadEmergencyEvents()
                        } else if (table == "volunteer_call_logs" && _activeSection.value == AdminSection.CALL_LOGS) {
                            loadCallLogs()
                        } else if (table == "profiles") {
                            if (_activeSection.value == AdminSection.USERS) loadUsers()
                            if (_activeSection.value == AdminSection.VOLUNTEERS) loadVolunteers()
                        }
                    }
                }
            }
        )
    }
}
