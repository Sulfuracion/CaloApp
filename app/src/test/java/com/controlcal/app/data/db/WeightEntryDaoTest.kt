package com.controlcal.app.data.db

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeightEntryDaoTest : DaoTestBase() {

    @Test
    fun upsertKeepsLatestWeightForDate() = runTest {
        val dao = db.weightEntryDao()
        dao.upsert(TestData.weightEntry(date = TestData.DATE_A, weightKg = 80.5))
        dao.upsert(TestData.weightEntry(date = TestData.DATE_A, weightKg = 79.9))

        val saved = dao.getByDate(TestData.DATE_A)
        assertNotNull(saved)
        assertEquals(79.9, saved!!.weightKg, 0.0)
    }

    @Test
    fun entriesBetweenReturnsRangeInOrder() = runTest {
        val dao = db.weightEntryDao()
        dao.upsert(TestData.weightEntry(date = "2026-10-01", weightKg = 81.0))
        dao.upsert(TestData.weightEntry(date = "2026-10-03", weightKg = 80.7))
        dao.upsert(TestData.weightEntry(date = "2026-10-05", weightKg = 80.4))

        val range = dao.entriesBetween("2026-10-02", "2026-10-05")
        assertEquals(listOf("2026-10-03", "2026-10-05"), range.map { it.date })
    }

    @Test
    fun getByDateReturnsNullForUnknownDay() = runTest {
        assertNull(db.weightEntryDao().getByDate(TestData.DATE_A))
    }
}
