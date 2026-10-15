package com.controlcal.app.data.db

import kotlinx.coroutines.flow.first
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

    @Test
    fun getAllAscReturnsAllEntriesInAscendingOrder() = runTest {
        val dao = db.weightEntryDao()
        dao.upsert(TestData.weightEntry(date = "2026-10-05", weightKg = 80.4))
        dao.upsert(TestData.weightEntry(date = "2026-10-01", weightKg = 81.0))
        dao.upsert(TestData.weightEntry(date = "2026-10-03", weightKg = 80.7))

        val all = dao.getAllAsc()
        assertEquals(listOf("2026-10-01", "2026-10-03", "2026-10-05"), all.map { it.date })
    }

    @Test
    fun observeAllDescEmitsEntriesInDescendingOrder() = runTest {
        val dao = db.weightEntryDao()
        dao.upsert(TestData.weightEntry(date = "2026-10-01", weightKg = 81.0))
        dao.upsert(TestData.weightEntry(date = "2026-10-05", weightKg = 80.4))

        val observed = dao.observeAllDesc().first()
        assertEquals(listOf("2026-10-05", "2026-10-01"), observed.map { it.date })
    }

    @Test
    fun deleteByDateRemovesEntry() = runTest {
        val dao = db.weightEntryDao()
        dao.upsert(TestData.weightEntry(date = "2026-10-01", weightKg = 81.0))
        assertNotNull(dao.getByDate("2026-10-01"))

        dao.deleteByDate("2026-10-01")
        assertNull(dao.getByDate("2026-10-01"))
    }

    @Test
    fun moveEntryToEmptyDateMovesEntryAtomically() = runTest {
        val dao = db.weightEntryDao()
        dao.upsert(TestData.weightEntry(date = "2026-10-01", weightKg = 81.0))

        dao.moveEntry(oldDate = "2026-10-01", newEntry = TestData.weightEntry(date = "2026-10-02", weightKg = 81.5))

        assertNull(dao.getByDate("2026-10-01"))
        val moved = dao.getByDate("2026-10-02")
        assertNotNull(moved)
        assertEquals(81.5, moved!!.weightKg, 0.0)
    }

    @Test
    fun moveEntryToExistingDateReplacesTargetAtomically() = runTest {
        val dao = db.weightEntryDao()
        dao.upsert(TestData.weightEntry(date = "2026-10-01", weightKg = 81.0))
        dao.upsert(TestData.weightEntry(date = "2026-10-02", weightKg = 75.0))

        dao.moveEntry(oldDate = "2026-10-01", newEntry = TestData.weightEntry(date = "2026-10-02", weightKg = 80.5))

        assertNull(dao.getByDate("2026-10-01"))
        val replaced = dao.getByDate("2026-10-02")
        assertNotNull(replaced)
        assertEquals(80.5, replaced!!.weightKg, 0.0)
    }

    @Test
    fun moveEntrySameDateUpdatesWeightWithoutDeleting() = runTest {
        val dao = db.weightEntryDao()
        dao.upsert(TestData.weightEntry(date = "2026-10-01", weightKg = 81.0))

        dao.moveEntry(oldDate = "2026-10-01", newEntry = TestData.weightEntry(date = "2026-10-01", weightKg = 82.0))

        val updated = dao.getByDate("2026-10-01")
        assertNotNull(updated)
        assertEquals(82.0, updated!!.weightKg, 0.0)
    }
}

