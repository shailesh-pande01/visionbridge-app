package com.example.visionbridge.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray

/**
 * Robust local SQLite storage for medications and reminders.
 * Provides offline-first resilience so low-vision users can access their saved medications
 * and alarm reminders continue to fire without requiring an active internet connection.
 */
class MedicationLocalStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MEDICATIONS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_USER_ID TEXT NOT NULL,
                $COL_NAME TEXT NOT NULL,
                $COL_STRENGTH TEXT,
                $COL_FORM TEXT,
                $COL_ACTIVE_INGREDIENTS TEXT,
                $COL_PRINTED_DIRECTIONS TEXT,
                $COL_EXPIRY_DATE TEXT,
                $COL_STORAGE_INFO TEXT,
                $COL_WARNINGS TEXT,
                $COL_RAW_TEXT TEXT,
                $COL_MANUFACTURER TEXT,
                $COL_BATCH_NUMBER TEXT,
                $COL_CONFIDENCE REAL,
                $COL_VERIFICATION_STATUS TEXT,
                $COL_CREATED_AT INTEGER,
                $COL_UPDATED_AT INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_REMINDERS (
                $COL_REMINDER_ID TEXT PRIMARY KEY,
                $COL_MEDICATION_ID TEXT,
                $COL_REMINDER_USER_ID TEXT NOT NULL,
                $COL_MEDICATION_NAME TEXT NOT NULL,
                $COL_REMINDER_TIME TEXT NOT NULL,
                $COL_FREQUENCY TEXT NOT NULL,
                $COL_ENABLED INTEGER NOT NULL,
                $COL_DOSAGE_LABEL TEXT,
                $COL_REMINDER_CREATED_AT INTEGER,
                $COL_REMINDER_UPDATED_AT INTEGER
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_local_med_user ON $TABLE_MEDICATIONS($COL_USER_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_local_rem_user ON $TABLE_REMINDERS($COL_REMINDER_USER_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_local_rem_enabled ON $TABLE_REMINDERS($COL_ENABLED)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Safe incremental migrations if schema upgrades
    }

    // ── Medications Operations ────────────────────────────────────────

    fun insertOrUpdateMedication(medication: Medication): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ID, medication.id)
            put(COL_USER_ID, medication.userId)
            put(COL_NAME, medication.name)
            put(COL_STRENGTH, medication.strength)
            put(COL_FORM, medication.form)
            put(COL_ACTIVE_INGREDIENTS, JSONArray(medication.activeIngredients).toString())
            put(COL_PRINTED_DIRECTIONS, medication.printedDirections)
            put(COL_EXPIRY_DATE, medication.expiryDate)
            put(COL_STORAGE_INFO, medication.storageInformation)
            put(COL_WARNINGS, JSONArray(medication.warningsVisibleOnPackage).toString())
            put(COL_RAW_TEXT, medication.rawVisibleText)
            put(COL_MANUFACTURER, medication.manufacturer)
            put(COL_BATCH_NUMBER, medication.batchNumber)
            put(COL_CONFIDENCE, medication.confidence)
            put(COL_VERIFICATION_STATUS, medication.verificationStatus)
            put(COL_CREATED_AT, medication.createdAt)
            put(COL_UPDATED_AT, medication.updatedAt)
        }
        return db.insertWithOnConflict(TABLE_MEDICATIONS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getMedications(userId: String): List<Medication> {
        val list = mutableListOf<Medication>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_MEDICATIONS,
            null,
            "$COL_USER_ID = ?",
            arrayOf(userId),
            null,
            null,
            "$COL_CREATED_AT DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToMedication(it))
            }
        }
        return list
    }

    fun getMedicationById(id: String): Medication? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MEDICATIONS,
            null,
            "$COL_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToNext()) cursorToMedication(it) else null
        }
    }

    fun deleteMedication(id: String): Boolean {
        val db = writableDatabase
        // Cascading delete reminders for this medication
        db.delete(TABLE_REMINDERS, "$COL_MEDICATION_ID = ?", arrayOf(id))
        val rows = db.delete(TABLE_MEDICATIONS, "$COL_ID = ?", arrayOf(id))
        return rows > 0
    }

    // ── Reminders Operations ──────────────────────────────────────────

    fun insertOrUpdateReminder(reminder: MedicationReminder): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_REMINDER_ID, reminder.id)
            put(COL_MEDICATION_ID, reminder.medicationId)
            put(COL_REMINDER_USER_ID, reminder.userId)
            put(COL_MEDICATION_NAME, reminder.medicationName)
            put(COL_REMINDER_TIME, reminder.reminderTime)
            put(COL_FREQUENCY, reminder.frequency)
            put(COL_ENABLED, if (reminder.enabled) 1 else 0)
            put(COL_DOSAGE_LABEL, reminder.dosageLabel)
            put(COL_REMINDER_CREATED_AT, reminder.createdAt)
            put(COL_REMINDER_UPDATED_AT, reminder.updatedAt)
        }
        return db.insertWithOnConflict(TABLE_REMINDERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getReminders(userId: String): List<MedicationReminder> {
        val list = mutableListOf<MedicationReminder>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_REMINDERS,
            null,
            "$COL_REMINDER_USER_ID = ?",
            arrayOf(userId),
            null,
            null,
            "$COL_REMINDER_TIME ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToReminder(it))
            }
        }
        return list
    }

    fun getAllActiveReminders(): List<MedicationReminder> {
        val list = mutableListOf<MedicationReminder>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_REMINDERS,
            null,
            "$COL_ENABLED = 1",
            null,
            null,
            null,
            "$COL_REMINDER_TIME ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToReminder(it))
            }
        }
        return list
    }

    fun getReminderById(id: String): MedicationReminder? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_REMINDERS,
            null,
            "$COL_REMINDER_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToNext()) cursorToReminder(it) else null
        }
    }

    fun deleteReminder(id: String): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_REMINDERS, "$COL_REMINDER_ID = ?", arrayOf(id))
        return rows > 0
    }

    // ── Cursor Mappers ────────────────────────────────────────────────

    private fun cursorToMedication(c: Cursor): Medication {
        val activeIngredientsJson = c.getString(c.getColumnIndexOrThrow(COL_ACTIVE_INGREDIENTS)) ?: "[]"
        val activeList = mutableListOf<String>()
        try {
            val arr = JSONArray(activeIngredientsJson)
            for (i in 0 until arr.length()) activeList.add(arr.getString(i))
        } catch (_: Exception) {}

        val warningsJson = c.getString(c.getColumnIndexOrThrow(COL_WARNINGS)) ?: "[]"
        val warningsList = mutableListOf<String>()
        try {
            val arr = JSONArray(warningsJson)
            for (i in 0 until arr.length()) warningsList.add(arr.getString(i))
        } catch (_: Exception) {}

        return Medication(
            id = c.getString(c.getColumnIndexOrThrow(COL_ID)),
            userId = c.getString(c.getColumnIndexOrThrow(COL_USER_ID)),
            name = c.getString(c.getColumnIndexOrThrow(COL_NAME)),
            strength = c.getString(c.getColumnIndexOrThrow(COL_STRENGTH)) ?: "",
            form = c.getString(c.getColumnIndexOrThrow(COL_FORM)) ?: "",
            activeIngredients = activeList,
            printedDirections = c.getString(c.getColumnIndexOrThrow(COL_PRINTED_DIRECTIONS)) ?: "",
            expiryDate = c.getString(c.getColumnIndexOrThrow(COL_EXPIRY_DATE)) ?: "",
            storageInformation = c.getString(c.getColumnIndexOrThrow(COL_STORAGE_INFO)) ?: "",
            warningsVisibleOnPackage = warningsList,
            rawVisibleText = c.getString(c.getColumnIndexOrThrow(COL_RAW_TEXT)) ?: "",
            manufacturer = c.getString(c.getColumnIndexOrThrow(COL_MANUFACTURER)) ?: "",
            batchNumber = c.getString(c.getColumnIndexOrThrow(COL_BATCH_NUMBER)) ?: "",
            confidence = c.getDouble(c.getColumnIndexOrThrow(COL_CONFIDENCE)),
            verificationStatus = c.getString(c.getColumnIndexOrThrow(COL_VERIFICATION_STATUS)) ?: VerificationStatus.AI_EXTRACTED,
            createdAt = c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT)),
            updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_UPDATED_AT))
        )
    }

    private fun cursorToReminder(c: Cursor): MedicationReminder {
        return MedicationReminder(
            id = c.getString(c.getColumnIndexOrThrow(COL_REMINDER_ID)),
            medicationId = c.getString(c.getColumnIndexOrThrow(COL_MEDICATION_ID)),
            userId = c.getString(c.getColumnIndexOrThrow(COL_REMINDER_USER_ID)),
            medicationName = c.getString(c.getColumnIndexOrThrow(COL_MEDICATION_NAME)),
            reminderTime = c.getString(c.getColumnIndexOrThrow(COL_REMINDER_TIME)),
            frequency = c.getString(c.getColumnIndexOrThrow(COL_FREQUENCY)) ?: "DAILY",
            enabled = c.getInt(c.getColumnIndexOrThrow(COL_ENABLED)) == 1,
            dosageLabel = c.getString(c.getColumnIndexOrThrow(COL_DOSAGE_LABEL)) ?: "",
            createdAt = c.getLong(c.getColumnIndexOrThrow(COL_REMINDER_CREATED_AT)),
            updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_REMINDER_UPDATED_AT))
        )
    }

    companion object {
        private const val DATABASE_NAME = "visionbridge_medication.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_MEDICATIONS = "medications"
        private const val COL_ID = "id"
        private const val COL_USER_ID = "user_id"
        private const val COL_NAME = "name"
        private const val COL_STRENGTH = "strength"
        private const val COL_FORM = "form"
        private const val COL_ACTIVE_INGREDIENTS = "active_ingredients"
        private const val COL_PRINTED_DIRECTIONS = "printed_directions"
        private const val COL_EXPIRY_DATE = "expiry_date"
        private const val COL_STORAGE_INFO = "storage_information"
        private const val COL_WARNINGS = "warnings_visible_on_package"
        private const val COL_RAW_TEXT = "raw_visible_text"
        private const val COL_MANUFACTURER = "manufacturer"
        private const val COL_BATCH_NUMBER = "batch_number"
        private const val COL_CONFIDENCE = "confidence"
        private const val COL_VERIFICATION_STATUS = "verification_status"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_UPDATED_AT = "updated_at"

        private const val TABLE_REMINDERS = "medication_reminders"
        private const val COL_REMINDER_ID = "id"
        private const val COL_MEDICATION_ID = "medication_id"
        private const val COL_REMINDER_USER_ID = "user_id"
        private const val COL_MEDICATION_NAME = "medication_name"
        private const val COL_REMINDER_TIME = "reminder_time"
        private const val COL_FREQUENCY = "frequency"
        private const val COL_ENABLED = "enabled"
        private const val COL_DOSAGE_LABEL = "dosage_label"
        private const val COL_REMINDER_CREATED_AT = "created_at"
        private const val COL_REMINDER_UPDATED_AT = "updated_at"

        @Volatile
        private var instance: MedicationLocalStore? = null

        fun getInstance(context: Context): MedicationLocalStore {
            return instance ?: synchronized(this) {
                instance ?: MedicationLocalStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
