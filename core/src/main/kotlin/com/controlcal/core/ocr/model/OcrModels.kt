package com.controlcal.core.ocr.model

import kotlinx.serialization.Serializable

@Serializable
data class OcrBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class OcrLine(
    val text: String,
    val box: OcrBox
)

@Serializable
data class OcrLabelMetadata(
    val imageFileName: String,
    val timestamp: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val lines: List<OcrLine>
)

@Serializable
data class ExpectedNutritionValues(
    val fileName: String,
    val productName: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val sugars: Double? = null,
    val satFat: Double? = null,
    val fiber: Double? = null,
    val salt: Double? = null
) {
    override fun toString(): String = "$fileName - $productName"
}

data class OcrParsedNutrients(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val sugars: Double? = null,
    val satFat: Double? = null,
    val fiber: Double? = null,
    val salt: Double? = null,
    val confidence: Map<String, Float> = emptyMap(),
    val approximations: Set<String> = emptySet()
)
