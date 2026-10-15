package com.controlcal.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.controlcal.core.model.DayType
import com.controlcal.core.model.WeeklyDayPattern
import com.controlcal.core.repo.UserSettingsRepository
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

    companion object {
        const val DEFAULT_MIN_KCAL = 1200.0

        fun serializePattern(pattern: WeeklyDayPattern): String {
            return listOf(
                pattern.monday.name,
                pattern.tuesday.name,
                pattern.wednesday.name,
                pattern.thursday.name,
                pattern.friday.name,
                pattern.saturday.name,
                pattern.sunday.name,
            ).joinToString(",")
        }

        fun parsePattern(raw: String): WeeklyDayPattern {
            val parts = raw.split(",")
            if (parts.size != 7) return WeeklyDayPattern()
            return try {
                WeeklyDayPattern(
                    monday = DayType.valueOf(parts[0]),
                    tuesday = DayType.valueOf(parts[1]),
                    wednesday = DayType.valueOf(parts[2]),
                    thursday = DayType.valueOf(parts[3]),
                    friday = DayType.valueOf(parts[4]),
                    saturday = DayType.valueOf(parts[5]),
                    sunday = DayType.valueOf(parts[6]),
                )
            } catch (_: IllegalArgumentException) {
                WeeklyDayPattern()
            }
        }
    }
}
