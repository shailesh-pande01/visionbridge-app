package com.example.visionbridge.api

import android.content.Context
import com.example.visionbridge.data.PhoneCallLog
import com.example.visionbridge.data.PhoneContact
import com.example.visionbridge.supabase.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject

class CallingApi(private val context: Context) {

    private val supabaseClient = SupabaseClient.getInstance(context)

    fun getContacts(userId: String): ApiResult<List<PhoneContact>> {
        val query = if (userId.isNotBlank()) {
            "phone_contacts?user_id=eq.$userId&select=*&order=is_favorite.desc,name.asc"
        } else {
            "phone_contacts?select=*&order=is_favorite.desc,name.asc"
        }

        return when (val res = supabaseClient.restGet(query)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val list = mutableListOf<PhoneContact>()
                try {
                    val arr = JSONArray(res.value)
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            PhoneContact(
                                id = item.optString("id", ""),
                                userId = item.optString("user_id", userId),
                                name = item.optString("name", ""),
                                phoneNumber = item.optString("phone_number", ""),
                                relationship = item.optString("relationship", "Friend"),
                                isFavorite = item.optBoolean("is_favorite", false),
                                normalizedName = item.optString("name", "").lowercase()
                            )
                        )
                    }
                    ApiResult.Success(list)
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse contacts list.",
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
        phoneNumber: String,
        relationship: String = "Friend",
        isFavorite: Boolean = false
    ): ApiResult<PhoneContact> {
        val body = JSONObject()
            .put("user_id", userId)
            .put("name", name)
            .put("phone_number", phoneNumber)
            .put("relationship", relationship)
            .put("is_favorite", isFavorite)

        return when (val res = supabaseClient.restPost("phone_contacts", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    val item = arr.getJSONObject(0)
                    ApiResult.Success(
                        PhoneContact(
                            id = item.optString("id", ""),
                            userId = item.optString("user_id", userId),
                            name = item.optString("name", name),
                            phoneNumber = item.optString("phone_number", phoneNumber),
                            relationship = item.optString("relationship", relationship),
                            isFavorite = item.optBoolean("is_favorite", isFavorite),
                            normalizedName = name.lowercase()
                        )
                    )
                } catch (e: Exception) {
                    ApiResult.Success(
                        PhoneContact(
                            id = "local_${System.currentTimeMillis()}",
                            userId = userId,
                            name = name,
                            phoneNumber = phoneNumber,
                            relationship = relationship,
                            isFavorite = isFavorite,
                            normalizedName = name.lowercase()
                        )
                    )
                }
            }
        }
    }

    fun deleteContact(contactId: String): ApiResult<Boolean> {
        return when (val res = supabaseClient.restDelete("phone_contacts?id=eq.$contactId")) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> ApiResult.Success(true)
        }
    }

    fun getRecentCalls(userId: String, limit: Int = 20): ApiResult<List<PhoneCallLog>> {
        val query = if (userId.isNotBlank()) {
            "phone_call_logs?user_id=eq.$userId&select=*&order=started_at.desc&limit=$limit"
        } else {
            "phone_call_logs?select=*&order=started_at.desc&limit=$limit"
        }

        return when (val res = supabaseClient.restGet(query)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val list = mutableListOf<PhoneCallLog>()
                try {
                    val arr = JSONArray(res.value)
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            PhoneCallLog(
                                id = item.optString("id", ""),
                                userId = item.optString("user_id", userId),
                                contactId = if (item.has("contact_id") && !item.isNull("contact_id")) item.optString("contact_id") else null,
                                name = item.optString("name", ""),
                                phoneNumber = item.optString("phone_number", ""),
                                direction = item.optString("direction", "outgoing"),
                                status = item.optString("status", "completed"),
                                duration = item.optInt("duration", 0),
                                startedAt = item.optString("started_at", "")
                            )
                        )
                    }
                    ApiResult.Success(list)
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse recent calls.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun logCall(
        userId: String,
        phoneNumber: String,
        name: String = "",
        contactId: String? = null,
        direction: String = "outgoing"
    ): ApiResult<PhoneCallLog> {
        val body = JSONObject()
            .put("user_id", userId)
            .put("phone_number", phoneNumber)
            .put("name", name)
            .put("direction", direction)
            .put("status", "initiated")

        if (contactId != null) {
            body.put("contact_id", contactId)
        }

        return when (val res = supabaseClient.restPost("phone_call_logs", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    val item = arr.getJSONObject(0)
                    ApiResult.Success(
                        PhoneCallLog(
                            id = item.optString("id", ""),
                            userId = item.optString("user_id", userId),
                            contactId = contactId,
                            name = name,
                            phoneNumber = phoneNumber,
                            direction = direction,
                            status = "initiated",
                            startedAt = item.optString("started_at", "")
                        )
                    )
                } catch (e: Exception) {
                    ApiResult.Success(
                        PhoneCallLog(
                            id = "local_${System.currentTimeMillis()}",
                            userId = userId,
                            contactId = contactId,
                            name = name,
                            phoneNumber = phoneNumber,
                            direction = direction,
                            status = "initiated"
                        )
                    )
                }
            }
        }
    }
}
