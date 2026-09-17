package com.example.visionbridge.api

import android.content.Context
import android.util.Log
import com.example.visionbridge.data.Medication
import com.example.visionbridge.data.MedicationExtraction
import com.example.visionbridge.data.MedicationReminder
import com.example.visionbridge.supabase.SupabaseClient
import com.example.visionbridge.supabase.SupabaseConfig
import org.json.JSONArray
import org.json.JSONObject

class MedicationApi(private val context: Context) {

    private val supabaseClient = SupabaseClient.getInstance(context)

    // ── AI Extraction via Edge Function ───────────────────────────────

    fun extractMedication(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        language: String = "en"
    ): ApiResult<MedicationExtraction> {
        val body = JSONObject()
            .put("imageBase64", imageBase64)
            .put("mimeType", mimeType)
            .put("language", language)

        Log.d(TAG, "MEDSAFE: calling function=${SupabaseConfig.FUNCTION_MEDICATION_EXTRACT}, payloadLength=${imageBase64.length}")

        val res = supabaseClient.callFunction(
            functionName = SupabaseConfig.FUNCTION_MEDICATION_EXTRACT,
            body = body,
            authRequired = false
        )

        return when (res) {
            is ApiResult.Failure -> {
                Log.e(TAG, "MEDSAFE: function invocation failed - ${res.error.technicalDetail}")
                val userMsg = when (res.error.kind) {
                    ApiError.Kind.NETWORK_IO -> "Please check your internet connection and try again."
                    ApiError.Kind.TIMEOUT -> "The medication analysis timed out. Please try again."
                    ApiError.Kind.PAYLOAD_TOO_LARGE -> "The medicine photo was too large. Please step back slightly and try again."
                    else -> {
                        val detail = res.error.technicalDetail.lowercase()
                        if (detail.contains("404") || detail.contains("not found")) {
                            "The medication analysis service is temporarily unavailable. Please try again."
                        } else {
                            "Medication scan could not be completed. Please try again."
                        }
                    }
                }
                ApiResult.Failure(res.error.copy(userMessage = userMsg))
            }
            is ApiResult.Success -> {
                Log.d(TAG, "MEDSAFE: function response received successfully")
                parseMedicationExtraction(res.value)
            }
        }
    }

    private fun parseMedicationExtraction(json: JSONObject): ApiResult<MedicationExtraction> {
        return try {
            val data = json.optJSONObject("data") ?: json

            val status = data.optString("status", "UNCERTAIN").uppercase()
            val rawName = data.optString("medicine_name", "").takeIf { it.isNotBlank() && it != "null" }
            val strength = data.optString("strength", "").takeIf { it.isNotBlank() && it != "null" }
            val form = data.optString("form", "").takeIf { it.isNotBlank() && it != "null" }
            val printedDirections = data.optString("printed_directions", "").takeIf { it.isNotBlank() && it != "null" }
            val expiryDate = data.optString("expiry_date", "").takeIf { it.isNotBlank() && it != "null" }
            val storageInfo = data.optString("storage_information", "").takeIf { it.isNotBlank() && it != "null" }
            val manufacturer = data.optString("manufacturer", "").takeIf { it.isNotBlank() && it != "null" }
            val batchNumber = data.optString("batch_number", "").takeIf { it.isNotBlank() && it != "null" }
            val rawVisibleText = data.optString("raw_visible_text", "")
            val confidence = data.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
            val uncertaintyReason = data.optString("uncertainty_reason", "").takeIf { it.isNotBlank() && it != "null" }

            Log.d(TAG, "MEDSAFE: parsed status=$status, confidence=$confidence, medicine=$rawName")

            val activeIngredients = mutableListOf<String>()
            val rawIngredients = data.optJSONArray("active_ingredients")
            if (rawIngredients != null) {
                for (i in 0 until rawIngredients.length()) {
                    val item = rawIngredients.optString(i)
                    if (item.isNotBlank()) activeIngredients.add(item)
                }
            }

            val warnings = mutableListOf<String>()
            val rawWarnings = data.optJSONArray("warnings_visible_on_package")
            if (rawWarnings != null) {
                for (i in 0 until rawWarnings.length()) {
                    val item = rawWarnings.optString(i)
                    if (item.isNotBlank()) warnings.add(item)
                }
            }

            val verificationFields = mutableListOf<String>()
            val rawVerification = data.optJSONArray("fields_needing_verification")
            if (rawVerification != null) {
                for (i in 0 until rawVerification.length()) {
                    val item = rawVerification.optString(i)
                    if (item.isNotBlank()) verificationFields.add(item)
                }
            }

            ApiResult.Success(
                MedicationExtraction(
                    status = status,
                    medicineName = rawName,
                    activeIngredients = activeIngredients,
                    strength = strength,
                    form = form,
                    printedDirections = printedDirections,
                    expiryDate = expiryDate,
                    storageInformation = storageInfo,
                    warningsVisibleOnPackage = warnings,
                    manufacturer = manufacturer,
                    batchNumber = batchNumber,
                    rawVisibleText = rawVisibleText,
                    confidence = confidence,
                    uncertaintyReason = uncertaintyReason,
                    fieldsNeedingVerification = verificationFields
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing medication JSON response", e)
            ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.MALFORMED_RESPONSE,
                    userMessage = "Could not understand the medication information returned by the server.",
                    technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                )
            )
        }
    }

    // ── Remote Medications CRUD ───────────────────────────────────────

    fun getMedications(userId: String): ApiResult<List<Medication>> {
        val query = if (userId.isNotBlank()) {
            "medications?user_id=eq.$userId&select=*&order=created_at.desc"
        } else {
            "medications?select=*&order=created_at.desc"
        }

        return when (val res = supabaseClient.restGet(query, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val list = mutableListOf<Medication>()
                try {
                    val arr = JSONArray(res.value)
                    for (i in 0 until arr.length()) {
                        list.add(Medication.fromJson(arr.getJSONObject(i)))
                    }
                    ApiResult.Success(list)
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse medications list.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun saveMedication(medication: Medication): ApiResult<Medication> {
        val body = medication.toJson()
        return when (val res = supabaseClient.restPost("medications", body, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    if (arr.length() > 0) {
                        ApiResult.Success(Medication.fromJson(arr.getJSONObject(0)))
                    } else {
                        ApiResult.Success(medication)
                    }
                } catch (_: Exception) {
                    ApiResult.Success(medication)
                }
            }
        }
    }

    fun updateMedication(medication: Medication): ApiResult<Medication> {
        val body = medication.toJson()
        return when (val res = supabaseClient.restPatch("medications?id=eq.${medication.id}", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    if (arr.length() > 0) {
                        ApiResult.Success(Medication.fromJson(arr.getJSONObject(0)))
                    } else {
                        ApiResult.Success(medication)
                    }
                } catch (_: Exception) {
                    ApiResult.Success(medication)
                }
            }
        }
    }

    fun deleteMedication(medicationId: String): ApiResult<Boolean> {
        return supabaseClient.restDelete("medications?id=eq.$medicationId")
    }

    // ── Remote Medication Reminders CRUD ──────────────────────────────

    fun getReminders(userId: String): ApiResult<List<MedicationReminder>> {
        val query = if (userId.isNotBlank()) {
            "medication_reminders?user_id=eq.$userId&select=*&order=reminder_time.asc"
        } else {
            "medication_reminders?select=*&order=reminder_time.asc"
        }

        return when (val res = supabaseClient.restGet(query, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                val list = mutableListOf<MedicationReminder>()
                try {
                    val arr = JSONArray(res.value)
                    for (i in 0 until arr.length()) {
                        list.add(MedicationReminder.fromJson(arr.getJSONObject(i)))
                    }
                    ApiResult.Success(list)
                } catch (e: Exception) {
                    ApiResult.Failure(
                        ApiError(
                            kind = ApiError.Kind.MALFORMED_RESPONSE,
                            userMessage = "Could not parse medication reminders.",
                            technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun saveReminder(reminder: MedicationReminder): ApiResult<MedicationReminder> {
        val body = reminder.toJson()
        return when (val res = supabaseClient.restPost("medication_reminders", body, authRequired = true)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    if (arr.length() > 0) {
                        ApiResult.Success(MedicationReminder.fromJson(arr.getJSONObject(0)))
                    } else {
                        ApiResult.Success(reminder)
                    }
                } catch (_: Exception) {
                    ApiResult.Success(reminder)
                }
            }
        }
    }

    fun updateReminder(reminder: MedicationReminder): ApiResult<MedicationReminder> {
        val body = reminder.toJson()
        return when (val res = supabaseClient.restPatch("medication_reminders?id=eq.${reminder.id}", body)) {
            is ApiResult.Failure -> res
            is ApiResult.Success -> {
                try {
                    val arr = JSONArray(res.value)
                    if (arr.length() > 0) {
                        ApiResult.Success(MedicationReminder.fromJson(arr.getJSONObject(0)))
                    } else {
                        ApiResult.Success(reminder)
                    }
                } catch (_: Exception) {
                    ApiResult.Success(reminder)
                }
            }
        }
    }

    fun deleteReminder(reminderId: String): ApiResult<Boolean> {
        return supabaseClient.restDelete("medication_reminders?id=eq.$reminderId")
    }

    companion object {
        private const val TAG = "VB-MedicationApi"
    }
}
