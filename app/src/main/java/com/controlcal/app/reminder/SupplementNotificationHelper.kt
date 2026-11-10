package com.controlcal.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.controlcal.app.MainActivity
import com.controlcal.app.R
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.supplement.Supplement
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplementNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val CHANNEL_ID = "supplements_channel"
        private const val REQUEST_CODE_ACTIVITY = 1000
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.supplement_notification_channel_name)
            val descriptionText = context.getString(R.string.supplement_notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showDoseNotification(
        supplement: Supplement,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        isSecondWarning: Boolean = false,
    ) {
        if (!hasNotificationPermission()) return

        val notificationId = (supplement.id * 1000 + scheduledTime.hour * 60 + scheduledTime.minute).toInt()
        val formattedDose = "${NumberFormatHelper.formatDecimal(supplement.doseAmount)} ${supplement.doseUnit}"

        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_ACTIVITY,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Public version (shown when device is locked): hides supplement name and dose
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.supplement_notification_public_text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        // Action: "Tomado"
        val markTakenIntent = SupplementActionReceiver.createMarkTakenIntent(
            context = context,
            supplementId = supplement.id,
            scheduledDate = scheduledDate,
            scheduledTime = scheduledTime,
            notificationId = notificationId,
        )
        val markTakenPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            markTakenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Action: "Posponer 15 min"
        val snoozeIntent = SupplementActionReceiver.createSnoozeIntent(
            context = context,
            supplementId = supplement.id,
            scheduledDate = scheduledDate,
            scheduledTime = scheduledTime,
            notificationId = notificationId,
        )
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (isSecondWarning) {
            context.getString(R.string.supplement_notification_second_warning_title, supplement.name)
        } else {
            supplement.name
        }

        val privateNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.supplement_notification_text, formattedDose, scheduledTime.toString()))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.supplement_action_taken),
                markTakenPendingIntent,
            )
            .addAction(
                R.drawable.ic_snooze,
                context.getString(R.string.supplement_action_snooze_15),
                snoozePendingIntent,
            )
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, privateNotification)
    }

    fun cancelNotification(notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}
