package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.HelpRequest
import com.example.visionbridge.supabase.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject

class VolunteerApi(private val context: Context) {

    private val supabaseClient = SupabaseClient.getInstance(context)

    fun createRequest(
        requester: String,
        latitude: Double,
        longitude: Double,
        helpDescription: String,
        requesterName: String? = null,
        address: String? = null,
        destination: String? = null,
        requestType: String = "general"
    ): ApiResult<HelpRequest> {
        val body = JSONObject()
            .put("requester_id", requester)
            .put("requester_name", requesterName ?: requester)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("help_description", helpDescription.trim())
            .put("request_type", requestType)
            .put("address", address ?: "")
            .put("destination", destination ?: "")
            .put("status", "PENDING")

        return when (val res = supabaseClient.restPost("help_requests", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    val data = arr.getJSONObject(0)
                    ApiResult.Success(parseHelpRequest(data))
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse created help request.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun getRequests(): ApiResult<List<HelpRequest>> {
        val query = "help_requests?status=in.(PENDING,searching)&order=created_at.desc"
        return when (val res = supabaseClient.restGet(query)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val list = mutableListOf<HelpRequest>()
                try {
                    val arr = JSONArray(res.value)
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(parseHelpRequest(item))
                    }
                    ApiResult.Success(list)
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse help requests.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun getRequestStatus(requestId: String): ApiResult<HelpRequest> {
        val query = "help_requests?id=eq.$requestId&select=*"
        return when (val res = supabaseClient.restGet(query)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    if (arr.length() > 0) {
                        ApiResult.Success(parseHelpRequest(arr.getJSONObject(0)))
                    } else {
                        ApiResult.Failure(
                            ApiError(
                                kind = ApiError.Kind.HTTP_ERROR,
                                userMessage = "Help request not found.",
                                technicalDetail = "Empty array for id $requestId"
                            )
                        )
                    }
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse request status.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun acceptRequest(requestId: String, volunteerId: String): ApiResult<HelpRequest> {
        val params = JSONObject().put("p_request_id", requestId)
        return when (val res = supabaseClient.rpc("accept_help_request", params)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val json = JSONObject(res.value)
                    ApiResult.Success(parseHelpRequest(json))
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse accepted request response.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun cancelRequest(requestId: String): ApiResult<HelpRequest> {
        val params = JSONObject().put("p_request_id", requestId)
        return when (val res = supabaseClient.rpc("cancel_help_request", params)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val json = JSONObject(res.value)
                    ApiResult.Success(parseHelpRequest(json))
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse cancel response.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun completeRequest(requestId: String): ApiResult<HelpRequest> {
        val params = JSONObject().put("p_request_id", requestId)
        return when (val res = supabaseClient.rpc("complete_help_request", params)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val json = JSONObject(res.value)
                    ApiResult.Success(parseHelpRequest(json))
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse complete response.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun startCallLog(requestId: String): ApiResult<Boolean> {
        val params = JSONObject().put("p_help_request_id", requestId)
        return when (val res = supabaseClient.rpc("start_volunteer_call_log", params)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> ApiResult.Success(true)
        }
    }

    fun endCallLog(requestId: String, status: String = "COMPLETED"): ApiResult<Boolean> {
        val params = JSONObject()
            .put("p_help_request_id", requestId)
            .put("p_status", status)
        return when (val res = supabaseClient.rpc("end_volunteer_call_log", params)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> ApiResult.Success(true)
        }
    }

    companion object {
        fun parseHelpRequest(json: JSONObject): HelpRequest {
            val id = json.optString("id", json.optString("_id", ""))
            val requester = json.optString("requester_id", json.optString("requester", ""))
            val requesterName = json.optString("requester_name", json.optString("requesterName", requester))
            val requestType = json.optString("request_type", json.optString("requestType", "general"))
            val desc = json.optString("help_description", json.optString("helpDescription", json.optString("description", "")))
            val status = json.optString("status", "PENDING")
            val destination = if (json.has("destination") && !json.isNull("destination")) json.optString("destination") else null
            val createdAt = if (json.has("created_at")) json.optString("created_at") else if (json.has("createdAt")) json.optString("createdAt") else null

            var lat = json.optDouble("latitude", 0.0)
            var lng = json.optDouble("longitude", 0.0)
            var addr = if (json.has("address") && !json.isNull("address")) json.optString("address") else null

            val locObj = json.optJSONObject("currentLocation")
            if (locObj != null) {
                lat = locObj.optDouble("latitude", lat)
                lng = locObj.optDouble("longitude", lng)
                if (locObj.has("address") && !locObj.isNull("address")) {
                    addr = locObj.optString("address")
                }
            }

            var volId: String? = null
            var volName: String? = null

            if (json.has("volunteer_id") && !json.isNull("volunteer_id")) {
                volId = json.optString("volunteer_id")
            } else if (json.has("volunteer") && !json.isNull("volunteer")) {
                val volObj = json.optJSONObject("volunteer")
                if (volObj != null) {
                    volId = if (volObj.has("id")) volObj.optString("id") else volObj.optString("_id")
                    volName = if (volObj.has("name") && !volObj.isNull("name")) volObj.optString("name") else null
                } else {
                    volId = json.optString("volunteer")
                }
            }

            if (volName.isNullOrBlank() && json.has("volunteer_name") && !json.isNull("volunteer_name")) {
                volName = json.optString("volunteer_name")
            }

            return HelpRequest(
                id = id,
                requester = requester,
                requesterName = requesterName,
                requestType = requestType,
                latitude = lat,
                longitude = lng,
                address = addr,
                destination = destination,
                helpDescription = desc,
                status = status,
                volunteerId = volId,
                volunteerName = volName,
                createdAt = createdAt
            )
        }
    }
}
