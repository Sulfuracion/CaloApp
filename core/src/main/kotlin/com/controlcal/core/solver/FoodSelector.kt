package com.controlcal.core.solver

import com.controlcal.core.model.DayRemaining
import com.controlcal.core.model.MacroTargets
import com.controlcal.core.model.NutrientTotals
import com.controlcal.core.model.Product
import com.controlcal.core.nutrition.NumberFormatHelper
import kotlin.math.abs
import kotlin.math.min

enum class FoodCategory {
    PROTEIN,
    CARBS,
    FAT,
    BALANCED,
}

/**
 * Orchestrates food selection and Macro Tetris optimization following PLAN.md section 5.6:
 * - Selects at most 5 products via greedy forward selection (stopping when improvement < 1%).
 * - Applies MacroSolver (bounded least squares with default/custom limits).
 * - Applies MacroRounding (5g multiples or discrete unit counts).
 * - Recalculates achieved macros and reports real error.
 * - Emits clear, actionable suggestions when error exceeds 10%.
 */
object FoodSelector {

    /**
     * Classifies a [Product] by its dominant macro calorie contribution (PLAN.md 5.6 step 3):
     * - Protein > 40% of kcal -> [FoodCategory.PROTEIN]
     * - Carbs > 50% of kcal -> [FoodCategory.CARBS]
     * - Fat > 50% of kcal -> [FoodCategory.FAT]
     * - Otherwise -> [FoodCategory.BALANCED]
     */
    fun classifyFood(product: Product): FoodCategory {
        val totalMacroKcal = (product.protein100g * 4.0) + (product.carbs100g * 4.0) + (product.fat100g * 9.0)
        val denominator = if (product.kcal100g > 0.0) product.kcal100g else totalMacroKcal
        if (denominator <= 0.0) return FoodCategory.BALANCED

        val proteinShare = (product.protein100g * 4.0) / denominator
        val carbsShare = (product.carbs100g * 4.0) / denominator
        val fatShare = (product.fat100g * 9.0) / denominator

        return when {
            proteinShare > 0.40 -> FoodCategory.PROTEIN
            carbsShare > 0.50 -> FoodCategory.CARBS
            fatShare > 0.50 -> FoodCategory.FAT
            else -> FoodCategory.BALANCED
        }
    }

    fun selectAndSolve(
        target: MacroTargets,
        pantryProducts: List<Product>,
        weights: MacroWeights = MacroWeights(),
    ): MacroSolution {
        val candidates = pantryProducts.filter { it.inPantry && !it.archived }

        // Edge Case 1: Empty pantry
        if (candidates.isEmpty()) {
            val achieved = NutrientTotals(0.0, 0.0, 0.0, 0.0)
            val error = computeError(achieved, target)
            val suggestions = buildSuggestions(achieved, target, error, isPantryEmpty = true)
            val rawLoss = MacroSolver.calculateLoss(target, emptyMap(), weights)
            return MacroSolution(
                items = emptyList(),
                targetMacros = target,
                achievedMacros = achieved,
                error = error,
                suggestions = suggestions,
                rawLoss = rawLoss,
            )
        }

        // Edge Case 2: Targets are zero or negative (already reached)
        if (target.kcal <= 0.0 && target.protein <= 0.0 && target.carbs <= 0.0 && target.fat <= 0.0) {
            val achieved = NutrientTotals(0.0, 0.0, 0.0, 0.0)
            val error = MacroError(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
            return MacroSolution(
                items = emptyList(),
                targetMacros = target,
                achievedMacros = achieved,
                error = error,
                suggestions = emptyList(),
                rawLoss = 0.0,
            )
        }

        // Step 4: Greedy Forward Selection (max 5 products, stop if improvement < 1%)
        val selected = mutableListOf<Product>()
        var currentLoss = MacroSolver.calculateLoss(target, emptyMap(), weights)
        val maxIterations = min(5, candidates.size)

        for (step in 0 until maxIterations) {
            var bestCandidate: Product? = null
            var bestLoss = currentLoss

            for (candidate in candidates) {
                if (candidate in selected) continue
                val candidateSet = selected + candidate
                val solvedAmounts = MacroSolver.solve(target, candidateSet, weights)
                val loss = MacroSolver.calculateLoss(target, solvedAmounts, weights)

                if (loss < bestLoss) {
                    bestLoss = loss
                    bestCandidate = candidate
                }
            }

            if (bestCandidate == null) {
                // No candidate further reduces the loss
                break
            }

            val relativeImprovement = if (currentLoss > 1e-9) {
                (currentLoss - bestLoss) / currentLoss
            } else {
                0.0
            }

            // Stop if improvement is less than 1% (PLAN.md 5.6 step 4)
            if (selected.isNotEmpty() && relativeImprovement < 0.01) {
                break
            }

            selected.add(bestCandidate)
            currentLoss = bestLoss
        }

        if (selected.isEmpty()) {
            val achieved = NutrientTotals(0.0, 0.0, 0.0, 0.0)
            val error = computeError(achieved, target)
            val suggestions = buildSuggestions(achieved, target, error, isPantryEmpty = false)
            return MacroSolution(
                items = emptyList(),
                targetMacros = target,
                achievedMacros = achieved,
                error = error,
                suggestions = suggestions,
                rawLoss = currentLoss,
            )
        }

        // Solve continuous allocation for selected subset
        val rawAmounts = MacroSolver.solve(target, selected, weights)
        val rawLoss = MacroSolver.calculateLoss(target, rawAmounts, weights)

        // Step 5: Round portions (5g or unit_g) and exclude 0g items
        val roundedItems = MacroRounding.round(rawAmounts)

        // Recalculate achieved macros from rounded items
        var achievedKcal = 0.0
        var achievedProtein = 0.0
        var achievedCarbs = 0.0
        var achievedFat = 0.0

        for (item in roundedItems) {
            val factor = item.roundedGrams / 100.0
            achievedKcal += item.product.kcal100g * factor
            achievedProtein += item.product.protein100g * factor
            achievedCarbs += item.product.carbs100g * factor
            achievedFat += item.product.fat100g * factor
        }

        val achievedMacros = NutrientTotals(
            kcal = achievedKcal,
            protein = achievedProtein,
            carbs = achievedCarbs,
            fat = achievedFat,
        )

        // Step 6: Real error and suggestions for errors > 10%
        val error = computeError(achievedMacros, target)
        val suggestions = buildSuggestions(achievedMacros, target, error, isPantryEmpty = false)

        return MacroSolution(
            items = roundedItems,
            targetMacros = target,
            achievedMacros = achievedMacros,
            error = error,
            suggestions = suggestions,
            rawLoss = rawLoss,
        )
    }

    fun selectAndSolve(
        remaining: DayRemaining,
        pantryProducts: List<Product>,
        weights: MacroWeights = MacroWeights(),
    ): MacroSolution = selectAndSolve(
        target = MacroTargets(
            kcal = remaining.remainingKcal,
            protein = remaining.remainingProtein,
            carbs = remaining.remainingCarbs,
            fat = remaining.remainingFat,
        ),
        pantryProducts = pantryProducts,
        weights = weights,
    )

    private fun computeError(achieved: NutrientTotals, target: MacroTargets): MacroError {
        val diffKcal = achieved.kcal - target.kcal
        val diffProtein = achieved.protein - target.protein
        val diffCarbs = achieved.carbs - target.carbs
        val diffFat = achieved.fat - target.fat

        fun pct(diff: Double, targetVal: Double): Double {
            if (targetVal <= 0.0) {
                return if (diff == 0.0) 0.0 else 100.0
            }
            return (abs(diff) / targetVal) * 100.0
        }

        return MacroError(
            diffKcal = diffKcal,
            diffProtein = diffProtein,
            diffCarbs = diffCarbs,
            diffFat = diffFat,
            percentKcal = pct(diffKcal, target.kcal),
            percentProtein = pct(diffProtein, target.protein),
            percentCarbs = pct(diffCarbs, target.carbs),
            percentFat = pct(diffFat, target.fat),
        )
    }

    private fun buildSuggestions(
        achieved: NutrientTotals,
        target: MacroTargets,
        error: MacroError,
        isPantryEmpty: Boolean,
    ): List<MacroSuggestion> {
        val list = mutableListOf<MacroSuggestion>()

        if (isPantryEmpty) {
            list.add(
                MacroSuggestion(
                    macro = "Despensa",
                    percentError = 100.0,
                    message = "La despensa está vacía: añade o marca alimentos en tu despensa para calcular combinaciones.",
                    suggestionType = MacroSuggestionType.EMPTY_PANTRY,
                )
            )
        }

        fun formatDec(value: Double): String = NumberFormatHelper.formatDecimal(value, maxDecimals = 1)

        // Protein suggestion (> 10%)
        if (error.percentProtein > 10.0 && target.protein > 0.0) {
            if (error.diffProtein < 0.0) {
                list.add(
                    MacroSuggestion(
                        macro = "Proteína",
                        percentError = error.percentProtein,
                        message = "Falta proteína (déficit del ${formatDec(error.percentProtein)}%): añade fuentes magras como pechuga de pollo, atún, claras de huevo o tofu.",
                        suggestionType = MacroSuggestionType.MISSING_PROTEIN,
                    )
                )
            } else {
                list.add(
                    MacroSuggestion(
                        macro = "Proteína",
                        percentError = error.percentProtein,
                        message = "Exceso de proteína (+${formatDec(error.percentProtein)}%): los alimentos disponibles superan el objetivo de proteína.",
                        suggestionType = MacroSuggestionType.EXCESS_PROTEIN,
                    )
                )
            }
        }

        // Carbs suggestion (> 10%)
        if (error.percentCarbs > 10.0 && target.carbs > 0.0) {
            if (error.diffCarbs < 0.0) {
                list.add(
                    MacroSuggestion(
                        macro = "Carbohidratos",
                        percentError = error.percentCarbs,
                        message = "Faltan carbohidratos (déficit del ${formatDec(error.percentCarbs)}%): añade alimentos como arroz, avena, patata o fruta.",
                        suggestionType = MacroSuggestionType.MISSING_CARBS,
                    )
                )
            } else {
                list.add(
                    MacroSuggestion(
                        macro = "Carbohidratos",
                        percentError = error.percentCarbs,
                        message = "Exceso de carbohidratos (+${formatDec(error.percentCarbs)}%): los alimentos disponibles superan el objetivo de carbohidratos.",
                        suggestionType = MacroSuggestionType.EXCESS_CARBS,
                    )
                )
            }
        }

        // Fat suggestion (> 10%)
        if (error.percentFat > 10.0 && target.fat > 0.0) {
            if (error.diffFat < 0.0) {
                list.add(
                    MacroSuggestion(
                        macro = "Grasa",
                        percentError = error.percentFat,
                        message = "Falta grasa (déficit del ${formatDec(error.percentFat)}%): añade grasas saludables como aceite de oliva virgen extra, frutos secos o aguacate.",
                        suggestionType = MacroSuggestionType.MISSING_FAT,
                    )
                )
            } else {
                list.add(
                    MacroSuggestion(
                        macro = "Grasa",
                        percentError = error.percentFat,
                        message = "Exceso de grasa (+${formatDec(error.percentFat)}%): los alimentos seleccionados superan el objetivo de grasa restante.",
                        suggestionType = MacroSuggestionType.EXCESS_FAT,
                    )
                )
            }
        }

        // Calories suggestion (> 10%)
        if (error.percentKcal > 10.0 && target.kcal > 0.0) {
            if (error.diffKcal < 0.0) {
                list.add(
                    MacroSuggestion(
                        macro = "Calorías",
                        percentError = error.percentKcal,
                        message = "Faltan calorías (déficit del ${formatDec(error.percentKcal)}%): añade alimentos más densos o aumenta las raciones permitidas.",
                        suggestionType = MacroSuggestionType.MISSING_CALORIES,
                    )
                )
            } else {
                list.add(
                    MacroSuggestion(
                        macro = "Calorías",
                        percentError = error.percentKcal,
                        message = "Exceso de calorías (+${formatDec(error.percentKcal)}%): se supera el objetivo calórico restante.",
                        suggestionType = MacroSuggestionType.EXCESS_CALORIES,
                    )
                )
            }
        }

        return list
    }
}
