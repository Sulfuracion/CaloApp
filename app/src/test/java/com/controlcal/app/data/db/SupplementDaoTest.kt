package com.controlcal.app.data.db

import com.controlcal.app.data.db.entity.SupplementEntity
import com.controlcal.app.data.db.entity.SupplementIntakeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementDaoTest : DaoTestBase() {

    @Test
    fun insertAndRetrieveSupplements() = runTest {
        val dao = db.supplementDao()

        val id1 = dao.insert(
            SupplementEntity(
                name = "Vitamina D3",
                doseAmount = 2000.0,
                doseUnit = "UI",
                scheduleType = "DAILY",
                startDate = "2026-11-01",
                times = """["08:00"]""",
                active = true,
                remindersEnabled = true,
                notes = "Tomar con comida",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        val id2 = dao.insert(
            SupplementEntity(
                name = "Creatina",
                doseAmount = 5.0,
                doseUnit = "g",
                scheduleType = "DAILY",
                startDate = "2026-11-01",
                times = """["09:00","21:00"]""",
                active = false,
                remindersEnabled = false,
                notes = null,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        val all = dao.getAll()
        assertEquals(2, all.size)

        val active = dao.getActive()
        assertEquals(1, active.size)
        assertEquals("Vitamina D3", active.first().name)

        val activeFlow = dao.getActiveFlow().first()
        assertEquals(1, activeFlow.size)
        assertEquals(id1, activeFlow.first().id)

        val retrieved1 = dao.getById(id1)
        assertNotNull(retrieved1)
        assertEquals("Vitamina D3", retrieved1?.name)
        assertEquals("Tomar con comida", retrieved1?.notes)

        val retrieved2 = dao.getById(id2)
        assertNotNull(retrieved2)
        assertEquals("Creatina", retrieved2?.name)
    }

    @Test
    fun updateSupplementAndToggleActivePreservesHistory() = runTest {
        val dao = db.supplementDao()

        val id = dao.insert(
            SupplementEntity(
                name = "Magnesio",
                doseAmount = 400.0,
                doseUnit = "mg",
                scheduleType = "DAILY",
                startDate = "2026-11-01",
                times = """["22:00"]""",
                active = true,
                remindersEnabled = true,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        dao.insertIntake(
            SupplementIntakeEntity(
                supplementId = id,
                scheduledDate = "2026-11-01",
                scheduledTime = "22:00",
                status = "TAKEN",
                recordedAt = 1050L,
            )
        )

        // Deactivate without deleting
        dao.setActive(id, false, 2000L)

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(false, all.first().active)
        assertEquals(2000L, all.first().updatedAt)

        val active = dao.getActive()
        assertTrue(active.isEmpty())

        // Verify intakes are preserved
        val intakes = dao.getAllIntakes().filter { it.supplementId == id }
        assertEquals(1, intakes.size)
        assertEquals("TAKEN", intakes.first().status)
    }

    @Test
    fun insertIntakeIsIdempotentOnConflict() = runTest {
        val dao = db.supplementDao()

        val suppId = dao.insert(
            SupplementEntity(
                name = "Omega 3",
                doseAmount = 1000.0,
                doseUnit = "mg",
                scheduleType = "DAILY",
                startDate = "2026-11-01",
                times = """["13:00"]""",
                active = true,
                remindersEnabled = true,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        // First tap: mark TAKEN
        dao.insertIntake(
            SupplementIntakeEntity(
                supplementId = suppId,
                scheduledDate = "2026-11-10",
                scheduledTime = "13:00",
                status = "TAKEN",
                recordedAt = 1000L,
            )
        )

        // Second tap on notification / UI: mark TAKEN again
        dao.insertIntake(
            SupplementIntakeEntity(
                supplementId = suppId,
                scheduledDate = "2026-11-10",
                scheduledTime = "13:00",
                status = "TAKEN",
                recordedAt = 1005L,
            )
        )

        val intakes = dao.getIntakesForDate("2026-11-10")
        assertEquals(1, intakes.size)
        assertEquals(1005L, intakes.first().recordedAt)

        // Change status to SKIPPED
        dao.insertIntake(
            SupplementIntakeEntity(
                supplementId = suppId,
                scheduledDate = "2026-11-10",
                scheduledTime = "13:00",
                status = "SKIPPED",
                recordedAt = 1010L,
            )
        )

        val updatedIntakes = dao.getIntakesForDate("2026-11-10")
        assertEquals(1, updatedIntakes.size)
        assertEquals("SKIPPED", updatedIntakes.first().status)

        // Delete intake (undo)
        dao.deleteIntake(suppId, "2026-11-10", "13:00")
        val emptyIntakes = dao.getIntakesForDate("2026-11-10")
        assertTrue(emptyIntakes.isEmpty())
    }

    @Test
    fun cascadeDeleteSupplementDeletesItsIntakes() = runTest {
        val dao = db.supplementDao()

        val suppId = dao.insert(
            SupplementEntity(
                name = "Zinc",
                doseAmount = 25.0,
                doseUnit = "mg",
                scheduleType = "DAILY",
                startDate = "2026-11-01",
                times = """["08:00"]""",
                active = true,
                remindersEnabled = true,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        dao.insertIntake(
            SupplementIntakeEntity(
                supplementId = suppId,
                scheduledDate = "2026-11-01",
                scheduledTime = "08:00",
                status = "TAKEN",
                recordedAt = 1000L,
            )
        )
        dao.insertIntake(
            SupplementIntakeEntity(
                supplementId = suppId,
                scheduledDate = "2026-11-02",
                scheduledTime = "08:00",
                status = "TAKEN",
                recordedAt = 2000L,
            )
        )

        assertEquals(2, dao.getAllIntakes().size)

        // Delete supplement
        dao.deleteById(suppId)

        assertNull(dao.getById(suppId))
        assertTrue(dao.getAllIntakes().isEmpty())
    }

    @Test
    fun getIntakesForDateRange() = runTest {
        val dao = db.supplementDao()

        val suppId = dao.insert(
            SupplementEntity(
                name = "Vitamina C",
                doseAmount = 500.0,
                doseUnit = "mg",
                scheduleType = "DAILY",
                startDate = "2026-11-01",
                times = """["08:00"]""",
                active = true,
                remindersEnabled = true,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        dao.insertIntake(SupplementIntakeEntity(supplementId = suppId, scheduledDate = "2026-10-31", scheduledTime = "08:00", status = "TAKEN", recordedAt = 1L))
        dao.insertIntake(SupplementIntakeEntity(supplementId = suppId, scheduledDate = "2026-11-01", scheduledTime = "08:00", status = "TAKEN", recordedAt = 2L))
        dao.insertIntake(SupplementIntakeEntity(supplementId = suppId, scheduledDate = "2026-11-05", scheduledTime = "08:00", status = "TAKEN", recordedAt = 3L))
        dao.insertIntake(SupplementIntakeEntity(supplementId = suppId, scheduledDate = "2026-11-10", scheduledTime = "08:00", status = "SKIPPED", recordedAt = 4L))
        dao.insertIntake(SupplementIntakeEntity(supplementId = suppId, scheduledDate = "2026-11-15", scheduledTime = "08:00", status = "TAKEN", recordedAt = 5L))

        val rangeIntakes = dao.getIntakesBetween("2026-11-01", "2026-11-10")
        assertEquals(3, rangeIntakes.size)
        val dates = rangeIntakes.map { it.scheduledDate }
        assertTrue(dates.contains("2026-11-01"))
        assertTrue(dates.contains("2026-11-05"))
        assertTrue(dates.contains("2026-11-10"))
    }
}
