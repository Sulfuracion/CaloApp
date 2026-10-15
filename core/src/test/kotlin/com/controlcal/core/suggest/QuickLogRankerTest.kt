package com.controlcal.core.suggest

import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.MealEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class QuickLogRankerTest {

    private val zoneId: ZoneId = ZoneOffset.UTC

    private fun localToMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }

    private fun createEntry(
        id: Long = 1L,
        productId: Long,
        productName: String = "Product $productId",
        grams: Double = 100.0,
        loggedAt: Long,
        date: String = "2026-10-14",
        meal: String = DefaultMealIds.BREAKFAST,
    ): MealEntry {
        return MealEntry(
            id = id,
            date = date,
            meal = meal,
            productId = productId,
            productName = productName,
            grams = grams,
            kcal = 100.0,
            protein = 5.0,
            carbs = 10.0,
            fat = 2.0,
            loggedAt = loggedAt,
        )
    }

    @Test
    fun emptyHistoryReturnsEmptyList() {
        val result = QuickLogRanker.rank(
            entries = emptyList(),
            nowMillis = localToMillis(2026, 10, 14, 12, 0),
            zoneId = zoneId,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun entriesOlderThan60DaysAreExcluded() {
        // Wednesday 2026-10-14 12:00
        val now = localToMillis(2026, 10, 14, 12, 0)
        val sixtyOneDaysAgo = now - (61L * 24 * 60 * 60 * 1000L)
        val fiftyNineDaysAgo = now - (59L * 24 * 60 * 60 * 1000L)

        val oldEntry = createEntry(id = 1, productId = 101, loggedAt = sixtyOneDaysAgo)
        val validEntry = createEntry(id = 2, productId = 102, loggedAt = fiftyNineDaysAgo)

        val result = QuickLogRanker.rank(
            entries = listOf(oldEntry, validEntry),
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(1, result.size)
        assertEquals(102L, result.first().productId)
    }

    @Test
    fun futureEntriesAreExcluded() {
        val now = localToMillis(2026, 10, 14, 12, 0)
        val futureMillis = now + 1000L * 60 * 60

        val futureEntry = createEntry(id = 1, productId = 101, loggedAt = futureMillis)
        val result = QuickLogRanker.rank(
            entries = listOf(futureEntry),
            nowMillis = now,
            zoneId = zoneId,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun entriesOutside90MinutesAreExcluded() {
        // Wednesday 2026-10-14 12:00
        val now = localToMillis(2026, 10, 14, 12, 0)
        // 12:00 - 91 min = 10:29 (excluded)
        val outsideEntry = createEntry(id = 1, productId = 101, loggedAt = localToMillis(2026, 10, 13, 10, 29))
        // 12:00 - 89 min = 10:31 (included)
        val insideEntry = createEntry(id = 2, productId = 102, loggedAt = localToMillis(2026, 10, 13, 10, 31))

        val result = QuickLogRanker.rank(
            entries = listOf(outsideEntry, insideEntry),
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(1, result.size)
        assertEquals(102L, result.first().productId)
    }

    @Test
    fun circularTimeAcrossMidnight_nowAfterMidnight() {
        // Wednesday 2026-10-14 00:30 (12:30 AM)
        val now = localToMillis(2026, 10, 14, 0, 30)
        // 23:45 of previous day: difference is 45 min across midnight (inside <= 90 min)
        val entryBeforeMidnight = createEntry(id = 1, productId = 201, loggedAt = localToMillis(2026, 10, 13, 23, 45))
        // 22:00 of previous day: difference is 150 min (excluded)
        val entryTooEarly = createEntry(id = 2, productId = 202, loggedAt = localToMillis(2026, 10, 13, 22, 0))

        val result = QuickLogRanker.rank(
            entries = listOf(entryBeforeMidnight, entryTooEarly),
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(1, result.size)
        assertEquals(201L, result.first().productId)
    }

    @Test
    fun circularTimeAcrossMidnight_nowBeforeMidnight() {
        // Tuesday 2026-10-13 23:45
        val now = localToMillis(2026, 10, 13, 23, 45)
        // 00:20 of some day in past: difference is 35 min across midnight (inside <= 90 min)
        val entryAfterMidnight = createEntry(id = 1, productId = 301, loggedAt = localToMillis(2026, 10, 10, 0, 20))

        val result = QuickLogRanker.rank(
            entries = listOf(entryAfterMidnight),
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(1, result.size)
        assertEquals(301L, result.first().productId)
    }

    @Test
    fun doubleWeightForMatchingDayOfWeek() {
        // Wednesday 2026-10-14 12:00
        val now = localToMillis(2026, 10, 14, 12, 0)
        // Previous Wednesday: 2026-10-07 12:00 (7 days ago, matches Wednesday -> x2.0)
        val wednesdayEntry = createEntry(id = 1, productId = 401, loggedAt = localToMillis(2026, 10, 7, 12, 0))
        // Previous Tuesday: 2026-10-06 12:00 (8 days ago, does not match Wednesday -> x1.0)
        val tuesdayEntry = createEntry(id = 2, productId = 402, loggedAt = localToMillis(2026, 10, 6, 12, 0))

        val result = QuickLogRanker.rank(
            entries = listOf(wednesdayEntry, tuesdayEntry),
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(2, result.size)
        val scoreWed = result.first { it.productId == 401L }.score
        val scoreTue = result.first { it.productId == 402L }.score

        // Wednesday entry (even slightly older than a 1-day diff) scores nearly double
        assertTrue(scoreWed > scoreTue * 1.8)
    }

    @Test
    fun decayByAge_moreRecentScoresHigher() {
        // Wednesday 2026-10-14 12:00
        val now = localToMillis(2026, 10, 14, 12, 0)
        // Both on Tuesday: 1 day ago vs 29 days ago
        val recentEntry = createEntry(id = 1, productId = 501, loggedAt = localToMillis(2026, 10, 13, 12, 0))
        val oldEntry = createEntry(id = 2, productId = 502, loggedAt = localToMillis(2026, 9, 15, 12, 0))

        val result = QuickLogRanker.rank(
            entries = listOf(recentEntry, oldEntry),
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(2, result.size)
        val recentScore = result.first { it.productId == 501L }.score
        val oldScore = result.first { it.productId == 502L }.score

        assertTrue("Recent score ($recentScore) should be higher than old score ($oldScore)", recentScore > oldScore)
    }

    @Test
    fun mostFrequentGramsCalculatesMode() {
        val now = localToMillis(2026, 10, 14, 12, 0)
        val entries = listOf(
            createEntry(id = 1, productId = 601, grams = 40.0, loggedAt = localToMillis(2026, 10, 13, 12, 0)),
            createEntry(id = 2, productId = 601, grams = 40.0, loggedAt = localToMillis(2026, 10, 12, 12, 0)),
            createEntry(id = 3, productId = 601, grams = 80.0, loggedAt = localToMillis(2026, 10, 11, 12, 0)),
        )

        val result = QuickLogRanker.rank(entries = entries, nowMillis = now, zoneId = zoneId)
        assertEquals(1, result.size)
        assertEquals(40.0, result.first().defaultGrams, 0.001)
    }

    @Test
    fun negativeGramsIgnoredInModeCalculation() {
        val now = localToMillis(2026, 10, 14, 12, 0)
        val entries = listOf(
            createEntry(id = 1, productId = 701, grams = 150.0, loggedAt = localToMillis(2026, 10, 13, 12, 0)),
            createEntry(id = 2, productId = 701, grams = -30.0, loggedAt = localToMillis(2026, 10, 12, 12, 0)),
            createEntry(id = 3, productId = 701, grams = -30.0, loggedAt = localToMillis(2026, 10, 11, 12, 0)),
        )

        val result = QuickLogRanker.rank(entries = entries, nowMillis = now, zoneId = zoneId)
        assertEquals(1, result.size)
        // Despite -30.0 appearing twice, negative tare entries must be ignored for default quantity
        assertEquals(150.0, result.first().defaultGrams, 0.001)
    }

    @Test
    fun limitTop6Suggestions() {
        val now = localToMillis(2026, 10, 14, 12, 0)
        val entries = (1..10).map { i ->
            createEntry(
                id = i.toLong(),
                productId = 1000L + i,
                productName = "Product $i",
                loggedAt = localToMillis(2026, 10, 14 - (i % 7), 12, 0),
            )
        }

        val result = QuickLogRanker.rank(entries = entries, nowMillis = now, zoneId = zoneId, limit = 6)
        assertEquals(6, result.size)
    }

    @Test
    fun activeProductIdsFilterApplied() {
        val now = localToMillis(2026, 10, 14, 12, 0)
        val entries = listOf(
            createEntry(id = 1, productId = 101, loggedAt = localToMillis(2026, 10, 13, 12, 0)),
            createEntry(id = 2, productId = 102, loggedAt = localToMillis(2026, 10, 13, 12, 0)),
        )

        val result = QuickLogRanker.rank(
            entries = entries,
            nowMillis = now,
            zoneId = zoneId,
            activeProductIds = setOf(101L), // 102 is inactive/archived
        )

        assertEquals(1, result.size)
        assertEquals(101L, result.first().productId)
    }
}
