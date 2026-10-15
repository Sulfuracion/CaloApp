package com.controlcal.core.nutrition

import com.controlcal.core.model.DayRemaining
import com.controlcal.core.model.DayTotals
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.MacroTargets
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.MealTotals
import com.controlcal.core.model.NutrientTotals
import com.controlcal.core.model.Product
import kotlin.math.max

object NutritionCalculator {

    /**
     * Calculates the proportional macronutrients for a given [grams] amount of a [product].
     * If [grams] is negative (tare mode), the resulting nutrients are negative.
     */
    fun calculatePortionMacros(product: Product, grams: Double): NutrientTotals {
        val factor = grams / 100.0
        return NutrientTotals(
            kcal = product.kcal100g * factor,
            protein = product.protein100g * factor,
            carbs = product.carbs100g * factor,
            fat = product.fat100g * factor,
        )
    }

    /**
     * Calculates aggregated totals for a collection of [entries].
     *
     * Rule from section 4 of docs/PLAN.md:
     * "El total de un día nunca se muestra por debajo de cero aunque haya entradas negativas."
     * Any macro sum that results in a negative value is clamped to 0.0.
     */
    fun calculateTotals(entries: Collection<MealEntry>): NutrientTotals {
        var kcalSum = 0.0
        var proteinSum = 0.0
        var carbsSum = 0.0
        var fatSum = 0.0

        for (entry in entries) {
            kcalSum += entry.kcal
            proteinSum += entry.protein
            carbsSum += entry.carbs
            fatSum += entry.fat
        }

        return NutrientTotals(
            kcal = max(0.0, kcalSum),
            protein = max(0.0, proteinSum),
            carbs = max(0.0, carbsSum),
            fat = max(0.0, fatSum),
        )
    }

    /**
     * Aggregates [entries] by meal ID, calculates per-meal totals (never below zero)
     * and day totals (never below zero). Ensures [mealIds] (defaulting to [DefaultMealIds.ALL])
     * plus any meal IDs present in [entries] are included in [DayTotals.byMeal].
     */
    fun calculateDayTotals(
        date: String,
        entries: List<MealEntry>,
        mealIds: List<String> = DefaultMealIds.ALL,
    ): DayTotals {
        val grouped = entries.groupBy { it.meal }
        val allMealIds = (mealIds + entries.map { it.meal }).distinct()
        val byMeal = allMealIds.associateWith { mealId ->
            val mealEntries = grouped[mealId] ?: emptyList()
            MealTotals(
                meal = mealId,
                totals = calculateTotals(mealEntries),
                entries = mealEntries,
            )
        }
        val dayNutrients = calculateTotals(entries)
        return DayTotals(
            date = date,
            totals = dayNutrients,
            byMeal = byMeal,
        )
    }

    /**
     * Compares [currentTotals] against [targets] to compute remaining amounts
     * (clamped to 0.0 if target is met or exceeded) and any surplus.
     */
    fun calculateRemaining(currentTotals: NutrientTotals, targets: MacroTargets): DayRemaining {
        val remKcal = max(0.0, targets.kcal - currentTotals.kcal)
        val remProtein = max(0.0, targets.protein - currentTotals.protein)
        val remCarbs = max(0.0, targets.carbs - currentTotals.carbs)
        val remFat = max(0.0, targets.fat - currentTotals.fat)

        val surpKcal = max(0.0, currentTotals.kcal - targets.kcal)
        val surpProtein = max(0.0, currentTotals.protein - targets.protein)
        val surpCarbs = max(0.0, currentTotals.carbs - targets.carbs)
        val surpFat = max(0.0, currentTotals.fat - targets.fat)

        return DayRemaining(
            remainingKcal = remKcal,
            remainingProtein = remProtein,
            remainingCarbs = remCarbs,
            remainingFat = remFat,
            surplusKcal = surpKcal,
            surplusProtein = surpProtein,
            surplusCarbs = surpCarbs,
            surplusFat = surpFat,
        )
    }

    /** Converts units to grams when the product defines [unitG]. */
    fun unitsToGrams(units: Double, unitG: Double): Double = units * unitG

    /** Converts grams to units when the product defines [unitG]. */
    fun gramsToUnits(grams: Double, unitG: Double): Double {
        if (unitG == 0.0) return 0.0
        return grams / unitG
    }
}
