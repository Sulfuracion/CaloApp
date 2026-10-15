package com.controlcal.core.solver

import com.controlcal.core.model.Product
import kotlin.math.round

/**
 * Handles rounding of continuous solver outputs according to PLAN.md section 5.6 (step 5):
 * - If product has [unitG], rounds to integer multiples of unitG.
 * - Otherwise, rounds to multiples of 5 g.
 * - Clamps to [minG, maxG].
 * - Excludes non-contributing portions of 0 g.
 */
object MacroRounding {

    fun round(
        rawAmounts: Map<Product, Double>,
    ): List<MacroSolutionItem> {
        val result = mutableListOf<MacroSolutionItem>()

        for ((product, rawGrams) in rawAmounts) {
            val (minG, maxG) = MacroSolver.resolveBounds(product)
            val unitG = product.unitG

            val (roundedGrams, isUnitBased, unitCount) = if (unitG != null && unitG > 0.0) {
                val rawUnits = rawGrams / unitG
                val roundedUnits = round(rawUnits)
                val grams = (roundedUnits * unitG).coerceIn(minG, maxG)
                val finalUnitCount = if (unitG > 0.0) round(grams / unitG) else null
                Triple(grams, true, finalUnitCount)
            } else {
                val grams = (round(rawGrams / 5.0) * 5.0).coerceIn(minG, maxG)
                Triple(grams, false, null)
            }

            if (roundedGrams > 0.0 || minG > 0.0) {
                result.add(
                    MacroSolutionItem(
                        product = product,
                        rawGrams = rawGrams,
                        roundedGrams = roundedGrams,
                        isUnitBased = isUnitBased,
                        unitCount = unitCount,
                        unitName = product.unitName,
                    )
                )
            }
        }

        return result
    }
}
