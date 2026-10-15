package com.controlcal.core.tdee

/**
 * Result of the 21-day TDEE estimation algorithm defined in section 5.5 of docs/PLAN.md.
 */
sealed interface TdeeCalculationResult {

    /**
     * The 21-day window did not fulfill the minimum data requirements:
     * at least 14 days of complete food intake and at least 8 weigh-ins.
     */
    data class InsufficientData(
        val loggedFoodDays: Int,
        val requiredFoodDays: Int = TdeeEstimator.REQUIRED_FOOD_DAYS,
        val weighInDays: Int,
        val requiredWeighInDays: Int = TdeeEstimator.REQUIRED_WEIGH_IN_DAYS,
        val missingExplanation: String,
    ) : TdeeCalculationResult

    /**
     * Successful estimation with all intermediate physiological figures exposed
     * for full calculation transparency.
     */
    data class Success(
        val tdeeKcal: Double,
        val averageDailyIntakeKcal: Double,
        val weightTrendChangeKg: Double,
        val startTrendKg: Double,
        val endTrendKg: Double,
        val windowDays: Int,
        val loggedFoodDays: Int,
        val weighInDays: Int,
    ) : TdeeCalculationResult
}
