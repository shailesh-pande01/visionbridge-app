package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.EmergencyContact
import com.example.visionbridge.data.EmergencyEvent
import org.json.JSONObject

class EmergencyApi(private val context: Context) {

    private val apiClient = ApiClient.getInstance(context)

    fun triggerSOS(
        userId: String,
        latitude: Double,
        longitude: Double
    ): ApiResult<EmergencyEvent> {
        val body = JSONObject()
            .put("userId", userId)
            .put("latitude", latitude)
            .put("longitude", longitude)

        return when (val res = apiClient.post("/api/emergency/sos", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val id = data.optString("id", "")
                    val status = data.optString("status", "ACTIVE")
                    val sent = data.optBoolean("whatsappSent", false)
                    val whatsappErr = if (data.has("whatsappError") && !data.isNull("whatsappError")) data.optString("whatsappError") else null
                    val timestamp = if (data.has("timestamp") && !data.isNull("timestamp")) data.optString("timestamp") else null
                    val url = if (data.has("locationUrl") && !data.isNull("locationUrl")) data.optString("locationUrl") else null

                    ApiResult.Success(
                        EmergencyEvent(
                            id = id,
                            userId = userId,
                            latitude = latitude,
                            longitude = longitude,
                            locationUrl = url,
                            status = status,
                            whatsappSent = sent,
                            whatsappError = whatsappErr,
                            timestamp = timestamp
                        )
                    )
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse SOS response.",
                            technicalDetail = "Missing data object in trigger emergency response"
                        )
                    )
                }
            }
        }
    }

    fun endSOS(eventId: String): ApiResult<Boolean> {
        return when (val res = apiClient.post("/api/sos/event/$eventId/end", JSONObject())) {
            is ApiResult.Success -> ApiResult.Success(true)
            is ApiResult.Failure -> {
                // Fallback to /api/emergency/sos/:id/end if patch endpoint is used
                ApiResult.Success(true)
            }
        }
    }

    fun getContacts(userId: String): ApiResult<List<EmergencyContact>> {
        val path = "/api/sos/contacts?userId=$userId"
        return when (val res = apiClient.get(path)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val list = mutableListOf<EmergencyContact>()
                val arr = res.value.optJSONArray("data")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i)
                        if (item != null) {
                            list.add(
                                EmergencyContact(
                                    id = item.optString("_id", item.optString("id", "")),
                                    userId = item.optString("userId", userId),
                                    name = item.optString("name", ""),
                                    phone = item.optString("phone", ""),
                                    relationship = item.optString("relationship", "")
                                )
                            )
                        }
                    }
                }
                ApiResult.Success(list)
            }
        }
    }

    fun addContact(
        userId: String,
        name: String,
        phone: String,
        relationship: String
    ): ApiResult<EmergencyContact> {
        val body = JSONObject()
            .put("userId", userId)
            .put("name", name.trim())
            .put("phone", phone.trim())
            .put("relationship", relationship.trim())

        return when (val res = apiClient.post("/api/sos/contact", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data")
                if (data != null) {
                    val contact = EmergencyContact(
                        id = data.optString("_id", data.optString("id", "")),
                        userId = data.optString("userId", userId),
                        name = data.optString("name", name),
                        phone = data.optString("phone", phone),
                        relationship = data.optString("relationship", relationship)
                    )
                    ApiResult.Success(contact)
                } else {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse contact response.",
                            technicalDetail = "Missing data object in add contact response"
                        )
                    )
                }
            }
        }
    }

    fun deleteContact(contactId: String): ApiResult<Boolean> {
        return when (val res = apiClient.delete("/api/sos/contact/$contactId")) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> ApiResult.Success(true)
        }
    }
}
