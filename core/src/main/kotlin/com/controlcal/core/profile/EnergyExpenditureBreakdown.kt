package com.controlcal.core.profile

/**
 * Breakdown of estimated energy expenditure for a specific day type (training, rest, or daily average).
 */
data class DayEnergyExpenditure(
    val basalKcal: Double,
    val stepsKcal: Double,
    val workoutKcal: Double,
    val digestionKcal: Double,
    val totalKcal: Double,
)

/**
 * Complete physiological energy expenditure calculation breakdown.
 */
data class EnergyExpenditureBreakdown(
    val basalKcal: Double,
    val weightTrendKg: Double,
    val heightCm: Double = 0.0,
    val ageYears: Int,
    val stepsKcal: Double,
    val averageSteps: Int,
    val validStepsDaysCount: Int,
    val hasStepsData: Boolean,
    val workoutKcalPerSession: Double,
    val weeklyTrainingDays: Int,
    val averageDailyWorkoutKcal: Double,
    val trainingDay: DayEnergyExpenditure,
    val restDay: DayEnergyExpenditure,
    val averageDay: DayEnergyExpenditure,
)

/**
 * Result of the energy expenditure calculation.
 */
sealed interface EnergyExpenditureResult {
    data class IncompleteData(
        val missingSex: Boolean,
        val missingBirthDate: Boolean,
        val missingHeight: Boolean,
        val missingWeightTrend: Boolean,
    ) : EnergyExpenditureResult

    data class Success(
        val breakdown: EnergyExpenditureBreakdown,
    ) : EnergyExpenditureResult
}
