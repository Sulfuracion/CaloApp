package com.controlcal.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.controlcal.app.data.db.dao.SupplementDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class SupplementAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var supplementDao: SupplementDao

    @Inject
    lateinit var notificationHelper: SupplementNotificationHelper

    @Inject
    lateinit var reminderScheduler: SupplementReminderScheduler

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun onReceive(context: Context, intent: Intent) {
        val supplementId = intent.getLongExtra(EXTRA_SUPPLEMENT_ID, -1L)
        val scheduledDateStr = intent.getStringExtra(EXTRA_SCHEDULED_DATE) ?: return
        val scheduledTimeStr = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: return
        val isSecondWarning = intent.getBooleanExtra(EXTRA_IS_SECOND_WARNING, false)

        if (supplementId == -1L) return

        val scheduledDate = LocalDate.parse(scheduledDateStr)
        val scheduledTime = LocalTime.parse(scheduledTimeStr)

        val pendingResult = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                val supplementEntity = supplementDao.getById(supplementId) ?: return@launch
                val supplement = supplementEntity.toDomain()

                if (!supplement.active || !supplement.remindersEnabled) {
                    return@launch
                }

                // Check if this dose was already recorded (taken or skipped)
                val existingIntake = supplementDao.getIntake(supplementId, scheduledDateStr, scheduledTimeStr)
                if (existingIntake != null) {
                    // Already marked: do not notify
                    if (!isSecondWarning) {
                        reminderScheduler.scheduleNextForSupplement(
                            supplementId = supplementId,
                            fromDateTime = LocalDateTime.of(scheduledDate, scheduledTime),
                        )
                    }
                    return@launch
                }

                // Show notification
                notificationHelper.showDoseNotification(
                    supplement = supplement,
                    scheduledDate = scheduledDate,
                    scheduledTime = scheduledTime,
                    isSecondWarning = isSecondWarning,
                )

                if (!isSecondWarning) {
                    // Schedule second warning after 30 minutes if still not recorded
                    reminderScheduler.scheduleSecondWarning(
                        supplementId = supplementId,
                        scheduledDate = scheduledDate,
                        scheduledTime = scheduledTime,
                    )
                    // Schedule the next subsequent dose for this supplement
                    reminderScheduler.scheduleNextForSupplement(
                        supplementId = supplementId,
                        fromDateTime = LocalDateTime.of(scheduledDate, scheduledTime),
                    )
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SUPPLEMENT_ID = "extra_supplement_id"
        const val EXTRA_SCHEDULED_DATE = "extra_scheduled_date"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_IS_SECOND_WARNING = "extra_is_second_warning"

        fun createIntent(
            context: Context,
            supplementId: Long,
            scheduledDate: LocalDate,
            scheduledTime: LocalTime,
            isSecondWarning: Boolean = false,
        ): Intent {
            return Intent(context, SupplementAlarmReceiver::class.java).apply {
                putExtra(EXTRA_SUPPLEMENT_ID, supplementId)
                putExtra(EXTRA_SCHEDULED_DATE, scheduledDate.toString())
                putExtra(EXTRA_SCHEDULED_TIME, scheduledTime.toString())
                putExtra(EXTRA_IS_SECOND_WARNING, isSecondWarning)
            }
        }
    }
}
