package com.controlcal.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.controlcal.core.model.DayType
import com.controlcal.core.model.WeeklyDayPattern
import com.controlcal.core.profile.UserProfile
import com.controlcal.core.profile.UserSex
import com.controlcal.core.repo.UserSettingsRepository
import com.controlcal.core.weight.WeightRange
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserSettingsRepository {

    private val weeklyPatternKey = stringPreferencesKey("default_weekly_pattern")
    private val minKcalLimitKey = doublePreferencesKey("min_kcal_limit")
    private val lastTdeeProposalReviewedDateKey = stringPreferencesKey("last_tdee_proposal_reviewed_date")
    private val healthConnectStepsSyncKey = booleanPreferencesKey("health_connect_steps_sync_enabled")
    private val lastBackupTimestampKey = longPreferencesKey("last_backup_timestamp")
    private val weightChartRangeKey = stringPreferencesKey("weight_chart_range")
    private val userSexKey = stringPreferencesKey("user_sex")
    private val userBirthDateKey = stringPreferencesKey("user_birth_date")
    private val userHeightCmKey = doublePreferencesKey("user_height_cm")
    private val userWorkoutKcalKey = doublePreferencesKey("user_workout_kcal_per_session")

    override suspend fun getLastBackupTimestamp(): Long? {
        val prefs = dataStore.data.first()
        return prefs[lastBackupTimestampKey]
    }

    override suspend fun setLastBackupTimestamp(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[lastBackupTimestampKey] = timestamp
        }
    }

    override suspend fun getWeeklyPattern(): WeeklyDayPattern {
        val prefs = dataStore.data.first()
        val raw = prefs[weeklyPatternKey] ?: return WeeklyDayPattern()
        return parsePattern(raw)
    }

    override suspend fun setWeeklyPattern(pattern: WeeklyDayPattern) {
        dataStore.edit { prefs ->
            prefs[weeklyPatternKey] = serializePattern(pattern)
        }
    }

    override suspend fun getMinKcalLimit(): Double {
        val prefs = dataStore.data.first()
        return prefs[minKcalLimitKey] ?: DEFAULT_MIN_KCAL
    }

    override suspend fun setMinKcalLimit(minKcal: Double) {
        dataStore.edit { prefs ->
            prefs[minKcalLimitKey] = minKcal
        }
    }

    override suspend fun getLastTdeeProposalReviewedDate(): String? {
        val prefs = dataStore.data.first()
        return prefs[lastTdeeProposalReviewedDateKey]
    }

    override suspend fun setLastTdeeProposalReviewedDate(date: String) {
        dataStore.edit { prefs ->
            prefs[lastTdeeProposalReviewedDateKey] = date
        }
    }

    override suspend fun isHealthConnectStepsSyncEnabled(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[healthConnectStepsSyncKey] ?: false
    }

    override suspend fun setHealthConnectStepsSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[healthConnectStepsSyncKey] = enabled
        }
    }

    override suspend fun getWeightChartRange(): WeightRange {
        val prefs = dataStore.data.first()
        val raw = prefs[weightChartRangeKey] ?: return WeightRange.LAST_1_MONTH
        return try {
            WeightRange.valueOf(raw)
        } catch (_: Exception) {
            WeightRange.LAST_1_MONTH
        }
    }

    override suspend fun setWeightChartRange(range: WeightRange) {
        dataStore.edit { prefs ->
            prefs[weightChartRangeKey] = range.name
        }
    }

    override suspend fun getUserProfile(): UserProfile {
        val prefs = dataStore.data.first()
        val rawSex = prefs[userSexKey]
        val sex = rawSex?.let {
            try {
                UserSex.valueOf(it)
            } catch (_: Exception) {
                null
            }
        }
        val rawBirthDate = prefs[userBirthDateKey]
        val birthDate = rawBirthDate?.let {
            try {
                LocalDate.parse(it)
            } catch (_: Exception) {
                null
            }
        }
        val heightCm = prefs[userHeightCmKey]
        val workoutKcal = prefs[userWorkoutKcalKey] ?: UserProfile.DEFAULT_WORKOUT_KCAL_PER_SESSION

        return UserProfile(
            sex = sex,
            birthDate = birthDate,
            heightCm = heightCm,
            workoutKcalPerSession = workoutKcal,
        )
    }

    override suspend fun setUserProfile(profile: UserProfile) {
        val sex = profile.sex
        val birthDate = profile.birthDate
        val heightCm = profile.heightCm

        dataStore.edit { prefs ->
            if (sex != null) {
                prefs[userSexKey] = sex.name
            } else {
                prefs.remove(userSexKey)
            }

            if (birthDate != null) {
                prefs[userBirthDateKey] = birthDate.toString()
            } else {
                prefs.remove(userBirthDateKey)
            }

            if (heightCm != null) {
                prefs[userHeightCmKey] = heightCm
            } else {
                prefs.remove(userHeightCmKey)
            }

            prefs[userWorkoutKcalKey] = profile.workoutKcalPerSession
        }
    }

    companion object {
        const val DEFAULT_MIN_KCAL = 1200.0

        fun serializePattern(pattern: WeeklyDayPattern): String = pattern.serialize()

        fun parsePattern(raw: String): WeeklyDayPattern = WeeklyDayPattern.parse(raw)
    }
}
