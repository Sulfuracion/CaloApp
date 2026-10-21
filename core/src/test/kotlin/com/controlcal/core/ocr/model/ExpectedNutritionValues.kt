package com.controlcal.core.ocr.model

import kotlinx.serialization.Serializable

@Serializable
data class ExpectedNutritionValues(
    val fileName: String,
    val productName: String,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val sugars: Double? = null,
    val satFat: Double? = null,
    val fiber: Double? = null,
    val salt: Double? = null
) {
    override fun toString(): String = "$fileName - $productName"
}
