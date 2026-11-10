package com.controlcal.core.model

/**
 * A saved meal that allows logging multiple food items at once into the diary.
 *
 * Unlike recipes (which are a single composite food consumed by weight), a saved meal
 * registers each item as a separate [MealEntry] in the diary.
 */
data class SavedMeal(
    val id: Long = 0L,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null,
    val useCount: Int = 0,
    val items: List<SavedMealItem> = emptyList(),
)

/**
 * An item within a [SavedMeal].
 *
 * If [productId] is not null, macros are calculated dynamically from the current product
 * upon logging or displaying.
 * If [productId] is null, it represents quick calories, and [kcal], [protein], [carbs], [fat]
 * are frozen values.
 */
data class SavedMealItem(
    val id: Long = 0L,
    val savedMealId: Long = 0L,
    val position: Int = 0,
    val productId: Long? = null,
    val productName: String,
    val grams: Double,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
)

/**
 * A resolved item of a saved meal with current macros calculated and product status determined.
 */
data class ResolvedSavedMealItem(
    val item: SavedMealItem,
    val product: Product? = null,
    val isArchived: Boolean = false,
    val resolvedGrams: Double,
    val resolvedKcal: Double,
    val resolvedProtein: Double,
    val resolvedCarbs: Double,
    val resolvedFat: Double,
)

/**
 * A saved meal with all items resolved and meal-level totals computed.
 */
data class ResolvedSavedMeal(
    val meal: SavedMeal,
    val resolvedItems: List<ResolvedSavedMealItem>,
    val totalKcal: Double,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val hasArchivedProducts: Boolean,
)
