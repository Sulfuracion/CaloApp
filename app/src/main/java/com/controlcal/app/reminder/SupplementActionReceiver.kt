package com.controlcal.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.controlcal.core.repo.SupplementRepository
import com.controlcal.core.supplement.IntakeStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class SupplementActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var supplementRepository: SupplementRepository

    @Inject
    lateinit var notificationHelper: SupplementNotificationHelper

    @Inject
    lateinit var reminderScheduler: SupplementReminderScheduler

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val supplementId = intent.getLongExtra(EXTRA_SUPPLEMENT_ID, -1L)
        val scheduledDateStr = intent.getStringExtra(EXTRA_SCHEDULED_DATE) ?: return
        val scheduledTimeStr = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (supplementId == -1L) return

        val scheduledDate = LocalDate.parse(scheduledDateStr)
        val scheduledTime = LocalTime.parse(scheduledTimeStr)

        if (notificationId != -1) {
            notificationHelper.cancelNotification(notificationId)
        }

        when (action) {
            ACTION_MARK_TAKEN -> {
                // Cancel second warning alarm
                reminderScheduler.cancelSecondWarning(supplementId)

                val pendingResult = goAsync()
                CoroutineScope(ioDispatcher).launch {
                    try {
                        supplementRepository.recordIntake(
                            supplementId = supplementId,
                            scheduledDate = scheduledDate,
                            scheduledTime = scheduledTime,
                            status = IntakeStatus.TAKEN,
                        )
                    } finally {
                        pendingResult?.finish()
                    }
                }
            }

            ACTION_SNOOZE_15_MIN -> {
                reminderScheduler.scheduleSnooze(
                    supplementId = supplementId,
                    scheduledDate = scheduledDate,
                    scheduledTime = scheduledTime,
                )
            }
        }
    }

    companion object {
        const val ACTION_MARK_TAKEN = "com.controlcal.app.ACTION_MARK_TAKEN"
        const val ACTION_SNOOZE_15_MIN = "com.controlcal.app.ACTION_SNOOZE_15_MIN"

        const val EXTRA_SUPPLEMENT_ID = "extra_supplement_id"
        const val EXTRA_SCHEDULED_DATE = "extra_scheduled_date"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        fun createMarkTakenIntent(
            context: Context,
            supplementId: Long,
            scheduledDate: LocalDate,
            scheduledTime: LocalTime,
            notificationId: Int,
        ): Intent {
            return Intent(context, SupplementActionReceiver::class.java).apply {
                action = ACTION_MARK_TAKEN
                putExtra(EXTRA_SUPPLEMENT_ID, supplementId)
                putExtra(EXTRA_SCHEDULED_DATE, scheduledDate.toString())
                putExtra(EXTRA_SCHEDULED_TIME, scheduledTime.toString())
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
        }

        fun createSnoozeIntent(
            context: Context,
            supplementId: Long,
            scheduledDate: LocalDate,
            scheduledTime: LocalTime,
            notificationId: Int,
        ): Intent {
            return Intent(context, SupplementActionReceiver::class.java).apply {
                action = ACTION_SNOOZE_15_MIN
                putExtra(EXTRA_SUPPLEMENT_ID, supplementId)
                putExtra(EXTRA_SCHEDULED_DATE, scheduledDate.toString())
                putExtra(EXTRA_SCHEDULED_TIME, scheduledTime.toString())
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
        }
    }
}
