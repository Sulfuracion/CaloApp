package com.controlcal.core.nutrition

import com.controlcal.core.model.DailyLog
import com.controlcal.core.model.DayType
import com.controlcal.core.model.WeeklyDayPattern
import com.controlcal.core.repo.DailyLogRepository
import com.controlcal.core.repo.UserSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class ResolveDayTypeUseCaseTest {

    private fun <T> runSync(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { res ->
            result = res
        })
        return result!!.getOrThrow()
    }

    private class FakeDailyLogRepo : DailyLogRepository {
        val logs = mutableMapOf<String, DailyLog>()

        override suspend fun getByDate(date: String): DailyLog? = logs[date]
        override suspend fun insert(log: DailyLog) { logs[log.date] = log }
        override suspend fun setDayType(date: String, dayType: DayType) {
            val existing = logs[date]
            if (existing != null) {
                logs[date] = existing.copy(dayType = dayType)
            } else {
                logs[date] = DailyLog(date = date, dayType = dayType)
            }
        }
        override suspend fun setWaterMl(date: String, waterMl: Int) {}
        override suspend fun setNotes(date: String, notes: String?) {}
    }

    private class FakeUserSettingsRepo : UserSettingsRepository {
        var pattern = WeeklyDayPattern()
        var minKcal = 1200.0
        var lastReviewed: String? = null

        override suspend fun getWeeklyPattern(): WeeklyDayPattern = pattern
        override suspend fun setWeeklyPattern(pattern: WeeklyDayPattern) {
            this.pattern = pattern
        }
        override suspend fun getMinKcalLimit(): Double = minKcal
        override suspend fun setMinKcalLimit(minKcal: Double) {
            this.minKcal = minKcal
        }
        override suspend fun getLastTdeeProposalReviewedDate(): String? = lastReviewed
        override suspend fun setLastTdeeProposalReviewedDate(date: String) {
            this.lastReviewed = date
        }
    }

    @Test
    fun returnsExplicitDayTypeWhenDailyLogExists() = runSync {
        val dailyLogRepo = FakeDailyLogRepo()
        val userSettingsRepo = FakeUserSettingsRepo()
        val useCase = ResolveDayTypeUseCase(dailyLogRepo, userSettingsRepo)

        // 2026-10-12 is a Monday (default TRAINING in WeeklyDayPattern)
        val monday = LocalDate.of(2026, 10, 12)
        // Explicitly set Monday to REST in daily log
        dailyLogRepo.insert(DailyLog(date = monday.toString(), dayType = DayType.REST))

        val resolved = useCase(monday)
        assertEquals(DayType.REST, resolved)
    }

    @Test
    fun fallsBackToWeeklyPatternWhenNoDailyLogExists() = runSync {
        val dailyLogRepo = FakeDailyLogRepo()
        val userSettingsRepo = FakeUserSettingsRepo()
        val useCase = ResolveDayTypeUseCase(dailyLogRepo, userSettingsRepo)

        // 2026-10-12 is Monday -> default TRAINING
        val monday = LocalDate.of(2026, 10, 12)
        // 2026-10-13 is Tuesday -> default REST
        val tuesday = LocalDate.of(2026, 10, 13)

        assertEquals(DayType.TRAINING, useCase(monday))
        assertEquals(DayType.REST, useCase(tuesday))

        // Custom weekly pattern
        userSettingsRepo.setWeeklyPattern(
            userSettingsRepo.getWeeklyPattern().withDayType(java.time.DayOfWeek.MONDAY, DayType.REST)
        )
        assertEquals(DayType.REST, useCase(monday))
    }
}
