package com.controlcal.core.repo

import com.controlcal.core.model.WeeklyDayPattern
import com.controlcal.core.weight.WeightRange

/**
 * Pure repository interface for application user settings (section 4.3 of docs/PLAN.md).
 * Implemented in :app using Preferences DataStore.
 */
interface UserSettingsRepository {
    suspend fun getWeeklyPattern(): WeeklyDayPattern
    suspend fun setWeeklyPattern(pattern: WeeklyDayPattern)
    suspend fun getMinKcalLimit(): Double
    suspend fun setMinKcalLimit(minKcal: Double)
    suspend fun getLastTdeeProposalReviewedDate(): String?
    suspend fun setLastTdeeProposalReviewedDate(date: String)
    suspend fun isHealthConnectStepsSyncEnabled(): Boolean = false
    suspend fun setHealthConnectStepsSyncEnabled(enabled: Boolean) {}
    suspend fun getLastBackupTimestamp(): Long? = null
    suspend fun setLastBackupTimestamp(timestamp: Long) {}
    suspend fun getWeightChartRange(): WeightRange = WeightRange.LAST_1_MONTH
    suspend fun setWeightChartRange(range: WeightRange) {}
}
