package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.HelpRequest
import org.json.JSONObject

class VolunteerApi(private val context: Context) {

    private val apiClient = ApiClient.getInstance(context)

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
            .put("requester", requester)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("helpDescription", helpDescription.trim())
            .put("description", helpDescription.trim())
            .put("requestType", requestType)

        if (!requesterName.isNullOrBlank()) body.put("requesterName", requesterName)
        if (!address.isNullOrBlank()) body.put("address", address)
        if (!destination.isNullOrBlank()) body.put("destination", destination)

        return when (val res = apiClient.post("/api/volunteer/request", body, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    ApiResult.Success(parseHelpRequest(data))
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse volunteer request response.",
                            technicalDetail = "Missing data object in create help request response"
                        )
                    )
                }
            }
        }
    }

    fun getRequests(): ApiResult<List<HelpRequest>> {
        return when (val res = apiClient.get("/api/volunteer/requests", authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val list = mutableListOf<HelpRequest>()
                val arr = res.value.optJSONArray("data") ?: res.value.optJSONArray("requests")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i)
                        if (item != null) {
                            list.add(parseHelpRequest(item))
                        }
                    }
                }
                ApiResult.Success(list)
            }
        }
    }

    fun getRequestStatus(requestId: String): ApiResult<HelpRequest> {
        return when (val res = apiClient.get("/api/volunteer/request/$requestId", authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    ApiResult.Success(parseHelpRequest(data))
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not fetch request status.",
                            technicalDetail = "Missing data object in request status response"
                        )
                    )
                }
            }
        }
    }

    fun acceptRequest(requestId: String, volunteerId: String): ApiResult<HelpRequest> {
        val body = JSONObject().put("volunteerId", volunteerId)
        return when (val res = apiClient.post("/api/volunteer/request/$requestId/accept", body, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    ApiResult.Success(parseHelpRequest(data))
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not accept request.",
                            technicalDetail = "Missing data object in accept response"
                        )
                    )
                }
            }
        }
    }

    fun cancelRequest(requestId: String): ApiResult<HelpRequest> {
        return when (val res = apiClient.post("/api/volunteer/request/$requestId/cancel", JSONObject(), authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    ApiResult.Success(parseHelpRequest(data))
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not cancel request.",
                            technicalDetail = "Missing data in cancel response"
                        )
                    )
                }
            }
        }
    }

    fun completeRequest(requestId: String): ApiResult<HelpRequest> {
        return when (val res = apiClient.post("/api/volunteer/request/$requestId/complete", JSONObject(), authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    ApiResult.Success(parseHelpRequest(data))
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not complete request.",
                            technicalDetail = "Missing data in complete response"
                        )
                    )
                }
            }
        }
    }

    fun startCallLog(requestId: String): ApiResult<Boolean> {
        return when (val res = apiClient.post("/api/volunteer/request/$requestId/call/start", JSONObject(), authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> ApiResult.Success(true)
        }
    }

    fun endCallLog(requestId: String, status: String = "COMPLETED"): ApiResult<Boolean> {
        val body = JSONObject().put("status", status)
        return when (val res = apiClient.post("/api/volunteer/request/$requestId/call/end", body, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> ApiResult.Success(true)
        }
    }

    companion object {
        fun parseHelpRequest(json: JSONObject): HelpRequest {
            val id = json.optString("_id", json.optString("id", ""))
            val requester = json.optString("requester", "")
            val requesterName = json.optString("requesterName", requester)
            val requestType = json.optString("requestType", "general")
            val desc = json.optString("helpDescription", json.optString("description", ""))
            val status = json.optString("status", "PENDING")
            val destination = if (json.has("destination") && !json.isNull("destination")) json.optString("destination") else null
            val createdAt = if (json.has("createdAt") && !json.isNull("createdAt")) json.optString("createdAt") else null

            var lat = 0.0
            var lng = 0.0
            var addr: String? = null

            val locObj = json.optJSONObject("currentLocation")
            if (locObj != null) {
                lat = locObj.optDouble("latitude", 0.0)
                lng = locObj.optDouble("longitude", 0.0)
                addr = if (locObj.has("address") && !locObj.isNull("address")) locObj.optString("address") else null
            } else {
                lat = json.optDouble("latitude", 0.0)
                lng = json.optDouble("longitude", 0.0)
            }

            var volId: String? = null
            var volName: String? = null
            val volObj = json.optJSONObject("volunteer")
            if (volObj != null) {
                volId = if (volObj.has("_id")) volObj.optString("_id") else if (volObj.has("id")) volObj.optString("id") else null
                volName = if (volObj.has("name") && !volObj.isNull("name")) volObj.optString("name") else null
            } else if (json.has("volunteer") && !json.isNull("volunteer")) {
                volId = json.optString("volunteer")
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
