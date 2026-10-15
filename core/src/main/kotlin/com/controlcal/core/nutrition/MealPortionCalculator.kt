package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry
import kotlin.math.max

/**
 * Pure domain logic for portion step calculations, rescaling, and adjustments
 * according to Task M.5 and section 4 of docs/PLAN.md:
 * - Step is 1 unit if product defines unitG; else servingG if defined; else 10 g.
 * - Rescaling recalculates kcal and macros proportionally from values stored in the entry itself,
 *   never from the product entity, protecting the historical log from subsequent product edits.
 * - [-] does not decrease below one step; to remove an entry, "Eliminar" must be used.
 * - Tare entries are not eligible for step increment/decrement.
 */
object MealPortionCalculator {

    const val DEFAULT_STEP_GRAMS: Double = 10.0

    /**
     * Calculates the step size in grams for [-] and [+] buttons.
     * Priority:
     * 1. [unitG] if present and strictly positive (1 discrete unit).
     * 2. [servingG] if present and strictly positive (1 serving portion).
     * 3. 10.0 grams default.
     */
    fun calculateStep(unitG: Double?, servingG: Double?): Double {
        return when {
            unitG != null && unitG > 0.0 -> unitG
            servingG != null && servingG > 0.0 -> servingG
            else -> DEFAULT_STEP_GRAMS
        }
    }

    /**
     * Rescales [entry] to [newGrams] strictly in proportion to the macronutrients
     * already stored in [entry], preserving history even if the underlying product
     * is subsequently modified.
     *
     * @throws IllegalArgumentException if [entry.grams] <= 0.0 or [newGrams] <= 0.0.
     */
    fun rescaleEntry(entry: MealEntry, newGrams: Double): MealEntry {
        require(entry.grams > 0.0) { "Cannot rescale a non-positive or tare entry" }
        require(newGrams > 0.0) { "newGrams must be strictly positive" }

        val factor = newGrams / entry.grams
        return entry.copy(
            grams = newGrams,
            kcal = entry.kcal * factor,
            protein = entry.protein * factor,
            carbs = entry.carbs * factor,
            fat = entry.fat * factor,
        )
    }

    /**
     * Increases [entry] by [step] grams.
     */
    fun increaseQuantity(entry: MealEntry, step: Double): MealEntry {
        require(step > 0.0) { "step must be strictly positive" }
        return rescaleEntry(entry, entry.grams + step)
    }

    /**
     * Decreases [entry] by [step] grams.
     * Rule from Task M.5: "[-] no baja de un paso: para quitarlo está «Eliminar»."
     * Clamped to [step] so it never goes below 1 step.
     */
    fun decreaseQuantity(entry: MealEntry, step: Double): MealEntry {
        require(step > 0.0) { "step must be strictly positive" }
        val targetGrams = max(step, entry.grams - step)
        return rescaleEntry(entry, targetGrams)
    }

    /**
     * Checks whether [entry] can be decreased further with [-].
     * Returns false if grams is already <= step (allowing small epsilon for floating point).
     */
    fun canDecrease(grams: Double, step: Double): Boolean {
        return grams > step + 0.0001
    }
}
