package com.example.visionbridge.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Standard confidence threshold for Medication Safety Mode.
 * If AI confidence is below 0.70, results will NOT be presented as confirmed facts.
 * Instead, uncertainty is presented and human volunteer verification is offered.
 */
const val MEDICATION_CONFIDENCE_THRESHOLD = 0.70

object VerificationStatus {
    const val AI_EXTRACTED = "AI_EXTRACTED"
    const val USER_VERIFIED = "USER_VERIFIED"
    const val VOLUNTEER_VERIFIED = "VOLUNTEER_VERIFIED"
    const val UNCERTAIN = "UNCERTAIN"
}

data class Medication(
    val id: String = "",
    val userId: String = "",
    val name: String,
    val strength: String = "",
    val form: String = "",
    val activeIngredients: List<String> = emptyList(),
    val printedDirections: String = "",
    val expiryDate: String = "",
    val storageInformation: String = "",
    val warningsVisibleOnPackage: List<String> = emptyList(),
    val rawVisibleText: String = "",
    val manufacturer: String = "",
    val batchNumber: String = "",
    val confidence: Double = 0.0,
    val verificationStatus: String = VerificationStatus.AI_EXTRACTED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = MedicationExtraction.isDatePast(expiryDate)

    fun toJson(): JSONObject {
        val json = JSONObject()
        if (id.isNotBlank() && !id.startsWith("local_")) json.put("id", id)
        json.put("user_id", userId)
        json.put("name", name)
        json.put("strength", strength)
        json.put("form", form)
        json.put("active_ingredients", JSONArray(activeIngredients))
        json.put("printed_directions", printedDirections)
        json.put("expiry_date", expiryDate)
        json.put("storage_information", storageInformation)
        json.put("warnings_visible_on_package", JSONArray(warningsVisibleOnPackage))
        json.put("raw_visible_text", rawVisibleText)
        json.put("manufacturer", manufacturer)
        json.put("batch_number", batchNumber)
        json.put("confidence", confidence)
        json.put("verification_status", verificationStatus)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): Medication {
            val ingredientsList = mutableListOf<String>()
            val rawIngredients = json.opt("active_ingredients")
            if (rawIngredients is JSONArray) {
                for (i in 0 until rawIngredients.length()) {
                    ingredientsList.add(rawIngredients.getString(i))
                }
            } else if (rawIngredients is String && rawIngredients.isNotBlank()) {
                try {
                    val arr = JSONArray(rawIngredients)
                    for (i in 0 until arr.length()) ingredientsList.add(arr.getString(i))
                } catch (_: Exception) {
                    ingredientsList.add(rawIngredients)
                }
            }

            val warningsList = mutableListOf<String>()
            val rawWarnings = json.opt("warnings_visible_on_package")
            if (rawWarnings is JSONArray) {
                for (i in 0 until rawWarnings.length()) {
                    warningsList.add(rawWarnings.getString(i))
                }
            } else if (rawWarnings is String && rawWarnings.isNotBlank()) {
                try {
                    val arr = JSONArray(rawWarnings)
                    for (i in 0 until arr.length()) warningsList.add(arr.getString(i))
                } catch (_: Exception) {
                    warningsList.add(rawWarnings)
                }
            }

            return Medication(
                id = json.optString("id", ""),
                userId = json.optString("user_id", ""),
                name = json.optString("name", "Unknown Medicine"),
                strength = json.optString("strength", ""),
                form = json.optString("form", ""),
                activeIngredients = ingredientsList,
                printedDirections = json.optString("printed_directions", ""),
                expiryDate = json.optString("expiry_date", ""),
                storageInformation = json.optString("storage_information", ""),
                warningsVisibleOnPackage = warningsList,
                rawVisibleText = json.optString("raw_visible_text", ""),
                manufacturer = json.optString("manufacturer", ""),
                batchNumber = json.optString("batch_number", ""),
                confidence = json.optDouble("confidence", 0.0),
                verificationStatus = json.optString("verification_status", VerificationStatus.AI_EXTRACTED),
                createdAt = parseTimestamp(json.optString("created_at")),
                updatedAt = parseTimestamp(json.optString("updated_at"))
            )
        }

        private fun parseTimestamp(raw: String): Long {
            if (raw.isBlank()) return System.currentTimeMillis()
            return try {
                java.time.Instant.parse(raw).toEpochMilli()
            } catch (_: Exception) {
                try {
                    raw.toLong()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
            }
        }
    }
}

data class MedicationReminder(
    val id: String = "",
    val medicationId: String? = null,
    val userId: String = "",
    val medicationName: String,
    val reminderTime: String, // "09:00" 24h format
    val frequency: String = "DAILY",
    val enabled: Boolean = true,
    val dosageLabel: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        if (id.isNotBlank() && !id.startsWith("local_")) json.put("id", id)
        if (!medicationId.isNullOrBlank()) json.put("medication_id", medicationId)
        json.put("user_id", userId)
        json.put("medication_name", medicationName)
        json.put("reminder_time", reminderTime)
        json.put("frequency", frequency)
        json.put("enabled", enabled)
        json.put("dosage_label", dosageLabel)
        return json
    }

    /** Returns hour of day (0-23) */
    val hour: Int
        get() = try { reminderTime.split(":")[0].toInt() } catch (_: Exception) { 9 }

    /** Returns minute of hour (0-59) */
    val minute: Int
        get() = try { reminderTime.split(":")[1].toInt() } catch (_: Exception) { 0 }

    /** Formats to 12-hour human readable format (e.g. "9:00 AM" or "9:00 PM") */
    val formattedTime12Hr: String
        get() {
            val h = hour
            val m = minute
            val period = if (h < 12) "AM" else "PM"
            val displayHour = when (h) {
                0 -> 12
                in 1..12 -> h
                else -> h - 12
            }
            return String.format(java.util.Locale.US, "%d:%02d %s", displayHour, m, period)
        }

    companion object {
        fun fromJson(json: JSONObject): MedicationReminder {
            return MedicationReminder(
                id = json.optString("id", ""),
                medicationId = if (json.has("medication_id") && !json.isNull("medication_id")) json.optString("medication_id") else null,
                userId = json.optString("user_id", ""),
                medicationName = json.optString("medication_name", "Medication"),
                reminderTime = json.optString("reminder_time", "09:00"),
                frequency = json.optString("frequency", "DAILY"),
                enabled = json.optBoolean("enabled", true),
                dosageLabel = json.optString("dosage_label", ""),
                createdAt = parseTimestamp(json.optString("created_at")),
                updatedAt = parseTimestamp(json.optString("updated_at"))
            )
        }

        private fun parseTimestamp(raw: String): Long {
            if (raw.isBlank()) return System.currentTimeMillis()
            return try {
                java.time.Instant.parse(raw).toEpochMilli()
            } catch (_: Exception) {
                try {
                    raw.toLong()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
            }
        }
    }
}

data class MedicationExtraction(
    val status: String, // "CLEAR", "UNCERTAIN", "NO_MEDICATION_TEXT"
    val medicineName: String?,
    val activeIngredients: List<String>,
    val strength: String?,
    val form: String?,
    val printedDirections: String?,
    val expiryDate: String?,
    val storageInformation: String?,
    val warningsVisibleOnPackage: List<String>,
    val manufacturer: String?,
    val batchNumber: String?,
    val rawVisibleText: String,
    val confidence: Double,
    val uncertaintyReason: String?,
    val fieldsNeedingVerification: List<String> = emptyList()
) {
    val isConfident: Boolean
        get() = status == "CLEAR" && !medicineName.isNullOrBlank() && confidence >= MEDICATION_CONFIDENCE_THRESHOLD

    val isExpired: Boolean
        get() {
            val dateStr = expiryDate ?: return false
            return isDatePast(dateStr)
        }

    companion object {
        fun isDatePast(dateStr: String): Boolean {
            val trimmed = dateStr.trim().lowercase()
            val now = java.time.LocalDate.now()
            
            // Try standard ISO yyyy-MM or yyyy-MM-dd
            val parts = trimmed.split(Regex("[/\\- ]"))
            if (parts.size >= 2) {
                try {
                    // Try parsing year and month
                    val monthMap = mapOf(
                        "jan" to 1, "january" to 1,
                        "feb" to 2, "february" to 2,
                        "mar" to 3, "march" to 3,
                        "apr" to 4, "april" to 4,
                        "may" to 5,
                        "jun" to 6, "june" to 6,
                        "jul" to 7, "july" to 7,
                        "aug" to 8, "august" to 8,
                        "sep" to 9, "september" to 9,
                        "oct" to 10, "october" to 10,
                        "nov" to 11, "november" to 11,
                        "dec" to 12, "december" to 12
                    )

                    var parsedYear: Int? = null
                    var parsedMonth: Int? = null

                    for (p in parts) {
                        val num = p.toIntOrNull()
                        if (num != null) {
                            if (num in 2000..2100) {
                                parsedYear = num
                            } else if (num in 24..99) {
                                parsedYear = 2000 + num
                            } else if (num in 1..12 && parsedMonth == null) {
                                parsedMonth = num
                            }
                        } else if (monthMap.containsKey(p)) {
                            parsedMonth = monthMap[p]
                        }
                    }

                    if (parsedYear != null && parsedMonth != null) {
                        val expiry = java.time.YearMonth.of(parsedYear, parsedMonth)
                        val current = java.time.YearMonth.from(now)
                        return expiry.isBefore(current)
                    }
                } catch (_: Exception) {}
            }
            return false
        }
    }
}
