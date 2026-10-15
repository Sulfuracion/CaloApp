package com.controlcal.core.solver

import com.controlcal.core.model.MacroTargets
import com.controlcal.core.model.NutrientTotals
import com.controlcal.core.model.Product

/**
 * Weights for the quadratic penalty in Macro Tetris solver.
 * Defaults according to PLAN.md section 5.6: kcal 1.0, protein 1.5, carbs 0.7, fat 0.7.
 */
data class MacroWeights(
    val kcal: Double = 1.0,
    val protein: Double = 1.5,
    val carbs: Double = 0.7,
    val fat: Double = 0.7,
)

/**
 * Real error between achieved macronutrients and target macronutrients.
 * Differences are (achieved - target). Positive means surplus, negative means deficit.
 * Percentages are relative to target: (|diff| / target) * 100.
 */
data class MacroError(
    val diffKcal: Double,
    val diffProtein: Double,
    val diffCarbs: Double,
    val diffFat: Double,
    val percentKcal: Double,
    val percentProtein: Double,
    val percentCarbs: Double,
    val percentFat: Double,
)

/**
 * Type of nutritional discrepancy when error exceeds 10%.
 */
enum class MacroSuggestionType {
    MISSING_PROTEIN,
    MISSING_CARBS,
    MISSING_FAT,
    MISSING_CALORIES,
    EXCESS_PROTEIN,
    EXCESS_CARBS,
    EXCESS_FAT,
    EXCESS_CALORIES,
    EMPTY_PANTRY,
}

/**
 * Actionable nutritional recommendation when a macronutrient deviates by more than 10%.
 */
data class MacroSuggestion(
    val macro: String,
    val percentError: Double,
    val message: String,
    val suggestionType: MacroSuggestionType,
)

/**
 * Item in the solution representing a food product and its portion in grams.
 */
data class MacroSolutionItem(
    val product: Product,
    val rawGrams: Double,
    val roundedGrams: Double,
    val isUnitBased: Boolean,
    val unitCount: Double? = null,
    val unitName: String? = null,
)

/**
 * Complete result of Macro Tetris optimization.
 */
data class MacroSolution(
    val items: List<MacroSolutionItem>,
    val targetMacros: MacroTargets,
    val achievedMacros: NutrientTotals,
    val error: MacroError,
    val suggestions: List<MacroSuggestion>,
    val rawLoss: Double,
)
