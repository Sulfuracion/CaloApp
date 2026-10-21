package com.controlcal.core.ocr.model

data class OcrBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class OcrLine(
    val text: String,
    val box: OcrBox
)

data class OcrLabelMetadata(
    val imageFileName: String,
    val timestamp: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val lines: List<OcrLine>
)

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
