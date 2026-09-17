package com.example.visionbridge.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.visionbridge.data.MedicationLocalStore
import com.example.visionbridge.data.MedicationReminder
import java.util.Calendar

/**
 * Manages alarm scheduling for medication reminders using Android AlarmManager.
 * Fully compliant with Android 12+ (API 31) through Android 16 (API 36).
 * Handles exact alarms when permitted, with graceful fallback to inexact idle alarms,
 * reboot re-arming, and daily recurrence calculation.
 */
class MedicationReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun scheduleReminder(reminder: MedicationReminder): Boolean {
        if (!reminder.enabled) {
            cancelReminder(reminder.id)
            return true
        }

        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager service unavailable")
            return false
        }

        val triggerTimeMs = calculateNextTriggerTime(reminder.hour, reminder.minute)
        val pendingIntent = createPendingIntent(reminder)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    Log.i(TAG, "Scheduled exact alarm for '${reminder.medicationName}' at $triggerTimeMs")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    Log.i(TAG, "Exact alarms not permitted; scheduled inexact idle alarm for '${reminder.medicationName}' at $triggerTimeMs")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
                Log.i(TAG, "Scheduled alarm for '${reminder.medicationName}' at $triggerTimeMs")
            }
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException scheduling alarm; falling back to setAndAllowWhileIdle", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to schedule alarm fallback", e2)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
            false
        }
    }

    fun cancelReminder(reminderId: String) {
        if (alarmManager == null) return
        try {
            val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
                action = MedicationReminderReceiver.ACTION_MEDICATION_REMINDER
            }
            val requestCode = getRequestCode(reminderId)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for reminder ID: $reminderId (requestCode: $requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling alarm $reminderId", e)
        }
    }

    fun rescheduleAllActiveReminders() {
        val localStore = MedicationLocalStore.getInstance(context)
        val activeReminders = localStore.getAllActiveReminders()
        Log.i(TAG, "Rescheduling ${activeReminders.size} active medication reminders...")
        for (reminder in activeReminders) {
            scheduleReminder(reminder)
        }
    }

    private fun createPendingIntent(reminder: MedicationReminder): PendingIntent {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = MedicationReminderReceiver.ACTION_MEDICATION_REMINDER
            putExtra(MedicationReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(MedicationReminderReceiver.EXTRA_MEDICATION_NAME, reminder.medicationName)
            putExtra(MedicationReminderReceiver.EXTRA_DOSAGE_LABEL, reminder.dosageLabel)
            putExtra(MedicationReminderReceiver.EXTRA_REMINDER_TIME, reminder.reminderTime)
        }

        val requestCode = getRequestCode(reminder.id)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getRequestCode(reminderId: String): Int {
        return Math.abs(reminderId.hashCode()) % 1000000 + 10000
    }

    companion object {
        private const val TAG = "VB-MedReminderScheduler"

        /**
         * Computes the next epoch millisecond timestamp for an hour and minute.
         * If the scheduled time has already passed today, advances to tomorrow.
         */
        fun calculateNextTriggerTime(hour: Int, minute: Int, nowMs: Long = System.currentTimeMillis()): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (calendar.timeInMillis <= nowMs) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            return calendar.timeInMillis
        }
    }
}
