package com.example.visionbridge.ui.screens.medication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.ai.ConfidenceEvaluator
import com.example.visionbridge.ai.ConfidenceLevel
import com.example.visionbridge.ai.FallbackContext
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.data.MEDICATION_CONFIDENCE_THRESHOLD
import com.example.visionbridge.data.Medication
import com.example.visionbridge.data.MedicationExtraction
import com.example.visionbridge.data.MedicationReminder
import com.example.visionbridge.data.MedicationRepository
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.data.VerificationStatus
import com.example.visionbridge.reminder.MedicationReminderReceiver
import com.example.visionbridge.reminder.MedicationReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class MedSafeSubScreen {
    HUB,
    SCAN,
    MY_MEDICINES,
    TODAYS_REMINDERS
}

sealed interface MedicationScanUiState {
    data object Ready : MedicationScanUiState
    data object Processing : MedicationScanUiState

    data class ConfidentResult(
        val id: Long,
        val extraction: MedicationExtraction,
        val speechText: String
    ) : MedicationScanUiState

    data class LowConfidenceResult(
        val id: Long,
        val extraction: MedicationExtraction,
        val speechText: String,
        val fallbackContext: FallbackContext
    ) : MedicationScanUiState

    data class NoMedicationText(
        val id: Long,
        val message: String
    ) : MedicationScanUiState

    data class Error(
        val id: Long,
        val userMessage: String,
        val technicalDetail: String?
    ) : MedicationScanUiState
}

class MedicationSafetyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MedicationRepository(application)
    private val reminderScheduler = MedicationReminderScheduler(application)
    private val sessionManager = SessionManager.getInstance(application)

    private val _currentSubScreen = MutableStateFlow(MedSafeSubScreen.HUB)
    val currentSubScreen: StateFlow<MedSafeSubScreen> = _currentSubScreen.asStateFlow()

    private val _scanUiState = MutableStateFlow<MedicationScanUiState>(MedicationScanUiState.Ready)
    val scanUiState: StateFlow<MedicationScanUiState> = _scanUiState.asStateFlow()

    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications: StateFlow<List<Medication>> = _medications.asStateFlow()

    private val _reminders = MutableStateFlow<List<MedicationReminder>>(emptyList())
    val reminders: StateFlow<List<MedicationReminder>> = _reminders.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val outcomeIds = AtomicLong(0)

    init {
        loadData()
    }

    fun navigateTo(subScreen: MedSafeSubScreen) {
        _currentSubScreen.value = subScreen
        if (subScreen == MedSafeSubScreen.MY_MEDICINES || subScreen == MedSafeSubScreen.TODAYS_REMINDERS) {
            loadData()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _medications.value = repository.getMedications()
            _reminders.value = repository.getReminders()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ── Scanning Flow ─────────────────────────────────────────────────

    fun onCaptureStarted() {
        _scanUiState.value = MedicationScanUiState.Processing
    }

    fun onImageCaptured(imageBytes: ByteArray, rotationDegrees: Int, language: String = "en") {
        _scanUiState.value = MedicationScanUiState.Processing
        android.util.Log.d("VB-MedSafe", "MEDSAFE: onImageCaptured rawBytes=${imageBytes.size}, rotation=$rotationDegrees, lang=$language")

        viewModelScope.launch {
            when (val result = repository.extractMedication(imageBytes, rotationDegrees, language)) {
                is ApiResult.Success -> {
                    val extraction = result.value
                    val id = outcomeIds.incrementAndGet()
                    android.util.Log.d("VB-MedSafe", "MEDSAFE: extraction result status=${extraction.status}, isConfident=${extraction.isConfident}, conf=${extraction.confidence}")

                    if (extraction.status == "NO_MEDICATION_TEXT") {
                        _scanUiState.value = MedicationScanUiState.NoMedicationText(
                            id = id,
                            message = "No medicine packaging or label was detected in this photo. Please point the camera directly at a medicine strip, box, or bottle."
                        )
                    } else if (extraction.isConfident) {
                        val speech = buildSpeechSummary(extraction)
                        _scanUiState.value = MedicationScanUiState.ConfidentResult(
                            id = id,
                            extraction = extraction,
                            speechText = speech
                        )
                    } else {
                        // LOW CONFIDENCE or UNCERTAIN: Safety Principle enforces refusal to guess
                        val reason = extraction.uncertaintyReason ?: "Low readability on medicine packaging"
                        val fallbackContext = FallbackContext(
                            sourceFeature = "Medication Safety",
                            confidence = extraction.confidence,
                            level = ConfidenceLevel.LOW,
                            reason = reason,
                            originalPrompt = extraction.rawVisibleText.take(100)
                        )
                        val speech = "I couldn't confidently read this medicine packaging. I don't want to guess. You can try another photo or connect to a human helper."
                        _scanUiState.value = MedicationScanUiState.LowConfidenceResult(
                            id = id,
                            extraction = extraction,
                            speechText = speech,
                            fallbackContext = fallbackContext
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _scanUiState.value = MedicationScanUiState.Error(
                        id = outcomeIds.incrementAndGet(),
                        userMessage = result.error.userMessage,
                        technicalDetail = result.error.technicalDetail
                    )
                }
            }
        }
    }

    fun onCaptureFailed(userMessage: String, technicalDetail: String?) {
        _scanUiState.value = MedicationScanUiState.Error(
            id = outcomeIds.incrementAndGet(),
            userMessage = userMessage,
            technicalDetail = technicalDetail
        )
    }

    fun resetScan() {
        _scanUiState.value = MedicationScanUiState.Ready
    }

    // ── Medication CRUD ───────────────────────────────────────────────

    fun saveMedication(
        name: String,
        strength: String,
        form: String,
        activeIngredients: List<String>,
        printedDirections: String,
        expiryDate: String,
        storageInformation: String,
        warnings: List<String>,
        manufacturer: String,
        batchNumber: String,
        confidence: Double,
        onSaved: (Medication) -> Unit
    ) {
        viewModelScope.launch {
            val med = Medication(
                name = name.trim(),
                strength = strength.trim(),
                form = form.trim(),
                activeIngredients = activeIngredients,
                printedDirections = printedDirections.trim(),
                expiryDate = expiryDate.trim(),
                storageInformation = storageInformation.trim(),
                warningsVisibleOnPackage = warnings,
                manufacturer = manufacturer.trim(),
                batchNumber = batchNumber.trim(),
                confidence = confidence,
                verificationStatus = VerificationStatus.USER_VERIFIED
            )
            val saved = repository.saveMedication(med)
            loadData()
            onSaved(saved)
        }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch {
            repository.saveMedication(medication)
            loadData()
        }
    }

    fun deleteMedication(medicationId: String) {
        viewModelScope.launch {
            repository.deleteMedication(medicationId)
            loadData()
        }
    }

    // ── Reminder CRUD ─────────────────────────────────────────────────

    fun createReminder(
        medicationName: String,
        reminderTime: String,
        frequency: String = "DAILY",
        dosageLabel: String = "",
        medicationId: String? = null,
        onCreated: (MedicationReminder) -> Unit = {}
    ) {
        viewModelScope.launch {
            val reminder = MedicationReminder(
                medicationId = medicationId,
                medicationName = medicationName.trim(),
                reminderTime = reminderTime.trim(),
                frequency = frequency,
                enabled = true,
                dosageLabel = dosageLabel.trim()
            )
            val saved = repository.saveReminder(reminder)
            loadData()
            onCreated(saved)
        }
    }

    fun toggleReminder(reminderId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleReminder(reminderId, enabled)
            loadData()
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            repository.deleteReminder(reminderId)
            loadData()
        }
    }

    /**
     * Development and testing utility: triggers immediate reminder broadcast
     * so user/judges can verify notification sound, vibration, and lockscreen privacy.
     */
    fun testTriggerReminder(reminder: MedicationReminder) {
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, MedicationReminderReceiver::class.java).apply {
            action = MedicationReminderReceiver.ACTION_MEDICATION_REMINDER
            putExtra(MedicationReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(MedicationReminderReceiver.EXTRA_MEDICATION_NAME, reminder.medicationName)
            putExtra(MedicationReminderReceiver.EXTRA_DOSAGE_LABEL, reminder.dosageLabel)
            putExtra(MedicationReminderReceiver.EXTRA_REMINDER_TIME, reminder.reminderTime)
        }
        context.sendBroadcast(intent)
    }

    // ── Speech Generation ─────────────────────────────────────────────

    fun buildSpeechSummary(extraction: MedicationExtraction): String {
        val parts = mutableListOf<String>()

        extraction.medicineName?.let {
            parts.add("Medicine: $it.")
        }
        if (!extraction.strength.isNullOrBlank()) {
            parts.add("Strength: ${extraction.strength}.")
        }
        if (!extraction.form.isNullOrBlank()) {
            parts.add("Form: ${extraction.form}.")
        }
        if (extraction.activeIngredients.isNotEmpty()) {
            parts.add("Active ingredients: ${extraction.activeIngredients.joinToString(", ")}.")
        }
        if (!extraction.printedDirections.isNullOrBlank()) {
            // Safety Principle (Section 13): Report verbatim, do NOT give medical advice
            parts.add("The packaging says: ${extraction.printedDirections}.")
        }
        if (!extraction.expiryDate.isNullOrBlank()) {
            if (extraction.isExpired) {
                parts.add("Warning: This medicine appears to be past its printed expiry date of ${extraction.expiryDate}. Please consult a pharmacist or doctor.")
            } else {
                parts.add("Expiry date: ${extraction.expiryDate}.")
            }
        }
        if (!extraction.storageInformation.isNullOrBlank()) {
            parts.add("Storage: ${extraction.storageInformation}.")
        }
        if (extraction.warningsVisibleOnPackage.isNotEmpty()) {
            parts.add("Printed warnings: ${extraction.warningsVisibleOnPackage.joinToString(". ")}.")
        }

        return parts.joinToString(" ")
    }

    fun buildTodayRemindersSpeech(): String {
        val active = _reminders.value.filter { it.enabled }
        if (active.isEmpty()) {
            return "You have no medication reminders scheduled for today."
        }
        val count = active.size
        val countWord = if (count == 1) "one medication reminder" else "$count medication reminders"
        val scheduleList = active.joinToString(", ") { "${it.medicationName} at ${it.formattedTime12Hr}" }
        return "You have $countWord scheduled for today: $scheduleList."
    }
}
