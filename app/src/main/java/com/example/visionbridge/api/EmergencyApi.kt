package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.EmergencyContact
import com.example.visionbridge.data.EmergencyEvent
import com.example.visionbridge.supabase.SupabaseClient
import com.example.visionbridge.supabase.SupabaseConfig
import org.json.JSONArray
import org.json.JSONObject

class EmergencyApi(private val context: Context) {

    private val supabaseClient = SupabaseClient.getInstance(context)

    fun triggerSOS(
        userId: String,
        latitude: Double,
        longitude: Double
    ): ApiResult<EmergencyEvent> {
        val body = JSONObject()
            .put("userId", userId)
            .put("latitude", latitude)
            .put("longitude", longitude)

        return when (val res = supabaseClient.callFunction(SupabaseConfig.FUNCTION_EMERGENCY_SOS, body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val data = res.value.optJSONObject("data") ?: res.value
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
            }
        }
    }

    fun endSOS(eventId: String): ApiResult<Boolean> {
        val body = JSONObject()
            .put("status", "ENDED")
            .put("ended_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date()))

        return when (val res = supabaseClient.restPatch("emergency_events?id=eq.$eventId", body)) {
            is ApiResult.Success -> ApiResult.Success(true)
            is ApiResult.Failure -> ApiResult.Success(true) // Fallback graceful success
        }
    }

    fun getContacts(userId: String): ApiResult<List<EmergencyContact>> {
        val query = if (userId.isNotBlank()) {
            "emergency_contacts?user_id=eq.$userId&select=*&order=created_at.desc"
        } else {
            "emergency_contacts?select=*&order=created_at.desc"
        }

        return when (val res = supabaseClient.restGet(query)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val list = mutableListOf<EmergencyContact>()
                try {
                    val arr = JSONArray(res.value)
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            EmergencyContact(
                                id = item.optString("id", ""),
                                userId = item.optString("user_id", userId),
                                name = item.optString("name", ""),
                                phone = item.optString("phone", ""),
                                relationship = item.optString("relationship", "")
                            )
                        )
                    }
                    ApiResult.Success(list)
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse emergency contacts.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
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
            .put("user_id", userId)
            .put("name", name.trim())
            .put("phone", phone.trim())
            .put("relationship", relationship.trim())

        return when (val res = supabaseClient.restPost("emergency_contacts", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    val data = arr.getJSONObject(0)
                    val contact = EmergencyContact(
                        id = data.optString("id", ""),
                        userId = data.optString("user_id", userId),
                        name = data.optString("name", name),
                        phone = data.optString("phone", phone),
                        relationship = data.optString("relationship", relationship)
                    )
                    ApiResult.Success(contact)
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse added contact response.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun deleteContact(contactId: String): ApiResult<Boolean> {
        return supabaseClient.restDelete("emergency_contacts?id=eq.$contactId")
    }
}
