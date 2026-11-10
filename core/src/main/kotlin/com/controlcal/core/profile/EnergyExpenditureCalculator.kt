package com.controlcal.core.profile

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Pure calculator for Basal Metabolic Rate (BMR) and daily energy expenditure.
 *
 * Implements:
 * 1. Mifflin-St Jeor formula for BMR using EMA weight trend:
 *    - Men:   10 * weight + 6.25 * height - 5 * age + 5
 *    - Women: 10 * weight + 6.25 * height - 5 * age - 161
 * 2. Activity from steps:
 *    - kcal_steps = average_steps_last_7_days * weight * KCAL_PER_STEP_PER_KG
 *    - Days with 0 steps are excluded from the average.
 * 3. Workout energy:
 *    - Configurable per-session kcal in profile (default 300).
 *    - Daily average extra = weekly_sessions * session_kcal / 7.
 * 4. Thermic Effect of Food (TEF / digestion):
 *    - total_expenditure = (basal + steps + workout) / 0.9
 *    - digestion = total_expenditure - (basal + steps + workout)
 */
object EnergyExpenditureCalculator {

    /**
     * Estimated energy expenditure factor per step per kilogram of body mass (kcal / (step * kg)).
     * Derived as an empirical estimate of human walking mechanical efficiency (~0.0004 kcal/step/kg).
     * Documented as a physiological estimation.
     */
    const val KCAL_PER_STEP_PER_KG = 0.0004

    /**
     * Thermic Effect of Food (TEF) divisor factor.
     * Accounting for ~10% of total daily energy expenditure expended in digestion:
     * Total = (Basal + Activity) / (1 - 0.10) = (Basal + Activity) / 0.9.
     */
    const val TEF_DIVISOR = 0.9

    /**
     * Calculates Basal Metabolic Rate (BMR) with the Mifflin-St Jeor equation.
     */
    fun calculateBmr(
        sex: UserSex,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
    ): Double {
        val base = (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * ageYears.toDouble())
        return when (sex) {
            UserSex.MALE -> base + 5.0
            UserSex.FEMALE -> base - 161.0
        }
    }

    /**
     * Calculates average steps and step energy expenditure.
     * Days with 0 steps are excluded as likely unsynchronized / unrecorded.
     */
    fun calculateStepsActivity(
        stepsInWindow: List<Int>,
        weightKg: Double,
    ): StepsActivityResult {
        val validSteps = stepsInWindow.filter { it > 0 }
        if (validSteps.isEmpty()) {
            return StepsActivityResult(
                averageSteps = 0,
                validDaysCount = 0,
                hasStepsData = false,
                stepsKcal = 0.0,
            )
        }
        val averageSteps = (validSteps.sum().toDouble() / validSteps.size.toDouble()).roundToInt()
        val stepsKcal = averageSteps.toDouble() * weightKg * KCAL_PER_STEP_PER_KG
        return StepsActivityResult(
            averageSteps = averageSteps,
            validDaysCount = validSteps.size,
            hasStepsData = true,
            stepsKcal = stepsKcal,
        )
    }

    /**
     * Calculates full energy expenditure breakdown.
     *
     * @param profile User profile (must have sex, birthDate, and heightCm).
     * @param weightTrendKg Latest weight from the EMA trend series (not an isolated scale weigh-in).
     * @param stepsInWindow Steps from the 7-day window.
     * @param weeklyTrainingDays Number of training days per week in user's weekly pattern (0..7).
     * @param referenceDate The date against which user age is evaluated (typically today).
     */
    fun calculate(
        profile: UserProfile,
        weightTrendKg: Double?,
        stepsInWindow: List<Int>,
        weeklyTrainingDays: Int,
        referenceDate: LocalDate,
    ): EnergyExpenditureResult {
        val sex = profile.sex
        val birthDate = profile.birthDate
        val heightCm = profile.heightCm
        val weightKg = weightTrendKg

        val isFutureBirthDate = birthDate?.isAfter(referenceDate) ?: false
        val isInvalidHeight = heightCm != null && heightCm <= 0.0
        val isInvalidWeight = weightKg != null && weightKg <= 0.0

        if (sex == null || birthDate == null || heightCm == null || weightKg == null ||
            isFutureBirthDate || isInvalidHeight || isInvalidWeight
        ) {
            return EnergyExpenditureResult.IncompleteData(
                missingSex = sex == null,
                missingBirthDate = birthDate == null || isFutureBirthDate,
                missingHeight = heightCm == null || isInvalidHeight,
                missingWeightTrend = weightKg == null || isInvalidWeight,
            )
        }

        val ageYears = UserProfileValidator.calculateAge(birthDate, referenceDate)

        val bmrKcal = calculateBmr(
            sex = sex,
            weightKg = weightKg,
            heightCm = heightCm,
            ageYears = ageYears,
        )

        val stepsResult = calculateStepsActivity(stepsInWindow, weightKg)

        val workoutKcalPerSession = profile.workoutKcalPerSession
        val clampedTrainingDays = weeklyTrainingDays.coerceIn(0, 7)
        val averageDailyWorkoutKcal = (clampedTrainingDays.toDouble() * workoutKcalPerSession) / 7.0

        val trainingDay = buildDayExpenditure(
            bmrKcal = bmrKcal,
            stepsKcal = stepsResult.stepsKcal,
            workoutKcal = workoutKcalPerSession,
        )

        val restDay = buildDayExpenditure(
            bmrKcal = bmrKcal,
            stepsKcal = stepsResult.stepsKcal,
            workoutKcal = 0.0,
        )

        val averageDay = buildDayExpenditure(
            bmrKcal = bmrKcal,
            stepsKcal = stepsResult.stepsKcal,
            workoutKcal = averageDailyWorkoutKcal,
        )

        val breakdown = EnergyExpenditureBreakdown(
            basalKcal = bmrKcal,
            weightTrendKg = weightKg,
            heightCm = heightCm,
            ageYears = ageYears,
            stepsKcal = stepsResult.stepsKcal,
            averageSteps = stepsResult.averageSteps,
            validStepsDaysCount = stepsResult.validDaysCount,
            hasStepsData = stepsResult.hasStepsData,
            workoutKcalPerSession = workoutKcalPerSession,
            weeklyTrainingDays = clampedTrainingDays,
            averageDailyWorkoutKcal = averageDailyWorkoutKcal,
            trainingDay = trainingDay,
            restDay = restDay,
            averageDay = averageDay,
        )

        return EnergyExpenditureResult.Success(breakdown)
    }

    private fun buildDayExpenditure(
        bmrKcal: Double,
        stepsKcal: Double,
        workoutKcal: Double,
    ): DayEnergyExpenditure {
        val subtotal = bmrKcal + stepsKcal + workoutKcal
        val totalKcal = subtotal / TEF_DIVISOR
        val digestionKcal = totalKcal - subtotal
        return DayEnergyExpenditure(
            basalKcal = bmrKcal,
            stepsKcal = stepsKcal,
            workoutKcal = workoutKcal,
            digestionKcal = digestionKcal,
            totalKcal = totalKcal,
        )
    }

    data class StepsActivityResult(
        val averageSteps: Int,
        val validDaysCount: Int,
        val hasStepsData: Boolean,
        val stepsKcal: Double,
    )
}
