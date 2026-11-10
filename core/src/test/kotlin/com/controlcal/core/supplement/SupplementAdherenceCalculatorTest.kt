package com.controlcal.core.supplement

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SupplementAdherenceCalculatorTest {

    private val endDate = LocalDate.of(2026, 11, 10)
    private val startDate = endDate.minusDays(29) // 30 days window

    @Test
    fun `calculateAdherence returns 100 percent when all scheduled doses are taken`() {
        val supplement = Supplement(
            id = 1L,
            name = "Vitamina D",
            doseAmount = 2000.0,
            doseUnit = "UI",
            scheduleType = ScheduleType.DAILY,
            startDate = startDate,
            times = listOf(LocalTime.of(8, 0)),
        )

        // Generate 30 intakes for each day taken
        val intakes = (0..29).map { offset ->
            SupplementIntake(
                id = offset.toLong() + 1,
                supplementId = 1L,
                scheduledDate = startDate.plusDays(offset.toLong()),
                scheduledTime = LocalTime.of(8, 0),
                status = IntakeStatus.TAKEN,
            )
        }

        val result = SupplementAdherenceCalculator.calculateAdherence(
            supplements = listOf(supplement),
            intakes = intakes,
            startDate = startDate,
            endDate = endDate,
        )

        assertEquals(30, result.totalScheduledDosesCount)
        assertEquals(30, result.totalTakenCount)
        assertEquals(0, result.totalSkippedCount)
        assertEquals(0, result.totalPendingCount)
        assertEquals(100.0, result.adherencePercentage, 0.01)

        val suppSummary = result.supplementSummaries.first()
        assertEquals(100.0, suppSummary.adherencePercentage, 0.01)
    }

    @Test
    fun `calculateAdherence counts skipped and pending correctly`() {
        val supplement = Supplement(
            id = 1L,
            name = "Zinc",
            doseAmount = 15.0,
            doseUnit = "mg",
            scheduleType = ScheduleType.DAILY,
            startDate = startDate,
            times = listOf(LocalTime.of(8, 0)),
        )

        // 30 scheduled doses: 15 taken, 5 skipped, 10 pending
        val intakes = mutableListOf<SupplementIntake>()
        for (i in 0 until 15) {
            intakes.add(
                SupplementIntake(
                    id = i.toLong() + 1,
                    supplementId = 1L,
                    scheduledDate = startDate.plusDays(i.toLong()),
                    scheduledTime = LocalTime.of(8, 0),
                    status = IntakeStatus.TAKEN,
                )
            )
        }
        for (i in 15 until 20) {
            intakes.add(
                SupplementIntake(
                    id = i.toLong() + 1,
                    supplementId = 1L,
                    scheduledDate = startDate.plusDays(i.toLong()),
                    scheduledTime = LocalTime.of(8, 0),
                    status = IntakeStatus.SKIPPED,
                )
            )
        }

        val result = SupplementAdherenceCalculator.calculateAdherence(
            supplements = listOf(supplement),
            intakes = intakes,
            startDate = startDate,
            endDate = endDate,
        )

        assertEquals(30, result.totalScheduledDosesCount)
        assertEquals(15, result.totalTakenCount)
        assertEquals(5, result.totalSkippedCount)
        assertEquals(10, result.totalPendingCount)
        // 15 / 30 = 50.0%
        assertEquals(50.0, result.adherencePercentage, 0.01)
    }

    @Test
    fun `calculateAdherence handles zero scheduled doses without division by zero`() {
        val futureSupplement = Supplement(
            id = 1L,
            name = "Futuro",
            doseAmount = 1.0,
            doseUnit = "cápsula",
            scheduleType = ScheduleType.DAILY,
            startDate = endDate.plusDays(5),
            times = listOf(LocalTime.of(8, 0)),
        )

        val result = SupplementAdherenceCalculator.calculateAdherence(
            supplements = listOf(futureSupplement),
            intakes = emptyList(),
            startDate = startDate,
            endDate = endDate,
        )

        assertEquals(0, result.totalScheduledDosesCount)
        assertEquals(0, result.totalTakenCount)
        assertEquals(100.0, result.adherencePercentage, 0.01)
        assertEquals(100.0, result.supplementSummaries.first().adherencePercentage, 0.01)
    }
}
