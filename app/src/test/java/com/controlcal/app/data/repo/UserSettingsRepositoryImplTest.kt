package com.controlcal.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.controlcal.core.model.DayType
import com.controlcal.core.model.WeeklyDayPattern
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class UserSettingsRepositoryImplTest {

    @After
    fun cleanUp() = runTest {
        dataStore.edit { it.clear() }
    }

    @Test
    fun returnsDefaultPatternWhenEmpty() = runTest {
        val repo = UserSettingsRepositoryImpl(dataStore)
        val pattern = repo.getWeeklyPattern()

        assertEquals(DayType.TRAINING, pattern.dayTypeFor(DayOfWeek.MONDAY))
        assertEquals(DayType.REST, pattern.dayTypeFor(DayOfWeek.TUESDAY))
        assertEquals(DayType.TRAINING, pattern.dayTypeFor(DayOfWeek.WEDNESDAY))
    }

    @Test
    fun saveAndRetrievePattern() = runTest {
        val repo = UserSettingsRepositoryImpl(dataStore)
        val custom = WeeklyDayPattern().withDayType(DayOfWeek.TUESDAY, DayType.TRAINING)
            .withDayType(DayOfWeek.FRIDAY, DayType.REST)

        repo.setWeeklyPattern(custom)
        val retrieved = repo.getWeeklyPattern()

        assertEquals(DayType.TRAINING, retrieved.dayTypeFor(DayOfWeek.TUESDAY))
        assertEquals(DayType.REST, retrieved.dayTypeFor(DayOfWeek.FRIDAY))
        assertEquals(DayType.TRAINING, retrieved.dayTypeFor(DayOfWeek.MONDAY))
    }

    @Test
    fun serializationHandlesCorruptionGracefully() {
        val corrupted = UserSettingsRepositoryImpl.parsePattern("INVALID,DATA")
        assertEquals(DayType.TRAINING, corrupted.dayTypeFor(DayOfWeek.MONDAY))
    }

    @Test
    fun minKcalLimitDefaultsTo1200AndSavesCorrectly() = runTest {
        val repo = UserSettingsRepositoryImpl(dataStore)
        assertEquals(1200.0, repo.getMinKcalLimit(), 0.001)

        repo.setMinKcalLimit(1500.0)
        assertEquals(1500.0, repo.getMinKcalLimit(), 0.001)
    }

    @Test
    fun lastTdeeProposalReviewedDateSavesAndRetrieves() = runTest {
        val repo = UserSettingsRepositoryImpl(dataStore)
        assertEquals(null, repo.getLastTdeeProposalReviewedDate())

        repo.setLastTdeeProposalReviewedDate("2026-10-14")
        assertEquals("2026-10-14", repo.getLastTdeeProposalReviewedDate())
    }

    companion object {
        private lateinit var dataStore: DataStore<Preferences>

        @BeforeClass
        @JvmStatic
        fun createStore() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "test_user_settings.preferences_pb").apply { delete() }
            dataStore = PreferenceDataStoreFactory.create { file }
        }
    }
}
