package com.example.visionbridge.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.visionbridge.MainActivity
import com.example.visionbridge.R
import com.example.visionbridge.data.MedicationLocalStore

/**
 * BroadcastReceiver triggered when a medication reminder alarm fires.
 * Delivers an accessible, high-priority notification with sound and vibration,
 * respecting locked-screen notification privacy guidelines (Section 27),
 * and automatically re-arms the subsequent day's alarm for daily frequency.
 */
class MedicationReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "onReceive action: $action")

        when (action) {
            ACTION_MEDICATION_REMINDER -> {
                val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
                val medName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "Medication"
                val dosage = intent.getStringExtra(EXTRA_DOSAGE_LABEL) ?: ""
                val timeStr = intent.getStringExtra(EXTRA_REMINDER_TIME) ?: ""

                showMedicationNotification(context, reminderId, medName, dosage)

                // Re-arm next day's alarm if daily
                val localStore = MedicationLocalStore.getInstance(context)
                val reminder = localStore.getReminderById(reminderId)
                if (reminder != null && reminder.enabled && reminder.frequency == "DAILY") {
                    val scheduler = MedicationReminderScheduler(context)
                    scheduler.scheduleReminder(reminder)
                    Log.d(TAG, "Re-armed daily reminder for '${reminder.medicationName}'")
                }
            }
            ACTION_DISMISS_NOTIFICATION -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                if (notificationId != 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(notificationId)
                }
            }
        }
    }

    private fun showMedicationNotification(
        context: Context,
        reminderId: String,
        medicationName: String,
        dosage: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        createNotificationChannel(context, nm)

        val notificationId = Math.abs(reminderId.hashCode()) % 1000000 + 20000

        // Tap notification to open Medication Safety in VisionBridge
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "medication")
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action button
        val dismissIntent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_DISMISS_NOTIFICATION
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (dosage.isNotBlank()) {
            "Scheduled reminder: $medicationName ($dosage)"
        } else {
            "Scheduled reminder: $medicationName"
        }

        // Build notification with high contrast and privacy
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("Medication Reminder")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\n\nTap to open VisionBridge MedSafe."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE) // Protects medical privacy on lock screen
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Done / Taken", dismissPendingIntent)
            .addAction(R.drawable.app_logo, "Open MedSafe", openAppPendingIntent)

        nm.notify(notificationId, builder.build())
        Log.i(TAG, "Notification posted for '$medicationName' (id: $notificationId)")
    }

    private fun createNotificationChannel(context: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Medication Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Accessible daily notifications for scheduled medications"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                    setSound(soundUri, audioAttributes)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                }
                nm.createNotificationChannel(channel)
                Log.d(TAG, "Created notification channel $CHANNEL_ID")
            }
        }
    }

    companion object {
        private const val TAG = "VB-MedReminderReceiver"
        const val CHANNEL_ID = "visionbridge_medication_reminders"

        const val ACTION_MEDICATION_REMINDER = "com.example.visionbridge.ACTION_MEDICATION_REMINDER"
        const val ACTION_DISMISS_NOTIFICATION = "com.example.visionbridge.ACTION_DISMISS_MEDICATION_NOTIFICATION"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_MEDICATION_NAME = "extra_medication_name"
        const val EXTRA_DOSAGE_LABEL = "extra_dosage_label"
        const val EXTRA_REMINDER_TIME = "extra_reminder_time"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
