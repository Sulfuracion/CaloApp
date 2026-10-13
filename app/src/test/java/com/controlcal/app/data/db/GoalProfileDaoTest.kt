package com.controlcal.app.data.db

import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalOrigin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GoalProfileDaoTest : DaoTestBase() {

    @Test
    fun profileForDayTypeReturnsLatestValidProfile() = runTest {
        val dao = db.goalProfileDao()
        dao.insert(
            TestData.goalProfile(
                dayType = DayType.TRAINING,
                kcal = 2600.0,
                validFrom = "2026-10-01",
            ),
        )
        dao.insert(
            TestData.goalProfile(
                dayType = DayType.TRAINING,
                kcal = 2500.0,
                validFrom = "2026-10-10",
                origin = GoalOrigin.TDEE,
            ),
        )
        dao.insert(
            TestData.goalProfile(
                dayType = DayType.REST,
                kcal = 2200.0,
                validFrom = "2026-10-05",
            ),
        )

        val current = dao.profileForDayType(DayType.TRAINING, "2026-10-15")
        assertNotNull(current)
        assertEquals(2500.0, current!!.kcal, 0.0)
        assertEquals(GoalOrigin.TDEE, current.origin)

        val midRange = dao.profileForDayType(DayType.TRAINING, "2026-10-05")
        assertNotNull(midRange)
        assertEquals(2600.0, midRange!!.kcal, 0.0)

        val rest = dao.profileForDayType(DayType.REST, "2026-10-15")
        assertNotNull(rest)
        assertEquals(2200.0, rest!!.kcal, 0.0)
    }

    @Test
    fun profileNotYetValidReturnsNull() = runTest {
        val dao = db.goalProfileDao()
        dao.insert(TestData.goalProfile(validFrom = "2026-10-10"))

        assertNull(dao.profileForDayType(DayType.REST, "2026-10-09"))
    }

    @Test
    fun sameValidFromLatestInsertWins() = runTest {
        val dao = db.goalProfileDao()
        dao.insert(TestData.goalProfile(kcal = 2200.0, validFrom = "2026-10-01"))
        dao.insert(TestData.goalProfile(kcal = 2300.0, validFrom = "2026-10-01"))

        val profile = dao.profileForDayType(DayType.REST, "2026-10-02")
        assertNotNull(profile)
        assertEquals(2300.0, profile!!.kcal, 0.0)
    }
}
