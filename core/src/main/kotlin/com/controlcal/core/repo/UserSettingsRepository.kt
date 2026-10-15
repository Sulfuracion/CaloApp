package com.controlcal.core.repo

import com.controlcal.core.model.WeeklyDayPattern

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
}
