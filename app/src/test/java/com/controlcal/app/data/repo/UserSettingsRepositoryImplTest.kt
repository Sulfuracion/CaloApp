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
import com.controlcal.core.profile.UserProfile
import com.controlcal.core.profile.UserSex
import com.controlcal.core.weight.WeightRange
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

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

    @Test
    fun healthConnectStepsSyncSavesAndRetrieves() = runTest {
        val repo = UserSettingsRepositoryImpl(dataStore)
        assertEquals(false, repo.isHealthConnectStepsSyncEnabled())

        repo.setHealthConnectStepsSyncEnabled(true)
        assertEquals(true, repo.isHealthConnectStepsSyncEnabled())

        repo.setHealthConnectStepsSyncEnabled(false)
        assertEquals(false, repo.isHealthConnectStepsSyncEnabled())
    }

    @Test
    fun lastBackupTimestampSavesAndRetrieves() = runTest {
        val repo = UserSettingsRepositoryImpl(dataStore)
        assertEquals(null, repo.getLastBackupTimestamp())

        val now = 1770000000000L
        repo.setLastBackupTimestamp(now)
        assertEquals(now, repo.getLastBackupTimestamp())
    }

    @Test
    fun weightChartRangeDefaultsTo1MAndSavesCorrectly() = runTest {
        val repo = UserSettingsRepositoryImpl(dataStore)
        assertEquals(WeightRange.LAST_1_MONTH, repo.getWeightChartRange())

        repo.setWeightChartRange(WeightRange.LAST_6_MONTHS)
        assertEquals(WeightRange.LAST_6_MONTHS, repo.getWeightChartRange())

        repo.setWeightChartRange(WeightRange.ALL)
        assertEquals(WeightRange.ALL, repo.getWeightChartRange())
    }

    @Test
    fun returnsEmptyProfileByDefault() = runTest {
        val repo = UserSettingsRepositoryImpl(dataStore)
        val profile = repo.getUserProfile()

        assertNull(profile.sex)
        assertNull(profile.birthDate)
        assertNull(profile.heightCm)
        assertEquals(UserProfile.DEFAULT_WORKOUT_KCAL_PER_SESSION, profile.workoutKcalPerSession, 0.001)
        assertFalse(profile.isComplete)
    }

    @Test
    fun savesAndRetrievesCompleteProfile() = runTest {
        val repo = UserSettingsRepositoryImpl(dataStore)
        val profile = UserProfile(
            sex = UserSex.MALE,
            birthDate = LocalDate.parse("1992-06-20"),
            heightCm = 182.5,
            workoutKcalPerSession = 400.0,
        )

        repo.setUserProfile(profile)
        val retrieved = repo.getUserProfile()

        assertEquals(UserSex.MALE, retrieved.sex)
        assertEquals(LocalDate.parse("1992-06-20"), retrieved.birthDate)
        assertEquals(182.5, retrieved.heightCm!!, 0.001)
        assertEquals(400.0, retrieved.workoutKcalPerSession, 0.001)
        assertTrue(retrieved.isComplete)
    }

    @Test
    fun clearingProfileFieldsRemovesThem() = runTest {
        val repo = UserSettingsRepositoryImpl(dataStore)
        repo.setUserProfile(
            UserProfile(
                sex = UserSex.FEMALE,
                birthDate = LocalDate.parse("1995-04-10"),
                heightCm = 168.0,
                workoutKcalPerSession = 320.0,
            )
        )

        repo.setUserProfile(UserProfile(workoutKcalPerSession = 300.0))
        val cleared = repo.getUserProfile()

        assertNull(cleared.sex)
        assertNull(cleared.birthDate)
        assertNull(cleared.heightCm)
        assertEquals(300.0, cleared.workoutKcalPerSession, 0.001)
        assertFalse(cleared.isComplete)
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
