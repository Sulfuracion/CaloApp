package com.controlcal.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.controlcal.app.data.db.dao.SupplementDao
import com.controlcal.core.supplement.NextAlarmCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplementReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supplementDao: SupplementDao,
) {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val SNOOZE_MINUTES = 15
        const val SECOND_WARNING_MINUTES = 30
        const val WINDOW_MARGIN_MILLIS = 10 * 60 * 1000L // 10 minutes

        private const val BASE_REQUEST_CODE_DOSE = 100_000
        private const val BASE_REQUEST_CODE_RETRY = 200_000
        private const val BASE_REQUEST_CODE_SNOOZE = 300_000
    }

    suspend fun scheduleNextForSupplement(
        supplementId: Long,
        fromDateTime: LocalDateTime = LocalDateTime.now(),
    ) = withContext(ioDispatcher) {
        val supplementEntity = supplementDao.getById(supplementId) ?: return@withContext
        val supplement = supplementEntity.toDomain()

        if (!supplement.active || !supplement.remindersEnabled || supplement.times.isEmpty()) {
            cancelSupplementAlarm(supplementId)
            cancelSecondWarning(supplementId)
            return@withContext
        }

        val next = NextAlarmCalculator.findNextDose(supplement, fromDateTime)
        if (next != null) {
            val (date, time) = next
            val triggerMillis = NextAlarmCalculator.calculateTriggerEpochMillis(
                scheduledDate = date,
                scheduledTime = time,
                zoneId = ZoneId.systemDefault(),
            )
            setAlarm(
                triggerMillis = triggerMillis,
                pendingIntent = createAlarmPendingIntent(
                    supplementId = supplementId,
                    scheduledDate = date,
                    scheduledTime = time,
                    isSecondWarning = false,
                    requestCode = (BASE_REQUEST_CODE_DOSE + supplementId).toInt(),
                ),
            )
        } else {
            cancelSupplementAlarm(supplementId)
        }
    }

    fun scheduleSecondWarning(
        supplementId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        delayMinutes: Int = SECOND_WARNING_MINUTES,
    ) {
        val triggerMillis = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        setAlarm(
            triggerMillis = triggerMillis,
            pendingIntent = createAlarmPendingIntent(
                supplementId = supplementId,
                scheduledDate = scheduledDate,
                scheduledTime = scheduledTime,
                isSecondWarning = true,
                requestCode = (BASE_REQUEST_CODE_RETRY + supplementId).toInt(),
            ),
        )
    }

    fun scheduleSnooze(
        supplementId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        delayMinutes: Int = SNOOZE_MINUTES,
    ) {
        val triggerMillis = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        setAlarm(
            triggerMillis = triggerMillis,
            pendingIntent = createAlarmPendingIntent(
                supplementId = supplementId,
                scheduledDate = scheduledDate,
                scheduledTime = scheduledTime,
                isSecondWarning = false,
                requestCode = (BASE_REQUEST_CODE_SNOOZE + supplementId).toInt(),
            ),
        )
    }

    fun cancelSupplementAlarm(supplementId: Long) {
        val intent = Intent(context, SupplementAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (BASE_REQUEST_CODE_DOSE + supplementId).toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun cancelSecondWarning(supplementId: Long) {
        val intent = Intent(context, SupplementAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (BASE_REQUEST_CODE_RETRY + supplementId).toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    suspend fun rescheduleAll() = withContext(ioDispatcher) {
        val activeSupplements = supplementDao.getActive()
        for (suppEntity in activeSupplements) {
            scheduleNextForSupplement(suppEntity.id)
        }
    }

    private fun setAlarm(triggerMillis: Long, pendingIntent: PendingIntent) {
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                WINDOW_MARGIN_MILLIS,
                pendingIntent,
            )
        }
    }

    private fun createAlarmPendingIntent(
        supplementId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        isSecondWarning: Boolean,
        requestCode: Int,
    ): PendingIntent {
        val intent = SupplementAlarmReceiver.createIntent(
            context = context,
            supplementId = supplementId,
            scheduledDate = scheduledDate,
            scheduledTime = scheduledTime,
            isSecondWarning = isSecondWarning,
        )
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
