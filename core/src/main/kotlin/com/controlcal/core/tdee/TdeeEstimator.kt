package com.controlcal.core.tdee

import com.controlcal.core.model.WeightEntry
import java.time.LocalDate

/**
 * Calculates adaptive Total Daily Energy Expenditure (TDEE) according to
 * section 5.5 of docs/PLAN.md:
 * - 21-day rolling window.
 * - Minimum data requirements: at least 14 days with complete food log and at least 8 weigh-ins.
 * - TDEE = average daily intake - (weight trend change in kg * 7700 / window days).
 */
object TdeeEstimator {

    const val WINDOW_DAYS = 21
    const val REQUIRED_FOOD_DAYS = 14
    const val REQUIRED_WEIGH_IN_DAYS = 8
    const val KCAL_PER_KG_BODY_MASS = 7700.0

    /**
     * Estimates TDEE for the 21-day period ending on [endDate] (ISO yyyy-MM-dd).
     *
     * @param endDate The final date of the 21-day window.
     * @param dailyIntakes Food intake history.
     * @param weightEntries Weight measurement history (can include entries prior to the window to
     *                      provide a pre-stabilized EMA trend).
     * @return [TdeeCalculationResult.Success] if data requirements are fulfilled, or
     *         [TdeeCalculationResult.InsufficientData] with explanation of what is missing.
     */
    fun estimate(
        endDate: String,
        dailyIntakes: List<DailyIntake>,
        weightEntries: List<WeightEntry>,
    ): TdeeCalculationResult {
        val endLocalDate = LocalDate.parse(endDate)
        val startLocalDate = endLocalDate.minusDays((WINDOW_DAYS - 1).toLong())
        val startDate = startLocalDate.toString()

        // 1. Filter and deduplicate valid food logs in the 21-day window
        val validIntakesInWindow = dailyIntakes
            .filter { it.isComplete && it.kcal > 0.0 && it.date in startDate..endDate }
            .associateBy { it.date } // deduplicate by date
            .values
            .toList()

        val loggedFoodDays = validIntakesInWindow.size

        // 2. Filter valid weight entries in the 21-day window
        val validWeightsInWindow = weightEntries
            .filter { it.weightKg > 0.0 && it.date in startDate..endDate }
            .associateBy { it.date } // deduplicate by date
            .values
            .toList()

        val weighInDays = validWeightsInWindow.size

        // 3. Check requirements: >= 14 food days and >= 8 weight days
        if (loggedFoodDays < REQUIRED_FOOD_DAYS || weighInDays < REQUIRED_WEIGH_IN_DAYS) {
            val explanation = buildInsufficientExplanation(
                loggedFoodDays = loggedFoodDays,
                weighInDays = weighInDays,
            )
            return TdeeCalculationResult.InsufficientData(
                loggedFoodDays = loggedFoodDays,
                requiredFoodDays = REQUIRED_FOOD_DAYS,
                weighInDays = weighInDays,
                requiredWeighInDays = REQUIRED_WEIGH_IN_DAYS,
                missingExplanation = explanation,
            )
        }

        // 4. Ingesta media diaria (average over logged days only)
        val totalKcal = validIntakesInWindow.sumOf { it.kcal }
        val averageDailyIntakeKcal = totalKcal / loggedFoodDays.toDouble()

        // 5. Weight trend calculation over the window
        // We calculate EMA on all valid weights up to endDate to ensure proper smoothing
        val validWeightsUpToEnd = weightEntries
            .filter { it.weightKg > 0.0 && it.date <= endDate }
            .associateBy { it.date }
            .values
            .sortedBy { it.date }

        val trendSeries = WeightTrend.calculateTrend(validWeightsUpToEnd)
        val trendInWindow = trendSeries.filter { it.date in startDate..endDate }

        val startTrendKg = trendInWindow.first().trendKg
        val endTrendKg = trendInWindow.last().trendKg
        val weightTrendChangeKg = endTrendKg - startTrendKg

        // 6. TDEE formula: average intake - (trend change in kg * 7700 / window days)
        val dailyEnergyDelta = (weightTrendChangeKg * KCAL_PER_KG_BODY_MASS) / WINDOW_DAYS.toDouble()
        val tdeeKcal = averageDailyIntakeKcal - dailyEnergyDelta

        return TdeeCalculationResult.Success(
            tdeeKcal = tdeeKcal,
            averageDailyIntakeKcal = averageDailyIntakeKcal,
            weightTrendChangeKg = weightTrendChangeKg,
            startTrendKg = startTrendKg,
            endTrendKg = endTrendKg,
            windowDays = WINDOW_DAYS,
            loggedFoodDays = loggedFoodDays,
            weighInDays = weighInDays,
        )
    }

    private fun buildInsufficientExplanation(
        loggedFoodDays: Int,
        weighInDays: Int,
    ): String {
        val missingFood = REQUIRED_FOOD_DAYS - loggedFoodDays
        val missingWeights = REQUIRED_WEIGH_IN_DAYS - weighInDays

        return when {
            missingFood > 0 && missingWeights > 0 ->
                "Faltan $missingFood días de registro de comida y $missingWeights pesajes en los últimos 21 días (se requieren al menos $REQUIRED_FOOD_DAYS días de comida y $REQUIRED_WEIGH_IN_DAYS pesajes)."
            missingFood > 0 ->
                "Faltan $missingFood días con registro de comida completo en los últimos 21 días (se requieren al menos $REQUIRED_FOOD_DAYS días, registrados $loggedFoodDays)."
            else ->
                "Faltan $missingWeights pesajes en los últimos 21 días (se requieren al menos $REQUIRED_WEIGH_IN_DAYS pesajes, registrados $weighInDays)."
        }
    }
}
