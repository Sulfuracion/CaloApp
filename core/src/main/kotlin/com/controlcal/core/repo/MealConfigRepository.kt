package com.controlcal.core.repo

import com.controlcal.core.model.ConfiguredMeal

/**
 * Pure repository interface for accessing and saving configured meals (section 4 of docs/PLAN.md).
 * Implemented in :app using Preferences DataStore.
 */
interface MealConfigRepository {
    suspend fun getMealConfigs(): List<ConfiguredMeal>
    suspend fun saveMealConfigs(configs: List<ConfiguredMeal>)
}
