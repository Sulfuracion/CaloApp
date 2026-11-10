package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.NutrientTotals
import com.controlcal.core.model.Product
import com.controlcal.core.model.ResolvedSavedMeal
import com.controlcal.core.model.ResolvedSavedMealItem
import com.controlcal.core.model.SavedMeal
import com.controlcal.core.model.SavedMealItem
import kotlin.math.max

/**
 * Pure domain calculations for saved meals:
 * - Creating a saved meal from diary entries (aggregating by product, subtracting taras, discarding <= 0).
 * - Resolving dynamic macros against current product values.
 * - Applying multipliers (0.5, 1.0, 1.5, 2.0, or custom).
 * - Converting to [MealEntry] instances for batch diary logging.
 */
object SavedMealCalculator {

    /**
     * Creates a [SavedMeal] from the current entries of a meal.
     *
     * Groups entries by [MealEntry.productId], sums their grams (so tara entries, which are negative,
     * are subtracted), and discards anything with grams <= 0.
     * Quick calories entries (where productId is null) are preserved if their kcal > 0.
     * Returns null if no valid positive items remain.
     */
    fun createFromEntries(
        name: String,
        entries: List<MealEntry>,
        currentTime: Long = System.currentTimeMillis(),
    ): SavedMeal? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || entries.isEmpty()) return null

        val productEntries = entries.filter { it.productId != null }
        val quickEntries = entries.filter { it.productId == null }

        val items = mutableListOf<SavedMealItem>()
        var position = 0

        // Group by productId and sum grams
        val groupedByProduct = productEntries.groupBy { it.productId!! }
        for ((productId, productGroup) in groupedByProduct) {
            val netGrams = productGroup.sumOf { it.grams }
            if (netGrams > 0.0) {
                val representative = productGroup.first()
                items.add(
                    SavedMealItem(
                        id = 0L,
                        savedMealId = 0L,
                        position = position++,
                        productId = productId,
                        productName = representative.productName,
                        grams = netGrams,
                        kcal = null,
                        protein = null,
                        carbs = null,
                        fat = null,
                    )
                )
            }
        }

        // Process quick calories entries
        for (quick in quickEntries) {
            if (quick.kcal > 0.0) {
                items.add(
                    SavedMealItem(
                        id = 0L,
                        savedMealId = 0L,
                        position = position++,
                        productId = null,
                        productName = quick.productName,
                        grams = 0.0,
                        kcal = quick.kcal,
                        protein = quick.protein,
                        carbs = quick.carbs,
                        fat = quick.fat,
                    )
                )
            }
        }

        if (items.isEmpty()) return null

        return SavedMeal(
            id = 0L,
            name = trimmedName,
            createdAt = currentTime,
            updatedAt = currentTime,
            lastUsedAt = null,
            useCount = 0,
            items = items,
        )
    }

    /**
     * Resolves a single [SavedMealItem] against current product values and an optional [multiplier].
     */
    fun resolveItem(
        item: SavedMealItem,
        product: Product?,
        multiplier: Double = 1.0,
    ): ResolvedSavedMealItem {
        val mult = max(0.0, multiplier)
        return if (item.productId != null) {
            val scaledGrams = item.grams * mult
            if (product != null) {
                val portion = NutritionCalculator.calculatePortionMacros(product, scaledGrams)
                ResolvedSavedMealItem(
                    item = item,
                    product = product,
                    isArchived = product.archived,
                    resolvedGrams = scaledGrams,
                    resolvedKcal = portion.kcal,
                    resolvedProtein = portion.protein,
                    resolvedCarbs = portion.carbs,
                    resolvedFat = portion.fat,
                )
            } else {
                ResolvedSavedMealItem(
                    item = item,
                    product = null,
                    isArchived = false,
                    resolvedGrams = scaledGrams,
                    resolvedKcal = (item.kcal ?: 0.0) * mult,
                    resolvedProtein = (item.protein ?: 0.0) * mult,
                    resolvedCarbs = (item.carbs ?: 0.0) * mult,
                    resolvedFat = (item.fat ?: 0.0) * mult,
                )
            }
        } else {
            ResolvedSavedMealItem(
                item = item,
                product = null,
                isArchived = false,
                resolvedGrams = 0.0,
                resolvedKcal = (item.kcal ?: 0.0) * mult,
                resolvedProtein = (item.protein ?: 0.0) * mult,
                resolvedCarbs = (item.carbs ?: 0.0) * mult,
                resolvedFat = (item.fat ?: 0.0) * mult,
            )
        }
    }

    /**
     * Resolves all items in [meal] and calculates meal-level totals and archived status.
     */
    fun resolveMeal(
        meal: SavedMeal,
        productsById: Map<Long, Product>,
        multiplier: Double = 1.0,
    ): ResolvedSavedMeal {
        val resolvedItems = meal.items.map { item ->
            val product = item.productId?.let { productsById[it] }
            resolveItem(item, product, multiplier)
        }

        val totalKcal = max(0.0, resolvedItems.sumOf { it.resolvedKcal })
        val totalProtein = max(0.0, resolvedItems.sumOf { it.resolvedProtein })
        val totalCarbs = max(0.0, resolvedItems.sumOf { it.resolvedCarbs })
        val totalFat = max(0.0, resolvedItems.sumOf { it.resolvedFat })
        val hasArchived = resolvedItems.any { it.isArchived }

        return ResolvedSavedMeal(
            meal = meal,
            resolvedItems = resolvedItems,
            totalKcal = totalKcal,
            totalProtein = totalProtein,
            totalCarbs = totalCarbs,
            totalFat = totalFat,
            hasArchivedProducts = hasArchived,
        )
    }

    /**
     * Converts a [SavedMeal] into a list of [MealEntry] ready to be persisted via [MealEntryRepository.saveAll].
     */
    fun buildMealEntries(
        meal: SavedMeal,
        productsById: Map<Long, Product>,
        multiplier: Double,
        date: String,
        mealId: String,
        loggedAt: Long,
    ): List<MealEntry> {
        val mult = max(0.0, multiplier)
        return meal.items.mapNotNull { item ->
            if (item.productId != null) {
                val scaledGrams = item.grams * mult
                if (scaledGrams <= 0.0) return@mapNotNull null
                val product = productsById[item.productId]
                val portion = if (product != null) {
                    NutritionCalculator.calculatePortionMacros(product, scaledGrams)
                } else {
                    NutrientTotals(
                        kcal = (item.kcal ?: 0.0) * mult,
                        protein = (item.protein ?: 0.0) * mult,
                        carbs = (item.carbs ?: 0.0) * mult,
                        fat = (item.fat ?: 0.0) * mult,
                    )
                }
                MealEntry(
                    id = 0L,
                    date = date,
                    meal = mealId,
                    productId = item.productId,
                    productName = product?.name ?: item.productName,
                    grams = scaledGrams,
                    kcal = portion.kcal,
                    protein = portion.protein,
                    carbs = portion.carbs,
                    fat = portion.fat,
                    loggedAt = loggedAt,
                )
            } else {
                val scaledKcal = (item.kcal ?: 0.0) * mult
                if (scaledKcal <= 0.0) return@mapNotNull null
                MealEntry(
                    id = 0L,
                    date = date,
                    meal = mealId,
                    productId = null,
                    productName = item.productName,
                    grams = 0.0,
                    kcal = scaledKcal,
                    protein = (item.protein ?: 0.0) * mult,
                    carbs = (item.carbs ?: 0.0) * mult,
                    fat = (item.fat ?: 0.0) * mult,
                    loggedAt = loggedAt,
                )
            }
        }
    }
}
