package com.loadalert.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.loadalert.R
import com.loadalert.data.model.CycleState
import com.loadalert.location.LocationPermissions
import com.loadalert.ui.MainActivity

/**
 * Builds the reminder notifications.
 *
 * Escalation is expressed as two channels rather than one, because a channel's importance,
 * sound and vibration are frozen once Android has created it. The urgent channel is only used
 * after the user has ignored the configured number of checks.
 */
class NotificationHelper(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return

        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            "Laundry reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Tells you when your wash has finished."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }

        val urgent = NotificationChannel(
            CHANNEL_URGENT,
            "Laundry reminders (urgent)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Louder reminders once earlier ones have gone unanswered."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 600, 200, 900)
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

        val status = NotificationChannel(
            CHANNEL_STATUS,
            "Cycle status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Quiet confirmations, such as a cycle timing out." }

        system.createNotificationChannels(listOf(reminder, urgent, status))
    }

    /**
     * Posts the reminder. [urgent] selects the louder channel. Returns false when we were not
     * allowed to post, so callers do not count a reminder the user never had a chance to see.
     */
    fun showLaundryReady(cycle: CycleState, urgent: Boolean): Boolean {
        if (!LocationPermissions.hasNotificationPermission(context)) return false

        val body = if (urgent) {
            "Your load is still sitting in the machine. Please go grab it."
        } else {
            "Your wash finished. Go grab it before someone else moves it."
        }

        val notification = NotificationCompat.Builder(
            context,
            if (urgent) CHANNEL_URGENT else CHANNEL_REMINDER,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Laundry's done - go grab it")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (urgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER,
            )
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(openAppIntent())
            .addAction(
                R.drawable.ic_notification,
                "I got it",
                actionIntent(CycleActionReceiver.ACTION_MARK_COLLECTED, cycle.cycleId, REQUEST_GOT_IT),
            )
            .addAction(
                R.drawable.ic_notification,
                "Snooze " + SNOOZE_MINUTES + "m",
                actionIntent(CycleActionReceiver.ACTION_SNOOZE, cycle.cycleId, REQUEST_SNOOZE),
            )
            .build()

        return try {
            manager.notify(NOTIFICATION_ID_REMINDER, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun showCycleEnded(title: String, body: String) {
        if (!LocationPermissions.hasNotificationPermission(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        try {
            manager.notify(NOTIFICATION_ID_STATUS, notification)
        } catch (e: SecurityException) {
            // The user has revoked notification access; nothing further to do.
        }
    }

    fun cancelReminder() {
        manager.cancel(NOTIFICATION_ID_REMINDER)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(action: String, cycleId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, CycleActionReceiver::class.java)
                .setAction(action)
                .putExtra(CycleActionReceiver.EXTRA_CYCLE_ID, cycleId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_REMINDER = "laundry_reminder"
        const val CHANNEL_URGENT = "laundry_reminder_urgent"
        const val CHANNEL_STATUS = "laundry_status"

        const val NOTIFICATION_ID_REMINDER = 2001
        const val NOTIFICATION_ID_STATUS = 2002

        const val SNOOZE_MINUTES = 5

        private const val REQUEST_OPEN = 3001
        private const val REQUEST_GOT_IT = 3002
        private const val REQUEST_SNOOZE = 3003
    }
}
