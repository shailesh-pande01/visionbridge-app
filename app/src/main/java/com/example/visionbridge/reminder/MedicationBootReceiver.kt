package com.example.visionbridge.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver to reschedule medication reminders when the device reboots
 * or when the user changes time or timezone.
 */
class MedicationBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "onReceive action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == "android.intent.action.MY_PACKAGE_REPLACED"
        ) {
            val scheduler = MedicationReminderScheduler(context)
            scheduler.rescheduleAllActiveReminders()
        }
    }

    companion object {
        private const val TAG = "VB-MedBootReceiver"
    }
}
