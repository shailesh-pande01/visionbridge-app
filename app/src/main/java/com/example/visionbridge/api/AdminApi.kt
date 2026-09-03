package com.example.visionbridge.api

import android.content.Context
import android.util.Log
import com.example.visionbridge.data.*
import com.example.visionbridge.supabase.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class AdminApi(private val context: Context) {

    private val supabaseClient = SupabaseClient.getInstance(context)
    private val apiClient = ApiClient.getInstance(context)
    private val sessionManager = SessionManager.getInstance(context)

    /**
     * Strict zero-trust backend authorization check:
     * Verifies against Supabase `profiles` table that the current authenticated user's
     * server-side role is strictly "admin".
     */
    fun verifyAdminRole(): ApiResult<Boolean> {
        val user = sessionManager.currentUser.value
        if (user == null || user.token.isNullOrBlank()) {
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.BACKEND_AUTH,
                    userMessage = "No authenticated session. Please sign in as an administrator.",
                    technicalDetail = "verifyAdminRole: User or token is null"
                )
            )
        }

        val userId = user.id
        return when (val res = supabaseClient.restGet("profiles?id=eq.$userId&select=role")) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    if (arr.length() > 0) {
                        val role = arr.getJSONObject(0).optString("role", "")
                        if (role.equals("admin", ignoreCase = true)) {
                            ApiResult.Success(true)
                        } else {
                            ApiResult.Failure(
                                ApiError(
                                    kind = ApiError.Kind.BACKEND_AUTH,
                                    userMessage = "This account does not have administrator access.",
                                    technicalDetail = "User role is '$role', not 'admin'"
                                )
                            )
                        }
                    } else {
                        ApiResult.Failure(
                            ApiError(
                                kind = ApiError.Kind.BACKEND_AUTH,
                                userMessage = "Administrator profile not found.",
                                technicalDetail = "No profile row for id $userId"
                            )
                        )
                    }
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Failed to verify admin privileges.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Dedicated Admin Login verification:
     * Checks if credentials are valid AND role is strictly "admin".
     */
    fun adminLogin(identifier: String, password: String): ApiResult<User> {
        val authApi = AuthApi(context)
        return when (val loginRes = authApi.login(identifier, password)) {
            is ApiResult.Failure -> loginRes
            is ApiResult.Success -> {
                val user = loginRes.value
                if (user.role.equals("admin", ignoreCase = true)) {
                    ApiResult.Success(user)
                } else {
                    // Non-admin signed in through admin gate -> clear session and reject
                    sessionManager.clearSession()
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.BACKEND_AUTH,
                            userMessage = "This account does not have administrator access.",
                            technicalDetail = "Account authenticated but role is '${user.role}'"
                        )
                    )
                }
            }
        }
    }

    /**
     * Fetch community overview statistics:
     * Uses atomic Postgres RPC `get_admin_overview()`, or falls back to Node backend `/api/admin/overview`.
     */
    fun getOverview(): ApiResult<AdminOverviewStats> {
        // 1. Try Supabase RPC get_admin_overview
        val rpcRes = supabaseClient.rpc("get_admin_overview", JSONObject())
        if (rpcRes is ApiResult.Success) {
            try {
                val json = JSONObject(rpcRes.value)
                return ApiResult.Success(
                    AdminOverviewStats(
                        totalUsers = json.optInt("totalUsers", 0),
                        totalVolunteers = json.optInt("totalVolunteers", 0),
                        activeVolunteers = json.optInt("activeVolunteers", 0),
                        totalHelpRequests = json.optInt("totalHelpRequests", 0),
                        activeCalls = json.optInt("activeCalls", 0),
                        completedCalls = json.optInt("completedCalls", 0),
                        activeSos = json.optInt("activeSos", 0)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing get_admin_overview RPC: ${e.message}")
            }
        }

        // 2. Fallback: Node backend /api/admin/overview if running
        val nodeRes = apiClient.get("/api/admin/overview", authRequired = true)
        if (nodeRes is ApiResult.Success) {
            val json = nodeRes.value
            val data = json.optJSONObject("data") ?: json
            return ApiResult.Success(
                AdminOverviewStats(
                    totalUsers = data.optInt("totalUsers", 0),
                    totalVolunteers = data.optInt("totalVolunteers", 0),
                    activeVolunteers = data.optInt("activeVolunteers", 0),
                    totalHelpRequests = data.optInt("totalHelpRequests", 0),
                    activeCalls = data.optInt("activeCalls", 0),
                    completedCalls = data.optInt("completedCalls", 0),
                    activeSos = data.optInt("activeSos", 0)
                )
            )
        }

        // 3. Fallback: Direct PostgREST multi-count fallback
        return try {
            val usersRes = supabaseClient.restGet("profiles?role=eq.lowVisionUser&select=id")
            val volsRes = supabaseClient.restGet("profiles?role=eq.volunteer&select=id,availability")
            val reqsRes = supabaseClient.restGet("help_requests?select=id")
            val callsRes = supabaseClient.restGet("volunteer_call_logs?select=id,status")
            val sosRes = supabaseClient.restGet("emergency_events?status=eq.ACTIVE&select=id")

            val usersCount = if (usersRes is ApiResult.Success) JSONArray(usersRes.value).length() else 0
            val volsArray = if (volsRes is ApiResult.Success) JSONArray(volsRes.value) else JSONArray()
            val totalVols = volsArray.length()
            var activeVols = 0
            for (i in 0 until totalVols) {
                if (volsArray.getJSONObject(i).optBoolean("availability", true)) {
                    activeVols++
                }
            }
            val reqsCount = if (reqsRes is ApiResult.Success) JSONArray(reqsRes.value).length() else 0
            val callsArray = if (callsRes is ApiResult.Success) JSONArray(callsRes.value) else JSONArray()
            var activeCalls = 0
            var completedCalls = 0
            for (i in 0 until callsArray.length()) {
                val st = callsArray.getJSONObject(i).optString("status", "")
                if (st.equals("ACTIVE", ignoreCase = true)) activeCalls++
                if (st.equals("COMPLETED", ignoreCase = true)) completedCalls++
            }
            val sosCount = if (sosRes is ApiResult.Success) JSONArray(sosRes.value).length() else 0

            ApiResult.Success(
                AdminOverviewStats(
                    totalUsers = usersCount,
                    totalVolunteers = totalVols,
                    activeVolunteers = activeVols,
                    totalHelpRequests = reqsCount,
                    activeCalls = activeCalls,
                    completedCalls = completedCalls,
                    activeSos = sosCount
                )
            )
        } catch (e: Exception) {
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.UNKNOWN,
                    userMessage = "Could not load overview stats.",
                    technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                )
            )
        }
    }

    /**
     * Fetch paginated low-vision users list with optional search query.
     */
    fun getUsers(
        page: Int = 1,
        limit: Int = 10,
        search: String = ""
    ): ApiResult<AdminPaginatedResult<AdminUserItem>> {
        val cleanSearch = search.trim()
        val offset = (page - 1) * limit

        // Build PostgREST query
        val sb = StringBuilder("profiles?role=eq.lowVisionUser&select=*&order=created_at.desc&limit=$limit&offset=$offset")
        if (cleanSearch.isNotBlank()) {
            val encoded = encodeQuery(cleanSearch)
            sb.append("&or=(name.ilike.*$encoded*,username.ilike.*$encoded*)")
        }

        return when (val res = supabaseClient.restGet(sb.toString())) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    val list = mutableListOf<AdminUserItem>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            AdminUserItem(
                                id = item.optString("id", ""),
                                name = item.optString("name", "User"),
                                username = item.optString("username", ""),
                                role = item.optString("role", "lowVisionUser"),
                                phone = if (item.has("phone") && !item.isNull("phone")) item.optString("phone") else null,
                                emergencyWhatsappNumber = if (item.has("emergency_whatsapp_number") && !item.isNull("emergency_whatsapp_number")) item.optString("emergency_whatsapp_number") else null,
                                createdAt = if (item.has("created_at")) item.optString("created_at") else null
                            )
                        )
                    }

                    val total = if (list.size < limit && page == 1) list.size else (page * limit + (if (list.size == limit) 1 else 0))
                    val totalPages = Math.max(1, (total + limit - 1) / limit)

                    ApiResult.Success(
                        AdminPaginatedResult(
                            data = list,
                            pagination = AdminPagination(page = page, limit = limit, total = total, totalPages = totalPages)
                        )
                    )
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse users list.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Fetch paginated volunteers with search, availability filter, and request statistics.
     */
    fun getVolunteers(
        page: Int = 1,
        limit: Int = 10,
        search: String = "",
        availability: String = "all"
    ): ApiResult<AdminPaginatedResult<AdminVolunteerItem>> {
        val cleanSearch = search.trim()
        val offset = (page - 1) * limit

        val sb = StringBuilder("profiles?role=eq.volunteer&select=*&order=created_at.desc&limit=$limit&offset=$offset")
        if (availability.equals("available", ignoreCase = true)) {
            sb.append("&availability=eq.true")
        } else if (availability.equals("unavailable", ignoreCase = true)) {
            sb.append("&availability=eq.false")
        }

        if (cleanSearch.isNotBlank()) {
            val encoded = encodeQuery(cleanSearch)
            sb.append("&or=(name.ilike.*$encoded*,username.ilike.*$encoded*)")
        }

        return when (val res = supabaseClient.restGet(sb.toString())) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    val volunteerList = mutableListOf<AdminVolunteerItem>()

                    // Fetch aggregated help_requests for accepted / completed counts
                    val requestsRes = supabaseClient.restGet("help_requests?select=volunteer_id,status")
                    val acceptedMap = mutableMapOf<String, Int>()
                    val completedMap = mutableMapOf<String, Int>()

                    if (requestsRes is ApiResult.Success) {
                        val reqArr = JSONArray(requestsRes.value)
                        for (i in 0 until reqArr.length()) {
                            val r = reqArr.getJSONObject(i)
                            val volId = r.optString("volunteer_id", "")
                            val status = r.optString("status", "").uppercase()
                            if (volId.isNotBlank()) {
                                if (status in listOf("ACCEPTED", "ACTIVE")) {
                                    acceptedMap[volId] = (acceptedMap[volId] ?: 0) + 1
                                } else if (status == "COMPLETED") {
                                    completedMap[volId] = (completedMap[volId] ?: 0) + 1
                                }
                            }
                        }
                    }

                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val id = item.optString("id", "")
                        volunteerList.add(
                            AdminVolunteerItem(
                                id = id,
                                name = item.optString("name", "Volunteer"),
                                username = item.optString("username", ""),
                                role = item.optString("role", "volunteer"),
                                availability = item.optBoolean("availability", true),
                                acceptedRequests = acceptedMap[id] ?: 0,
                                completedRequests = completedMap[id] ?: 0,
                                phone = if (item.has("phone") && !item.isNull("phone")) item.optString("phone") else null,
                                createdAt = if (item.has("created_at")) item.optString("created_at") else null
                            )
                        )
                    }

                    val total = if (volunteerList.size < limit && page == 1) volunteerList.size else (page * limit + (if (volunteerList.size == limit) 1 else 0))
                    val totalPages = Math.max(1, (total + limit - 1) / limit)

                    ApiResult.Success(
                        AdminPaginatedResult(
                            data = volunteerList,
                            pagination = AdminPagination(page = page, limit = limit, total = total, totalPages = totalPages)
                        )
                    )
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse volunteers list.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Fetch volunteer call logs with user/volunteer name lookups, status filter, date range, and sorting.
     */
    fun getCallLogs(
        page: Int = 1,
        limit: Int = 10,
        search: String = "",
        status: String = "all",
        fromDate: String = "",
        toDate: String = "",
        sort: String = "latest"
    ): ApiResult<AdminPaginatedResult<AdminCallLogItem>> {
        val cleanSearch = search.trim()
        val offset = (page - 1) * limit
        val order = if (sort.equals("oldest", ignoreCase = true)) "started_at.asc" else "started_at.desc"

        val sb = StringBuilder("volunteer_call_logs?select=*&order=$order&limit=$limit&offset=$offset")
        if (status.isNotBlank() && !status.equals("all", ignoreCase = true)) {
            sb.append("&status=eq.${status.uppercase()}")
        }
        if (fromDate.isNotBlank()) {
            sb.append("&started_at=gte.${fromDate}T00:00:00Z")
        }
        if (toDate.isNotBlank()) {
            sb.append("&started_at=lte.${toDate}T23:59:59Z")
        }

        return when (val res = supabaseClient.restGet(sb.toString())) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)

                    // Collect all user IDs and volunteer IDs to populate profile names
                    val personIds = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val uId = item.optString("user_id", "")
                        val vId = item.optString("volunteer_id", "")
                        if (uId.isNotBlank()) personIds.add(uId)
                        if (vId.isNotBlank()) personIds.add(vId)
                    }

                    val profileMap = mutableMapOf<String, Pair<String, String>>() // id -> (name, username)
                    if (personIds.isNotEmpty()) {
                        val inQuery = personIds.joinToString(",")
                        val profRes = supabaseClient.restGet("profiles?id=in.($inQuery)&select=id,name,username")
                        if (profRes is ApiResult.Success) {
                            val profArr = JSONArray(profRes.value)
                            for (i in 0 until profArr.length()) {
                                val p = profArr.getJSONObject(i)
                                val pid = p.optString("id", "")
                                val pname = p.optString("name", "User")
                                val puname = p.optString("username", "")
                                profileMap[pid] = Pair(pname, puname)
                            }
                        }
                    }

                    val logs = mutableListOf<AdminCallLogItem>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val uId = item.optString("user_id", "")
                        val vId = item.optString("volunteer_id", "")
                        val uInfo = profileMap[uId] ?: Pair("User", "")
                        val vInfo = profileMap[vId] ?: Pair("Volunteer", "")

                        val logItem = AdminCallLogItem(
                            id = item.optString("id", ""),
                            helpRequestId = item.optString("help_request_id", ""),
                            userId = uId,
                            userName = uInfo.first,
                            userUsername = uInfo.second,
                            volunteerId = vId,
                            volunteerName = vInfo.first,
                            volunteerUsername = vInfo.second,
                            startedAt = if (item.has("started_at")) item.optString("started_at") else null,
                            endedAt = if (item.has("ended_at") && !item.isNull("ended_at")) item.optString("ended_at") else null,
                            durationSec = if (item.has("duration_sec") && !item.isNull("duration_sec")) item.optInt("duration_sec") else null,
                            status = item.optString("status", "COMPLETED"),
                            createdAt = if (item.has("created_at")) item.optString("created_at") else null
                        )

                        // Client-side search match filter if search was applied
                        if (cleanSearch.isBlank() ||
                            logItem.userName.contains(cleanSearch, ignoreCase = true) ||
                            logItem.userUsername.contains(cleanSearch, ignoreCase = true) ||
                            logItem.volunteerName.contains(cleanSearch, ignoreCase = true) ||
                            logItem.volunteerUsername.contains(cleanSearch, ignoreCase = true)
                        ) {
                            logs.add(logItem)
                        }
                    }

                    val total = if (logs.size < limit && page == 1) logs.size else (page * limit + (if (logs.size == limit) 1 else 0))
                    val totalPages = Math.max(1, (total + limit - 1) / limit)

                    ApiResult.Success(
                        AdminPaginatedResult(
                            data = logs,
                            pagination = AdminPagination(page = page, limit = limit, total = total, totalPages = totalPages)
                        )
                    )
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse call logs.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Fetch emergency events (SOS alerts) for live monitoring.
     */
    fun getEmergencyEvents(
        page: Int = 1,
        limit: Int = 10,
        status: String = "all"
    ): ApiResult<AdminPaginatedResult<AdminEmergencyItem>> {
        val offset = (page - 1) * limit
        val sb = StringBuilder("emergency_events?select=*&order=created_at.desc&limit=$limit&offset=$offset")
        if (status.isNotBlank() && !status.equals("all", ignoreCase = true)) {
            sb.append("&status=eq.${status.uppercase()}")
        }

        return when (val res = supabaseClient.restGet(sb.toString())) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    val userIds = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        val uId = arr.getJSONObject(i).optString("user_id", "")
                        if (uId.isNotBlank()) userIds.add(uId)
                    }

                    val profileMap = mutableMapOf<String, Pair<String, String>>()
                    if (userIds.isNotEmpty()) {
                        val inQuery = userIds.joinToString(",")
                        val profRes = supabaseClient.restGet("profiles?id=in.($inQuery)&select=id,name,username")
                        if (profRes is ApiResult.Success) {
                            val profArr = JSONArray(profRes.value)
                            for (i in 0 until profArr.length()) {
                                val p = profArr.getJSONObject(i)
                                profileMap[p.optString("id", "")] = Pair(p.optString("name", "User"), p.optString("username", ""))
                            }
                        }
                    }

                    val events = mutableListOf<AdminEmergencyItem>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val uId = item.optString("user_id", "")
                        val uInfo = profileMap[uId] ?: Pair("User", "")

                        events.add(
                            AdminEmergencyItem(
                                id = item.optString("id", ""),
                                userId = uId,
                                userName = uInfo.first,
                                userUsername = uInfo.second,
                                latitude = item.optDouble("latitude", 0.0),
                                longitude = item.optDouble("longitude", 0.0),
                                locationUrl = if (item.has("location_url")) item.optString("location_url") else null,
                                status = item.optString("status", "ACTIVE"),
                                whatsappSent = item.optBoolean("whatsapp_sent", false),
                                whatsappError = if (item.has("whatsapp_error") && !item.isNull("whatsapp_error")) item.optString("whatsapp_error") else null,
                                endedAt = if (item.has("ended_at") && !item.isNull("ended_at")) item.optString("ended_at") else null,
                                createdAt = if (item.has("created_at")) item.optString("created_at") else null
                            )
                        )
                    }

                    val total = if (events.size < limit && page == 1) events.size else (page * limit + (if (events.size == limit) 1 else 0))
                    val totalPages = Math.max(1, (total + limit - 1) / limit)

                    ApiResult.Success(
                        AdminPaginatedResult(
                            data = events,
                            pagination = AdminPagination(page = page, limit = limit, total = total, totalPages = totalPages)
                        )
                    )
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse emergency events.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Resolve / End an Emergency SOS event.
     */
    fun resolveEmergencyEvent(eventId: String): ApiResult<Boolean> {
        val body = JSONObject()
            .put("status", "ENDED")
            .put("ended_at", java.time.Instant.now().toString())

        return when (val res = supabaseClient.restPatch("emergency_events?id=eq.$eventId", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> ApiResult.Success(true)
        }
    }

    private fun encodeQuery(query: String): String {
        return try {
            URLEncoder.encode(query, "UTF-8")
        } catch (_: Exception) {
            query
        }
    }

    companion object {
        private const val TAG = "VB-AdminApi"
    }
}
