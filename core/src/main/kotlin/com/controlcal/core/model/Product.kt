package com.controlcal.core.model

/**
 * Domain model representing a food product.
 * Nutritional values are per 100 g (or 100 ml when [isLiquid]).
 */
data class Product(
    val id: Long = 0,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val kcal100g: Double,
    val protein100g: Double,
    val carbs100g: Double,
    val fat100g: Double,
    val fiber100g: Double? = null,
    val sugars100g: Double? = null,
    val satFat100g: Double? = null,
    val salt100g: Double? = null,
    val isLiquid: Boolean = false,
    val unitName: String? = null,
    val unitG: Double? = null,
    val servingG: Double? = null,
    val minG: Double? = null,
    val maxG: Double? = null,
    val inPantry: Boolean = false,
    val source: ProductSource = ProductSource.MANUAL,
    val archived: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
