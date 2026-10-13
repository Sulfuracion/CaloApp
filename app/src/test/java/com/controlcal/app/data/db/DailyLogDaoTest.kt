package com.controlcal.app.data.db

import android.database.sqlite.SQLiteConstraintException
import com.controlcal.core.model.DayType
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DailyLogDaoTest : DaoTestBase() {

    @Test
    fun insertAndReadByDate() = runTest {
        val dao = db.dailyLogDao()
        dao.insert(TestData.dailyLog(date = TestData.DATE_A, dayType = DayType.TRAINING, waterMl = 1500))

        val log = dao.getByDate(TestData.DATE_A)
        assertNotNull(log)
        assertEquals(DayType.TRAINING, log!!.dayType)
        assertEquals(1500, log.waterMl)
        assertNull(log.notes)
    }

    @Test
    fun settersUpdateDayTypeWaterAndNotes() = runTest {
        val dao = db.dailyLogDao()
        dao.insert(TestData.dailyLog(date = TestData.DATE_A))

        dao.setDayType(TestData.DATE_A, DayType.TRAINING)
        dao.setWaterMl(TestData.DATE_A, 2000)
        dao.setNotes(TestData.DATE_A, "Hoy toca pierna")

        val log = dao.getByDate(TestData.DATE_A)
        assertNotNull(log)
        assertEquals(DayType.TRAINING, log!!.dayType)
        assertEquals(2000, log.waterMl)
        assertEquals("Hoy toca pierna", log.notes)
    }

    @Test
    fun dateIsUniquePerDay() = runTest {
        val dao = db.dailyLogDao()
        dao.insert(TestData.dailyLog(date = TestData.DATE_A))

        assertFailsWith<SQLiteConstraintException> {
            dao.insert(TestData.dailyLog(date = TestData.DATE_A))
        }
    }

    @Test
    fun getByDateReturnsNullForUnknownDay() = runTest {
        assertNull(db.dailyLogDao().getByDate(TestData.DATE_A))
    }
}
