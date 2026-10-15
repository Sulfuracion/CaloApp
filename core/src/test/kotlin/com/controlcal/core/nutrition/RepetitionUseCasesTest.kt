package com.controlcal.core.nutrition

import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.MealEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RepetitionUseCasesTest {

    private val zoneId = ZoneId.of("Europe/Madrid")

    private fun sampleEntry(
        id: Long,
        date: String,
        meal: String,
        productId: Long,
        grams: Double,
        time: LocalTime = LocalTime.of(8, 30),
    ): MealEntry {
        val localDate = LocalDate.parse(date)
        val loggedAt = localDate.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
        return MealEntry(
            id = id,
            date = date,
            meal = meal,
            productId = productId,
            productName = "Product $productId",
            grams = grams,
            kcal = 200.0,
            protein = 15.0,
            carbs = 20.0,
            fat = 5.0,
            loggedAt = loggedAt,
        )
    }

    @Test
    fun repeatMealUseCase_clonesEntriesToTargetDateAndPreservesTime() {
        val useCase = RepeatMealUseCase()
        val source = listOf(
            sampleEntry(id = 1, date = "2026-10-13", meal = DefaultMealIds.BREAKFAST, productId = 10, grams = 50.0, time = LocalTime.of(8, 15)),
            sampleEntry(id = 2, date = "2026-10-13", meal = DefaultMealIds.BREAKFAST, productId = 20, grams = 200.0, time = LocalTime.of(8, 45)),
        )

        val result = useCase(
            sourceEntries = source,
            targetDate = "2026-10-14",
            targetMeal = DefaultMealIds.BREAKFAST,
            zoneId = zoneId,
        )

        assertEquals(2, result.size)
        assertEquals(listOf(0L, 0L), result.map { it.id })
        assertEquals(listOf("2026-10-14", "2026-10-14"), result.map { it.date })
        assertEquals(listOf(DefaultMealIds.BREAKFAST, DefaultMealIds.BREAKFAST), result.map { it.meal })
        assertEquals(listOf(50.0, 200.0), result.map { it.grams })

        // Check that times of day are preserved on target date
        val expected0 = LocalDate.of(2026, 10, 14).atTime(LocalTime.of(8, 15)).atZone(zoneId).toInstant().toEpochMilli()
        val expected1 = LocalDate.of(2026, 10, 14).atTime(LocalTime.of(8, 45)).atZone(zoneId).toInstant().toEpochMilli()
        assertEquals(listOf(expected0, expected1), result.map { it.loggedAt })
    }

    @Test
    fun copyDayUseCase_clonesAllEntriesToTargetDateAndPreservesTime() {
        val useCase = CopyDayUseCase()
        val source = listOf(
            sampleEntry(id = 1, date = "2026-10-13", meal = DefaultMealIds.BREAKFAST, productId = 10, grams = 50.0, time = LocalTime.of(8, 30)),
            sampleEntry(id = 2, date = "2026-10-13", meal = DefaultMealIds.LUNCH, productId = 30, grams = 150.0, time = LocalTime.of(14, 0)),
            sampleEntry(id = 3, date = "2026-10-13", meal = DefaultMealIds.DINNER, productId = 40, grams = 80.0, time = LocalTime.of(21, 30)),
        )

        val result = useCase(
            sourceDayEntries = source,
            targetDate = "2026-10-14",
            zoneId = zoneId,
        )

        assertEquals(3, result.size)
        assertEquals(listOf(0L, 0L, 0L), result.map { it.id })
        assertEquals(listOf("2026-10-14", "2026-10-14", "2026-10-14"), result.map { it.date })
        assertEquals(listOf(DefaultMealIds.BREAKFAST, DefaultMealIds.LUNCH, DefaultMealIds.DINNER), result.map { it.meal })

        val targetDate = LocalDate.of(2026, 10, 14)
        val expectedTimes = listOf(LocalTime.of(8, 30), LocalTime.of(14, 0), LocalTime.of(21, 30))
        val actualTimes = result.map { Instant.ofEpochMilli(it.loggedAt).atZone(zoneId).toLocalTime() }
        assertEquals(expectedTimes, actualTimes)
    }
}
