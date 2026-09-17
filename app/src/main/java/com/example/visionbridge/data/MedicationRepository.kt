package com.example.visionbridge.data

import android.content.Context
import android.util.Log
import com.example.visionbridge.api.ApiError
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.MedicationApi
import com.example.visionbridge.reminder.MedicationReminderScheduler
import com.example.visionbridge.utils.ImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MedicationRepository(private val context: Context) {

    private val api = MedicationApi(context)
    private val localStore = MedicationLocalStore.getInstance(context)
    private val reminderScheduler = MedicationReminderScheduler(context)
    private val sessionManager = SessionManager.getInstance(context)

    // ── Image Processing & AI Extraction ──────────────────────────────

    suspend fun extractMedication(
        imageBytes: ByteArray,
        rotationDegrees: Int,
        language: String = "en"
    ): ApiResult<MedicationExtraction> {
        // Client-side image sanity check (Section 7)
        if (imageBytes.isEmpty()) {
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.IMAGE_PROCESSING,
                    userMessage = "No photo was captured. Please point the camera at the medicine and try again.",
                    technicalDetail = "Empty byte array handed to extractMedication"
                )
            )
        }

        if (imageBytes.size < 1000) { // < 1KB is almost certainly corrupted or empty
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.IMAGE_PROCESSING,
                    userMessage = "I can't read this image clearly. Please move closer, hold steady, and try again.",
                    technicalDetail = "Capture buffer too small (${imageBytes.size} bytes)"
                )
            )
        }

        val optimized = try {
            withContext(Dispatchers.Default) {
                ImageHelper.optimizeAndEncode(imageBytes, rotationDegrees)
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError optimizing medicine capture", e)
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.IMAGE_PROCESSING,
                    userMessage = "The photo was too large for device memory. Please capture again.",
                    technicalDetail = "OutOfMemoryError: ${imageBytes.size} bytes"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing capture", e)
            return ApiResult.Failure(
                ApiError(
                    kind = ApiError.Kind.IMAGE_PROCESSING,
                    userMessage = "Could not prepare photo for reading. Please try again.",
                    technicalDetail = "${e.javaClass.simpleName}: ${e.message}"
                )
            )
        }

        Log.i(TAG, "Optimized image: ${optimized.width}x${optimized.height}, ~${optimized.sizeKb}KB, lang=$language")

        return withContext(Dispatchers.IO) {
            api.extractMedication(optimized.base64, optimized.mimeType, language)
        }
    }

    // ── Medication CRUD Operations ────────────────────────────────────

    suspend fun getMedications(): List<Medication> = withContext(Dispatchers.IO) {
        val currentUserId = sessionManager.currentUser.value?.id ?: "local_user"
        // 1. Load local cache first for instantaneous UI
        val localList = localStore.getMedications(currentUserId)

        // 2. If authenticated, try fetching fresh remote data and sync
        if (currentUserId != "local_user" && sessionManager.isLoggedIn) {
            val remoteRes = api.getMedications(currentUserId)
            if (remoteRes is ApiResult.Success) {
                for (remoteMed in remoteRes.value) {
                    localStore.insertOrUpdateMedication(remoteMed)
                }
                return@withContext localStore.getMedications(currentUserId)
            }
        }

        return@withContext localList
    }

    suspend fun saveMedication(medication: Medication): Medication = withContext(Dispatchers.IO) {
        val currentUserId = sessionManager.currentUser.value?.id ?: "local_user"
        val medToSave = medication.copy(
            id = if (medication.id.isBlank()) "local_${System.currentTimeMillis()}" else medication.id,
            userId = currentUserId,
            updatedAt = System.currentTimeMillis()
        )

        // Save locally first (offline guaranteed)
        localStore.insertOrUpdateMedication(medToSave)

        // Sync to Supabase if authenticated
        if (currentUserId != "local_user" && sessionManager.isLoggedIn) {
            val remoteRes = api.saveMedication(medToSave)
            if (remoteRes is ApiResult.Success) {
                val savedRemote = remoteRes.value
                localStore.insertOrUpdateMedication(savedRemote)
                return@withContext savedRemote
            }
        }

        return@withContext medToSave
    }

    suspend fun deleteMedication(medicationId: String): Boolean = withContext(Dispatchers.IO) {
        localStore.deleteMedication(medicationId)

        val currentUserId = sessionManager.currentUser.value?.id ?: "local_user"
        if (currentUserId != "local_user" && sessionManager.isLoggedIn && !medicationId.startsWith("local_")) {
            api.deleteMedication(medicationId)
        }
        return@withContext true
    }

    // ── Medication Reminder Operations ────────────────────────────────

    suspend fun getReminders(): List<MedicationReminder> = withContext(Dispatchers.IO) {
        val currentUserId = sessionManager.currentUser.value?.id ?: "local_user"
        val localReminders = localStore.getReminders(currentUserId)

        if (currentUserId != "local_user" && sessionManager.isLoggedIn) {
            val remoteRes = api.getReminders(currentUserId)
            if (remoteRes is ApiResult.Success) {
                for (r in remoteRes.value) {
                    localStore.insertOrUpdateReminder(r)
                }
                return@withContext localStore.getReminders(currentUserId)
            }
        }

        return@withContext localReminders
    }

    suspend fun saveReminder(reminder: MedicationReminder): MedicationReminder = withContext(Dispatchers.IO) {
        val currentUserId = sessionManager.currentUser.value?.id ?: "local_user"
        val reminderToSave = reminder.copy(
            id = if (reminder.id.isBlank()) "local_${System.currentTimeMillis()}" else reminder.id,
            userId = currentUserId,
            updatedAt = System.currentTimeMillis()
        )

        localStore.insertOrUpdateReminder(reminderToSave)

        // Schedule or cancel alarm via AlarmManager
        if (reminderToSave.enabled) {
            reminderScheduler.scheduleReminder(reminderToSave)
        } else {
            reminderScheduler.cancelReminder(reminderToSave.id)
        }

        // Sync to cloud if online
        if (currentUserId != "local_user" && sessionManager.isLoggedIn) {
            val remoteRes = api.saveReminder(reminderToSave)
            if (remoteRes is ApiResult.Success) {
                val saved = remoteRes.value
                localStore.insertOrUpdateReminder(saved)
                return@withContext saved
            }
        }

        return@withContext reminderToSave
    }

    suspend fun toggleReminder(reminderId: String, enabled: Boolean): MedicationReminder? = withContext(Dispatchers.IO) {
        val reminder = localStore.getReminderById(reminderId) ?: return@withContext null
        val updated = reminder.copy(enabled = enabled, updatedAt = System.currentTimeMillis())
        saveReminder(updated)
    }

    suspend fun deleteReminder(reminderId: String): Boolean = withContext(Dispatchers.IO) {
        reminderScheduler.cancelReminder(reminderId)
        localStore.deleteReminder(reminderId)

        val currentUserId = sessionManager.currentUser.value?.id ?: "local_user"
        if (currentUserId != "local_user" && sessionManager.isLoggedIn && !reminderId.startsWith("local_")) {
            api.deleteReminder(reminderId)
        }
        return@withContext true
    }

    companion object {
        private const val TAG = "VB-MedRepository"
    }
}
